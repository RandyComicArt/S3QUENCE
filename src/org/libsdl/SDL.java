package org.libsdl;

import java.io.File;

public final class SDL {
    public static final int SDL_INIT_EVENTS = 0x00004000;
    public static final int SDL_INIT_JOYSTICK = 0x00000200;
    public static final int SDL_INIT_GAMECONTROLLER = 0x00002000;

    public static final int SDL_CONTROLLER_AXIS_LEFTX = 0;
    public static final int SDL_CONTROLLER_AXIS_LEFTY = 1;

    public static final int SDL_CONTROLLER_BUTTON_A = 0;
    public static final int SDL_CONTROLLER_BUTTON_B = 1;
    public static final int SDL_CONTROLLER_BUTTON_X = 2;
    public static final int SDL_CONTROLLER_BUTTON_Y = 3;
    public static final int SDL_CONTROLLER_BUTTON_BACK = 4;
    public static final int SDL_CONTROLLER_BUTTON_START = 6;
    public static final int SDL_CONTROLLER_BUTTON_DPAD_UP = 11;
    public static final int SDL_CONTROLLER_BUTTON_DPAD_DOWN = 12;
    public static final int SDL_CONTROLLER_BUTTON_DPAD_LEFT = 13;
    public static final int SDL_CONTROLLER_BUTTON_DPAD_RIGHT = 14;

    public static final int SDL_CONTROLLER_TYPE_NINTENDO_SWITCH_PRO = 5;

    private static final boolean AVAILABLE;
    private static final Throwable LOAD_ERROR;

    static {
        boolean loaded = false;
        Throwable error = null;
        try {
            File nativeLib = new File("lib/libsdl2gdx64.dylib");
            if (nativeLib.isFile()) {
                System.load(nativeLib.getAbsolutePath());
                loaded = true;
            } else {
                error = new IllegalStateException("Missing SDL native library: " + nativeLib.getAbsolutePath());
            }
        } catch (Throwable t) {
            error = t;
        }
        AVAILABLE = loaded;
        LOAD_ERROR = error;
    }

    private SDL() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static Throwable getLoadError() {
        return LOAD_ERROR;
    }

    public static native int SDL_Init(int flags);
    public static native void SDL_Quit();
    public static native void SDL_PumpEvents();
    public static native int SDL_NumJoysticks();
    public static native boolean SDL_IsGameController(int joystickIndex);
    public static native String SDL_GameControllerNameForIndex(int index);
    public static native int SDL_GameControllerAddMappingsFromFile(String path);
    public static native long SDL_GameControllerOpen(int index);
    public static native void SDL_GameControllerClose(long ptr);
    public static native boolean SDL_GameControllerGetAttached(long ptr);
    public static native String SDL_GameControllerName(long ptr);
    public static native int SDL_GameControllerGetType(long ptr);
    public static native int SDL_GameControllerGetAxis(long ptr, int axis);
    public static native int SDL_GameControllerGetButton(long ptr, int button);
    public static native void SDL_GameControllerUpdate();
    public static native String SDL_GetError();
}
