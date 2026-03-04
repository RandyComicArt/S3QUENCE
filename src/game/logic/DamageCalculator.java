package game.logic;

import game.config.GameConfig;

public final class DamageCalculator {
    private DamageCalculator() {
    }

    public static int calculateDamage(RoundCompletion completion) {
        double lengthFactor = completion.getSequenceLength() / (double) GameConfig.MAX_SEQUENCE_LENGTH;
        int baseDamage = 10 + (int) Math.round(26.0 * lengthFactor);

        double speedFactor = completion.getRoundDurationMs() > 0
                ? completion.getTimeLeftMs() / (double) completion.getRoundDurationMs()
                : 0.0;
        speedFactor = Math.max(0.0, Math.min(1.0, speedFactor));
        int speedBonus = (int) Math.round((8 + completion.getSequenceLength() * 2.0) * speedFactor);

        return Math.max(6, baseDamage + speedBonus);
    }
}
