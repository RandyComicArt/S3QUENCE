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
import java.awt.event.KeyEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

@SuppressWarnings({"serial", "this-escape"})
public class GamePanel extends JPanel {
    private static final long TARGET_FRAME_NANOS = 1_000_000_000L / 120L;
    private static final long MAX_FRAME_DELTA_NANOS = 50_000_000L;
    private static final Stroke STROKE_1 = new BasicStroke(1f);
    private static final Stroke STROKE_5 = new BasicStroke(5f);
    private static final Color DUNGEON_FRAME_PINK = new Color(255, 122, 200);
    private static final Color DUNGEON_FRAME_YELLOW = new Color(255, 232, 95);
    private static final Color EDGE_ANCHOR_BLUE = new Color(178, 240, 255);
    private static final Color HUD_LINE_WHITE = new Color(255, 255, 255, 210);
    private static final Color HUD_LINE_WHITE_DIM = new Color(255, 255, 255, 70);
    private static final int LEVEL_UP_CHOICE_COUNT = 3;

    private final Object stateLock = new Object();
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
    private BufferedImage radioOverlaySprite;
    private Image openingStaticGif;
    private long openingStaticSequenceMs = OPENING_STATIC_SEQUENCE_FALLBACK_MS;
    private BufferedImage poisonIconSprite;
    private BufferedImage poisonIconAttack1Sprite;
    private BufferedImage poisonIconAttack2Sprite;
    private BufferedImage poisonIconGlowSprite;
    private BufferedImage poisonIconHighlightSprite;
    private final BufferedImage[] initialSurgeSprites = new BufferedImage[5];
    private final BufferedImage[] initialSurgeGlowSprites = new BufferedImage[5];
    private BufferedImage sceneBuffer;
    private Thread gameLoopThread;
    private volatile boolean gameLoopRunning;

    private final Random random = new Random();
    private final List<EncounterNode> roomEncounters = new ArrayList<>();

    private ScreenState screen = ScreenState.OPENING;
    private int roomNumber = 1;
    private int roomWorldWidth = ROOM_WORLD_W;
    private int roomWorldHeight = ROOM_WORLD_H;
    private double playerX;
    private double playerY;
    private final List<Rectangle> roomPathSegments = new ArrayList<>();
    private Area roomWalkableArea = new Area();
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
    private RoomRenderState roomTransitionPreviousState;
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
    private Direction lastEnemyHitDirection = Direction.RIGHT;
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
    private int sequenceHitShakeOffsetMagnitude = 2;
    private int sequenceHitShakeVerticalBias;
    private int sequenceHitShakePhaseStepMs = 24;
    private int sequenceHitShakeDurationMs = 110;
    private float sequenceHitShakeAlphaScale = 1.0f;
    private int lastFinisherSfxIndex = -1;
    private int playerLevel = 1;
    private int playerXp;
    private int displayedPlayerXpLevel = 1;
    private double displayedPlayerXp;
    private int skillPoints;
    private int levelUpSelectionIndex;
    private boolean radioClosing;
    private int lastXpGain;
    private long lastXpGainUntilMs;
    private final int[] playerItemLevels = new int[ItemArchetype.values().length];
    private final List<ItemArchetype> levelUpChoices = new ArrayList<>();
    private double radioRevealProgress;
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

    private static final class RoomRenderState {
        private final int worldWidth;
        private final int worldHeight;
        private final double playerX;
        private final double playerY;
        private final Direction doorDirection;
        private final Area walkableArea;
        private final List<EncounterNode> encounters;

        private RoomRenderState(
                int worldWidth,
                int worldHeight,
                double playerX,
                double playerY,
                Direction doorDirection,
                Area walkableArea,
                List<EncounterNode> encounters
        ) {
            this.worldWidth = worldWidth;
            this.worldHeight = worldHeight;
            this.playerX = playerX;
            this.playerY = playerY;
            this.doorDirection = doorDirection;
            this.walkableArea = walkableArea;
            this.encounters = encounters;
        }
    }

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
    }

    @Override
    public void addNotify() {
        super.addNotify();
        startGameLoop();
    }

    @Override
    public void removeNotify() {
        stopGameLoop();
        super.removeNotify();
    }

    private void startGameLoop() {
        if (gameLoopRunning) {
            return;
        }
        gameLoopRunning = true;
        lastTickNanos = System.nanoTime();
        gameLoopThread = new Thread(this::runGameLoop, "s3quence-game-loop");
        gameLoopThread.setDaemon(true);
        gameLoopThread.start();
    }

    private void stopGameLoop() {
        gameLoopRunning = false;
        Thread loopThread = gameLoopThread;
        if (loopThread == null) {
            return;
        }
        loopThread.interrupt();
        if (Thread.currentThread() == loopThread) {
            return;
        }
        try {
            loopThread.join(250L);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } finally {
            gameLoopThread = null;
        }
    }

    private void runGameLoop() {
        long nextFrameNanos = System.nanoTime();
        while (gameLoopRunning) {
            long now = System.nanoTime();
            long elapsedNanos = now - lastTickNanos;
            lastTickNanos = now;
            double deltaSeconds = Math.min(elapsedNanos, MAX_FRAME_DELTA_NANOS) / 1_000_000_000.0;

            synchronized (stateLock) {
                updateGame(deltaSeconds);
            }
            repaint();

            nextFrameNanos += TARGET_FRAME_NANOS;
            long sleepNanos = nextFrameNanos - System.nanoTime();
            if (sleepNanos > 0L) {
                LockSupport.parkNanos(sleepNanos);
            } else if (sleepNanos < -(TARGET_FRAME_NANOS * 4L)) {
                nextFrameNanos = System.nanoTime();
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        synchronized (stateLock) {
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
    }

    private void renderGameScene(Graphics2D gameG) {
        int gameWidth = GameConfig.WIDTH;
        int gameHeight = GameConfig.HEIGHT;

        gameG.setColor(BG);
        gameG.fillRect(0, 0, gameWidth, gameHeight);
        ScreenState backdropScreen = encounterTransitionActive ? ScreenState.ENCOUNTER : screen;
        backdropEffects.drawBackdrop(gameG, backdropScreen, game.model.TimerStyle.BACKDROP_HUE, getEncounterTimerProgress());

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

        if (screen == ScreenState.DUNGEON || screen == ScreenState.LEVEL_UP) {
            if (screen == ScreenState.LEVEL_UP || radioRevealProgress > 0.001) {
                drawDungeonRadioCarousel(gameG);
            } else if (roomTransitionActive) {
                // Room transition renders both the outgoing and incoming rooms itself.
            } else if (encounterTransitionActive) {
                // Encounter transition expands the dungeon panel itself.
            } else {
                drawDungeon(gameG);
            }
            if (encounterBestedTransitionActive) {
                drawEncounterBestedTransition(gameG);
            }
        } else {
            drawEncounterGameplay(gameG);
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

    private void updateGame(double deltaSeconds) {
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
                    && !roomIntroActive) {
                updateDungeonMovement(deltaSeconds);
            }
            if (encounterTransitionActive) {
                long elapsedMs = System.currentTimeMillis() - encounterTransitionStartMs;
                if (elapsedMs >= ENCOUNTER_TRANSITION_MS + ENCOUNTER_TRANSITION_HOLD_MS) {
                    encounterTransitionActive = false;
                    pendingEncounterIndex = -1;
                    roundManager.resumeForPlayerControl();
                    resetTimerBarAnimation();
                    screen = ScreenState.ENCOUNTER;
                    encounterIntroActive = false;
                    encounterIntroStartMs = 0L;
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
                    roomIntroActive = false;
                    roomIntroStartMs = 0L;
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
        updateRadioRevealAnimation(deltaSeconds);
        updateTimerBarAnimation(deltaSeconds);
        updatePlayerHealthAnimation(deltaSeconds);
        updateXpBarAnimation(deltaSeconds);
        updateEnemyHealthAnimation(deltaSeconds);
        updateBackgroundMusic();
        backdropEffects.update();
        updateEnemyKillEffects();
    }

    private void drawMenu(Graphics2D g2d) {
        int menuLeft = 70;
        int menuLineStep = 42;
        int menuBottomMargin = 56;
        int baseMenuStartY = GameConfig.HEIGHT - menuBottomMargin - (menuLineStep * 3);
        int menuLift = (int) Math.round(300 * easeInOut(settingsRevealProgress));
        int menuStartY = baseMenuStartY - menuLift;

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
        int menuBottomMargin = 56;
        int baseMenuStartY = GameConfig.HEIGHT - menuBottomMargin - (menuLineStep * 3);
        double eased = easeInOut(settingsRevealProgress);
        int menuLift = (int) Math.round(300 * eased);
        int menuStartY = baseMenuStartY - menuLift;

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

    private void updateRadioRevealAnimation(double deltaSeconds) {
        double target = screen == ScreenState.LEVEL_UP && !radioClosing ? 1.0 : 0.0;
        double rate = target > radioRevealProgress ? 1.1 : 1.1;
        radioRevealProgress = moveTowards(radioRevealProgress, target, rate * deltaSeconds);
        if (radioClosing && radioRevealProgress <= 0.001) {
            radioRevealProgress = 0.0;
            radioClosing = false;
            screen = ScreenState.DUNGEON;
        }
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
        if (snapshot.isRadioPressed()) {
            toggleRadioOverlay();
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
        if (screen == ScreenState.LEVEL_UP && !radioClosing && !menuTransitionActive && !startRunTransitionActive) {
            handleLevelUpDirection(direction);
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
        } else if (screen == ScreenState.LEVEL_UP && !radioClosing) {
            applySelectedLevelUpChoice();
        } else if (screen == ScreenState.LOST) {
            startRun();
        }
    }

    private void handleBackAction() {
        if (screen == ScreenState.LEVEL_UP) {
            closeRadioOverlay();
            return;
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

    private void handleLevelUpDirection(Direction direction) {
        if (levelUpChoices.isEmpty()) {
            return;
        }
        if (direction == Direction.UP || direction == Direction.LEFT) {
            levelUpSelectionIndex = (levelUpSelectionIndex - 1 + levelUpChoices.size()) % levelUpChoices.size();
        } else if (direction == Direction.DOWN || direction == Direction.RIGHT) {
            levelUpSelectionIndex = (levelUpSelectionIndex + 1) % levelUpChoices.size();
        } else {
            return;
        }
        AudioManager.playSfx("tab_switch.wav");
        controllerInputManager.rumble(MENU_NAV_RUMBLE_STRENGTH, MENU_NAV_RUMBLE_MS);
    }

    private void toggleRadioOverlay() {
        if (menuTransitionActive || startRunTransitionActive) {
            return;
        }
        if (screen == ScreenState.LEVEL_UP) {
            if (radioClosing || radioRevealProgress < 0.999) {
                return;
            }
            closeRadioOverlay();
            return;
        }
        if (screen != ScreenState.DUNGEON || encounterTransitionActive || encounterBestedTransitionActive
                || roomTransitionActive || roomIntroActive) {
            return;
        }
        if (radioRevealProgress > 0.001) {
            return;
        }
        openRadioOverlay();
    }

    private void openRadioOverlay() {
        boolean previewOnly = skillPoints <= 0 && radioOverlaySprite != null;
        if (skillPoints <= 0 && !previewOnly) {
            return;
        }
        if (skillPoints > 0) {
            populateLevelUpChoices();
        } else {
            levelUpChoices.clear();
        }
        levelUpSelectionIndex = 0;
        radioClosing = false;
        clearMovementInput();
        screen = ScreenState.LEVEL_UP;
        AudioManager.playSfx("toggle_tab.wav");
    }

    private void closeRadioOverlay() {
        if (screen != ScreenState.LEVEL_UP) {
            return;
        }
        levelUpChoices.clear();
        levelUpSelectionIndex = 0;
        radioClosing = true;
        AudioManager.playSfx("back_toggle.wav");
    }

    private void applySelectedLevelUpChoice() {
        if (levelUpChoices.isEmpty()) {
            return;
        }

        ItemArchetype choice = levelUpChoices.get(levelUpSelectionIndex);
        playerItemLevels[choice.ordinal()]++;
        skillPoints = Math.max(0, skillPoints - 1);
        levelUpChoices.clear();
        levelUpSelectionIndex = 0;
        clearActiveItemEffects();
        AudioManager.playSfx("bar_fill.wav");

        if (skillPoints > 0) {
            populateLevelUpChoices();
        } else {
            closeRadioOverlay();
        }
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
            if (!hasForcedTestItem(item)) {
                continue;
            }
            if (label.length() > 0) {
                label.append(" + ");
            }
            label.append(item.getLabel());
        }
        return label.toString();
    }

    private boolean hasForcedTestItem(ItemArchetype item) {
        return item != null && (forcedTestItemMask & (1 << item.ordinal())) != 0;
    }

    private boolean hasActiveItem(ItemArchetype item) {
        if (item == null) {
            return false;
        }
        return hasForcedTestItem(item) || playerItemLevels[item.ordinal()] > 0;
    }

    private int getItemLevel(ItemArchetype item) {
        if (item == null) {
            return 0;
        }
        int level = playerItemLevels[item.ordinal()];
        if (level <= 0 && ((forcedTestItemMask & (1 << item.ordinal())) != 0)) {
            return 1;
        }
        return level;
    }

    private boolean hasAnyPlayerItems() {
        for (int level : playerItemLevels) {
            if (level > 0) {
                return true;
            }
        }
        return false;
    }

    private List<ItemArchetype> getEnabledItems() {
        List<ItemArchetype> enabled = new ArrayList<>();
        for (ItemArchetype item : ItemArchetype.values()) {
            if (hasActiveItem(item)) {
                enabled.add(item);
            }
        }
        return enabled;
    }

    private String getLevelUpChoiceDescription(ItemArchetype item) {
        if (item == ItemArchetype.POISON) {
            return "BUILD TOXIN ON CORRECT INPUTS";
        }
        if (item == ItemArchetype.INITIAL_SURGE) {
            return "BONUS DAMAGE WHILE TIMER IS HIGH";
        }
        return "NEW UPGRADE";
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
        drawDungeon(g2d, roomWorldWidth, roomWorldHeight, playerX, playerY, doorDirection, roomWalkableArea, roomEncounters, true, true);
    }

    private void drawDungeon(Graphics2D g2d, RoomRenderState state) {
        drawDungeon(g2d, state.worldWidth, state.worldHeight, state.playerX, state.playerY, state.doorDirection, state.walkableArea, state.encounters, false, true);
    }

    private void drawDungeon(
            Graphics2D g2d,
            int worldWidth,
            int worldHeight,
            double renderPlayerX,
            double renderPlayerY,
            Direction renderDoorDirection,
            Area renderWalkableArea,
            List<EncounterNode> renderEncounters,
            boolean includeKillEffects,
            boolean drawRoomFrame
    ) {
        g2d.setColor(ROOM_GLASS);
        g2d.fillRect(ROOM_X + 2, ROOM_Y + 2, ROOM_W - 3, ROOM_H - 3);
        drawEdgeAnchors(g2d, ROOM_X, ROOM_Y, ROOM_W, ROOM_H, EDGE_ANCHOR_BLUE, 112);

        Shape oldClip = g2d.getClip();
        g2d.clipRect(ROOM_X + 1, ROOM_Y + 1, ROOM_W - 2, ROOM_H - 2);

        int cameraX = getCameraX(renderPlayerX, worldWidth);
        int cameraY = getCameraY(renderPlayerY, worldHeight);
        g2d.translate(ROOM_X - cameraX, ROOM_Y - cameraY);

        drawDungeonWorldBackdrop(g2d, renderWalkableArea);
        drawDungeonWorldDoor(g2d, getDoorRect(renderDoorDirection, worldWidth, worldHeight), renderEncounters);
        drawDungeonWorldEncounters(g2d, renderEncounters);
        if (includeKillEffects) {
            enemyKillEffects.draw(g2d, 0, 0, worldWidth, worldHeight);
        }
        drawSoul(g2d, (int) Math.round(renderPlayerX), (int) Math.round(renderPlayerY), PLAYER_SIZE, YELLOW);

        g2d.translate(cameraX - ROOM_X, cameraY - ROOM_Y);
        g2d.setClip(oldClip);
    }

    private void drawDungeonRadioCarousel(Graphics2D g2d) {
        double eased = radioClosing
                ? 1.0 - easeOutCubic(1.0 - radioRevealProgress)
                : easeOutCubic(radioRevealProgress);
        int travelDistance = ROOM_H + 220;
        int dungeonOffsetY = -(int) Math.round(travelDistance * eased);
        int radioOffsetY = travelDistance - (int) Math.round(travelDistance * eased);
        double outgoingScale = 1.0 + (0.11 * Math.min(1.0, radioRevealProgress / 0.42));
        double incomingScale = 1.16 - (0.16 * eased);
        int roomCenterX = ROOM_X + (ROOM_W / 2);
        int roomCenterY = ROOM_Y + (ROOM_H / 2);

        Graphics2D dungeonG = (Graphics2D) g2d.create();
        dungeonG.translate(roomCenterX, roomCenterY + dungeonOffsetY);
        dungeonG.scale(outgoingScale, outgoingScale);
        dungeonG.translate(-roomCenterX, -roomCenterY);
        drawDungeon(dungeonG);
        dungeonG.dispose();

        Graphics2D radioG = (Graphics2D) g2d.create();
        radioG.translate(0, radioOffsetY);
        drawRadioWindow(radioG, incomingScale);
        radioG.dispose();
    }

    private void drawRadioWindow(Graphics2D g2d) {
        drawRadioWindow(g2d, 1.0);
    }

    private void drawRadioWindow(Graphics2D g2d, double transitionScale) {
        int radioViewportX = RADIO_VIEWPORT_X;
        int radioViewportY = RADIO_VIEWPORT_Y;
        int radioViewportW = RADIO_VIEWPORT_W;
        int radioViewportH = RADIO_VIEWPORT_H;

        Shape oldClip = g2d.getClip();
        g2d.clipRect(radioViewportX, radioViewportY, radioViewportW, radioViewportH);

        double fitScale = Math.min(radioViewportW / (double) GameConfig.WIDTH, radioViewportH / (double) GameConfig.HEIGHT);
        double scale = fitScale * RADIO_ART_DISPLAY_ZOOM * transitionScale;
        int drawWidth = Math.max(1, (int) Math.round(GameConfig.WIDTH * scale));
        int drawHeight = Math.max(1, (int) Math.round(GameConfig.HEIGHT * scale));
        int drawX = radioViewportX + ((radioViewportW - drawWidth) / 2);
        int drawY = radioViewportY + ((radioViewportH - drawHeight) / 2);

        Graphics2D radioViewport = (Graphics2D) g2d.create(drawX, drawY, drawWidth, drawHeight);
        radioViewport.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        radioViewport.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        radioViewport.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        radioViewport.scale(scale, scale);
        drawRadioScene(radioViewport);
        radioViewport.dispose();

        g2d.setClip(oldClip);
    }

    private void drawDungeonWorldBackdrop(Graphics2D g2d, Area walkableArea) {
        if (walkableArea == null || walkableArea.isEmpty()) {
            return;
        }

        g2d.setColor(new Color(74, 244, 255, 26));
        g2d.fill(walkableArea);

        g2d.setColor(new Color(74, 244, 255, 220));
        g2d.draw(walkableArea);
    }

    private void drawDungeonWorldDoor(Graphics2D g2d, Rectangle door, List<EncounterNode> encounters) {
        g2d.setColor(allEncountersCleared(encounters) ? GREEN : new Color(34, 74, 128));
        g2d.fillRect(door.x, door.y, door.width, door.height);
        g2d.setColor(WHITE);
        g2d.drawRect(door.x, door.y, door.width, door.height);
    }

    private void drawDungeonWorldEncounters(Graphics2D g2d, List<EncounterNode> encounters) {
        for (EncounterNode node : encounters) {
            if (node.isEncounter() && node.isCleared()) {
                continue;
            }
            g2d.setColor(RED);
            g2d.fillRect(node.getX(), node.getY(), ENCOUNTER_SIZE, ENCOUNTER_SIZE);
            g2d.setColor(WHITE);
            g2d.drawRect(node.getX(), node.getY(), ENCOUNTER_SIZE, ENCOUNTER_SIZE);
        }
    }

    private void drawArena(Graphics2D g2d) {
        int encounterArenaY = ENCOUNTER_ARENA_Y;
        drawEncounterEnemyBar(g2d, encounterArenaY);
    }

    private void drawEncounterGameplay(Graphics2D g2d) {
        drawArena(g2d);
        if (roundManager.getActiveArchetype().isRhythmMode()) {
            drawRhythmEncounter(g2d);
        } else {
            drawSequence(g2d);
        }
        drawEdgeAnchors(g2d, 0, 0, GameConfig.WIDTH, GameConfig.HEIGHT, EDGE_ANCHOR_BLUE, 92);
    }

    private void drawEncounterEnemyBar(Graphics2D g2d, int encounterArenaY) {
        EncounterEnemy enemy = getActiveEncounterEnemy();
        if (enemy == null) {
            return;
        }
        int enemyBarY = encounterArenaY + 400;
        long now = System.currentTimeMillis();
        double enemyHitShakeAmount = getEnemyHitShakeAmount(now);
        int enemyHitImpactOffsetX = getEnemyHitImpactOffsetX(now);
        int enemyHitImpactOffsetY = getEnemyHitImpactOffsetY(now);
        int enemyBarJitterX = getEnemyHitJitterX(now);
        int enemyBarJitterY = getEnemyHitJitterY(now);
        int enemyBarX = ENEMY_BAR_X + enemyBarJitterX;
        int enemyBarDrawY = enemyBarY + enemyBarJitterY;

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
        g2d.fillRect(enemyBarX, enemyBarDrawY, ENEMY_BAR_W, ENEMY_BAR_H);

        int displayedHealth = displayedEnemyHealth >= 0 ? displayedEnemyHealth : enemy.getHealth();
        double ratio = Math.max(0.0, Math.min(1.0, displayedHealth / (double) enemy.getMaxHealth()));
        int fillWidth = (int) Math.round((ENEMY_BAR_W - 4) * ratio);
        if (fillWidth > 0) {
            g2d.setColor(new Color(255, 92, 198, 78));
            g2d.fillRect(enemyBarX + 2, enemyBarDrawY + 2, fillWidth, ENEMY_BAR_H - 3);
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
                        enemyBarX + 2 + totalPreviewWidth,
                        enemyBarDrawY + 2,
                        surgeSegmentWidth,
                        ENEMY_BAR_H - 3
                );
            }

            int basePreviewSegmentWidth = fillWidth - baseOnlyPreviewWidth;
            if (basePreviewSegmentWidth > 0) {
                g2d.setColor(new Color(209, 118, 212, 190));
                g2d.fillRect(
                        enemyBarX + 2 + baseOnlyPreviewWidth,
                        enemyBarDrawY + 2,
                        basePreviewSegmentWidth,
                        ENEMY_BAR_H - 3
                );
            }
        }

        drawActiveItemIndicators(g2d, enemyBarDrawY);

        /*g2d.setColor(WHITE);
        String hpText = enemy.getHealth() + " / " + enemy.getMaxHealth();
        drawCenteredString(g2d, hpText, GameConfig.WIDTH / 2, enemyBarY + ENEMY_BAR_H + 18);
        if (previewDamage > 0) {
            g2d.setColor(new Color(255, 162, 228));
            drawCenteredString(g2d,  "" + previewDamage, GameConfig.WIDTH / 2, enemyBarY + ENEMY_BAR_H + 36);
        }*/

        if (now < lastHitUntilMs && lastHitDamage > 0) {
            double popProgress = 1.0 - ((lastHitUntilMs - now) / 650.0);
            int yOffset = (int) Math.round(14 * popProgress);
            int alpha = (int) Math.round(255 * (1.0 - popProgress));
            alpha = Math.max(0, Math.min(255, alpha));
            g2d.setColor(new Color(255, 122, 200, alpha));
            drawCenteredString(g2d, "-" + lastHitDamage, GameConfig.WIDTH / 2, enemyBarDrawY - 18 - yOffset);
        }
        if (poisonTicksRemaining > 0 && !enemy.isDefeated()) {
            double pulse = 0.5 + (0.5 * Math.sin(now / 120.0));
            int pulseAlpha = (int) Math.round(90 + (70 * pulse));
            g2d.setColor(new Color(80, 255, 140, pulseAlpha));
            g2d.fillRect(enemyBarX + 2, enemyBarDrawY + ENEMY_BAR_H - 6, ENEMY_BAR_W - 4, 4);
        }
        if (now < lastPoisonUntilMs && lastPoisonDamage > 0) {
            double popProgress = 1.0 - ((lastPoisonUntilMs - now) / 520.0);
            int yOffset = (int) Math.round(12 * popProgress);
            int alpha = (int) Math.round(220 * (1.0 - popProgress));
            alpha = Math.max(0, Math.min(255, alpha));
            g2d.setColor(new Color(90, 255, 140, alpha));
            drawCenteredString(g2d, "-" + lastPoisonDamage, GameConfig.WIDTH / 2, enemyBarDrawY - 36 - yOffset);
        }
        if (enemyHitShakeAmount > 0.001 && (enemyHitImpactOffsetX != 0 || enemyHitImpactOffsetY != 0)) {
            drawEnemyHealthBarImpact(
                    g2d,
                    enemyBarX,
                    enemyBarDrawY,
                    enemyHitShakeAmount,
                    enemyHitImpactOffsetX,
                    enemyHitImpactOffsetY,
                    fillWidth
            );
        }
    }

    private void drawEnemyHealthBarImpact(
            Graphics2D g2d,
            int enemyBarX,
            int enemyBarY,
            double hitShakeAmount,
            int offsetX,
            int offsetY,
            int fillWidth
    ) {
        int shellAlpha = clampInt((int) Math.round(70 * hitShakeAmount), 0, 95);
        g2d.setColor(new Color(255, 86, 86, shellAlpha));
        g2d.fillRect(enemyBarX - offsetX, enemyBarY - offsetY, ENEMY_BAR_W, ENEMY_BAR_H);
        g2d.setColor(new Color(86, 228, 255, shellAlpha));
        g2d.fillRect(enemyBarX + offsetX, enemyBarY + offsetY, ENEMY_BAR_W, ENEMY_BAR_H);

        if (fillWidth > 0) {
            int fillAlpha = clampInt((int) Math.round(145 * hitShakeAmount), 0, 185);
            g2d.setColor(new Color(255, 86, 86, fillAlpha));
            g2d.fillRect(enemyBarX + 2 - offsetX, enemyBarY + 2 - offsetY, fillWidth, ENEMY_BAR_H - 3);
            g2d.setColor(new Color(86, 228, 255, fillAlpha));
            g2d.fillRect(enemyBarX + 2 + offsetX, enemyBarY + 2 + offsetY, fillWidth, ENEMY_BAR_H - 3);
        }
    }

    private double getEnemyHitShakeAmount(long now) {
        if (lastHitUntilMs <= now || lastHitDamage <= 0) {
            return 0.0;
        }
        long elapsed = 650L - Math.max(0L, lastHitUntilMs - now);
        if (elapsed < 0L || elapsed > 120L) {
            return 0.0;
        }
        double normalized = elapsed / 120.0;
        return 1.0 - (normalized * normalized);
    }

    private int getEnemyHitImpactOffsetX(long now) {
        if (lastHitUntilMs <= now || lastHitDamage <= 0) {
            return 0;
        }
        long elapsed = 650L - Math.max(0L, lastHitUntilMs - now);
        if (elapsed < 0L || elapsed > 120L) {
            return 0;
        }
        int direction = getEnemyHitDirectionX();
        if (direction == 0) {
            return 0;
        }
        return elapsed < 48L ? (direction * 4) : (-direction * 2);
    }

    private int getEnemyHitImpactOffsetY(long now) {
        if (lastHitUntilMs <= now || lastHitDamage <= 0) {
            return 0;
        }
        long elapsed = 650L - Math.max(0L, lastHitUntilMs - now);
        if (elapsed < 0L || elapsed > 120L) {
            return 0;
        }
        int direction = getEnemyHitDirectionY();
        if (direction == 0) {
            return 0;
        }
        return elapsed < 48L ? (direction * 4) : (-direction * 2);
    }

    private int getEnemyHitJitterX(long now) {
        if (lastHitUntilMs <= now || lastHitDamage <= 0) {
            return 0;
        }
        long elapsed = 650L - Math.max(0L, lastHitUntilMs - now);
        if (elapsed < 0L || elapsed > 100L) {
            return 0;
        }
        int direction = getEnemyHitDirectionX();
        if (direction == 0) {
            return 0;
        }
        return elapsed < 44L ? (direction * 10) : (-direction * 4);
    }

    private int getEnemyHitJitterY(long now) {
        if (lastHitUntilMs <= now || lastHitDamage <= 0) {
            return 0;
        }
        long elapsed = 650L - Math.max(0L, lastHitUntilMs - now);
        if (elapsed < 0L || elapsed > 100L) {
            return 0;
        }
        int direction = getEnemyHitDirectionY();
        if (direction == 0) {
            return 0;
        }
        return elapsed < 44L ? (direction * 10) : (-direction * 4);
    }

    private int getEnemyHitDirectionX() {
        if (lastEnemyHitDirection == Direction.LEFT) {
            return -1;
        }
        if (lastEnemyHitDirection == Direction.RIGHT) {
            return 1;
        }
        return 0;
    }

    private int getEnemyHitDirectionY() {
        if (lastEnemyHitDirection == Direction.UP) {
            return -1;
        }
        if (lastEnemyHitDirection == Direction.DOWN) {
            return 1;
        }
        return 0;
    }

    private void drawActiveItemIndicators(Graphics2D g2d, int enemyBarY) {
        List<ItemArchetype> activeItems = getEnabledItems();
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
            if (poisonIconGlowSprite != null) {
                int glowSize = iconSize + 6;
                int glowInset = (glowSize - iconSize) / 2;
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.06 + (0.06 * pulse))));
                g2d.drawImage(poisonIconGlowSprite, iconX - glowInset, iconY - glowInset, glowSize, glowSize, null);
            }
        }
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.70f));
        g2d.drawImage(poisonSprite, iconX, iconY, iconSize, iconSize, null);
        if (pulse > 0.0) {
            if (poisonIconHighlightSprite != null) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.18 + (0.20 * pulse))));
                g2d.drawImage(poisonIconHighlightSprite, iconX, iconY, iconSize, iconSize, null);
            }
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
            BufferedImage glowSprite = getInitialSurgeGlowSprite();
            if (glowSprite != null) {
                int glowSize = iconSize + 6;
                int glowInset = (glowSize - iconSize) / 2;
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.08 + (0.10 * pulse))));
                g2d.drawImage(glowSprite, iconX - glowInset, iconY - glowInset, glowSize, glowSize, null);
            }
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

        g2d.setColor(HUD_LINE_WHITE);
        g2d.drawLine(x, lineY, x + ITEM_CHARGE_BAR_WIDTH - 1, lineY);

        int fillWidth = (int) Math.round(ITEM_CHARGE_BAR_WIDTH * clampedRatio);
        if (fillWidth > 0) {
            int alpha = (int) Math.round(190 + (45 * pulse));
            g2d.setColor(new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), alpha));
            g2d.fillRect(x, fillY, fillWidth, fillHeight);
        } else {
            g2d.setColor(HUD_LINE_WHITE_DIM);
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

    private BufferedImage getInitialSurgeGlowSprite() {
        BufferedImage fallback = null;
        for (BufferedImage sprite : initialSurgeGlowSprites) {
            if (sprite != null) {
                fallback = sprite;
                break;
            }
        }
        if (fallback == null) {
            return null;
        }

        int frameCount = initialSurgeGlowSprites.length;
        int frameIndex = (int) ((System.currentTimeMillis() / INITIAL_SURGE_FRAME_DURATION_MS) % frameCount);
        BufferedImage frame = initialSurgeGlowSprites[frameIndex];
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
        if (!hasActiveItem(ItemArchetype.INITIAL_SURGE)) {
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
        if (timeoutRecoveryActive || encounterTransitionActive) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.46f));
        }
        boolean wrongFlash = roundManager.isWrongFlashActive();
        int progressIndex = roundManager.getProgressIndex();
        boolean hideSequence = roundManager.shouldHideSequence();
        boolean reverseInput = roundManager.getActiveArchetype().isReverseInput();
        int visibleCount = roundManager.getVisibleSequenceCount();
        long now = System.currentTimeMillis();
        double hitShakeAmount = getSequenceHitShakeAmount(now);
        int shakeOffset = getSequenceHitShakeOffset(now);
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
            drawSequenceSymbolBorder(g2d, x, y, SEQUENCE_SYMBOL_SIZE, borderColor, wrongFlash, hitShakeAmount, shakeOffset);

            if (hideSequence || !isVisible) {
                g2d.setColor(new Color(255, 255, 255, 110));
                drawCenteredString(g2d, "?", x + (SEQUENCE_SYMBOL_SIZE / 2), y + 47);
            } else {
                Direction direction = Direction.values()[sequence.get(i)];
                BufferedImage sprite = isCorrect ? arrowSpritesGreen.get(direction) : arrowSprites.get(direction);
                if (sprite != null) {
                    drawArrowSprite(g2d, sprite, x, y, SEQUENCE_SYMBOL_SIZE, wrongFlash, hitShakeAmount, shakeOffset);
                } else {
                    drawArrow(g2d, direction, x, y, SEQUENCE_SYMBOL_SIZE, symbolColor, wrongFlash, hitShakeAmount, shakeOffset);
                }
            }
        }
        g2d.setComposite(oldComposite);
    }

    private void drawSequencePunchSprite(Graphics2D g2d, int arrowRowY) {
        long now = System.currentTimeMillis();
        BufferedImage sprite = getSequencePunchSprite(now);
        if (sprite == null) {
            return;
        }
        int size = getSequencePunchSize();
        int x = 20;
        int y = ENCOUNTER_ARENA_Y + (ARENA_H - size) / 2 + SEQUENCE_PUNCH_OFFSET_Y;
        Composite oldComposite = g2d.getComposite();
        double hitShakeAmount = getSequenceHitShakeAmount(now);
        int shakeOffset = getSequenceHitShakeOffset(now);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, SEQUENCE_PUNCH_ALPHA));
        if (hitShakeAmount > 0.001 && shakeOffset != 0) {
            float ghostAlpha = (float) Math.max(0.0, Math.min(0.58, 0.42 * hitShakeAmount * sequenceHitShakeAlphaScale));
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ghostAlpha));
            g2d.drawImage(sprite, x - shakeOffset, y - sequenceHitShakeVerticalBias, size, size, null);
            g2d.drawImage(sprite, x + shakeOffset, y + sequenceHitShakeVerticalBias, size, size, null);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, SEQUENCE_PUNCH_ALPHA));
        }
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

    private void registerSequencePunch(boolean sequenceComplete, long cadenceMs) {
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
        double speedIntensity = getSequenceHitSpeedIntensity(cadenceMs);
        int baseOffset = sequenceComplete ? 2 : 1;
        sequenceHitShakeOffsetMagnitude = baseOffset
                + (int) Math.round(speedIntensity * speedIntensity * (sequenceComplete ? 4.0 : 3.0))
                + random.nextInt(2);
        sequenceHitShakeVerticalBias = (speedIntensity > 0.48 || random.nextBoolean()) ? (1 + (speedIntensity > 0.82 ? 1 : 0)) : 0;
        sequenceHitShakePhaseStepMs = Math.max(12, 28 - (int) Math.round(speedIntensity * speedIntensity * 14.0) - random.nextInt(3));
        int baseDuration = sequenceComplete ? 92 : 72;
        int bonusDuration = (int) Math.round(speedIntensity * speedIntensity * (sequenceComplete ? 42.0 : 30.0));
        sequenceHitShakeDurationMs = baseDuration + bonusDuration + random.nextInt(12);
        float baseAlpha = sequenceComplete ? 0.84f : 0.68f;
        float bonusAlpha = (float) (speedIntensity * speedIntensity * (sequenceComplete ? 0.58f : 0.46f));
        sequenceHitShakeAlphaScale = baseAlpha + bonusAlpha + (random.nextFloat() * 0.08f);
        lastSequencePunchMs = now;
    }

    private void resetSequencePunchState() {
        sequencePunchFrame = 0;
        sequencePunchPatternIndex = -1;
        sequenceHitShakeOffsetMagnitude = 2;
        sequenceHitShakeVerticalBias = 0;
        sequenceHitShakePhaseStepMs = 24;
        sequenceHitShakeDurationMs = 110;
        sequenceHitShakeAlphaScale = 1.0f;
        lastSequencePunchMs = 0L;
    }

    private double getSequenceHitSpeedIntensity(long cadenceMs) {
        if (cadenceMs <= 0L) {
            return 0.3;
        }
        double clamped = Math.max(70.0, Math.min(320.0, cadenceMs));
        double normalized = 1.0 - ((clamped - 70.0) / 250.0);
        return Math.max(0.0, Math.min(1.0, normalized));
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

    private void drawLevelUpOverlay(Graphics2D g2d) {
        drawRadioScene(g2d);
    }

    private void drawRadioScene(Graphics2D g2d) {
        if (radioOverlaySprite != null) {
            drawRadioSceneSprite(g2d);
            return;
        }

        int w = 560;
        int h = 310;
        int x = GameConfig.WIDTH - w - 28;
        int y = (GameConfig.HEIGHT - h) / 2 + 10;

        g2d.setColor(new Color(3, 16, 38, 236));
        g2d.fillRect(x, y, w, h);
        drawFrame(g2d, x, y, w, h, 4, WHITE);

        g2d.setFont(HUD_FONT);
        drawGlowingCenteredString(g2d, "LEVEL UP", GameConfig.WIDTH / 2, y + 44, YELLOW, GLOW_CYAN);

        g2d.setFont(SMALL_FONT);
        g2d.setColor(TEXT_DIM);
        drawCenteredString(g2d, "SPEND 1 SKILL POINT  |  STOCK " + skillPoints, x + (w / 2), y + 72);

        int optionY = y + 112;
        int optionH = 46;
        for (int i = 0; i < levelUpChoices.size(); i++) {
            ItemArchetype item = levelUpChoices.get(i);
            boolean selected = levelUpSelectionIndex == i;
            int rowY = optionY + (i * 58);

            g2d.setColor(new Color(62, 124, 220, 140));
            g2d.fillRect(x + 42, rowY - 28, w - 84, optionH);
            g2d.setColor(selected ? new Color(120, 200, 255, 200) : new Color(92, 162, 240, 160));
            g2d.fillRect(x + 44, rowY - 26, w - 88, optionH - 4);

            String label = item.getLabel() + "  LV " + (getItemLevel(item) + 1);
            g2d.setFont(BODY_FONT);
            if (selected) {
                drawGlowingString(g2d, label, x + 64, rowY, YELLOW, GLOW_CYAN);
            } else {
                g2d.setColor(WHITE);
                g2d.drawString(label, x + 64, rowY);
            }

            g2d.setFont(SMALL_FONT);
            g2d.setColor(TEXT_DIM);
            g2d.drawString(getLevelUpChoiceDescription(item), x + 64, rowY + 18);
        }

        g2d.setFont(SMALL_FONT);
        g2d.setColor(TEXT_DIM);
        drawCenteredString(g2d, "ENTER CHOOSE  |  SHIFT / RT CLOSE", x + (w / 2), y + h - 24);
    }

    private void drawRadioSceneSprite(Graphics2D g2d) {
        if (radioOverlaySprite == null) {
            return;
        }

        int spriteWidth = radioOverlaySprite.getWidth();
        int spriteHeight = radioOverlaySprite.getHeight();
        if (spriteWidth <= 0 || spriteHeight <= 0) {
            return;
        }

        g2d.drawImage(radioOverlaySprite, 0, 0, GameConfig.WIDTH, GameConfig.HEIGHT, null);

        drawRadioSelectionOverlay(g2d);
    }

    private void drawRadioSelectionOverlay(Graphics2D g2d) {
        if (levelUpChoices.isEmpty()) {
            return;
        }

        int slotGap = 46;
        int slotSize = 84;
        int totalWidth = (slotSize * levelUpChoices.size()) + (slotGap * Math.max(0, levelUpChoices.size() - 1));
        int slotX = (GameConfig.WIDTH - totalWidth) / 2;
        int slotY = GameConfig.HEIGHT - 250;

        for (int i = 0; i < levelUpChoices.size(); i++) {
            ItemArchetype item = levelUpChoices.get(i);
            boolean selected = levelUpSelectionIndex == i;
            int x = slotX + (i * (slotSize + slotGap));

            if (selected) {
                Composite oldComposite = g2d.getComposite();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
                g2d.setColor(new Color(255, 238, 130));
                g2d.fillOval(x - 12, slotY - 12, slotSize + 24, slotSize + 24);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.34f));
                g2d.setColor(new Color(120, 220, 255));
                g2d.fillOval(x - 6, slotY - 6, slotSize + 12, slotSize + 12);
                g2d.setComposite(oldComposite);
            }

            drawRadioUpgradeIcon(g2d, item, x, slotY, slotSize);
        }
    }

    private void drawRadioUpgradeIcon(Graphics2D g2d, ItemArchetype item, int x, int y, int size) {
        if (item == ItemArchetype.POISON && poisonIconSprite != null) {
            g2d.drawImage(poisonIconSprite, x, y, size, size, null);
            return;
        }
        if (item == ItemArchetype.INITIAL_SURGE) {
            BufferedImage sprite = getInitialSurgeIndicatorSprite(true);
            if (sprite != null) {
                g2d.drawImage(sprite, x, y, size, size, null);
                return;
            }
        }

        g2d.setColor(WHITE);
        g2d.fillRoundRect(x + 8, y + 8, size - 16, size - 16, 12, 12);
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
                synchronized (stateLock) {
                    handleConfirmAction();
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "go_to_menu");
        actionMap.put("go_to_menu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                synchronized (stateLock) {
                    handleBackAction();
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("pressed SPACE"), "radio_toggle");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "radio_toggle");
        actionMap.put("radio_toggle", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                synchronized (stateLock) {
                    toggleRadioOverlay();
                }
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
                synchronized (stateLock) {
                    processDirectionalInput(direction);
                }
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
                            && !roomIntroActive
                            && !menuTransitionActive) {
                        synchronized (stateLock) {
                            setMovementFromKeyCode(e.getKeyCode(), true);
                        }
                    }
                } else if (id == KeyEvent.KEY_RELEASED) {
                    synchronized (stateLock) {
                        setMovementFromKeyCode(e.getKeyCode(), false);
                    }
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
        playerLevel = 1;
        playerXp = 0;
        displayedPlayerXpLevel = 1;
        displayedPlayerXp = 0.0;
        skillPoints = 0;
        levelUpSelectionIndex = 0;
        lastXpGain = 0;
        lastXpGainUntilMs = 0L;
        levelUpChoices.clear();
        for (int i = 0; i < playerItemLevels.length; i++) {
            playerItemLevels[i] = 0;
        }
        playerHealth = GameConfig.PLAYER_MAX_HEALTH;
        displayedPlayerHealth = playerHealth;
        heartDamageFlashUntilMs = 0L;
        healthDrainReliefMs = 0L;
        timeoutRecoveryActive = false;
        timeoutRecoveryStartMs = 0L;
        timeoutRecoveryTargetMs = 0L;
        mistakeGuardCharges = 0L;
        nextEncounterTimeBonusMs = 0L;
        radioRevealProgress = 0.0;
        radioClosing = false;
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
        generateRoom(null);
        AudioManager.playSfx("enter_game.wav");
    }

    private void generateRoom(Direction entryDirection) {
        roomEncounters.clear();
        roomPathSegments.clear();
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

        Direction incomingDirection = entryDirection == null
                ? getOppositeDirection(getRandomRoomSide())
                : entryDirection;
        Direction spawnSide = getOppositeDirection(incomingDirection);
        doorDirection = getRandomRoomSideExcluding(spawnSide);
        positionPlayerFromEntry(incomingDirection);
        buildRoomPath(incomingDirection);

        int encounters = 1 + random.nextInt(3);
        int maxTries = 50;
        Rectangle playerRect = getPlayerRect();
        Rectangle doorRect = getDoorRect();
        for (int i = 0; i < encounters; i++) {
            EncounterNode node = EncounterNode.createEncounter(generateEnemyHealthForRoom(), rollEnemyArchetype());
            if (placeEncounterOnPath(node, maxTries, playerRect, doorRect)) {
                roomEncounters.add(node);
            }
        }

        if (roomEncounters.isEmpty()) {
            EncounterNode fallback = EncounterNode.createEncounter(generateEnemyHealthForRoom(), rollEnemyArchetype());
            Rectangle fallbackRect = roomPathSegments.isEmpty()
                    ? new Rectangle(roomWorldWidth / 2, roomWorldHeight / 2, ROOM_PATH_WIDTH, ROOM_PATH_WIDTH)
                    : roomPathSegments.get(roomPathSegments.size() / 2);
            fallback.setX(fallbackRect.x + Math.max(0, (fallbackRect.width - ENCOUNTER_SIZE) / 2));
            fallback.setY(fallbackRect.y + Math.max(0, (fallbackRect.height - ENCOUNTER_SIZE) / 2));
            roomEncounters.add(fallback);
        }

        rebuildWalkableArea();
    }

    private Direction getRandomRoomSide() {
        Direction[] sides = {Direction.RIGHT, Direction.UP, Direction.DOWN, Direction.LEFT};
        return sides[random.nextInt(sides.length)];
    }

    private Direction getOppositeDirection(Direction direction) {
        if (direction == null) {
            return Direction.RIGHT;
        }
        switch (direction) {
            case LEFT:
                return Direction.RIGHT;
            case RIGHT:
                return Direction.LEFT;
            case UP:
                return Direction.DOWN;
            case DOWN:
                return Direction.UP;
            default:
                return Direction.RIGHT;
        }
    }

    private Direction getRandomRoomSideExcluding(Direction excluded) {
        Direction[] sides = {Direction.RIGHT, Direction.UP, Direction.DOWN, Direction.LEFT};
        Direction fallback = excluded == Direction.RIGHT ? Direction.LEFT : Direction.RIGHT;
        int availableCount = 0;
        for (Direction side : sides) {
            if (side != excluded) {
                availableCount++;
            }
        }
        if (availableCount <= 0) {
            return fallback;
        }

        int choice = random.nextInt(availableCount);
        for (Direction side : sides) {
            if (side == excluded) {
                continue;
            }
            if (choice == 0) {
                return side;
            }
            choice--;
        }
        return fallback;
    }

    private boolean placeEncounterOnPath(EncounterNode node, int maxTries, Rectangle playerRect, Rectangle doorRect) {
        if (roomPathSegments.isEmpty()) {
            return false;
        }
        for (int tries = 0; tries < maxTries; tries++) {
            Rectangle segment = roomPathSegments.get(random.nextInt(roomPathSegments.size()));
            int minX = segment.x + 10;
            int minY = segment.y + 10;
            int maxX = segment.x + Math.max(10, segment.width - ENCOUNTER_SIZE - 10);
            int maxY = segment.y + Math.max(10, segment.height - ENCOUNTER_SIZE - 10);
            int nx = minX + random.nextInt(Math.max(1, maxX - minX + 1));
            int ny = minY + random.nextInt(Math.max(1, maxY - minY + 1));
            Rectangle candidate = new Rectangle(nx, ny, ENCOUNTER_SIZE, ENCOUNTER_SIZE);
            if (candidate.intersects(playerRect)) {
                continue;
            }
            if (candidate.intersects(doorRect)) {
                continue;
            }
            if (intersectsAnyEncounter(candidate)) {
                continue;
            }
            if (distanceBetweenRectCenters(candidate, playerRect) < 120.0) {
                continue;
            }
            if (distanceBetweenRectCenters(candidate, doorRect) < 120.0) {
                continue;
            }
            node.setX(nx);
            node.setY(ny);
            return true;
        }
        return false;
    }

    private void buildRoomPath(Direction entryDirection) {
        int startX = clampInt((int) Math.round(playerX) + (PLAYER_SIZE / 2), ROOM_PATH_WIDTH / 2, roomWorldWidth - (ROOM_PATH_WIDTH / 2));
        int startY = clampInt((int) Math.round(playerY) + (PLAYER_SIZE / 2), ROOM_PATH_WIDTH / 2, roomWorldHeight - (ROOM_PATH_WIDTH / 2));
        Rectangle doorRect = getDoorRect();
        int endX = doorRect.x + (doorRect.width / 2);
        int endY = doorRect.y + (doorRect.height / 2);

        List<int[]> points = new ArrayList<>();
        points.add(new int[]{startX, startY});

        int waypointCount = 4 + random.nextInt(4);
        double dx = endX - startX;
        double dy = endY - startY;
        boolean horizontalBias = Math.abs(dx) >= Math.abs(dy);
        int maxOffsetX = Math.max(ROOM_PATH_WIDTH, roomWorldWidth / 5);
        int maxOffsetY = Math.max(ROOM_PATH_WIDTH, roomWorldHeight / 5);

        for (int i = 1; i <= waypointCount; i++) {
            double progress = i / (double) (waypointCount + 1);
            int px = (int) Math.round(startX + (dx * progress));
            int py = (int) Math.round(startY + (dy * progress));
            int jitterX = horizontalBias ? random.nextInt((maxOffsetX * 2) + 1) - maxOffsetX : random.nextInt(maxOffsetX + 1) - (maxOffsetX / 2);
            int jitterY = horizontalBias ? random.nextInt(maxOffsetY + 1) - (maxOffsetY / 2) : random.nextInt((maxOffsetY * 2) + 1) - maxOffsetY;
            px = clampInt(px + jitterX, ROOM_PATH_WIDTH / 2, roomWorldWidth - (ROOM_PATH_WIDTH / 2));
            py = clampInt(py + jitterY, ROOM_PATH_WIDTH / 2, roomWorldHeight - (ROOM_PATH_WIDTH / 2));
            points.add(new int[]{px, py});
        }
        points.add(new int[]{endX, endY});

        int[] previous = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            int[] current = points.get(i);
            addJaggedConnection(previous[0], previous[1], current[0], current[1]);
            previous = current;
        }

        int branchCount = 2 + random.nextInt(3);
        for (int i = 0; i < branchCount && points.size() > 2; i++) {
            int[] anchor = points.get(1 + random.nextInt(points.size() - 2));
            addSideBranch(anchor[0], anchor[1], horizontalBias);
        }
    }

    private void addPathSegment(int x1, int y1, int x2, int y2) {
        int corridorHalf = ROOM_PATH_WIDTH / 2;
        int left = Math.min(x1, x2) - corridorHalf;
        int top = Math.min(y1, y2) - corridorHalf;
        int width = Math.abs(x2 - x1) + ROOM_PATH_WIDTH;
        int height = Math.abs(y2 - y1) + ROOM_PATH_WIDTH;
        Rectangle segment = new Rectangle(
                clampInt(left, 0, Math.max(0, roomWorldWidth - width)),
                clampInt(top, 0, Math.max(0, roomWorldHeight - height)),
                Math.min(roomWorldWidth, Math.max(ROOM_PATH_WIDTH, width)),
                Math.min(roomWorldHeight, Math.max(ROOM_PATH_WIDTH, height))
        );
        roomPathSegments.add(segment);
    }

    private void addJaggedConnection(int startX, int startY, int endX, int endY) {
        int currentX = startX;
        int currentY = startY;
        int steps = 3 + random.nextInt(4);
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            int targetX = step == steps
                    ? endX
                    : clampInt((int) Math.round(startX + ((endX - startX) * progress) + random.nextInt(181) - 90),
                    ROOM_PATH_WIDTH / 2,
                    roomWorldWidth - (ROOM_PATH_WIDTH / 2));
            int targetY = step == steps
                    ? endY
                    : clampInt((int) Math.round(startY + ((endY - startY) * progress) + random.nextInt(181) - 90),
                    ROOM_PATH_WIDTH / 2,
                    roomWorldHeight - (ROOM_PATH_WIDTH / 2));

            if (random.nextBoolean()) {
                addPathSegment(currentX, currentY, targetX, currentY);
                addPathSegment(targetX, currentY, targetX, targetY);
            } else {
                addPathSegment(currentX, currentY, currentX, targetY);
                addPathSegment(currentX, targetY, targetX, targetY);
            }
            currentX = targetX;
            currentY = targetY;
        }
    }

    private void addSideBranch(int anchorX, int anchorY, boolean horizontalBias) {
        int branchLengthPrimary = 80 + random.nextInt(180);
        int branchLengthSecondary = 40 + random.nextInt(120);
        int directionA = random.nextBoolean() ? 1 : -1;
        int directionB = random.nextBoolean() ? 1 : -1;

        int bendX = anchorX;
        int bendY = anchorY;
        int endX = anchorX;
        int endY = anchorY;
        if (horizontalBias) {
            bendY = clampInt(anchorY + (directionA * branchLengthPrimary), ROOM_PATH_WIDTH / 2, roomWorldHeight - (ROOM_PATH_WIDTH / 2));
            endX = clampInt(anchorX + (directionB * branchLengthSecondary), ROOM_PATH_WIDTH / 2, roomWorldWidth - (ROOM_PATH_WIDTH / 2));
            endY = bendY;
        } else {
            bendX = clampInt(anchorX + (directionA * branchLengthPrimary), ROOM_PATH_WIDTH / 2, roomWorldWidth - (ROOM_PATH_WIDTH / 2));
            endX = bendX;
            endY = clampInt(anchorY + (directionB * branchLengthSecondary), ROOM_PATH_WIDTH / 2, roomWorldHeight - (ROOM_PATH_WIDTH / 2));
        }

        addPathSegment(anchorX, anchorY, bendX, bendY);
        addPathSegment(bendX, bendY, endX, endY);
    }

    private void rebuildWalkableArea() {
        Area combined = new Area();
        for (Rectangle segment : roomPathSegments) {
            combined.add(new Area(segment));
        }
        combined.add(new Area(getDoorRect()));
        roomWalkableArea = combined;
    }

    private double distanceBetweenRectCenters(Rectangle a, Rectangle b) {
        double ax = a.getCenterX();
        double ay = a.getCenterY();
        double bx = b.getCenterX();
        double by = b.getCenterY();
        return Math.hypot(ax - bx, ay - by);
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
        attemptPlayerMove(dx, 0.0);
        attemptPlayerMove(0.0, dy);

        Rectangle playerRect = getPlayerRect();

        if (!allEncountersCleared()) {
            for (int i = 0; i < roomEncounters.size(); i++) {
                EncounterNode node = roomEncounters.get(i);
                if (node.isEncounter() && node.isCleared()) {
                    continue;
                }
                Rectangle encounterRect = new Rectangle(node.getX(), node.getY(), ENCOUNTER_SIZE, ENCOUNTER_SIZE);
                if (playerRect.intersects(encounterRect)) {
                    startEncounter(i);
                    return;
                }
            }
        } else if (playerRect.intersects(getDoorRect())) {
            startRoomTransition(doorDirection);
        }
    }

    private void attemptPlayerMove(double dx, double dy) {
        if (dx == 0.0 && dy == 0.0) {
            return;
        }

        double maxX = Math.max(0, roomWorldWidth - PLAYER_SIZE);
        double maxY = Math.max(0, roomWorldHeight - PLAYER_SIZE);
        double nextX = clampDouble(playerX + dx, 0.0, maxX);
        double nextY = clampDouble(playerY + dy, 0.0, maxY);
        if (isWalkable(nextX, nextY)) {
            playerX = nextX;
            playerY = nextY;
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
        activeEncounterIndex = encounterIndex;
        long encounterBonusMs = nextEncounterTimeBonusMs;
        nextEncounterTimeBonusMs = 0L;
        roundManager.startGame(false, encounterBonusMs);
        roundManager.pauseTimer(ENCOUNTER_TRANSITION_MS + ENCOUNTER_TRANSITION_HOLD_MS + 160L);
        resetTimerBarAnimation();
        AudioManager.playSfx("encounter_start.wav");
        controllerInputManager.rumble(ENEMY_TOUCH_RUMBLE_STRENGTH, ENEMY_TOUCH_RUMBLE_MS);
        pendingEncounterIndex = encounterIndex;
        encounterTransitionActive = true;
        encounterTransitionStartMs = System.currentTimeMillis();
    }

    private void startRoomTransition(Direction exitedDir) {
        backdropEffects.clearHueSweeps();
        roomTransitionPreviousState = captureCurrentRoomState();
        prepareNextRoomForTransition(exitedDir);
        roomTransitionActive = true;
        roomTransitionStartMs = System.currentTimeMillis();
        roomIntroActive = false;
        pendingRoomEntryDirection = exitedDir;
        roomIntroDirection = exitedDir;
        AudioManager.playSfx("next_room.wav");
        controllerInputManager.rumble(ROOM_ENTRY_RUMBLE_STRENGTH, ROOM_ENTRY_RUMBLE_MS);
    }

    private void completeRoomTransition() {
        roomTransitionPreviousState = null;
        Direction exitedDir = pendingRoomEntryDirection;
        pendingRoomEntryDirection = null;
        roomIntroDirection = exitedDir;
    }

    private RoomRenderState captureCurrentRoomState() {
        return new RoomRenderState(
                roomWorldWidth,
                roomWorldHeight,
                playerX,
                playerY,
                doorDirection,
                roomWalkableArea == null ? new Area() : new Area(roomWalkableArea),
                new ArrayList<>(roomEncounters)
        );
    }

    private void prepareNextRoomForTransition(Direction exitedDir) {
        if (exitedDir == null) {
            return;
        }

        roomNumber++;
        generateRoom(exitedDir);

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
            generateRoom(exitedDir);
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
            registerSequencePunch(completion != null, roundManager.getLastCorrectCadenceMs());
        }
        if (progressAfter > progressBefore) {
            controllerInputManager.rumble(KEY_SUCCESS_RUMBLE_STRENGTH, KEY_SUCCESS_RUMBLE_MS);
            backdropEffects.triggerPinkWaveHitPulse();
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
            if (symbol >= 0 && symbol < Direction.values().length) {
                lastEnemyHitDirection = Direction.values()[symbol];
            }
            lastHitDamage = damage;
            lastHitUntilMs = System.currentTimeMillis() + 650L;
        } else {
            lastHitDamage = 0;
            lastHitUntilMs = 0;
        }

        boolean enemyDefeated = currentNode.isCleared();
        if (completion != null && !enemyDefeated) {
            AudioManager.playClickSfx();
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
        int xpReward = calculateSequenceXpReward(completion);
        if (xpReward > 0) {
            grantXp(xpReward);
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
        if (!hasActiveItem(ItemArchetype.POISON)) {
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
        boolean poisonActive = hasActiveItem(ItemArchetype.POISON);
        boolean anyItemActive = forcedTestItemMask != 0 || hasAnyPlayerItems();
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
        encounterBestedTransitionActive = false;
        encounterBestedTransitionStartMs = 0L;
    }

    private void spawnEnemyDefeatEffect(EncounterNode node) {
        int centerX = ROOM_X + (node.getX() - getCameraX()) + (ENCOUNTER_SIZE / 2);
        int centerY = ROOM_Y + (node.getY() - getCameraY()) + (ENCOUNTER_SIZE / 2);
        enemyKillEffects.spawn();
        backdropEffects.spawnEnemyDefeatRipples(centerX, centerY);
    }

    private int calculateSequenceXpReward(RoundCompletion completion) {
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

    private void grantXp(int xpReward) {
        if (xpReward <= 0) {
            return;
        }
        playerXp += xpReward;
        lastXpGain = xpReward;
        lastXpGainUntilMs = System.currentTimeMillis() + 760L;

        int xpRequired = getXpRequiredForLevel(playerLevel);
        while (playerXp >= xpRequired) {
            playerXp -= xpRequired;
            playerLevel++;
            skillPoints++;
            xpRequired = getXpRequiredForLevel(playerLevel);
        }
        if (displayedPlayerXpLevel > playerLevel) {
            displayedPlayerXpLevel = playerLevel;
            displayedPlayerXp = playerXp;
        }
    }

    private int getXpRequiredForLevel(int level) {
        int normalizedLevel = Math.max(1, level);
        return 18 + ((normalizedLevel - 1) * 8);
    }

    private void populateLevelUpChoices() {
        levelUpChoices.clear();
        ItemArchetype[] items = ItemArchetype.values();
        List<ItemArchetype> pool = new ArrayList<>();
        for (ItemArchetype item : items) {
            pool.add(item);
        }

        while (levelUpChoices.size() < LEVEL_UP_CHOICE_COUNT && items.length > 0) {
            if (pool.isEmpty()) {
                for (ItemArchetype item : items) {
                    pool.add(item);
                }
            }
            int index = random.nextInt(pool.size());
            levelUpChoices.add(pool.remove(index));
        }
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
        return allEncountersCleared(roomEncounters);
    }

    private boolean allEncountersCleared(List<EncounterNode> encounters) {
        for (EncounterNode node : encounters) {
            if (node.isEncounter() && !node.isCleared()) {
                return false;
            }
        }
        return true;
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

    private Rectangle getPlayerRect() {
        return new Rectangle((int) Math.round(playerX), (int) Math.round(playerY), PLAYER_SIZE, PLAYER_SIZE);
    }

    private boolean isWalkable(double candidateX, double candidateY) {
        if (candidateX < 0.0 || candidateY < 0.0
                || candidateX + PLAYER_SIZE > roomWorldWidth
                || candidateY + PLAYER_SIZE > roomWorldHeight) {
            return false;
        }
        Rectangle2D.Double collisionRect = getPlayerCollisionRect(candidateX, candidateY);
        return roomWalkableArea != null && roomWalkableArea.contains(collisionRect);
    }

    private Rectangle2D.Double getPlayerCollisionRect(double candidateX, double candidateY) {
        double insetX = 3.0;
        double insetY = 3.0;
        return new Rectangle2D.Double(
                candidateX + insetX,
                candidateY + insetY,
                Math.max(1.0, PLAYER_SIZE - (insetX * 2.0)),
                Math.max(1.0, PLAYER_SIZE - (insetY * 2.0))
        );
    }

    private Rectangle getDoorRect() {
        return getDoorRect(doorDirection, roomWorldWidth, roomWorldHeight);
    }

    private Rectangle getDoorRect(Direction renderDoorDirection, int worldWidth, int worldHeight) {
        int x;
        int y;

        switch (renderDoorDirection) {
            case UP:
                x = (worldWidth - DOOR_H) / 2;
                y = 0;
                return new Rectangle(x, y, DOOR_H, DOOR_W);

            case DOWN:
                x = (worldWidth - DOOR_H) / 2;
                y = worldHeight - DOOR_W;
                return new Rectangle(x, y, DOOR_H, DOOR_W);

            case LEFT:
                x = 0;
                y = (worldHeight - DOOR_H) / 2;
                return new Rectangle(x, y, DOOR_W, DOOR_H);

            case RIGHT:
            default:
                x = worldWidth - DOOR_W;
                y = (worldHeight - DOOR_H) / 2;
                return new Rectangle(x, y, DOOR_W, DOOR_H);
        }
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void positionPlayerFromEntry(Direction exitedDir) {
        final int padding = 26;

        switch (exitedDir) {
            case LEFT:
                playerX = roomWorldWidth - padding - PLAYER_SIZE;
                playerY = (roomWorldHeight / 2.0) - (PLAYER_SIZE / 2.0);
                break;
            case RIGHT:
                playerX = padding;
                playerY = (roomWorldHeight / 2.0) - (PLAYER_SIZE / 2.0);
                break;
            case UP:
                playerX = (roomWorldWidth / 2.0) - (PLAYER_SIZE / 2.0);
                playerY = roomWorldHeight - padding - PLAYER_SIZE;
                break;
            case DOWN:
                playerX = (roomWorldWidth / 2.0) - (PLAYER_SIZE / 2.0);
                playerY = padding;
                break;
            default:
                playerX = padding;
                playerY = (roomWorldHeight / 2.0) - (PLAYER_SIZE / 2.0);
        }

        int maxX = Math.max(0, roomWorldWidth - PLAYER_SIZE);
        int maxY = Math.max(0, roomWorldHeight - PLAYER_SIZE);
        playerX = clampDouble(playerX, 0.0, maxX);
        playerY = clampDouble(playerY, 0.0, maxY);

        Rectangle spawnRect = getPlayerRect();
        Rectangle newDoor = getDoorRect();
        if (spawnRect.intersects(newDoor)) {
            if (exitedDir == Direction.LEFT || exitedDir == Direction.RIGHT) {
                playerY = clampDouble(playerY + (PLAYER_SIZE + 6), 0.0, maxY);
            } else {
                playerX = clampDouble(playerX + (PLAYER_SIZE + 6), 0.0, maxX);
            }
            spawnRect = getPlayerRect();
        }

        if (intersectsAnyEncounter(spawnRect)) {
            int tries = 6;
            int offset = 18;
            boolean placed = false;
            for (int i = 0; i < tries && !placed; i++) {
                int dx = ((i % 3) - 1) * offset;
                int dy = ((i / 3) - 1) * offset;
                double tryX = clampDouble(playerX + dx, 0.0, maxX);
                double tryY = clampDouble(playerY + dy, 0.0, maxY);
                Rectangle r = new Rectangle((int) Math.round(tryX), (int) Math.round(tryY), PLAYER_SIZE, PLAYER_SIZE);
                if (!intersectsAnyEncounter(r) && !r.intersects(newDoor)) {
                    playerX = tryX;
                    playerY = tryY;
                    placed = true;
                }
            }
        }
    }

    private int getCameraX() {
        return getCameraX(playerX, roomWorldWidth);
    }

    private int getCameraX(double renderPlayerX, int worldWidth) {
        int preferredPlayerScreenX = (ROOM_W - PLAYER_SIZE) / 2;
        double target = renderPlayerX - preferredPlayerScreenX;
        return clampInt((int) Math.round(target), 0, Math.max(0, worldWidth - ROOM_W));
    }

    private int getCameraY() {
        return getCameraY(playerY, roomWorldHeight);
    }

    private int getCameraY(double renderPlayerY, int worldHeight) {
        int preferredPlayerScreenY = (ROOM_H - PLAYER_SIZE) / 2;
        double target = renderPlayerY - preferredPlayerScreenY;
        return clampInt((int) Math.round(target), 0, Math.max(0, worldHeight - ROOM_H));
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
        if (encounterTransitionActive) {
            long duration = Math.max(1L, roundManager.getRoundDurationMs());
            long transitionDuration = Math.max(1L, ENCOUNTER_TRANSITION_MS + ENCOUNTER_TRANSITION_HOLD_MS);
            double progress = (System.currentTimeMillis() - encounterTransitionStartMs) / (double) transitionDuration;
            double fillProgress = easeInOut(progress);
            displayedTimerDurationMs = duration;
            displayedTimerMs = Math.round(duration * fillProgress);
            return;
        }
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

    private void updateXpBarAnimation(double deltaSeconds) {
        double targetXp = Math.max(0.0, playerXp);
        if (displayedPlayerXpLevel > playerLevel) {
            displayedPlayerXpLevel = playerLevel;
            displayedPlayerXp = targetXp;
            return;
        }

        double slidePerSecond = Math.max(8.0, getXpRequiredForLevel(displayedPlayerXpLevel) * 1.5);

        if (displayedPlayerXpLevel < playerLevel) {
            double displayedLevelXpRequired = getXpRequiredForLevel(displayedPlayerXpLevel);
            displayedPlayerXp = Math.min(
                    displayedLevelXpRequired,
                    displayedPlayerXp + (slidePerSecond * Math.max(0.0, deltaSeconds))
            );
            if (displayedPlayerXp >= displayedLevelXpRequired - 0.001) {
                displayedPlayerXpLevel++;
                displayedPlayerXp = 0.0;
            }
            return;
        }

        if (displayedPlayerXp > targetXp) {
            displayedPlayerXp = targetXp;
            return;
        }
        if (displayedPlayerXp >= targetXp) {
            displayedPlayerXp = targetXp;
            return;
        }

        displayedPlayerXp = Math.min(targetXp, displayedPlayerXp + (slidePerSecond * Math.max(0.0, deltaSeconds)));
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
        g2d.setStroke(STROKE_5);
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
        drawArrow(g2d, direction, x, y, boxSize, color, false, 0.0, 0);
    }

    private void drawArrow(
            Graphics2D g2d,
            Direction direction,
            int x,
            int y,
            int boxSize,
            Color color,
            boolean wrongFlash,
            double hitShakeAmount,
            int shakeOffset
    ) {
        if (!wrongFlash && hitShakeAmount > 0.001 && shakeOffset != 0) {
            int alpha = clampInt((int) Math.round(180 * hitShakeAmount * sequenceHitShakeAlphaScale), 0, 220);
            drawArrowLines(g2d, direction, x - shakeOffset, y - sequenceHitShakeVerticalBias, boxSize, new Color(255, 86, 86, alpha));
            drawArrowLines(g2d, direction, x + shakeOffset, y + sequenceHitShakeVerticalBias, boxSize, new Color(86, 228, 255, alpha));
        }
        drawArrowLines(g2d, direction, x, y, boxSize, color);
    }

    private void drawArrowLines(Graphics2D g2d, Direction direction, int x, int y, int boxSize, Color color) {
        int cx = x + (boxSize / 2);
        int cy = y + (boxSize / 2);
        int shaft = boxSize / 4;
        int head = boxSize / 6;

        Stroke oldStroke = g2d.getStroke();
        g2d.setStroke(STROKE_5);
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
        drawArrowSprite(g2d, sprite, x, y, boxSize, false, 0.0, 0);
    }

    private void drawArrowSprite(
            Graphics2D g2d,
            BufferedImage sprite,
            int x,
            int y,
            int boxSize,
            boolean wrongFlash,
            double hitShakeAmount,
            int shakeOffset
    ) {
        int padding = 8;
        int size = boxSize - (padding * 2);
        if (!wrongFlash && hitShakeAmount > 0.001 && shakeOffset != 0) {
            Composite oldComposite = g2d.getComposite();
            float alpha = (float) Math.max(0.0, Math.min(0.62, 0.5 * hitShakeAmount * sequenceHitShakeAlphaScale));
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2d.drawImage(sprite, x + padding - shakeOffset, y + padding - sequenceHitShakeVerticalBias, size, size, null);
            g2d.drawImage(sprite, x + padding + shakeOffset, y + padding + sequenceHitShakeVerticalBias, size, size, null);
            g2d.setComposite(oldComposite);
        }
        g2d.drawImage(sprite, x + padding, y + padding, size, size, null);
    }

    private void drawSequenceSymbolBorder(
            Graphics2D g2d,
            int x,
            int y,
            int size,
            Color borderColor,
            boolean wrongFlash,
            double hitShakeAmount,
            int shakeOffset
    ) {
        if (!wrongFlash && hitShakeAmount > 0.001 && shakeOffset != 0) {
            int alpha = clampInt((int) Math.round(190 * hitShakeAmount * sequenceHitShakeAlphaScale), 0, 235);
            g2d.setColor(new Color(255, 86, 86, alpha));
            g2d.drawRect(x - shakeOffset, y - sequenceHitShakeVerticalBias, size, size);
            g2d.setColor(new Color(86, 228, 255, alpha));
            g2d.drawRect(x + shakeOffset, y + sequenceHitShakeVerticalBias, size, size);
        }
        g2d.setColor(borderColor);
        g2d.drawRect(x, y, size, size);
    }

    private double getSequenceHitShakeAmount(long now) {
        if (lastSequencePunchMs <= 0L) {
            return 0.0;
        }
        long elapsed = now - lastSequencePunchMs;
        if (elapsed < 0L || elapsed > sequenceHitShakeDurationMs) {
            return 0.0;
        }
        double normalized = elapsed / (double) sequenceHitShakeDurationMs;
        return 1.0 - (normalized * normalized);
    }

    private int getSequenceHitShakeOffset(long now) {
        if (lastSequencePunchMs <= 0L) {
            return 0;
        }
        long elapsed = now - lastSequencePunchMs;
        if (elapsed < 0L || elapsed > sequenceHitShakeDurationMs) {
            return 0;
        }
        return ((elapsed / sequenceHitShakePhaseStepMs) % 2L == 0L)
                ? sequenceHitShakeOffsetMagnitude
                : -sequenceHitShakeOffsetMagnitude;
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
        radioOverlaySprite = GameImageLoader.loadImage(getClass(), "radio.png");
        openingStaticGif = GameImageLoader.loadAnimatedImage(getClass(), "startup_static.gif");
        openingStaticSequenceMs = GameImageLoader.loadGifDurationMillis(getClass(), "startup_static.gif");
        poisonIconSprite = GameImageLoader.loadImage(getClass(), "poison_icon.png");
        poisonIconAttack1Sprite = GameImageLoader.loadImage(getClass(), "poison_icon_attack1.png");
        poisonIconAttack2Sprite = GameImageLoader.loadImage(getClass(), "poison_icon_attack2.png");
        poisonIconGlowSprite = poisonIconSprite != null ? GameImageLoader.tintSprite(poisonIconSprite, new Color(120, 255, 170)) : null;
        poisonIconHighlightSprite = poisonIconSprite != null ? GameImageLoader.tintSprite(poisonIconSprite, new Color(170, 255, 210)) : null;
        for (int i = 0; i < initialSurgeSprites.length; i++) {
            initialSurgeSprites[i] = GameImageLoader.loadImage(getClass(), "initial_surge" + (i + 1) + ".png");
            if (initialSurgeSprites[i] != null) {
                initialSurgeGlowSprites[i] = GameImageLoader.tintSprite(initialSurgeSprites[i], new Color(255, 248, 190));
            }
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
        int xpRequired = getXpRequiredForLevel(displayedPlayerXpLevel);
        drawXpBar(g2d, 0, 0, GameConfig.WIDTH, 10, xpRequired <= 0 ? 0.0 : displayedPlayerXp / xpRequired);
        g2d.setColor(WHITE);
        if (nextEncounterTimeBonusMs > 0L) {
            g2d.drawString("NEXT +" + nextEncounterTimeBonusMs + "MS", 28, 106);
        }

        if (now < lastXpGainUntilMs && lastXpGain > 0) {
            double popProgress = 1.0 - ((lastXpGainUntilMs - now) / 760.0);
            int yOffset = (int) Math.round(10 * popProgress);
            int alpha = (int) Math.round(255 * (1.0 - popProgress));
            alpha = Math.max(0, Math.min(255, alpha));
            g2d.setColor(new Color(120, 220, 255, alpha));
            g2d.drawString("+" + lastXpGain + " XP", 28, 56 - yOffset);
        }
    }

    private void drawXpBar(Graphics2D g2d, int x, int y, int width, int height, double ratio) {
        double clampedRatio = Math.max(0.0, Math.min(1.0, ratio));
        g2d.setColor(new Color(90, 96, 112, 72));
        g2d.fillRect(x, y, width, height);
        int fillWidth = Math.max(0, Math.min(width, (int) Math.round(width * clampedRatio)));
        if (fillWidth > 0) {
            g2d.setColor(new Color(255, 232, 72, 156));
            g2d.fillRect(x, y, fillWidth, height);
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
            g2d.setStroke(STROKE_1);
            g2d.setColor(HUD_LINE_WHITE_DIM);
            g2d.drawRect(x + 1, y + 1, w - 2, h - 2);
        }
        g2d.setStroke(old);
    }

    private void drawEdgeAnchors(Graphics2D g2d, int x, int y, int w, int h, Color color, int alpha) {
        if (w <= 24 || h <= 24 || alpha <= 0) {
            return;
        }

        Stroke oldStroke = g2d.getStroke();
        g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), clampInt(alpha, 0, 255)));

        int inset = 14;
        int longMark = 38;
        int shortMark = 16;
        int left = x + inset;
        int right = x + w - inset;
        int top = y + inset;
        int bottom = y + h - inset;

        g2d.drawLine(left, top, left + longMark, top);
        g2d.drawLine(left, top, left, top + shortMark);
        g2d.drawLine(right, top, right - longMark, top);
        g2d.drawLine(right, top, right, top + shortMark);
        g2d.drawLine(left, bottom, left + longMark, bottom);
        g2d.drawLine(left, bottom, left, bottom - shortMark);
        g2d.drawLine(right, bottom, right - longMark, bottom);
        g2d.drawLine(right, bottom, right, bottom - shortMark);

        int tickAlpha = clampInt(alpha / 2, 0, 255);
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), tickAlpha));
        int centerX = x + (w / 2);
        int centerY = y + (h / 2);
        g2d.drawLine(centerX - 11, top, centerX + 11, top);
        g2d.drawLine(centerX - 11, bottom, centerX + 11, bottom);
        g2d.drawLine(left, centerY - 11, left, centerY + 11);
        g2d.drawLine(right, centerY - 11, right, centerY + 11);

        g2d.setStroke(oldStroke);
    }

    private void drawEncounterTransition(Graphics2D g2d) {
        drawDungeonToEncounterExpansion(g2d);
    }

    private void drawDungeonToEncounterExpansion(Graphics2D g2d) {
        long elapsedMs = System.currentTimeMillis() - encounterTransitionStartMs;
        double progress = elapsedMs / (double) Math.max(1L, ENCOUNTER_TRANSITION_MS);
        progress = Math.max(0.0, Math.min(1.0, progress));

        double expandProgress = Math.max(0.0, Math.min(1.0, (progress - 0.14) / 0.86));
        double eased = easeOutCubic(expandProgress);
        int contentX = (int) Math.round(ROOM_X * (1.0 - eased));
        int contentY = (int) Math.round(ROOM_Y * (1.0 - eased));
        int contentW = (int) Math.round(ROOM_W + ((GameConfig.WIDTH - ROOM_W) * eased));
        int contentH = (int) Math.round(ROOM_H + ((GameConfig.HEIGHT - ROOM_H) * eased));
        int frameX = contentX;
        int frameY = contentY;
        int frameW = contentW;
        int frameH = contentH;

        drawEncounterExpansionAtmosphere(g2d, progress, frameX, frameY, frameW, frameH);

        Shape oldClip = g2d.getClip();
        g2d.clipRect(contentX, contentY, contentW, contentH);

        double dungeonFadeProgress = Math.max(0.0, Math.min(1.0, (progress - 0.34) / 0.34));
        float dungeonAlpha = (float) (1.0 - easeOutCubic(dungeonFadeProgress));
        Graphics2D dungeonG = (Graphics2D) g2d.create(contentX, contentY, contentW, contentH);
        dungeonG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        dungeonG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        dungeonG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        dungeonG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, dungeonAlpha));
        dungeonG.scale(contentW / (double) ROOM_W, contentH / (double) ROOM_H);
        dungeonG.translate(-ROOM_X, -ROOM_Y);
        drawDungeon(dungeonG, roomWorldWidth, roomWorldHeight, playerX, playerY, doorDirection, roomWalkableArea, roomEncounters, true, false);
        dungeonG.dispose();

        double revealProgress = Math.max(0.0, Math.min(1.0, (progress - 0.48) / 0.42));
        if (revealProgress > 0.0) {
            Composite oldComposite = g2d.getComposite();
            float alpha = (float) easeOutCubic(revealProgress);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            Graphics2D encounterG = (Graphics2D) g2d.create(contentX, contentY, contentW, contentH);
            encounterG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            encounterG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            encounterG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            encounterG.scale(contentW / (double) GameConfig.WIDTH, contentH / (double) GameConfig.HEIGHT);
            drawEncounterGameplay(encounterG);
            encounterG.dispose();
            g2d.setComposite(oldComposite);
        }

        g2d.setClip(oldClip);

        int pulseAlpha = clampInt((int) Math.round(155 * (1.0 - Math.min(1.0, progress / 0.28))), 0, 155);
        if (pulseAlpha > 0) {
            g2d.setColor(new Color(255, 255, 255, pulseAlpha));
            g2d.fillRect(ROOM_X + 1, ROOM_Y + 1, ROOM_W - 2, ROOM_H - 2);
        }

        drawEncounterExpansionFrame(g2d, frameX, frameY, frameW, frameH, 0, progress);
    }

    private void drawEncounterExpansionAtmosphere(Graphics2D g2d, double progress, int x, int y, int w, int h) {
        double impact = 1.0 - Math.max(0.0, Math.min(1.0, progress / 0.32));
        int bloomAlpha = clampInt((int) Math.round(70 * impact), 0, 70);
        if (bloomAlpha > 0) {
            g2d.setColor(new Color(255, 255, 255, bloomAlpha));
            g2d.fillRect(x - 10, y - 10, w + 20, h + 20);
        }

        double streakWindow = Math.sin(Math.max(0.0, Math.min(1.0, progress / 0.72)) * Math.PI);
        int streakAlpha = clampInt((int) Math.round(58 * streakWindow), 0, 58);
        if (streakAlpha <= 0) {
            return;
        }

        Stroke oldStroke = g2d.getStroke();
        g2d.setStroke(STROKE_1);
        for (int i = 0; i < 7; i++) {
            int laneY = y + 18 + (i * Math.max(16, h / 8));
            int offset = (int) Math.round((progress * 86) + (i * 19));
            int startX = x - 38 + (offset % 54);
            int endX = Math.min(x + w + 38, startX + 42 + (i % 3) * 14);
            g2d.setColor(new Color(80, 228, 255, Math.max(0, streakAlpha - (i * 5))));
            g2d.drawLine(startX, laneY, endX, laneY - 7);
        }
        g2d.setStroke(oldStroke);
    }

    private void drawEncounterExpansionFrame(Graphics2D g2d, int x, int y, int w, int h, int alpha, double progress) {
        Stroke oldStroke = g2d.getStroke();
        int smear = 1 + (int) Math.round(5 * Math.sin(Math.max(0.0, Math.min(1.0, progress / 0.55)) * Math.PI));
        int ghostAlpha = clampInt((int) Math.round(alpha * 0.28), 0, 72);
        Color transitionColor = lerpColor(DUNGEON_FRAME_YELLOW, DUNGEON_FRAME_PINK, easeInOut(progress));
        Color borderColor = new Color(transitionColor.getRed(), transitionColor.getGreen(), transitionColor.getBlue(), alpha);

        if (ghostAlpha > 0) {
            g2d.setStroke(STROKE_1);
            g2d.setColor(new Color(80, 228, 255, ghostAlpha));
            g2d.drawRect(x - smear, y, w, h);
            g2d.setColor(new Color(255, 58, 98, ghostAlpha));
            g2d.drawRect(x + smear, y, w, h);
        }

        drawEncounterEdgeFrame(g2d, x, y, w, h, borderColor);
        g2d.setStroke(oldStroke);
    }

    private void drawEncounterScreenFrame(Graphics2D g2d, int alpha) {
        drawEncounterEdgeFrame(
                g2d,
                0,
                0,
                GameConfig.WIDTH,
                GameConfig.HEIGHT,
                new Color(DUNGEON_FRAME_PINK.getRed(), DUNGEON_FRAME_PINK.getGreen(), DUNGEON_FRAME_PINK.getBlue(), alpha)
        );
    }

    private void drawEncounterEdgeFrame(Graphics2D g2d, int x, int y, int w, int h, Color color) {
        if (w <= 2 || h <= 2) {
            return;
        }

        Stroke oldStroke = g2d.getStroke();
        int glowAlpha = Math.min(72, Math.max(0, color.getAlpha() / 4));
        int coreAlpha = Math.min(205, Math.max(0, color.getAlpha()));

        g2d.setStroke(new BasicStroke(4f));
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), glowAlpha));
        g2d.drawRect(x, y, w, h);

        g2d.setStroke(STROKE_1);
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), coreAlpha));
        g2d.drawRect(x, y, w, h);
        g2d.setStroke(oldStroke);
    }

    private void drawEncounterTransitionLabel(Graphics2D g2d, int x, int y, int width, int height, double progress) {
        double labelIn = Math.max(0.0, Math.min(1.0, progress / 0.22));
        double labelOut = 1.0 - Math.max(0.0, Math.min(1.0, (progress - 0.52) / 0.24));
        int alpha = clampInt((int) Math.round(255 * easeOutCubic(labelIn) * labelOut), 0, 255);
        if (alpha <= 0) {
            return;
        }

        g2d.setFont(TRANSITION_ENEMY_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        int centerX = x + (width / 2);
        int centerY = y + (height / 2);
        int baselineY = centerY - (metrics.getHeight() / 2) + metrics.getAscent();

        int pulseAlpha = clampInt((int) Math.round(110 * Math.sin(Math.min(1.0, progress / 0.32) * Math.PI)), 0, 110);
        if (pulseAlpha > 0) {
            g2d.setColor(new Color(RED.getRed(), RED.getGreen(), RED.getBlue(), pulseAlpha));
            g2d.fillRect(x, y, width, height);
        }

        drawGlowingCenteredString(
                g2d,
                "ENCOUNTER",
                centerX,
                baselineY,
                new Color(RED.getRed(), RED.getGreen(), RED.getBlue(), alpha),
                new Color(RED.getRed(), RED.getGreen(), RED.getBlue(), Math.max(20, alpha / 2))
        );
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
        drawDirectionalRoomBoxTransition(
                g2d,
                roomTransitionStartMs,
                ROOM_TRANSITION_MS,
                ROOM_TRANSITION_HOLD_MS,
                "ADVANCING",
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

    private void drawDirectionalRoomBoxTransition(
            Graphics2D g2d,
            long startMs,
            long transitionMs,
            long holdMs,
            String text,
            Direction direction
    ) {
        if (roomTransitionPreviousState == null) {
            drawDungeon(g2d);
            return;
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        double totalProgress = elapsedMs / (double) (transitionMs + holdMs);
        totalProgress = Math.max(0.0, Math.min(1.0, totalProgress));

        if (direction == null) {
            direction = Direction.RIGHT;
        }

        double slideProgress = elapsedMs / (double) Math.max(1L, transitionMs);
        slideProgress = Math.max(0.0, Math.min(1.0, slideProgress));
        double eased = easeOutCubic(slideProgress);
        int travelX = GameConfig.WIDTH + 32;
        int travelY = GameConfig.HEIGHT + 32;
        double outgoingScale = 1.0 + (0.11 * Math.min(1.0, slideProgress / 0.42));
        double incomingScale = 1.12 - (0.12 * eased);

        int oldOffsetX = 0;
        int oldOffsetY = 0;
        int newOffsetX = 0;
        int newOffsetY = 0;
        switch (direction) {
            case LEFT:
                oldOffsetX = (int) Math.round(travelX * eased);
                newOffsetX = oldOffsetX - travelX;
                break;
            case RIGHT:
                oldOffsetX = -(int) Math.round(travelX * eased);
                newOffsetX = oldOffsetX + travelX;
                break;
            case UP:
                oldOffsetY = (int) Math.round(travelY * eased);
                newOffsetY = oldOffsetY - travelY;
                break;
            case DOWN:
                oldOffsetY = -(int) Math.round(travelY * eased);
                newOffsetY = oldOffsetY + travelY;
                break;
            default:
                break;
        }

        drawScaledDungeon(g2d, roomTransitionPreviousState, oldOffsetX, oldOffsetY, outgoingScale);
        drawScaledDungeon(g2d, null, newOffsetX, newOffsetY, incomingScale);
    }

    private void drawScaledDungeon(Graphics2D g2d, RoomRenderState state, int offsetX, int offsetY, double scale) {
        int centerX = ROOM_X + (ROOM_W / 2);
        int centerY = ROOM_Y + (ROOM_H / 2);
        Graphics2D roomG = (Graphics2D) g2d.create();
        roomG.translate(offsetX + centerX, offsetY + centerY);
        roomG.scale(scale, scale);
        roomG.translate(-centerX, -centerY);
        if (state == null) {
            drawDungeon(roomG);
        } else {
            drawDungeon(roomG, state);
        }
        roomG.dispose();
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
        double progress = (System.currentTimeMillis() - roomIntroStartMs) / (double) ROOM_INTRO_MS;
        progress = Math.max(0.0, Math.min(1.0, progress));

        int overlayAlpha = (int) Math.round(110 * (1.0 - progress));
        if (overlayAlpha <= 0) {
            return;
        }

        Shape oldClip = g2d.getClip();
        g2d.clipRect(ROOM_X + 1, ROOM_Y + 1, ROOM_W - 2, ROOM_H - 2);
        g2d.setColor(new Color(ROOM_GLASS.getRed(), ROOM_GLASS.getGreen(), ROOM_GLASS.getBlue(), overlayAlpha));
        g2d.fillRect(ROOM_X + 1, ROOM_Y + 1, ROOM_W - 2, ROOM_H - 2);
        g2d.setClip(oldClip);
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

    private void drawWrappedCenteredText(
            Graphics2D g2d,
            String text,
            int centerX,
            int startBaselineY,
            int maxWidth,
            int maxLines,
            int lineHeight
    ) {
        FontMetrics metrics = g2d.getFontMetrics();
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (metrics.stringWidth(candidate) <= maxWidth || current.length() == 0) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }

        int lineCount = Math.min(maxLines, lines.size());
        for (int i = 0; i < lineCount; i++) {
            String line = lines.get(i);
            if (i == lineCount - 1 && lines.size() > maxLines) {
                while (line.length() > 3 && metrics.stringWidth(line + "...") > maxWidth) {
                    line = line.substring(0, line.length() - 1);
                }
                line += "...";
            }
            drawCenteredString(g2d, line, centerX, startBaselineY + (i * lineHeight));
        }
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
