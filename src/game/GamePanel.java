package game;

import game.audio.AudioManager;
import game.config.GameConfig;
import game.logic.RoundCompletion;
import game.logic.RoundManager;
import game.model.Direction;
import game.model.EncounterEnemy;
import game.model.EncounterNode;
import game.model.ScreenState;

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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

public class GamePanel extends JPanel implements ActionListener {
    private static final Color BG = new Color(1, 5, 16);
    private static final Color BG_DEEP = new Color(0, 2, 10);
    private static final Color BG_MID = new Color(5, 18, 48);
    private static final Color WHITE = new Color(194, 236, 255);
    private static final Color TEXT_DIM = new Color(104, 156, 208);
    private static final Color YELLOW = new Color(94, 245, 255);
    private static final Color GREEN = new Color(74, 228, 255);
    private static final Color RED = new Color(255, 89, 177);
    private static final Color TILE_IDLE = new Color(10, 30, 58);
    private static final Color TILE_BG = new Color(4, 13, 30);
    private static final Color GLOW_CYAN = new Color(94, 245, 255);
    private static final Color TIMER_HIGH = new Color(98, 247, 255);
    private static final Color TIMER_LOW = new Color(74, 106, 255);
    private static final Color ARENA_GLASS = new Color(2, 10, 24, 102);
    private static final Color ROOM_GLASS = new Color(4, 18, 40, 90);

    private static final Font TITLE_FONT = new Font("Monospaced", Font.BOLD, 40);
    private static final Font HUD_FONT = new Font("Monospaced", Font.BOLD, 24);
    private static final Font BODY_FONT = new Font("Monospaced", Font.PLAIN, 20);
    private static final Font SMALL_FONT = new Font("Monospaced", Font.PLAIN, 16);
    private static final int HUD_X = 40;
    private static final int HUD_Y = 30;
    private static final int HUD_W = GameConfig.WIDTH - 80;
    private static final int HUD_H = 150;
    private static final int HUD_TIMER_X = HUD_X + 250;
    private static final int HUD_TIMER_W = 460;

    private static final int ARENA_X = 120;
    private static final int ARENA_Y = 220;
    private static final int ARENA_W = GameConfig.WIDTH - 240;
    private static final int ARENA_H = 420;
    private static final int ENEMY_BAR_X = ARENA_X + 150;
    private static final int ENEMY_BAR_Y = ARENA_Y + 54;
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
    private static final long ENCOUNTER_TRANSITION_MS = 720L;
    private static final long ENCOUNTER_TRANSITION_HOLD_MS = 180L;
    private static final long ENCOUNTER_INTRO_MS = 360L;
    private static final int ENCOUNTER_TEXT_HANDOFF_OFFSET = 120;
    private static final int BACKDROP_GRID_SPACING = 52;
    private static final int RIPPLE_MAX_COUNT = 16;

    private final Timer timer;
    private final RoundManager roundManager = new RoundManager();
    private final EnumMap<Direction, BufferedImage> arrowSprites = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, BufferedImage> arrowSpritesGreen = new EnumMap<>(Direction.class);

    private final Random random = new Random();
    private final List<EncounterNode> roomEncounters = new ArrayList<>();
    private final List<BackgroundRipple> backgroundRipples = new ArrayList<>();

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
    private boolean moveUpHeld;
    private boolean moveDownHeld;
    private boolean moveLeftHeld;
    private boolean moveRightHeld;
    private long lastTickNanos = System.nanoTime();
    private int lastHitDamage;
    private long lastHitUntilMs;

    private static class BackgroundRipple {
        int x;
        int y;
        int maxRadius;
        int bandWidth;
        double strength;
        double wavelength;
        double phase;
        long startMs;
        long durationMs;
    }

    public GamePanel() {
        setPreferredSize(new Dimension(GameConfig.WIDTH, GameConfig.HEIGHT));
        setBackground(BG);
        setFocusable(true);
        loadArrowSprites();
        setupMovementDispatcher();
        setupKeyBindings();
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
        double scale = Math.min(panelWidth / (double) gameWidth, panelHeight / (double) gameHeight);

        int renderWidth = (int) Math.round(gameWidth * scale);
        int renderHeight = (int) Math.round(gameHeight * scale);
        int renderX = (panelWidth - renderWidth) / 2;
        int renderY = (panelHeight - renderHeight) / 2;

        drawLetterboxFrame(g2d, panelWidth, panelHeight, renderX, renderY, renderWidth, renderHeight);

        Graphics2D gameG = (Graphics2D) g2d.create(renderX, renderY, renderWidth, renderHeight);
        gameG.scale(renderWidth / (double) gameWidth, renderHeight / (double) gameHeight);
        gameG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        gameG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        gameG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        gameG.setColor(BG);
        gameG.fillRect(0, 0, gameWidth, gameHeight);
        drawBackdrop(gameG);
        drawScanlines(gameG);

        if (screen == ScreenState.MENU) {
            drawMenu(gameG);
            gameG.dispose();
            g2d.dispose();
            return;
        }

        if (screen == ScreenState.DUNGEON) {
            drawDungeonHud(gameG);
            drawDungeon(gameG);
        } else {
            drawEncounterHud(gameG);
            drawArena(gameG);
            drawSequence(gameG);
        }

        if (screen == ScreenState.LOST) {
            drawLossOverlay(gameG);
        }
        if (encounterTransitionActive) {
            drawEncounterTransition(gameG);
        } else if (encounterIntroActive) {
            drawEncounterIntro(gameG);
        }

        gameG.dispose();
        g2d.dispose();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double deltaSeconds = (now - lastTickNanos) / 1_000_000_000.0;
        lastTickNanos = now;

        // Prevent giant movement jumps after focus loss or window stalls.
        deltaSeconds = Math.min(deltaSeconds, 0.05);

        if (screen == ScreenState.DUNGEON && !encounterTransitionActive) {
            updateDungeonMovement(deltaSeconds);
        }
        if (encounterTransitionActive) {
            long elapsedMs = System.currentTimeMillis() - encounterTransitionStartMs;
            if (elapsedMs >= ENCOUNTER_TRANSITION_MS + ENCOUNTER_TRANSITION_HOLD_MS) {
                encounterTransitionActive = false;
                activeEncounterIndex = pendingEncounterIndex;
                pendingEncounterIndex = -1;
                roundManager.startGame(false);
                screen = ScreenState.ENCOUNTER;
                encounterIntroActive = true;
                encounterIntroStartMs = System.currentTimeMillis();
            }
        }
        if (encounterIntroActive) {
            long introElapsedMs = System.currentTimeMillis() - encounterIntroStartMs;
            if (introElapsedMs >= ENCOUNTER_INTRO_MS) {
                encounterIntroActive = false;
            }
        }
        if (screen == ScreenState.ENCOUNTER && !encounterIntroActive && roundManager.hasTimedOut()) {
            clearMovementInput();
            screen = ScreenState.LOST;
        }
        updateBackgroundRipples();
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
        drawCenteredString(g2d, "Use Arrow Keys in Encounters", GameConfig.WIDTH / 2, boxY + 235);

        g2d.setFont(SMALL_FONT);
        g2d.setColor(YELLOW);
        drawCenteredString(g2d, "PRESS ENTER TO START", GameConfig.WIDTH / 2, boxY + boxH - 62);
        g2d.setColor(TEXT_DIM);
        drawCenteredString(g2d, "WASD / ARROWS MOVE  |  ESC MENU", GameConfig.WIDTH / 2, boxY + boxH - 30);
    }

    private void drawDungeonHud(Graphics2D g2d) {
        g2d.setColor(new Color(2, 14, 34, 220));
        g2d.fillRect(HUD_X + 2, HUD_Y + 2, HUD_W - 3, HUD_H - 3);
        drawFrame(g2d, HUD_X, HUD_Y, HUD_W, HUD_H, 3, WHITE);

        int left = countUnclearedEncounters();
        g2d.setColor(WHITE);
        g2d.setFont(BODY_FONT);
        drawCenteredString(g2d, "ENEMIES REMAINING: " + left, GameConfig.WIDTH / 2, HUD_Y + 62);
    }

    private void drawEncounterHud(Graphics2D g2d) {
        g2d.setColor(new Color(2, 14, 34, 220));
        g2d.fillRect(HUD_X + 2, HUD_Y + 2, HUD_W - 3, HUD_H - 3);
        drawFrame(g2d, HUD_X, HUD_Y, HUD_W, HUD_H, 3, WHITE);

        drawTimerBar(g2d, HUD_TIMER_X, HUD_Y + 56, HUD_TIMER_W, 36);
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
            if (node.isCleared()) {
                continue;
            }
            g2d.setColor(RED);
            g2d.fillRect(node.getX(), node.getY(), ENCOUNTER_SIZE, ENCOUNTER_SIZE);
            g2d.setColor(WHITE);
            g2d.drawRect(node.getX(), node.getY(), ENCOUNTER_SIZE, ENCOUNTER_SIZE);
        }

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
    }

    private void drawArena(Graphics2D g2d) {
        g2d.setColor(ARENA_GLASS);
        g2d.fillRect(ARENA_X + 2, ARENA_Y + 2, ARENA_W - 3, ARENA_H - 3);
        drawFrame(g2d, ARENA_X, ARENA_Y, ARENA_W, ARENA_H, 4, WHITE);
        g2d.setColor(WHITE);
        g2d.setFont(SMALL_FONT);
        drawGlowingCenteredString(g2d, "ENCOUNTER", GameConfig.WIDTH / 2, ARENA_Y + 34, WHITE, GLOW_CYAN);
        drawEncounterEnemyBar(g2d);
    }

    private void drawEncounterEnemyBar(Graphics2D g2d) {
        EncounterEnemy enemy = getActiveEncounterEnemy();
        if (enemy == null) {
            return;
        }

        g2d.setFont(SMALL_FONT);
        g2d.setColor(TEXT_DIM);
        drawCenteredString(g2d, "TARGET", GameConfig.WIDTH / 2, ENEMY_BAR_Y - 8);

        g2d.setColor(new Color(2, 12, 32));
        g2d.fillRect(ENEMY_BAR_X, ENEMY_BAR_Y, ENEMY_BAR_W, ENEMY_BAR_H);
        g2d.setColor(new Color(GLOW_CYAN.getRed(), GLOW_CYAN.getGreen(), GLOW_CYAN.getBlue(), 110));
        g2d.drawRect(ENEMY_BAR_X - 1, ENEMY_BAR_Y - 1, ENEMY_BAR_W + 1, ENEMY_BAR_H + 1);
        g2d.setColor(WHITE);
        g2d.drawRect(ENEMY_BAR_X, ENEMY_BAR_Y, ENEMY_BAR_W, ENEMY_BAR_H);

        double ratio = Math.max(0.0, Math.min(1.0, enemy.getHealthRatio()));
        int fillWidth = (int) Math.round((ENEMY_BAR_W - 4) * ratio);
        if (fillWidth > 0) {
            Color hpColor = lerpColor(RED, GREEN, ratio);
            g2d.setColor(hpColor);
            g2d.fillRect(ENEMY_BAR_X + 2, ENEMY_BAR_Y + 2, fillWidth, ENEMY_BAR_H - 3);
        }

        int pendingDamage = Math.max(0, roundManager.getPendingDamage());
        int previewDamage = Math.min(enemy.getHealth(), pendingDamage);
        if (previewDamage > 0 && fillWidth > 0) {
            int previewHealth = Math.max(0, enemy.getHealth() - previewDamage);
            int previewWidth = (int) Math.round((ENEMY_BAR_W - 4) * (previewHealth / (double) enemy.getMaxHealth()));
            previewWidth = Math.max(0, Math.min(fillWidth, previewWidth));
            int previewSegmentWidth = fillWidth - previewWidth;
            if (previewSegmentWidth > 0) {
                g2d.setColor(new Color(255, 128, 216, 185));
                g2d.fillRect(
                        ENEMY_BAR_X + 2 + previewWidth,
                        ENEMY_BAR_Y + 2,
                        previewSegmentWidth,
                        ENEMY_BAR_H - 3
                );
                g2d.setColor(new Color(255, 192, 234, 220));
                g2d.drawLine(
                        ENEMY_BAR_X + 2 + previewWidth,
                        ENEMY_BAR_Y + 2,
                        ENEMY_BAR_X + 2 + previewWidth,
                        ENEMY_BAR_Y + ENEMY_BAR_H - 2
                );
            }
        }

        g2d.setColor(WHITE);
        String hpText = enemy.getHealth() + " / " + enemy.getMaxHealth();
        drawCenteredString(g2d, hpText, GameConfig.WIDTH / 2, ENEMY_BAR_Y + ENEMY_BAR_H + 18);
        if (previewDamage > 0) {
            g2d.setColor(new Color(255, 162, 228));
            drawCenteredString(g2d, "POTENTIAL: -" + previewDamage, GameConfig.WIDTH / 2, ENEMY_BAR_Y + ENEMY_BAR_H + 36);
        }

        long now = System.currentTimeMillis();
        if (now < lastHitUntilMs && lastHitDamage > 0) {
            double popProgress = 1.0 - ((lastHitUntilMs - now) / 650.0);
            int yOffset = (int) Math.round(14 * popProgress);
            int alpha = (int) Math.round(255 * (1.0 - popProgress));
            alpha = Math.max(0, Math.min(255, alpha));
            g2d.setColor(new Color(255, 122, 200, alpha));
            drawCenteredString(g2d, "-" + lastHitDamage, GameConfig.WIDTH / 2, ENEMY_BAR_Y - 18 - yOffset);
        }
    }

    private void drawSequence(Graphics2D g2d) {
        List<Integer> sequence = roundManager.getSequence();
        int count = sequence.size();
        if (count == 0) {
            return;
        }

        int totalWidth = (count * GameConfig.BOX_SIZE) + ((count - 1) * GameConfig.BOX_GAP);
        int startX = ARENA_X + (ARENA_W - totalWidth) / 2;
        int y = ARENA_Y + (ARENA_H - GameConfig.BOX_SIZE) / 2;
        boolean wrongFlash = roundManager.isWrongFlashActive();
        int progressIndex = roundManager.getProgressIndex();

        for (int i = 0; i < count; i++) {
            int x = startX + i * (GameConfig.BOX_SIZE + GameConfig.BOX_GAP);
            boolean isCorrect = !wrongFlash && i < progressIndex;
            Color fillColor = TILE_IDLE;
            if (wrongFlash) {
                fillColor = RED;
            } else if (isCorrect) {
                fillColor = new Color(12, 64, 108);
            }

            g2d.setColor(TILE_BG);
            g2d.fillRect(x, y, GameConfig.BOX_SIZE, GameConfig.BOX_SIZE);
            g2d.setColor(fillColor);
            g2d.fillRect(x + 4, y + 4, GameConfig.BOX_SIZE - 8, GameConfig.BOX_SIZE - 8);
            Color borderColor = wrongFlash ? RED : (isCorrect ? GREEN : WHITE);
            g2d.setColor(borderColor);
            g2d.drawRect(x, y, GameConfig.BOX_SIZE, GameConfig.BOX_SIZE);

            Direction direction = Direction.values()[sequence.get(i)];
            Color arrowColor = isCorrect ? GREEN : WHITE;
            BufferedImage sprite = isCorrect ? arrowSpritesGreen.get(direction) : arrowSprites.get(direction);
            if (sprite != null) {
                drawArrowSprite(g2d, sprite, x, y, GameConfig.BOX_SIZE);
            } else {
                drawArrow(g2d, direction, x, y, GameConfig.BOX_SIZE, arrowColor);
            }
        }
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
                if (screen == ScreenState.MENU) {
                    startRun();
                } else if (screen == ScreenState.LOST) {
                    startRun();
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "go_to_menu");
        actionMap.put("go_to_menu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearMovementInput();
                encounterTransitionActive = false;
                pendingEncounterIndex = -1;
                encounterIntroActive = false;
                screen = ScreenState.MENU;
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
                if (screen == ScreenState.ENCOUNTER && !encounterIntroActive) {
                    AudioManager.playClickSfx();
                    spawnInputRipple(direction.ordinal());
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
                    if (screen == ScreenState.DUNGEON && !encounterTransitionActive) {
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
        clearMovementInput();
        encounterTransitionActive = false;
        pendingEncounterIndex = -1;
        encounterIntroActive = false;
        roundManager.startGame(true);
        generateRoom();
        screen = ScreenState.DUNGEON;
    }

    private void generateRoom() {
        roomEncounters.clear();
        activeEncounterIndex = -1;
        pendingEncounterIndex = -1;
        encounterTransitionActive = false;
        encounterIntroActive = false;
        lastHitDamage = 0;
        lastHitUntilMs = 0;

        playerX = ROOM_X + 26;
        playerY = ROOM_Y + (ROOM_H / 2) - (PLAYER_SIZE / 2);

        int encounters = 1 + random.nextInt(3);
        int maxTries = 50;
        for (int i = 0; i < encounters; i++) {
            EncounterNode node = new EncounterNode(generateEnemyHealthForRoom());
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

        if (roomEncounters.isEmpty()) {
            EncounterNode fallback = new EncounterNode(generateEnemyHealthForRoom());
            fallback.setX(ROOM_X + ROOM_W / 2);
            fallback.setY(ROOM_Y + ROOM_H / 2);
            roomEncounters.add(fallback);
        }
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
                if (node.isCleared()) {
                    continue;
                }
                Rectangle encounterRect = new Rectangle(node.getX(), node.getY(), ENCOUNTER_SIZE, ENCOUNTER_SIZE);
                if (playerRect.intersects(encounterRect)) {
                    startEncounter(i);
                    return;
                }
            }
        } else if (playerRect.intersects(getDoorRect())) {
            roomNumber++;
            AudioManager.playSfx("next_room.wav");
            generateRoom();
        }
    }

    private void startEncounter(int encounterIndex) {
        clearMovementInput();
        lastHitDamage = 0;
        lastHitUntilMs = 0;
        AudioManager.playSfx("encounter_start.wav");
        pendingEncounterIndex = encounterIndex;
        encounterTransitionActive = true;
        encounterTransitionStartMs = System.currentTimeMillis();
    }

    private void handleEncounterInput(int symbol) {
        RoundCompletion completion = roundManager.handleSymbolInput(symbol);
        if (completion == null || activeEncounterIndex < 0 || activeEncounterIndex >= roomEncounters.size()) {
            return;
        }

        EncounterNode currentNode = roomEncounters.get(activeEncounterIndex);
        int damage = Math.max(0, completion.getResolvedDamage());
        currentNode.getEnemy().applyDamage(damage);
        if (damage > 0) {
            lastHitDamage = damage;
            lastHitUntilMs = System.currentTimeMillis() + 650L;
        } else {
            lastHitDamage = 0;
            lastHitUntilMs = 0;
        }

        boolean enemyDefeated = currentNode.isCleared();
        if (enemyDefeated) {
            AudioManager.playSfx("enemy_defeated.wav");
        } else {
            AudioManager.playSfx("sequence_done.wav");
        }

        if (enemyDefeated) {
            activeEncounterIndex = -1;
            clearMovementInput();
            screen = ScreenState.DUNGEON;
        }
    }

    private int countUnclearedEncounters() {
        int count = 0;
        for (EncounterNode node : roomEncounters) {
            if (!node.isCleared()) {
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
        int x = ROOM_X + ROOM_W - DOOR_W;
        int y = ROOM_Y + (ROOM_H - DOOR_H) / 2;
        return new Rectangle(x, y, DOOR_W, DOOR_H);
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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

    private void drawTimerBar(Graphics2D g2d, int x, int y, int width, int height) {
        long timeLeft = roundManager.getTimeLeftMs();
        long duration = roundManager.getRoundDurationMs();
        double progress = duration > 0 ? (double) timeLeft / duration : 0.0;
        progress = Math.max(0.0, Math.min(1.0, progress));

        double danger = 1.0 - progress;
        Color fillColor = lerpColor(TIMER_HIGH, TIMER_LOW, danger);
        if (progress < 0.3) {
            double pulse = 0.5 + (0.5 * Math.sin(System.currentTimeMillis() / 80.0));
            fillColor = lerpColor(fillColor, WHITE, pulse * 0.35);
        }

        g2d.setColor(new Color(2, 12, 32));
        g2d.fillRect(x, y, width, height);
        int glowAlpha = 60 + (int) Math.round(70 * progress);
        g2d.setColor(new Color(GLOW_CYAN.getRed(), GLOW_CYAN.getGreen(), GLOW_CYAN.getBlue(), glowAlpha));
        g2d.drawRect(x - 1, y - 1, width + 1, height + 1);
        g2d.setColor(WHITE);
        g2d.drawRect(x, y, width, height);

        int fillWidth = (int) Math.round((width - 4) * progress);
        if (fillWidth > 0) {
            g2d.setColor(fillColor);
            g2d.fillRect(x + 2, y + 2, fillWidth, height - 3);
            int sheenHeight = Math.max(1, height / 4);
            Color sheen = lerpColor(fillColor, WHITE, 0.5);
            g2d.setColor(new Color(sheen.getRed(), sheen.getGreen(), sheen.getBlue(), 120));
            g2d.fillRect(x + 2, y + 2, fillWidth, sheenHeight);
        }

        g2d.setColor(YELLOW);
        g2d.setFont(SMALL_FONT);
        String label = String.format("%.1fs", timeLeft / 1000.0);
        g2d.drawString(label, x + width - 72, y - 8);
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
        long elapsedMs = System.currentTimeMillis() - encounterTransitionStartMs;
        double progress = elapsedMs / (double) ENCOUNTER_TRANSITION_MS;
        progress = Math.max(0.0, Math.min(1.0, progress));
        double totalProgress = elapsedMs / (double) (ENCOUNTER_TRANSITION_MS + ENCOUNTER_TRANSITION_HOLD_MS);
        totalProgress = Math.max(0.0, Math.min(1.0, totalProgress));

        // Fast at start, then slows as shutters approach the encounter banner.
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
        // Keep the center gap opaque during transition so the dungeon scene never bleeds through.
        int centerBandY = Math.max(0, topLineY + 1);
        int centerBandHeight = Math.max(0, bottomLineY - topLineY - 1);
        if (centerBandHeight > 0) {
            g2d.fillRect(0, centerBandY, GameConfig.WIDTH, centerBandHeight);
        }

        g2d.setColor(WHITE);
        g2d.drawLine(0, topLineY, GameConfig.WIDTH, topLineY);
        g2d.drawLine(0, bottomLineY, GameConfig.WIDTH, bottomLineY);

        String text = "ENCOUNTER";
        int textWidth = metrics.stringWidth(text);
        int centerX = GameConfig.WIDTH / 2;
        int startCenterX = GameConfig.WIDTH + (textWidth / 2) + 40;
        int handoffCenterX = centerX - ENCOUNTER_TEXT_HANDOFF_OFFSET;

        int textCenterX;
        int textAlpha;
        if (totalProgress < 0.78) {
            double enterProgress = Math.max(0.0, Math.min(1.0, (totalProgress - 0.12) / 0.66));
            double easeOut = 1.0 - Math.pow(1.0 - enterProgress, 3.0);
            // Keep residual velocity near the center by mixing linear movement.
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
        double progress = (System.currentTimeMillis() - encounterIntroStartMs) / (double) ENCOUNTER_INTRO_MS;
        progress = Math.max(0.0, Math.min(1.0, progress));

        int overlayAlpha = (int) Math.round(255 * (1.0 - progress));
        g2d.setColor(new Color(0, 0, 0, overlayAlpha));
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        g2d.setFont(HUD_FONT);
        FontMetrics metrics = g2d.getFontMetrics();
        String text = "ENCOUNTER";
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

    private void drawScanlines(Graphics2D g2d) {
        g2d.setColor(new Color(112, 210, 255, 16));
        for (int y = 0; y < GameConfig.HEIGHT; y += 4) {
            g2d.drawLine(0, y, GameConfig.WIDTH, y);
        }
    }

    private void drawSoul(Graphics2D g2d, int x, int y, int size, Color color) {
        int half = size / 2;
        int[] xs = {x + half, x + size, x + half, x};
        int[] ys = {y, y + half, y + size, y + half};
        g2d.setColor(color);
        g2d.fillPolygon(xs, ys, 4);
    }

    private void drawBackdrop(Graphics2D g2d) {
        GradientPaint gradient = new GradientPaint(0, 0, BG_MID, 0, GameConfig.HEIGHT, BG_DEEP);
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        long nowMs = System.currentTimeMillis();
        double t = nowMs / 1000.0;
        drawFlowField(g2d, t);
        int pulseA = 36 + (int) Math.round(22 * ((Math.sin(t * 1.7) + 1.0) / 2.0));
        g2d.setColor(new Color(56, 176, 255, pulseA));
        g2d.fillOval(GameConfig.WIDTH - 300, -140, 430, 430);
        g2d.fillOval(-180, GameConfig.HEIGHT - 270, 410, 410);

        drawWarpedGrid(g2d, nowMs);
    }

    private void drawFlowField(Graphics2D g2d, double t) {
        for (int i = 0; i < 4; i++) {
            int amplitude = 20 + (i * 8);
            int yBase = 140 + (i * 120);
            int alpha = 24 - (i * 4);
            g2d.setColor(new Color(80, 190, 255, Math.max(8, alpha)));
            int previousX = 0;
            int previousY = yBase + (int) Math.round(Math.sin((t * 1.2) + (i * 0.7)) * amplitude);
            for (int x = 24; x <= GameConfig.WIDTH; x += 24) {
                double wave = Math.sin((x * 0.012) + (t * (1.3 + (i * 0.18))) + (i * 0.5));
                int y = yBase + (int) Math.round(wave * amplitude);
                g2d.drawLine(previousX, previousY, x, y);
                previousX = x;
                previousY = y;
            }
        }
    }

    private void drawWarpedGrid(Graphics2D g2d, long nowMs) {
        g2d.setColor(new Color(63, 126, 205, 28));
        double[] warp = new double[2];
        int step = 10;

        for (int y = 0; y <= GameConfig.HEIGHT; y += BACKDROP_GRID_SPACING) {
            int prevX = 0;
            calculateRippleWarp(0, y, nowMs, warp);
            int prevY = (int) Math.round(y + warp[1]);
            for (int x = step; x <= GameConfig.WIDTH; x += step) {
                calculateRippleWarp(x, y, nowMs, warp);
                int warpedX = (int) Math.round(x + warp[0]);
                int warpedY = (int) Math.round(y + warp[1]);
                g2d.drawLine(prevX, prevY, warpedX, warpedY);
                prevX = warpedX;
                prevY = warpedY;
            }
        }

        for (int x = 0; x <= GameConfig.WIDTH; x += BACKDROP_GRID_SPACING) {
            int prevY = 0;
            calculateRippleWarp(x, 0, nowMs, warp);
            int prevX = (int) Math.round(x + warp[0]);
            for (int y = step; y <= GameConfig.HEIGHT; y += step) {
                calculateRippleWarp(x, y, nowMs, warp);
                int warpedX = (int) Math.round(x + warp[0]);
                int warpedY = (int) Math.round(y + warp[1]);
                g2d.drawLine(prevX, prevY, warpedX, warpedY);
                prevX = warpedX;
                prevY = warpedY;
            }
        }
    }

    private void calculateRippleWarp(double x, double y, long nowMs, double[] outWarp) {
        double warpX = 0.0;
        double warpY = 0.0;

        for (BackgroundRipple ripple : backgroundRipples) {
            long elapsedMs = nowMs - ripple.startMs;
            if (elapsedMs < 0 || elapsedMs > ripple.durationMs) {
                continue;
            }

            double progress = elapsedMs / (double) ripple.durationMs;
            double life = 1.0 - progress;
            double dx = x - ripple.x;
            double dy = y - ripple.y;
            double distance = Math.sqrt((dx * dx) + (dy * dy));
            if (distance < 0.0001) {
                continue;
            }

            double radius = 14.0 + (ripple.maxRadius * progress);
            double bandDistance = Math.abs(distance - radius);
            if (bandDistance > ripple.bandWidth * 1.15) {
                continue;
            }

            double bandFalloff = 1.0 - (bandDistance / (ripple.bandWidth * 1.15));
            bandFalloff = Math.pow(Math.max(0.0, bandFalloff), 1.35);
            double phase = (distance / ripple.wavelength) - (progress * 15.8) + ripple.phase;
            double wave = Math.sin(phase);
            double swirl = Math.cos((phase * 0.68) + 0.6);

            double nx = dx / distance;
            double ny = dy / distance;
            double amplitude = ripple.strength * (0.34 + (0.66 * life)) * bandFalloff;
            warpX += (nx * wave * amplitude) + (-ny * swirl * amplitude * 0.55);
            warpY += (ny * wave * amplitude) + (nx * swirl * amplitude * 0.55);
        }

        double magnitude = Math.sqrt((warpX * warpX) + (warpY * warpY));
        if (magnitude > 44.0) {
            double scale = 44.0 / magnitude;
            warpX *= scale;
            warpY *= scale;
        }

        outWarp[0] = warpX;
        outWarp[1] = warpY;
    }

    private void spawnInputRipple(int symbol) {
        List<Integer> sequence = roundManager.getSequence();
        if (sequence.isEmpty()) {
            return;
        }

        int count = sequence.size();
        int sequenceIndex = findRippleSequenceIndex(sequence, roundManager.getProgressIndex(), symbol);
        if (sequenceIndex < 0) {
            return;
        }

        int totalWidth = (count * GameConfig.BOX_SIZE) + ((count - 1) * GameConfig.BOX_GAP);
        int startX = ARENA_X + (ARENA_W - totalWidth) / 2;
        int spawnX = startX + (sequenceIndex * (GameConfig.BOX_SIZE + GameConfig.BOX_GAP)) + (GameConfig.BOX_SIZE / 2);
        int spawnY = ARENA_Y + (ARENA_H / 2);
        long nowMs = System.currentTimeMillis();

        BackgroundRipple ripple = new BackgroundRipple();
        ripple.x = spawnX;
        ripple.y = spawnY;
        ripple.maxRadius = 300 + random.nextInt(211);
        ripple.bandWidth = 34 + random.nextInt(43);
        ripple.strength = 15.0 + (random.nextDouble() * 12.0);
        ripple.wavelength = 22.0 + (random.nextDouble() * 20.0);
        ripple.phase = random.nextDouble() * Math.PI * 2.0;
        ripple.startMs = nowMs;
        ripple.durationMs = 760L + random.nextInt(501);
        backgroundRipples.add(ripple);

        if (backgroundRipples.size() > RIPPLE_MAX_COUNT) {
            backgroundRipples.remove(0);
        }
    }

    private int findRippleSequenceIndex(List<Integer> sequence, int progressIndex, int symbol) {
        int size = sequence.size();
        if (size == 0) {
            return -1;
        }

        int clampedProgress = Math.max(0, Math.min(progressIndex, size - 1));
        if (sequence.get(clampedProgress) == symbol) {
            return clampedProgress;
        }
        for (int i = clampedProgress + 1; i < size; i++) {
            if (sequence.get(i) == symbol) {
                return i;
            }
        }
        for (int i = clampedProgress - 1; i >= 0; i--) {
            if (sequence.get(i) == symbol) {
                return i;
            }
        }
        return clampedProgress;
    }

    private void updateBackgroundRipples() {
        long nowMs = System.currentTimeMillis();
        backgroundRipples.removeIf(ripple -> nowMs - ripple.startMs > ripple.durationMs);
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

    private Color lerpColor(Color from, Color to, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(from.getRed() + ((to.getRed() - from.getRed()) * clamped));
        int g = (int) Math.round(from.getGreen() + ((to.getGreen() - from.getGreen()) * clamped));
        int b = (int) Math.round(from.getBlue() + ((to.getBlue() - from.getBlue()) * clamped));
        int a = (int) Math.round(from.getAlpha() + ((to.getAlpha() - from.getAlpha()) * clamped));
        return new Color(r, g, b, a);
    }
}
