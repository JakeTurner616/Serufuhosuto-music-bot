package org.serverboi.audio;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FfmpegPcm {
    private FfmpegPcm() {}

    public static Process start(String ffmpegPath, String streamUrl, String tag) throws Exception {
        return start(ffmpegPath, streamUrl, Map.of(), tag);
    }

    public static Process start(String ffmpegPath, String streamUrl, Map<String, String> headers, String tag) throws Exception {
        List<String> args = new ArrayList<>();
        args.add(ffmpegPath);
        args.add("-hide_banner");
        args.add("-nostdin");

        // Reconnect helps with googlevideo transient resets.
        args.add("-reconnect");
        args.add("1");
        args.add("-reconnect_streamed");
        args.add("1");
        args.add("-reconnect_delay_max");
        args.add("5");

        if (headers != null && !headers.isEmpty()) {
            String userAgent = headers.get("User-Agent");
            if (userAgent != null && !userAgent.isBlank()) {
                args.add("-user_agent");
                args.add(userAgent);
            }

            StringBuilder headerText = new StringBuilder();
            headers.forEach((name, value) -> {
                if (name != null && value != null && !name.isBlank() && !value.isBlank()) {
                    headerText.append(name).append(": ").append(value).append("\r\n");
                }
            });
            if (!headerText.isEmpty()) {
                args.add("-headers");
                args.add(headerText.toString());
            }
        }

        args.add("-i");
        args.add(streamUrl);
        args.add("-vn");

        // JDA AudioSendHandler expects signed 16-bit stereo 48kHz big-endian PCM.
        args.add("-f");
        args.add("s16be");
        args.add("-ar");
        args.add("48000");
        args.add("-ac");
        args.add("2");

        args.add("-loglevel");
        args.add("error");
        args.add("pipe:1");

        Process p = new ProcessBuilder(args).start();

        Thread stderr = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("[FFMPEG " + tag + "] " + line);
                }
            } catch (Exception ignored) {}
        }, "FFmpeg-stderr-" + tag);
        stderr.setDaemon(true);
        stderr.start();

        Thread waiter = new Thread(() -> {
            try {
                int code = p.waitFor();
                System.out.println("[FFMPEG " + tag + "] exit=" + code);
            } catch (InterruptedException ignored) {}
        }, "FFmpeg-wait-" + tag);
        waiter.setDaemon(true);
        waiter.start();

        return p;
    }
}
