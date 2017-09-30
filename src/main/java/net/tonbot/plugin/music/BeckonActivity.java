package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import net.tonbot.common.TonbotBusinessException;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IVoiceChannel;
import sx.blah.discord.util.MissingPermissionsException;

class BeckonActivity implements Activity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "beckon"))
			.routeAliases(ImmutableList.of(
					ImmutableList.of("music", "summon"),
					ImmutableList.of("music", "join")))
			.description(
					"Makes me join your voice channel. The channel that this command is sent in will be used for music commands.")
			.usageDescription(
					"Once this command is said in a text channel, I will join the voice channel that you are in and then "
							+ "I will only respond to music commands in that same text channel. Users can still move me to another text "
							+ "channel by saying this command in the text channel they want me to connect to.")
			.build();

	private final DiscordAudioPlayerManager discordAudioPlayerManager;
	private final BotUtils botUtils;

	@Inject
	public BeckonActivity(
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

		IVoiceChannel userVoiceChannel = event.getAuthor().getVoiceStateForGuild(event.getGuild()).getChannel();

		if (userVoiceChannel == null) {
			throw new TonbotBusinessException("You need to be in a voice channel first.");
		}

		IDiscordClient client = event.getClient();

		// Bot should hop onto the user's voice channel if the bot is in no voice
		// channel or if it's in a different voice channel than the user.
		IVoiceChannel botVoiceChannel = client.getOurUser().getVoiceStateForGuild(event.getGuild()).getChannel();
		if (botVoiceChannel == null || botVoiceChannel.getLongID() != userVoiceChannel.getLongID()) {
			try {
				userVoiceChannel.join();

				try {
					discordAudioPlayerManager.destroyFor(event.getGuild());
				} catch (NoSessionException e) {
					// This is fine.
				}

				discordAudioPlayerManager.initFor(event.getGuild(), event.getChannel());
			} catch (MissingPermissionsException e) {
				botUtils.sendMessage(event.getChannel(), "I don't have permission to join your voice channel.");
			}
		}
	}
}
