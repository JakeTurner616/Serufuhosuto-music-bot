// src/main/java/org/serverboi/commands/StopCommand.java
package org.serverboi.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.serverboi.BotLauncher;
import org.serverboi.audio.AudioSessionManager;

public class StopCommand extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        String prefix = BotLauncher.config.getString("prefix");

        if (!content.equalsIgnoreCase(prefix + "stop")) return;

        var guild = event.getGuild();
        var audioManager = guild.getAudioManager();

        // Stop current stream (kills ffmpeg + handler) and clear any queued tracks
        AudioSessionManager.stop(guild);
        AudioSessionManager.clearQueue(guild);

        audioManager.setSendingHandler(null);

        event.getChannel().sendMessage("🛑 Playback stopped and queue cleared.").queue();
    }
}
