package game.logic;

import game.config.GameConfig;
import game.model.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RoundManager {
    private final Random random = new Random();
    private final List<Integer> sequence = new ArrayList<>();

    private int progressIndex;
    private int roundsCleared;
    private long roundStartTimeMs;
    private long roundDurationMs;
    private long wrongFlashUntilMs;

    public void startGame(boolean resetScore) {
        if (resetScore) {
            roundsCleared = 0;
        }
        startNewRound();
    }

    public void handleSymbolInput(int input) {
        long now = System.currentTimeMillis();
        if (now < wrongFlashUntilMs || sequence.isEmpty()) {
            return;
        }

        if (sequence.get(progressIndex) == input) {
            progressIndex++;
            if (progressIndex == sequence.size()) {
                roundsCleared++;
                startNewRound();
            }
            return;
        }

        wrongFlashUntilMs = now + GameConfig.WRONG_FLASH_MS;
        progressIndex = 0;
    }

    public List<Integer> getSequence() {
        return Collections.unmodifiableList(sequence);
    }

    public int getProgressIndex() {
        return progressIndex;
    }

    public int getRoundsCleared() {
        return roundsCleared;
    }

    public long getTimeLeftMs() {
        long elapsed = System.currentTimeMillis() - roundStartTimeMs;
        return Math.max(0, roundDurationMs - elapsed);
    }

    public long getRoundDurationMs() {
        return roundDurationMs;
    }

    public boolean isWrongFlashActive() {
        return System.currentTimeMillis() < wrongFlashUntilMs;
    }

    public boolean hasTimedOut() {
        return getTimeLeftMs() <= 0;
    }

    private void startNewRound() {
        sequence.clear();
        int sequenceLength = random.nextInt(
                GameConfig.MAX_SEQUENCE_LENGTH - GameConfig.MIN_SEQUENCE_LENGTH + 1
        ) + GameConfig.MIN_SEQUENCE_LENGTH;

        for (int i = 0; i < sequenceLength; i++) {
            sequence.add(random.nextInt(Direction.values().length));
        }

        progressIndex = 0;
        wrongFlashUntilMs = 0;
        roundStartTimeMs = System.currentTimeMillis();
        roundDurationMs = GameConfig.BASE_ROUND_TIME_MS + (sequenceLength * GameConfig.TIME_PER_SYMBOL_MS);
    }
}
