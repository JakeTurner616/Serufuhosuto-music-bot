package org.serverboi.audio;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class FfmpegIntegrationTest {

    private static final String YTDLP_EXECUTABLE = "yt-dlp";
    private static final String FFMPEG_EXECUTABLE = "ffmpeg";
    private static final String VALID_YOUTUBE_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"; // test video

    @Test
    public void testFfmpegIsInstalled() throws Exception {
        Process proc = new ProcessBuilder(FFMPEG_EXECUTABLE, "-version")
                .redirectErrorStream(true)
                .start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String versionLine = reader.readLine();
            assertEquals(0, proc.waitFor(), "ffmpeg should exit successfully");
            assertNotNull(versionLine);
            assertTrue(versionLine.toLowerCase().contains("ffmpeg"));
        }
    }

    @Test
    public void testFfmpegCanStreamFromUrl() throws Exception {
        // Ask yt-dlp to emit a direct audio URL in JSON form for maximum compatibility
        Process ytProc = new ProcessBuilder(
                YTDLP_EXECUTABLE,
                "--no-warnings",
                "--dump-json",
                "--geo-bypass",
                "--force-ipv4",
                "--no-playlist",
                "-f", "bestaudio",
                VALID_YOUTUBE_URL
        ).redirectErrorStream(true).start();

        StringBuilder ytOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ytProc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) ytOutput.append(line).append("\n");
        }

        ytProc.waitFor(15, TimeUnit.SECONDS);
        String json = ytOutput.toString().trim();
        assertFalse(json.isBlank(), "yt-dlp output should not be blank");

        // Extract URL manually (don’t rely on fragile line match)
        String streamUrl = null;
        for (String token : json.split("\"")) {
            if (token.startsWith("http") && token.contains("googlevideo.com")) {
                streamUrl = token;
                break;
            }
        }

        if (streamUrl == null) {
            System.err.println("yt-dlp output:\n" + json);
        }

        assertNotNull(streamUrl, "yt-dlp should return a stream URL");

        // Stream a short sample from ffmpeg with timeout and safety
        Process ffmpegProc = new ProcessBuilder(
                FFMPEG_EXECUTABLE,
                "-hide_banner",
                "-reconnect", "1",
                "-reconnect_streamed", "1",
                "-reconnect_delay_max", "5",
                "-i", streamUrl,
                "-vn",
                "-f", "s16le",
                "-ar", "48000",
                "-ac", "2",
                "-t", "0.5",
                "-loglevel", "error",
                "pipe:1"
        ).start();

        Future<byte[]> readTask = Executors.newSingleThreadExecutor().submit(() -> {
            try (InputStream audioStream = ffmpegProc.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[2048];
                int n;
                while ((n = audioStream.read(buf)) != -1)
                    out.write(buf, 0, n);
                return out.toByteArray();
            }
        });

        boolean finished = ffmpegProc.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            ffmpegProc.destroyForcibly();
            fail("ffmpeg process timed out");
        }

        byte[] audioData = readTask.get(3, TimeUnit.SECONDS);
        assertTrue(audioData.length > 0, "ffmpeg should produce audio data");
    }

    @Test
    public void testFfmpegFailsGracefullyOnInvalidUrl() {
        Exception exception = assertThrows(Exception.class, () -> {
            Process proc = new ProcessBuilder(
                    FFMPEG_EXECUTABLE,
                    "-i", "https://invalid.url/stream",
                    "-f", "s16le", "-ar", "48000", "-ac", "2",
                    "-t", "0.5", "-loglevel", "error", "pipe:1"
            ).start();
            if (!proc.waitFor(5, TimeUnit.SECONDS)) proc.destroyForcibly();
            if (proc.exitValue() != 0)
                throw new RuntimeException("ffmpeg failed with exit code " + proc.exitValue());
        });
        assertTrue(exception.getMessage().contains("ffmpeg failed"));
    }

    @Test
    public void testMissingFfmpegBinaryFails() {
        Exception exception = assertThrows(Exception.class, () -> {
            new ProcessBuilder("ffmpeg-missing", "-version").start();
        });
        assertTrue(
                exception.getMessage().toLowerCase().contains("cannot run") ||
                exception.getMessage().toLowerCase().contains("no such file")
        );
    }
}
