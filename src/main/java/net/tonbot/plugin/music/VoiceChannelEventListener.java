package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import net.tonbot.common.BotUtils;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.voice.VoiceDisconnectedEvent;
import sx.blah.discord.handle.impl.events.guild.voice.user.UserVoiceChannelJoinEvent;
import sx.blah.discord.handle.impl.events.guild.voice.user.UserVoiceChannelLeaveEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IVoiceChannel;

/**
 * Automatically pauses playback if everyone leaves the bot's voice channel.
 * Automatically unpauses if someone joins the previously empty channel.
 * Automatically destroys the session if the bot leaves its voice channel.
 */
class VoiceChannelEventListener {

	private final DiscordAudioPlayerManager discordAudioPlayerManager;
	private final BotUtils botUtils;

	@Inject
	public VoiceChannelEventListener(
			DiscordAudioPlayerManager discordAudioPlayerManager,
			BotUtils botUtils) {
		this.discordAudioPlayerManager = Preconditions.checkNotNull(discordAudioPlayerManager,
				"discordAudioPlayerManager must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@EventSubscriber
	public void onUserVoiceStateUpdate(UserVoiceChannelJoinEvent event) {
		autoPauseAndResume(event.getVoiceChannel());
	}

	@EventSubscriber
	public void onUserVoiceChannelLeave(UserVoiceChannelLeaveEvent event) {
		// Note: If our bot user leaves a VC, this event will not be fired. A
		// VoiceDisconnectedEvent will fire instead.
		autoPauseAndResume(event.getVoiceChannel());
	}

	@EventSubscriber
	public void onVoiceDisconnected(VoiceDisconnectedEvent event) {
		try {
			discordAudioPlayerManager.destroyFor(event.getGuild());
		} catch (NoSessionException e) {

		}
	}

	private void autoPauseAndResume(IVoiceChannel vc) {
		IVoiceChannel botVc = vc.getGuild().getConnectedVoiceChannel();

		if (botVc == null || vc.getLongID() != botVc.getLongID()) {
			return;
		}

		final IGuild guild = vc.getGuild();
		AudioSession audioSession;
		try {
			audioSession = discordAudioPlayerManager.checkout(guild);
		} catch (NoSessionException e) {
			return;
		}

		try {
			long ourUserId = vc.getClient().getOurUser().getLongID();

			long otherUsersCount = vc.getUsersHere().stream()
					.filter(user -> user.getLongID() != ourUserId)
					.count();

			IChannel defaultChannel = vc.getClient().getChannelByID(audioSession.getDefaultChannelId());

			if (otherUsersCount == 1) {
				// Someone just joined.
				audioSession.setPaused(false);
			} else if (otherUsersCount == 0) {
				// Everyone left.
				audioSession.setPaused(true);
				botUtils.sendMessage(defaultChannel, "Paused because everyone left the voice channel.");
			}
		} finally {
			discordAudioPlayerManager.checkin(guild);
		}

	}
}
