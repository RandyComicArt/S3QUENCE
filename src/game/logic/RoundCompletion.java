package game.logic;

public class RoundCompletion {
    private final int sequenceLength;
    private final long roundDurationMs;
    private final long elapsedMs;
    private final long timeLeftMs;
    private final int pendingDamage;
    private final int resolvedDamage;

    public RoundCompletion(
            int sequenceLength,
            long roundDurationMs,
            long elapsedMs,
            long timeLeftMs,
            int pendingDamage,
            int resolvedDamage
    ) {
        this.sequenceLength = sequenceLength;
        this.roundDurationMs = roundDurationMs;
        this.elapsedMs = elapsedMs;
        this.timeLeftMs = timeLeftMs;
        this.pendingDamage = pendingDamage;
        this.resolvedDamage = resolvedDamage;
    }

    public int getSequenceLength() {
        return sequenceLength;
    }

    public long getRoundDurationMs() {
        return roundDurationMs;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public long getTimeLeftMs() {
        return timeLeftMs;
    }

    public int getPendingDamage() {
        return pendingDamage;
    }

    public int getResolvedDamage() {
        return resolvedDamage;
    }
}
