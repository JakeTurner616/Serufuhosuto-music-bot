package org.serverboi;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.json.JSONObject;
import org.serverboi.commands.*;
import org.serverboi.listeners.VoiceStateListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

public class BotLauncher {
    public static JSONObject config;

    public static void main(String[] args) throws Exception {
        config = loadConfig();

        verifyFfmpegAvailable(); // early runtime check

        JDABuilder.createDefault(
                config.getString("token"),
                EnumSet.of(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_VOICE_STATES,
                        GatewayIntent.MESSAGE_CONTENT
                )
        )
        .addEventListeners(
            new PlayCommand(),
            new StopCommand(),
            new LeaveCommand(),
            new SkipCommand(),
            new SeekCommand(),
            new ClearQueueCommand(),
            new VoiceStateListener()
        )
        .setActivity(Activity.listening("Music"))
        .build();
    }

    private static JSONObject loadConfig() {
        Path externalPath = Paths.get("config.json"); // Same folder as JAR
        Path devPath = Paths.get("src/main/resources/config.json"); // IDE/dev fallback

        try {
            if (Files.exists(externalPath)) {
                System.out.println("[INFO] Loading config from working directory: config.json");
                return new JSONObject(Files.readString(externalPath));
            } else if (Files.exists(devPath)) {
                System.out.println("[INFO] Loading config from dev resources.");
                return new JSONObject(Files.readString(devPath));
            } else {
                System.err.println("❌ No config.json found in working dir or resources.");
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to read config.json: " + e.getMessage());
            System.exit(1);
        }

        return null; // Unreachable, but required by compiler
    }

    private static void verifyFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder(config.getString("ffmpegPath"), "-version")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("❌ FFmpeg is installed but failed to run.");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("❌ FFmpeg is not available. Please install it or add it to your system PATH.");
            System.err.println("👉 On Windows: choco install ffmpeg -y");
            System.exit(1);
        }
    }
}