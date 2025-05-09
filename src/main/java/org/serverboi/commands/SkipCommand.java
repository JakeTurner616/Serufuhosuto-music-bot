package org.serverboi.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.serverboi.BotLauncher;
import org.serverboi.audio.AudioSessionManager;

public class SkipCommand extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        String prefix = BotLauncher.config.getString("prefix");

        if (content.equalsIgnoreCase(prefix + "skip")) {
            AudioSessionManager.stop(event.getGuild());
            event.getChannel().sendMessage("⏭ Skipped current track.").queue();
        }
    }
}