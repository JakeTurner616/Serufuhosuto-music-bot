package org.serverboi.listeners;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import org.serverboi.audio.AudioSessionManager;

public class VoiceStateListener extends ListenerAdapter {
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        var channel = event.getGuild().getAudioManager().getConnectedChannel();

        if (channel != null && channel.getMembers().stream().noneMatch(m -> !m.getUser().isBot())) {
            // No human users left
            AudioSessionManager.stop(event.getGuild());
            event.getGuild().getAudioManager().setSendingHandler(null);
            event.getGuild().getAudioManager().closeAudioConnection();
        }
    }
}
