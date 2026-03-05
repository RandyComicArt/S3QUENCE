package game.visual;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EnemyKillEffects {
    private static final int KILL_EFFECT_MAX_COUNT = 10;
    private static final long ENEMY_KILL_EFFECT_MS = 760L;

    private final Random random = new Random();
    private final List<EnemyKillEffect> effects = new ArrayList<>();

    private static class EnemyKillEffect {
        long startMs;
        long durationMs;
    }

    public void clear() {
        effects.clear();
    }

    public void spawn() {
        long nowMs = System.currentTimeMillis();
        EnemyKillEffect effect = new EnemyKillEffect();
        effect.startMs = nowMs;
        effect.durationMs = ENEMY_KILL_EFFECT_MS + random.nextInt(181);
        effects.add(effect);
        if (effects.size() > KILL_EFFECT_MAX_COUNT) {
            effects.remove(0);
        }
    }

    public void update() {
        long nowMs = System.currentTimeMillis();
        effects.removeIf(effect -> nowMs - effect.startMs > effect.durationMs);
    }

    public void draw(Graphics2D g2d, int roomX, int roomY, int roomW, int roomH) {
        if (effects.isEmpty()) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        int strongestFlashAlpha = 0;

        for (EnemyKillEffect effect : effects) {
            long elapsedMs = nowMs - effect.startMs;
            if (elapsedMs < 0 || elapsedMs > effect.durationMs) {
                continue;
            }

            double progress = elapsedMs / (double) effect.durationMs;
            double life = 1.0 - progress;
            int flashAlpha = clampInt((int) Math.round(95 * life * life), 0, 255);
            strongestFlashAlpha = Math.max(strongestFlashAlpha, flashAlpha);
        }

        if (strongestFlashAlpha > 0) {
            g2d.setColor(new Color(120, 226, 255, strongestFlashAlpha));
            g2d.fillRect(roomX + 1, roomY + 1, roomW - 2, roomH - 2);
        }
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
