package game.logic;

import game.config.GameConfig;

public final class DamageCalculator {
    private static final long FAST_CADENCE_MS = 180L;
    private static final long SLOW_CADENCE_MS = 760L;

    private DamageCalculator() {
    }

    public static int calculatePotentialIncrement(int sequenceLength, int keyIndex, long cadenceMs) {
        double speedFactor = cadenceToFactor(cadenceMs);
        if (speedFactor <= 0.0) {
            return 0;
        }

        // Keep longer sequences rewarding, but avoid runaway scaling.
        int safeLength = Math.max(GameConfig.MIN_SEQUENCE_LENGTH, sequenceLength);
        double lengthDelta = safeLength - GameConfig.MIN_SEQUENCE_LENGTH;
        double sequenceFactor = 1.0 + (lengthDelta * 0.03);

        // Use relative key progress so long sequences don't gain huge per-key multipliers.
        double progress = safeLength > 1
                ? Math.max(0.0, Math.min(1.0, keyIndex / (double) (safeLength - 1)))
                : 0.0;
        double keyProgressFactor = 0.92 + (progress * 0.16);
        double base = 3.8;

        int increment = (int) Math.round(
                base * sequenceFactor * keyProgressFactor * speedFactor * GameConfig.DAMAGE_OUTPUT_SCALE
        );
        return Math.max(0, increment);
    }

    private static double cadenceToFactor(long cadenceMs) {
        if (cadenceMs <= FAST_CADENCE_MS) {
            return 1.0;
        }
        if (cadenceMs >= SLOW_CADENCE_MS) {
            return 0.0;
        }
        double normalized = (cadenceMs - FAST_CADENCE_MS) / (double) (SLOW_CADENCE_MS - FAST_CADENCE_MS);
        return Math.max(0.0, Math.min(1.0, 1.0 - normalized));
    }
}
