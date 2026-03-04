package game;

import game.config.GameConfig;
import game.logic.RoundManager;
import game.model.Direction;
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

    private final Timer timer;
    private final RoundManager roundManager = new RoundManager();
    private final EnumMap<Direction, BufferedImage> arrowSprites = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, BufferedImage> arrowSpritesGreen = new EnumMap<>(Direction.class);

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
    private boolean moveUpHeld;
    private boolean moveDownHeld;
    private boolean moveLeftHeld;
    private boolean moveRightHeld;
    private long lastTickNanos = System.nanoTime();

    private static class EncounterNode {
        int x;
        int y;
        boolean cleared;
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
        g2d.setColor(new Color(2, 10, 24));
        g2d.fillRect(ARENA_X + 2, ARENA_Y + 2, ARENA_W - 3, ARENA_H - 3);
        g2d.setColor(new Color(4, 18, 40));
        g2d.fillRect(ROOM_X + 2, ROOM_Y + 2, ROOM_W - 3, ROOM_H - 3);
        drawFrame(g2d, ARENA_X, ARENA_Y, ARENA_W, ARENA_H, 4, WHITE);
        drawFrame(g2d, ROOM_X, ROOM_Y, ROOM_W, ROOM_H, 2, WHITE);

        g2d.setFont(SMALL_FONT);
        g2d.setColor(TEXT_DIM);
        drawCenteredString(g2d, "DUNGEON ROOM " + roomNumber, GameConfig.WIDTH / 2, ARENA_Y + 28);

        for (EncounterNode node : roomEncounters) {
            if (node.cleared) {
                continue;
            }
            g2d.setColor(RED);
            g2d.fillRect(node.x, node.y, ENCOUNTER_SIZE, ENCOUNTER_SIZE);
            g2d.setColor(WHITE);
            g2d.drawRect(node.x, node.y, ENCOUNTER_SIZE, ENCOUNTER_SIZE);
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
        g2d.setColor(new Color(2, 10, 24));
        g2d.fillRect(ARENA_X + 2, ARENA_Y + 2, ARENA_W - 3, ARENA_H - 3);
        drawFrame(g2d, ARENA_X, ARENA_Y, ARENA_W, ARENA_H, 4, WHITE);
        g2d.setColor(WHITE);
        g2d.setFont(SMALL_FONT);
        drawGlowingCenteredString(g2d, "ENCOUNTER", GameConfig.WIDTH / 2, ARENA_Y + 34, WHITE, GLOW_CYAN);
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

        playerX = ROOM_X + 26;
        playerY = ROOM_Y + (ROOM_H / 2) - (PLAYER_SIZE / 2);

        int encounters = 1 + random.nextInt(3);
        int maxTries = 50;
        for (int i = 0; i < encounters; i++) {
            EncounterNode node = new EncounterNode();
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
                node.x = nx;
                node.y = ny;
                placed = true;
                break;
            }
            if (placed) {
                roomEncounters.add(node);
            }
        }

        if (roomEncounters.isEmpty()) {
            EncounterNode fallback = new EncounterNode();
            fallback.x = ROOM_X + ROOM_W / 2;
            fallback.y = ROOM_Y + ROOM_H / 2;
            roomEncounters.add(fallback);
        }
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
                if (node.cleared) {
                    continue;
                }
                Rectangle encounterRect = new Rectangle(node.x, node.y, ENCOUNTER_SIZE, ENCOUNTER_SIZE);
                if (playerRect.intersects(encounterRect)) {
                    startEncounter(i);
                    return;
                }
            }
        } else if (playerRect.intersects(getDoorRect())) {
            roomNumber++;
            generateRoom();
        }
    }

    private void startEncounter(int encounterIndex) {
        clearMovementInput();
        pendingEncounterIndex = encounterIndex;
        encounterTransitionActive = true;
        encounterTransitionStartMs = System.currentTimeMillis();
    }

    private void handleEncounterInput(int symbol) {
        int before = roundManager.getRoundsCleared();
        roundManager.handleSymbolInput(symbol);
        if (roundManager.getRoundsCleared() > before) {
            if (activeEncounterIndex >= 0 && activeEncounterIndex < roomEncounters.size()) {
                roomEncounters.get(activeEncounterIndex).cleared = true;
            }
            activeEncounterIndex = -1;
            clearMovementInput();
            screen = ScreenState.DUNGEON;
        }
    }

    private int countUnclearedEncounters() {
        int count = 0;
        for (EncounterNode node : roomEncounters) {
            if (!node.cleared) {
                count++;
            }
        }
        return count;
    }

    private boolean allEncountersCleared() {
        return countUnclearedEncounters() == 0;
    }

    private boolean intersectsAnyEncounter(Rectangle candidate) {
        for (EncounterNode node : roomEncounters) {
            Rectangle rect = new Rectangle(node.x, node.y, ENCOUNTER_SIZE, ENCOUNTER_SIZE);
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
        g2d.setColor(new Color(41, 152, 236, 90));
        g2d.drawRect(renderX - 3, renderY - 3, renderWidth + 5, renderHeight + 5);
        g2d.setColor(new Color(94, 245, 255, 140));
        g2d.drawRect(renderX - 2, renderY - 2, renderWidth + 3, renderHeight + 3);
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

        double t = System.currentTimeMillis() / 1000.0;
        int pulseA = 36 + (int) Math.round(22 * ((Math.sin(t * 1.7) + 1.0) / 2.0));
        g2d.setColor(new Color(56, 176, 255, pulseA));
        g2d.fillOval(GameConfig.WIDTH - 300, -140, 430, 430);
        g2d.fillOval(-180, GameConfig.HEIGHT - 270, 410, 410);

        g2d.setColor(new Color(63, 126, 205, 24));
        for (int y = 0; y < GameConfig.HEIGHT; y += 52) {
            g2d.drawLine(0, y, GameConfig.WIDTH, y);
        }
        for (int x = 0; x < GameConfig.WIDTH; x += 52) {
            g2d.drawLine(x, 0, x, GameConfig.HEIGHT);
        }
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
