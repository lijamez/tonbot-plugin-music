package net.tonbot.plugin.music;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;

class PlayActivity implements Activity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "play"))
			.parameters(ImmutableList.of("link to song"))
			.description(
					"Plays the song provided by the link. If no song link is provided, then it unpauses the player.")
			.build();

	private final DiscordAudioPlayerManager discordAudioPlayerManager;
	private final BotUtils botUtils;

	@Inject
	public PlayActivity(
			DiscordAudioPlayerManager discordAudioPlayerManager,
			BotUtils botUtils) {
		this.discordAudioPlayerManager = Preconditions.checkNotNull(discordAudioPlayerManager,
				"discordAudioPlayerManager must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils,"botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public void enact(MessageReceivedEvent event, String args) {
		IGuild guild = event.getGuild();
		
		Long defaultChannelId = discordAudioPlayerManager.getDefaultChannelId(guild).orElse(null);
		if (defaultChannelId == null || defaultChannelId != event.getChannel().getLongID()) {
			return;
		}

		if (!StringUtils.isBlank(args)) {
			discordAudioPlayerManager.enqueue(args, event.getGuild(), event.getAuthor());
		}
		
		discordAudioPlayerManager.play(guild);
		
		event.getMessage().delete();
	}
}
