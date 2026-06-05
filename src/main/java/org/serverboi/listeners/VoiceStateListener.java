package org.serverboi.listeners;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import org.serverboi.audio.AudioSessionManager;

public class VoiceStateListener extends ListenerAdapter {
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        var channel = event.getGuild().getAudioManager().getConnectedChannel();

        if (channel != null) {
            boolean noHumans = channel.getMembers().stream().noneMatch(m -> !m.getUser().isBot());
            // Only stop and disconnect if there are no human users AND we are not currently streaming.
            // This prevents a connect/disconnect loop when playback is active.
            if (noHumans && !AudioSessionManager.isStreaming(event.getGuild())) {
                AudioSessionManager.stop(event.getGuild());
                event.getGuild().getAudioManager().setSendingHandler(null);
                event.getGuild().getAudioManager().closeAudioConnection();
            }
        }
    }
}
