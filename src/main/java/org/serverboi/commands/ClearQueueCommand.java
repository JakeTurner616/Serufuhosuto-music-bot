// src/main/java/org/serverboi/commands/ClearQueueCommand.java
package org.serverboi.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.serverboi.BotLauncher;
import org.serverboi.audio.AudioSessionManager;

public class ClearQueueCommand extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();
        String prefix = BotLauncher.config.getString("prefix");

        if (!content.equalsIgnoreCase(prefix + "clear")) return;

        var guild = event.getGuild();

        boolean hadQueue = AudioSessionManager.hasQueue(guild);
        AudioSessionManager.clearQueue(guild);

        if (hadQueue) {
            event.getChannel().sendMessage("🧹 Cleared the queue.").queue();
        } else {
            event.getChannel().sendMessage("ℹ️ The queue was already empty.").queue();
        }
    }
}
