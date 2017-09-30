package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

class PauseActivity extends AudioSessionActivity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "pause"))
			.routeAliases(ImmutableList.of(
					ImmutableList.of("pause")))
			.description("Pauses the player.")
			.build();

	private final BotUtils botUtils;

	@Inject
	public PauseActivity(DiscordAudioPlayerManager discordAudioPlayerManager, BotUtils botUtils) {
		super(discordAudioPlayerManager);
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {
		audioSession.setPaused(true);
		botUtils.sendMessage(event.getChannel(), "Paused.");
	}
}
