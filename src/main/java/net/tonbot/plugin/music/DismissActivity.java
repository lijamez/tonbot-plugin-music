package net.tonbot.plugin.music;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.TonbotBusinessException;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IVoiceChannel;

public class DismissActivity implements Activity {
	
	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "dismiss"))
			.description("Makes me leave the voice channel.")
			.build();


	@Inject
	public DismissActivity() {

	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public void enact(MessageReceivedEvent event, String args) {
		IGuild guild = event.getGuild();
		
		// Get the voice channel that this bot is in.
		IVoiceChannel voiceChannel = event.getClient().getOurUser().getVoiceStateForGuild(guild).getChannel();
		
		if (voiceChannel == null) {
			throw new TonbotBusinessException("I'm already not in a voice channel. :confused:");
		}
		
		voiceChannel.leave();
		
		//TODO: Consider also stopping playback.
	}
}
