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

        verifyBinaryAvailable(config.optString("ffmpegPath", "ffmpeg"), "-version", "FFmpeg",
                "👉 On Windows: choco install ffmpeg -y");

        verifyBinaryAvailable(config.optString("ytDlpPath", "yt-dlp"), "--version", "yt-dlp",
                "👉 Install: python -m pip install -U yt-dlp");

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
        Path externalPath = Paths.get("config.json");
        Path devPath = Paths.get("src/main/resources/config.json");

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

        return null;
    }

    private static void verifyBinaryAvailable(String exe, String arg, String name, String help) {
        try {
            Process process = new ProcessBuilder(exe, arg)
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("❌ " + name + " is installed but failed to run (" + exe + ").");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("❌ " + name + " is not available (" + exe + ").");
            System.err.println(help);
            System.exit(1);
        }
    }
}
