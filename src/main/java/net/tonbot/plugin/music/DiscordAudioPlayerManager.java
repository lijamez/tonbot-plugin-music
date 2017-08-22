package net.tonbot.plugin.music;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IUser;

class DiscordAudioPlayerManager {

	private static final Logger LOG = LoggerFactory.getLogger(DiscordAudioPlayerManager.class);

	private final AudioPlayerManager apm;
	private final BotUtils botUtils;
	private final ConcurrentHashMap<Long, AudioSession> audioSessions;

	@Inject
	public DiscordAudioPlayerManager(AudioPlayerManager apm, BotUtils botUtils) {
		this.apm = Preconditions.checkNotNull(apm, "apm must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.audioSessions = new ConcurrentHashMap<>();
	}

	/**
	 * Sets up a session for the guild. No-op if a session already exists.
	 * 
	 * @param guild
	 *            {@link IGuild}. Non-null.
	 * @param channel
	 *            The default channel which messages will be sent to.,
	 */
	public void initFor(IGuild guild, IChannel defaultChannel) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");
		Preconditions.checkNotNull(defaultChannel, "defaultChannel must be non-null.");

		audioSessions.computeIfAbsent(guild.getLongID(), guildId -> {
			AudioPlayer audioPlayer = apm.createPlayer();
			guild.getAudioManager().setAudioProvider(new LavaplayerAudioProvider(audioPlayer));

			AudioSession audioSession = new AudioSession(audioPlayer, defaultChannel.getLongID(), botUtils);
			audioPlayer.addListener(audioSession);

			return audioSession;
		});
	}

	/**
	 * Stops playback and destroys the session for the guild. No-op if a session
	 * doesn't exist.
	 * 
	 * @param guild
	 *            {@link IGuild}. Non-null.
	 */
	public void destroyFor(IGuild guild) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");

		AudioSession audioSession = audioSessions.remove(guild.getLongID());
		if (audioSession != null) {
			audioSession.destroy();
		}
	}

	/**
	 * Checks whether if a session exists for the guild.
	 * 
	 * @param guild
	 *            {@link IGuild}. Non-null.
	 * @return True if there is a session. False otherwise.
	 */
	public boolean hasSession(IGuild guild) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");

		return audioSessions.containsKey(guild.getLongID());
	}

	/**
	 * Enqueues a song with an identifier. If the track is added successfully, it is
	 * up to the audio event adapter to determine what happens to the track (e.g. if
	 * it gets queued, or if it plays immediately, etc.).
	 * 
	 * @param identifier
	 *            An identifier for the track. Non-null.
	 * @param channel
	 *            {@link IChannel}. Non-null.
	 * @param user
	 *            The {@link IUser} that queued the track. Non-null.
	 * @throws IllegalStateException
	 *             If there is no session.
	 */
	public void enqueue(String identifier, IGuild guild, IUser user) {
		Preconditions.checkNotNull(identifier, "identifier must be non-null.");
		Preconditions.checkNotNull(guild, "guild must be non-null.");
		Preconditions.checkNotNull(user, "user must be non-null.");

		AudioSession audioSession = audioSessions.get(guild.getLongID());
		Preconditions.checkState(audioSession != null, "There is no audio session for this guild.");

		IChannel channel = guild.getClient().getChannelByID(audioSession.getDefaultChannelId());

		try {
			apm.loadItem(identifier, new AudioLoadResultHandler() {

				@Override
				public void trackLoaded(AudioTrack track) {
					audioSession.enqueue(track, channel, user);
				}

				@Override
				public void playlistLoaded(AudioPlaylist playlist) {
					audioSession.enqueuePlaylist(playlist);
				}

				@Override
				public void noMatches() {
					botUtils.sendMessage(channel, "I can't play that. :shrug:");
				}

				@Override
				public void loadFailed(FriendlyException exception) {
					botUtils.sendMessage(channel, "I couldn't load it.\n" + exception.getMessage());
				}

			}).get();
		} catch (InterruptedException | ExecutionException e) {
			LOG.error("Failed to load a track.", e);
		}
	}

	/**
	 * Stops the player.
	 * 
	 * @param guild
	 *            {@link IGuild}. Non-null.
	 * @throws IllegalStateException
	 *             If there is no session.
	 */
	public void stop(IGuild guild) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");

		AudioSession audioSession = audioSessions.get(guild.getLongID());
		Preconditions.checkState(audioSession != null, "There is no audio session for this guild.");

		audioSession.stopTrack();
	}

	/**
	 * Plays the next song, if one exists. If the player is paused, it will be
	 * unpaused. If player is currently playing, then no-op.
	 * 
	 * @param guild
	 *            {@link IGuild}. Non-null.
	 */
	public void play(IGuild guild) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");

		AudioSession audioSession = audioSessions.get(guild.getLongID());
		Preconditions.checkState(audioSession != null, "There is no audio session for this guild.");

		audioSession.play();
	}

	/**
	 * Sets the pause state.
	 * 
	 * @param channel
	 *            {@link IChannel}. Non-null.
	 * @param paused
	 *            True if the player should be paused. False to resume.
	 * @throws IllegalStateException
	 *             If there is no session.
	 */
	public void setPauseState(IGuild guild, boolean paused) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");

		AudioSession audioSession = audioSessions.get(guild.getLongID());
		Preconditions.checkState(audioSession != null, "There is no audio session for this guild.");

		audioSession.setPaused(paused);
	}

	/**
	 * Gets the default channel ID for the guild's session.
	 * 
	 * @param guild
	 *            {@link IGuild}. Non-null.
	 * @return The default channel ID. Null if there is no session.
	 */
	public Optional<Long> getDefaultChannelId(IGuild guild) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");

		AudioSession audioSession = audioSessions.get(guild.getLongID());

		Long defaultChannelId = audioSession != null ? audioSession.getDefaultChannelId() : null;
		return Optional.ofNullable(defaultChannelId);
	}

	public Optional<AudioSessionStatus> getSessionStatus(IGuild guild) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");

		AudioSession audioSession = audioSessions.get(guild.getLongID());

		AudioSessionStatus audioSessionStatus = audioSession != null ? audioSession.getStatus() : null;

		return Optional.ofNullable(audioSessionStatus);
	}
	
	public void setMode(IGuild guild, PlayMode playMode) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");
		Preconditions.checkNotNull(playMode, "playMode must be non-null.");

		AudioSession audioSession = audioSessions.get(guild.getLongID());
		Preconditions.checkState(audioSession != null, "There is no audio session for this guild.");

		audioSession.setMode(playMode);
	}
}
