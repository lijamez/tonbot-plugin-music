package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import net.tonbot.common.TonbotBusinessException;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IVoiceChannel;

class DismissActivity implements Activity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "dismiss"))
			.description("Makes me leave the voice channel.")
			.build();

	private final DiscordAudioPlayerManager discordAudioPlayerManager;
	private final BotUtils botUtils;

	@Inject
	public DismissActivity(
			DiscordAudioPlayerManager discordAudioPlayerManager,
			BotUtils botUtils) {
		this.discordAudioPlayerManager = Preconditions.checkNotNull(discordAudioPlayerManager,
				"discordAudioPlayerManager must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils,
				"botUtils must be non-null.");
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

		// Get the voice channel that this bot is in.
		IVoiceChannel voiceChannel = event.getClient().getOurUser().getVoiceStateForGuild(guild).getChannel();

		if (voiceChannel == null) {
			throw new TonbotBusinessException("I'm already not in a voice channel. :confused:");
		}

		voiceChannel.leave();
		discordAudioPlayerManager.destroyFor(event.getGuild());
		botUtils.sendMessage(event.getChannel(), "Bye. :wave:");
	}
}
