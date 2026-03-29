package game.config;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.RescaleOp;

/**
 * Visual layout, timing, and tuning constants for {@link game.GamePanel}.
 */
public final class GamePanelConstants {
    private GamePanelConstants() {
    }

    public static final Color BG = new Color(1, 5, 16);
    public static final Color WHITE = new Color(194, 236, 255);
    public static final Color TEXT_DIM = new Color(104, 156, 208);
    public static final Color YELLOW = new Color(94, 245, 255);
    public static final Color GREEN = new Color(74, 228, 255);
    public static final Color RED = new Color(255, 89, 177);
    public static final Color GLOW_CYAN = new Color(94, 245, 255);
    public static final Color TIMER_HIGH = new Color(98, 247, 255);
    public static final Color TIMER_LOW = new Color(74, 106, 255);
    public static final Color ARENA_GLASS = new Color(2, 10, 24, 102);
    public static final Color ROOM_GLASS = new Color(4, 18, 40, 90);
    public static final String MENU_MUSIC_FILE = "main_menu.wav";
    public static final String DUNGEON_MUSIC_FILE = "Loop_drum.wav";
    public static final String[] ENCOUNTER_MUSIC_FILES = {"guitar_loop.wav", "guitar_loop2.wav", "guitar_loop3.wav", "guitar_loop4.wav"};
    public static final String[] ECHO_MUSIC_FILES = {"guitar_loop4.wav", "echo_song2.wav"};
    public static final String SHOP_MUSIC_FILE = "shop_loop.wav";

    public static final Font TITLE_FONT = new Font("Monospaced", Font.BOLD, 40);
    public static final Font HUD_FONT = new Font("Monospaced", Font.BOLD, 24);
    public static final Font BODY_FONT = new Font("Monospaced", Font.PLAIN, 20);
    public static final Font SMALL_FONT = new Font("Monospaced", Font.PLAIN, 16);
    public static final Font TRANSITION_ENEMY_FONT = new Font("Monospaced", Font.BOLD, 36);

    public static final int ARENA_X = 120;
    public static final int ARENA_Y = 170;
    public static final int ARENA_W = GameConfig.WIDTH - 240;
    public static final int ARENA_H = 420;
    public static final int ENCOUNTER_ARENA_Y = 170;
    public static final int ENEMY_BAR_W = ARENA_W - 180;
    public static final int ENEMY_BAR_X = ARENA_X + ((ARENA_W - ENEMY_BAR_W) / 2);
    public static final int ENEMY_BAR_H = 50;

    public static final int ROOM_X = ARENA_X + 35;
    public static final int ROOM_Y = ARENA_Y + 40;
    public static final int ROOM_W = ARENA_W - 70;
    public static final int ROOM_H = ARENA_H - 65;
    public static final int DOOR_W = 16;
    public static final int DOOR_H = 88;
    public static final int PLAYER_SIZE = 18;
    public static final double PLAYER_SPEED_PER_SECOND = 280.0;
    public static final int ENCOUNTER_SIZE = 18;
    public static final long ENCOUNTER_TRANSITION_MS = 1280L;
    public static final long ENCOUNTER_TRANSITION_HOLD_MS = 360L;
    public static final long ENCOUNTER_INTRO_MS = 360L;
    public static final long ROOM_TRANSITION_MS = 900L;
    public static final long ROOM_TRANSITION_HOLD_MS = 180L;
    public static final long ROOM_INTRO_MS = 360L;
    public static final long RUN_START_FADE_IN_MS = 1000L;
    public static final long MENU_TRANSITION_MS = 520L;
    public static final long MENU_TRANSITION_SWITCH_MS = MENU_TRANSITION_MS / 2;
    public static final long OPENING_TEXT_SEQUENCE_MS = 2200L;
    public static final long OPENING_STATIC_SEQUENCE_FALLBACK_MS = 520L;
    public static final long OPENING_STATIC_SOUND_FADE_LEAD_MS = 320L;
    public static final long OPENING_STATIC_SOUND_FADE_TAIL_MS = 220L;
    public static final long OPENING_FADE_IN_MS = 260L;
    public static final long OPENING_FADE_OUT_MS = 520L;
    public static final long START_RUN_TRANSITION_MS = 1250L;
    public static final long START_RUN_TRANSITION_SWITCH_MS = 420L;
    public static final long START_RUN_MUSIC_DELAY_MS = 320L;
    public static final int ENCOUNTER_TEXT_HANDOFF_OFFSET = 120;
    public static final double TIMER_REFILL_ANIM_PER_SECOND = 3600.0;
    public static final int MENU_ITEM_START = 0;
    public static final int MENU_ITEM_TEST_ENEMY = 1;
    public static final int MENU_ITEM_TEST_ITEM = 2;
    public static final int MENU_ITEM_SETTINGS = 3;
    public static final int MENU_ITEM_COUNT = 4;
    public static final int SETTINGS_TAB_VIDEO = 0;
    public static final int SETTINGS_TAB_AUDIO = 1;
    public static final int SETTINGS_ITEM_TAB = 0;
    public static final int SETTINGS_VIDEO_RENDER_QUALITY = 1;
    public static final int SETTINGS_VIDEO_CRT_BRIGHTNESS = 2;
    public static final int SETTINGS_VIDEO_ASPECT = 3;
    public static final int SETTINGS_VIDEO_BACK = 4;
    public static final int SETTINGS_VIDEO_ITEM_COUNT = 5;
    public static final int SETTINGS_AUDIO_MASTER = 1;
    public static final int SETTINGS_AUDIO_MUSIC = 2;
    public static final int SETTINGS_AUDIO_SFX = 3;
    public static final int SETTINGS_AUDIO_BACK = 4;
    public static final int SETTINGS_AUDIO_ITEM_COUNT = 5;
    public static final int SHOP_ITEM_COUNT = 3;
    public static final int MAX_HEARTS = 3;
    public static final int HEART_GAP = 22;
    public static final int HEART_BG_MARGIN_X = 20;
    public static final int HEART_BG_Y = 24;
    public static final float HEART_BG_ALPHA = 0.28f;
    public static final float HEART_DAMAGE_FLASH_ALPHA = 0.7f;
    public static final float HEART_DAMAGE_PENDING_ALPHA = 0.5f;
    public static final long HEART_DAMAGE_FLASH_MS = 130L;
    public static final double HEART_DAMAGE_SLIDE_PER_SECOND = 2.2;
    public static final long TIMEOUT_RESET_RECOVERY_BUFFER_MS = 140L;
    public static final long TIMEOUT_TIMER_REFILL_DURATION_MS = 650L;
    public static final int ITEM_CHARGE_BAR_WIDTH = 76;
    public static final int ITEM_CHARGE_BAR_HEIGHT = 4;
    public static final int ITEM_INDICATOR_SIZE = 68;
    public static final int ITEM_INDICATOR_GAP = 20;
    public static final int INITIAL_SURGE_FRAME_DURATION_MS = 90;
    public static final double INITIAL_SURGE_ACTIVE_THRESHOLD = 0.50;
    public static final double INITIAL_SURGE_DAMAGE_MULTIPLIER = 1.20;
    public static final int SEQUENCE_SYMBOL_SIZE = 76;
    public static final int SEQUENCE_SYMBOL_GAP = 24;
    public static final long SEQUENCE_PUNCH_IDLE_RESET_MS = 240L;
    public static final int SEQUENCE_PUNCH_SIZE = SEQUENCE_SYMBOL_SIZE + 20;
    public static final float SEQUENCE_PUNCH_ALPHA = 0.75f;
    public static final int SEQUENCE_PUNCH_OFFSET_Y = 220;
    public static final float SEQUENCE_PUNCH_SCALE = 0.7f;
    public static final int[] SEQUENCE_PUNCH_PATTERN = {1, 2, 1, 2};
    public static final String[] FINISHER_SFX_FILES = {"finisher1.wav", "finisher2.wav", "finisher3.wav", "finisher4.wav", "finisher5.wav"};
    public static final float FINISHER_SFX_GAIN_DB = -3.0f;
    public static final float MENU_NAV_RUMBLE_STRENGTH = 0.14f;
    public static final int MENU_NAV_RUMBLE_MS = 35;
    public static final float START_CONFIRM_RUMBLE_STRENGTH = 0.32f;
    public static final int START_CONFIRM_RUMBLE_MS = 75;
    public static final float ROOM_ENTRY_RUMBLE_STRENGTH = 0.28f;
    public static final int ROOM_ENTRY_RUMBLE_MS = 70;
    public static final float ENEMY_TOUCH_RUMBLE_STRENGTH = 0.38f;
    public static final int ENEMY_TOUCH_RUMBLE_MS = 80;
    public static final float KEY_SUCCESS_RUMBLE_STRENGTH = 0.12f;
    public static final int KEY_SUCCESS_RUMBLE_MS = 28;
    public static final float KEY_FAIL_RUMBLE_STRENGTH = 0.34f;
    public static final int KEY_FAIL_RUMBLE_MS = 95;
    public static final float DAMAGE_RUMBLE_STRENGTH = 0.62f;
    public static final int DAMAGE_RUMBLE_MS = 130;
    public static final float SEQUENCE_COMPLETE_RUMBLE_STRENGTH = 0.62f;
    public static final int SEQUENCE_COMPLETE_RUMBLE_MS = 130;
    public static final float[] FINISHER_SFX_GAIN_OFFSETS_DB = {0.0f, 5.0f, 0.0f, 0.0f, 5.0f};
    public static final int[] RENDER_QUALITY_WIDTHS = {480, 560, 640};
    public static final int[] RENDER_QUALITY_HEIGHTS = {360, 420, 480};
    public static final String[] RENDER_QUALITY_LABELS = {"PERFORMANCE", "BALANCED", "CLARITY"};
    public static final String[] ASPECT_MODE_LABELS = {"ORIGINAL", "FILL"};
    public static final float[] CRT_BRIGHTNESS_LEVELS = {
            0.50f, 0.6786f, 0.8571f, 1.0357f, 1.2143f,
            1.3929f, 1.5714f, 1.7500f, 1.9286f, 2.1071f,
            2.2857f, 2.4643f, 2.6429f, 2.8214f, 3.00f
    };
    public static final float[] VOLUME_LEVELS = {
            0.0f, 0.1f, 0.2f, 0.3f, 0.4f,
            0.5f, 0.6f, 0.7f, 0.8f, 0.9f,
            1.0f
    };
    public static final double CRT_WARP_STRENGTH = 0.085;
    public static final double CRT_VERTICAL_CURVE_STRENGTH = 0.04;
    public static final int CRT_WARP_STRIP_PX = 2;
    public static final boolean DEFAULT_CRT_COLOR_BLEED = true;
    public static final int DEFAULT_RENDER_QUALITY_INDEX = 0;
    public static final int DEFAULT_BRIGHTNESS_INDEX = 9;
    public static final int DEFAULT_MASTER_VOLUME_INDEX = 10;
    public static final int DEFAULT_MUSIC_VOLUME_INDEX = 10;
    public static final int DEFAULT_SFX_VOLUME_INDEX = 10;
    public static final int CRT_BLEED_OFFSET_X = 2;
    public static final int CRT_BLEED_OFFSET_Y = 1;
    public static final float CRT_BLEED_INTENSITY = 0.32f;
    public static final RescaleOp CRT_BLEED_RED_OP = new RescaleOp(
            new float[]{1f, 0f, 0f, 1f},
            new float[]{0f, 0f, 0f, 0f},
            null
    );
    public static final RescaleOp CRT_BLEED_GREEN_OP = new RescaleOp(
            new float[]{0f, 1f, 0f, 1f},
            new float[]{0f, 0f, 0f, 0f},
            null
    );
    public static final RescaleOp CRT_BLEED_BLUE_OP = new RescaleOp(
            new float[]{0f, 0f, 1f, 1f},
            new float[]{0f, 0f, 0f, 0f},
            null
    );
}
