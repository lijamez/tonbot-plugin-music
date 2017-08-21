package net.tonbot.plugin.music;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.TonbotBusinessException;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IVoiceChannel;

public class BeckonActivity implements Activity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "beckon"))
			.description("Makes me join a voice channel.")
			.build();


	@Inject
	public BeckonActivity() {

	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public void enact(MessageReceivedEvent event, String args) {
		IVoiceChannel voiceChannel = event.getAuthor().getVoiceStateForGuild(event.getGuild()).getChannel();
		
		// TODO: Maybe the user can also specify a voice channel in the args.
		if (voiceChannel == null) {
			throw new TonbotBusinessException("You need to be in a voice channel first.");
		}
		
		voiceChannel.join();
	}
}
