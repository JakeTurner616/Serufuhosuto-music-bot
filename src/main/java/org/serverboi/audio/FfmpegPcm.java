package org.serverboi.audio;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class FfmpegPcm {
    private FfmpegPcm() {}

    public static Process start(String ffmpegPath, String streamUrl, String tag) throws Exception {
        Process p = new ProcessBuilder(
                ffmpegPath,
                "-hide_banner",
                "-nostdin",

                // reconnect helps with googlevideo transient resets
                "-reconnect", "1",
                "-reconnect_streamed", "1",
                "-reconnect_delay_max", "5",

                "-i", streamUrl,
                "-vn",

                // ✅ JDA raw PCM expects little-endian
                "-f", "s16le",
                "-ar", "48000",
                "-ac", "2",

                // keep logs readable; change to "warning" if you want more
                "-loglevel", "error",
                "pipe:1"
        ).start();

        // ✅ Drain stderr so FFmpeg can't block AND so you can see why it ended
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.err.println("[FFMPEG " + tag + "] " + line);
                }
            } catch (Exception ignored) {}
        }, "FFmpeg-stderr-" + tag);
        t.setDaemon(true);
        t.start();

        // Log exit code when it finishes (super useful for the “ended immediately” case)
        Thread w = new Thread(() -> {
            try {
                int code = p.waitFor();
                System.out.println("[FFMPEG " + tag + "] exit=" + code);
            } catch (InterruptedException ignored) {}
        }, "FFmpeg-wait-" + tag);
        w.setDaemon(true);
        w.start();

        return p;
    }
}
