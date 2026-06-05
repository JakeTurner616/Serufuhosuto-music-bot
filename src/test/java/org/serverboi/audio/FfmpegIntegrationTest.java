package org.serverboi.audio;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class FfmpegIntegrationTest {

    private static final String YTDLP_EXECUTABLE = resolveYtDlpExecutable();
    private static final String FFMPEG_EXECUTABLE = "ffmpeg";
    private static final String VALID_YOUTUBE_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    private record StreamInfo(String url, Map<String, String> headers) {}

    private static String resolveYtDlpExecutable() {
        Path local = Path.of("tools", "yt-dlp.exe");
        return Files.exists(local) ? local.toString() : "yt-dlp";
    }

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
        StreamInfo streamInfo = fetchStreamInfo(VALID_YOUTUBE_URL);
        assertNotNull(streamInfo, "yt-dlp should return stream info");
        assertNotNull(streamInfo.url(), "yt-dlp should return a stream URL");

        Process ffmpegProc = startShortFfmpegSample(streamInfo);

        Future<byte[]> readTask = Executors.newSingleThreadExecutor().submit(() -> {
            try (InputStream audioStream = ffmpegProc.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[2048];
                while (true) {
                    int n = audioStream.read(buf);
                    if (n == -1) break;
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        });

        assertTrue(ffmpegProc.waitFor(10, TimeUnit.SECONDS), "ffmpeg should finish within timeout");
        assertEquals(0, ffmpegProc.exitValue(), "ffmpeg should exit successfully");
        byte[] audioData = readTask.get(8, TimeUnit.SECONDS);
        assertTrue(audioData.length > 0, "ffmpeg should produce audio data");
    }

    private Process startShortFfmpegSample(StreamInfo streamInfo) throws Exception {
        List<String> args = new ArrayList<>();
        args.add(FFMPEG_EXECUTABLE);
        args.add("-hide_banner");
        args.add("-nostdin");

        String userAgent = streamInfo.headers().get("User-Agent");
        if (userAgent != null && !userAgent.isBlank()) {
            args.add("-user_agent");
            args.add(userAgent);
        }

        StringBuilder headerText = new StringBuilder();
        streamInfo.headers().forEach((name, value) -> {
            if (name != null && value != null && !name.isBlank() && !value.isBlank()) {
                headerText.append(name).append(": ").append(value).append("\r\n");
            }
        });
        if (!headerText.isEmpty()) {
            args.add("-headers");
            args.add(headerText.toString());
        }

        args.add("-i");
        args.add(streamInfo.url());
        args.add("-vn");
        args.add("-f");
        args.add("s16be");
        args.add("-ar");
        args.add("48000");
        args.add("-ac");
        args.add("2");
        args.add("-t");
        args.add("0.5");
        args.add("-loglevel");
        args.add("error");
        args.add("pipe:1");
        return new ProcessBuilder(args).start();
    }

    @Test
    public void testFfmpegFailsGracefullyOnInvalidUrl() {
        Exception exception = assertThrows(Exception.class, () -> {
            Process proc = new ProcessBuilder(
                    FFMPEG_EXECUTABLE,
                    "-i", "https://invalid.url/stream",
                    "-f", "s16be", "-ar", "48000", "-ac", "2",
                    "-t", "0.5", "-loglevel", "error", "pipe:1"
            ).start();
            if (!proc.waitFor(5, TimeUnit.SECONDS)) proc.destroyForcibly();
            if (proc.exitValue() != 0) {
                throw new RuntimeException("ffmpeg failed with exit code " + proc.exitValue());
            }
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

    private StreamInfo fetchStreamInfo(String url) throws Exception {
        Process ytProc = new ProcessBuilder(
                YTDLP_EXECUTABLE,
                "--no-warnings",
                "--quiet",
                "--no-playlist",
                "-f", "bestaudio[ext=webm]/bestaudio/bestaudio[ext=m4a]",
                "--print", "%(url)s",
                "--print", "%(http_headers)j",
                url
        ).redirectErrorStream(true).start();

        String streamUrl = null;
        Map<String, String> headers = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ytProc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("http") && streamUrl == null) {
                    streamUrl = line;
                } else if (line.startsWith("{") && line.endsWith("}")) {
                    JSONObject json = new JSONObject(line);
                    for (String key : json.keySet()) {
                        String value = json.optString(key, "");
                        if (!value.isBlank()) {
                            headers.put(key, value);
                        }
                    }
                }
            }
        }

        assertTrue(ytProc.waitFor(20, TimeUnit.SECONDS), "yt-dlp should finish within timeout");
        assertEquals(0, ytProc.exitValue(), "yt-dlp should exit successfully");
        return streamUrl == null ? null : new StreamInfo(streamUrl, headers);
    }
}
