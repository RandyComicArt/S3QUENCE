package game.config;

public final class GameConfig {
    public static final int WIDTH = 960;
    public static final int HEIGHT = 720;

    public static final int MIN_SEQUENCE_LENGTH = 4;
    public static final int MAX_SEQUENCE_LENGTH = 8;

    public static final long ENCOUNTER_START_TIME_MS = 5600L;
    public static final long ENCOUNTER_MAX_TIME_MS = 5600L;
    public static final long SHOP_TIMER_BONUS_MS = 1200L;
    public static final long TIME_RESTORE_PER_CORRECT_KEY_MS = 150L;
    public static final long TIME_RESTORE_BASE_MS = 170L;
    public static final long TIME_RESTORE_PER_SYMBOL_MS = 55L;
    public static final long TIME_RESTORE_SPEED_BONUS_MS = 130L;
    public static final long TIME_RESTORE_PRESSURE_BONUS_MS = 170L;
    public static final long TIME_RESTORE_MIN_MS = 110L;
    public static final long WRONG_FLASH_MS = 300L;
    public static final long SEQUENCE_COMPLETE_HOLD_MS = 220L;

    public static final int BOX_SIZE = 64;
    public static final int BOX_GAP = 12;

    public static final int ENEMY_BASE_HEALTH = 125;
    public static final int ENEMY_HEALTH_PER_ROOM = 11;
    public static final int ENEMY_HEALTH_VARIANCE = 32;
    public static final double DAMAGE_OUTPUT_SCALE = 1.12;

    private GameConfig() {
    }
}
