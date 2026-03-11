package game.logic;

import game.config.GameConfig;
import game.model.Direction;
import game.model.EnemyArchetype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RoundManager {
    private final Random random = new Random();
    private final List<Integer> sequence = new ArrayList<>();

    private int progressIndex;
    private int roundsCleared;
    private long sequenceStartTimeMs;
    private long timerMaxMs;
    private long timerRemainingMs;
    private long lastTimerUpdateMs;
    private long wrongFlashUntilMs;
    private int pendingDamage;
    private long lastCorrectInputTimeMs;
    private boolean sequenceCompleteHoldActive;
    private long sequenceCompleteHoldUntilMs;
    private long heldTimeLeftMs;
    private long pendingTimeRestoreMs;
    private EnemyArchetype activeArchetype = EnemyArchetype.NORMAL;
    private int minSequenceLength = GameConfig.MIN_SEQUENCE_LENGTH;
    private int maxSequenceLength = GameConfig.MAX_SEQUENCE_LENGTH;
    private long encounterTimerBaseMs = GameConfig.ENCOUNTER_MAX_TIME_MS;
    private long sequenceRevealWindowMs = -1L;

    public void startGame(boolean resetScore) {
        startGame(resetScore, 0L);
    }

    public void configureEncounter(EnemyArchetype archetype) {
        activeArchetype = archetype == null ? EnemyArchetype.NORMAL : archetype;
        minSequenceLength = activeArchetype.getMinSequenceLength();
        maxSequenceLength = activeArchetype.getMaxSequenceLength();
        encounterTimerBaseMs = Math.max(
                1000L,
                Math.round(GameConfig.ENCOUNTER_MAX_TIME_MS * activeArchetype.getTimerMultiplier())
        );
        sequenceRevealWindowMs = activeArchetype.getRevealWindowMs();
    }

    public void startGame(boolean resetScore, long bonusTimeMs) {
        if (resetScore) {
            roundsCleared = 0;
        }
        long configuredStart = Math.max(0L, encounterTimerBaseMs + bonusTimeMs);
        timerMaxMs = Math.max(1L, configuredStart);
        timerRemainingMs = timerMaxMs;
        lastTimerUpdateMs = System.currentTimeMillis();
        startNewSequence();
    }

    public RoundCompletion handleSymbolInput(int input) {
        updateRuntimeState();
        long now = System.currentTimeMillis();
        if (sequenceCompleteHoldActive || now < wrongFlashUntilMs || sequence.isEmpty()) {
            return null;
        }

        int expectedIndex = activeArchetype.isReverseInput()
                ? (sequence.size() - 1 - progressIndex)
                : progressIndex;
        if (sequence.get(expectedIndex) == input) {
            long cadenceReferenceMs = lastCorrectInputTimeMs > 0L ? lastCorrectInputTimeMs : sequenceStartTimeMs;
            long cadenceMs = Math.max(0L, now - cadenceReferenceMs);
            int increment = DamageCalculator.calculatePotentialIncrement(sequence.size(), progressIndex, cadenceMs);
            pendingDamage += increment;
            if (activeArchetype.isTimeRecoveryEnabled()) {
                timerRemainingMs = Math.min(timerMaxMs, timerRemainingMs + GameConfig.TIME_RESTORE_PER_CORRECT_KEY_MS);
            }

            lastCorrectInputTimeMs = now;

            progressIndex++;
            if (progressIndex == sequence.size()) {
                long sequenceElapsedMs = Math.max(0L, now - sequenceStartTimeMs);
                long timeLeftMs = Math.max(0L, timerRemainingMs);
                int pendingBeforeResolve = pendingDamage;
                pendingTimeRestoreMs = calculateTimeRestoreMs(sequence.size(), sequenceElapsedMs, timeLeftMs);
                heldTimeLeftMs = timeLeftMs;
                sequenceCompleteHoldActive = true;
                sequenceCompleteHoldUntilMs = now + GameConfig.SEQUENCE_COMPLETE_HOLD_MS;
                RoundCompletion completion = new RoundCompletion(
                        sequence.size(),
                        timerMaxMs,
                        Math.max(0L, timerMaxMs - timeLeftMs),
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
        clearComboState();
        if (sequenceRevealWindowMs > 0L) {
            sequenceStartTimeMs = now;
        }
        return null;
    }

    public List<Integer> getSequence() {
        updateRuntimeState();
        return Collections.unmodifiableList(sequence);
    }

    public EnemyArchetype getActiveArchetype() {
        return activeArchetype;
    }

    public boolean shouldHideSequence() {
        updateRuntimeState();
        return sequenceRevealWindowMs > 0L
                && !sequenceCompleteHoldActive
                && System.currentTimeMillis() - sequenceStartTimeMs >= sequenceRevealWindowMs;
    }

    public int getVisibleSequenceCount() {
        updateRuntimeState();
        if (!activeArchetype.isProgressiveReveal()) {
            return sequence.size();
        }
        int visibleCount = activeArchetype.getInitialVisibleCount() + progressIndex;
        return Math.max(0, Math.min(sequence.size(), visibleCount));
    }

    public int getProgressIndex() {
        updateRuntimeState();
        return progressIndex;
    }

    public int getRoundsCleared() {
        return roundsCleared;
    }

    public long getTimeLeftMs() {
        updateRuntimeState();
        if (sequenceCompleteHoldActive) {
            long displayed = activeArchetype.isTimeRecoveryEnabled()
                    ? heldTimeLeftMs + pendingTimeRestoreMs
                    : heldTimeLeftMs;
            return Math.max(1L, Math.min(timerMaxMs, displayed));
        }
        return Math.max(0L, timerRemainingMs);
    }

    public long getRoundDurationMs() {
        updateRuntimeState();
        return timerMaxMs;
    }

    public boolean isWrongFlashActive() {
        updateRuntimeState();
        return System.currentTimeMillis() < wrongFlashUntilMs;
    }

    public boolean hasTimedOut() {
        updateRuntimeState();
        if (sequenceCompleteHoldActive) {
            return false;
        }
        return timerRemainingMs <= 0L;
    }

    public int getPendingDamage() {
        updateRuntimeState();
        return pendingDamage;
    }

    private void startNewSequence() {
        sequence.clear();
        int sequenceLength = random.nextInt(maxSequenceLength - minSequenceLength + 1) + minSequenceLength;

        for (int i = 0; i < sequenceLength; i++) {
            sequence.add(random.nextInt(Direction.values().length));
        }

        progressIndex = 0;
        wrongFlashUntilMs = 0;
        sequenceStartTimeMs = System.currentTimeMillis();
        clearComboState();
        sequenceCompleteHoldActive = false;
        sequenceCompleteHoldUntilMs = 0L;
        heldTimeLeftMs = 0L;
        pendingTimeRestoreMs = 0L;
    }

    private void clearComboState() {
        pendingDamage = 0;
        lastCorrectInputTimeMs = 0L;
    }

    private void updateRuntimeState() {
        long now = System.currentTimeMillis();
        updateTimer(now);
        updateSequenceCompleteHold(now);
    }

    private void updateTimer(long now) {
        if (lastTimerUpdateMs == 0L) {
            lastTimerUpdateMs = now;
            return;
        }
        long elapsedMs = Math.max(0L, now - lastTimerUpdateMs);
        lastTimerUpdateMs = now;
        if (elapsedMs <= 0L || sequenceCompleteHoldActive || now < wrongFlashUntilMs) {
            return;
        }
        timerRemainingMs = Math.max(0L, timerRemainingMs - elapsedMs);
    }

    private void updateSequenceCompleteHold(long now) {
        if (!sequenceCompleteHoldActive) {
            return;
        }
        if (now < sequenceCompleteHoldUntilMs) {
            return;
        }

        if (activeArchetype.isTimeRecoveryEnabled()) {
            timerRemainingMs = Math.min(timerMaxMs, timerRemainingMs + pendingTimeRestoreMs);
        }
        pendingTimeRestoreMs = 0L;
        sequenceCompleteHoldActive = false;
        sequenceCompleteHoldUntilMs = 0L;
        heldTimeLeftMs = 0L;
        lastTimerUpdateMs = now;
        startNewSequence();
    }

    private long calculateTimeRestoreMs(int sequenceLength, long sequenceElapsedMs, long timeLeftMs) {
        long base = GameConfig.TIME_RESTORE_BASE_MS + (sequenceLength * GameConfig.TIME_RESTORE_PER_SYMBOL_MS);

        double speedTargetMs = 900.0 + (sequenceLength * 320.0);
        double speedRatio = 1.0 - Math.min(1.0, sequenceElapsedMs / speedTargetMs);
        long speedBonus = Math.round(speedRatio * GameConfig.TIME_RESTORE_SPEED_BONUS_MS);

        double pressureRatio = 1.0 - Math.min(1.0, timeLeftMs / (double) Math.max(1L, timerMaxMs));
        long pressureBonus = Math.round(pressureRatio * GameConfig.TIME_RESTORE_PRESSURE_BONUS_MS);

        return Math.max(GameConfig.TIME_RESTORE_MIN_MS, base + speedBonus + pressureBonus);
    }
}
