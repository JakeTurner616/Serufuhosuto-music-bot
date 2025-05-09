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
                String nextQuery = AudioSessionManager.dequeue(guild);
                if (nextQuery != null) {
                    event.getChannel().sendTyping().queue();

                    try {
                        // Fetch stream URL
                        Process yt = new ProcessBuilder(
                            "yt-dlp", "-f", BotLauncher.config.getString("ytQuality"), "-g", nextQuery
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

                        // Optionally get title if search
                        String title = nextQuery;
                        if (nextQuery.startsWith("ytsearch1:")) {
                            Process meta = new ProcessBuilder(
                                "yt-dlp", "--no-playlist", "--print", "%(title)s", nextQuery
                            ).start();
                            BufferedReader metaReader = new BufferedReader(new InputStreamReader(meta.getInputStream()));
                            String fetchedTitle = metaReader.readLine();
                            if (fetchedTitle != null && !fetchedTitle.isEmpty()) {
                                title = fetchedTitle;
                            }
                        }

                        // FFmpeg stream
                        Process ffmpeg = new ProcessBuilder(
                            BotLauncher.config.getString("ffmpegPath"),
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

                        AudioManager audioManager = guild.getAudioManager();
                        audioManager.setSendingHandler(new StreamSendHandler(ffmpeg.getInputStream(), () -> {
                            AudioSessionManager.stop(guild);
                            if (AudioSessionManager.hasQueue(guild)) {
                                String queued = AudioSessionManager.dequeue(guild);
                                if (queued != null) {
                                    event.getChannel().sendMessage(prefix + "play " + queued).queue();
                                }
                            }
                        }));

                        AudioSessionManager.register(guild, ffmpeg);
                        AudioSessionManager.setNowPlaying(guild, nextQuery);

                        event.getChannel().sendMessage("⏭ Skipping to: " + title).queue();
                        System.out.println("[DEBUG] Skipped to: " + title);

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
