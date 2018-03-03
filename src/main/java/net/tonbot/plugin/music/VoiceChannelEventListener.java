package net.tonbot.plugin.music;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import net.tonbot.common.BotUtils;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.voice.VoiceDisconnectedEvent;
import sx.blah.discord.handle.impl.events.guild.voice.user.UserVoiceChannelJoinEvent;
import sx.blah.discord.handle.impl.events.guild.voice.user.UserVoiceChannelLeaveEvent;
import sx.blah.discord.handle.impl.events.guild.voice.user.UserVoiceChannelMoveEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IVoiceChannel;

/**
 * Automatically pauses playback if everyone leaves the bot's voice channel.
 * Automatically unpauses if someone joins the previously empty channel.
 * Automatically destroys the session if the bot leaves its voice channel.
 */
class VoiceChannelEventListener {

	private static final Logger LOG = LoggerFactory.getLogger(VoiceChannelEventListener.class);

	private final GuildMusicManager guildMusicManager;
	private final BotUtils botUtils;

	@Inject
	public VoiceChannelEventListener(GuildMusicManager guildMusicManager, BotUtils botUtils) {
		this.guildMusicManager = Preconditions.checkNotNull(guildMusicManager,
				"discordAudioPlayerManager must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@EventSubscriber
	public void onUserVoiceChannelJoin(UserVoiceChannelJoinEvent event) {
		// Fired when a user joins any VC in a guild.
		LOG.debug("UserVoiceChannelJoinEvent fired");

		checkIntegrity(event.getGuild());

		IVoiceChannel botVc = event.getGuild().getConnectedVoiceChannel();
		if (event.getUser().getLongID() == event.getClient().getOurUser().getLongID()) {
			// It's just us. No need to auto pause or resume.
			LOG.debug("The bot has joined the voice channel '{}' in guild '{}'", botVc.getName(),
					event.getGuild().getName());
			return;
		} else if (botVc == null || event.getVoiceChannel().getLongID() != botVc.getLongID()) {
			// Event is not applicable to us because either we are not in a VC or the event
			// has nothing to do with our VC.
			return;
		}

		autoPauseAndResume(event.getGuild(), true);
	}

	@EventSubscriber
	public void onUserVoiceChannelLeave(UserVoiceChannelLeaveEvent event) {
		// Fired whenever a user is no longer connected to any voice channel in the
		// guild.
		// Note: If our bot user leaves a VC, a VoiceDisconnectedEvent will fire
		// instead.

		LOG.debug("UserVoiceChannelLeaveEvent fired");

		checkIntegrity(event.getGuild());

		IVoiceChannel botVc = event.getGuild().getConnectedVoiceChannel();
		if (botVc == null || event.getVoiceChannel().getLongID() != botVc.getLongID()) {
			// Event is not applicable to us because either we are not in a VC or the event
			// has nothing to do with our VC.
			return;
		}

		autoPauseAndResume(event.getGuild(), false);
	}

	@EventSubscriber
	public void onVoiceChannelMove(UserVoiceChannelMoveEvent event) {
		// Fired when a user moves to a different voice channel in the guild.
		LOG.debug("UserVoiceChannelMoveEvent fired");

		checkIntegrity(event.getGuild());

		IVoiceChannel botVc = event.getGuild().getConnectedVoiceChannel();
		if (botVc == null) {
			return;
		}
		
		boolean userLeftBotVc = event.getOldChannel().getLongID() == botVc.getLongID();
		boolean userJoinedBotVc = event.getNewChannel().getLongID() == botVc.getLongID();

		if (userLeftBotVc) {
			autoPauseAndResume(event.getGuild(), false);
		} else if (userJoinedBotVc){
			autoPauseAndResume(event.getGuild(), true);
		}

		// Event is not applicable to us because either we are not in a VC or the event
		// has nothing to do with our VC.
	}

	@EventSubscriber
	public void onVoiceDisconnected(VoiceDisconnectedEvent event) {
		// Fired when our bot user disconnects from a VC.

		LOG.debug("VoiceDisconnectedEvent fired");

		try {
			guildMusicManager.destroyAudioSessionFor(event.getGuild().getLongID());
		} catch (NoSessionException e) {

		}
	}

	/**
	 * Destroys the {@link AudioSession} for the given {@code guild} if there exists
	 * an {@link AudioSession} but the bot isn't connected to any voice channel.
	 * 
	 * @param guild
	 *            {@link IGuild} Non-null.
	 */
	private void checkIntegrity(IGuild guild) {
		IVoiceChannel botVc = guild.getConnectedVoiceChannel();

		if (botVc == null) {
			// If a session exists, but the bot isn't in a VC, then this is a bad state.
			// Destroy the session to fix itself.
			guildMusicManager.destroyAudioSessionFor(guild.getLongID());
		}
	}

	/**
	 * Automatically pauses or resumes the player when voice channel activity occurs
	 * in the given {@code guild}.
	 * 
	 * @param guild
	 *            {@link IGuild} where the voice activity occurred.
	 * @param userJoined
	 *            True if this method was called as a result of a user joining the
	 *            bot's VC. False if this method was called a result of a user
	 *            leaving the bot's VC.
	 */
	private void autoPauseAndResume(IGuild guild, boolean userJoined) {

		AudioSession audioSession = guildMusicManager.getAudioSession(guild.getLongID()).orElse(null);
		if (audioSession == null) {
			return;
		}

		IDiscordClient discordClient = guild.getClient();
		IVoiceChannel botVc = guild.getConnectedVoiceChannel();

		long ourUserId = discordClient.getOurUser().getLongID();

		long otherUsersCount = botVc.getUsersHere().stream().filter(user -> user.getLongID() != ourUserId).count();

		IChannel defaultChannel = discordClient.getChannelByID(audioSession.getDefaultChannelId());

		if (otherUsersCount == 1 && userJoined) {
			// Someone just joined.
			audioSession.setPaused(false);
			botUtils.sendMessage(defaultChannel, ":arrow_forward: Resuming playback.");
		} else if (otherUsersCount == 0) {
			// Everyone left.
			audioSession.setPaused(true);
			botUtils.sendMessage(defaultChannel,
					":pause_button: Paused because everyone left ``" + botVc.getName() + "``.");
		}
	}
}
