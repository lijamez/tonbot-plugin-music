package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

class StopActivity extends AudioSessionActivity<Void> {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder().route("music stop")
			.description("Stops playing the current track.").build();

	private final BotUtils botUtils;

	@Inject
	public StopActivity(GuildMusicManager guildMusicManager, BotUtils botUtils) {
		super(guildMusicManager);
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public Class<?> getRequestType() {
		return Void.class;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, Void request, AudioSession audioSession) {
		audioSession.stop();
		botUtils.sendMessage(event.getChannel(), "Stopped.");
	}
}
