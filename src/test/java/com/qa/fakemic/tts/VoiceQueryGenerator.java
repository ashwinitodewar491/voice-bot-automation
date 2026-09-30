package com.qa.fakemic.tts;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Turns a text query into a WAV file that Chromium can play through its fake microphone.
 *
 * <p>Pipeline: text → edge-tts (free Microsoft neural voices, MP3) → ffmpeg (16 kHz mono PCM WAV,
 * padded with silence) → temp file. Call {@link #delete(Path)} when the test is done with it.
 *
 * <p>Requirements on PATH: {@code python} with {@code pip install edge-tts}, and {@code ffmpeg}.
 */
public final class VoiceQueryGenerator {

    public static final String DEFAULT_VOICE = "en-US-AriaNeural";

    /** Silence before speech, so the page has time to open the mic before the first word plays. */
    private static final int LEAD_SILENCE_MS = 1500;

    /** Silence after speech, so voice-activity detection sees the user "stop talking". */
    private static final int TRAIL_SILENCE_MS = 3000;

    private VoiceQueryGenerator() {
    }

    /** Generates a WAV for {@code text} using {@link #DEFAULT_VOICE}. */
    public static Path generate(String text) throws Exception {
        return generate(text, DEFAULT_VOICE);
    }

    /**
     * Generates a WAV for {@code text} spoken by {@code voice} (any edge-tts voice name,
     * e.g. {@code en-GB-RyanNeural}, {@code hi-IN-SwaraNeural}; list them with
     * {@code python -m edge_tts --list-voices}).
     */
    public static Path generate(String text, String voice) throws Exception {
        Path txtFile = Files.createTempFile("voice_query_", ".txt");
        Path mp3File = Files.createTempFile("voice_query_", ".mp3");
        Path wavFile = Files.createTempFile("voice_query_", ".wav");
        Files.writeString(txtFile, text, StandardCharsets.UTF_8);

        try {
            // "python -m edge_tts" instead of the edge-tts.exe stub: Windows Smart App Control can
            // block unsigned pip console-script wrappers. Text goes via a UTF-8 file, not --text,
            // to avoid command-line encoding problems with non-ASCII queries.
            run("edge-tts synthesis failed for: " + text,
                    "python", "-m", "edge_tts", "--voice", voice,
                    "--file", txtFile.toString(),
                    "--write-media", mp3File.toString());

            run("ffmpeg conversion failed for: " + text,
                    "ffmpeg", "-y", "-loglevel", "error", "-i", mp3File.toString(),
                    "-af", "adelay=" + LEAD_SILENCE_MS + ":all=1,apad=pad_dur=" + TRAIL_SILENCE_MS + "ms",
                    "-ar", "16000", "-ac", "1", "-acodec", "pcm_s16le",
                    wavFile.toString());
        } catch (Exception e) {
            Files.deleteIfExists(wavFile);
            throw e;
        } finally {
            Files.deleteIfExists(txtFile);
            Files.deleteIfExists(mp3File);
        }
        return wavFile;
    }

    /** Length of a WAV produced by {@link #generate} (16 kHz, mono, 16-bit → 32,000 bytes/second). */
    public static long durationMs(Path wavFile) throws Exception {
        return (Files.size(wavFile) - 44) * 1000 / 32_000;
    }

    /** Deletes a generated WAV. Safe to call with {@code null} or an already-deleted path. */
    public static void delete(Path wavFile) {
        if (wavFile == null) return;
        try {
            Files.deleteIfExists(wavFile);
        } catch (Exception e) {
            // Chromium can still hold the file open for a moment on Windows; don't fail the test.
            wavFile.toFile().deleteOnExit();
        }
    }

    private static void run(String errorMessage, String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException(errorMessage + "\n" + output);
        }
    }
}
