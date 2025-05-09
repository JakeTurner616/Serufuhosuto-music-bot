package org.serverboi.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.serverboi.BotLauncher;
import org.serverboi.audio.AudioSessionManager;
import org.serverboi.audio.StreamSendHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SeekCommand extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        String prefix = BotLauncher.config.getString("prefix");

        if (content.startsWith(prefix + "seek ")) {
            String[] parts = content.split(" ");
            if (parts.length != 2) {
                event.getChannel().sendMessage("❌ Usage: " + prefix + "seek <seconds>").queue();
                return;
            }

            int seconds;
            try {
                seconds = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("❌ Please provide a valid number of seconds.").queue();
                return;
            }

            // Restart playback from offset
            String lastPlayedUrl = AudioSessionManager.dequeue(event.getGuild());
            if (lastPlayedUrl == null) {
                event.getChannel().sendMessage("❌ No track to seek in.").queue();
                return;
            }

            try {
                Process yt = new ProcessBuilder(
                    "yt-dlp", "-f", "bestaudio[ext=m4a]/bestaudio", "-g", lastPlayedUrl
                ).redirectErrorStream(true).start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(yt.getInputStream()));
                String streamUrl = null, line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("http")) streamUrl = line;
                }
                yt.waitFor();

                if (streamUrl == null) {
                    event.getChannel().sendMessage("❌ Failed to fetch stream URL.").queue();
                    return;
                }

                Process ffmpeg = new ProcessBuilder(
                    BotLauncher.config.getString("ffmpegPath"),
                    "-ss", String.valueOf(seconds),
                    "-i", streamUrl,
                    "-vn", "-f", "s16be", "-ar", "48000", "-ac", "2", "-loglevel", "error", "pipe:1"
                ).start();

                event.getGuild().getAudioManager().setSendingHandler(
                    new StreamSendHandler(ffmpeg.getInputStream(), () -> {
                        AudioSessionManager.stop(event.getGuild());
                    })
                );
                AudioSessionManager.register(event.getGuild(), ffmpeg);
                event.getChannel().sendMessage("⏩ Seeking to " + seconds + " seconds...").queue();

            } catch (Exception e) {
                event.getChannel().sendMessage("❌ Error seeking: " + e.getMessage()).queue();
            }
        }
    }
}