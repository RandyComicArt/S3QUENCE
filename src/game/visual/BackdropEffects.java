package game.visual;

import game.audio.AudioManager;
import game.config.GameConfig;
import game.logic.RoundCompletion;
import game.model.ScreenState;
import game.model.TimerStyle;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BackdropEffects {
    private static final Color BG_DEEP = new Color(0, 2, 10);
    private static final Color BG_MID = new Color(5, 18, 48);
    private static final Color WAVEFORM_COLOR = new Color(120, 230, 255);
    private static final int BACKDROP_GRID_SPACING = 52;
    private static final int RIPPLE_MAX_COUNT = 16;
    private static final int HUE_SWEEP_MAX_COUNT = 8;
    private static final int WAVEFORM_STEP = 6;
    private static final double WAVEFORM_BASE_Y_RATIO = 0.56;
    private static final double WAVEFORM_ENERGY_SMOOTH = 0.12;
    private static final int WAVEFORM_TRAIL_COUNT = 3;
    private static final double WAVEFORM_TRAIL_SPACING = 10.0;

    private final Random random = new Random();
    private final List<BackgroundRipple> backgroundRipples = new ArrayList<>();
    private final List<HueSweepRipple> hueSweepRipples = new ArrayList<>();
    private double waveformEnergy;

    private static class BackgroundRipple {
        int x;
        int y;
        int maxRadius;
        int bandWidth;
        double strength;
        double wavelength;
        double phase;
        long startMs;
        long durationMs;
    }

    private static class HueSweepRipple {
        long startMs;
        long durationMs;
        double startX;
        double endX;
        double bandWidth;
        double strength;
        double wavelength;
        double phase;
    }

    public void drawBackdrop(Graphics2D g2d, ScreenState screen, TimerStyle timerStyle, double encounterTimerProgress) {
        GradientPaint gradient = new GradientPaint(0, 0, BG_MID, 0, GameConfig.HEIGHT, BG_DEEP);
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, GameConfig.WIDTH, GameConfig.HEIGHT);

        long nowMs = System.currentTimeMillis();
        double t = nowMs / 1000.0;
        drawAudioWaveform(g2d, t);
        drawFlowField(g2d, t);
        drawWarpedGrid(g2d, nowMs);
        if (screen == ScreenState.ENCOUNTER && timerStyle == TimerStyle.BACKDROP_HUE) {
            drawBackdropTimerHue(g2d, encounterTimerProgress);
        }
    }

    public void update() {
        long nowMs = System.currentTimeMillis();
        backgroundRipples.removeIf(ripple -> nowMs - ripple.startMs > ripple.durationMs);
        hueSweepRipples.removeIf(ripple -> nowMs - ripple.startMs > ripple.durationMs);
    }

    public void clearHueSweeps() {
        hueSweepRipples.clear();
    }

    public void spawnInputRipple(
            int symbol,
            List<Integer> sequence,
            int progressIndex,
            int arenaX,
            int arenaY,
            int arenaW,
            int arenaH,
            int boxSize,
            int boxGap
    ) {
        if (sequence.isEmpty()) {
            return;
        }

        int count = sequence.size();
        int sequenceIndex = findRippleSequenceIndex(sequence, progressIndex, symbol);
        if (sequenceIndex < 0) {
            return;
        }

        int totalWidth = (count * boxSize) + ((count - 1) * boxGap);
        int startX = arenaX + (arenaW - totalWidth) / 2;
        int spawnX = startX + (sequenceIndex * (boxSize + boxGap)) + (boxSize / 2);
        int spawnY = arenaY + (arenaH / 2);
        spawnBackdropRippleAt(spawnX, spawnY, 300, 211, 34, 43, 15.0, 12.0, 22.0, 20.0, 760L, 501);
    }

    public void spawnEnemyDefeatRipples(int centerX, int centerY) {
        spawnBackdropRippleAt(
                centerX + random.nextInt(17) - 8,
                centerY + random.nextInt(17) - 8,
                430,
                250,
                42,
                38,
                24.0,
                13.0,
                18.0,
                14.0,
                980L,
                430
        );
        spawnBackdropRippleAt(
                centerX + random.nextInt(15) - 7,
                centerY + random.nextInt(15) - 7,
                330,
                220,
                38,
                34,
                20.0,
                11.0,
                16.0,
                15.0,
                860L,
                380
        );
    }

    public void triggerHueSweepRipple(RoundCompletion completion, long currentTimeLeftMs, TimerStyle selectedTimerStyle) {
        if (selectedTimerStyle != TimerStyle.BACKDROP_HUE) {
            return;
        }

        long durationMs = Math.max(1L, completion.getRoundDurationMs());
        double startProgress = clampDouble(completion.getTimeLeftMs() / (double) durationMs, 0.0, 1.0);
        double endProgress = clampDouble(currentTimeLeftMs / (double) durationMs, 0.0, 1.0);
        if (endProgress <= startProgress + 0.001) {
            return;
        }

        double deltaProgress = endProgress - startProgress;
        HueSweepRipple ripple = new HueSweepRipple();
        ripple.startMs = System.currentTimeMillis();
        ripple.durationMs = 680L + Math.round(Math.min(420.0, deltaProgress * 1900.0));
        ripple.startX = startProgress * GameConfig.WIDTH;
        ripple.endX = endProgress * GameConfig.WIDTH;
        ripple.bandWidth = 76.0 + Math.min(64.0, deltaProgress * 320.0);
        ripple.strength = 9.0 + Math.min(13.0, deltaProgress * 90.0);
        ripple.wavelength = 18.0 + random.nextDouble() * 10.0;
        ripple.phase = random.nextDouble() * Math.PI * 2.0;
        hueSweepRipples.add(ripple);

        while (hueSweepRipples.size() > HUE_SWEEP_MAX_COUNT) {
            hueSweepRipples.remove(0);
        }
    }

    private void drawBackdropTimerHue(Graphics2D g2d, double progress) {
        int overlayWidth = GameConfig.WIDTH;
        int xOffset = (int) Math.round((1.0 - progress) * overlayWidth);
        int x = -xOffset;

        g2d.setColor(new Color(24, 128, 236, 20));
        g2d.fillRect(x, 0, overlayWidth, GameConfig.HEIGHT);
    }

    private void drawFlowField(Graphics2D g2d, double t) {
        for (int i = 0; i < 4; i++) {
            int amplitude = 20 + (i * 8);
            int yBase = 140 + (i * 120);
            int alpha = 24 - (i * 4);
            g2d.setColor(new Color(80, 190, 255, Math.max(8, alpha)));
            int previousX = 0;
            int previousY = yBase + (int) Math.round(Math.sin((t * 1.2) + (i * 0.7)) * amplitude);
            for (int x = 24; x <= GameConfig.WIDTH; x += 24) {
                double wave = Math.sin((x * 0.012) + (t * (1.3 + (i * 0.18))) + (i * 0.5));
                int y = yBase + (int) Math.round(wave * amplitude);
                g2d.drawLine(previousX, previousY, x, y);
                previousX = x;
                previousY = y;
            }
        }
    }

    private void drawAudioWaveform(Graphics2D g2d, double t) {
        double rawEnergy = AudioManager.getMusicEnergy();
        double targetEnergy = Math.pow(clampDouble(rawEnergy, 0.0, 1.0), 0.7);
        waveformEnergy += (targetEnergy - waveformEnergy) * WAVEFORM_ENERGY_SMOOTH;

        double amplitude = 22.0 + (waveformEnergy * 120.0);
        double lowFreq = 0.0029;
        double midFreq = 0.0068;
        double highFreq = 0.0125;
        double phaseLow = t * 0.65;
        double phaseMid = t * 1.05;
        double phaseHigh = t * 1.55;
        double breath = 0.75 + (0.25 * Math.sin(t * 0.28));
        int baseY = (int) Math.round(GameConfig.HEIGHT * WAVEFORM_BASE_Y_RATIO);
        int alpha = clampAlpha(32 + (int) Math.round(90 * waveformEnergy));

        Color lineColor = new Color(WAVEFORM_COLOR.getRed(), WAVEFORM_COLOR.getGreen(), WAVEFORM_COLOR.getBlue(), alpha);
        Stroke oldStroke = g2d.getStroke();

        for (int pass = 0; pass < WAVEFORM_TRAIL_COUNT; pass++) {
            double trailRatio = pass / (double) Math.max(1, WAVEFORM_TRAIL_COUNT - 1);
            double trailOffset = (trailRatio - 0.5) * WAVEFORM_TRAIL_SPACING;
            double trailEnergy = waveformEnergy * (1.0 - (trailRatio * 0.4));
            int trailAlpha = clampAlpha(alpha - (int) Math.round(22 * trailRatio));

            Color trailGlow = new Color(WAVEFORM_COLOR.getRed(), WAVEFORM_COLOR.getGreen(), WAVEFORM_COLOR.getBlue(), Math.max(0, trailAlpha - 20));
            g2d.setStroke(new BasicStroke((float) (3.6 + (trailEnergy * 2.2)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(trailGlow);
            drawWaveformCurve(g2d, baseY + trailOffset, amplitude * (0.92 + (trailRatio * 0.1)), lowFreq, midFreq, highFreq,
                    phaseLow + (trailRatio * 0.2), phaseMid - (trailRatio * 0.18), phaseHigh + (trailRatio * 0.12), breath);

            g2d.setStroke(new BasicStroke((float) (1.9 + (trailEnergy * 1.0)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), trailAlpha));
            drawWaveformCurve(g2d, baseY + trailOffset, amplitude * (0.92 + (trailRatio * 0.1)), lowFreq, midFreq, highFreq,
                    phaseLow + (trailRatio * 0.2), phaseMid - (trailRatio * 0.18), phaseHigh + (trailRatio * 0.12), breath);
        }

        g2d.setStroke(oldStroke);
    }

    private int clampAlpha(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }

    private void drawWaveformCurve(
            Graphics2D g2d,
            double baseY,
            double amplitude,
            double lowFreq,
            double midFreq,
            double highFreq,
            double phaseLow,
            double phaseMid,
            double phaseHigh,
            double breath
    ) {
        int prevX = 0;
        double prevXNorm = 0.0;
        double prevLeftBias = Math.pow(1.0 - prevXNorm, 0.6);
        double prevMidBias = 1.0 - Math.abs(prevXNorm - 0.55) * 1.6;
        prevMidBias = Math.max(0.0, prevMidBias);
        double low = Math.sin((prevX * lowFreq) + phaseLow);
        double mid = Math.sin((prevX * midFreq) - phaseMid);
        double high = Math.sin((prevX * highFreq) + phaseHigh);
        double lowAmp = amplitude * (0.45 + (0.95 * prevLeftBias));
        double midAmp = amplitude * (0.22 + (0.55 * prevMidBias));
        double highAmp = amplitude * 0.10;
        int prevY = (int) Math.round(baseY + ((low * lowAmp + mid * midAmp + high * highAmp) * breath));

        for (int x = WAVEFORM_STEP; x <= GameConfig.WIDTH; x += WAVEFORM_STEP) {
            double xNorm = x / (double) GameConfig.WIDTH;
            double leftBias = Math.pow(1.0 - xNorm, 0.6);
            double midBias = 1.0 - Math.abs(xNorm - 0.55) * 1.6;
            midBias = Math.max(0.0, midBias);
            low = Math.sin((x * lowFreq) + phaseLow);
            mid = Math.sin((x * midFreq) - phaseMid);
            high = Math.sin((x * highFreq) + phaseHigh);
            lowAmp = amplitude * (0.45 + (0.95 * leftBias));
            midAmp = amplitude * (0.22 + (0.55 * midBias));
            highAmp = amplitude * 0.10;
            int y = (int) Math.round(baseY + ((low * lowAmp + mid * midAmp + high * highAmp) * breath));
            g2d.drawLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }
    }

    private void drawWarpedGrid(Graphics2D g2d, long nowMs) {
        g2d.setColor(new Color(63, 126, 205, 28));
        double[] warp = new double[2];
        int step = 10;

        for (int y = 0; y <= GameConfig.HEIGHT; y += BACKDROP_GRID_SPACING) {
            int prevX = 0;
            calculateRippleWarp(0, y, nowMs, warp);
            int prevY = (int) Math.round(y + warp[1]);
            for (int x = step; x <= GameConfig.WIDTH; x += step) {
                calculateRippleWarp(x, y, nowMs, warp);
                int warpedX = (int) Math.round(x + warp[0]);
                int warpedY = (int) Math.round(y + warp[1]);
                g2d.drawLine(prevX, prevY, warpedX, warpedY);
                prevX = warpedX;
                prevY = warpedY;
            }
        }

        for (int x = 0; x <= GameConfig.WIDTH; x += BACKDROP_GRID_SPACING) {
            int prevY = 0;
            calculateRippleWarp(x, 0, nowMs, warp);
            int prevX = (int) Math.round(x + warp[0]);
            for (int y = step; y <= GameConfig.HEIGHT; y += step) {
                calculateRippleWarp(x, y, nowMs, warp);
                int warpedX = (int) Math.round(x + warp[0]);
                int warpedY = (int) Math.round(y + warp[1]);
                g2d.drawLine(prevX, prevY, warpedX, warpedY);
                prevX = warpedX;
                prevY = warpedY;
            }
        }
    }

    private void calculateRippleWarp(double x, double y, long nowMs, double[] outWarp) {
        double warpX = 0.0;
        double warpY = 0.0;

        for (BackgroundRipple ripple : backgroundRipples) {
            long elapsedMs = nowMs - ripple.startMs;
            if (elapsedMs < 0 || elapsedMs > ripple.durationMs) {
                continue;
            }

            double progress = elapsedMs / (double) ripple.durationMs;
            double life = 1.0 - progress;
            double dx = x - ripple.x;
            double dy = y - ripple.y;
            double distance = Math.sqrt((dx * dx) + (dy * dy));
            if (distance < 0.0001) {
                continue;
            }

            double radius = 14.0 + (ripple.maxRadius * progress);
            double bandDistance = Math.abs(distance - radius);
            if (bandDistance > ripple.bandWidth * 1.15) {
                continue;
            }

            double bandFalloff = 1.0 - (bandDistance / (ripple.bandWidth * 1.15));
            bandFalloff = Math.pow(Math.max(0.0, bandFalloff), 1.35);
            double phase = (distance / ripple.wavelength) - (progress * 15.8) + ripple.phase;
            double wave = Math.sin(phase);
            double swirl = Math.cos((phase * 0.68) + 0.6);

            double nx = dx / distance;
            double ny = dy / distance;
            double amplitude = ripple.strength * (0.34 + (0.66 * life)) * bandFalloff;
            warpX += (nx * wave * amplitude) + (-ny * swirl * amplitude * 0.55);
            warpY += (ny * wave * amplitude) + (nx * swirl * amplitude * 0.55);
        }

        for (HueSweepRipple ripple : hueSweepRipples) {
            long elapsedMs = nowMs - ripple.startMs;
            if (elapsedMs < 0 || elapsedMs > ripple.durationMs) {
                continue;
            }

            double progress = elapsedMs / (double) ripple.durationMs;
            double life = 1.0 - progress;
            double eased = 1.0 - Math.pow(1.0 - progress, 2.1);
            double frontX = ripple.startX + ((ripple.endX - ripple.startX) * eased);
            double distance = Math.abs(x - frontX);
            if (distance > ripple.bandWidth * 1.2) {
                continue;
            }

            double bandFalloff = 1.0 - (distance / (ripple.bandWidth * 1.2));
            bandFalloff = Math.pow(Math.max(0.0, bandFalloff), 1.45);
            double phase = (y / ripple.wavelength) - (progress * 15.0) + ripple.phase;
            double wave = Math.sin(phase);
            double swirl = Math.cos((phase * 0.72) + 0.45);
            double direction = ripple.endX >= ripple.startX ? 1.0 : -1.0;
            double amplitude = ripple.strength * (0.32 + (0.68 * life)) * bandFalloff;

            warpX += direction * ((wave * amplitude) + (swirl * amplitude * 0.30));
            warpY += swirl * amplitude * 0.22;
        }

        double magnitude = Math.sqrt((warpX * warpX) + (warpY * warpY));
        if (magnitude > 44.0) {
            double scale = 44.0 / magnitude;
            warpX *= scale;
            warpY *= scale;
        }

        outWarp[0] = warpX;
        outWarp[1] = warpY;
    }

    private void spawnBackdropRippleAt(
            int x,
            int y,
            int minRadius,
            int radiusRange,
            int minBandWidth,
            int bandWidthRange,
            double minStrength,
            double strengthRange,
            double minWavelength,
            double wavelengthRange,
            long minDurationMs,
            int durationRangeMs
    ) {
        BackgroundRipple ripple = new BackgroundRipple();
        ripple.x = x;
        ripple.y = y;
        ripple.maxRadius = minRadius + random.nextInt(Math.max(1, radiusRange));
        ripple.bandWidth = minBandWidth + random.nextInt(Math.max(1, bandWidthRange));
        ripple.strength = minStrength + (random.nextDouble() * strengthRange);
        ripple.wavelength = minWavelength + (random.nextDouble() * wavelengthRange);
        ripple.phase = random.nextDouble() * Math.PI * 2.0;
        ripple.startMs = System.currentTimeMillis();
        ripple.durationMs = minDurationMs + random.nextInt(Math.max(1, durationRangeMs));
        backgroundRipples.add(ripple);

        while (backgroundRipples.size() > RIPPLE_MAX_COUNT) {
            backgroundRipples.remove(0);
        }
    }

    private int findRippleSequenceIndex(List<Integer> sequence, int progressIndex, int symbol) {
        int size = sequence.size();
        if (size == 0) {
            return -1;
        }

        int clampedProgress = Math.max(0, Math.min(progressIndex, size - 1));
        if (sequence.get(clampedProgress) == symbol) {
            return clampedProgress;
        }
        for (int i = clampedProgress + 1; i < size; i++) {
            if (sequence.get(i) == symbol) {
                return i;
            }
        }
        for (int i = clampedProgress - 1; i >= 0; i--) {
            if (sequence.get(i) == symbol) {
                return i;
            }
        }
        return clampedProgress;
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
