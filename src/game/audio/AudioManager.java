package game.audio;

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

public final class AudioManager {
    private static Clip musicClip;
    private static final float DEFAULT_MUSIC_GAIN_DB = -5.0f;
    private static final float DEFAULT_SFX_GAIN_DB = -3.0f;

    private AudioManager() {
    }

    public static synchronized void startBackgroundLoop(String fileName) {
        if (musicClip != null && musicClip.isOpen()) {
            return;
        }

        URL url = findAudioUrl(fileName);
        if (url == null) {
            System.err.println("Could not find music file: " + fileName);
            return;
        }

        try (AudioInputStream stream = AudioSystem.getAudioInputStream(url)) {
            musicClip = AudioSystem.getClip();
            musicClip.open(stream);
            applyGain(musicClip, DEFAULT_MUSIC_GAIN_DB);
            musicClip.loop(Clip.LOOP_CONTINUOUSLY);
            musicClip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            musicClip = null;
            System.err.println("Failed to start background loop: " + fileName);
            e.printStackTrace();
        }
    }

    public static void playSfx(String fileName) {
        URL url = findAudioUrl(fileName);
        if (url == null) {
            return;
        }
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(url)) {
            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            applyGain(clip, DEFAULT_SFX_GAIN_DB);
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

    private static void applyGain(Clip clip, float gainDb) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float clamped = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), gainDb));
        gainControl.setValue(clamped);
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
