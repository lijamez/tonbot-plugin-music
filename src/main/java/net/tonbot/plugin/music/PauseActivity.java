package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

class PauseActivity extends AudioSessionActivity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route("music pause")
			.description("Pauses the player.")
			.build();

	private final BotUtils botUtils;

	@Inject
	public PauseActivity(GuildMusicManager guildMusicManager, BotUtils botUtils) {
		super(guildMusicManager);
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
