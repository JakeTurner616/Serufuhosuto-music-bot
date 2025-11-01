package org.serverboi.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import org.serverboi.BotLauncher;
import org.serverboi.audio.AudioSessionManager;
import org.serverboi.audio.StreamSendHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SkipCommand extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        String prefix = BotLauncher.config.getString("prefix");

        if (!content.equalsIgnoreCase(prefix + "skip")) return;

        var guild = event.getGuild();
        AudioSessionManager.stop(guild);
        var channel = event.getChannel();

        // 🧹 Nothing queued? Stop completely.
        if (!AudioSessionManager.hasQueue(guild)) {
            guild.getAudioManager().setSendingHandler(null);
            channel.sendMessage("⏹️ Skipped current track — no songs remaining in queue.").queue();
            System.out.println("[DEBUG] SkipCommand: queue empty, playback stopped.");
            return;
        }

        // Otherwise, load next song
        String nextQuery = AudioSessionManager.dequeue(guild);
        if (nextQuery == null) {
            guild.getAudioManager().setSendingHandler(null);
            channel.sendMessage("⏹️ No more tracks left to play.").queue();
            System.out.println("[DEBUG] SkipCommand: dequeue returned null, stopped playback.");
            return;
        }

        channel.sendTyping().queue();

        try {
            // Fetch stream URL
            Process yt = new ProcessBuilder(
                "yt-dlp", "-f", BotLauncher.config.getString("ytQuality"), "-g", nextQuery
            ).redirectErrorStream(true).start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(yt.getInputStream()));
            String streamUrl = null, line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("http")) streamUrl = line;
            }
            yt.waitFor();

            if (streamUrl == null) {
                channel.sendMessage("❌ Failed to fetch stream URL for next track.").queue();
                System.err.println("[ERROR] SkipCommand: yt-dlp did not return a valid URL.");
                return;
            }

            // Fetch metadata
            String title = nextQuery;
            if (nextQuery.startsWith("ytsearch1:")) {
                Process meta = new ProcessBuilder(
                    "yt-dlp", "--no-playlist", "--print", "%(title)s", nextQuery
                ).start();
                BufferedReader metaReader = new BufferedReader(new InputStreamReader(meta.getInputStream()));
                String fetchedTitle = metaReader.readLine();
                if (fetchedTitle != null && !fetchedTitle.isEmpty()) title = fetchedTitle;
            }

            // Stream via FFmpeg
            Process ffmpeg = new ProcessBuilder(
                BotLauncher.config.getString("ffmpegPath"),
                "-reconnect", "1",
                "-reconnect_streamed", "1",
                "-reconnect_delay_max", "5",
                "-i", streamUrl,
                "-vn", "-f", "s16be", "-ar", "48000", "-ac", "2",
                "-loglevel", "error", "pipe:1"
            ).start();

            AudioManager audioManager = guild.getAudioManager();
            audioManager.setSendingHandler(new StreamSendHandler(ffmpeg.getInputStream(), () -> {
                AudioSessionManager.stop(guild);
                if (AudioSessionManager.hasQueue(guild)) {
                    String queued = AudioSessionManager.dequeue(guild);
                    if (queued != null) {
                        channel.sendMessage(prefix + "play " + queued).queue();
                    }
                } else {
                    audioManager.setSendingHandler(null);
                    channel.sendMessage("⏹️ Queue finished.").queue();
                }
            }));

            AudioSessionManager.register(guild, ffmpeg);
            AudioSessionManager.setNowPlaying(guild, nextQuery);

            channel.sendMessage("⏭ Skipping to: " + title).queue();
            System.out.println("[DEBUG] SkipCommand: skipping to " + title);

        } catch (Exception e) {
            channel.sendMessage("❌ Error playing next track: " + e.getMessage()).queue();
            e.printStackTrace();
        }
    }
}