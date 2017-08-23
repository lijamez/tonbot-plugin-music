package net.tonbot.plugin.music;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.ActivityDescriptor;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

class PlayActivity extends AudioSessionActivity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "play"))
			.parameters(ImmutableList.of("link to song"))
			.description(
					"Plays the song provided by the link. If no song link is provided, then it unpauses the player.")
			.build();

	@Inject
	public PlayActivity(DiscordAudioPlayerManager discordAudioPlayerManager) {
		super(discordAudioPlayerManager);
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {

		if (!StringUtils.isBlank(args)) {
			audioSession.enqueue(args, event.getGuild(), event.getAuthor());
			event.getMessage().delete();
		}

		audioSession.play();
	}
}
