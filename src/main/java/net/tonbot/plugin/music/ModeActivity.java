package net.tonbot.plugin.music;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

class ModeActivity implements Activity {
	
	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "mode"))
			.parameters(ImmutableList.of("mode"))
			.description("Sets the play mode. Available modes: " + Arrays.asList(PlayMode.values()))
			.build();

	private final DiscordAudioPlayerManager discordAudioPlayerManager;
	private final BotUtils botUtils;
	
	@Inject
	public ModeActivity(DiscordAudioPlayerManager discordAudioPlayerManager, BotUtils botUtils) {
		this.discordAudioPlayerManager = Preconditions.checkNotNull(discordAudioPlayerManager, "discordAudioPlayerManager must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");

	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public void enact(MessageReceivedEvent event, String args) {
		
		Long defaultChannelId = discordAudioPlayerManager.getDefaultChannelId(event.getGuild()).orElse(null);
		if (defaultChannelId == null || defaultChannelId != event.getChannel().getLongID()) {
			return;
		}
		
		PlayMode playMode;
		try {
			playMode = PlayMode.valueOf(args.trim());			
		} catch (IllegalArgumentException e) {
			playMode = null;
		}

		if (playMode != null) {
			discordAudioPlayerManager.setMode(event.getGuild(), playMode);
			botUtils.sendMessage(event.getChannel(), "Play mode has been changed to " + playMode);			
		} else {
			botUtils.sendMessage(event.getChannel(), "Invalid play mode. Must be one of: " + Arrays.asList(PlayMode.values()));
		}
	}
}
