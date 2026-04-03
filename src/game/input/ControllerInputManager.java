package game.input;

import org.libsdl.SDL;

import java.io.File;
import java.util.Locale;

public final class ControllerInputManager {
    private static final long RESCAN_INTERVAL_NS = 2_000_000_000L;
    private static final float STICK_THRESHOLD = 0.55f;
    private static final boolean DEBUG_LOGGING = false;

    private boolean initialized;
    private boolean available = true;
    private long activeControllerPtr;
    private long lastRescanNanos;
    private boolean noControllerLogged;
    private boolean connectedControllerLogged;
    private boolean upHeld;
    private boolean downHeld;
    private boolean leftHeld;
    private boolean rightHeld;
    private boolean confirmHeld;
    private boolean backHeld;
    private boolean radioHeld;

    public synchronized Snapshot poll() {
        if (!initializeIfNeeded()) {
            clearHeldState();
            return Snapshot.disconnected();
        }

        long now = System.nanoTime();
        ensureController(now);
        if (activeControllerPtr == 0L) {
            clearHeldState();
            return Snapshot.disconnected();
        }

        SDL.SDL_PumpEvents();
        SDL.SDL_GameControllerUpdate();
        if (!SDL.SDL_GameControllerGetAttached(activeControllerPtr)) {
            closeActiveController();
            clearHeldState();
            return Snapshot.disconnected();
        }

        boolean nextUpHeld = getButton(SDL.SDL_CONTROLLER_BUTTON_DPAD_UP) || getAxis(SDL.SDL_CONTROLLER_AXIS_LEFTY) <= -STICK_THRESHOLD;
        boolean nextDownHeld = getButton(SDL.SDL_CONTROLLER_BUTTON_DPAD_DOWN) || getAxis(SDL.SDL_CONTROLLER_AXIS_LEFTY) >= STICK_THRESHOLD;
        boolean nextLeftHeld = getButton(SDL.SDL_CONTROLLER_BUTTON_DPAD_LEFT) || getAxis(SDL.SDL_CONTROLLER_AXIS_LEFTX) <= -STICK_THRESHOLD;
        boolean nextRightHeld = getButton(SDL.SDL_CONTROLLER_BUTTON_DPAD_RIGHT) || getAxis(SDL.SDL_CONTROLLER_AXIS_LEFTX) >= STICK_THRESHOLD;
        boolean nextConfirmHeld = getButton(SDL.SDL_CONTROLLER_BUTTON_A) || getButton(SDL.SDL_CONTROLLER_BUTTON_X) || getButton(SDL.SDL_CONTROLLER_BUTTON_START);
        boolean nextBackHeld = getButton(SDL.SDL_CONTROLLER_BUTTON_B) || getButton(SDL.SDL_CONTROLLER_BUTTON_Y) || getButton(SDL.SDL_CONTROLLER_BUTTON_BACK);
        boolean nextRadioHeld = getAxis(SDL.SDL_CONTROLLER_AXIS_TRIGGERRIGHT) >= STICK_THRESHOLD;

        Snapshot snapshot = new Snapshot(
                true,
                nextUpHeld,
                nextDownHeld,
                nextLeftHeld,
                nextRightHeld,
                nextConfirmHeld,
                nextBackHeld,
                nextRadioHeld,
                nextUpHeld && !upHeld,
                nextDownHeld && !downHeld,
                nextLeftHeld && !leftHeld,
                nextRightHeld && !rightHeld,
                nextConfirmHeld && !confirmHeld,
                nextBackHeld && !backHeld,
                nextRadioHeld && !radioHeld
        );

        upHeld = nextUpHeld;
        downHeld = nextDownHeld;
        leftHeld = nextLeftHeld;
        rightHeld = nextRightHeld;
        confirmHeld = nextConfirmHeld;
        backHeld = nextBackHeld;
        radioHeld = nextRadioHeld;
        return snapshot;
    }

    public synchronized void prewarm() {
        if (!initializeIfNeeded()) {
            return;
        }
        ensureController(System.nanoTime());
    }

    public synchronized void rumble(float strength, int durationMs) {
        if (!initializeIfNeeded()) {
            return;
        }
        ensureController(System.nanoTime());
        if (activeControllerPtr == 0L) {
            return;
        }

        float clampedStrength = Math.max(0.0f, Math.min(1.0f, strength));
        int rumble = Math.max(0, Math.min(0xFFFF, Math.round(clampedStrength * 0xFFFF)));
        SDL.SDL_GameControllerRumble(activeControllerPtr, rumble, rumble, Math.max(0, durationMs));
    }

    private boolean initializeIfNeeded() {
        if (initialized) {
            return available;
        }
        initialized = true;
        if (!SDL.isAvailable()) {
            available = false;
            log("SDL native unavailable: " + SDL.getLoadError());
            return false;
        }

        int initResult = SDL.SDL_Init(SDL.SDL_INIT_EVENTS | SDL.SDL_INIT_JOYSTICK | SDL.SDL_INIT_GAMECONTROLLER);
        if (initResult != 0) {
            available = false;
            log("SDL_Init failed: " + SDL.SDL_GetError());
            return false;
        }

        log("SDL initialized");
        loadMappings();
        return true;
    }

    private void loadMappings() {
        File[] candidates = {
                new File("src/assets/gamecontrollerdb.txt"),
                new File("assets/gamecontrollerdb.txt")
        };
        for (File file : candidates) {
            if (!file.isFile()) {
                continue;
            }
            int added = SDL.SDL_GameControllerAddMappingsFromFile(file.getAbsolutePath());
            log("Loaded controller mappings from " + file.getAbsolutePath() + " result=" + added);
            return;
        }
        log("No controller mapping file found");
    }

    private void ensureController(long now) {
        if (activeControllerPtr != 0L && SDL.SDL_GameControllerGetAttached(activeControllerPtr)) {
            return;
        }
        closeActiveController();
        if (now - lastRescanNanos < RESCAN_INTERVAL_NS) {
            return;
        }
        lastRescanNanos = now;

        int joystickCount = SDL.SDL_NumJoysticks();
        if (joystickCount > 0) {
            log("SDL reports " + joystickCount + " joystick(s)");
        }
        long preferredPtr = 0L;
        long fallbackPtr = 0L;
        for (int index = 0; index < joystickCount; index++) {
            String deviceName = SDL.SDL_GameControllerNameForIndex(index);
            boolean gameController = SDL.SDL_IsGameController(index);
            log("Index " + index + " name=" + deviceName + " isGameController=" + gameController);
            if (!gameController) {
                continue;
            }

            long controllerPtr = SDL.SDL_GameControllerOpen(index);
            if (controllerPtr == 0L) {
                log("Failed to open controller index " + index + ": " + SDL.SDL_GetError());
                continue;
            }

            if (fallbackPtr == 0L) {
                fallbackPtr = controllerPtr;
            }

            String name = SDL.SDL_GameControllerName(controllerPtr);
            int type = SDL.SDL_GameControllerGetType(controllerPtr);
            String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
            if (type == SDL.SDL_CONTROLLER_TYPE_NINTENDO_SWITCH_PRO
                    || normalized.contains("switch")
                    || normalized.contains("pro controller")
                    || normalized.contains("nintendo")) {
                preferredPtr = controllerPtr;
                log("Selected preferred controller: " + name + " type=" + type);
                break;
            }

            if (controllerPtr != fallbackPtr) {
                SDL.SDL_GameControllerClose(controllerPtr);
            }
        }

        activeControllerPtr = preferredPtr != 0L ? preferredPtr : fallbackPtr;
        if (activeControllerPtr != 0L) {
            noControllerLogged = false;
            if (!connectedControllerLogged) {
                connectedControllerLogged = true;
                System.out.println("[controller] Connected: " + SDL.SDL_GameControllerName(activeControllerPtr)
                        + " (type=" + SDL.SDL_GameControllerGetType(activeControllerPtr) + ")");
            }
            log("Active controller: " + SDL.SDL_GameControllerName(activeControllerPtr)
                    + " type=" + SDL.SDL_GameControllerGetType(activeControllerPtr));
        } else if (!noControllerLogged) {
            noControllerLogged = true;
            log("No active controller selected");
        }
    }

    private float getAxis(int axis) {
        if (activeControllerPtr == 0L) {
            return 0.0f;
        }
        int raw = SDL.SDL_GameControllerGetAxis(activeControllerPtr, axis);
        if (raw < 0) {
            return raw / 32768.0f;
        }
        return raw / 32767.0f;
    }

    private boolean getButton(int button) {
        return activeControllerPtr != 0L && SDL.SDL_GameControllerGetButton(activeControllerPtr, button) == 1;
    }

    private void closeActiveController() {
        if (activeControllerPtr != 0L) {
            SDL.SDL_GameControllerClose(activeControllerPtr);
            activeControllerPtr = 0L;
            connectedControllerLogged = false;
        }
    }

    private void clearHeldState() {
        upHeld = false;
        downHeld = false;
        leftHeld = false;
        rightHeld = false;
        confirmHeld = false;
        backHeld = false;
        radioHeld = false;
    }

    private void log(String message) {
        if (DEBUG_LOGGING) {
            System.out.println("[controller] " + message);
        }
    }

    public static final class Snapshot {
        private final boolean connected;
        private final boolean upHeld;
        private final boolean downHeld;
        private final boolean leftHeld;
        private final boolean rightHeld;
        private final boolean confirmHeld;
        private final boolean backHeld;
        private final boolean radioHeld;
        private final boolean upPressed;
        private final boolean downPressed;
        private final boolean leftPressed;
        private final boolean rightPressed;
        private final boolean confirmPressed;
        private final boolean backPressed;
        private final boolean radioPressed;

        private Snapshot(
                boolean connected,
                boolean upHeld,
                boolean downHeld,
                boolean leftHeld,
                boolean rightHeld,
                boolean confirmHeld,
                boolean backHeld,
                boolean radioHeld,
                boolean upPressed,
                boolean downPressed,
                boolean leftPressed,
                boolean rightPressed,
                boolean confirmPressed,
                boolean backPressed,
                boolean radioPressed
        ) {
            this.connected = connected;
            this.upHeld = upHeld;
            this.downHeld = downHeld;
            this.leftHeld = leftHeld;
            this.rightHeld = rightHeld;
            this.confirmHeld = confirmHeld;
            this.backHeld = backHeld;
            this.radioHeld = radioHeld;
            this.upPressed = upPressed;
            this.downPressed = downPressed;
            this.leftPressed = leftPressed;
            this.rightPressed = rightPressed;
            this.confirmPressed = confirmPressed;
            this.backPressed = backPressed;
            this.radioPressed = radioPressed;
        }

        private static Snapshot disconnected() {
            return new Snapshot(false, false, false, false, false, false, false, false, false, false, false, false, false, false, false);
        }

        public boolean isConnected() {
            return connected;
        }

        public boolean isUpHeld() {
            return upHeld;
        }

        public boolean isDownHeld() {
            return downHeld;
        }

        public boolean isLeftHeld() {
            return leftHeld;
        }

        public boolean isRightHeld() {
            return rightHeld;
        }

        public boolean isConfirmPressed() {
            return confirmPressed;
        }

        public boolean isBackPressed() {
            return backPressed;
        }

        public boolean isUpPressed() {
            return upPressed;
        }

        public boolean isDownPressed() {
            return downPressed;
        }

        public boolean isLeftPressed() {
            return leftPressed;
        }

        public boolean isRightPressed() {
            return rightPressed;
        }

        public boolean isRadioPressed() {
            return radioPressed;
        }
    }
}
