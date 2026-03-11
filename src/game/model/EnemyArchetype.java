package game.model;

public enum EnemyArchetype {
    NORMAL("DRONE", "STANDARD PATTERN", 1.00, 4, 8, 1.00, false, -1L, true, 99, false),
    BERSERKER("BERSERKER", "NO REFILL", 2.15, 5, 8, 1.18, false, -1L, false, 99, false),
    ECHO("ECHO", "MEMORY FIGHT", 1.00, 4, 5, 2.15, false, 900L, true, 99, false),
    REVERSE("REVEAL", "UNFOLD THE CHAIN", 1.25, 4, 7, 1.85, false, -1L, true, 2, true);

    private final String label;
    private final String ruleLabel;
    private final double timerMultiplier;
    private final int minSequenceLength;
    private final int maxSequenceLength;
    private final double damageMultiplier;
    private final boolean reverseInput;
    private final long revealWindowMs;
    private final boolean timeRecoveryEnabled;
    private final int initialVisibleCount;
    private final boolean progressiveReveal;

    EnemyArchetype(
            String label,
            String ruleLabel,
            double timerMultiplier,
            int minSequenceLength,
            int maxSequenceLength,
            double damageMultiplier,
            boolean reverseInput,
            long revealWindowMs,
            boolean timeRecoveryEnabled,
            int initialVisibleCount,
            boolean progressiveReveal
    ) {
        this.label = label;
        this.ruleLabel = ruleLabel;
        this.timerMultiplier = timerMultiplier;
        this.minSequenceLength = minSequenceLength;
        this.maxSequenceLength = maxSequenceLength;
        this.damageMultiplier = damageMultiplier;
        this.reverseInput = reverseInput;
        this.revealWindowMs = revealWindowMs;
        this.timeRecoveryEnabled = timeRecoveryEnabled;
        this.initialVisibleCount = initialVisibleCount;
        this.progressiveReveal = progressiveReveal;
    }

    public String getLabel() {
        return label;
    }

    public String getRuleLabel() {
        return ruleLabel;
    }

    public double getTimerMultiplier() {
        return timerMultiplier;
    }

    public int getMinSequenceLength() {
        return minSequenceLength;
    }

    public int getMaxSequenceLength() {
        return maxSequenceLength;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public boolean isReverseInput() {
        return reverseInput;
    }

    public long getRevealWindowMs() {
        return revealWindowMs;
    }

    public boolean isTimeRecoveryEnabled() {
        return timeRecoveryEnabled;
    }

    public int getInitialVisibleCount() {
        return initialVisibleCount;
    }

    public boolean isProgressiveReveal() {
        return progressiveReveal;
    }

    public boolean isRhythmMode() {
        return false;
    }
}
