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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class AudioManager {
    private static Clip musicClip;
    private static String currentMusicFile;
    private static Clip layeredBaseClip;
    private static Clip layeredLayerClip;
    private static Clip layeredAltClip;
    private static String currentLayeredBaseFile;
    private static String currentLayeredLayerFile;
    private static String currentLayeredAltFile;
    private static float layeredBaseMix = 1.0f;
    private static float layeredLayerMix = 0.0f;
    private static float layeredAltMix = 0.0f;
    private static final float DEFAULT_MUSIC_GAIN_DB = -5.0f;
    private static final float DEFAULT_SFX_GAIN_DB = -3.0f;
    private static final float ENCOUNTER_LOOP3_GAIN_DB = 5.0f;
    private static final float ENCOUNTER_LOOP4_GAIN_DB = 3.0f;
    private static final int ENVELOPE_BIN_MS = 33;
    private static final float CLICK_GAIN_JITTER_DB = 2.0f;
    private static final double CLICK_RATE_JITTER = 0.055;
    private static final float CLICK_PAN_JITTER = 0.28f;
    private static float masterVolume = 1.0f;
    private static float musicVolume = 1.0f;
    private static float sfxVolume = 1.0f;
    private static float backgroundFade = 1.0f;
    private static final Map<String, Envelope> envelopeCache = new HashMap<>();

    private static final class Envelope {
        private final float[] bins;
        private final int framesPerBin;

        private Envelope(float[] bins, int framesPerBin) {
            this.bins = bins;
            this.framesPerBin = framesPerBin;
        }
    }

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

        stopLayeredLoops();
        stopBackgroundLoop();

        URL url = findAudioUrl(fileName);
        if (url == null) {
            System.err.println("Could not find music file: " + fileName);
            return;
        }

        try (AudioInputStream stream = openPlayableStream(url)) {
            musicClip = AudioSystem.getClip();
            musicClip.open(stream);
            applyGain(musicClip, gainWithVolume(DEFAULT_MUSIC_GAIN_DB, masterVolume * musicVolume));
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

    public static synchronized void pauseBackgroundLoop() {
        if (musicClip != null) {
            musicClip.stop();
        }
    }

    public static synchronized void ensureLayeredLoops(String baseFile, String layerFile, String altFile) {
        if (baseFile == null || baseFile.isBlank() || layerFile == null || layerFile.isBlank()
                || altFile == null || altFile.isBlank()) {
            return;
        }

        boolean baseMatches = baseFile.equals(currentLayeredBaseFile);
        boolean layerMatches = layerFile.equals(currentLayeredLayerFile);
        boolean altMatches = altFile.equals(currentLayeredAltFile);
        boolean baseReady = isClipReady(layeredBaseClip);
        boolean layerReady = isClipReady(layeredLayerClip);
        boolean altReady = isClipReady(layeredAltClip);

        if (baseReady && layerReady && altReady && baseMatches && layerMatches && altMatches) {
            ensureLoopRunning(layeredBaseClip);
            ensureLoopRunning(layeredLayerClip);
            ensureLoopRunning(layeredAltClip);
            applyLayeredMix();
            return;
        }

        if (baseReady && altReady && baseMatches && altMatches) {
            if (!layerMatches || !layerReady) {
                Clip nextLayerClip = loadLoopClip(layerFile);
                if (nextLayerClip == null) {
                    System.err.println("Failed to load layered music file: " + layerFile);
                    return;
                }
                if (layeredLayerClip != null) {
                    layeredLayerClip.stop();
                    layeredLayerClip.close();
                }
                layeredLayerClip = nextLayerClip;
                layeredLayerClip.setFramePosition(0);
                applyLayeredMix();
                ensureLoopRunning(layeredBaseClip);
                ensureLoopRunning(layeredAltClip);
                layeredLayerClip.loop(Clip.LOOP_CONTINUOUSLY);
                layeredLayerClip.start();
                currentLayeredLayerFile = layerFile;
                return;
            }
        }

        stopBackgroundLoop();
        stopLayeredLoops();

        Clip baseClip = loadLoopClip(baseFile);
        Clip layerClip = loadLoopClip(layerFile);
        Clip altClip = loadLoopClip(altFile);
        if (baseClip == null || layerClip == null || altClip == null) {
            stopLayeredLoops();
            System.err.println("Failed to start layered loops: " + baseFile + " / " + layerFile + " / " + altFile);
            return;
        }

        layeredBaseClip = baseClip;
        layeredLayerClip = layerClip;
        layeredAltClip = altClip;
        layeredBaseClip.setFramePosition(0);
        layeredLayerClip.setFramePosition(0);
        layeredAltClip.setFramePosition(0);
        applyLayeredMix();
        layeredBaseClip.loop(Clip.LOOP_CONTINUOUSLY);
        layeredLayerClip.loop(Clip.LOOP_CONTINUOUSLY);
        layeredAltClip.loop(Clip.LOOP_CONTINUOUSLY);
        layeredBaseClip.start();
        layeredLayerClip.start();
        layeredAltClip.start();
        currentLayeredBaseFile = baseFile;
        currentLayeredLayerFile = layerFile;
        currentLayeredAltFile = altFile;
    }

    public static synchronized void setLayeredMix(float baseMix, float layerMix, float altMix) {
        layeredBaseMix = clampVolume(baseMix);
        layeredLayerMix = clampVolume(layerMix);
        layeredAltMix = clampVolume(altMix);
        applyLayeredMix();
    }

    public static synchronized void stopLayeredLoops() {
        if (layeredBaseClip != null) {
            layeredBaseClip.stop();
            layeredBaseClip.close();
            layeredBaseClip = null;
        }
        if (layeredLayerClip != null) {
            layeredLayerClip.stop();
            layeredLayerClip.close();
            layeredLayerClip = null;
        }
        if (layeredAltClip != null) {
            layeredAltClip.stop();
            layeredAltClip.close();
            layeredAltClip = null;
        }
        currentLayeredBaseFile = null;
        currentLayeredLayerFile = null;
        currentLayeredAltFile = null;
    }

    private static Clip loadLoopClip(String fileName) {
        URL url = findAudioUrl(fileName);
        if (url == null) {
            return null;
        }
        try (AudioInputStream stream = openPlayableStream(url)) {
            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            return clip;
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            return null;
        }
    }

    private static boolean isClipReady(Clip clip) {
        return clip != null && clip.isOpen();
    }

    private static void ensureLoopRunning(Clip clip) {
        if (clip == null || !clip.isOpen() || clip.isRunning()) {
            return;
        }
        clip.loop(Clip.LOOP_CONTINUOUSLY);
        clip.start();
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
            applyGain(clip, gainWithVolume(randomGain, masterVolume * sfxVolume));
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

    public static synchronized void setMasterVolume(float volume) {
        masterVolume = clampVolume(volume);
        applyMusicGain();
    }

    public static synchronized void setMusicVolume(float volume) {
        musicVolume = clampVolume(volume);
        applyMusicGain();
    }

    public static synchronized void setBackgroundFade(float fade) {
        backgroundFade = clampVolume(fade);
        applyMusicGain();
    }

    public static synchronized void setSfxVolume(float volume) {
        sfxVolume = clampVolume(volume);
    }

    public static synchronized float getMasterVolume() {
        return masterVolume;
    }

    public static synchronized float getMusicVolume() {
        return musicVolume;
    }

    public static synchronized float getSfxVolume() {
        return sfxVolume;
    }

    public static synchronized float getMusicEnergy() {
        if (musicClip != null && musicClip.isOpen() && currentMusicFile != null) {
            float energy = sampleEnvelope(currentMusicFile, musicClip);
            return clampVolume(energy * backgroundFade);
        }

        if (layeredBaseClip != null && layeredBaseClip.isOpen()) {
            float baseEnergy = sampleEnvelope(currentLayeredBaseFile, layeredBaseClip) * layeredBaseMix;
            float layerEnergy = sampleEnvelope(currentLayeredLayerFile, layeredLayerClip) * layeredLayerMix;
            float altEnergy = sampleEnvelope(currentLayeredAltFile, layeredAltClip) * layeredAltMix;
            return clampVolume(baseEnergy + layerEnergy + altEnergy);
        }

        return 0.0f;
    }

    private static void applyMusicGain() {
        if (musicClip == null || !musicClip.isOpen()) {
            applyLayeredMix();
            return;
        }
        applyGain(musicClip, gainWithVolume(DEFAULT_MUSIC_GAIN_DB, masterVolume * musicVolume * backgroundFade));
        applyLayeredMix();
    }

    private static void applyLayeredMix() {
        if (layeredBaseClip != null && layeredBaseClip.isOpen()) {
            float baseVolume = masterVolume * musicVolume * layeredBaseMix;
            applyGain(layeredBaseClip, gainWithVolume(DEFAULT_MUSIC_GAIN_DB, baseVolume));
        }
        if (layeredLayerClip != null && layeredLayerClip.isOpen()) {
            float layerVolume = masterVolume * musicVolume * layeredLayerMix;
            float layerGainDb = DEFAULT_MUSIC_GAIN_DB;
            if ("guitar_loop3.wav".equals(currentLayeredLayerFile)) {
                layerGainDb += ENCOUNTER_LOOP3_GAIN_DB;
            }
            if ("guitar_loop4.wav".equals(currentLayeredLayerFile)) {
                layerGainDb += ENCOUNTER_LOOP4_GAIN_DB;
            }
            applyGain(layeredLayerClip, gainWithVolume(layerGainDb, layerVolume));
        }
        if (layeredAltClip != null && layeredAltClip.isOpen()) {
            float altVolume = masterVolume * musicVolume * layeredAltMix;
            applyGain(layeredAltClip, gainWithVolume(DEFAULT_MUSIC_GAIN_DB, altVolume));
        }
    }

    private static float clampVolume(float volume) {
        return Math.max(0.0f, Math.min(1.0f, volume));
    }

    private static float sampleEnvelope(String fileName, Clip clip) {
        if (fileName == null || fileName.isBlank() || clip == null || !clip.isOpen()) {
            return 0.0f;
        }
        Envelope envelope = getEnvelope(fileName);
        if (envelope == null || envelope.bins.length == 0) {
            return 0.0f;
        }
        long framePosition = clip.getFramePosition();
        int index = (int) ((framePosition / envelope.framesPerBin) % envelope.bins.length);
        if (index < 0) {
            index += envelope.bins.length;
        }
        return envelope.bins[index];
    }

    private static Envelope getEnvelope(String fileName) {
        Envelope cached = envelopeCache.get(fileName);
        if (cached != null) {
            return cached;
        }

        URL url = findAudioUrl(fileName);
        if (url == null) {
            return null;
        }

        List<Float> bins = new ArrayList<>();
        int framesPerBin = 1;
        try (AudioInputStream stream = openPlayableStream(url)) {
            AudioFormat format = stream.getFormat();
            int frameSize = format.getFrameSize();
            if (frameSize <= 0) {
                return null;
            }
            framesPerBin = Math.max(1, Math.round(format.getFrameRate() * (ENVELOPE_BIN_MS / 1000.0f)));
            int bufferSize = framesPerBin * frameSize;
            byte[] buffer = new byte[bufferSize];
            boolean bigEndian = format.isBigEndian();

            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                int framesRead = bytesRead / frameSize;
                if (framesRead <= 0) {
                    continue;
                }
                int samples = 0;
                double sumSquares = 0.0;
                int limit = framesRead * frameSize;
                for (int i = 0; i + 1 < limit; i += 2) {
                    int sample;
                    if (bigEndian) {
                        sample = (short) ((buffer[i] << 8) | (buffer[i + 1] & 0xFF));
                    } else {
                        sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
                    }
                    sumSquares += (double) sample * sample;
                    samples++;
                }
                if (samples == 0) {
                    bins.add(0.0f);
                } else {
                    double rms = Math.sqrt(sumSquares / samples) / 32768.0;
                    bins.add((float) Math.min(1.0, rms));
                }
            }
        } catch (UnsupportedAudioFileException | IOException ignored) {
            return null;
        }

        float[] data = new float[bins.size()];
        for (int i = 0; i < bins.size(); i++) {
            data[i] = bins.get(i);
        }
        Envelope envelope = new Envelope(data, framesPerBin);
        envelopeCache.put(fileName, envelope);
        return envelope;
    }

    private static float gainWithVolume(float baseGainDb, float volume) {
        return baseGainDb + volumeToDb(volume);
    }

    private static float volumeToDb(float volume) {
        if (volume <= 0.0001f) {
            return -80.0f;
        }
        return (float) (20.0 * Math.log10(volume));
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
