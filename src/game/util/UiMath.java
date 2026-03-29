package game.util;

import java.awt.Color;

/**
 * Small easing and color helpers used by the game UI.
 */
public final class UiMath {
    private UiMath() {
    }

    public static double easeInOut(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    public static double easeOutCubic(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return 1.0 - Math.pow(1.0 - clamped, 3.0);
    }

    public static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static Color lerpColor(Color from, Color to, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(from.getRed() + ((to.getRed() - from.getRed()) * clamped));
        int g = (int) Math.round(from.getGreen() + ((to.getGreen() - from.getGreen()) * clamped));
        int b = (int) Math.round(from.getBlue() + ((to.getBlue() - from.getBlue()) * clamped));
        int a = (int) Math.round(from.getAlpha() + ((to.getAlpha() - from.getAlpha()) * clamped));
        return new Color(r, g, b, a);
    }
}
