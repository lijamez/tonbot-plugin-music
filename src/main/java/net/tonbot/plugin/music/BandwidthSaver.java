package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import net.tonbot.common.BotUtils;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.voice.user.UserVoiceChannelJoinEvent;
import sx.blah.discord.handle.impl.events.guild.voice.user.UserVoiceChannelLeaveEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IVoiceChannel;

/**
 * Automatically pauses playback if everyone leaves the bot's voice channel. 
 * Automatically unpauses if someone joins the previously empty channel.
 */
class BandwidthSaver {

	private final DiscordAudioPlayerManager discordAudioPlayerManager;
	private final BotUtils botUtils;

	@Inject
	public BandwidthSaver(
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
		autoPauseAndResume(event.getVoiceChannel());
	}

	private void autoPauseAndResume(IVoiceChannel vc) {
		IVoiceChannel botVc = vc.getGuild().getConnectedVoiceChannel();
		
		if (botVc == null || vc.getLongID() != botVc.getLongID() || !discordAudioPlayerManager.hasSession(vc.getGuild())) {
			return;
		}
		
		long ourUserId = vc.getClient().getOurUser().getLongID();

		long otherUsersCount = vc.getUsersHere().stream()
				.filter(user -> user.getLongID() != ourUserId)
				.count();

		long defaultChannelId = discordAudioPlayerManager.getDefaultChannelId(vc.getGuild()).get();
		IChannel defaultChannel = vc.getClient().getChannelByID(defaultChannelId);
		
		if (otherUsersCount == 1) {
			// Someone just joined.
			discordAudioPlayerManager.setPauseState(vc.getGuild(), false);
			botUtils.sendMessage(defaultChannel, "Unpaused due to user join.");
		} else if (otherUsersCount == 0) {
			// Everyone left.
			discordAudioPlayerManager.setPauseState(vc.getGuild(), true);
			botUtils.sendMessage(defaultChannel, "Paused due to everyone leaving.");
		}
	}
}
