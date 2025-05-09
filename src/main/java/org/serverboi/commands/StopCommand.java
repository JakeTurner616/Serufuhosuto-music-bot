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

        if (content.equalsIgnoreCase(prefix + "stop")) {
            AudioSessionManager.stop(event.getGuild());
            event.getGuild().getAudioManager().setSendingHandler(null);
            event.getChannel().sendMessage("🛑 Playback stopped.").queue();
        }
    }
}
