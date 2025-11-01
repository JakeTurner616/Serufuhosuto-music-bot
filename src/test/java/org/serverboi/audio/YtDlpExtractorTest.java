package org.serverboi.audio;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class YtDlpExtractorTest {

    private static final String VALID_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String YTDLP_EXECUTABLE = "yt-dlp";

    @Test
    public void testYtDlpIsInstalled() throws Exception {
        Process proc = new ProcessBuilder(YTDLP_EXECUTABLE, "--version")
                .redirectErrorStream(true)
                .start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String versionLine = reader.readLine();
        int exitCode = proc.waitFor();

        assertEquals(0, exitCode, "yt-dlp should exit cleanly");
        assertNotNull(versionLine, "yt-dlp version output should not be null");
        assertFalse(versionLine.isBlank(), "yt-dlp version output should not be blank");
    }

    @Test
    public void testValidUrlReturnsStreamUrl() throws Exception {
        Process proc = new ProcessBuilder(
                YTDLP_EXECUTABLE,
                "-f", "bestaudio/best",
                "--get-url",
                VALID_URL
        ).redirectErrorStream(true).start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line.trim());
        }
        int exitCode = proc.waitFor();

        // Find the first valid URL among all lines
        String streamUrl = lines.stream()
                .filter(l -> l.startsWith("http"))
                .findFirst()
                .orElse(null);

        System.out.println("yt-dlp output:\n" + String.join("\n", lines));

        assertEquals(0, exitCode, "yt-dlp should succeed");
        assertNotNull(streamUrl, "yt-dlp should return a stream URL");
        assertTrue(streamUrl.startsWith("http"), "yt-dlp output should be a valid URL");
    }

    @Test
    public void testTitleExtractionFromSearch() throws Exception {
        Process proc = new ProcessBuilder(
                YTDLP_EXECUTABLE,
                "--no-playlist",
                "--print", "%(title)s",
                "ytsearch1:rick astley never gonna give you up"
        ).redirectErrorStream(true).start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String title = reader.readLine();
        int exitCode = proc.waitFor();

        System.out.println("yt-dlp title: " + title);

        assertEquals(0, exitCode, "yt-dlp title extraction should succeed");
        assertNotNull(title, "yt-dlp should return a title");
        assertFalse(title.isBlank(), "yt-dlp title should not be blank");
    }

    @Test
    public void testInvalidUrlFailsGracefully() {
        Exception exception = assertThrows(Exception.class, () -> {
            Process proc = new ProcessBuilder(
                    YTDLP_EXECUTABLE,
                    "-f", "bestaudio/best",
                    "--get-url",
                    "https://youtube.com/watch?v=invalid123"
            ).redirectErrorStream(true).start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            while (reader.readLine() != null);
            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("yt-dlp failed with exit code " + exitCode);
            }
        });

        assertTrue(exception.getMessage().contains("yt-dlp failed"), "Error should be from yt-dlp failure");
    }

    @Test
    public void testMissingBinaryFails() {
        Exception exception = assertThrows(Exception.class, () -> {
            new ProcessBuilder("yt-dlp-missing", "--version").start();
        });

        assertTrue(
                exception.getMessage().toLowerCase().contains("cannot run") ||
                exception.getMessage().toLowerCase().contains("no such file"),
                "Should detect missing yt-dlp"
        );
    }
}