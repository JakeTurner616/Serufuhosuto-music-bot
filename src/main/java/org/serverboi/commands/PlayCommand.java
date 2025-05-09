package org.serverboi.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import org.serverboi.BotLauncher;
import org.serverboi.audio.AudioSessionManager;
import org.serverboi.audio.StreamSendHandler;

import java.io.BufferedReader;
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

        event.getChannel().sendTyping().queue();

        try {
            Process versionProc = new ProcessBuilder("yt-dlp", "--version")
                    .redirectErrorStream(true).start();
            BufferedReader versionReader = new BufferedReader(new InputStreamReader(versionProc.getInputStream()));
            String versionLine = versionReader.readLine();
            System.out.println("[DEBUG] yt-dlp version: " + versionLine);
            versionProc.waitFor();
        } catch (Exception ve) {
            System.err.println("[ERROR] Failed to check yt-dlp version: " + ve.getMessage());
        }

        System.out.println("[DEBUG] Received play command: " + rawInput);

        boolean isURL = rawInput.startsWith("http://") || rawInput.startsWith("https://");
        String normalizedQuery = isURL ? rawInput : "ytsearch1:" + rawInput;

        // If something is already playing, add to queue and return
        if (AudioSessionManager.isStreaming(guild)) {
            String title = rawInput;
            if (!isURL) {
                try {
                    Process meta = new ProcessBuilder(
                        "yt-dlp", "--no-playlist", "--print", "%(title)s", normalizedQuery
                    ).start();
                    BufferedReader metaReader = new BufferedReader(new InputStreamReader(meta.getInputStream()));
                    String fetchedTitle = metaReader.readLine();
                    if (fetchedTitle != null && !fetchedTitle.isEmpty()) {
                        title = fetchedTitle;
                    }
                } catch (Exception e) {
                    System.err.println("[WARN] Could not resolve title for queued track.");
                }
            }

            AudioSessionManager.enqueue(guild, normalizedQuery);
            event.getChannel().sendMessage("➕ Added to queue: " + title).queue();
            System.out.println("[DEBUG] Added to queue: " + title);
            return;
        }

        try {
            // Get direct audio stream URL
            Process yt = new ProcessBuilder(
                "yt-dlp", "-f", "bestaudio[ext=m4a]/bestaudio", "-g", normalizedQuery
            ).redirectErrorStream(true).start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(yt.getInputStream()));
            String streamUrl = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("http")) {
                    streamUrl = line;
                } else {
                    System.err.println("[YT-DLP WARNING] " + line);
                }
            }
            yt.waitFor();

            if (streamUrl == null || streamUrl.isEmpty()) {
                event.getChannel().sendMessage("❌ Failed to get a valid audio stream URL from yt-dlp.").queue();
                System.err.println("[ERROR] yt-dlp did not return a usable URL.");
                return;
            }

            // Get the title (if search query)
            String title = rawInput;
            if (!isURL) {
                Process meta = new ProcessBuilder(
                    "yt-dlp", "--no-playlist", "--print", "%(title)s", normalizedQuery
                ).start();
                BufferedReader metaReader = new BufferedReader(new InputStreamReader(meta.getInputStream()));
                String fetchedTitle = metaReader.readLine();
                if (fetchedTitle != null && !fetchedTitle.isEmpty()) {
                    title = fetchedTitle;
                }
            }

            System.out.println("[DEBUG] Stream URL: " + streamUrl);

            AudioManager audioManager = guild.getAudioManager();
            if (!audioManager.isConnected()) {
                var vc = event.getMember().getVoiceState().getChannel();
                if (vc == null) {
                    event.getChannel().sendMessage("❌ You must be in a voice channel.").queue();
                    System.err.println("[ERROR] User not in voice channel.");
                    return;
                }
                audioManager.openAudioConnection(vc);
                System.out.println("[DEBUG] Connected to voice channel: " + vc.getName());
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
            ).redirectErrorStream(true).start();

            Runnable onEnd = () -> {
                System.out.println("[DEBUG] Track ended.");
                AudioSessionManager.stop(guild);
                if (AudioSessionManager.hasQueue(guild)) {
                    String next = AudioSessionManager.dequeue(guild);
                    if (next != null) {
                        event.getChannel().sendMessage("⏭ Playing next: " + next).queue();
                        event.getJDA().getGuildById(guild.getId()).getAudioManager().setSendingHandler(null);
                        event.getMessage().getChannel().sendMessage(prefix + "play " + next).queue();
                    }
                }
            };

            audioManager.setSendingHandler(new StreamSendHandler(ffmpeg.getInputStream(), onEnd));
            AudioSessionManager.register(guild, ffmpeg);
            AudioSessionManager.setNowPlaying(guild, normalizedQuery);

            event.getChannel().sendMessage("🔊 Now streaming: " + title).queue();
            System.out.println("[DEBUG] Streaming started: " + title);

        } catch (Exception e) {
            e.printStackTrace();
            event.getChannel().sendMessage("❌ Error: " + e.getMessage()).queue();
        }
    }
}
