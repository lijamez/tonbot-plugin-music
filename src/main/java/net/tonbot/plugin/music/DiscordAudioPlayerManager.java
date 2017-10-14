package net.tonbot.plugin.music;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSearchProvider;

import lombok.Data;
import lombok.NonNull;
import net.tonbot.common.BotUtils;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;

class DiscordAudioPlayerManager {

	private final IDiscordClient discordClient;
	private final AudioPlayerManager apm;
	private final YoutubeSearchProvider ytSearchProvider;
	private final BotUtils botUtils;
	private final ConcurrentHashMap<Long, LockableAudioSession> audioSessions;

	@Inject
	public DiscordAudioPlayerManager(
			IDiscordClient discordClient,
			AudioPlayerManager apm,
			YoutubeSearchProvider ytSearchProvider,
			BotUtils botUtils) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.apm = Preconditions.checkNotNull(apm, "apm must be non-null.");
		this.ytSearchProvider = Preconditions.checkNotNull(ytSearchProvider, "ytSearchProvider must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.audioSessions = new ConcurrentHashMap<>();
	}

	/**
	 * Checks out a session and locks it. Subsequent checkouts for the same session
	 * will be blocked until the session is returned in which case the lock is
	 * released.
	 * 
	 * @return The {@link AudioSession}, if a session exists.
	 * @throws NoSessionException
	 *             if there is no session for the given guild.
	 */
	public AudioSession checkout(IGuild guild) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");

		LockableAudioSession lockableSession = audioSessions.get(guild.getLongID());

		if (lockableSession == null) {
			throw new NoSessionException("There is no session for the provided guild.");
		}

		lockableSession.getLock().lock();

		return lockableSession.getAudioSession();
	}

	/**
	 * Checks in the session that is associated with the given {@link IGuild}.
	 * 
	 * @param guild
	 *            {@link IGuild}. Non-null.
	 * @throws NoSessionException
	 *             if there is no session for the provided {@link IGuild}.
	 */
	public void checkin(IGuild guild) {
		LockableAudioSession lockableSession = audioSessions.get(guild.getLongID());

		if (lockableSession == null) {
			throw new NoSessionException("There is no session for this guild.");
		}

		lockableSession.getLock().unlock();
	}

	@Data
	private static class LockableAudioSession {
		@NonNull
		private final Lock lock;

		@NonNull
		private final AudioSession audioSession;
	}

	/**
	 * Sets up a session for the guild. No-op if a session already exists.
	 * 
	 * @param guild
	 *            {@link IGuild}. Non-null.
	 * @param channel
	 *            The default channel which messages will be sent to.
	 */
	public void initFor(IGuild guild, IChannel defaultChannel) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");
		Preconditions.checkNotNull(defaultChannel, "defaultChannel must be non-null.");

		audioSessions.computeIfAbsent(guild.getLongID(), guildId -> {
			AudioPlayer audioPlayer = apm.createPlayer();
			guild.getAudioManager().setAudioProvider(new LavaplayerAudioProvider(audioPlayer));

			AudioSession audioSession = new AudioSession(
					discordClient, apm, audioPlayer, ytSearchProvider, defaultChannel.getLongID(), botUtils);
			audioPlayer.addListener(audioSession);

			return new LockableAudioSession(new ReentrantLock(), audioSession);
		});
	}

	/**
	 * Stops playback and destroys the session for the guild.
	 * 
	 * @param guild
	 *            {@link IGuild}. Non-null.
	 * @throws NoSessionException
	 *             If there is no session for the provided guild.
	 */
	public void destroyFor(IGuild guild) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");

		LockableAudioSession lockableSession = audioSessions.remove(guild.getLongID());

		if (lockableSession != null) {
			lockableSession.getLock().lock();
			try {
				lockableSession.getAudioSession().destroy();
			} finally {
				lockableSession.getLock().unlock();
			}
			
		} else {
			throw new NoSessionException("There is no session for the provided guild.");
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
}
