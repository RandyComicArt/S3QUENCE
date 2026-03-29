package game;

import static game.config.GamePanelConstants.*;
import static game.util.UiMath.*;

import game.audio.AudioManager;
import game.config.GameConfig;
import game.input.ControllerInputManager;
import game.logic.DamageCalculator;
import game.logic.RoundCompletion;
import game.logic.RoundManager;
import game.model.Direction;
import game.model.EnemyArchetype;
import game.model.EncounterEnemy;
import game.model.EncounterNode;
import game.model.ItemArchetype;
import game.model.ScreenState;
import game.model.ShopOption;
import game.util.GameImageLoader;
import game.visual.BackdropEffects;
import game.visual.CrtDisplay;
import game.visual.EnemyKillEffects;

import javax.sound.sampled.Clip;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

@SuppressWarnings({"serial", "this-escape"})
public class GamePanel extends JPanel implements ActionListener {
    private final Timer timer;
    private final RoundManager roundManager = new RoundManager();
    private final BackdropEffects backdropEffects = new BackdropEffects();
    private final EnemyKillEffects enemyKillEffects = new EnemyKillEffects();
    private final CrtDisplay crtDisplay = new CrtDisplay();
    private final ControllerInputManager controllerInputManager = new ControllerInputManager();
    private final EnumMap<Direction, BufferedImage> arrowSprites = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, BufferedImage> arrowSpritesGreen = new EnumMap<>(Direction.class);
    private BufferedImage sequenceIdleSprite;
    private BufferedImage sequencePunch1Sprite;
    private BufferedImage sequencePunch2Sprite;
    private BufferedImage sequencePunch3Sprite;
    private BufferedImage fullHeartSprite;
    private BufferedImage damageFlashHeartSprite;
    private BufferedImage damagedHeartSprite;
    private BufferedImage emptyHeartSprite;
    private BufferedImage megamanTransitionSprite;
    private BufferedImage startMenuSprite;
    private BufferedImage openingTextSprite;
    private Image openingStaticGif;
    private long openingStaticSequenceMs = OPENING_STATIC_SEQUENCE_FALLBACK_MS;
    private BufferedImage poisonIconSprite;
    private BufferedImage poisonIconAttack1Sprite;
    private BufferedImage poisonIconAttack2Sprite;
    private final BufferedImage[] initialSurgeSprites = new BufferedImage[5];
    private BufferedImage sceneBuffer;

    private final Random random = new Random();
    private final List<EncounterNode> roomEncounters = new ArrayList<>();

    private ScreenState screen = ScreenState.OPENING;
    private int roomNumber = 1;
    private double playerX;
    private double playerY;
    private int activeEncounterIndex = -1;
    private int pendingEncounterIndex = -1;
    private boolean encounterTransitionActive;
    private long encounterTransitionStartMs;
    private boolean encounterIntroActive;
    private long encounterIntroStartMs;
    private boolean encounterBestedTransitionActive;
    private long encounterBestedTransitionStartMs;
    private boolean roomTransitionActive;
    private long roomTransitionStartMs;
    private boolean roomIntroActive;
    private long roomIntroStartMs;
    private Direction pendingRoomEntryDirection;
    private Direction roomIntroDirection;
    private boolean keyboardMoveUpHeld;
    private boolean keyboardMoveDownHeld;
    private boolean keyboardMoveLeftHeld;
    private boolean keyboardMoveRightHeld;
    private boolean controllerMoveUpHeld;
    private boolean controllerMoveDownHeld;
    private boolean controllerMoveLeftHeld;
    private boolean controllerMoveRightHeld;
    private long lastTickNanos = System.nanoTime();
    private int lastHitDamage;
    private long lastHitUntilMs;
    private int initialSurgePendingBaseDamage;
    private int displayedEnemyHealth = -1;
    private EncounterEnemy displayedEnemyRef;
    private int lastPoisonDamage;
    private long lastPoisonUntilMs;
    private long lastPoisonAnimationStartMs;
    private int poisonTicksRemaining;
    private long nextPoisonTickMs;
    private double poisonBuildUp;
    private boolean runStartFadeInActive;
    private long runStartFadeInStartMs;
    private long openingSequenceStartMs;
    private boolean menuTransitionActive;
    private long menuTransitionStartMs;
    private boolean startRunTransitionActive;
    private long startRunTransitionStartMs;
    private int sequencePunchFrame;
    private int sequencePunchPatternIndex = -1;
    private long lastSequencePunchMs;
    private int lastFinisherSfxIndex = -1;
    private int coinCount;
    private int lastCoinGain;
    private long lastCoinGainUntilMs;
    private String activeMusicFile;
    private String encounterMusicFile = ENCOUNTER_MUSIC_FILES[0];
    private long displayedTimerMs = -1L;
    private long displayedTimerDurationMs = 1L;
    private double playerHealth = GameConfig.PLAYER_MAX_HEALTH;
    private double displayedPlayerHealth = GameConfig.PLAYER_MAX_HEALTH;
    private long heartDamageFlashUntilMs;
    private long healthDrainReliefMs;
    private boolean timeoutRecoveryActive;
    private long timeoutRecoveryStartMs;
    private long timeoutRecoveryTargetMs;
    private int menuSelectionIndex = MENU_ITEM_START;
    private double menuStartHoverProgress;
    private double settingsRevealProgress;
    private double encounterMusicMix;
    private double shopMusicFade;
    private int settingsSelectionIndex = SETTINGS_VIDEO_RENDER_QUALITY;
    private int settingsTabIndex = SETTINGS_TAB_VIDEO;
    private int shopSelectionIndex;
    private long mistakeGuardCharges;
    private long nextEncounterTimeBonusMs;
    private EnemyArchetype forcedTestEnemy;
    private int forcedTestItemMask;
    private Direction doorDirection = Direction.RIGHT;
    private int sceneBufferWidth = RENDER_QUALITY_WIDTHS[DEFAULT_RENDER_QUALITY_INDEX];
    private int sceneBufferHeight = RENDER_QUALITY_HEIGHTS[DEFAULT_RENDER_QUALITY_INDEX];
    private int renderQualityIndex = DEFAULT_RENDER_QUALITY_INDEX;
    private boolean crtBleedEnabled = DEFAULT_CRT_COLOR_BLEED;
    private boolean crtScanlinesEnabled = true;
    private int crtBrightnessIndex = DEFAULT_BRIGHTNESS_INDEX;
    private float crtBrightnessGain = CRT_BRIGHTNESS_LEVELS[DEFAULT_BRIGHTNESS_INDEX];
    private int aspectModeIndex = 0;
    private int masterVolumeIndex = DEFAULT_MASTER_VOLUME_INDEX;
    private int musicVolumeIndex = DEFAULT_MUSIC_VOLUME_INDEX;
    private int sfxVolumeIndex = DEFAULT_SFX_VOLUME_INDEX;
    private boolean openingStaticSoundPlayed;
    private boolean openingStaticSoundFadeStarted;
    private Clip openingStaticSoundClip;
    private volatile boolean controllerPrewarmStarted;
    private volatile boolean controllerPrewarmFinished;

    public GamePanel() {
        setPreferredSize(new Dimension(GameConfig.WIDTH, GameConfig.HEIGHT));
        setBackground(BG);
        setFocusable(true);
        loadArrowSprites();
        loadSequenceSprites();
        loadHeartSprites();
        loadTransitionSprites();
        loadMenuSprites();
        setupMovementDispatcher();
        setupKeyBindings();
        applyAudioVolumes();
        openingSequenceStartMs = System.currentTimeMillis();
        updateBackgroundMusic();
        AudioManager.playSfx("intro_sound.wav");
        timer = new Timer(16, this); // ~60 FPS
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        int panelWidth = getWidth();
        int panelHeight = getHeight();

        g2d.setColor(BG);
        g2d.fillRect(0, 0, panelWidth, panelHeight);

        int gameWidth = GameConfig.WIDTH;
        int gameHeight = GameConfig.HEIGHT;
        boolean fillScreen = aspectModeIndex == 1;
        double scale = Math.min(panelWidth / (double) gameWidth, panelHeight / (double) gameHeight);

        int renderWidth = fillScreen ? panelWidth : (int) Math.round(gameWidth * scale);
        int renderHeight = fillScreen ? panelHeight : (int) Math.round(gameHeight * scale);
        int renderX = fillScreen ? 0 : (panelWidth - renderWidth) / 2;
        int renderY = fillScreen ? 0 : (panelHeight - renderHeight) / 2;

        if (!fillScreen) {
            drawLetterboxFrame(g2d, panelWidth, panelHeight, renderX, renderY, renderWidth, renderHeight);
        }

        Graphics2D gameG = (Graphics2D) g2d.create(renderX, renderY, renderWidth, renderHeight);
        gameG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        gameG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        gameG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        if (sceneBuffer == null || sceneBuffer.getWidth() != sceneBufferWidth || sceneBuffer.getHeight() != sceneBufferHeight) {
            sceneBuffer = new BufferedImage(sceneBufferWidth, sceneBufferHeight, BufferedImage.TYPE_INT_ARGB);
        }

        Graphics2D sceneG = sceneBuffer.createGraphics();
        sceneG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        sceneG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        sceneG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        sceneG.setColor(BG);
        sceneG.fillRect(0, 0, sceneBufferWidth, sceneBufferHeight);
        sceneG.scale(sceneBufferWidth / (double) gameWidth, sceneBufferHeight / (double) gameHeight);
        renderGameScene(sceneG);
        sceneG.dispose();

        crtDisplay.ensureGeometry(renderWidth, renderHeight);
        crtDisplay.drawCurvedScreenImage(gameG, sceneBuffer, renderWidth, renderHeight, crtBleedEnabled, crtBrightnessGain);
        crtDisplay.applyOverlay(gameG, renderWidth, renderHeight, crtScanlinesEnabled);
        crtDisplay.drawMatte(gameG, renderWidth, renderHeight);

        gameG.dispose();
        g2d.dispose();
    }

    private void renderGameScene(Graphics2D gameG) {
        int gameWidth = GameConfig.WIDTH;
        int gameHeight = GameConfig.HEIGHT;

        gameG.setColor(BG);
        gameG.fillRect(0, 0, gameWidth, gameHeight);
        backdropEffects.drawBackdrop(gameG, screen, game.model.TimerStyle.BACKDROP_HUE, getEncounterTimerProgress());

        if (screen == ScreenState.OPENING) {
            drawOpeningSplash(gameG);
            return;
        }
        if (screen == ScreenState.MENU) {
            drawMenu(gameG);
            if (startRunTransitionActive) {
                drawStartRunTransitionOverlay(gameG);
            }
            if (menuTransitionActive) {
                drawMenuTransitionOverlay(gameG);
            }
            return;
        }
        if (screen == ScreenState.SETTINGS) {
            drawSettingsMenu(gameG);
            if (startRunTransitionActive) {
                drawStartRunTransitionOverlay(gameG);
            }
            return;
        }

        drawHeartHud(gameG);

        if (screen == ScreenState.DUNGEON || screen == ScreenState.SHOP) {
            drawDungeon(gameG);
            if (screen == ScreenState.SHOP) {
                drawShopOverlay(gameG);
            }
            if (encounterBestedTransitionActive) {
                drawEncounterBestedTransition(gameG);
            }
        } else {
            drawArena(gameG);
            if (roundManager.getActiveArchetype().isRhythmMode()) {
                drawRhythmEncounter(gameG);
            } else {
                drawSequence(gameG);
            }
        }

        if (screen == ScreenState.LOST) {
            drawLossOverlay(gameG);
        }
        if (encounterTransitionActive) {
            drawEncounterTransition(gameG);
        } else if (encounterIntroActive) {
            drawEncounterIntro(gameG);
        } else if (roomTransitionActive) {
            drawRoomTransition(gameG);
        } else if (roomIntroActive) {
            drawRoomIntro(gameG);
        }
        if (runStartFadeInActive) {
            drawRunStartFadeIn(gameG);
        }
        if (startRunTransitionActive) {
            drawStartRunTransitionOverlay(gameG);
        }
        if (menuTransitionActive) {
            drawMenuTransitionOverlay(gameG);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double deltaSeconds = (now - lastTickNanos) / 1_000_000_000.0;
        lastTickNanos = now;

        // Prevent giant movement jumps after focus loss or window stalls.
        deltaSeconds = Math.min(deltaSeconds, 0.05);

        if (screen == ScreenState.OPENING) {
            updateOpeningSequence();
        } else if (menuTransitionActive) {
            pollControllerInput();
            updateMenuTransition();
        } else {
            pollControllerInput();
            if (screen == ScreenState.DUNGEON
                    && !encounterTransitionActive
                    && !encounterBestedTransitionActive
                    && !roomTransitionActive
                    && !roomIntroActive) {
                updateDungeonMovement(deltaSeconds);
            }
            if (encounterTransitionActive) {
                long elapsedMs = System.currentTimeMillis() - encounterTransitionStartMs;
                if (elapsedMs >= ENCOUNTER_TRANSITION_MS + ENCOUNTER_TRANSITION_HOLD_MS) {
                    encounterTransitionActive = false;
                    activeEncounterIndex = pendingEncounterIndex;
                    pendingEncounterIndex = -1;
                    long encounterBonusMs = nextEncounterTimeBonusMs;
                    nextEncounterTimeBonusMs = 0L;
                    roundManager.startGame(false, encounterBonusMs);
                    resetTimerBarAnimation();
                    screen = ScreenState.ENCOUNTER;
                    encounterIntroActive = true;
                    encounterIntroStartMs = System.currentTimeMillis();
                }
            }
            if (encounterBestedTransitionActive) {
                long elapsedMs = System.currentTimeMillis() - encounterBestedTransitionStartMs;
                if (elapsedMs >= ENCOUNTER_TRANSITION_MS + ENCOUNTER_TRANSITION_HOLD_MS + ENCOUNTER_INTRO_MS) {
                    encounterBestedTransitionActive = false;
                }
            }
            if (roomTransitionActive) {
                long elapsedMs = System.currentTimeMillis() - roomTransitionStartMs;
                if (elapsedMs >= ROOM_TRANSITION_MS + ROOM_TRANSITION_HOLD_MS) {
                    roomTransitionActive = false;
                    completeRoomTransition();
                    roomIntroActive = true;
                    roomIntroStartMs = System.currentTimeMillis();
                }
            }
            if (encounterIntroActive) {
                long introElapsedMs = System.currentTimeMillis() - encounterIntroStartMs;
                if (introElapsedMs >= ENCOUNTER_INTRO_MS) {
                    encounterIntroActive = false;
                }
            }
            if (roomIntroActive) {
                long introElapsedMs = System.currentTimeMillis() - roomIntroStartMs;
                if (introElapsedMs >= ROOM_INTRO_MS) {
                    roomIntroActive = false;
                    roomIntroDirection = null;
                }
            }
            if (screen == ScreenState.ENCOUNTER && !encounterIntroActive && !timeoutRecoveryActive) {
                updateEncounterHealthDrain(deltaSeconds);
            }
            if (screen == ScreenState.ENCOUNTER && !encounterIntroActive && roundManager.hasTimedOut()) {
                handleEncounterTimeout();
            }
            if (!timeoutRecoveryActive) {
                updateItemEffects(deltaSeconds);
            }
        }
        if (runStartFadeInActive) {
            long fadeElapsedMs = System.currentTimeMillis() - runStartFadeInStartMs;
            if (fadeElapsedMs >= RUN_START_FADE_IN_MS) {
                runStartFadeInActive = false;
            }
        }
        if (startRunTransitionActive) {
            updateStartRunTransition();
        }
        updateMenuHoverAnimation(deltaSeconds);
        updateSettingsRevealAnimation(deltaSeconds);
        updateEncounterMusicMix(deltaSeconds);
        updateShopMusicFade(deltaSeconds);
        updateTimerBarAnimation(deltaSeconds);
        updatePlayerHealthAnimation(deltaSeconds);
        updateEnemyHealthAnimation(deltaSeconds);
        updateBackgroundMusic();
        backdropEffects.update();
        updateEnemyKillEffects();
        repaint();
    }

    private void drawMenu(Graphics2D g2d) {
        int menuLeft = 70;
        int baseMenuStartY = GameConfig.HEIGHT - 150;
        int menuLift = (int) Math.round(300 * easeInOut(settingsRevealProgress));
        int menuStartY = baseMenuStartY - menuLift;
        int menuLineStep = 42;

        g2d.setFont(TITLE_FONT);
        drawGlowingString(g2d, "S3QUENCE", menuLeft, menuStartY - 110, WHITE, GLOW_CYAN);

        drawMenuOption(g2d, MENU_ITEM_START, "START GAME", menuLeft, menuStartY);
        drawMenuOption(
                g2d,
                MENU_ITEM_TEST_ENEMY,
                "TEST ENEMY: " + getTestEnemyMenuLabel(),
                menuLeft,
                menuStartY + menuLineStep
        );
        drawMenuOption(
                g2d,
                MENU_ITEM_TEST_ITEM,
                "TEST ITEM: " + getTestItemMenuLabel(),
                menuLeft,
                menuStartY + (menuLineStep * 2)
        );
        drawMenuOption(g2d, MENU_ITEM_SETTINGS, "SETTINGS", menuLeft, menuStartY + (menuLineStep * 3));
    }

    private void drawOpeningSplash(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        long elapsedMs = Math.max(0L, System.currentTimeMillis() - openingSequenceStartMs);
        if (elapsedMs >= OPENING_TEXT_SEQUENCE_MS) {
            drawOpeningStatic(g2d);
            return;
        }
        if (openingTextSprite == null) {
            return;
        }

        float alpha = 1.0f;
        if (elapsedMs < OPENING_FADE_IN_MS) {
            alpha = (float) (elapsedMs / (double) OPENING_FADE_IN_MS);
        } else if (elapsedMs > OPENING_TEXT_SEQUENCE_MS - OPENING_FADE_OUT_MS) {
            long fadeOutElapsedMs = elapsedMs - (OPENING_TEXT_SEQUENCE_MS - OPENING_FADE_OUT_MS);
            alpha = (float) (1.0 - (fadeOutElapsedMs / (double) OPENING_FADE_OUT_MS));
        }
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));

        double scale = 0.85;
        int spriteW = (int) Math.round(openingTextSprite.getWidth() * scale);
        int spriteH = (int) Math.round(openingTextSprite.getHeight() * scale);
        int x = (GameConfig.WIDTH - spriteW) / 2;
        int y = (GameConfig.HEIGHT - spriteH) / 2;

        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.drawImage(openingTextSprite, x, y, spriteW, spriteH, null);
        g2d.setComposite(oldComposite);
    }

    private void drawOpeningStatic(Graphics2D g2d) {
        if (openingStaticGif == null) {
            return;
        }
        g2d.drawImage(openingStaticGif, 0, 0, GameConfig.WIDTH, GameConfig.HEIGHT, this);
    }

    private void drawSettingsMenu(Graphics2D g2d) {
        int menuLeft = 70;
        int menuLineStep = 42;
        int baseMenuStartY = GameConfig.HEIGHT - 150;
        double eased = easeInOut(settingsRevealProgress);
        int menuLift = (int) Math.round(300 * eased);
        int menuStartY = baseMenuStartY - menuLift;

        g2d.setFont(TITLE_FONT);
        drawGlowingString(g2d, "S3QUENCE", menuLeft, menuStartY - 110, WHITE, GLOW_CYAN);

        drawMenuOption(g2d, MENU_ITEM_START, "START GAME", menuLeft, menuStartY);
        drawMenuOption(
                g2d,
                MENU_ITEM_TEST_ENEMY,
                "TEST ENEMY: " + getTestEnemyMenuLabel(),
                menuLeft,
                menuStartY + menuLineStep
        );
        drawMenuOption(
                g2d,
                MENU_ITEM_TEST_ITEM,
                "TEST ITEM: " + getTestItemMenuLabel(),
                menuLeft,
                menuStartY + (menuLineStep * 2)
        );
        drawMenuOption(g2d, MENU_ITEM_SETTINGS, "SETTINGS", menuLeft, menuStartY + (menuLineStep * 3));

        int settingsLeft = menuLeft;
        int settingsStartY = menuStartY + (menuLineStep * 4) + 30;
        int settingsLineStep = 44;
        int revealOffset = (int) Math.round(28 * (1.0 - eased));
        float alpha = (float) Math.max(0.0, Math.min(1.0, eased));
        int lineY = settingsStartY + revealOffset;

        drawSettingsOption(g2d, SETTINGS_ITEM_TAB, "TAB", getSettingsTabLabel(), settingsLeft, lineY, alpha);
        lineY += settingsLineStep;
        if (settingsTabIndex == SETTINGS_TAB_VIDEO) {
            drawSettingsOption(g2d, SETTINGS_VIDEO_RENDER_QUALITY, "RENDER QUALITY", getRenderQualityLabel(), settingsLeft, lineY, alpha);
            lineY += settingsLineStep;
            drawSettingsBrightness(g2d, SETTINGS_VIDEO_CRT_BRIGHTNESS, settingsLeft, lineY, alpha);
            lineY += settingsLineStep + 20;
            drawSettingsOption(g2d, SETTINGS_VIDEO_ASPECT, "ASPECT RATIO", getAspectLabel(), settingsLeft, lineY, alpha);
            lineY += settingsLineStep;
            drawSettingsOption(g2d, SETTINGS_VIDEO_BACK, "BACK", "", settingsLeft, lineY, alpha);
        } else {
            drawSettingsVolume(g2d, SETTINGS_AUDIO_MASTER, "MASTER VOLUME", masterVolumeIndex, settingsLeft, lineY, alpha);
            lineY += settingsLineStep + 20;
            drawSettingsVolume(g2d, SETTINGS_AUDIO_MUSIC, "MUSIC VOLUME", musicVolumeIndex, settingsLeft, lineY, alpha);
            lineY += settingsLineStep + 20;
            drawSettingsVolume(g2d, SETTINGS_AUDIO_SFX, "SFX VOLUME", sfxVolumeIndex, settingsLeft, lineY, alpha);
            lineY += settingsLineStep + 20;
            drawSettingsOption(g2d, SETTINGS_AUDIO_BACK, "BACK", "", settingsLeft, lineY, alpha);
        }
    }

    private void drawSettingsOption(
            Graphics2D g2d,
            int optionIndex,
            String label,
            String value,
            int leftX,
            int baselineY,
            float alpha
    ) {
        boolean selected = settingsSelectionIndex == optionIndex;
        String display = value == null || value.isBlank() ? label : label + ": " + value;
        g2d.setFont(BODY_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int paddingX = 16;
        int paddingY = 8;
        int minWidth = 320;
        int textWidth = metrics.stringWidth(display);
        int textHeight = metrics.getAscent();
        int rectW = Math.max(minWidth, textWidth + (paddingX * 2));
        int rectH = textHeight + metrics.getDescent() + (paddingY * 2);
        int rectX = leftX;
        int rectY = baselineY - textHeight - paddingY;

        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(new Color(62, 124, 220, 140));
        g2d.fillRect(rectX, rectY, rectW, rectH);
        g2d.setColor(selected ? new Color(120, 200, 255, 200) : new Color(92, 162, 240, 160));
        if (rectW > 4 && rectH > 4) {
            g2d.fillRect(rectX + 2, rectY + 2, rectW - 4, rectH - 4);
        }

        int textX = rectX + paddingX;
        if (selected) {
            drawGlowingString(g2d, display, textX, baselineY, YELLOW, GLOW_CYAN);
        } else {
            g2d.setColor(WHITE);
            g2d.drawString(display, textX, baselineY);
        }
        g2d.setComposite(oldComposite);
    }

    private void drawMenuOption(Graphics2D g2d, int optionIndex, String label, int leftX, int baselineY) {
        if (optionIndex == MENU_ITEM_START && startMenuSprite != null) {
            drawMenuStartSprite(g2d, leftX, baselineY);
            return;
        }
        boolean selected = menuSelectionIndex == optionIndex;
        g2d.setFont(BODY_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = metrics.stringWidth(label);
        int textHeight = metrics.getAscent();
        int paddingX = 16;
        int paddingY = 8;
        int rectX = leftX;
        int rectY = baselineY - textHeight - paddingY;
        int rectW = textWidth + (paddingX * 2);
        int rectH = textHeight + metrics.getDescent() + (paddingY * 2);

        g2d.setColor(new Color(62, 124, 220, 140));
        g2d.fillRect(rectX, rectY, rectW, rectH);
        g2d.setColor(selected ? new Color(120, 200, 255, 200) : new Color(92, 162, 240, 160));
        if (rectW > 4 && rectH > 4) {
            g2d.fillRect(rectX + 2, rectY + 2, rectW - 4, rectH - 4);
        }

        int textX = rectX + paddingX;
        if (selected) {
            drawGlowingString(g2d, label, textX, baselineY, YELLOW, GLOW_CYAN);
        } else {
            g2d.setColor(WHITE);
            g2d.drawString(label, textX, baselineY);
        }
    }

    private void drawMenuStartSprite(Graphics2D g2d, int leftX, int baselineY) {
        BufferedImage sprite = startMenuSprite;
        if (sprite == null) {
            return;
        }
        double eased = easeOutCubic(menuStartHoverProgress);
        double baseScale = 3.0;
        double hoverScale = 3.6;
        double scale = baseScale + ((hoverScale - baseScale) * eased);
        int scaledW = (int) Math.round(sprite.getWidth() * scale);
        int scaledH = (int) Math.round(sprite.getHeight() * scale);
        int spriteX = leftX;
        int lift = (int) Math.round(6 * eased);
        int spriteY = baselineY - scaledH + 6 - lift;
        Composite oldComposite = g2d.getComposite();
        float alpha = (float) (0.65 + (0.25 * eased));
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.drawImage(sprite, spriteX, spriteY, scaledW, scaledH, null);
        g2d.setComposite(oldComposite);
    }

    private void updateMenuHoverAnimation(double deltaSeconds) {
        double target = (screen == ScreenState.MENU && !menuTransitionActive && menuSelectionIndex == MENU_ITEM_START)
                ? 1.0
                : 0.0;
        double rate = target > menuStartHoverProgress ? 6.0 : 9.0;
        menuStartHoverProgress = moveTowards(menuStartHoverProgress, target, rate * deltaSeconds);
    }

    private void updateSettingsRevealAnimation(double deltaSeconds) {
        double target = screen == ScreenState.SETTINGS ? 1.0 : 0.0;
        double rate = target > settingsRevealProgress ? 3.5 : 5.5;
        settingsRevealProgress = moveTowards(settingsRevealProgress, target, rate * deltaSeconds);
    }

    private void updateEncounterMusicMix(double deltaSeconds) {
        boolean enteringEncounter = encounterTransitionActive || encounterIntroActive || screen == ScreenState.ENCOUNTER;
        double target = enteringEncounter ? 1.0 : 0.0;
        if (encounterTransitionActive) {
            double progress = (System.currentTimeMillis() - encounterTransitionStartMs) / (double) ENCOUNTER_TRANSITION_MS;
            progress = Math.max(0.0, Math.min(1.0, progress));
            encounterMusicMix = Math.max(encounterMusicMix, progress);
        }
        double rate = target > encounterMusicMix ? 0.7 : 0.9;
        encounterMusicMix = moveTowards(encounterMusicMix, target, rate * deltaSeconds);
    }

    private void updateShopMusicFade(double deltaSeconds) {
        double target = screen == ScreenState.SHOP ? 1.0 : 0.0;
        double rate = target > shopMusicFade ? 1.8 : 3.0;
        shopMusicFade = moveTowards(shopMusicFade, target, rate * deltaSeconds);
    }

    private double moveTowards(double current, double target, double maxDelta) {
        if (current < target) {
            return Math.min(target, current + maxDelta);
        }
        return Math.max(target, current - maxDelta);
    }

    private void pollControllerInput() {
        if (!controllerPrewarmFinished) {
            return;
        }
        ControllerInputManager.Snapshot snapshot = controllerInputManager.poll();
        setControllerMovementHeld(Direction.UP, snapshot.isConnected() && snapshot.isUpHeld());
        setControllerMovementHeld(Direction.DOWN, snapshot.isConnected() && snapshot.isDownHeld());
        setControllerMovementHeld(Direction.LEFT, snapshot.isConnected() && snapshot.isLeftHeld());
        setControllerMovementHeld(Direction.RIGHT, snapshot.isConnected() && snapshot.isRightHeld());

        if (snapshot.isUpPressed()) {
            processDirectionalInput(Direction.UP);
        }
        if (snapshot.isDownPressed()) {
            processDirectionalInput(Direction.DOWN);
        }
        if (snapshot.isLeftPressed()) {
            processDirectionalInput(Direction.LEFT);
        }
        if (snapshot.isRightPressed()) {
            processDirectionalInput(Direction.RIGHT);
        }
        if (snapshot.isConfirmPressed()) {
            handleConfirmAction();
        }
        if (snapshot.isBackPressed()) {
            handleBackAction();
        }
    }

    private void handleMenuDirection(Direction direction) {
        int previousIndex = menuSelectionIndex;
        if (direction == Direction.UP) {
            menuSelectionIndex = (menuSelectionIndex - 1 + MENU_ITEM_COUNT) % MENU_ITEM_COUNT;
        } else if (direction == Direction.DOWN) {
            menuSelectionIndex = (menuSelectionIndex + 1) % MENU_ITEM_COUNT;
        } else if (menuSelectionIndex == MENU_ITEM_TEST_ENEMY) {
            if (direction == Direction.LEFT) {
                cycleTestEnemy(-1);
                AudioManager.playSfx("main_toggle_tab.wav");
            } else if (direction == Direction.RIGHT) {
                cycleTestEnemy(1);
                AudioManager.playSfx("main_toggle_tab.wav");
            }
        } else if (menuSelectionIndex == MENU_ITEM_TEST_ITEM) {
            if (direction == Direction.LEFT) {
                cycleTestItem(-1);
                AudioManager.playSfx("main_toggle_tab.wav");
            } else if (direction == Direction.RIGHT) {
                cycleTestItem(1);
                AudioManager.playSfx("main_toggle_tab.wav");
            }
        }
        if (menuSelectionIndex != previousIndex) {
            AudioManager.playSfx("tab_switch.wav");
            controllerInputManager.rumble(MENU_NAV_RUMBLE_STRENGTH, MENU_NAV_RUMBLE_MS);
        }
    }

    private void processDirectionalInput(Direction direction) {
        if (screen == ScreenState.MENU && !menuTransitionActive && !startRunTransitionActive) {
            handleMenuDirection(direction);
            return;
        }
        if (screen == ScreenState.SETTINGS && !menuTransitionActive && !startRunTransitionActive) {
            handleSettingsDirection(direction);
            return;
        }
        if (screen == ScreenState.SHOP && !menuTransitionActive && !startRunTransitionActive) {
            handleShopDirection(direction);
            return;
        }
        if (screen == ScreenState.ENCOUNTER && !encounterIntroActive && !menuTransitionActive && !startRunTransitionActive) {
            if (timeoutRecoveryActive) {
                return;
            }
            List<Integer> sequence = roundManager.getSequence();
            int progressIndex = roundManager.getProgressIndex();
            boolean isLastInput = !sequence.isEmpty() && progressIndex >= sequence.size() - 1;
            if (!isLastInput) {
                AudioManager.playClickSfx();
            }
            backdropEffects.spawnInputRipple(
                    direction.ordinal(),
                    sequence,
                    progressIndex,
                    ARENA_X,
                    ENCOUNTER_ARENA_Y,
                    ARENA_W,
                    ARENA_H,
                    SEQUENCE_SYMBOL_SIZE,
                    SEQUENCE_SYMBOL_GAP
            );
            handleEncounterInput(direction.ordinal());
        }
    }

    private void handleConfirmAction() {
        if (menuTransitionActive || startRunTransitionActive) {
            return;
        }
        if (screen == ScreenState.MENU) {
            activateSelectedMenuItem();
        } else if (screen == ScreenState.SETTINGS) {
            activateSelectedSettingsItem();
        } else if (screen == ScreenState.SHOP) {
            purchaseSelectedShopItem();
        } else if (screen == ScreenState.LOST) {
            startRun();
        }
    }

    private void handleBackAction() {
        if (screen == ScreenState.SHOP) {
            closeShop();
        } else if (screen == ScreenState.SETTINGS) {
            AudioManager.playSfx("back_toggle.wav");
            screen = ScreenState.MENU;
        } else if (screen != ScreenState.MENU && !menuTransitionActive && !startRunTransitionActive) {
            startMenuTransition();
        }
    }

    private void activateSelectedMenuItem() {
        if (menuSelectionIndex == MENU_ITEM_START) {
            controllerInputManager.rumble(START_CONFIRM_RUMBLE_STRENGTH, START_CONFIRM_RUMBLE_MS);
            startRun();
        } else if (menuSelectionIndex == MENU_ITEM_TEST_ENEMY) {
            cycleTestEnemy(1);
            AudioManager.playSfx("main_toggle_tab.wav");
        } else if (menuSelectionIndex == MENU_ITEM_TEST_ITEM) {
            cycleTestItem(1);
            AudioManager.playSfx("main_toggle_tab.wav");
        } else if (menuSelectionIndex == MENU_ITEM_SETTINGS) {
            AudioManager.playSfx("main_toggle_tab.wav");
            settingsTabIndex = SETTINGS_TAB_VIDEO;
            settingsSelectionIndex = SETTINGS_VIDEO_RENDER_QUALITY;
            screen = ScreenState.SETTINGS;
        }
    }

    private void handleSettingsDirection(Direction direction) {
        if (direction == Direction.UP) {
            int previousIndex = settingsSelectionIndex;
            settingsSelectionIndex = (settingsSelectionIndex - 1 + getSettingsItemCount()) % getSettingsItemCount();
            if (settingsSelectionIndex != previousIndex) {
                AudioManager.playSfx("tab_switch.wav");
                controllerInputManager.rumble(MENU_NAV_RUMBLE_STRENGTH, MENU_NAV_RUMBLE_MS);
            }
            return;
        }
        if (direction == Direction.DOWN) {
            int previousIndex = settingsSelectionIndex;
            settingsSelectionIndex = (settingsSelectionIndex + 1) % getSettingsItemCount();
            if (settingsSelectionIndex != previousIndex) {
                AudioManager.playSfx("tab_switch.wav");
                controllerInputManager.rumble(MENU_NAV_RUMBLE_STRENGTH, MENU_NAV_RUMBLE_MS);
            }
            return;
        }

        int delta = direction == Direction.LEFT ? -1 : direction == Direction.RIGHT ? 1 : 0;
        if (delta == 0) {
            return;
        }

        if (settingsSelectionIndex == SETTINGS_ITEM_TAB) {
            switchSettingsTab(delta);
            return;
        }

        if (settingsTabIndex == SETTINGS_TAB_VIDEO) {
            handleVideoSettingsDelta(delta);
        } else {
            handleAudioSettingsDelta(delta);
        }
    }

    private void activateSelectedSettingsItem() {
        if (settingsSelectionIndex == SETTINGS_ITEM_TAB) {
            switchSettingsTab(1);
            return;
        }

        if (isSettingsBackSelected()) {
            AudioManager.playSfx("back_toggle.wav");
            screen = ScreenState.MENU;
            return;
        }

        if (settingsTabIndex == SETTINGS_TAB_VIDEO) {
            handleVideoSettingsDelta(1);
        } else {
            handleAudioSettingsDelta(1);
        }
    }

    private void handleShopDirection(Direction direction) {
        if (direction == Direction.UP) {
            shopSelectionIndex = (shopSelectionIndex - 1 + SHOP_ITEM_COUNT) % SHOP_ITEM_COUNT;
        } else if (direction == Direction.DOWN) {
            shopSelectionIndex = (shopSelectionIndex + 1) % SHOP_ITEM_COUNT;
        } else {
            return;
        }
        controllerInputManager.rumble(MENU_NAV_RUMBLE_STRENGTH, MENU_NAV_RUMBLE_MS);
    }

    private void purchaseSelectedShopItem() {
        ShopOption option = ShopOption.values()[shopSelectionIndex];
        if (coinCount < option.getCost()) {
            return;
        }

        boolean purchased = false;
        if (option == ShopOption.HEAL && playerHealth < GameConfig.PLAYER_MAX_HEALTH) {
            playerHealth = Math.min(GameConfig.PLAYER_MAX_HEALTH, playerHealth + GameConfig.SHOP_HEAL_AMOUNT);
            purchased = true;
        } else if (option == ShopOption.SHIELD) {
            mistakeGuardCharges++;
            purchased = true;
        } else if (option == ShopOption.TIMER) {
            nextEncounterTimeBonusMs += GameConfig.SHOP_TIMER_BONUS_MS;
            purchased = true;
        }

        if (!purchased) {
            return;
        }

        coinCount -= option.getCost();
        lastCoinGain = -option.getCost();
        lastCoinGainUntilMs = System.currentTimeMillis() + 760L;
        AudioManager.playSfx("bar_fill.wav");
    }

    private void cycleTestEnemy(int delta) {
        EnemyArchetype[] archetypes = EnemyArchetype.values();
        int currentIndex = forcedTestEnemy == null ? -1 : forcedTestEnemy.ordinal();
        int nextIndex = currentIndex + delta;
        if (nextIndex < -1) {
            nextIndex = archetypes.length - 1;
        } else if (nextIndex >= archetypes.length) {
            nextIndex = -1;
        }
        forcedTestEnemy = nextIndex == -1 ? null : archetypes[nextIndex];
    }

    private String getTestEnemyMenuLabel() {
        return forcedTestEnemy == null ? "OFF" : forcedTestEnemy.getLabel();
    }

    private void cycleTestItem(int delta) {
        ItemArchetype[] items = ItemArchetype.values();
        int combinationCount = 1 << items.length;
        int nextMask = forcedTestItemMask + delta;
        if (nextMask < 0) {
            nextMask = combinationCount - 1;
        } else if (nextMask >= combinationCount) {
            nextMask = 0;
        }
        forcedTestItemMask = nextMask;
        clearActiveItemEffects();
    }

    private String getTestItemMenuLabel() {
        if (forcedTestItemMask == 0) {
            return "OFF";
        }

        StringBuilder label = new StringBuilder();
        for (ItemArchetype item : ItemArchetype.values()) {
            if (!hasTestItem(item)) {
                continue;
            }
            if (label.length() > 0) {
                label.append(" + ");
            }
            label.append(item.getLabel());
        }
        return label.toString();
    }

    private boolean hasTestItem(ItemArchetype item) {
        return item != null && (forcedTestItemMask & (1 << item.ordinal())) != 0;
    }

    private List<ItemArchetype> getEnabledTestItems() {
        List<ItemArchetype> enabled = new ArrayList<>();
        for (ItemArchetype item : ItemArchetype.values()) {
            if (hasTestItem(item)) {
                enabled.add(item);
            }
        }
        return enabled;
    }

    private String getRenderQualityLabel() {
        return RENDER_QUALITY_LABELS[renderQualityIndex];
    }

    private String getAspectLabel() {
        return ASPECT_MODE_LABELS[aspectModeIndex];
    }

    private String getEncounterMusicFile(EnemyArchetype archetype) {
        if (archetype == null) {
            return ENCOUNTER_MUSIC_FILES[0];
        }
        switch (archetype) {
            case BERSERKER:
                return ENCOUNTER_MUSIC_FILES[1];
            case ECHO:
                return ECHO_MUSIC_FILES[random.nextInt(ECHO_MUSIC_FILES.length)];
            case REVERSE:
                return ENCOUNTER_MUSIC_FILES[2];
            case NORMAL:
            default:
                return ENCOUNTER_MUSIC_FILES[0];
        }
    }

    private void drawSettingsBrightness(Graphics2D g2d, int optionIndex, int leftX, int baselineY, float alpha) {
        boolean selected = settingsSelectionIndex == optionIndex;
        int minBarWidth = 300;
        int barHeight = 10;
        int paddingX = 16;
        int paddingY = 8;
        String labelText = "BRIGHTNESS";

        g2d.setFont(BODY_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = metrics.stringWidth(labelText);
        int textHeight = metrics.getAscent();
        int rectW = Math.max(minBarWidth + (paddingX * 2), textWidth + (paddingX * 2));
        int rectH = textHeight + metrics.getDescent() + (paddingY * 2);
        int rectX = leftX;
        int rectY = baselineY - textHeight - paddingY;
        int barWidth = rectW - (paddingX * 2);
        int barX = rectX + paddingX;
        int barY = rectY + rectH + 8;

        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(new Color(62, 124, 220, 140));
        g2d.fillRect(rectX, rectY, rectW, rectH);
        g2d.setColor(selected ? new Color(120, 200, 255, 200) : new Color(92, 162, 240, 160));
        if (rectW > 4 && rectH > 4) {
            g2d.fillRect(rectX + 2, rectY + 2, rectW - 4, rectH - 4);
        }

        int textX = rectX + paddingX;
        if (selected) {
            drawGlowingString(g2d, labelText, textX, baselineY, YELLOW, GLOW_CYAN);
        } else {
            g2d.setColor(WHITE);
            g2d.drawString(labelText, textX, baselineY);
        }

        g2d.setColor(new Color(12, 26, 48, 200));
        g2d.fillRect(barX, barY, barWidth, barHeight);
        g2d.setColor(new Color(160, 220, 255, 120));
        g2d.drawRect(barX, barY, barWidth, barHeight);

        int segments = CRT_BRIGHTNESS_LEVELS.length;
        int gap = 4;
        int segmentWidth = (barWidth - (gap * (segments - 1))) / segments;
        for (int i = 0; i < segments; i++) {
            int x = barX + (i * (segmentWidth + gap));
            if (i <= crtBrightnessIndex) {
                g2d.setColor(new Color(110, 240, 255, 200));
                g2d.fillRect(x, barY + 2, segmentWidth, barHeight - 3);
            } else {
                g2d.setColor(new Color(70, 120, 170, 120));
                g2d.fillRect(x, barY + 2, segmentWidth, barHeight - 3);
            }
        }
        g2d.setComposite(oldComposite);
    }

    private void drawSettingsVolume(
            Graphics2D g2d,
            int optionIndex,
            String label,
            int volumeIndex,
            int leftX,
            int baselineY,
            float alpha
    ) {
        boolean selected = settingsSelectionIndex == optionIndex;
        int minBarWidth = 300;
        int barHeight = 10;
        int paddingX = 16;
        int paddingY = 8;
        String labelText = label + ": " + getVolumePercentLabel(volumeIndex);

        g2d.setFont(BODY_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = metrics.stringWidth(labelText);
        int textHeight = metrics.getAscent();
        int rectW = Math.max(minBarWidth + (paddingX * 2), textWidth + (paddingX * 2));
        int rectH = textHeight + metrics.getDescent() + (paddingY * 2);
        int rectX = leftX;
        int rectY = baselineY - textHeight - paddingY;
        int barWidth = rectW - (paddingX * 2);
        int barX = rectX + paddingX;
        int barY = rectY + rectH + 8;

        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(new Color(62, 124, 220, 140));
        g2d.fillRect(rectX, rectY, rectW, rectH);
        g2d.setColor(selected ? new Color(120, 200, 255, 200) : new Color(92, 162, 240, 160));
        if (rectW > 4 && rectH > 4) {
            g2d.fillRect(rectX + 2, rectY + 2, rectW - 4, rectH - 4);
        }

        int textX = rectX + paddingX;
        if (selected) {
            drawGlowingString(g2d, labelText, textX, baselineY, YELLOW, GLOW_CYAN);
        } else {
            g2d.setColor(WHITE);
            g2d.drawString(labelText, textX, baselineY);
        }

        g2d.setColor(new Color(12, 26, 48, 200));
        g2d.fillRect(barX, barY, barWidth, barHeight);
        g2d.setColor(new Color(160, 220, 255, 120));
        g2d.drawRect(barX, barY, barWidth, barHeight);

        int segments = VOLUME_LEVELS.length;
        int gap = 4;
        int segmentWidth = (barWidth - (gap * (segments - 1))) / segments;
        for (int i = 0; i < segments; i++) {
            int x = barX + (i * (segmentWidth + gap));
            if (i <= volumeIndex) {
                g2d.setColor(new Color(110, 240, 255, 200));
                g2d.fillRect(x, barY + 2, segmentWidth, barHeight - 3);
            } else {
                g2d.setColor(new Color(70, 120, 170, 120));
                g2d.fillRect(x, barY + 2, segmentWidth, barHeight - 3);
            }
        }
        g2d.setComposite(oldComposite);
    }

    private void applyRenderQuality() {
        sceneBufferWidth = RENDER_QUALITY_WIDTHS[renderQualityIndex];
        sceneBufferHeight = RENDER_QUALITY_HEIGHTS[renderQualityIndex];
        sceneBuffer = null;
    }

    private void applyAudioVolumes() {
        AudioManager.setMasterVolume(getVolumeLevel(masterVolumeIndex));
        AudioManager.setMusicVolume(getVolumeLevel(musicVolumeIndex));
        AudioManager.setSfxVolume(getVolumeLevel(sfxVolumeIndex));
    }

    private void handleVideoSettingsDelta(int delta) {
        if (settingsSelectionIndex == SETTINGS_VIDEO_RENDER_QUALITY) {
            renderQualityIndex = wrapIndex(renderQualityIndex + delta, RENDER_QUALITY_LABELS.length);
            applyRenderQuality();
            AudioManager.playSfx("toggle_tab.wav");
        } else if (settingsSelectionIndex == SETTINGS_VIDEO_CRT_BRIGHTNESS) {
            crtBrightnessIndex = wrapIndex(crtBrightnessIndex + delta, CRT_BRIGHTNESS_LEVELS.length);
            crtBrightnessGain = CRT_BRIGHTNESS_LEVELS[crtBrightnessIndex];
        } else if (settingsSelectionIndex == SETTINGS_VIDEO_ASPECT) {
            aspectModeIndex = wrapIndex(aspectModeIndex + delta, ASPECT_MODE_LABELS.length);
            AudioManager.playSfx("toggle_tab.wav");
        }
    }

    private void handleAudioSettingsDelta(int delta) {
        if (settingsSelectionIndex == SETTINGS_AUDIO_MASTER) {
            masterVolumeIndex = wrapIndex(masterVolumeIndex + delta, VOLUME_LEVELS.length);
            applyAudioVolumes();
        } else if (settingsSelectionIndex == SETTINGS_AUDIO_MUSIC) {
            musicVolumeIndex = wrapIndex(musicVolumeIndex + delta, VOLUME_LEVELS.length);
            applyAudioVolumes();
        } else if (settingsSelectionIndex == SETTINGS_AUDIO_SFX) {
            sfxVolumeIndex = wrapIndex(sfxVolumeIndex + delta, VOLUME_LEVELS.length);
            applyAudioVolumes();
        }
    }

    private void switchSettingsTab(int delta) {
        settingsTabIndex = wrapIndex(settingsTabIndex + delta, 2);
        int maxIndex = getSettingsItemCount() - 1;
        if (settingsSelectionIndex > maxIndex) {
            settingsSelectionIndex = maxIndex;
        }
        AudioManager.playSfx("toggle_tab.wav");
    }

    private int getSettingsItemCount() {
        return settingsTabIndex == SETTINGS_TAB_VIDEO ? SETTINGS_VIDEO_ITEM_COUNT : SETTINGS_AUDIO_ITEM_COUNT;
    }

    private boolean isSettingsBackSelected() {
        if (settingsTabIndex == SETTINGS_TAB_VIDEO) {
            return settingsSelectionIndex == SETTINGS_VIDEO_BACK;
        }
        return settingsSelectionIndex == SETTINGS_AUDIO_BACK;
    }

    private float getVolumeLevel(int volumeIndex) {
        int idx = Math.max(0, Math.min(VOLUME_LEVELS.length - 1, volumeIndex));
        return VOLUME_LEVELS[idx];
    }

    private String getVolumePercentLabel(int volumeIndex) {
        int percent = Math.round(getVolumeLevel(volumeIndex) * 100.0f);
        return percent + "%";
    }

    private String getSettingsTabLabel() {
        if (settingsTabIndex == SETTINGS_TAB_VIDEO) {
            return "[VIDEO] | AUDIO";
        }
        return "VIDEO | [AUDIO]";
    }

    private int wrapIndex(int value, int size) {
        int idx = value % size;
        return idx < 0 ? idx + size : idx;
    }

    private void drawDungeon(Graphics2D g2d) {
        g2d.setColor(ARENA_GLASS);
        g2d.fillRect(ARENA_X + 2, ARENA_Y + 2, ARENA_W - 3, ARENA_H - 3);
        g2d.setColor(ROOM_GLASS);
        g2d.fillRect(ROOM_X + 2, ROOM_Y + 2, ROOM_W - 3, ROOM_H - 3);
        drawFrame(g2d, ARENA_X, ARENA_Y, ARENA_W, ARENA_H, 4, WHITE);
        drawFrame(g2d, ROOM_X, ROOM_Y, ROOM_W, ROOM_H, 2, WHITE);

        g2d.setFont(SMALL_FONT);
        g2d.setColor(TEXT_DIM);
        drawCenteredString(g2d, "DUNGEON ROOM " + roomNumber, GameConfig.WIDTH / 2, ARENA_Y + 28);

        for (EncounterNode node : roomEncounters) {
            if (node.isEncounter() && node.isCleared()) {
                continue;
            }
            g2d.setColor(node.isShop() ? YELLOW : RED);
            g2d.fillRect(node.getX(), node.getY(), ENCOUNTER_SIZE, ENCOUNTER_SIZE);
            g2d.setColor(WHITE);
            g2d.drawRect(node.getX(), node.getY(), ENCOUNTER_SIZE, ENCOUNTER_SIZE);
        }
        drawEnemyKillEffects(g2d);

        Rectangle door = getDoorRect();
        if (allEncountersCleared()) {
            g2d.setColor(GREEN);
        } else {
            g2d.setColor(new Color(34, 74, 128));
        }
        g2d.fillRect(door.x, door.y, door.width, door.height);
        g2d.setColor(WHITE);
        g2d.drawRect(door.x, door.y, door.width, door.height);

        drawSoul(g2d, (int) Math.round(playerX), (int) Math.round(playerY), PLAYER_SIZE, YELLOW);

        g2d.setFont(SMALL_FONT);
        g2d.setColor(TEXT_DIM);
        drawCenteredString(g2d, "RED = FIGHT   CYAN = SHOP", GameConfig.WIDTH / 2, ARENA_Y + ARENA_H + 34);
    }

    private void drawEnemyKillEffects(Graphics2D g2d) {
        enemyKillEffects.draw(g2d, ROOM_X, ROOM_Y, ROOM_W, ROOM_H);
    }

    private void drawArena(Graphics2D g2d) {
        int encounterArenaY = ENCOUNTER_ARENA_Y;
        drawEncounterEnemyBar(g2d, encounterArenaY);
    }

    private void drawEncounterEnemyBar(Graphics2D g2d, int encounterArenaY) {
        EncounterEnemy enemy = getActiveEncounterEnemy();
        if (enemy == null) {
            return;
        }
        int enemyBarY = encounterArenaY + 400;

        g2d.setFont(SMALL_FONT);
        g2d.setColor(new Color(255, 186, 230, 170));
        drawCenteredString(
                g2d,
                enemy.getArchetype().getLabel() + "  |  " + enemy.getArchetype().getRuleLabel(),
                GameConfig.WIDTH / 2,
                enemyBarY - 10
        );

        //enemy health bar
        g2d.setColor(new Color(255, 116, 208, 50));
        g2d.fillRect(ENEMY_BAR_X, enemyBarY, ENEMY_BAR_W, ENEMY_BAR_H);

        int displayedHealth = displayedEnemyHealth >= 0 ? displayedEnemyHealth : enemy.getHealth();
        double ratio = Math.max(0.0, Math.min(1.0, displayedHealth / (double) enemy.getMaxHealth()));
        int fillWidth = (int) Math.round((ENEMY_BAR_W - 4) * ratio);
        if (fillWidth > 0) {
            g2d.setColor(new Color(255, 92, 198, 78));
            g2d.fillRect(ENEMY_BAR_X + 2, enemyBarY + 2, fillWidth, ENEMY_BAR_H - 3);
        }

        int baseHealth = Math.max(0, displayedHealth);
        int pendingDamage = Math.max(0, roundManager.getPendingDamage());
        int surgeEligibleDamage = Math.min(pendingDamage, Math.max(0, initialSurgePendingBaseDamage));
        int surgeBonusDamage = Math.max(0, applyInitialSurgeBonus(surgeEligibleDamage) - surgeEligibleDamage);
        int previewBaseDamage = Math.min(baseHealth, pendingDamage);
        int previewSurgeDamage = Math.min(Math.max(0, baseHealth - previewBaseDamage), surgeBonusDamage);
        int previewDamage = previewBaseDamage + previewSurgeDamage;
        if (previewDamage > 0 && fillWidth > 0) {
            int totalPreviewHealth = Math.max(0, baseHealth - previewDamage);
            int totalPreviewWidth = (int) Math.round((ENEMY_BAR_W - 4) * (totalPreviewHealth / (double) enemy.getMaxHealth()));
            totalPreviewWidth = Math.max(0, Math.min(fillWidth, totalPreviewWidth));

            int baseOnlyPreviewHealth = Math.max(0, baseHealth - previewBaseDamage);
            int baseOnlyPreviewWidth = (int) Math.round((ENEMY_BAR_W - 4) * (baseOnlyPreviewHealth / (double) enemy.getMaxHealth()));
            baseOnlyPreviewWidth = Math.max(totalPreviewWidth, Math.min(fillWidth, baseOnlyPreviewWidth));

            int surgeSegmentWidth = baseOnlyPreviewWidth - totalPreviewWidth;
            if (surgeSegmentWidth > 0) {
                g2d.setColor(new Color(214, 194, 96, 190));
                g2d.fillRect(
                        ENEMY_BAR_X + 2 + totalPreviewWidth,
                        enemyBarY + 2,
                        surgeSegmentWidth,
                        ENEMY_BAR_H - 3
                );
            }

            int basePreviewSegmentWidth = fillWidth - baseOnlyPreviewWidth;
            if (basePreviewSegmentWidth > 0) {
                g2d.setColor(new Color(209, 118, 212, 190));
                g2d.fillRect(
                        ENEMY_BAR_X + 2 + baseOnlyPreviewWidth,
                        enemyBarY + 2,
                        basePreviewSegmentWidth,
                        ENEMY_BAR_H - 3
                );
            }
        }

        drawActiveItemIndicators(g2d, enemyBarY);

        /*g2d.setColor(WHITE);
        String hpText = enemy.getHealth() + " / " + enemy.getMaxHealth();
        drawCenteredString(g2d, hpText, GameConfig.WIDTH / 2, enemyBarY + ENEMY_BAR_H + 18);
        if (previewDamage > 0) {
            g2d.setColor(new Color(255, 162, 228));
            drawCenteredString(g2d,  "" + previewDamage, GameConfig.WIDTH / 2, enemyBarY + ENEMY_BAR_H + 36);
        }*/

        long now = System.currentTimeMillis();
        if (now < lastHitUntilMs && lastHitDamage > 0) {
            double popProgress = 1.0 - ((lastHitUntilMs - now) / 650.0);
            int yOffset = (int) Math.round(14 * popProgress);
            int alpha = (int) Math.round(255 * (1.0 - popProgress));
            alpha = Math.max(0, Math.min(255, alpha));
            g2d.setColor(new Color(255, 122, 200, alpha));
            drawCenteredString(g2d, "-" + lastHitDamage, GameConfig.WIDTH / 2, enemyBarY - 18 - yOffset);
        }
        if (poisonTicksRemaining > 0 && !enemy.isDefeated()) {
            double pulse = 0.5 + (0.5 * Math.sin(now / 120.0));
            int pulseAlpha = (int) Math.round(90 + (70 * pulse));
            g2d.setColor(new Color(80, 255, 140, pulseAlpha));
            g2d.fillRect(ENEMY_BAR_X + 2, enemyBarY + ENEMY_BAR_H - 6, ENEMY_BAR_W - 4, 4);
        }
        if (now < lastPoisonUntilMs && lastPoisonDamage > 0) {
            double popProgress = 1.0 - ((lastPoisonUntilMs - now) / 520.0);
            int yOffset = (int) Math.round(12 * popProgress);
            int alpha = (int) Math.round(220 * (1.0 - popProgress));
            alpha = Math.max(0, Math.min(255, alpha));
            g2d.setColor(new Color(90, 255, 140, alpha));
            drawCenteredString(g2d, "-" + lastPoisonDamage, GameConfig.WIDTH / 2, enemyBarY - 36 - yOffset);
        }
    }

    private void drawActiveItemIndicators(Graphics2D g2d, int enemyBarY) {
        List<ItemArchetype> activeItems = getEnabledTestItems();
        if (activeItems.isEmpty()) {
            return;
        }

        int totalWidth = (activeItems.size() * ITEM_INDICATOR_SIZE)
                + ((activeItems.size() - 1) * ITEM_INDICATOR_GAP);
        int startX = ENEMY_BAR_X + ((ENEMY_BAR_W - totalWidth) / 2);
        int baseY = enemyBarY + ENEMY_BAR_H + 8;

        for (int i = 0; i < activeItems.size(); i++) {
            int baseX = startX + (i * (ITEM_INDICATOR_SIZE + ITEM_INDICATOR_GAP));
            ItemArchetype item = activeItems.get(i);
            if (item == ItemArchetype.POISON) {
                drawPoisonItemIndicator(g2d, baseX, baseY);
            } else if (item == ItemArchetype.INITIAL_SURGE) {
                drawInitialSurgeIndicator(g2d, baseX, baseY);
            }
        }
    }

    private void drawPoisonItemIndicator(Graphics2D g2d, int baseX, int baseY) {
        BufferedImage poisonSprite = getPoisonIndicatorSprite();
        if (poisonSprite == null) {
            return;
        }

        long animationElapsedMs = getPoisonAnimationElapsedMs();
        double pulse = 0.0;
        if (animationElapsedMs >= 0L) {
            double progress = Math.min(1.0, animationElapsedMs / 220.0);
            pulse = Math.sin(progress * Math.PI);
        }

        int iconSize = ITEM_INDICATOR_SIZE + (int) Math.round(8.0 * pulse);
        int iconX = baseX - ((iconSize - ITEM_INDICATOR_SIZE) / 2);
        int iconY = baseY - ((iconSize - ITEM_INDICATOR_SIZE) / 2);

        Composite oldComposite = g2d.getComposite();
        if (pulse > 0.0) {
            BufferedImage glowSprite = GameImageLoader.tintSprite(poisonSprite, new Color(120, 255, 170));
            int glowSize = iconSize + 6;
            int glowInset = (glowSize - iconSize) / 2;
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.06 + (0.06 * pulse))));
            g2d.drawImage(glowSprite, iconX - glowInset, iconY - glowInset, glowSize, glowSize, null);
        }
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.70f));
        g2d.drawImage(poisonSprite, iconX, iconY, iconSize, iconSize, null);
        if (pulse > 0.0) {
            BufferedImage glowSprite = GameImageLoader.tintSprite(poisonSprite, new Color(170, 255, 210));
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.18 + (0.20 * pulse))));
            g2d.drawImage(glowSprite, iconX, iconY, iconSize, iconSize, null);
        }
        g2d.setComposite(oldComposite);

        int barX = iconX + ((iconSize - ITEM_CHARGE_BAR_WIDTH) / 2);
        int barY = iconY + iconSize + 6;
        drawItemBuildUpMeter(g2d, barX, barY, getPoisonBuildUpRatio(), new Color(90, 255, 140), pulse);
    }

    private void drawInitialSurgeIndicator(Graphics2D g2d, int baseX, int baseY) {
        boolean active = isInitialSurgeActive();
        BufferedImage sprite = getInitialSurgeIndicatorSprite(active);
        if (sprite == null) {
            return;
        }

        double pulse = active ? 0.5 + (0.5 * Math.sin(System.currentTimeMillis() / 110.0)) : 0.0;
        int iconSize = ITEM_INDICATOR_SIZE + (active ? (int) Math.round(6.0 * pulse) : 0);
        int iconX = baseX - ((iconSize - ITEM_INDICATOR_SIZE) / 2);
        int iconY = baseY - ((iconSize - ITEM_INDICATOR_SIZE) / 2);

        Composite oldComposite = g2d.getComposite();
        if (active) {
            BufferedImage glowSprite = GameImageLoader.tintSprite(sprite, new Color(255, 248, 190));
            int glowSize = iconSize + 6;
            int glowInset = (glowSize - iconSize) / 2;
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.08 + (0.10 * pulse))));
            g2d.drawImage(glowSprite, iconX - glowInset, iconY - glowInset, glowSize, glowSize, null);
        }
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, active ? 0.82f : 0.70f));
        g2d.drawImage(sprite, iconX, iconY, iconSize, iconSize, null);
        g2d.setComposite(oldComposite);

        int barX = iconX + ((iconSize - ITEM_CHARGE_BAR_WIDTH) / 2);
        int barY = iconY + iconSize + 6;
        drawItemBuildUpMeter(
                g2d,
                barX,
                barY,
                active ? 1.0 : 0.0,
                new Color(255, 244, 136),
                pulse
        );
    }

    private void drawItemBuildUpMeter(Graphics2D g2d, int x, int y, double fillRatio, Color fillColor, double pulse) {
        double clampedRatio = Math.max(0.0, Math.min(1.0, fillRatio));
        int lineY = y + (ITEM_CHARGE_BAR_HEIGHT / 2);
        int fillHeight = ITEM_CHARGE_BAR_HEIGHT + 3;
        int fillY = y - 1;

        g2d.setColor(new Color(255, 255, 255, 210));
        g2d.drawLine(x, lineY, x + ITEM_CHARGE_BAR_WIDTH - 1, lineY);

        int fillWidth = (int) Math.round(ITEM_CHARGE_BAR_WIDTH * clampedRatio);
        if (fillWidth > 0) {
            int alpha = (int) Math.round(190 + (45 * pulse));
            g2d.setColor(new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), alpha));
            g2d.fillRect(x, fillY, fillWidth, fillHeight);
        } else {
            g2d.setColor(new Color(255, 255, 255, 70));
            g2d.fillRect(x, y + 1, 1, Math.max(1, ITEM_CHARGE_BAR_HEIGHT - 2));
        }
    }

    private double getPoisonBuildUpRatio() {
        return poisonBuildUp / GameConfig.ITEM_BUILDUP_TARGET;
    }

    private BufferedImage getInitialSurgeIndicatorSprite(boolean active) {
        BufferedImage fallback = null;
        for (BufferedImage sprite : initialSurgeSprites) {
            if (sprite != null) {
                fallback = sprite;
                break;
            }
        }
        if (fallback == null) {
            return null;
        }
        if (!active) {
            return fallback;
        }

        int frameCount = initialSurgeSprites.length;
        int frameIndex = (int) ((System.currentTimeMillis() / INITIAL_SURGE_FRAME_DURATION_MS) % frameCount);
        BufferedImage frame = initialSurgeSprites[frameIndex];
        return frame != null ? frame : fallback;
    }

    private BufferedImage getPoisonIndicatorSprite() {
        if (poisonIconSprite == null) {
            return null;
        }
        if (lastPoisonAnimationStartMs <= 0L) {
            return poisonIconSprite;
        }

        long elapsedMs = System.currentTimeMillis() - lastPoisonAnimationStartMs;
        if (elapsedMs < 110L) {
            return poisonIconAttack1Sprite != null ? poisonIconAttack1Sprite : poisonIconSprite;
        }
        if (elapsedMs < 220L) {
            return poisonIconAttack2Sprite != null ? poisonIconAttack2Sprite : poisonIconSprite;
        }
        return poisonIconSprite;
    }

    private long getPoisonAnimationElapsedMs() {
        if (lastPoisonAnimationStartMs <= 0L) {
            return -1L;
        }
        long elapsedMs = System.currentTimeMillis() - lastPoisonAnimationStartMs;
        return elapsedMs < 220L ? elapsedMs : -1L;
    }

    private boolean isInitialSurgeActive() {
        return isInitialSurgeActive(roundManager.getTimeLeftMs(), roundManager.getRoundDurationMs());
    }

    private boolean isInitialSurgeActive(long timeLeftMs, long durationMs) {
        if (!hasTestItem(ItemArchetype.INITIAL_SURGE)) {
            return false;
        }
        if (screen != ScreenState.ENCOUNTER || durationMs <= 0L) {
            return false;
        }
        return (timeLeftMs / (double) durationMs) >= INITIAL_SURGE_ACTIVE_THRESHOLD;
    }

    private int applyInitialSurgeBonus(int damage) {
        if (damage <= 0) {
            return 0;
        }
        return Math.max(0, (int) Math.round(damage * INITIAL_SURGE_DAMAGE_MULTIPLIER));
    }

    private void drawSequence(Graphics2D g2d) {
        List<Integer> sequence = roundManager.getSequence();
        int count = sequence.size();
        if (count == 0) {
            return;
        }

        int totalWidth = (count * SEQUENCE_SYMBOL_SIZE) + ((count - 1) * SEQUENCE_SYMBOL_GAP);
        int startX = ARENA_X + (ARENA_W - totalWidth) / 2;
        int y = ENCOUNTER_ARENA_Y + (ARENA_H - SEQUENCE_SYMBOL_SIZE) / 2 + 52;
        drawSequencePunchSprite(g2d, y);
        Composite oldComposite = g2d.getComposite();
        if (timeoutRecoveryActive) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.46f));
        }
        boolean wrongFlash = roundManager.isWrongFlashActive();
        int progressIndex = roundManager.getProgressIndex();
        boolean hideSequence = roundManager.shouldHideSequence();
        boolean reverseInput = roundManager.getActiveArchetype().isReverseInput();
        int visibleCount = roundManager.getVisibleSequenceCount();
        for (int i = 0; i < count; i++) {
            int x = startX + i * (SEQUENCE_SYMBOL_SIZE + SEQUENCE_SYMBOL_GAP);
            boolean isCorrect = !wrongFlash
                    && (reverseInput ? i >= count - progressIndex : i < progressIndex);
            boolean isVisible = i < visibleCount;
            Color tileBg = new Color(3, 16, 38, 150);
            Color tileFill = wrongFlash ? new Color(130, 24, 68, 170) : (isCorrect ? new Color(14, 72, 120, 180) : new Color(12, 34, 66, 165));
            Color borderColor = wrongFlash ? RED : (isCorrect ? GREEN : WHITE);
            Color symbolColor = wrongFlash ? RED : (isCorrect ? GREEN : WHITE);

            g2d.setColor(tileBg);
            g2d.fillRect(x, y, SEQUENCE_SYMBOL_SIZE, SEQUENCE_SYMBOL_SIZE);
            g2d.setColor(tileFill);
            g2d.fillRect(x + 5, y + 5, SEQUENCE_SYMBOL_SIZE - 10, SEQUENCE_SYMBOL_SIZE - 10);
            g2d.setColor(borderColor);
            g2d.drawRect(x, y, SEQUENCE_SYMBOL_SIZE, SEQUENCE_SYMBOL_SIZE);

            if (hideSequence || !isVisible) {
                g2d.setColor(new Color(255, 255, 255, 110));
                drawCenteredString(g2d, "?", x + (SEQUENCE_SYMBOL_SIZE / 2), y + 47);
            } else {
                Direction direction = Direction.values()[sequence.get(i)];
                BufferedImage sprite = isCorrect ? arrowSpritesGreen.get(direction) : arrowSprites.get(direction);
                if (sprite != null) {
                    drawArrowSprite(g2d, sprite, x, y, SEQUENCE_SYMBOL_SIZE);
                } else {
                    drawArrow(g2d, direction, x, y, SEQUENCE_SYMBOL_SIZE, symbolColor);
                }
            }
        }
        g2d.setComposite(oldComposite);
    }

    private void drawSequencePunchSprite(Graphics2D g2d, int arrowRowY) {
        BufferedImage sprite = getSequencePunchSprite(System.currentTimeMillis());
        if (sprite == null) {
            return;
        }
        int size = getSequencePunchSize();
        int x = 20;
        int y = ENCOUNTER_ARENA_Y + (ARENA_H - size) / 2 + SEQUENCE_PUNCH_OFFSET_Y;
        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, SEQUENCE_PUNCH_ALPHA));
        g2d.drawImage(sprite, x, y, size, size, null);
        g2d.setComposite(oldComposite);
    }

    private BufferedImage getSequencePunchSprite(long now) {
        if (lastSequencePunchMs <= 0L || now - lastSequencePunchMs > SEQUENCE_PUNCH_IDLE_RESET_MS) {
            return sequenceIdleSprite != null ? sequenceIdleSprite : sequencePunch1Sprite;
        }
        if (sequencePunchFrame == 2 && sequencePunch2Sprite != null) {
            return sequencePunch2Sprite;
        }
        if (sequencePunchFrame == 3 && sequencePunch3Sprite != null) {
            return sequencePunch3Sprite;
        }
        if (sequencePunchFrame == 1 && sequencePunch1Sprite != null) {
            return sequencePunch1Sprite;
        }
        if (sequenceIdleSprite != null) {
            return sequenceIdleSprite;
        }
        if (sequencePunch1Sprite != null) {
            return sequencePunch1Sprite;
        }
        return sequencePunch2Sprite != null ? sequencePunch2Sprite : sequencePunch3Sprite;
    }

    private int getSequencePunchSize() {
        int availableWidth = GameConfig.WIDTH - (HEART_BG_MARGIN_X * 2) - ((MAX_HEARTS - 1) * HEART_GAP);
        int heartSize = Math.max(24, availableWidth / MAX_HEARTS);
        int baseSize = Math.max(SEQUENCE_PUNCH_SIZE, heartSize);
        return Math.max(1, Math.round(baseSize * SEQUENCE_PUNCH_SCALE));
    }

    private void registerSequencePunch(boolean sequenceComplete) {
        long now = System.currentTimeMillis();
        if (sequenceComplete) {
            sequencePunchFrame = 3;
            sequencePunchPatternIndex = -1;
        } else {
            if (sequencePunchPatternIndex < 0) {
                sequencePunchPatternIndex = 0;
            } else {
                sequencePunchPatternIndex = (sequencePunchPatternIndex + 1) % SEQUENCE_PUNCH_PATTERN.length;
            }
            sequencePunchFrame = SEQUENCE_PUNCH_PATTERN[sequencePunchPatternIndex];
        }
        lastSequencePunchMs = now;
    }

    private void resetSequencePunchState() {
        sequencePunchFrame = 0;
        sequencePunchPatternIndex = -1;
        lastSequencePunchMs = 0L;
    }

    private void drawRhythmEncounter(Graphics2D g2d) {
        drawSequence(g2d);
    }

    private void drawLossOverlay(Graphics2D g2d) {
        int w = 500;
        int h = 180;
        int x = (GameConfig.WIDTH - w) / 2;
        int y = (GameConfig.HEIGHT - h) / 2 + 20;

        g2d.setColor(new Color(2, 14, 36));
        g2d.fillRect(x, y, w, h);
        drawFrame(g2d, x, y, w, h, 4, WHITE);

        g2d.setFont(HUD_FONT);
        g2d.setColor(RED);
        drawCenteredString(g2d, "ENCOUNTER FAILED", GameConfig.WIDTH / 2, y + 58);

        g2d.setFont(SMALL_FONT);
        g2d.setColor(WHITE);
        drawCenteredString(g2d, "ENTER = NEW RUN", GameConfig.WIDTH / 2, y + 108);
        drawCenteredString(g2d, "ESC = MENU", GameConfig.WIDTH / 2, y + 138);
    }

    private void drawShopOverlay(Graphics2D g2d) {
        int w = 520;
        int h = 270;
        int x = (GameConfig.WIDTH - w) / 2;
        int y = (GameConfig.HEIGHT - h) / 2 + 10;

        g2d.setColor(new Color(3, 16, 38, 232));
        g2d.fillRect(x, y, w, h);
        drawFrame(g2d, x, y, w, h, 4, WHITE);

        g2d.setFont(HUD_FONT);
        drawGlowingCenteredString(g2d, "FIELD SHOP", GameConfig.WIDTH / 2, y + 46, YELLOW, GLOW_CYAN);

        ShopOption[] options = ShopOption.values();
        for (int i = 0; i < options.length; i++) {
            ShopOption option = options[i];
            int rowY = y + 90 + (i * 52);
            boolean selected = shopSelectionIndex == i;
            boolean affordable = coinCount >= option.getCost();
            Color color = selected ? YELLOW : (affordable ? WHITE : TEXT_DIM);

            g2d.setFont(BODY_FONT);
            g2d.setColor(color);
            String label = (selected ? "> " : "  ") + option.getLabel() + "  [" + option.getCost() + "C]";
            g2d.drawString(label, x + 42, rowY);

            g2d.setFont(SMALL_FONT);
            g2d.setColor(TEXT_DIM);
            g2d.drawString(option.getDescription(), x + 64, rowY + 20);
        }

        g2d.setFont(SMALL_FONT);
        g2d.setColor(TEXT_DIM);
        drawCenteredString(g2d, "ENTER BUY  |  ESC LEAVE", GameConfig.WIDTH / 2, y + h - 26);
    }

    private void setupKeyBindings() {
        bindDirection("UP", Direction.UP);
        bindDirection("DOWN", Direction.DOWN);
        bindDirection("LEFT", Direction.LEFT);
        bindDirection("RIGHT", Direction.RIGHT);

        InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "confirm_action");
        actionMap.put("confirm_action", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleConfirmAction();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "go_to_menu");
        actionMap.put("go_to_menu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleBackAction();
            }
        });
    }

    private void bindDirection(String keyName, Direction direction) {
        String actionName = "input_dir_" + keyName;
        InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(keyName), actionName);
        actionMap.put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processDirectionalInput(direction);
            }
        });
    }

    private void setupMovementDispatcher() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                int id = e.getID();
                if (id == KeyEvent.KEY_PRESSED) {
                    if (screen == ScreenState.DUNGEON
                            && !encounterTransitionActive
                            && !roomTransitionActive
                            && !roomIntroActive
                            && !menuTransitionActive) {
                        setMovementFromKeyCode(e.getKeyCode(), true);
                    }
                } else if (id == KeyEvent.KEY_RELEASED) {
                    setMovementFromKeyCode(e.getKeyCode(), false);
                }
                return false;
            }
        });
    }

    private void ensureControllerPrewarmStarted() {
        if (controllerPrewarmStarted) {
            return;
        }
        controllerPrewarmStarted = true;
        Thread thread = new Thread(() -> {
            controllerInputManager.prewarm();
            controllerPrewarmFinished = true;
        }, "controller-prewarm");
        thread.setDaemon(true);
        thread.start();
    }

    private void startRun() {
        roomNumber = 1;
        coinCount = 0;
        playerHealth = GameConfig.PLAYER_MAX_HEALTH;
        displayedPlayerHealth = playerHealth;
        heartDamageFlashUntilMs = 0L;
        healthDrainReliefMs = 0L;
        timeoutRecoveryActive = false;
        timeoutRecoveryStartMs = 0L;
        timeoutRecoveryTargetMs = 0L;
        mistakeGuardCharges = 0L;
        nextEncounterTimeBonusMs = 0L;
        shopSelectionIndex = 0;
        lastCoinGain = 0;
        lastCoinGainUntilMs = 0L;
        clearActiveItemEffects();
        clearMovementInput();
        clearTimerBarAnimation();
        menuTransitionActive = false;
        startRunTransitionActive = true;
        encounterTransitionActive = false;
        pendingEncounterIndex = -1;
        encounterIntroActive = false;
        roomTransitionActive = false;
        roomIntroActive = false;
        pendingRoomEntryDirection = null;
        roomIntroDirection = null;
        runStartFadeInActive = false;
        runStartFadeInStartMs = 0L;
        startRunTransitionStartMs = System.currentTimeMillis();
        enemyKillEffects.clear();
        backdropEffects.clearHueSweeps();
        roundManager.configureEncounter(EnemyArchetype.NORMAL);
        roundManager.startGame(true);
        generateRoom();
        AudioManager.playSfx("enter_game.wav");
    }

    private void generateRoom() {
        roomEncounters.clear();
        activeEncounterIndex = -1;
        pendingEncounterIndex = -1;
        menuTransitionActive = false;
        encounterTransitionActive = false;
        encounterIntroActive = false;
        roomTransitionActive = false;
        roomIntroActive = false;
        pendingRoomEntryDirection = null;
        roomIntroDirection = null;
        lastHitDamage = 0;
        lastHitUntilMs = 0;
        enemyKillEffects.clear();
        backdropEffects.clearHueSweeps();

        //randomize door location
        Direction[] possibleDoors = { Direction.RIGHT, Direction.UP, Direction.DOWN, Direction.LEFT };
        doorDirection = possibleDoors[random.nextInt(possibleDoors.length)];

        playerX = ROOM_X + 26;
        playerY = ROOM_Y + (ROOM_H / 2) - (PLAYER_SIZE / 2);

        int encounters = 1 + random.nextInt(3);
        int maxTries = 50;
        for (int i = 0; i < encounters; i++) {
            EncounterNode node = EncounterNode.createEncounter(generateEnemyHealthForRoom(), rollEnemyArchetype());
            boolean placed = false;
            for (int tries = 0; tries < maxTries; tries++) {
                int nx = ROOM_X + 80 + random.nextInt(Math.max(1, ROOM_W - 220));
                int ny = ROOM_Y + 40 + random.nextInt(Math.max(1, ROOM_H - 80));
                Rectangle candidate = new Rectangle(nx, ny, ENCOUNTER_SIZE, ENCOUNTER_SIZE);
                if (candidate.intersects(new Rectangle((int) Math.round(playerX), (int) Math.round(playerY), PLAYER_SIZE, PLAYER_SIZE))) {
                    continue;
                }
                if (candidate.intersects(getDoorRect())) {
                    continue;
                }
                if (intersectsAnyEncounter(candidate)) {
                    continue;
                }
                node.setX(nx);
                node.setY(ny);
                placed = true;
                break;
            }
            if (placed) {
                roomEncounters.add(node);
            }
        }

        if (shouldSpawnShopInRoom()) {
            EncounterNode shopNode = EncounterNode.createShop();
            placeRoomNode(shopNode, maxTries);
        }

        if (roomEncounters.isEmpty()) {
            EncounterNode fallback = EncounterNode.createEncounter(generateEnemyHealthForRoom(), rollEnemyArchetype());
            fallback.setX(ROOM_X + ROOM_W / 2);
            fallback.setY(ROOM_Y + ROOM_H / 2);
            roomEncounters.add(fallback);
        }
    }

    private boolean shouldSpawnShopInRoom() {
        return true;
    }

    private void placeRoomNode(EncounterNode node, int maxTries) {
        for (int tries = 0; tries < maxTries; tries++) {
            int nx = ROOM_X + 80 + random.nextInt(Math.max(1, ROOM_W - 220));
            int ny = ROOM_Y + 40 + random.nextInt(Math.max(1, ROOM_H - 80));
            Rectangle candidate = new Rectangle(nx, ny, ENCOUNTER_SIZE, ENCOUNTER_SIZE);
            if (candidate.intersects(new Rectangle((int) Math.round(playerX), (int) Math.round(playerY), PLAYER_SIZE, PLAYER_SIZE))) {
                continue;
            }
            if (candidate.intersects(getDoorRect())) {
                continue;
            }
            if (intersectsAnyEncounter(candidate)) {
                continue;
            }
            node.setX(nx);
            node.setY(ny);
            roomEncounters.add(node);
            return;
        }
    }

    private EnemyArchetype rollEnemyArchetype() {
        if (forcedTestEnemy != null) {
            return forcedTestEnemy;
        }
        EnemyArchetype[] archetypes = EnemyArchetype.values();
        return archetypes[random.nextInt(archetypes.length)];
    }

    private int generateEnemyHealthForRoom() {
        int base = GameConfig.ENEMY_BASE_HEALTH + ((roomNumber - 1) * GameConfig.ENEMY_HEALTH_PER_ROOM);
        int variance = random.nextInt((GameConfig.ENEMY_HEALTH_VARIANCE * 2) + 1) - GameConfig.ENEMY_HEALTH_VARIANCE;
        return Math.max(40, base + variance);
    }

    private void updateDungeonMovement(double deltaSeconds) {
        double dx = 0.0;
        double dy = 0.0;

        if (isMovementHeld(Direction.UP)) {
            dy -= 1.0;
        }
        if (isMovementHeld(Direction.DOWN)) {
            dy += 1.0;
        }
        if (isMovementHeld(Direction.LEFT)) {
            dx -= 1.0;
        }
        if (isMovementHeld(Direction.RIGHT)) {
            dx += 1.0;
        }

        if (dx == 0.0 && dy == 0.0) {
            return;
        }

        double length = Math.sqrt((dx * dx) + (dy * dy));
        double step = PLAYER_SPEED_PER_SECOND * deltaSeconds;
        movePlayer((dx / length) * step, (dy / length) * step);
    }

    private void movePlayer(double dx, double dy) {
        int minX = ROOM_X + 2;
        int minY = ROOM_Y + 2;
        int maxX = ROOM_X + ROOM_W - PLAYER_SIZE - 2;
        int maxY = ROOM_Y + ROOM_H - PLAYER_SIZE - 2;

        playerX = clampDouble(playerX + dx, minX, maxX);
        playerY = clampDouble(playerY + dy, minY, maxY);

        Rectangle playerRect = new Rectangle((int) Math.round(playerX), (int) Math.round(playerY), PLAYER_SIZE, PLAYER_SIZE);

        if (!allEncountersCleared()) {
            for (int i = 0; i < roomEncounters.size(); i++) {
                EncounterNode node = roomEncounters.get(i);
                if (node.isEncounter() && node.isCleared()) {
                    continue;
                }
                Rectangle encounterRect = new Rectangle(node.getX(), node.getY(), ENCOUNTER_SIZE, ENCOUNTER_SIZE);
                if (playerRect.intersects(encounterRect)) {
                    if (node.isShop()) {
                        openShop();
                    } else {
                        startEncounter(i);
                    }
                    return;
                }
            }
        } else if (playerRect.intersects(getDoorRect())) {
            startRoomTransition(doorDirection);
        }
    }

    private void startEncounter(int encounterIndex) {
        clearMovementInput();
        lastHitDamage = 0;
        lastHitUntilMs = 0;
        timeoutRecoveryActive = false;
        timeoutRecoveryStartMs = 0L;
        timeoutRecoveryTargetMs = 0L;
        healthDrainReliefMs = 0L;
        clearActiveItemEffects();
        resetSequencePunchState();
        backdropEffects.clearHueSweeps();
        EncounterEnemy enemy = roomEncounters.get(encounterIndex).getEnemy();
        encounterMusicFile = getEncounterMusicFile(enemy.getArchetype());
        roundManager.configureEncounter(enemy.getArchetype());
        AudioManager.playSfx("encounter_start.wav");
        controllerInputManager.rumble(ENEMY_TOUCH_RUMBLE_STRENGTH, ENEMY_TOUCH_RUMBLE_MS);
        pendingEncounterIndex = encounterIndex;
        encounterTransitionActive = true;
        encounterTransitionStartMs = System.currentTimeMillis();
    }

    private void openShop() {
        clearMovementInput();
        screen = ScreenState.SHOP;
        shopSelectionIndex = 0;
        shopMusicFade = 0.0;
    }

    private void closeShop() {
        if (screen == ScreenState.SHOP) {
            screen = ScreenState.DUNGEON;
            clearMovementInput();
        }
    }

    private void startRoomTransition(Direction exitedDir) {
        clearMovementInput();
        backdropEffects.clearHueSweeps();
        roomTransitionActive = true;
        roomTransitionStartMs = System.currentTimeMillis();
        roomIntroActive = false;
        pendingRoomEntryDirection = exitedDir;
        roomIntroDirection = exitedDir;
        AudioManager.playSfx("next_room.wav");
        controllerInputManager.rumble(ROOM_ENTRY_RUMBLE_STRENGTH, ROOM_ENTRY_RUMBLE_MS);
    }

    private void completeRoomTransition() {
        Direction exitedDir = pendingRoomEntryDirection;
        pendingRoomEntryDirection = null;
        if (exitedDir == null) {
            return;
        }

        roomNumber++;
        generateRoom();

        Direction forbid;
        switch (exitedDir) {
            case LEFT:  forbid = Direction.RIGHT; break;
            case RIGHT: forbid = Direction.LEFT;  break;
            case UP:    forbid = Direction.DOWN;  break;
            case DOWN:  forbid = Direction.UP;    break;
            default:    forbid = null;            break;
        }

        int safety = 0;
        while (forbid != null && doorDirection == forbid && safety++ < 8) {
            generateRoom();
        }

        positionPlayerFromEntry(exitedDir);
    }

    private void handleEncounterInput(int symbol) {
        boolean wrongFlashBefore = roundManager.isWrongFlashActive();
        int progressBefore = roundManager.getProgressIndex();
        int sequenceLengthBefore = roundManager.getSequence().size();
        int pendingDamageBefore = roundManager.getPendingDamage();
        RoundCompletion completion = roundManager.handleSymbolInput(symbol);
        int progressAfter = roundManager.getProgressIndex();
        if (completion != null || progressAfter > progressBefore) {
            registerSequencePunch(completion != null);
        }
        if (progressAfter > progressBefore) {
            controllerInputManager.rumble(KEY_SUCCESS_RUMBLE_STRENGTH, KEY_SUCCESS_RUMBLE_MS);
            int pendingDamageAfter = roundManager.getPendingDamage();
            registerInitialSurgeDamage(Math.max(0, pendingDamageAfter - pendingDamageBefore));
            addHealthDrainRelief();
            restoreHealthOnCorrectKey();
            applyItemBuildUpOnCorrectKey(sequenceLengthBefore, progressBefore, roundManager.getLastCorrectCadenceMs());
        }
        if (completion == null) {
            boolean triggeredWrongInput = !wrongFlashBefore && roundManager.isWrongFlashActive();
            if (triggeredWrongInput) {
                controllerInputManager.rumble(KEY_FAIL_RUMBLE_STRENGTH, KEY_FAIL_RUMBLE_MS);
                clearInitialSurgeState();
                resetSequencePunchState();
                if (mistakeGuardCharges > 0) {
                    mistakeGuardCharges--;
                } else {
                    applyPlayerDamage(GameConfig.WRONG_INPUT_HEALTH_LOSS, false, "heart_lost.wav");
                }
            }
            return;
        }
        if (activeEncounterIndex < 0 || activeEncounterIndex >= roomEncounters.size()) {
            return;
        }

        controllerInputManager.rumble(SEQUENCE_COMPLETE_RUMBLE_STRENGTH, SEQUENCE_COMPLETE_RUMBLE_MS);
        backdropEffects.triggerHueSweepRipple(completion, roundManager.getTimeLeftMs(), game.model.TimerStyle.BACKDROP_HUE);

        EncounterNode currentNode = roomEncounters.get(activeEncounterIndex);
        int resolvedDamage = Math.max(0, completion.getResolvedDamage());
        int surgeEligibleDamage = Math.min(resolvedDamage, Math.max(0, initialSurgePendingBaseDamage));
        resolvedDamage += Math.max(0, applyInitialSurgeBonus(surgeEligibleDamage) - surgeEligibleDamage);
        clearInitialSurgeState();
        int damage = (int) Math.round(
                resolvedDamage * currentNode.getEnemy().getArchetype().getDamageMultiplier()
        );
        currentNode.getEnemy().applyDamage(damage);
        if (damage > 0) {
            lastHitDamage = damage;
            lastHitUntilMs = System.currentTimeMillis() + 650L;
        } else {
            lastHitDamage = 0;
            lastHitUntilMs = 0;
        }

        boolean enemyDefeated = currentNode.isCleared();
        if (completion != null && !enemyDefeated) {
            int nextIndex = random.nextInt(FINISHER_SFX_FILES.length);
            if (FINISHER_SFX_FILES.length > 1 && nextIndex == lastFinisherSfxIndex) {
                nextIndex = (nextIndex + 1 + random.nextInt(FINISHER_SFX_FILES.length - 1)) % FINISHER_SFX_FILES.length;
            }
            lastFinisherSfxIndex = nextIndex;
            float offset = 0.0f;
            if (nextIndex < FINISHER_SFX_GAIN_OFFSETS_DB.length) {
                offset = FINISHER_SFX_GAIN_OFFSETS_DB[nextIndex];
            }
            AudioManager.playSfx(FINISHER_SFX_FILES[nextIndex], FINISHER_SFX_GAIN_DB + offset);
        }
        int coinReward = calculateComboCoinReward(completion);
        if (coinReward > 0) {
            coinCount += coinReward;
            lastCoinGain = coinReward;
            lastCoinGainUntilMs = System.currentTimeMillis() + 760L;
            /*if (!enemyDefeated) {
                AudioManager.playSfx("coin_collect.wav");
            }*/
        }
        if (enemyDefeated) {
            finalizeEncounterIfEnemyDefeated(currentNode);
        } else if (currentNode.getEnemy().getArchetype().isTimeRecoveryEnabled()) {
            AudioManager.playSfx("bar_fill.wav");
        }
    }

    private void handleEncounterTimeout() {
        applyPlayerDamage(GameConfig.TIMEOUT_HEALTH_LOSS, true, "timeout.wav");
    }

    private void updateEncounterHealthDrain(double deltaSeconds) {
        if (deltaSeconds <= 0.0 || screen != ScreenState.ENCOUNTER || encounterIntroActive || playerHealth <= 0.0) {
            return;
        }

        double reliefSeconds = Math.min(deltaSeconds, healthDrainReliefMs / 1000.0);
        double normalSeconds = Math.max(0.0, deltaSeconds - reliefSeconds);
        if (reliefSeconds > 0.0) {
            healthDrainReliefMs = Math.max(0L, healthDrainReliefMs - Math.round(reliefSeconds * 1000.0));
        }

        double drain = (normalSeconds * GameConfig.ENCOUNTER_HEALTH_DRAIN_PER_SECOND)
                + (reliefSeconds * GameConfig.ENCOUNTER_HEALTH_DRAIN_PER_SECOND * GameConfig.HEALTH_DRAIN_RELIEF_MULTIPLIER);
        if (drain > 0.0) {
            applyPassiveHealthDrain(drain);
        }
    }

    private void addHealthDrainRelief() {
        healthDrainReliefMs = Math.min(
                GameConfig.HEALTH_DRAIN_RELIEF_MAX_MS,
                healthDrainReliefMs + GameConfig.HEALTH_DRAIN_RELIEF_PER_CORRECT_KEY_MS
        );
    }

    private void restoreHealthOnCorrectKey() {
        if (screen != ScreenState.ENCOUNTER || encounterIntroActive) {
            return;
        }
        playerHealth = Math.min(
                GameConfig.PLAYER_MAX_HEALTH,
                playerHealth + GameConfig.HEALTH_RESTORE_PER_CORRECT_KEY
        );
    }

    private void applyItemBuildUpOnCorrectKey(int sequenceLength, int keyIndex, long cadenceMs) {
        if (!hasTestItem(ItemArchetype.POISON)) {
            return;
        }
        if (screen != ScreenState.ENCOUNTER || encounterIntroActive) {
            return;
        }

        double increment = DamageCalculator.calculateItemBuildUpIncrement(sequenceLength, keyIndex, cadenceMs);
        if (increment <= 0.0) {
            return;
        }

        poisonBuildUp += increment;
        while (poisonBuildUp >= GameConfig.ITEM_BUILDUP_TARGET) {
            poisonBuildUp -= GameConfig.ITEM_BUILDUP_TARGET;
            triggerPoisonProc();
        }
    }

    private void triggerPoisonProc() {
        poisonTicksRemaining += GameConfig.POISON_TICKS_PER_PROC;
        if (poisonTicksRemaining < 0) {
            poisonTicksRemaining = GameConfig.POISON_TICKS_PER_PROC;
        }
        lastPoisonAnimationStartMs = System.currentTimeMillis();
        if (nextPoisonTickMs <= 0L) {
            nextPoisonTickMs = System.currentTimeMillis() + GameConfig.POISON_TICK_INTERVAL_MS;
        }
    }

    private void updateItemEffects(double deltaSeconds) {
        boolean poisonActive = hasTestItem(ItemArchetype.POISON);
        boolean anyItemActive = forcedTestItemMask != 0;
        if (!anyItemActive) {
            clearActiveItemEffects();
            return;
        }
        if (screen != ScreenState.ENCOUNTER || encounterIntroActive) {
            clearActiveItemEffects();
            return;
        }
        if (!poisonActive) {
            clearPoisonState();
            return;
        }

        updateItemBuildUpDecay(deltaSeconds);
        if (poisonTicksRemaining <= 0) {
            return;
        }

        EncounterNode currentNode = getActiveEncounterNode();
        if (currentNode == null || currentNode.isCleared()) {
            clearActiveItemEffects();
            return;
        }

        long now = System.currentTimeMillis();
        if (nextPoisonTickMs <= 0L) {
            nextPoisonTickMs = now + GameConfig.POISON_TICK_INTERVAL_MS;
            return;
        }
        if (now < nextPoisonTickMs) {
            return;
        }

        int tickDamage = Math.max(0, GameConfig.POISON_DAMAGE_PER_TICK);
        if (tickDamage > 0) {
            currentNode.getEnemy().applyDamage(tickDamage);
            lastPoisonDamage = tickDamage;
            lastPoisonUntilMs = now + 520L;
        }
        poisonTicksRemaining = Math.max(0, poisonTicksRemaining - 1);

        if (currentNode.isCleared()) {
            clearActiveItemEffects();
            finalizeEncounterIfEnemyDefeated(currentNode);
            return;
        }

        if (poisonTicksRemaining > 0) {
            nextPoisonTickMs = now + GameConfig.POISON_TICK_INTERVAL_MS;
        } else {
            nextPoisonTickMs = 0L;
        }
    }

    private void updateItemBuildUpDecay(double deltaSeconds) {
        if (poisonBuildUp <= 0.0 || deltaSeconds <= 0.0) {
            return;
        }
        poisonBuildUp = Math.max(
                0.0,
                poisonBuildUp - (GameConfig.ITEM_BUILDUP_DECAY_PER_SECOND * deltaSeconds)
        );
    }

    private void clearActiveItemEffects() {
        clearInitialSurgeState();
        clearPoisonState();
    }

    private void registerInitialSurgeDamage(int baseDamage) {
        if (baseDamage <= 0) {
            return;
        }
        if (!isInitialSurgeActive()) {
            return;
        }
        initialSurgePendingBaseDamage += baseDamage;
    }

    private void clearInitialSurgeState() {
        initialSurgePendingBaseDamage = 0;
    }

    private void clearPoisonState() {
        poisonBuildUp = 0.0;
        poisonTicksRemaining = 0;
        nextPoisonTickMs = 0L;
        lastPoisonDamage = 0;
        lastPoisonUntilMs = 0L;
        lastPoisonAnimationStartMs = 0L;
    }

    private void applyPassiveHealthDrain(double amount) {
        if (amount <= 0.0 || screen != ScreenState.ENCOUNTER) {
            return;
        }

        playerHealth = Math.max(0.0, playerHealth - amount);
        if (playerHealth <= 0.0) {
            clearMovementInput();
            clearTimerBarAnimation();
            AudioManager.playSfx("player_death.wav");
            screen = ScreenState.LOST;
        }
    }

    private void applyPlayerDamage(double amountUnits, boolean resetTimerOnSurvive, String nonLethalSfx) {
        if (screen != ScreenState.ENCOUNTER) {
            return;
        }
        if (amountUnits <= 0.0) {
            return;
        }

        controllerInputManager.rumble(DAMAGE_RUMBLE_STRENGTH, DAMAGE_RUMBLE_MS);
        beginHealthDamageAnimation();
        double nextHealth = Math.max(0.0, playerHealth - amountUnits);
        boolean lethal = nextHealth <= 0.0;
        playerHealth = nextHealth;
        clearInitialSurgeState();
        healthDrainReliefMs = 0L;
        if (lethal) {
            AudioManager.playSfx("player_death.wav");
        } else {
            AudioManager.playSfx(nonLethalSfx);
        }
        if (playerHealth <= 0.0) {
            clearMovementInput();
            clearTimerBarAnimation();
            screen = ScreenState.LOST;
            return;
        }

        if (resetTimerOnSurvive) {
            roundManager.startGame(false);
            startTimeoutRecoveryAnimation();
        }
    }

    private void beginHealthDamageAnimation() {
        displayedPlayerHealth = Math.max(displayedPlayerHealth, playerHealth);
        heartDamageFlashUntilMs = System.currentTimeMillis() + HEART_DAMAGE_FLASH_MS;
    }

    private void startTimeoutRecoveryAnimation() {
        long targetTimeLeft = Math.max(0L, roundManager.getTimeLeftMs());
        displayedTimerDurationMs = Math.max(1L, roundManager.getRoundDurationMs());
        displayedTimerMs = 0L;
        timeoutRecoveryActive = true;
        timeoutRecoveryStartMs = System.currentTimeMillis();
        timeoutRecoveryTargetMs = targetTimeLeft;
        roundManager.pauseTimer(TIMEOUT_TIMER_REFILL_DURATION_MS + TIMEOUT_RESET_RECOVERY_BUFFER_MS);
    }

    private void finalizeEncounterIfEnemyDefeated(EncounterNode node) {
        if (node == null || !node.isCleared()) {
            return;
        }
        spawnEnemyDefeatEffect(node);
        AudioManager.playSfx("enemy_defeated.wav", 7.0f);
        activeEncounterIndex = -1;
        clearInitialSurgeState();
        timeoutRecoveryActive = false;
        clearMovementInput();
        clearTimerBarAnimation();
        screen = ScreenState.DUNGEON;
        encounterBestedTransitionActive = true;
        encounterBestedTransitionStartMs = System.currentTimeMillis();
    }

    private void spawnEnemyDefeatEffect(EncounterNode node) {
        int centerX = node.getX() + (ENCOUNTER_SIZE / 2);
        int centerY = node.getY() + (ENCOUNTER_SIZE / 2);
        enemyKillEffects.spawn();
        backdropEffects.spawnEnemyDefeatRipples(centerX, centerY);
    }

    private int calculateComboCoinReward(RoundCompletion completion) {
        int damage = Math.max(0, completion.getResolvedDamage());
        if (damage <= 0) {
            return 0;
        }

        double duration = Math.max(1.0, completion.getRoundDurationMs());
        double timeLeftRatio = completion.getTimeLeftMs() / duration;
        timeLeftRatio = Math.max(0.0, Math.min(1.0, timeLeftRatio));

        int sequenceLength = completion.getSequenceLength();
        int base = Math.max(1, (int) Math.round(damage * 0.20));
        int lengthBonus = Math.max(0, sequenceLength - GameConfig.MIN_SEQUENCE_LENGTH);
        int speedBonus = (int) Math.round(timeLeftRatio * (2.0 + (sequenceLength * 1.1)));

        int comboTierBonus = 0;
        if (timeLeftRatio >= 0.82) {
            comboTierBonus = 4 + lengthBonus;
        } else if (timeLeftRatio >= 0.68) {
            comboTierBonus = 2 + (lengthBonus / 2);
        } else if (timeLeftRatio >= 0.52) {
            comboTierBonus = 1;
        }

        return base + lengthBonus + speedBonus + comboTierBonus;
    }

    private int countUnclearedEncounters() {
        int count = 0;
        for (EncounterNode node : roomEncounters) {
            if (node.isEncounter() && !node.isCleared()) {
                count++;
            }
        }
        return count;
    }

    private boolean allEncountersCleared() {
        return countUnclearedEncounters() == 0;
    }

    private EncounterEnemy getActiveEncounterEnemy() {
        if (activeEncounterIndex < 0 || activeEncounterIndex >= roomEncounters.size()) {
            return null;
        }
        return roomEncounters.get(activeEncounterIndex).getEnemy();
    }

    private EncounterNode getActiveEncounterNode() {
        if (activeEncounterIndex < 0 || activeEncounterIndex >= roomEncounters.size()) {
            return null;
        }
        return roomEncounters.get(activeEncounterIndex);
    }

    private boolean intersectsAnyEncounter(Rectangle candidate) {
        for (EncounterNode node : roomEncounters) {
            Rectangle rect = new Rectangle(node.getX(), node.getY(), ENCOUNTER_SIZE, ENCOUNTER_SIZE);
            if (candidate.intersects(rect)) {
                return true;
            }
        }
        return false;
    }

    private Rectangle getDoorRect() {
        int x;
        int y;

        switch (doorDirection) {
            case UP:
                x = ROOM_X + (ROOM_W - DOOR_H) / 2;
                y = ROOM_Y;
                return new Rectangle(x, y, DOOR_H, DOOR_W);

            case DOWN:
                x = ROOM_X + (ROOM_W - DOOR_H) / 2;
                y = ROOM_Y + ROOM_H - DOOR_W;
                return new Rectangle(x, y, DOOR_H, DOOR_W);

            case LEFT:
                x = ROOM_X;
                y = ROOM_Y + (ROOM_H - DOOR_H) / 2;
                return new Rectangle(x, y, DOOR_W, DOOR_H);

            case RIGHT:
            default:
                x = ROOM_X + ROOM_W - DOOR_W;
                y = ROOM_Y + (ROOM_H - DOOR_H) / 2;
                return new Rectangle(x, y, DOOR_W, DOOR_H);
        }
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    //directional room helper method
    private void positionPlayerFromEntry(Direction exitedDir) {
        // how far from the inner edge to place the player
        final int padding = 26;

        switch (exitedDir) {
            case LEFT:
                // came out the left door of previous room -> spawn near right side of new room
                playerX = ROOM_X + ROOM_W - padding;
                playerY = ROOM_Y + (ROOM_H / 2) - (PLAYER_SIZE / 2);
                break;
            case RIGHT:
                // came out the right door -> spawn near left side
                playerX = ROOM_X + padding;
                playerY = ROOM_Y + (ROOM_H / 2) - (PLAYER_SIZE / 2);
                break;
            case UP:
                // came out the top -> spawn near bottom
                playerX = ROOM_X + (ROOM_W / 2) - (PLAYER_SIZE / 2);
                playerY = ROOM_Y + ROOM_H - padding;
                break;
            case DOWN:
                // came out the bottom -> spawn near top
                playerX = ROOM_X + (ROOM_W / 2) - (PLAYER_SIZE / 2);
                playerY = ROOM_Y + padding;
                break;
            default:
                // fallback: stay roughly where your generator used to put you
                playerX = ROOM_X + 26;
                playerY = ROOM_Y + (ROOM_H / 2) - (PLAYER_SIZE / 2);
        }

        // make sure the player is inside the room bounds
        int minX = ROOM_X + 2;
        int minY = ROOM_Y + 2;
        int maxX = ROOM_X + ROOM_W - PLAYER_SIZE - 2;
        int maxY = ROOM_Y + ROOM_H - PLAYER_SIZE - 2;
        playerX = clampDouble(playerX, minX, maxX);
        playerY = clampDouble(playerY, minY, maxY);

        // If the spawn overlaps the door in the new room or an encounter, nudge a bit.
        // This avoids immediately triggering a new transition or spawning on top of an enemy.
        Rectangle spawnRect = new Rectangle((int)Math.round(playerX), (int)Math.round(playerY), PLAYER_SIZE, PLAYER_SIZE);
        Rectangle newDoor = getDoorRect();

        // If spawn intersects the new door, nudge away along the same axis a little.
        if (spawnRect.intersects(newDoor)) {
            if (exitedDir == Direction.LEFT || exitedDir == Direction.RIGHT) {
                // horizontal door -> nudge vertically a bit
                playerY = clampDouble(playerY + (PLAYER_SIZE + 6), minY, maxY);
            } else {
                // vertical door -> nudge horizontally a bit
                playerX = clampDouble(playerX + (PLAYER_SIZE + 6), minX, maxX);
            }
            spawnRect.setLocation((int)Math.round(playerX), (int)Math.round(playerY));
        }

        // If spawn intersects any encounter, try a few small offsets
        if (intersectsAnyEncounter(spawnRect)) {
            int tries = 6;
            int offset = 18;
            boolean placed = false;
            for (int i = 0; i < tries && !placed; i++) {
                // try offsets in a small cross pattern
                int dx = ((i % 3) - 1) * offset;
                int dy = ((i / 3) - 1) * offset;
                double tryX = clampDouble(playerX + dx, minX, maxX);
                double tryY = clampDouble(playerY + dy, minY, maxY);
                Rectangle r = new Rectangle((int)Math.round(tryX), (int)Math.round(tryY), PLAYER_SIZE, PLAYER_SIZE);
                if (!intersectsAnyEncounter(r) && !r.intersects(newDoor)) {
                    playerX = tryX;
                    playerY = tryY;
                    placed = true;
                }
            }
            // if none of the offsets worked, we leave the clamped spawn — it's probably fine.
        }
    }

    private void setMovementHeld(Direction direction, boolean held) {
        setKeyboardMovementHeld(direction, held);
    }

    private boolean isMovementHeld(Direction direction) {
        if (direction == Direction.UP) {
            return keyboardMoveUpHeld || controllerMoveUpHeld;
        }
        if (direction == Direction.DOWN) {
            return keyboardMoveDownHeld || controllerMoveDownHeld;
        }
        if (direction == Direction.LEFT) {
            return keyboardMoveLeftHeld || controllerMoveLeftHeld;
        }
        return keyboardMoveRightHeld || controllerMoveRightHeld;
    }

    private void setKeyboardMovementHeld(Direction direction, boolean held) {
        if (direction == Direction.UP) {
            keyboardMoveUpHeld = held;
        } else if (direction == Direction.DOWN) {
            keyboardMoveDownHeld = held;
        } else if (direction == Direction.LEFT) {
            keyboardMoveLeftHeld = held;
        } else {
            keyboardMoveRightHeld = held;
        }
    }

    private void setControllerMovementHeld(Direction direction, boolean held) {
        if (direction == Direction.UP) {
            controllerMoveUpHeld = held;
        } else if (direction == Direction.DOWN) {
            controllerMoveDownHeld = held;
        } else if (direction == Direction.LEFT) {
            controllerMoveLeftHeld = held;
        } else {
            controllerMoveRightHeld = held;
        }
    }

    private void setMovementFromKeyCode(int keyCode, boolean held) {
        if (keyCode == KeyEvent.VK_W || keyCode == KeyEvent.VK_UP) {
            setMovementHeld(Direction.UP, held);
        } else if (keyCode == KeyEvent.VK_S || keyCode == KeyEvent.VK_DOWN) {
            setMovementHeld(Direction.DOWN, held);
        } else if (keyCode == KeyEvent.VK_A || keyCode == KeyEvent.VK_LEFT) {
            setMovementHeld(Direction.LEFT, held);
        } else if (keyCode == KeyEvent.VK_D || keyCode == KeyEvent.VK_RIGHT) {
            setMovementHeld(Direction.RIGHT, held);
        }
    }

    private void clearMovementInput() {
        keyboardMoveUpHeld = false;
        keyboardMoveDownHeld = false;
        keyboardMoveLeftHeld = false;
        keyboardMoveRightHeld = false;
        controllerMoveUpHeld = false;
        controllerMoveDownHeld = false;
        controllerMoveLeftHeld = false;
        controllerMoveRightHeld = false;
        lastTickNanos = System.nanoTime();
    }

    private void updateTimerBarAnimation(double deltaSeconds) {
        if (screen != ScreenState.ENCOUNTER) {
            clearTimerBarAnimation();
            return;
        }

        long targetTimeLeft = roundManager.getTimeLeftMs();
        long duration = Math.max(1L, roundManager.getRoundDurationMs());
        displayedTimerDurationMs = duration;

        if (timeoutRecoveryActive) {
            long elapsedMs = Math.max(0L, System.currentTimeMillis() - timeoutRecoveryStartMs);
            double progress = Math.max(0.0, Math.min(1.0, elapsedMs / (double) TIMEOUT_TIMER_REFILL_DURATION_MS));
            displayedTimerMs = Math.round(timeoutRecoveryTargetMs * progress);
            if (progress >= 1.0) {
                timeoutRecoveryActive = false;
                timeoutRecoveryStartMs = 0L;
                timeoutRecoveryTargetMs = 0L;
                displayedTimerMs = targetTimeLeft;
            }
            displayedTimerMs = Math.max(0L, Math.min(duration, displayedTimerMs));
            return;
        }

        if (!roundManager.getActiveArchetype().isTimeRecoveryEnabled()) {
            displayedTimerMs = Math.max(0L, Math.min(duration, targetTimeLeft));
            return;
        }

        if (displayedTimerMs < 0L) {
            displayedTimerMs = targetTimeLeft;
            return;
        }

        if (targetTimeLeft < displayedTimerMs) {
            displayedTimerMs = targetTimeLeft;
        } else if (targetTimeLeft > displayedTimerMs) {
            long refillStep = Math.max(1L, Math.round(TIMER_REFILL_ANIM_PER_SECOND * deltaSeconds));
            displayedTimerMs = Math.min(targetTimeLeft, displayedTimerMs + refillStep);
        }

        displayedTimerMs = Math.max(0L, Math.min(duration, displayedTimerMs));
    }

    private void updatePlayerHealthAnimation(double deltaSeconds) {
        if (screen == ScreenState.LOST) {
            displayedPlayerHealth = Math.max(0.0, Math.min(GameConfig.PLAYER_MAX_HEALTH, displayedPlayerHealth));
            return;
        }
        if (displayedPlayerHealth < playerHealth) {
            displayedPlayerHealth = playerHealth;
        }
        if (displayedPlayerHealth <= playerHealth) {
            displayedPlayerHealth = playerHealth;
            return;
        }
        if (System.currentTimeMillis() < heartDamageFlashUntilMs) {
            return;
        }

        displayedPlayerHealth = Math.max(
                playerHealth,
                displayedPlayerHealth - (HEART_DAMAGE_SLIDE_PER_SECOND * Math.max(0.0, deltaSeconds))
        );
    }

    private void updateEnemyHealthAnimation(double deltaSeconds) {
        if (screen != ScreenState.ENCOUNTER) {
            displayedEnemyHealth = -1;
            displayedEnemyRef = null;
            return;
        }
        EncounterEnemy enemy = getActiveEncounterEnemy();
        if (enemy == null) {
            displayedEnemyHealth = -1;
            displayedEnemyRef = null;
            return;
        }
        if (displayedEnemyRef != enemy || displayedEnemyHealth < 0) {
            displayedEnemyRef = enemy;
            displayedEnemyHealth = enemy.getHealth();
            return;
        }

        int actualHealth = enemy.getHealth();
        if (displayedEnemyHealth <= actualHealth) {
            displayedEnemyHealth = actualHealth;
            return;
        }

        double dropPerSecond = Math.max(40.0, enemy.getMaxHealth() * 5.0);
        int drop = Math.max(1, (int) Math.round(dropPerSecond * deltaSeconds));
        displayedEnemyHealth = Math.max(actualHealth, displayedEnemyHealth - drop);
    }

    private void resetTimerBarAnimation() {
        long timeLeft = roundManager.getTimeLeftMs();
        displayedTimerMs = timeLeft;
        displayedTimerDurationMs = Math.max(1L, roundManager.getRoundDurationMs());
        timeoutRecoveryActive = false;
        timeoutRecoveryStartMs = 0L;
        timeoutRecoveryTargetMs = 0L;
    }

    private void clearTimerBarAnimation() {
        displayedTimerMs = -1L;
        displayedTimerDurationMs = 1L;
        timeoutRecoveryActive = false;
        timeoutRecoveryStartMs = 0L;
        timeoutRecoveryTargetMs = 0L;
    }

    private void drawEncounterTimerBorder(Graphics2D g2d, int x, int y, int width, int height) {
        long timeLeft = displayedTimerMs >= 0L ? displayedTimerMs : roundManager.getTimeLeftMs();
        long duration = displayedTimerDurationMs > 0L ? displayedTimerDurationMs : roundManager.getRoundDurationMs();
        double progress = duration > 0 ? (double) timeLeft / duration : 0.0;
        progress = Math.max(0.0, Math.min(1.0, progress));

        double danger = 1.0 - progress;
        Color activeColor = lerpColor(TIMER_HIGH, TIMER_LOW, danger);
        if (timeoutRecoveryActive) {
            double pulse = 0.5 + (0.5 * Math.sin(System.currentTimeMillis() / 90.0));
            activeColor = lerpColor(new Color(110, 214, 255), WHITE, pulse * 0.45);
        }
        if (progress < 0.28) {
            double pulse = 0.5 + (0.5 * Math.sin(System.currentTimeMillis() / 80.0));
            activeColor = lerpColor(activeColor, WHITE, pulse * 0.45);
        }

        int borderInset = 7;
        int bx = x - borderInset;
        int by = y - borderInset;
        int bw = width + (borderInset * 2);
        int bh = height + (borderInset * 2);

        Stroke old = g2d.getStroke();
        g2d.setStroke(new BasicStroke(5f));
        g2d.setColor(new Color(30, 68, 116, 160));
        g2d.drawRect(bx, by, bw, bh);

        g2d.setColor(new Color(activeColor.getRed(), activeColor.getGreen(), activeColor.getBlue(), 240));
        g2d.drawRect(bx, by, bw, bh);

        double perimeter = (bw * 2.0) + (bh * 2.0);
        double depletedLength = perimeter * (1.0 - progress);
        if (depletedLength > 0.0) {
            g2d.setColor(new Color(6, 16, 34, 235));
            drawCounterClockwiseBorderSegment(g2d, bx, by, bw, bh, depletedLength);
        }

        g2d.setStroke(old);
    }

    private void drawCounterClockwiseBorderSegment(Graphics2D g2d, int x, int y, int width, int height, double length) {
        double remaining = Math.max(0.0, length);
        int xLeft = x;
        int xRight = x + width;
        int yTop = y;
        int yBottom = y + height;

        if (remaining > 0.0) {
            double segment = Math.min(remaining, height);
            int yEnd = yTop + (int) Math.round(segment);
            g2d.drawLine(xLeft, yTop, xLeft, yEnd);
            remaining -= segment;
        }
        if (remaining > 0.0) {
            double segment = Math.min(remaining, width);
            int xEnd = xLeft + (int) Math.round(segment);
            g2d.drawLine(xLeft, yBottom, xEnd, yBottom);
            remaining -= segment;
        }
        if (remaining > 0.0) {
            double segment = Math.min(remaining, height);
            int yEnd = yBottom - (int) Math.round(segment);
            g2d.drawLine(xRight, yBottom, xRight, yEnd);
            remaining -= segment;
        }
        if (remaining > 0.0) {
            double segment = Math.min(remaining, width);
            int xEnd = xRight - (int) Math.round(segment);
            g2d.drawLine(xRight, yTop, xEnd, yTop);
        }
    }

    private void drawArrow(Graphics2D g2d, Direction direction, int x, int y, int boxSize, Color color) {
        int cx = x + (boxSize / 2);
        int cy = y + (boxSize / 2);
        int shaft = boxSize / 4;
        int head = boxSize / 6;

        Stroke oldStroke = g2d.getStroke();
        g2d.setStroke(new BasicStroke(5f));
        g2d.setColor(color);

        if (direction == Direction.UP) {
            g2d.drawLine(cx, cy + shaft, cx, cy - shaft);
            g2d.drawLine(cx, cy - shaft, cx - head, cy - shaft + head);
            g2d.drawLine(cx, cy - shaft, cx + head, cy - shaft + head);
        } else if (direction == Direction.DOWN) {
            g2d.drawLine(cx, cy - shaft, cx, cy + shaft);
            g2d.drawLine(cx, cy + shaft, cx - head, cy + shaft - head);
            g2d.drawLine(cx, cy + shaft, cx + head, cy + shaft - head);
        } else if (direction == Direction.LEFT) {
            g2d.drawLine(cx + shaft, cy, cx - shaft, cy);
            g2d.drawLine(cx - shaft, cy, cx - shaft + head, cy - head);
            g2d.drawLine(cx - shaft, cy, cx - shaft + head, cy + head);
        } else {
            g2d.drawLine(cx - shaft, cy, cx + shaft, cy);
            g2d.drawLine(cx + shaft, cy, cx + shaft - head, cy - head);
            g2d.drawLine(cx + shaft, cy, cx + shaft - head, cy + head);
        }

        g2d.setStroke(oldStroke);
    }

    private void drawArrowSprite(Graphics2D g2d, BufferedImage sprite, int x, int y, int boxSize) {
        int padding = 8;
        int size = boxSize - (padding * 2);
        g2d.drawImage(sprite, x + padding, y + padding, size, size, null);
    }

    private void loadArrowSprites() {
        arrowSprites.put(Direction.UP, GameImageLoader.loadImage(getClass(), "arrow_up.png"));
        arrowSprites.put(Direction.DOWN, GameImageLoader.loadImage(getClass(), "arrow_down.png"));
        arrowSprites.put(Direction.LEFT, GameImageLoader.loadImage(getClass(), "arrow_left.png"));
        arrowSprites.put(Direction.RIGHT, GameImageLoader.loadImage(getClass(), "arrow_right.png"));

        for (Direction direction : Direction.values()) {
            BufferedImage sprite = arrowSprites.get(direction);
            if (sprite != null) {
                arrowSpritesGreen.put(direction, GameImageLoader.tintSprite(sprite, GREEN));
            }
        }
    }

    private void loadSequenceSprites() {
        sequenceIdleSprite = GameImageLoader.loadImage(getClass(), "idle.png");
        sequencePunch1Sprite = GameImageLoader.loadImage(getClass(), "punch1.png");
        sequencePunch2Sprite = GameImageLoader.loadImage(getClass(), "punch2.png");
        sequencePunch3Sprite = GameImageLoader.loadImage(getClass(), "punch3.png");
    }

    private void loadHeartSprites() {
        fullHeartSprite = GameImageLoader.loadImage(getClass(), "full_heart.png");
        damageFlashHeartSprite = fullHeartSprite != null ? GameImageLoader.tintSprite(fullHeartSprite, new Color(255, 176, 236)) : null;
        damagedHeartSprite = fullHeartSprite != null ? GameImageLoader.tintSprite(fullHeartSprite, new Color(255, 156, 228)) : null;
        emptyHeartSprite = GameImageLoader.loadImage(getClass(), "empty_heart.png");
    }

    private void loadTransitionSprites() {
        megamanTransitionSprite = GameImageLoader.loadImage(getClass(), "megaman.png");
    }

    private void loadMenuSprites() {
        startMenuSprite = GameImageLoader.loadImage(getClass(), "START.png");
        openingTextSprite = GameImageLoader.loadImage(getClass(), "opening_text.png");
        openingStaticGif = GameImageLoader.loadAnimatedImage(getClass(), "startup_static.gif");
        openingStaticSequenceMs = GameImageLoader.loadGifDurationMillis(getClass(), "startup_static.gif");
        poisonIconSprite = GameImageLoader.loadImage(getClass(), "poison_icon.png");
        poisonIconAttack1Sprite = GameImageLoader.loadImage(getClass(), "poison_icon_attack1.png");
        poisonIconAttack2Sprite = GameImageLoader.loadImage(getClass(), "poison_icon_attack2.png");
        for (int i = 0; i < initialSurgeSprites.length; i++) {
            initialSurgeSprites[i] = GameImageLoader.loadImage(getClass(), "initial_surge" + (i + 1) + ".png");
        }
    }

    private void drawHeartHud(Graphics2D g2d) {
        int availableWidth = GameConfig.WIDTH - (HEART_BG_MARGIN_X * 2) - ((MAX_HEARTS - 1) * HEART_GAP);
        int heartSize = Math.max(24, availableWidth / MAX_HEARTS);
        int startX = HEART_BG_MARGIN_X + Math.max(0, (availableWidth - (heartSize * MAX_HEARTS)) / 2);
        int y = HEART_BG_Y;
        long now = System.currentTimeMillis();
        boolean damageFlashActive = now < heartDamageFlashUntilMs;
        Composite oldComposite = g2d.getComposite();

        for (int i = 0; i < MAX_HEARTS; i++) {
            int x = startX + (i * (heartSize + HEART_GAP));
            double heartFill = Math.max(0.0, Math.min(1.0, playerHealth - i));
            double displayedFill = Math.max(0.0, Math.min(1.0, displayedPlayerHealth - i));

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, HEART_BG_ALPHA));
            if (emptyHeartSprite != null) {
                g2d.drawImage(emptyHeartSprite, x, y, heartSize, heartSize, null);
            } else {
                drawFallbackHeartBackground(g2d, x, y, heartSize);
            }

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, HEART_BG_ALPHA));
            if (heartFill > 0.0) {
                if (fullHeartSprite != null) {
                    Shape oldClip = g2d.getClip();
                    int fillWidth = Math.max(1, Math.min(heartSize, (int) Math.round(heartSize * heartFill)));
                    g2d.clipRect(x, y, fillWidth, heartSize);
                    g2d.drawImage(fullHeartSprite, x, y, heartSize, heartSize, null);
                    g2d.setClip(oldClip);
                } else {
                    drawFallbackHeartFill(g2d, x, y, heartFill, heartSize);
                }
            }

            double damageFill = Math.max(0.0, displayedFill - heartFill);
            if (damageFill > 0.0) {
                int fillStart = x + (int) Math.round(heartSize * heartFill);
                int fillWidth = Math.max(1, (int) Math.round(heartSize * damageFill));
                BufferedImage damageSprite = damageFlashActive && damageFlashHeartSprite != null
                        ? damageFlashHeartSprite
                        : damagedHeartSprite;
                if (damageSprite != null) {
                    Shape oldClip = g2d.getClip();
                    Composite damageComposite = g2d.getComposite();
                    g2d.setComposite(AlphaComposite.getInstance(
                            AlphaComposite.SRC_OVER,
                            damageFlashActive ? HEART_DAMAGE_FLASH_ALPHA : HEART_DAMAGE_PENDING_ALPHA
                    ));
                    g2d.clipRect(fillStart, y, fillWidth, heartSize);
                    g2d.drawImage(damageSprite, x, y, heartSize, heartSize, null);
                    g2d.setClip(oldClip);
                    g2d.setComposite(damageComposite);
                } else {
                    g2d.setColor(damageFlashActive
                            ? new Color(255, 176, 236, 215)
                            : new Color(255, 156, 228, 175));
                    g2d.fillRect(fillStart, y + 2, fillWidth, Math.max(1, heartSize - 4));
                }
            }
        }
        g2d.setComposite(oldComposite);

        g2d.setFont(SMALL_FONT);
        g2d.setColor(TEXT_DIM);
        g2d.drawString("COINS " + coinCount, 28, 86);
        g2d.drawString("GUARDS " + mistakeGuardCharges, 28, 106);
        if (nextEncounterTimeBonusMs > 0L) {
            g2d.drawString("NEXT +" + nextEncounterTimeBonusMs + "MS", 28, 126);
        }

        if (now < lastCoinGainUntilMs && lastCoinGain != 0) {
            double popProgress = 1.0 - ((lastCoinGainUntilMs - now) / 760.0);
            int yOffset = (int) Math.round(10 * popProgress);
            int alpha = (int) Math.round(255 * (1.0 - popProgress));
            alpha = Math.max(0, Math.min(255, alpha));
            String deltaLabel = (lastCoinGain > 0 ? "+" : "") + lastCoinGain + " COINS";
            g2d.setColor(new Color(255, 214, 112, alpha));
            g2d.drawString(deltaLabel, 28, 70 - yOffset);
        }
    }

    private void drawFallbackHeartBackground(Graphics2D g2d, int x, int y, int heartSize) {
        g2d.setColor(new Color(90, 96, 112));
        g2d.fillRect(x + 2, y + 2, heartSize - 4, heartSize - 4);
        g2d.setColor(WHITE);
        g2d.drawRect(x, y, heartSize, heartSize);
    }

    private void drawFallbackHeartFill(Graphics2D g2d, int x, int y, double fillRatio, int heartSize) {
        int fillWidth = Math.max(1, Math.min(heartSize - 4, (int) Math.round((heartSize - 4) * fillRatio)));
        g2d.setColor(RED);
        g2d.fillRect(x + 2, y + 2, fillWidth, heartSize - 4);
        g2d.setColor(WHITE);
        g2d.drawRect(x, y, heartSize, heartSize);
    }

    private void drawFrame(Graphics2D g2d, int x, int y, int w, int h, int thickness, Color color) {
        Stroke old = g2d.getStroke();
        g2d.setStroke(new BasicStroke(thickness + 2f));
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 85));
        g2d.drawRect(x, y, w, h);
        g2d.setStroke(new BasicStroke(thickness));
        g2d.setColor(color);
        g2d.drawRect(x, y, w, h);
        if (w > 4 && h > 4) {
            g2d.setStroke(new BasicStroke(1f));
            g2d.setColor(new Color(255, 255, 255, 70));
            g2d.drawRect(x + 1, y + 1, w - 2, h - 2);
        }
        g2d.setStroke(old);
    }

    private void drawEncounterTransition(Graphics2D g2d) {
        drawEncounterEnemySlide(g2d, encounterTransitionStartMs, ENCOUNTER_TRANSITION_MS, ENCOUNTER_TRANSITION_HOLD_MS, getPendingEncounterEnemy());
    }

    private void drawEncounterBestedTransition(Graphics2D g2d) {
        long elapsedMs = System.currentTimeMillis() - encounterBestedTransitionStartMs;
        long statusDurationMs = ENCOUNTER_TRANSITION_MS + ENCOUNTER_TRANSITION_HOLD_MS;
        if (elapsedMs <= statusDurationMs) {
            drawEncounterStatusSlide(
                    g2d,
                    encounterBestedTransitionStartMs,
                    ENCOUNTER_TRANSITION_MS,
                    ENCOUNTER_TRANSITION_HOLD_MS,
                    "ENEMY BESTED",
                    GREEN
            );
            return;
        }

        double outroProgress = (elapsedMs - statusDurationMs) / (double) ENCOUNTER_INTRO_MS;
        outroProgress = Math.max(0.0, Math.min(1.0, outroProgress));
        int alpha = clampInt((int) Math.round(255 * (1.0 - outroProgress)), 0, 255);
        if (alpha <= 0) {
            return;
        }
        g2d.setColor(new Color(0, 0, 0, alpha));
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
    }

    private void drawRoomTransition(Graphics2D g2d) {
        drawDirectionalRoomTransition(
                g2d,
                roomTransitionStartMs,
                ROOM_TRANSITION_MS,
                ROOM_TRANSITION_HOLD_MS,
                "NEXT ROOM",
                pendingRoomEntryDirection
        );
    }

    private void drawDirectionalRoomTransition(
            Graphics2D g2d,
            long startMs,
            long transitionMs,
            long holdMs,
            String text,
            Direction direction
    ) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        double totalProgress = elapsedMs / (double) (transitionMs + holdMs);
        totalProgress = Math.max(0.0, Math.min(1.0, totalProgress));

        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        g2d.setFont(HUD_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int centerX = GameConfig.WIDTH / 2;
        int centerY = GameConfig.HEIGHT / 2;
        int textTopY = centerY - (metrics.getHeight() / 2);
        int textBaselineY = textTopY + metrics.getAscent();

        if (direction == null) {
            direction = Direction.RIGHT;
        }

        boolean horizontal = direction == Direction.LEFT || direction == Direction.RIGHT;
        int startPos;
        int centerPos;
        int exitPos;
        if (horizontal) {
            int textWidth = metrics.stringWidth(text);
            int offscreen = (textWidth / 2) + 40;
            centerPos = centerX;
            if (direction == Direction.RIGHT) {
                startPos = -offscreen;
                exitPos = GameConfig.WIDTH + offscreen;
            } else {
                startPos = GameConfig.WIDTH + offscreen;
                exitPos = -offscreen;
            }
        } else {
            int textHeight = metrics.getHeight();
            int offscreen = textHeight + 40;
            centerPos = textBaselineY;
            if (direction == Direction.DOWN) {
                startPos = -offscreen;
                exitPos = GameConfig.HEIGHT + offscreen;
            } else {
                startPos = GameConfig.HEIGHT + offscreen;
                exitPos = -offscreen;
            }
        }

        int textPos;
        int textAlpha;
        if (totalProgress < 0.78) {
            double enterProgress = Math.max(0.0, Math.min(1.0, (totalProgress - 0.12) / 0.66));
            double easeOut = 1.0 - Math.pow(1.0 - enterProgress, 3.0);
            double textEased = (0.68 * easeOut) + (0.32 * enterProgress);
            textPos = (int) Math.round(startPos + ((centerPos - startPos) * textEased));
            textAlpha = (int) Math.round(255 * enterProgress);
        } else {
            double exitProgress = Math.max(0.0, Math.min(1.0, (totalProgress - 0.78) / 0.22));
            double exitEased = (0.34 * exitProgress) + (0.66 * Math.pow(exitProgress, 1.9));
            textPos = (int) Math.round(centerPos + ((exitPos - centerPos) * exitEased));
            textAlpha = 255;
        }
        textAlpha = Math.max(0, Math.min(255, textAlpha));

        int textCenterX = horizontal ? textPos : centerX;
        int textBaseline = horizontal ? textBaselineY : textPos;
        drawGlowingCenteredString(
                g2d,
                text,
                textCenterX,
                textBaseline,
                new Color(WHITE.getRed(), WHITE.getGreen(), WHITE.getBlue(), textAlpha),
                new Color(GLOW_CYAN.getRed(), GLOW_CYAN.getGreen(), GLOW_CYAN.getBlue(), Math.max(20, textAlpha / 2))
        );
    }

    private void drawHorizontalShutterTransition(
            Graphics2D g2d,
            long startMs,
            long transitionMs,
            long holdMs,
            String text,
            EncounterEnemy encounterEnemy
    ) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        double progress = elapsedMs / (double) transitionMs;
        progress = Math.max(0.0, Math.min(1.0, progress));
        double totalProgress = elapsedMs / (double) (transitionMs + holdMs);
        totalProgress = Math.max(0.0, Math.min(1.0, totalProgress));

        double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
        int centerY = GameConfig.HEIGHT / 2;
        g2d.setFont(HUD_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int textTopY = centerY - (metrics.getHeight() / 2);
        int textBaselineY = textTopY + metrics.getAscent();
        int linePadding = 16;
        int halfBand = (metrics.getHeight() / 2) + linePadding;
        int targetTopY = centerY - halfBand;
        int targetBottomY = centerY + halfBand;

        int topLineY = (int) Math.round(targetTopY * eased);
        int bottomLineY = (int) Math.round(GameConfig.HEIGHT - ((GameConfig.HEIGHT - targetBottomY) * eased));

        g2d.setColor(BG);
        g2d.fillRect(0, 0, GameConfig.WIDTH, topLineY);
        g2d.fillRect(0, bottomLineY, GameConfig.WIDTH, GameConfig.HEIGHT - bottomLineY);
        int centerBandY = Math.max(0, topLineY + 1);
        int centerBandHeight = Math.max(0, bottomLineY - topLineY - 1);
        if (centerBandHeight > 0) {
            g2d.fillRect(0, centerBandY, GameConfig.WIDTH, centerBandHeight);
        }

        g2d.setColor(WHITE);
        g2d.drawLine(0, topLineY, GameConfig.WIDTH, topLineY);
        g2d.drawLine(0, bottomLineY, GameConfig.WIDTH, bottomLineY);

        int textWidth = metrics.stringWidth(text);
        int centerX = GameConfig.WIDTH / 2;
        int startCenterX = GameConfig.WIDTH + (textWidth / 2) + 40;
        int handoffCenterX = centerX - ENCOUNTER_TEXT_HANDOFF_OFFSET;

        int textCenterX;
        int textAlpha;
        if (totalProgress < 0.78) {
            double enterProgress = Math.max(0.0, Math.min(1.0, (totalProgress - 0.12) / 0.66));
            double easeOut = 1.0 - Math.pow(1.0 - enterProgress, 3.0);
            double textEased = (0.68 * easeOut) + (0.32 * enterProgress);
            textCenterX = (int) Math.round(startCenterX + ((centerX - startCenterX) * textEased));
            textAlpha = (int) Math.round(255 * enterProgress);
        } else {
            double exitProgress = Math.max(0.0, Math.min(1.0, (totalProgress - 0.78) / 0.22));
            double exitEased = (0.34 * exitProgress) + (0.66 * Math.pow(exitProgress, 1.9));
            textCenterX = (int) Math.round(centerX + ((handoffCenterX - centerX) * exitEased));
            textAlpha = 255;
        }
        textAlpha = Math.max(0, Math.min(255, textAlpha));
        drawGlowingCenteredString(
                g2d,
                text,
                textCenterX,
                textBaselineY,
                new Color(WHITE.getRed(), WHITE.getGreen(), WHITE.getBlue(), textAlpha),
                new Color(GLOW_CYAN.getRed(), GLOW_CYAN.getGreen(), GLOW_CYAN.getBlue(), Math.max(20, textAlpha / 2))
        );
    }

    private void drawEncounterIntro(Graphics2D g2d) {
        drawTransitionIntro(g2d, encounterIntroStartMs, ENCOUNTER_INTRO_MS, "", null);
    }

    private void drawRoomIntro(Graphics2D g2d) {
        drawTransitionIntro(g2d, roomIntroStartMs, ROOM_INTRO_MS, "", null);
    }

    private void drawTransitionIntro(Graphics2D g2d, long startMs, long introMs, String text, EncounterEnemy encounterEnemy) {
        double progress = (System.currentTimeMillis() - startMs) / (double) introMs;
        progress = Math.max(0.0, Math.min(1.0, progress));

        int overlayAlpha = (int) Math.round(255 * (1.0 - progress));
        g2d.setColor(new Color(0, 0, 0, overlayAlpha));
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        g2d.setFont(HUD_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int centerY = GameConfig.HEIGHT / 2;
        int textTopY = centerY - (metrics.getHeight() / 2);
        int textBaselineY = textTopY + metrics.getAscent();

        int startCenterX = (GameConfig.WIDTH / 2) - ENCOUNTER_TEXT_HANDOFF_OFFSET;
        int endCenterX = -((textWidth / 2) + 40);
        double exitEased = (0.42 * progress) + (0.58 * Math.pow(progress, 1.9));
        int textCenterX = (int) Math.round(startCenterX + ((endCenterX - startCenterX) * exitEased));

        int textAlpha = (int) Math.round(255 * (1.0 - (progress * 0.35)));
        textAlpha = Math.max(0, Math.min(255, textAlpha));
        drawGlowingCenteredString(
                g2d,
                text,
                textCenterX,
                textBaselineY,
                new Color(WHITE.getRed(), WHITE.getGreen(), WHITE.getBlue(), textAlpha),
                new Color(GLOW_CYAN.getRed(), GLOW_CYAN.getGreen(), GLOW_CYAN.getBlue(), Math.max(20, textAlpha / 2))
        );
    }

    private void drawDirectionalRoomIntro(
            Graphics2D g2d,
            long startMs,
            long introMs,
            String text,
            Direction direction
    ) {
        double progress = (System.currentTimeMillis() - startMs) / (double) introMs;
        progress = Math.max(0.0, Math.min(1.0, progress));

        int overlayAlpha = (int) Math.round(255 * (1.0 - progress));
        g2d.setColor(new Color(0, 0, 0, overlayAlpha));
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        g2d.setFont(HUD_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int centerX = GameConfig.WIDTH / 2;
        int centerY = GameConfig.HEIGHT / 2;
        int textTopY = centerY - (metrics.getHeight() / 2);
        int textBaselineY = textTopY + metrics.getAscent();

        if (direction == null) {
            direction = Direction.RIGHT;
        }

        boolean horizontal = direction == Direction.LEFT || direction == Direction.RIGHT;
        int startPos;
        int endPos;
        if (horizontal) {
            int offscreen = (textWidth / 2) + 40;
            if (direction == Direction.RIGHT) {
                startPos = centerX + ENCOUNTER_TEXT_HANDOFF_OFFSET;
                endPos = GameConfig.WIDTH + offscreen;
            } else {
                startPos = centerX - ENCOUNTER_TEXT_HANDOFF_OFFSET;
                endPos = -offscreen;
            }
        } else {
            int textHeight = metrics.getHeight();
            int offscreen = textHeight + 40;
            if (direction == Direction.DOWN) {
                startPos = textBaselineY + ENCOUNTER_TEXT_HANDOFF_OFFSET;
                endPos = GameConfig.HEIGHT + offscreen;
            } else {
                startPos = textBaselineY - ENCOUNTER_TEXT_HANDOFF_OFFSET;
                endPos = -offscreen;
            }
        }

        double exitEased = (0.42 * progress) + (0.58 * Math.pow(progress, 1.9));
        int textPos = (int) Math.round(startPos + ((endPos - startPos) * exitEased));

        int textAlpha = (int) Math.round(255 * (1.0 - (progress * 0.35)));
        textAlpha = Math.max(0, Math.min(255, textAlpha));
        int textCenterX = horizontal ? textPos : centerX;
        int textBaseline = horizontal ? textBaselineY : textPos;
        drawGlowingCenteredString(
                g2d,
                text,
                textCenterX,
                textBaseline,
                new Color(WHITE.getRed(), WHITE.getGreen(), WHITE.getBlue(), textAlpha),
                new Color(GLOW_CYAN.getRed(), GLOW_CYAN.getGreen(), GLOW_CYAN.getBlue(), Math.max(20, textAlpha / 2))
        );
    }

    private void drawEncounterEnemySlide(
            Graphics2D g2d,
            long startMs,
            long transitionMs,
            long holdMs,
            EncounterEnemy encounterEnemy
    ) {
        if (encounterEnemy == null) {
            return;
        }
        Color accent = getEnemyAccentColor(encounterEnemy);
        String label = encounterEnemy.getArchetype().getLabel();
        drawEncounterStatusSlide(g2d, startMs, transitionMs, holdMs, label, accent);
    }

    private void drawEncounterStatusSlide(
            Graphics2D g2d,
            long startMs,
            long transitionMs,
            long holdMs,
            String label,
            Color accent
    ) {
        long totalDurationMs = Math.max(1L, transitionMs + holdMs);
        double progress = (System.currentTimeMillis() - startMs) / (double) totalDurationMs;
        progress = Math.max(0.0, Math.min(1.0, progress));

        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        g2d.setFont(TRANSITION_ENEMY_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = metrics.stringWidth(label);
        int textBaselineY = GameConfig.HEIGHT - 92;
        int startX = -textWidth - 60;
        int centerX = (GameConfig.WIDTH - textWidth) / 2;
        int exitX = GameConfig.WIDTH + 60;

        int textX;
        if (progress < 0.78) {
            double enterProgress = Math.max(0.0, Math.min(1.0, (progress - 0.12) / 0.66));
            double eased = (0.68 * easeOutCubic(enterProgress)) + (0.32 * enterProgress);
            textX = (int) Math.round(startX + ((centerX - startX) * eased));
        } else {
            double exitProgress = Math.max(0.0, Math.min(1.0, (progress - 0.78) / 0.22));
            double eased = (0.34 * exitProgress) + (0.66 * Math.pow(exitProgress, 1.9));
            textX = (int) Math.round(centerX + ((exitX - centerX) * eased));
        }

        int glowAlpha = clampInt((int) Math.round(255 * Math.min(1.0, progress * 1.4)), 0, 255);
        drawEncounterTransitionSprite(g2d, progress, glowAlpha, textX, textBaselineY);
        g2d.setFont(TRANSITION_ENEMY_FONT);
        drawGlowingString(
                g2d,
                label,
                textX,
                textBaselineY,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), glowAlpha),
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.max(20, glowAlpha / 2))
        );
    }

    private void drawEncounterTransitionSprite(Graphics2D g2d, double progress, int alpha, int textX, int textBaselineY) {
        if (megamanTransitionSprite == null || alpha <= 0) {
            return;
        }

        int targetHeight = 460;
        int targetWidth = (int) Math.round(targetHeight * (megamanTransitionSprite.getWidth() / (double) megamanTransitionSprite.getHeight()));
        int spriteY = textBaselineY - targetHeight - 126;
        int settledX = GameConfig.WIDTH - targetWidth - 40;
        int startX = GameConfig.WIDTH + 80;
        int spriteX;
        if (progress < 0.74) {
            double enterProgress = Math.max(0.0, Math.min(1.0, (progress - 0.08) / 0.58));
            double eased = (0.72 * easeOutCubic(enterProgress)) + (0.28 * enterProgress);
            spriteX = (int) Math.round(startX + ((settledX - startX) * eased));
        } else {
            double exitProgress = Math.max(0.0, Math.min(1.0, (progress - 0.74) / 0.26));
            double eased = (0.24 * exitProgress) + (0.76 * Math.pow(exitProgress, 1.85));
            int exitX = -targetWidth - 120;
            spriteX = (int) Math.round(settledX + ((exitX - settledX) * eased));
        }

        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.15f, alpha / 255f)));
        g2d.drawImage(megamanTransitionSprite, spriteX, spriteY, targetWidth, targetHeight, null);
        g2d.setComposite(oldComposite);
    }

    private EncounterEnemy getPendingEncounterEnemy() {
        if (pendingEncounterIndex < 0 || pendingEncounterIndex >= roomEncounters.size()) {
            return null;
        }
        return roomEncounters.get(pendingEncounterIndex).getEnemy();
    }

    private Color getEnemyAccentColor(EncounterEnemy enemy) {
        if (enemy == null) {
            return GLOW_CYAN;
        }
        switch (enemy.getArchetype()) {
            case BERSERKER:
                return new Color(255, 126, 92);
            case ECHO:
                return new Color(255, 92, 198);
            case REVERSE:
                return new Color(151, 255, 120);
            case NORMAL:
            default:
                return GLOW_CYAN;
        }
    }

    private double easeOutCubic(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return 1.0 - Math.pow(1.0 - clamped, 3.0);
    }

    private void drawRunStartFadeIn(Graphics2D g2d) {
        double progress = (System.currentTimeMillis() - runStartFadeInStartMs) / (double) RUN_START_FADE_IN_MS;
        progress = Math.max(0.0, Math.min(1.0, progress));
        int alpha = clampInt((int) Math.round(255 * (1.0 - progress)), 0, 255);
        if (alpha <= 0) {
            return;
        }
        g2d.setColor(new Color(0, 0, 0, alpha));
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
    }

    private void drawMenuTransitionOverlay(Graphics2D g2d) {
        double progress = (System.currentTimeMillis() - menuTransitionStartMs) / (double) MENU_TRANSITION_MS;
        progress = Math.max(0.0, Math.min(1.0, progress));
        double alphaProgress = progress < 0.5 ? (progress / 0.5) : ((1.0 - progress) / 0.5);
        int alpha = clampInt((int) Math.round(255 * Math.max(0.0, alphaProgress)), 0, 255);
        if (alpha <= 0) {
            return;
        }
        g2d.setColor(new Color(0, 0, 0, alpha));
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
    }

    private void drawStartRunTransitionOverlay(Graphics2D g2d) {
        double progress = getStartRunTransitionProgress();
        double switchPoint = START_RUN_TRANSITION_SWITCH_MS / (double) START_RUN_TRANSITION_MS;
        double alphaProgress;
        if (progress <= switchPoint) {
            alphaProgress = easeInOut(progress / switchPoint);
        } else {
            alphaProgress = 1.0 - easeInOut((progress - switchPoint) / (1.0 - switchPoint));
        }
        int alpha = clampInt((int) Math.round(255 * Math.max(0.0, alphaProgress)), 0, 255);
        if (alpha <= 0) {
            return;
        }
        g2d.setColor(new Color(0, 0, 0, alpha));
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);
    }

    private void startMenuTransition() {
        clearMovementInput();
        backdropEffects.clearHueSweeps();
        startRunTransitionActive = false;
        encounterTransitionActive = false;
        pendingEncounterIndex = -1;
        encounterIntroActive = false;
        roomTransitionActive = false;
        roomIntroActive = false;
        pendingRoomEntryDirection = null;
        runStartFadeInActive = false;
        menuTransitionActive = true;
        menuTransitionStartMs = System.currentTimeMillis();
    }

    private void updateMenuTransition() {
        long elapsedMs = System.currentTimeMillis() - menuTransitionStartMs;
        if (elapsedMs >= MENU_TRANSITION_SWITCH_MS && screen != ScreenState.MENU) {
            screen = ScreenState.MENU;
        }
        if (elapsedMs >= MENU_TRANSITION_MS) {
            menuTransitionActive = false;
        }
    }

    private void updateStartRunTransition() {
        long elapsedMs = System.currentTimeMillis() - startRunTransitionStartMs;
        if (elapsedMs >= START_RUN_TRANSITION_SWITCH_MS && screen != ScreenState.DUNGEON) {
            screen = ScreenState.DUNGEON;
        }
        if (elapsedMs >= START_RUN_TRANSITION_MS) {
            startRunTransitionActive = false;
        }
    }

    private void updateOpeningSequence() {
        long elapsedMs = System.currentTimeMillis() - openingSequenceStartMs;
        if (elapsedMs >= OPENING_TEXT_SEQUENCE_MS) {
            ensureControllerPrewarmStarted();
        }
        if (!openingStaticSoundPlayed && elapsedMs >= OPENING_TEXT_SEQUENCE_MS) {
            openingStaticSoundPlayed = true;
            openingStaticSoundClip = AudioManager.playManagedSfx("startup_static.wav", -3.0f);
        }

        long openingStaticEndMs = OPENING_TEXT_SEQUENCE_MS + openingStaticSequenceMs;
        long fadeDurationMs = OPENING_STATIC_SOUND_FADE_LEAD_MS + OPENING_STATIC_SOUND_FADE_TAIL_MS;
        long fadeStartMs = Math.max(OPENING_TEXT_SEQUENCE_MS, openingStaticEndMs - OPENING_STATIC_SOUND_FADE_LEAD_MS);
        if (!openingStaticSoundFadeStarted && elapsedMs >= fadeStartMs) {
            openingStaticSoundFadeStarted = true;
        }
        if (openingStaticSoundFadeStarted && openingStaticSoundClip != null) {
            long fadeElapsedMs = Math.max(0L, elapsedMs - fadeStartMs);
            float fadeProgress = Math.max(0.0f, Math.min(1.0f, fadeElapsedMs / (float) fadeDurationMs));
            float gainDb = -3.0f + ((-77.0f) * fadeProgress);
            AudioManager.setManagedSfxGain(openingStaticSoundClip, gainDb);
        }
        if (elapsedMs < openingStaticEndMs) {
            return;
        }
        screen = ScreenState.MENU;
        runStartFadeInActive = true;
        runStartFadeInStartMs = System.currentTimeMillis();
    }

    private double getStartRunTransitionProgress() {
        if (!startRunTransitionActive) {
            return 1.0;
        }
        double progress = (System.currentTimeMillis() - startRunTransitionStartMs) / (double) START_RUN_TRANSITION_MS;
        return Math.max(0.0, Math.min(1.0, progress));
    }

    private void updateBackgroundMusic() {
        if (screen == ScreenState.OPENING) {
            AudioManager.stopBackgroundLoop();
            AudioManager.stopLayeredLoops();
            activeMusicFile = null;
            return;
        }

        double shopMix = easeInOut(shopMusicFade);
        double encounterMix = easeInOut(encounterMusicMix) * (1.0 - shopMix);
        double baseMix = Math.max(0.0, 1.0 - shopMix - encounterMix);
        boolean allowStartRunMusic = !startRunTransitionActive
                || (System.currentTimeMillis() - startRunTransitionStartMs) >= START_RUN_MUSIC_DELAY_MS;

        boolean inMenu = screen == ScreenState.MENU || screen == ScreenState.SETTINGS;
        if (inMenu) {
            if (!MENU_MUSIC_FILE.equals(activeMusicFile)) {
                AudioManager.ensureBackgroundLoop(MENU_MUSIC_FILE);
                activeMusicFile = MENU_MUSIC_FILE;
            }
            float menuFade = 1.0f;
            if (startRunTransitionActive) {
                double switchPoint = START_RUN_TRANSITION_SWITCH_MS / (double) START_RUN_TRANSITION_MS;
                double progress = Math.min(1.0, getStartRunTransitionProgress() / switchPoint);
                menuFade = (float) (1.0 - easeInOut(progress));
            }
            AudioManager.setBackgroundFade(menuFade);
            if (startRunTransitionActive && allowStartRunMusic) {
                AudioManager.setLayeredMix(
                        (float) baseMix,
                        (float) encounterMix,
                        (float) shopMix
                );
                AudioManager.ensureLayeredLoops(DUNGEON_MUSIC_FILE, encounterMusicFile, SHOP_MUSIC_FILE);
            } else {
                AudioManager.stopLayeredLoops();
            }
            return;
        }

        if (activeMusicFile != null) {
            AudioManager.stopBackgroundLoop();
            activeMusicFile = null;
        }

        if (allowStartRunMusic) {
            AudioManager.setLayeredMix(
                    (float) baseMix,
                    (float) encounterMix,
                    (float) shopMix
            );
            AudioManager.ensureLayeredLoops(DUNGEON_MUSIC_FILE, encounterMusicFile, SHOP_MUSIC_FILE);
        } else {
            AudioManager.stopLayeredLoops();
        }
    }

    private void drawCenteredString(Graphics2D g2d, String text, int centerX, int baselineY) {
        FontMetrics metrics = g2d.getFontMetrics();
        int x = centerX - (metrics.stringWidth(text) / 2);
        g2d.drawString(text, x, baselineY);
    }

    private void drawLetterboxFrame(
            Graphics2D g2d,
            int panelWidth,
            int panelHeight,
            int renderX,
            int renderY,
            int renderWidth,
            int renderHeight
    ) {
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, panelWidth, panelHeight);
    }

    private void drawSoul(Graphics2D g2d, int x, int y, int size, Color color) {
        int half = size / 2;
        int[] xs = {x + half, x + size, x + half, x};
        int[] ys = {y, y + half, y + size, y + half};
        g2d.setColor(color);
        g2d.fillPolygon(xs, ys, 4);
    }

    private double getEncounterTimerProgress() {
        long timeLeft = displayedTimerMs >= 0L ? displayedTimerMs : roundManager.getTimeLeftMs();
        long duration = displayedTimerDurationMs > 0L ? displayedTimerDurationMs : roundManager.getRoundDurationMs();
        if (duration <= 0L) {
            return 0.0;
        }
        double progress = timeLeft / (double) duration;
        return Math.max(0.0, Math.min(1.0, progress));
    }

    private void updateEnemyKillEffects() {
        enemyKillEffects.update();
    }

    private void drawGlowingCenteredString(
            Graphics2D g2d,
            String text,
            int centerX,
            int baselineY,
            Color core,
            Color glow
    ) {
        FontMetrics metrics = g2d.getFontMetrics();
        int x = centerX - (metrics.stringWidth(text) / 2);
        g2d.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), Math.min(220, glow.getAlpha())));
        g2d.drawString(text, x + 1, baselineY);
        g2d.drawString(text, x - 1, baselineY);
        g2d.drawString(text, x, baselineY + 1);
        g2d.drawString(text, x, baselineY - 1);
        g2d.setColor(core);
        g2d.drawString(text, x, baselineY);
    }

    private void drawGlowingString(
            Graphics2D g2d,
            String text,
            int x,
            int baselineY,
            Color core,
            Color glow
    ) {
        g2d.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), Math.min(220, glow.getAlpha())));
        g2d.drawString(text, x + 1, baselineY);
        g2d.drawString(text, x - 1, baselineY);
        g2d.drawString(text, x, baselineY + 1);
        g2d.drawString(text, x, baselineY - 1);
        g2d.setColor(core);
        g2d.drawString(text, x, baselineY);
    }
}
