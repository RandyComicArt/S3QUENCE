package game.config;

public final class GameConfig {
    public static final int WIDTH = 960;
    public static final int HEIGHT = 720;

    public static final int MIN_SEQUENCE_LENGTH = 4;
    public static final int MAX_SEQUENCE_LENGTH = 8;

    public static final long BASE_ROUND_TIME_MS = 1800L;
    public static final long TIME_PER_SYMBOL_MS = 700L;
    public static final long WRONG_FLASH_MS = 300L;
    public static final long SEQUENCE_COMPLETE_HOLD_MS = 220L;

    public static final int BOX_SIZE = 64;
    public static final int BOX_GAP = 12;

    public static final int ENEMY_BASE_HEALTH = 95;
    public static final int ENEMY_HEALTH_PER_ROOM = 8;
    public static final int ENEMY_HEALTH_VARIANCE = 25;

    private GameConfig() {
    }
}
