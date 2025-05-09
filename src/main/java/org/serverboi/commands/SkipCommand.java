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

        if (content.equalsIgnoreCase(prefix + "skip")) {
            var guild = event.getGuild();
            AudioSessionManager.stop(guild);

            if (AudioSessionManager.hasQueue(guild)) {
                String nextUrl = AudioSessionManager.dequeue(guild);
                if (nextUrl != null) {
                    event.getChannel().sendMessage("⏭ Skipping to next: " + nextUrl).queue();
                    event.getChannel().sendTyping().queue();

                    try {
                        Process yt = new ProcessBuilder(
                            "yt-dlp", "-f", "bestaudio[ext=m4a]/bestaudio", "-g", nextUrl
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
                            event.getChannel().sendMessage("❌ Failed to fetch stream URL for next track.").queue();
                            return;
                        }

                        Process ffmpeg = new ProcessBuilder(
                            BotLauncher.config.getString("ffmpegPath"),
                            "-i", streamUrl,
                            "-vn",
                            "-f", "s16be",
                            "-ar", "48000",
                            "-ac", "2",
                            "-loglevel", "error",
                            "pipe:1"
                        ).start();

                        AudioManager audioManager = guild.getAudioManager();
                        audioManager.setSendingHandler(new StreamSendHandler(ffmpeg.getInputStream(), () -> {
                            AudioSessionManager.stop(guild);
                            if (AudioSessionManager.hasQueue(guild)) {
                                String queued = AudioSessionManager.dequeue(guild);
                                if (queued != null) {
                                    event.getMessage().getChannel().sendMessage(prefix + "play " + queued).queue();
                                }
                            }
                        }));

                        AudioSessionManager.register(guild, ffmpeg);
                        AudioSessionManager.setNowPlaying(guild, nextUrl);
                    } catch (Exception e) {
                        event.getChannel().sendMessage("❌ Error playing next track: " + e.getMessage()).queue();
                        e.printStackTrace();
                    }
                }
            } else {
                event.getChannel().sendMessage("⏭ Skipped current track. No more tracks in queue.").queue();
            }
        }
    }
}