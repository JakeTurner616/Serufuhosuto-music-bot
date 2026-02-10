// src/main/java/org/serverboi/commands/SkipCommand.java
package org.serverboi.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import org.serverboi.BotLauncher;
import org.serverboi.audio.AudioSessionManager;
import org.serverboi.audio.StreamSendHandler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SkipCommand extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        String prefix = BotLauncher.config.getString("prefix");
        if (!content.equalsIgnoreCase(prefix + "skip")) return;

        var guild = event.getGuild();
        var channel = event.getChannel();

        // stop current stream immediately
        AudioSessionManager.stop(guild);

        // dequeue next
        var next = AudioSessionManager.dequeue(guild);
        if (next == null) {
            guild.getAudioManager().setSendingHandler(null);
            channel.sendMessage("⏹️ Skipped current track — no songs remaining in queue.").queue();
            System.out.println("[DEBUG] SkipCommand: queue empty, playback stopped.");
            return;
        }

        channel.sendTyping().queue();
        playNext(guild, channel, next);
    }

    private void playNext(net.dv8tion.jda.api.entities.Guild guild,
                          net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel,
                          AudioSessionManager.TrackRequest next) {
        try {
            String ytDlp = BotLauncher.config.optString("ytDlpPath", "yt-dlp");
            String ffmpegBin = BotLauncher.config.optString("ffmpegPath", "ffmpeg");
            String quality = BotLauncher.config.getString("ytQuality");

            String streamUrl = fetchStreamUrl(ytDlp, quality, next.query());
            if (streamUrl == null) {
                channel.sendMessage("❌ Failed to fetch stream URL for next track.").queue();
                System.err.println("[ERROR] SkipCommand: yt-dlp did not return a valid URL.");
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

            AudioManager audioManager = guild.getAudioManager();
            StreamSendHandler handler = new StreamSendHandler(ffmpeg.getInputStream(), () -> {
                AudioSessionManager.stop(guild);

                var queued = AudioSessionManager.dequeue(guild);
                if (queued != null) {
                    channel.sendMessage("⏭ Playing next: " + queued.title()).queue();
                    playNext(guild, channel, queued);
                } else {
                    audioManager.setSendingHandler(null);
                    channel.sendMessage("⏹️ Queue finished.").queue();
                }
            });

            audioManager.setSendingHandler(handler);
            AudioSessionManager.register(guild, ffmpeg, handler, next);

            channel.sendMessage("⏭ Skipping to: " + next.title()).queue();
            System.out.println("[DEBUG] SkipCommand: skipping to " + next.title());

        } catch (Exception e) {
            channel.sendMessage("❌ Error playing next track: " + e.getMessage()).queue();
            e.printStackTrace();
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
            System.err.println("[ERROR] SkipCommand yt-dlp failed: " + e.getMessage());
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
}
