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

            String url = AudioSessionManager.getNowPlaying(event.getGuild());
            if (url == null) {
                event.getChannel().sendMessage("❌ No active track to seek in.").queue();
                return;
            }

            event.getChannel().sendTyping().queue();

            try {
                Process yt = new ProcessBuilder(
                    "yt-dlp", "-f", BotLauncher.config.getString("ytQuality"), "-g", url
                ).redirectErrorStream(true).start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(yt.getInputStream()));
                String streamUrl = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("http")) {
                        streamUrl = line;
                    }
                }
                yt.waitFor();

                if (streamUrl == null) {
                    event.getChannel().sendMessage("❌ Failed to fetch stream URL.").queue();
                    return;
                }

                Process ffmpeg = new ProcessBuilder(
                    BotLauncher.config.getString("ffmpegPath"),
                    "-reconnect", "1",
                    "-reconnect_streamed", "1",
                    "-reconnect_delay_max", "5",
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
                AudioSessionManager.setNowPlaying(event.getGuild(), url);

                event.getChannel().sendMessage("⏩ Seeking to `" + parts[1] + "`...").queue();

            } catch (Exception e) {
                event.getChannel().sendMessage("❌ Error seeking: " + e.getMessage()).queue();
            }
        }
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
