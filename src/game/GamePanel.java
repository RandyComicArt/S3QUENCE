package game;

import game.audio.AudioManager;
import game.config.GameConfig;
import game.logic.RoundCompletion;
import game.logic.RoundManager;
import game.model.Direction;
import game.model.EnemyArchetype;
import game.model.EncounterEnemy;
import game.model.EncounterNode;
import game.model.ScreenState;
import game.model.ShopOption;
import game.visual.BackdropEffects;
import game.visual.EnemyKillEffects;

import javax.imageio.ImageIO;
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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

@SuppressWarnings({"serial", "this-escape"})
public class GamePanel extends JPanel implements ActionListener {
    private static final Color BG = new Color(1, 5, 16);
    private static final Color WHITE = new Color(194, 236, 255);
    private static final Color TEXT_DIM = new Color(104, 156, 208);
    private static final Color YELLOW = new Color(94, 245, 255);
    private static final Color GREEN = new Color(74, 228, 255);
    private static final Color RED = new Color(255, 89, 177);
    private static final Color GLOW_CYAN = new Color(94, 245, 255);
    private static final Color TIMER_HIGH = new Color(98, 247, 255);
    private static final Color TIMER_LOW = new Color(74, 106, 255);
    private static final Color ARENA_GLASS = new Color(2, 10, 24, 102);
    private static final Color ROOM_GLASS = new Color(4, 18, 40, 90);
    private static final String MENU_MUSIC_FILE = "main_menu.wav";
    private static final String GAMEPLAY_MUSIC_FILE = "Loop_drum.wav";

    private static final Font TITLE_FONT = new Font("Monospaced", Font.BOLD, 40);
    private static final Font HUD_FONT = new Font("Monospaced", Font.BOLD, 24);
    private static final Font BODY_FONT = new Font("Monospaced", Font.PLAIN, 20);
    private static final Font SMALL_FONT = new Font("Monospaced", Font.PLAIN, 16);
    private static final Font TRANSITION_ENEMY_FONT = new Font("Monospaced", Font.BOLD, 36);

    private static final int ARENA_X = 120;
    private static final int ARENA_Y = 170;
    private static final int ARENA_W = GameConfig.WIDTH - 240;
    private static final int ARENA_H = 420;
    private static final int ENCOUNTER_ARENA_Y = 170;
    private static final int ENEMY_BAR_X = ARENA_X + 150;
    private static final int ENEMY_BAR_W = ARENA_W - 300;
    private static final int ENEMY_BAR_H = 22;

    private static final int ROOM_X = ARENA_X + 35;
    private static final int ROOM_Y = ARENA_Y + 40;
    private static final int ROOM_W = ARENA_W - 70;
    private static final int ROOM_H = ARENA_H - 65;
    private static final int DOOR_W = 16;
    private static final int DOOR_H = 88;
    private static final int PLAYER_SIZE = 18;
    private static final double PLAYER_SPEED_PER_SECOND = 280.0;
    private static final int ENCOUNTER_SIZE = 18;
    private static final long ENCOUNTER_TRANSITION_MS = 1280L;
    private static final long ENCOUNTER_TRANSITION_HOLD_MS = 360L;
    private static final long ENCOUNTER_INTRO_MS = 360L;
    private static final long ROOM_TRANSITION_MS = 720L;
    private static final long ROOM_TRANSITION_HOLD_MS = 180L;
    private static final long ROOM_INTRO_MS = 360L;
    private static final long RUN_START_FADE_IN_MS = 1000L;
    private static final long MENU_TRANSITION_MS = 520L;
    private static final long MENU_TRANSITION_SWITCH_MS = MENU_TRANSITION_MS / 2;
    private static final int ENCOUNTER_TEXT_HANDOFF_OFFSET = 120;
    private static final double TIMER_REFILL_ANIM_PER_SECOND = 3600.0;
    private static final int MENU_ITEM_START = 0;
    private static final int MENU_ITEM_TEST_ENEMY = 1;
    private static final int MENU_ITEM_SETTINGS = 2;
    private static final int MENU_ITEM_COUNT = 3;
    private static final int SETTINGS_ITEM_RENDER_QUALITY = 0;
    private static final int SETTINGS_ITEM_CRT_BLEED = 1;
    private static final int SETTINGS_ITEM_CRT_BRIGHTNESS = 2;
    private static final int SETTINGS_ITEM_ASPECT = 3;
    private static final int SETTINGS_ITEM_BACK = 4;
    private static final int SETTINGS_ITEM_COUNT = 5;
    private static final int SHOP_ITEM_COUNT = 3;
    private static final int MAX_HEARTS = 3;
    private static final int MAX_HEALTH_UNITS = MAX_HEARTS * 2;
    private static final int HEART_GAP = 22;
    private static final int HEART_BG_MARGIN_X = 20;
    private static final int HEART_BG_Y = 24;
    private static final float HEART_BG_ALPHA = 0.28f;
    private static final int SEQUENCE_SYMBOL_SIZE = 76;
    private static final int SEQUENCE_SYMBOL_GAP = 24;
    private static final int[] RENDER_QUALITY_WIDTHS = {480, 560, 640};
    private static final int[] RENDER_QUALITY_HEIGHTS = {360, 420, 480};
    private static final String[] RENDER_QUALITY_LABELS = {"PERFORMANCE", "BALANCED", "CLARITY"};
    private static final String[] ASPECT_MODE_LABELS = {"ORIGINAL", "FILL"};
    private static final float[] CRT_BRIGHTNESS_LEVELS = {
            0.50f, 0.60f, 0.70f, 0.80f, 0.90f,
            1.00f, 1.10f, 1.20f, 1.30f, 1.40f,
            1.50f, 1.60f, 1.70f, 1.80f, 1.90f,
            2.00f, 2.10f
    };
    private static final double CRT_WARP_STRENGTH = 0.085;
    private static final double CRT_VERTICAL_CURVE_STRENGTH = 0.04;
    private static final int CRT_WARP_STRIP_PX = 2;
    private static final boolean DEFAULT_CRT_COLOR_BLEED = true;
    private static final int DEFAULT_RENDER_QUALITY_INDEX = 0;
    private static final int DEFAULT_BRIGHTNESS_INDEX = 12;
    private static final int CRT_BLEED_OFFSET_X = 2;
    private static final int CRT_BLEED_OFFSET_Y = 1;
    private static final float CRT_BLEED_INTENSITY = 0.32f;
    private static final RescaleOp CRT_BLEED_RED_OP = new RescaleOp(
            new float[]{1f, 0f, 0f, 1f},
            new float[]{0f, 0f, 0f, 0f},
            null
    );
    private static final RescaleOp CRT_BLEED_GREEN_OP = new RescaleOp(
            new float[]{0f, 1f, 0f, 1f},
            new float[]{0f, 0f, 0f, 0f},
            null
    );
    private static final RescaleOp CRT_BLEED_BLUE_OP = new RescaleOp(
            new float[]{0f, 0f, 1f, 1f},
            new float[]{0f, 0f, 0f, 0f},
            null
    );

    private final Timer timer;
    private final RoundManager roundManager = new RoundManager();
    private final BackdropEffects backdropEffects = new BackdropEffects();
    private final EnemyKillEffects enemyKillEffects = new EnemyKillEffects();
    private final EnumMap<Direction, BufferedImage> arrowSprites = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, BufferedImage> arrowSpritesGreen = new EnumMap<>(Direction.class);
    private BufferedImage fullHeartSprite;
    private BufferedImage halfHeartSprite;
    private BufferedImage emptyHeartSprite;
    private BufferedImage megamanTransitionSprite;
    private BufferedImage sceneBuffer;
    private BufferedImage crtWarpBuffer;
    private BufferedImage crtOverlayBuffer;
    private BufferedImage crtMatteBuffer;
    private BufferedImage crtBleedBuffer;
    private BufferedImage crtBrightnessBuffer;
    private RescaleOp crtBrightnessOp;
    private int[] crtRowXs;
    private int[] crtRowWidths;
    private int[] crtColTops;
    private int[] crtColBottoms;

    private final Random random = new Random();
    private final List<EncounterNode> roomEncounters = new ArrayList<>();

    private ScreenState screen = ScreenState.MENU;
    private int roomNumber = 1;
    private double playerX;
    private double playerY;
    private int activeEncounterIndex = -1;
    private int pendingEncounterIndex = -1;
    private boolean encounterTransitionActive;
    private long encounterTransitionStartMs;
    private boolean encounterIntroActive;
    private long encounterIntroStartMs;
    private boolean roomTransitionActive;
    private long roomTransitionStartMs;
    private boolean roomIntroActive;
    private long roomIntroStartMs;
    private Direction pendingRoomEntryDirection;
    private boolean moveUpHeld;
    private boolean moveDownHeld;
    private boolean moveLeftHeld;
    private boolean moveRightHeld;
    private long lastTickNanos = System.nanoTime();
    private int lastHitDamage;
    private long lastHitUntilMs;
    private boolean runStartFadeInActive;
    private long runStartFadeInStartMs;
    private boolean menuTransitionActive;
    private long menuTransitionStartMs;
    private int coinCount;
    private int lastCoinGain;
    private long lastCoinGainUntilMs;
    private String activeMusicFile;
    private long displayedTimerMs = -1L;
    private long displayedTimerDurationMs = 1L;
    private int playerHealthUnits = MAX_HEALTH_UNITS;
    private int menuSelectionIndex = MENU_ITEM_START;
    private int settingsSelectionIndex = SETTINGS_ITEM_RENDER_QUALITY;
    private int shopSelectionIndex;
    private long mistakeGuardCharges;
    private long nextEncounterTimeBonusMs;
    private EnemyArchetype forcedTestEnemy;
    private Direction doorDirection = Direction.RIGHT;
    private int sceneBufferWidth = RENDER_QUALITY_WIDTHS[DEFAULT_RENDER_QUALITY_INDEX];
    private int sceneBufferHeight = RENDER_QUALITY_HEIGHTS[DEFAULT_RENDER_QUALITY_INDEX];
    private int renderQualityIndex = DEFAULT_RENDER_QUALITY_INDEX;
    private boolean crtBleedEnabled = DEFAULT_CRT_COLOR_BLEED;
    private boolean crtScanlinesEnabled = true;
    private int crtBrightnessIndex = DEFAULT_BRIGHTNESS_INDEX;
    private float crtBrightnessGain = CRT_BRIGHTNESS_LEVELS[DEFAULT_BRIGHTNESS_INDEX];
    private float lastCrtBrightnessGain = -1f;
    private int aspectModeIndex = 0;

    public GamePanel() {
        setPreferredSize(new Dimension(GameConfig.WIDTH, GameConfig.HEIGHT));
        setBackground(BG);
        setFocusable(true);
        loadArrowSprites();
        loadHeartSprites();
        loadTransitionSprites();
        setupMovementDispatcher();
        setupKeyBindings();
        updateBackgroundMusic();
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

        ensureCrtGeometry(renderWidth, renderHeight);
        drawCurvedScreenImage(gameG, sceneBuffer, renderWidth, renderHeight);
        applyCrtOverlay(gameG, renderWidth, renderHeight);
        drawCurvedScreenMatte(gameG, renderWidth, renderHeight);

        gameG.dispose();
        g2d.dispose();
    }

    private void renderGameScene(Graphics2D gameG) {
        int gameWidth = GameConfig.WIDTH;
        int gameHeight = GameConfig.HEIGHT;

        gameG.setColor(BG);
        gameG.fillRect(0, 0, gameWidth, gameHeight);
        backdropEffects.drawBackdrop(gameG, screen, game.model.TimerStyle.BACKDROP_HUE, getEncounterTimerProgress());

        if (screen == ScreenState.MENU) {
            drawMenu(gameG);
            if (menuTransitionActive) {
                drawMenuTransitionOverlay(gameG);
            }
            return;
        }
        if (screen == ScreenState.SETTINGS) {
            drawSettingsMenu(gameG);
            return;
        }

        drawHeartHud(gameG);

        if (screen == ScreenState.DUNGEON || screen == ScreenState.SHOP) {
            drawDungeon(gameG);
            if (screen == ScreenState.SHOP) {
                drawShopOverlay(gameG);
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
        if (menuTransitionActive) {
            drawMenuTransitionOverlay(gameG);
        }
    }

    private void drawCurvedScreenImage(Graphics2D g2d, BufferedImage source, int targetWidth, int targetHeight) {
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, targetWidth, targetHeight);

        BufferedImage warpSource = source;
        if (crtBleedEnabled) {
            warpSource = buildCrtBleedBuffer(source);
        }
        if (Math.abs(crtBrightnessGain - 1.0f) > 0.001f) {
            warpSource = applyCrtBrightness(warpSource);
        }

        if (crtWarpBuffer == null || crtWarpBuffer.getWidth() != targetWidth || crtWarpBuffer.getHeight() != targetHeight) {
            crtWarpBuffer = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        }

        Graphics2D warpG = crtWarpBuffer.createGraphics();
        warpG.setComposite(AlphaComposite.Src);
        warpG.setColor(new Color(0, 0, 0, 0));
        warpG.fillRect(0, 0, targetWidth, targetHeight);

        for (int y = 0; y < targetHeight; y += CRT_WARP_STRIP_PX) {
            int stripHeight = Math.min(CRT_WARP_STRIP_PX, targetHeight - y);
            int rowWidth = crtRowWidths[y];
            int rowX = crtRowXs[y];

            int srcY0 = (int) ((y / (double) targetHeight) * warpSource.getHeight());
            int srcY1 = (int) (((y + stripHeight) / (double) targetHeight) * warpSource.getHeight());
            if (srcY1 <= srcY0) {
                srcY1 = Math.min(warpSource.getHeight(), srcY0 + 1);
            }

            warpG.drawImage(
                    warpSource,
                    rowX,
                    y,
                    rowX + rowWidth,
                    y + stripHeight,
                    0,
                    srcY0,
                    warpSource.getWidth(),
                    srcY1,
                    null
            );
        }
        warpG.dispose();

        for (int x = 0; x < targetWidth; x += CRT_WARP_STRIP_PX) {
            int stripWidth = Math.min(CRT_WARP_STRIP_PX, targetWidth - x);
            int destTop = crtColTops[x];
            int destBottom = crtColBottoms[x];
            if (destBottom <= destTop) {
                continue;
            }

            g2d.drawImage(
                    crtWarpBuffer,
                    x,
                    destTop,
                    x + stripWidth,
                    destBottom,
                    x,
                    0,
                    x + stripWidth,
                    targetHeight,
                    null
            );
        }
    }

    private void drawCurvedScreenMatte(Graphics2D g2d, int targetWidth, int targetHeight) {
        if (crtMatteBuffer == null || crtMatteBuffer.getWidth() != targetWidth || crtMatteBuffer.getHeight() != targetHeight) {
            crtMatteBuffer = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D matteG = crtMatteBuffer.createGraphics();
            matteG.setComposite(AlphaComposite.Src);
            matteG.setColor(new Color(0, 0, 0, 0));
            matteG.fillRect(0, 0, targetWidth, targetHeight);
            matteG.setColor(Color.BLACK);
            for (int y = 0; y < targetHeight; y++) {
                int rowWidth = crtRowWidths[y];
                int rowX = crtRowXs[y];
                if (rowX > 0) {
                    matteG.drawLine(0, y, rowX, y);
                    matteG.drawLine(rowX + rowWidth, y, targetWidth, y);
                }
            }
            for (int x = 0; x < targetWidth; x++) {
                int verticalInset = crtColTops[x];
                if (verticalInset > 0) {
                    matteG.drawLine(x, 0, x, verticalInset);
                    matteG.drawLine(x, targetHeight - verticalInset, x, targetHeight);
                }
            }
            matteG.dispose();
        }
        g2d.drawImage(crtMatteBuffer, 0, 0, null);
    }

    private BufferedImage buildCrtBleedBuffer(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (crtBleedBuffer == null || crtBleedBuffer.getWidth() != width || crtBleedBuffer.getHeight() != height) {
            crtBleedBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        Graphics2D bleedG = crtBleedBuffer.createGraphics();
        bleedG.setComposite(AlphaComposite.Src);
        bleedG.setColor(new Color(0, 0, 0, 0));
        bleedG.fillRect(0, 0, width, height);
        bleedG.drawImage(source, 0, 0, null);

        bleedG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, CRT_BLEED_INTENSITY));
        bleedG.drawImage(source, CRT_BLEED_RED_OP, CRT_BLEED_OFFSET_X, 0);
        bleedG.drawImage(source, CRT_BLEED_GREEN_OP, 0, CRT_BLEED_OFFSET_Y);
        bleedG.drawImage(source, CRT_BLEED_BLUE_OP, -CRT_BLEED_OFFSET_X, 0);
        bleedG.dispose();

        return crtBleedBuffer;
    }

    private BufferedImage applyCrtBrightness(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (crtBrightnessBuffer == null || crtBrightnessBuffer.getWidth() != width || crtBrightnessBuffer.getHeight() != height) {
            crtBrightnessBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
        getCrtBrightnessOp().filter(source, crtBrightnessBuffer);
        return crtBrightnessBuffer;
    }

    private RescaleOp getCrtBrightnessOp() {
        if (crtBrightnessOp == null || Math.abs(lastCrtBrightnessGain - crtBrightnessGain) > 0.001f) {
            lastCrtBrightnessGain = crtBrightnessGain;
            crtBrightnessOp = new RescaleOp(
                    new float[]{crtBrightnessGain, crtBrightnessGain, crtBrightnessGain, 1f},
                    new float[]{0f, 0f, 0f, 0f},
                    null
            );
        }
        return crtBrightnessOp;
    }

    private void ensureCrtGeometry(int targetWidth, int targetHeight) {
        boolean sizeChanged = crtRowXs == null
                || crtRowXs.length != targetHeight
                || crtColTops == null
                || crtColTops.length != targetWidth;
        if (!sizeChanged) {
            return;
        }

        crtRowXs = new int[targetHeight];
        crtRowWidths = new int[targetHeight];
        for (int y = 0; y < targetHeight; y++) {
            double normalizedY = ((y + 0.5) / targetHeight) * 2.0 - 1.0;
            double curveFactor = 1.0 - (CRT_WARP_STRENGTH * (normalizedY * normalizedY));
            int rowWidth = Math.max(1, (int) Math.round(targetWidth * curveFactor));
            crtRowWidths[y] = rowWidth;
            crtRowXs[y] = (targetWidth - rowWidth) / 2;
        }

        crtColTops = new int[targetWidth];
        crtColBottoms = new int[targetWidth];
        for (int x = 0; x < targetWidth; x++) {
            double normalizedX = ((x + 0.5) / targetWidth) * 2.0 - 1.0;
            int verticalInset = (int) Math.round(targetHeight * CRT_VERTICAL_CURVE_STRENGTH * (normalizedX * normalizedX));
            crtColTops[x] = verticalInset;
            crtColBottoms[x] = targetHeight - verticalInset;
        }

        crtOverlayBuffer = null;
        crtMatteBuffer = null;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double deltaSeconds = (now - lastTickNanos) / 1_000_000_000.0;
        lastTickNanos = now;

        // Prevent giant movement jumps after focus loss or window stalls.
        deltaSeconds = Math.min(deltaSeconds, 0.05);

        if (menuTransitionActive) {
            updateMenuTransition();
        } else {
            if (screen == ScreenState.DUNGEON && !encounterTransitionActive && !roomTransitionActive && !roomIntroActive) {
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
                }
            }
            if (screen == ScreenState.ENCOUNTER && !encounterIntroActive && roundManager.hasTimedOut()) {
                handleEncounterTimeout();
            }
        }
        if (runStartFadeInActive) {
            long fadeElapsedMs = System.currentTimeMillis() - runStartFadeInStartMs;
            if (fadeElapsedMs >= RUN_START_FADE_IN_MS) {
                runStartFadeInActive = false;
            }
        }
        updateTimerBarAnimation(deltaSeconds);
        updateBackgroundMusic();
        backdropEffects.update();
        updateEnemyKillEffects();
        repaint();
    }

    private void drawMenu(Graphics2D g2d) {
        int boxX = 110;
        int boxY = 150;
        int boxW = GameConfig.WIDTH - 220;
        int boxH = GameConfig.HEIGHT - 250;

        g2d.setColor(new Color(4, 15, 36, 220));
        g2d.fillRect(boxX + 2, boxY + 2, boxW - 3, boxH - 3);
        drawFrame(g2d, boxX, boxY, boxW, boxH, 4, WHITE);

        g2d.setFont(TITLE_FONT);
        drawGlowingCenteredString(g2d, "* S3QUENCE *", GameConfig.WIDTH / 2, boxY + 90, WHITE, GLOW_CYAN);

        g2d.setFont(BODY_FONT);
        g2d.setColor(TEXT_DIM);
        drawCenteredString(g2d, "Arrows Edition", GameConfig.WIDTH / 2, boxY + 145);
        drawCenteredString(g2d, "Use Arrow Keys in Encounters", GameConfig.WIDTH / 2, boxY + 206);

        drawMenuOption(g2d, MENU_ITEM_START, "START RUN", GameConfig.WIDTH / 2, boxY + 265);
        drawMenuOption(
                g2d,
                MENU_ITEM_TEST_ENEMY,
                "TEST ENEMY: " + getTestEnemyMenuLabel(),
                GameConfig.WIDTH / 2,
                boxY + 305
        );
        drawMenuOption(g2d, MENU_ITEM_SETTINGS, "SETTINGS", GameConfig.WIDTH / 2, boxY + 345);

        g2d.setFont(SMALL_FONT);
        g2d.setColor(TEXT_DIM);
        drawCenteredString(g2d, "UP/DOWN SELECT  |  LEFT/RIGHT CHANGE", GameConfig.WIDTH / 2, boxY + boxH - 58);
        drawCenteredString(g2d, "ENTER CONFIRM  |  ESC MENU", GameConfig.WIDTH / 2, boxY + boxH - 30);
    }

    private void drawSettingsMenu(Graphics2D g2d) {
        int boxX = 110;
        int boxY = 130;
        int boxW = GameConfig.WIDTH - 220;
        int boxH = GameConfig.HEIGHT - 210;

        g2d.setColor(new Color(4, 15, 36, 220));
        g2d.fillRect(boxX + 2, boxY + 2, boxW - 3, boxH - 3);
        drawFrame(g2d, boxX, boxY, boxW, boxH, 4, WHITE);

        g2d.setFont(TITLE_FONT);
        drawGlowingCenteredString(g2d, "SETTINGS", GameConfig.WIDTH / 2, boxY + 80, WHITE, GLOW_CYAN);

        int lineY = boxY + 150;
        int lineStep = 42;
        drawSettingsOption(g2d, SETTINGS_ITEM_RENDER_QUALITY, "RENDER QUALITY", getRenderQualityLabel(), lineY);
        lineY += lineStep;
        drawSettingsOption(g2d, SETTINGS_ITEM_CRT_BLEED, "CRT BLEED", getCrtBleedLabel(), lineY);
        lineY += lineStep;
        drawSettingsBrightness(g2d, SETTINGS_ITEM_CRT_BRIGHTNESS, lineY);
        lineY += lineStep;
        drawSettingsOption(g2d, SETTINGS_ITEM_ASPECT, "ASPECT RATIO", getAspectLabel(), lineY);
        lineY += lineStep;
        drawSettingsOption(g2d, SETTINGS_ITEM_BACK, "BACK", "", lineY);

        g2d.setFont(SMALL_FONT);
        g2d.setColor(TEXT_DIM);
        drawCenteredString(g2d, "UP/DOWN SELECT  |  LEFT/RIGHT CHANGE", GameConfig.WIDTH / 2, boxY + boxH - 58);
        drawCenteredString(g2d, "ENTER TOGGLE  |  ESC BACK", GameConfig.WIDTH / 2, boxY + boxH - 30);
    }

    private void drawSettingsOption(Graphics2D g2d, int optionIndex, String label, String value, int baselineY) {
        boolean selected = settingsSelectionIndex == optionIndex;
        String display = value == null || value.isBlank() ? label : label + ": " + value;
        int centerX = GameConfig.WIDTH / 2;
        g2d.setFont(BODY_FONT);
        if (selected) {
            drawGlowingCenteredString(g2d, "> " + display + " <", centerX, baselineY, YELLOW, GLOW_CYAN);
        } else {
            g2d.setColor(WHITE);
            drawCenteredString(g2d, display, centerX, baselineY);
        }
    }

    private void drawMenuOption(Graphics2D g2d, int optionIndex, String label, int centerX, int baselineY) {
        boolean selected = menuSelectionIndex == optionIndex;
        g2d.setFont(BODY_FONT);
        if (selected) {
            drawGlowingCenteredString(g2d, "> " + label + " <", centerX, baselineY, YELLOW, GLOW_CYAN);
        } else {
            g2d.setColor(WHITE);
            drawCenteredString(g2d, label, centerX, baselineY);
        }
    }

    private void handleMenuDirection(Direction direction) {
        if (direction == Direction.UP) {
            menuSelectionIndex = (menuSelectionIndex - 1 + MENU_ITEM_COUNT) % MENU_ITEM_COUNT;
        } else if (direction == Direction.DOWN) {
            menuSelectionIndex = (menuSelectionIndex + 1) % MENU_ITEM_COUNT;
        } else if (menuSelectionIndex == MENU_ITEM_TEST_ENEMY) {
            if (direction == Direction.LEFT) {
                cycleTestEnemy(-1);
            } else if (direction == Direction.RIGHT) {
                cycleTestEnemy(1);
            }
        }
    }

    private void activateSelectedMenuItem() {
        if (menuSelectionIndex == MENU_ITEM_START) {
            startRun();
        } else if (menuSelectionIndex == MENU_ITEM_TEST_ENEMY) {
            cycleTestEnemy(1);
        } else if (menuSelectionIndex == MENU_ITEM_SETTINGS) {
            settingsSelectionIndex = SETTINGS_ITEM_RENDER_QUALITY;
            screen = ScreenState.SETTINGS;
        }
    }

    private void handleSettingsDirection(Direction direction) {
        if (direction == Direction.UP) {
            settingsSelectionIndex = (settingsSelectionIndex - 1 + SETTINGS_ITEM_COUNT) % SETTINGS_ITEM_COUNT;
            return;
        }
        if (direction == Direction.DOWN) {
            settingsSelectionIndex = (settingsSelectionIndex + 1) % SETTINGS_ITEM_COUNT;
            return;
        }

        int delta = direction == Direction.LEFT ? -1 : direction == Direction.RIGHT ? 1 : 0;
        if (delta == 0) {
            return;
        }

        if (settingsSelectionIndex == SETTINGS_ITEM_RENDER_QUALITY) {
            renderQualityIndex = wrapIndex(renderQualityIndex + delta, RENDER_QUALITY_LABELS.length);
            applyRenderQuality();
        } else if (settingsSelectionIndex == SETTINGS_ITEM_CRT_BLEED) {
            crtBleedEnabled = !crtBleedEnabled;
        } else if (settingsSelectionIndex == SETTINGS_ITEM_CRT_BRIGHTNESS) {
            crtBrightnessIndex = wrapIndex(crtBrightnessIndex + delta, CRT_BRIGHTNESS_LEVELS.length);
            crtBrightnessGain = CRT_BRIGHTNESS_LEVELS[crtBrightnessIndex];
        } else if (settingsSelectionIndex == SETTINGS_ITEM_ASPECT) {
            aspectModeIndex = wrapIndex(aspectModeIndex + delta, ASPECT_MODE_LABELS.length);
        }
    }

    private void activateSelectedSettingsItem() {
        if (settingsSelectionIndex == SETTINGS_ITEM_BACK) {
            screen = ScreenState.MENU;
            return;
        }

        if (settingsSelectionIndex == SETTINGS_ITEM_RENDER_QUALITY) {
            renderQualityIndex = wrapIndex(renderQualityIndex + 1, RENDER_QUALITY_LABELS.length);
            applyRenderQuality();
        } else if (settingsSelectionIndex == SETTINGS_ITEM_CRT_BLEED) {
            crtBleedEnabled = !crtBleedEnabled;
        } else if (settingsSelectionIndex == SETTINGS_ITEM_CRT_BRIGHTNESS) {
            crtBrightnessIndex = wrapIndex(crtBrightnessIndex + 1, CRT_BRIGHTNESS_LEVELS.length);
            crtBrightnessGain = CRT_BRIGHTNESS_LEVELS[crtBrightnessIndex];
        } else if (settingsSelectionIndex == SETTINGS_ITEM_ASPECT) {
            aspectModeIndex = wrapIndex(aspectModeIndex + 1, ASPECT_MODE_LABELS.length);
        }
    }

    private void handleShopDirection(Direction direction) {
        if (direction == Direction.UP) {
            shopSelectionIndex = (shopSelectionIndex - 1 + SHOP_ITEM_COUNT) % SHOP_ITEM_COUNT;
        } else if (direction == Direction.DOWN) {
            shopSelectionIndex = (shopSelectionIndex + 1) % SHOP_ITEM_COUNT;
        }
    }

    private void purchaseSelectedShopItem() {
        ShopOption option = ShopOption.values()[shopSelectionIndex];
        if (coinCount < option.getCost()) {
            return;
        }

        boolean purchased = false;
        if (option == ShopOption.HEAL && playerHealthUnits < MAX_HEALTH_UNITS) {
            playerHealthUnits = Math.min(MAX_HEALTH_UNITS, playerHealthUnits + 2);
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

    private String getRenderQualityLabel() {
        return RENDER_QUALITY_LABELS[renderQualityIndex];
    }

    private String getCrtBleedLabel() {
        return crtBleedEnabled ? "ON" : "OFF";
    }

    private String getAspectLabel() {
        return ASPECT_MODE_LABELS[aspectModeIndex];
    }

    private void drawSettingsBrightness(Graphics2D g2d, int optionIndex, int baselineY) {
        boolean selected = settingsSelectionIndex == optionIndex;
        int centerX = GameConfig.WIDTH / 2;
        int barWidth = 260;
        int barHeight = 10;
        int barX = centerX - (barWidth / 2);
        int barY = baselineY + 8;

        g2d.setFont(BODY_FONT);
        if (selected) {
            drawGlowingCenteredString(g2d, "> CRT BRIGHTNESS <", centerX, baselineY, YELLOW, GLOW_CYAN);
        } else {
            g2d.setColor(WHITE);
            drawCenteredString(g2d, "CRT BRIGHTNESS", centerX, baselineY);
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
    }

    private void applyRenderQuality() {
        sceneBufferWidth = RENDER_QUALITY_WIDTHS[renderQualityIndex];
        sceneBufferHeight = RENDER_QUALITY_HEIGHTS[renderQualityIndex];
        sceneBuffer = null;
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

        double ratio = Math.max(0.0, Math.min(1.0, enemy.getHealthRatio()));
        int fillWidth = (int) Math.round((ENEMY_BAR_W - 4) * ratio);
        if (fillWidth > 0) {
            g2d.setColor(new Color(255, 92, 198, 78));
            g2d.fillRect(ENEMY_BAR_X + 2, enemyBarY + 2, fillWidth, ENEMY_BAR_H - 3);
        }

        int pendingDamage = Math.max(0, roundManager.getPendingDamage());
        int previewDamage = Math.min(enemy.getHealth(), pendingDamage);
        if (previewDamage > 0 && fillWidth > 0) {
            int previewHealth = Math.max(0, enemy.getHealth() - previewDamage);
            int previewWidth = (int) Math.round((ENEMY_BAR_W - 4) * (previewHealth / (double) enemy.getMaxHealth()));
            previewWidth = Math.max(0, Math.min(fillWidth, previewWidth));
            int previewSegmentWidth = fillWidth - previewWidth;
            if (previewSegmentWidth > 0) {
                g2d.setColor(new Color(251, 142, 255, 152));
                g2d.fillRect(
                        ENEMY_BAR_X + 2 + previewWidth,
                        enemyBarY + 2,
                        previewSegmentWidth,
                        ENEMY_BAR_H - 3
                );
            }
        }

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
        if (now < lastCoinGainUntilMs && lastCoinGain != 0) {
            double popProgress = 1.0 - ((lastCoinGainUntilMs - now) / 760.0);
            int yOffset = (int) Math.round(12 * popProgress);
            int alpha = (int) Math.round(255 * (1.0 - popProgress));
            alpha = Math.max(0, Math.min(255, alpha));
            String deltaLabel = (lastCoinGain > 0 ? "+" : "") + lastCoinGain + " COINS";
            g2d.setColor(new Color(255, 214, 112, alpha));
            drawCenteredString(g2d, deltaLabel, GameConfig.WIDTH / 2, enemyBarY - 34 - yOffset);
        }
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
                if (menuTransitionActive) {
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
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "go_to_menu");
        actionMap.put("go_to_menu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (screen == ScreenState.SHOP) {
                    closeShop();
                } else if (screen == ScreenState.SETTINGS) {
                    screen = ScreenState.MENU;
                } else if (screen != ScreenState.MENU && !menuTransitionActive) {
                    startMenuTransition();
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
                if (screen == ScreenState.MENU && !menuTransitionActive) {
                    handleMenuDirection(direction);
                    return;
                }
                if (screen == ScreenState.SETTINGS && !menuTransitionActive) {
                    handleSettingsDirection(direction);
                    return;
                }
                if (screen == ScreenState.SHOP && !menuTransitionActive) {
                    handleShopDirection(direction);
                    return;
                }
                if (screen == ScreenState.ENCOUNTER && !encounterIntroActive && !menuTransitionActive) {
                    AudioManager.playClickSfx();
                    backdropEffects.spawnInputRipple(
                            direction.ordinal(),
                            roundManager.getSequence(),
                            roundManager.getProgressIndex(),
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

    private void startRun() {
        roomNumber = 1;
        coinCount = 0;
        playerHealthUnits = MAX_HEALTH_UNITS;
        mistakeGuardCharges = 0L;
        nextEncounterTimeBonusMs = 0L;
        shopSelectionIndex = 0;
        lastCoinGain = 0;
        lastCoinGainUntilMs = 0L;
        clearMovementInput();
        clearTimerBarAnimation();
        menuTransitionActive = false;
        encounterTransitionActive = false;
        pendingEncounterIndex = -1;
        encounterIntroActive = false;
        roomTransitionActive = false;
        roomIntroActive = false;
        pendingRoomEntryDirection = null;
        runStartFadeInActive = true;
        runStartFadeInStartMs = System.currentTimeMillis();
        enemyKillEffects.clear();
        backdropEffects.clearHueSweeps();
        roundManager.configureEncounter(EnemyArchetype.NORMAL);
        roundManager.startGame(true);
        generateRoom();
        AudioManager.playSfx("enter_game.wav");
        screen = ScreenState.DUNGEON;
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
        return roomNumber > 1 && (roomNumber % 3 == 0 || random.nextDouble() < 0.18);
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

        if (moveUpHeld) {
            dy -= 1.0;
        }
        if (moveDownHeld) {
            dy += 1.0;
        }
        if (moveLeftHeld) {
            dx -= 1.0;
        }
        if (moveRightHeld) {
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
        backdropEffects.clearHueSweeps();
        EncounterEnemy enemy = roomEncounters.get(encounterIndex).getEnemy();
        roundManager.configureEncounter(enemy.getArchetype());
        AudioManager.playSfx("encounter_start.wav");
        pendingEncounterIndex = encounterIndex;
        encounterTransitionActive = true;
        encounterTransitionStartMs = System.currentTimeMillis();
    }

    private void openShop() {
        clearMovementInput();
        screen = ScreenState.SHOP;
        shopSelectionIndex = 0;
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
        AudioManager.playSfx("next_room.wav");
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
        RoundCompletion completion = roundManager.handleSymbolInput(symbol);
        if (completion == null) {
            boolean triggeredWrongInput = !wrongFlashBefore && roundManager.isWrongFlashActive();
            if (triggeredWrongInput) {
                if (mistakeGuardCharges > 0) {
                    mistakeGuardCharges--;
                } else {
                    applyPlayerDamage(1, false);
                }
            }
            return;
        }
        if (activeEncounterIndex < 0 || activeEncounterIndex >= roomEncounters.size()) {
            return;
        }

        backdropEffects.triggerHueSweepRipple(completion, roundManager.getTimeLeftMs(), game.model.TimerStyle.BACKDROP_HUE);

        EncounterNode currentNode = roomEncounters.get(activeEncounterIndex);
        int damage = (int) Math.round(
                Math.max(0, completion.getResolvedDamage()) * currentNode.getEnemy().getArchetype().getDamageMultiplier()
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
            spawnEnemyDefeatEffect(currentNode);
            AudioManager.playSfx("enemy_defeated.wav");
        } else if (currentNode.getEnemy().getArchetype().isTimeRecoveryEnabled()) {
            AudioManager.playSfx("bar_fill.wav");
        }

        if (enemyDefeated) {
            activeEncounterIndex = -1;
            clearMovementInput();
            clearTimerBarAnimation();
            screen = ScreenState.DUNGEON;
        }
    }

    private void handleEncounterTimeout() {
        applyPlayerDamage(2, true);
    }

    private void applyPlayerDamage(int amountUnits, boolean resetTimerOnSurvive) {
        if (screen != ScreenState.ENCOUNTER) {
            return;
        }
        if (amountUnits <= 0) {
            return;
        }

        playerHealthUnits = Math.max(0, playerHealthUnits - amountUnits);
        AudioManager.playSfx("heart_lost.wav");
        if (playerHealthUnits <= 0) {
            clearMovementInput();
            clearTimerBarAnimation();
            AudioManager.playSfx("player_death.wav");
            screen = ScreenState.LOST;
            return;
        }

        if (resetTimerOnSurvive) {
            roundManager.startGame(false);
            resetTimerBarAnimation();
        }
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
        if (direction == Direction.UP) {
            moveUpHeld = held;
        } else if (direction == Direction.DOWN) {
            moveDownHeld = held;
        } else if (direction == Direction.LEFT) {
            moveLeftHeld = held;
        } else {
            moveRightHeld = held;
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
        moveUpHeld = false;
        moveDownHeld = false;
        moveLeftHeld = false;
        moveRightHeld = false;
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

    private void resetTimerBarAnimation() {
        long timeLeft = roundManager.getTimeLeftMs();
        displayedTimerMs = timeLeft;
        displayedTimerDurationMs = Math.max(1L, roundManager.getRoundDurationMs());
    }

    private void clearTimerBarAnimation() {
        displayedTimerMs = -1L;
        displayedTimerDurationMs = 1L;
    }

    private void drawEncounterTimerBorder(Graphics2D g2d, int x, int y, int width, int height) {
        long timeLeft = displayedTimerMs >= 0L ? displayedTimerMs : roundManager.getTimeLeftMs();
        long duration = displayedTimerDurationMs > 0L ? displayedTimerDurationMs : roundManager.getRoundDurationMs();
        double progress = duration > 0 ? (double) timeLeft / duration : 0.0;
        progress = Math.max(0.0, Math.min(1.0, progress));

        double danger = 1.0 - progress;
        Color activeColor = lerpColor(TIMER_HIGH, TIMER_LOW, danger);
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
        arrowSprites.put(Direction.UP, loadImage("arrow_up.png"));
        arrowSprites.put(Direction.DOWN, loadImage("arrow_down.png"));
        arrowSprites.put(Direction.LEFT, loadImage("arrow_left.png"));
        arrowSprites.put(Direction.RIGHT, loadImage("arrow_right.png"));

        for (Direction direction : Direction.values()) {
            BufferedImage sprite = arrowSprites.get(direction);
            if (sprite != null) {
                arrowSpritesGreen.put(direction, tintSprite(sprite, GREEN));
            }
        }
    }

    private void loadHeartSprites() {
        fullHeartSprite = loadImage("full_heart.png");
        halfHeartSprite = loadImage("half_heart.png");
        emptyHeartSprite = loadImage("empty_heart.png");
    }

    private void loadTransitionSprites() {
        megamanTransitionSprite = loadImage("megaman.png");
    }

    private void drawHeartHud(Graphics2D g2d) {
        int availableWidth = GameConfig.WIDTH - (HEART_BG_MARGIN_X * 2) - ((MAX_HEARTS - 1) * HEART_GAP);
        int heartSize = Math.max(24, availableWidth / MAX_HEARTS);
        int startX = HEART_BG_MARGIN_X + Math.max(0, (availableWidth - (heartSize * MAX_HEARTS)) / 2);
        int y = HEART_BG_Y;
        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, HEART_BG_ALPHA));

        for (int i = 0; i < MAX_HEARTS; i++) {
            int unitsInSlot = playerHealthUnits - (i * 2);
            BufferedImage sprite;
            if (unitsInSlot >= 2) {
                sprite = fullHeartSprite;
            } else if (unitsInSlot == 1) {
                sprite = halfHeartSprite;
            } else {
                sprite = emptyHeartSprite;
            }

            int x = startX + (i * (heartSize + HEART_GAP));
            if (sprite != null) {
                g2d.drawImage(sprite, x, y, heartSize, heartSize, null);
            } else {
                drawFallbackHeart(g2d, x, y, unitsInSlot, heartSize);
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
    }

    private void drawFallbackHeart(Graphics2D g2d, int x, int y, int unitsInSlot, int heartSize) {
        Color fillColor = unitsInSlot >= 2 ? RED : (unitsInSlot == 1 ? new Color(255, 138, 188) : new Color(26, 28, 42));
        g2d.setColor(fillColor);
        g2d.fillRect(x + 2, y + 2, heartSize - 4, heartSize - 4);
        g2d.setColor(WHITE);
        g2d.drawRect(x, y, heartSize, heartSize);
    }

    private BufferedImage loadImage(String fileName) {
        BufferedImage fromClasspath = loadFromClasspath(fileName);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        return loadFromFiles(fileName);
    }

    private BufferedImage loadFromClasspath(String fileName) {
        URL url = getClass().getClassLoader().getResource("assets/" + fileName);
        if (url == null) {
            return null;
        }
        try {
            return ImageIO.read(url);
        } catch (IOException ignored) {
            return null;
        }
    }

    private BufferedImage loadFromFiles(String fileName) {
        File[] candidates = {
                new File("src/assets/" + fileName),
                new File("assets/" + fileName)
        };

        for (File file : candidates) {
            if (!file.isFile()) {
                continue;
            }
            try {
                return ImageIO.read(file);
            } catch (IOException ignored) {
                return null;
            }
        }
        return null;
    }

    private BufferedImage tintSprite(BufferedImage sprite, Color tint) {
        BufferedImage tinted = new BufferedImage(
                sprite.getWidth(),
                sprite.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g2d = tinted.createGraphics();
        g2d.drawImage(sprite, 0, 0, null);
        g2d.setComposite(AlphaComposite.SrcIn);
        g2d.setColor(tint);
        g2d.fillRect(0, 0, sprite.getWidth(), sprite.getHeight());
        g2d.dispose();
        return tinted;
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

    private void drawRoomTransition(Graphics2D g2d) {
        drawHorizontalShutterTransition(
                g2d,
                roomTransitionStartMs,
                ROOM_TRANSITION_MS,
                ROOM_TRANSITION_HOLD_MS,
                "NEXT ROOM",
                null
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
        drawTransitionIntro(g2d, roomIntroStartMs, ROOM_INTRO_MS, "NEXT ROOM", null);
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

        long totalDurationMs = Math.max(1L, transitionMs + holdMs);
        double progress = (System.currentTimeMillis() - startMs) / (double) totalDurationMs;
        progress = Math.max(0.0, Math.min(1.0, progress));

        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        Color accent = getEnemyAccentColor(encounterEnemy);
        String label = encounterEnemy.getArchetype().getLabel();
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

    private void startMenuTransition() {
        clearMovementInput();
        backdropEffects.clearHueSweeps();
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

    private void updateBackgroundMusic() {
        String targetFile = (screen == ScreenState.MENU || screen == ScreenState.SETTINGS)
                ? MENU_MUSIC_FILE
                : GAMEPLAY_MUSIC_FILE;
        if (targetFile.equals(activeMusicFile)) {
            return;
        }
        AudioManager.ensureBackgroundLoop(targetFile);
        activeMusicFile = targetFile;
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

    private void applyCrtOverlay(Graphics2D g2d, int width, int height) {
        if (crtOverlayBuffer == null || crtOverlayBuffer.getWidth() != width || crtOverlayBuffer.getHeight() != height) {
            crtOverlayBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D overlayG = crtOverlayBuffer.createGraphics();
            overlayG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            overlayG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            overlayG.setComposite(AlphaComposite.Src);
            overlayG.setColor(new Color(0, 0, 0, 0));
            overlayG.fillRect(0, 0, width, height);
            drawCrtGlow(overlayG, width, height);
            drawCrtMask(overlayG, width, height);
            if (crtScanlinesEnabled) {
                drawCrtScanlines(overlayG, width, height);
            }
            drawCrtVignette(overlayG, width, height);
            overlayG.dispose();
        }
        g2d.drawImage(crtOverlayBuffer, 0, 0, null);
    }

    private void drawCrtGlow(Graphics2D g2d, int width, int height) {
        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
        g2d.setColor(new Color(120, 220, 255, 255));
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(oldComposite);
    }

    private void drawCrtMask(Graphics2D g2d, int width, int height) {
        for (int x = 0; x < width; x += 3) {
            g2d.setColor(new Color(255, 70, 70, 16));
            g2d.drawLine(x, 0, x, height);
            if (x + 1 < width) {
                g2d.setColor(new Color(120, 255, 120, 14));
                g2d.drawLine(x + 1, 0, x + 1, height);
            }
            if (x + 2 < width) {
                g2d.setColor(new Color(110, 190, 255, 16));
                g2d.drawLine(x + 2, 0, x + 2, height);
            }
        }
    }

    private void drawCrtScanlines(Graphics2D g2d, int width, int height) {
        for (int y = 0; y < height; y += 3) {
            g2d.setColor(new Color(0, 6, 16, 78));
            g2d.fillRect(0, y, width, 1);
        }
        for (int y = 1; y < height; y += 6) {
            g2d.setColor(new Color(140, 220, 255, 18));
            g2d.drawLine(0, y, width, y);
        }
    }

    private void drawCrtVignette(Graphics2D g2d, int width, int height) {
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
        float radius = Math.max(width, height) * 0.72f;
        RadialGradientPaint vignette = new RadialGradientPaint(
                centerX,
                centerY,
                radius,
                new float[]{0.0f, 0.72f, 1.0f},
                new Color[]{
                        new Color(0, 0, 0, 0),
                        new Color(0, 0, 0, 35),
                        new Color(0, 0, 0, 130)
                }
        );
        g2d.setPaint(vignette);
        g2d.fillRect(0, 0, width, height);
        drawCrtCornerCurve(g2d, width, height);
    }

    private void drawCrtCornerCurve(Graphics2D g2d, int width, int height) {
        int arc = Math.max(36, Math.min(width, height) / 7);
        Area outer = new Area(new Rectangle(0, 0, width, height));
        Area inner = new Area(new RoundRectangle2D.Double(3, 3, width - 6.0, height - 6.0, arc, arc));
        outer.subtract(inner);
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fill(outer);

        g2d.setColor(new Color(170, 240, 255, 22));
        g2d.draw(new RoundRectangle2D.Double(2, 2, width - 5.0, height - 5.0, arc, arc));
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

    private Color lerpColor(Color from, Color to, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(from.getRed() + ((to.getRed() - from.getRed()) * clamped));
        int g = (int) Math.round(from.getGreen() + ((to.getGreen() - from.getGreen()) * clamped));
        int b = (int) Math.round(from.getBlue() + ((to.getBlue() - from.getBlue()) * clamped));
        int a = (int) Math.round(from.getAlpha() + ((to.getAlpha() - from.getAlpha()) * clamped));
        return new Color(r, g, b, a);
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
