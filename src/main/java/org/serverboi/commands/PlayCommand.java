// src/main/java/org/serverboi/commands/PlayCommand.java
package org.serverboi.commands;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import org.serverboi.BotLauncher;
import org.serverboi.audio.AudioSessionManager;
import org.serverboi.audio.StreamSendHandler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class PlayCommand extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        String prefix = BotLauncher.config.getString("prefix");

        boolean isPlay = content.startsWith(prefix + "play ");
        boolean isAlias = content.startsWith(prefix + "p ");
        if (!isPlay && !isAlias) return;

        String rawInput = content.substring((isPlay ? prefix.length() + 5 : prefix.length() + 2)).trim();
        var guild = event.getGuild();
        var channel = event.getChannel();

        channel.sendTyping().queue();

        boolean isURL = rawInput.startsWith("http://") || rawInput.startsWith("https://");
        String normalizedQuery = isURL ? rawInput : "ytsearch1:" + rawInput;

        String title = resolveTitle(normalizedQuery, rawInput);
        AudioSessionManager.TrackRequest req = new AudioSessionManager.TrackRequest(normalizedQuery, title);

        // Already streaming? queue it.
        if (AudioSessionManager.isStreaming(guild)) {
            AudioSessionManager.enqueue(guild, req);
            channel.sendMessage("➕ Added to queue: " + title).queue();
            return;
        }

        // Start immediately.
        startPlayback(
                guild,
                event.getMember() != null ? event.getMember().getVoiceState().getChannel() : null,
                channel,
                prefix,
                req
        );
    }

    private void startPlayback(
            net.dv8tion.jda.api.entities.Guild guild,
            net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion joinChannel,
            MessageChannel textChannel,
            String prefix,
            AudioSessionManager.TrackRequest req
    ) {
        try {
            AudioManager audioManager = guild.getAudioManager();

            // Ensure voice connection
            if (!audioManager.isConnected()) {
                if (joinChannel == null) {
                    textChannel.sendMessage("❌ You must be in a voice channel.").queue();
                    return;
                }
                audioManager.openAudioConnection(joinChannel);
            }

            String ytDlp = BotLauncher.config.optString("ytDlpPath", "yt-dlp");
            String ffmpegBin = BotLauncher.config.optString("ffmpegPath", "ffmpeg");
            String quality = BotLauncher.config.getString("ytQuality");

            String streamUrl = fetchStreamUrl(ytDlp, quality, req.query());
            if (streamUrl == null || streamUrl.isEmpty()) {
                textChannel.sendMessage("❌ Failed to get a valid audio stream URL from yt-dlp.").queue();
                return;
            }

            // IMPORTANT: JDA expects 48kHz stereo 16-bit BIG-ENDIAN PCM (s16be)
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

            // Drain stderr so ffmpeg can't block
            drainAsync(ffmpeg.getErrorStream(), "FFmpeg-stderr");

            Runnable onEnd = () -> {
                AudioSessionManager.stop(guild);

                var next = AudioSessionManager.dequeue(guild);
                if (next != null) {
                    textChannel.sendMessage("⏭ Playing next: " + next.title()).queue();
                    startPlayback(guild, null, textChannel, prefix, next);
                } else {
                    guild.getAudioManager().setSendingHandler(null);
                    textChannel.sendMessage("⏹️ Queue finished.").queue();
                }
            };

            StreamSendHandler handler = new StreamSendHandler(ffmpeg.getInputStream(), onEnd);
            audioManager.setSendingHandler(handler);

            AudioSessionManager.register(guild, ffmpeg, handler, req);

            textChannel.sendMessage("🔊 Now streaming: " + req.title()).queue();

        } catch (Exception e) {
            e.printStackTrace();
            textChannel.sendMessage("❌ Error: " + e.getMessage()).queue();
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
            System.err.println("[ERROR] yt-dlp failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolve title for search queries.
     * IMPORTANT: Read stdout only; warnings live on stderr.
     */
    private String resolveTitle(String normalizedQuery, String fallback) {
        if (!normalizedQuery.startsWith("ytsearch")) return fallback;

        String ytDlp = BotLauncher.config.optString("ytDlpPath", "yt-dlp");
        try {
            Process meta = new ProcessBuilder(
                    ytDlp,
                    "--no-warnings",
                    "--quiet",
                    "--no-playlist",
                    "--print", "%(title)s",
                    normalizedQuery
            ).start();

            drainAsync(meta.getErrorStream(), "yt-dlp-meta-stderr");

            BufferedReader stdout = new BufferedReader(new InputStreamReader(meta.getInputStream()));
            String title;
            while ((title = stdout.readLine()) != null) {
                title = title.trim();
                if (!title.isEmpty()) {
                    meta.waitFor();
                    return title;
                }
            }

            meta.waitFor();
        } catch (Exception ignored) {
        }

        return fallback;
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
}
