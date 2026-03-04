package game.logic;

public class RoundCompletion {
    private final int sequenceLength;
    private final long roundDurationMs;
    private final long elapsedMs;
    private final long timeLeftMs;

    public RoundCompletion(int sequenceLength, long roundDurationMs, long elapsedMs, long timeLeftMs) {
        this.sequenceLength = sequenceLength;
        this.roundDurationMs = roundDurationMs;
        this.elapsedMs = elapsedMs;
        this.timeLeftMs = timeLeftMs;
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
}
