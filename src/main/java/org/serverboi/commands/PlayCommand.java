// src/main/java/org/serverboi/commands/PlayCommand.java
package org.serverboi.commands;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.audio.hooks.ConnectionListener;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.managers.AudioManager;
import org.serverboi.BotLauncher;
import org.serverboi.audio.AudioSessionManager;
import org.serverboi.audio.FfmpegPcm;
import org.serverboi.audio.StreamSendHandler;
import org.json.JSONObject;

import net.dv8tion.jda.api.Permission;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class PlayCommand extends ListenerAdapter {
    private record StreamInfo(String url, Map<String, String> headers) {}

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
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
            configureVoiceConnectionTracking(audioManager, guild);

            // Ensure voice connection
            if (!audioManager.isConnected()) {
                if (joinChannel == null) {
                    textChannel.sendMessage("❌ You must be in a voice channel.").queue();
                    return;
                }

                // Check bot permissions to connect/speak
                boolean hasPerms = guild.getSelfMember().hasPermission(joinChannel.asVoiceChannel(), Permission.VOICE_CONNECT, Permission.VOICE_SPEAK);
                if (!hasPerms) {
                    textChannel.sendMessage("❌ I don't have permission to join or speak in that voice channel. Please grant Connect & Speak permissions to the bot.").queue();
                    return;
                }

                // Respect join cooldowns to avoid tight reconnect loops when the voice server
                // rejects connections (E2EE/DAVE required, auth/session problems, etc.).
                if (!AudioSessionManager.canAttemptJoin(guild)) {
                    textChannel.sendMessage("❌ The bot is currently unable to join voice in this server (recent failures). Please try again in a bit or check server voice settings.").queue();
                    return;
                }

                audioManager.setConnectTimeout(30_000);
                audioManager.openAudioConnection(joinChannel);
            }

            String ytDlp = BotLauncher.config.optString("ytDlpPath", "yt-dlp");
            String ffmpegBin = BotLauncher.config.optString("ffmpegPath", "ffmpeg");
            String quality = BotLauncher.config.getString("ytQuality");

            StreamInfo streamInfo = fetchStreamInfo(ytDlp, quality, req.query());
            if (streamInfo == null || streamInfo.url().isEmpty()) {
                textChannel.sendMessage("❌ Failed to get a valid audio stream URL from yt-dlp.").queue();
                return;
            }

            Process ffmpeg = FfmpegPcm.start(ffmpegBin, streamInfo.url(), streamInfo.headers(), "play-" + guild.getId());

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

    private void configureVoiceConnectionTracking(AudioManager audioManager, net.dv8tion.jda.api.entities.Guild guild) {
        if (audioManager.getConnectionListener() != null) {
            return;
        }

        audioManager.setConnectionListener(new ConnectionListener() {
            @Override
            public void onStatusChange(ConnectionStatus status) {
                System.out.println("[VOICE " + guild.getId() + "] " + status);

                if (status == ConnectionStatus.CONNECTED) {
                    AudioSessionManager.clearJoinCooldown(guild);
                    return;
                }

                if (status == ConnectionStatus.ERROR_CONNECTION_TIMEOUT
                        || status == ConnectionStatus.ERROR_UNSUPPORTED_ENCRYPTION_MODES
                        || status == ConnectionStatus.ERROR_WEBSOCKET_UNABLE_TO_CONNECT
                        || status == ConnectionStatus.ERROR_UDP_UNABLE_TO_CONNECT
                        || status == ConnectionStatus.DISCONNECTED_AUTHENTICATION_FAILURE) {
                    AudioSessionManager.registerFailedJoin(guild);
                }
            }
        });
    }

    /**
     * Fetch direct media URL and request headers from yt-dlp.
     * IMPORTANT: Do NOT merge stderr into stdout, otherwise warnings can pollute parsing.
     */
    private StreamInfo fetchStreamInfo(String ytDlp, String quality, String query) {
        try {
            Process yt = new ProcessBuilder(
                    ytDlp,
                    "--no-warnings",
                    "--quiet",
                    "--no-playlist",
                    "-f", quality,
                    "--print", "%(url)s",
                    "--print", "%(http_headers)j",
                    query
            ).start();

            // Drain stderr so yt-dlp can't block, but don't mix it into stdout.
            drainAsync(yt.getErrorStream(), "yt-dlp-stderr", "yt-dlp");

            BufferedReader out = new BufferedReader(new InputStreamReader(yt.getInputStream()));
            String streamUrl = null;
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = out.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("http") && streamUrl == null) {
                    streamUrl = line;
                } else if (line.startsWith("{") && line.endsWith("}")) {
                    JSONObject json = new JSONObject(line);
                    for (String key : json.keySet()) {
                        String value = json.optString(key, "");
                        if (!value.isBlank()) {
                            headers.put(key, value);
                        }
                    }
                }
            }

            int exitCode = yt.waitFor();
            if (exitCode != 0) {
                System.err.println("[ERROR] yt-dlp exited with code " + exitCode + " for query: " + query);
            }
            if (streamUrl == null || streamUrl.isBlank()) {
                return null;
            }

            return new StreamInfo(streamUrl, headers);

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

            drainAsync(meta.getErrorStream(), "yt-dlp-meta-stderr", "yt-dlp-meta");

            BufferedReader stdout = new BufferedReader(new InputStreamReader(meta.getInputStream()));
            String title;
            while ((title = stdout.readLine()) != null) {
                title = title.trim();
                if (!title.isEmpty()) {
                    int exitCode = meta.waitFor();
                    if (exitCode != 0) {
                        System.err.println("[WARN] yt-dlp title lookup exited with code " + exitCode + " for query: " + normalizedQuery);
                    }
                    return title;
                }
            }

            int exitCode = meta.waitFor();
            if (exitCode != 0) {
                System.err.println("[WARN] yt-dlp title lookup exited with code " + exitCode + " for query: " + normalizedQuery);
            }
        } catch (Exception ignored) {
        }

        return fallback;
    }

    private void drainAsync(InputStream in, String threadName, String logPrefix) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        System.err.println("[" + logPrefix + "] " + line);
                    }
                }
            } catch (Exception ignored) {
            }
        }, threadName);
        t.setDaemon(true);
        t.start();
    }
}
