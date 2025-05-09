package org.serverboi.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.serverboi.BotLauncher;
import org.serverboi.audio.AudioSessionManager;

public class LeaveCommand extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        String prefix = BotLauncher.config.getString("prefix");

        if (content.equalsIgnoreCase(prefix + "leave")) {
            var audioManager = event.getGuild().getAudioManager();

            // Stop any active stream
            AudioSessionManager.stop(event.getGuild());

            // Remove the audio handler and disconnect
            audioManager.setSendingHandler(null);
            audioManager.closeAudioConnection();

            event.getChannel().sendMessage("👋 Left the voice channel.").queue();
        }
    }
}