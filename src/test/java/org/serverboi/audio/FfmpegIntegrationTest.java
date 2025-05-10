package org.serverboi.audio;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class FfmpegIntegrationTest {

    private static final String YTDLP_EXECUTABLE = "yt-dlp";
    private static final String FFMPEG_EXECUTABLE = "ffmpeg";
    private static final String VALID_YOUTUBE_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    /**
     * Verifies ffmpeg is installed and returns version info.
     */
    @Test
    public void testFfmpegIsInstalled() throws Exception {
        Process proc = new ProcessBuilder(FFMPEG_EXECUTABLE, "-version")
                .redirectErrorStream(true)
                .start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String versionLine = reader.readLine();
        int exitCode = proc.waitFor();

        assertEquals(0, exitCode, "ffmpeg should exit successfully");
        assertNotNull(versionLine, "ffmpeg version output should not be null");
        assertTrue(versionLine.toLowerCase().contains("ffmpeg"), "Should mention ffmpeg in the output");
    }

    /**
     * Full integration: yt-dlp resolves stream URL, ffmpeg connects and outputs raw audio.
     */
    @Test
    public void testFfmpegCanStreamFromUrl() throws Exception {
        // Get a valid audio stream URL using yt-dlp
        Process ytProc = new ProcessBuilder(YTDLP_EXECUTABLE, "-f", "bestaudio[ext=m4a]", "-g", VALID_YOUTUBE_URL)
                .redirectErrorStream(true)
                .start();

        BufferedReader ytReader = new BufferedReader(new InputStreamReader(ytProc.getInputStream()));
        String streamUrl = ytReader.readLine();
        ytProc.waitFor();

        assertNotNull(streamUrl, "yt-dlp should return a stream URL");
        assertTrue(streamUrl.startsWith("http"), "yt-dlp output should be a valid URL");

        // Use ffmpeg to stream and convert audio to 48kHz 16-bit PCM
        Process ffmpegProc = new ProcessBuilder(
                FFMPEG_EXECUTABLE,
                "-reconnect", "1",
                "-reconnect_streamed", "1",
                "-reconnect_delay_max", "5",
                "-i", streamUrl,
                "-vn",
                "-f", "s16be",
                "-ar", "48000",
                "-ac", "2",
                "-t", "1", // only read 1 second for testing
                "-loglevel", "error",
                "pipe:1"
        ).start();

        InputStream audioStream = ffmpegProc.getInputStream();
        byte[] buffer = new byte[3840]; // 20ms of PCM audio
        int bytesRead = audioStream.read(buffer);
        int exitCode = ffmpegProc.waitFor();

        assertEquals(0, exitCode, "ffmpeg should exit cleanly");
        assertTrue(bytesRead > 0, "ffmpeg should produce audio data");
    }

    /**
     * Simulates ffmpeg failure on bad input.
     */
    @Test
    public void testFfmpegFailsGracefullyOnInvalidUrl() {
        Exception exception = assertThrows(Exception.class, () -> {
            Process proc = new ProcessBuilder(
                    FFMPEG_EXECUTABLE,
                    "-i", "https://invalid.url/stream",
                    "-f", "s16be",
                    "-ar", "48000",
                    "-ac", "2",
                    "-t", "1",
                    "-loglevel", "error",
                    "pipe:1"
            ).start();

            InputStream error = proc.getInputStream();
            while (error.read() != -1); // drain
            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("ffmpeg failed with exit code " + exitCode);
            }
        });

        assertTrue(exception.getMessage().contains("ffmpeg failed"), "Should indicate ffmpeg failed");
    }

    /**
     * Simulates missing ffmpeg binary.
     */
    @Test
    public void testMissingFfmpegBinaryFails() {
        Exception exception = assertThrows(Exception.class, () -> {
            new ProcessBuilder("ffmpeg-missing", "-version").start();
        });

        assertTrue(exception.getMessage().toLowerCase().contains("cannot run") ||
                   exception.getMessage().toLowerCase().contains("no such file"), "Should detect missing ffmpeg");
    }
}
