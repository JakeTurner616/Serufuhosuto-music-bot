// src/main/java/org/serverboi/commands/SeekCommand.java
package org.serverboi.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.serverboi.BotLauncher;
import org.serverboi.audio.AudioSessionManager;
import org.serverboi.audio.StreamSendHandler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SeekCommand extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        String prefix = BotLauncher.config.getString("prefix");

        if (!content.startsWith(prefix + "seek ")) return;

        String[] parts = content.split(" ");
        if (parts.length != 2) {
            event.getChannel().sendMessage("❌ Usage: " + prefix + "seek <seconds | MM:SS | HH:MM:SS>").queue();
            return;
        }

        int seconds;
        try {
            seconds = parseTimeToSeconds(parts[1]);
        } catch (IllegalArgumentException e) {
            event.getChannel().sendMessage("❌ Invalid time format. Use `<seconds>` or `MM:SS` or `HH:MM:SS`.").queue();
            return;
        }

        var np = AudioSessionManager.getNowPlaying(event.getGuild());
        String query = (np != null) ? np.query() : null;
        if (query == null) {
            event.getChannel().sendMessage("❌ No active track to seek in.").queue();
            return;
        }

        event.getChannel().sendTyping().queue();

        try {
            String ytDlp = BotLauncher.config.optString("ytDlpPath", "yt-dlp");
            String ffmpegBin = BotLauncher.config.optString("ffmpegPath", "ffmpeg");
            String quality = BotLauncher.config.getString("ytQuality");

            String streamUrl = fetchStreamUrl(ytDlp, quality, query);
            if (streamUrl == null) {
                event.getChannel().sendMessage("❌ Failed to fetch stream URL.").queue();
                return;
            }

            // stop old stream first
            AudioSessionManager.stop(event.getGuild());

            // IMPORTANT: JDA expects 48kHz stereo 16-bit BIG-ENDIAN PCM (s16be)
            Process ffmpeg = new ProcessBuilder(
                    ffmpegBin,
                    "-hide_banner",
                    "-reconnect", "1",
                    "-reconnect_streamed", "1",
                    "-reconnect_delay_max", "5",
                    "-ss", String.valueOf(seconds),
                    "-i", streamUrl,
                    "-vn",
                    "-f", "s16be",
                    "-ar", "48000",
                    "-ac", "2",
                    "-loglevel", "error",
                    "pipe:1"
            ).start();

            // Drain stderr so ffmpeg can't block
            drainAsync(ffmpeg.getErrorStream(), "FFmpeg-stderr");

            var audioManager = event.getGuild().getAudioManager();
            StreamSendHandler handler = new StreamSendHandler(ffmpeg.getInputStream(), () -> {
                AudioSessionManager.stop(event.getGuild());
                var next = AudioSessionManager.dequeue(event.getGuild());
                if (next != null) {
                    event.getChannel().sendMessage("⏭ Playing next: " + next.title()).queue();
                    tryStartNext(event, next);
                } else {
                    audioManager.setSendingHandler(null);
                    event.getChannel().sendMessage("⏹️ Queue finished.").queue();
                }
            });

            audioManager.setSendingHandler(handler);
            AudioSessionManager.register(event.getGuild(), ffmpeg, handler, np);

            event.getChannel().sendMessage("⏩ Seeking to `" + parts[1] + "`...").queue();

        } catch (Exception e) {
            event.getChannel().sendMessage("❌ Error seeking: " + e.getMessage()).queue();
        }
    }

    // Minimal “start next” helper so Seek doesn't depend on bot-sent messages.
    private void tryStartNext(MessageReceivedEvent event, AudioSessionManager.TrackRequest next) {
        try {
            String ytDlp = BotLauncher.config.optString("ytDlpPath", "yt-dlp");
            String ffmpegBin = BotLauncher.config.optString("ffmpegPath", "ffmpeg");
            String quality = BotLauncher.config.getString("ytQuality");

            String streamUrl = fetchStreamUrl(ytDlp, quality, next.query());
            if (streamUrl == null) return;

            Process ffmpeg = new ProcessBuilder(
                    ffmpegBin,
                    "-hide_banner",
                    "-reconnect", "1",
                    "-reconnect_streamed", "1",
                    "-reconnect_delay_max", "5",
                    "-i", streamUrl,
                    "-vn",
                    "-f", "s16be",
                    "-ar", "48000",
                    "-ac", "2",
                    "-loglevel", "error",
                    "pipe:1"
            ).start();

            drainAsync(ffmpeg.getErrorStream(), "FFmpeg-stderr");

            var audioManager = event.getGuild().getAudioManager();
            StreamSendHandler handler = new StreamSendHandler(ffmpeg.getInputStream(), () -> {
                AudioSessionManager.stop(event.getGuild());
                var queued = AudioSessionManager.dequeue(event.getGuild());
                if (queued != null) tryStartNext(event, queued);
                else {
                    audioManager.setSendingHandler(null);
                    event.getChannel().sendMessage("⏹️ Queue finished.").queue();
                }
            });

            audioManager.setSendingHandler(handler);
            AudioSessionManager.register(event.getGuild(), ffmpeg, handler, next);
            event.getChannel().sendMessage("🔊 Now streaming: " + next.title()).queue();

        } catch (Exception ignored) {
        }
    }

    /**
     * Fetch direct media URL from yt-dlp.
     * IMPORTANT: Do NOT merge stderr into stdout, otherwise warnings can pollute parsing.
     */
    private String fetchStreamUrl(String ytDlp, String quality, String query) {
        try {
            Process yt = new ProcessBuilder(
                    ytDlp,
                    "--no-warnings",
                    "--quiet",
                    "--no-playlist",
                    "-f", quality,
                    "-g", query
            ).start();

            // Drain stderr so yt-dlp can't block, but don't mix it into stdout
            drainAsync(yt.getErrorStream(), "yt-dlp-stderr");

            BufferedReader out = new BufferedReader(new InputStreamReader(yt.getInputStream()));
            String line;
            while ((line = out.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("http")) {
                    yt.waitFor();
                    return line;
                }
            }

            yt.waitFor();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void drainAsync(InputStream in, String threadName) {
        Thread t = new Thread(() -> {
            try (in) {
                byte[] buf = new byte[2048];
                while (in.read(buf) != -1) { /* discard */ }
            } catch (Exception ignored) {}
        }, threadName);
        t.setDaemon(true);
        t.start();
    }

    private int parseTimeToSeconds(String input) {
        String[] tokens = input.split(":");
        if (tokens.length == 1) {
            return Integer.parseInt(tokens[0]);
        } else if (tokens.length == 2) {
            int minutes = Integer.parseInt(tokens[0]);
            int seconds = Integer.parseInt(tokens[1]);
            return minutes * 60 + seconds;
        } else if (tokens.length == 3) {
            int hours = Integer.parseInt(tokens[0]);
            int minutes = Integer.parseInt(tokens[1]);
            int seconds = Integer.parseInt(tokens[2]);
            return hours * 3600 + minutes * 60 + seconds;
        } else {
            throw new IllegalArgumentException("Too many colons");
        }
    }
}
