package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import net.tonbot.common.TonbotBusinessException;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IVoiceChannel;

class DismissActivity implements Activity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "dismiss"))
			.description("Makes me leave the voice channel.")
			.build();

	private final BotUtils botUtils;

	@Inject
	public DismissActivity(
			BotUtils botUtils) {
		this.botUtils = Preconditions.checkNotNull(botUtils,
				"botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public void enact(MessageReceivedEvent event, String args) {
		// Get the voice channel that this bot is in.
		IVoiceChannel voiceChannel = event.getClient().getOurUser().getVoiceStateForGuild(event.getGuild())
				.getChannel();

		if (voiceChannel == null) {
			throw new TonbotBusinessException("I'm already not in a voice channel. :confused:");
		}

		voiceChannel.leave();
		botUtils.sendMessage(event.getChannel(), "Bye. :wave:");
	}
}
