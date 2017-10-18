package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

import net.tonbot.common.BotUtils;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.obj.IGuild;

class AudioSessionFactory {

	private final IDiscordClient discordClient;
	private final AudioPlayerManager audioPlayerManager;
	private final BotUtils botUtils;

	@Inject
	public AudioSessionFactory(
			IDiscordClient discordClient,
			AudioPlayerManager audioPlayerManager,
			BotUtils botUtils) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.audioPlayerManager = Preconditions.checkNotNull(audioPlayerManager,
				"audioPlayerManager must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	public AudioSession create(long guildId, long textChannelId) {
		IGuild guild = discordClient.getGuildByID(guildId);

		AudioPlayer audioPlayer = audioPlayerManager.createPlayer();
		guild.getAudioManager().setAudioProvider(new LavaplayerAudioProvider(audioPlayer));

		AudioSession audioSession = new AudioSession(
				discordClient, audioPlayerManager, audioPlayer, textChannelId, botUtils);
		audioPlayer.addListener(audioSession);

		return audioSession;
	}
}
