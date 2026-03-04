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

        double sequenceFactor = 0.9 + ((sequenceLength - GameConfig.MIN_SEQUENCE_LENGTH) * 0.12);
        double keyProgressFactor = 0.82 + (Math.max(0, keyIndex) * 0.12);
        double base = 2.4 + (sequenceLength * 0.72);

        int increment = (int) Math.round(base * sequenceFactor * keyProgressFactor * speedFactor);
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
