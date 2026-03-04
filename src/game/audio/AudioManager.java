package game.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ThreadLocalRandom;

public final class AudioManager {
    private static Clip musicClip;
    private static String currentMusicFile;
    private static final float DEFAULT_MUSIC_GAIN_DB = -5.0f;
    private static final float DEFAULT_SFX_GAIN_DB = -3.0f;
    private static final float CLICK_GAIN_JITTER_DB = 2.0f;
    private static final double CLICK_RATE_JITTER = 0.055;
    private static final float CLICK_PAN_JITTER = 0.28f;

    private AudioManager() {
    }

    public static synchronized void startBackgroundLoop(String fileName) {
        ensureBackgroundLoop(fileName);
    }

    public static synchronized void ensureBackgroundLoop(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        if (musicClip != null && musicClip.isOpen() && fileName.equals(currentMusicFile)) {
            if (!musicClip.isRunning()) {
                musicClip.loop(Clip.LOOP_CONTINUOUSLY);
                musicClip.start();
            }
            return;
        }

        stopBackgroundLoop();

        URL url = findAudioUrl(fileName);
        if (url == null) {
            System.err.println("Could not find music file: " + fileName);
            return;
        }

        try (AudioInputStream stream = openPlayableStream(url)) {
            musicClip = AudioSystem.getClip();
            musicClip.open(stream);
            applyGain(musicClip, DEFAULT_MUSIC_GAIN_DB);
            musicClip.loop(Clip.LOOP_CONTINUOUSLY);
            musicClip.start();
            currentMusicFile = fileName;
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            musicClip = null;
            currentMusicFile = null;
            System.err.println("Failed to start background loop: " + fileName);
            e.printStackTrace();
        }
    }

    public static synchronized void stopBackgroundLoop() {
        if (musicClip != null) {
            musicClip.stop();
            musicClip.close();
            musicClip = null;
        }
        currentMusicFile = null;
    }

    public static void playSfx(String fileName) {
        playSfxModulated(fileName, DEFAULT_SFX_GAIN_DB, 0.0f, 0.0, 0.0f);
    }

    public static void playClickSfx() {
        playSfxModulated(
                "click.wav",
                DEFAULT_SFX_GAIN_DB,
                CLICK_GAIN_JITTER_DB,
                CLICK_RATE_JITTER,
                CLICK_PAN_JITTER
        );
    }

    private static void playSfxModulated(
            String fileName,
            float baseGainDb,
            float gainJitterDb,
            double rateJitter,
            float panJitter
    ) {
        URL url = findAudioUrl(fileName);
        if (url == null) {
            return;
        }
        try (AudioInputStream stream = openPlayableStream(url)) {
            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            float randomGain = baseGainDb;
            if (gainJitterDb > 0.0f) {
                randomGain += randomSignedFloat(gainJitterDb);
            }
            applyGain(clip, randomGain);
            applyRateJitter(clip, rateJitter);
            applyPan(clip, panJitter > 0.0f ? randomSignedFloat(panJitter) : 0.0f);
            clip.addLineListener(event -> {
                if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException ignored) {
            // Keep gameplay resilient if a sound fails.
        }
    }

    private static AudioInputStream openPlayableStream(URL url) throws UnsupportedAudioFileException, IOException {
        AudioInputStream sourceStream = AudioSystem.getAudioInputStream(url);
        AudioFormat source = sourceStream.getFormat();

        AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                source.getSampleRate(),
                16,
                source.getChannels(),
                source.getChannels() * 2,
                source.getSampleRate(),
                false
        );

        if (AudioSystem.isConversionSupported(target, source)) {
            return AudioSystem.getAudioInputStream(target, sourceStream);
        }
        return sourceStream;
    }

    private static float randomSignedFloat(float range) {
        return (float) ((ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * range);
    }

    private static void applyGain(Clip clip, float gainDb) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float clamped = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), gainDb));
        gainControl.setValue(clamped);
    }

    private static void applyRateJitter(Clip clip, double jitterRatio) {
        if (jitterRatio <= 0.0 || !clip.isControlSupported(FloatControl.Type.SAMPLE_RATE)) {
            return;
        }
        FloatControl rateControl = (FloatControl) clip.getControl(FloatControl.Type.SAMPLE_RATE);
        double factor = 1.0 + ((ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * jitterRatio);
        float targetRate = (float) (rateControl.getValue() * factor);
        float clampedRate = Math.max(rateControl.getMinimum(), Math.min(rateControl.getMaximum(), targetRate));
        rateControl.setValue(clampedRate);
    }

    private static void applyPan(Clip clip, float panValue) {
        if (!clip.isControlSupported(FloatControl.Type.PAN)) {
            return;
        }
        FloatControl panControl = (FloatControl) clip.getControl(FloatControl.Type.PAN);
        float clamped = Math.max(panControl.getMinimum(), Math.min(panControl.getMaximum(), panValue));
        panControl.setValue(clamped);
    }

    private static URL findAudioUrl(String fileName) {
        URL classpathUrl = AudioManager.class.getClassLoader().getResource("assets/sfx/" + fileName);
        if (classpathUrl != null) {
            return classpathUrl;
        }

        File[] candidates = {
                new File("src/assets/sfx/" + fileName),
                new File("assets/sfx/" + fileName)
        };

        for (File file : candidates) {
            if (!file.isFile()) {
                continue;
            }
            try {
                return file.toURI().toURL();
            } catch (MalformedURLException ignored) {
                return null;
            }
        }
        return null;
    }
}
