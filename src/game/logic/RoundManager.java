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
    private int pendingDamage;
    private long lastCorrectInputTimeMs;
    private boolean sequenceCompleteHoldActive;
    private long sequenceCompleteHoldUntilMs;
    private long heldTimeLeftMs;

    public void startGame(boolean resetScore) {
        if (resetScore) {
            roundsCleared = 0;
        }
        startNewRound();
    }

    public RoundCompletion handleSymbolInput(int input) {
        updateSequenceCompleteHold();
        long now = System.currentTimeMillis();
        if (sequenceCompleteHoldActive || now < wrongFlashUntilMs || sequence.isEmpty()) {
            return null;
        }

        if (sequence.get(progressIndex) == input) {
            long cadenceReferenceMs = lastCorrectInputTimeMs > 0L ? lastCorrectInputTimeMs : roundStartTimeMs;
            long cadenceMs = Math.max(0L, now - cadenceReferenceMs);
            int increment = DamageCalculator.calculatePotentialIncrement(sequence.size(), progressIndex, cadenceMs);
            pendingDamage += increment;

            lastCorrectInputTimeMs = now;

            progressIndex++;
            if (progressIndex == sequence.size()) {
                long elapsedMs = Math.max(0L, now - roundStartTimeMs);
                long timeLeftMs = Math.max(0L, roundDurationMs - elapsedMs);
                int pendingBeforeResolve = pendingDamage;
                heldTimeLeftMs = timeLeftMs;
                sequenceCompleteHoldActive = true;
                sequenceCompleteHoldUntilMs = now + GameConfig.SEQUENCE_COMPLETE_HOLD_MS;
                RoundCompletion completion = new RoundCompletion(
                        sequence.size(),
                        roundDurationMs,
                        elapsedMs,
                        timeLeftMs,
                        pendingBeforeResolve,
                        pendingBeforeResolve
                );
                roundsCleared++;
                // Damage is resolved by the caller immediately; clear preview state
                // while preserving this sequence for the brief completion hold.
                pendingDamage = 0;
                lastCorrectInputTimeMs = 0L;
                return completion;
            }
            return null;
        }

        wrongFlashUntilMs = now + GameConfig.WRONG_FLASH_MS;
        progressIndex = 0;
        resetComboState();
        return null;
    }

    public List<Integer> getSequence() {
        updateSequenceCompleteHold();
        return Collections.unmodifiableList(sequence);
    }

    public int getProgressIndex() {
        updateSequenceCompleteHold();
        return progressIndex;
    }

    public int getRoundsCleared() {
        return roundsCleared;
    }

    public long getTimeLeftMs() {
        updateSequenceCompleteHold();
        if (sequenceCompleteHoldActive) {
            return Math.max(1L, heldTimeLeftMs);
        }
        long elapsed = System.currentTimeMillis() - roundStartTimeMs;
        return Math.max(0, roundDurationMs - elapsed);
    }

    public long getRoundDurationMs() {
        updateSequenceCompleteHold();
        return roundDurationMs;
    }

    public boolean isWrongFlashActive() {
        updateSequenceCompleteHold();
        return System.currentTimeMillis() < wrongFlashUntilMs;
    }

    public boolean hasTimedOut() {
        updateSequenceCompleteHold();
        if (sequenceCompleteHoldActive) {
            return false;
        }
        return getTimeLeftMs() <= 0;
    }

    public int getPendingDamage() {
        updateSequenceCompleteHold();
        return pendingDamage;
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
        resetComboState();
    }

    private void resetComboState() {
        pendingDamage = 0;
        lastCorrectInputTimeMs = 0L;
        sequenceCompleteHoldActive = false;
        sequenceCompleteHoldUntilMs = 0L;
        heldTimeLeftMs = 0L;
    }

    private void updateSequenceCompleteHold() {
        if (!sequenceCompleteHoldActive) {
            return;
        }
        if (System.currentTimeMillis() < sequenceCompleteHoldUntilMs) {
            return;
        }
        startNewRound();
    }
}
