package net.tonbot.plugin.music;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import net.tonbot.plugin.music.permissions.MusicPermissions;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.obj.IGuild;

class GuildMusicManager {

	private final IDiscordClient discordClient;
	private final AudioSessionFactory audioSessionFactory;
	private final ConcurrentHashMap<Long, MusicState> states;

	@Inject
	public GuildMusicManager(
			IDiscordClient discordClient,
			AudioSessionFactory audioSessionFactory) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.audioSessionFactory = Preconditions.checkNotNull(audioSessionFactory,
				"audioSessionFactory must be non-null.");
		this.states = new ConcurrentHashMap<>();
	}

	/**
	 * Gets an {@link AudioSession}.
	 * 
	 * @param guild
	 *            The guild ID.
	 * @return {@link AudioSession}, if it exists.
	 */
	public Optional<AudioSession> getAudioSession(long guildId) {

		MusicState ms = this.states.computeIfAbsent(guildId, gid -> {
			return createMusicState(gid);
		});

		AudioSession as = ms.getAudioSession().orElse(null);
		if (as == null) {
			return Optional.empty();
		}

		return Optional.of(as);
	}

	/**
	 * Stops playback and destroys the session for the guild. No-op if this
	 * {@link IGuild} has not been seen by this {@link GuildMusicManager}.
	 * 
	 * @param guildId
	 *            Guild ID.
	 */
	public void destroyAudioSessionFor(long guildId) {
		MusicState ms = this.states.get(guildId);
		if (ms != null) {
			synchronized (ms) {
				ms.getAudioSession().ifPresent(as -> as.destroy());
				ms.setAudioSession(null);
			}
		}
	}

	/**
	 * Creates an {@link AudioSession} for a guild and text channel ID.
	 * 
	 * @param guildId
	 *            Guild ID.
	 * @param textChannelId
	 *            Text channel ID.
	 */
	public void initAudioSessionFor(long guildId, long textChannelId) {
		MusicState ms = this.states.computeIfAbsent(guildId, gid -> {
			return createMusicState(gid);
		});

		synchronized (ms) {
			Preconditions.checkState(!ms.getAudioSession().isPresent(), "MusicState already has an AudioSession.");

			AudioSession as = audioSessionFactory.create(guildId, textChannelId);
			ms.setAudioSession(as);
		}
	}

	/**
	 * Gets the {@link MusicPermissions} for the given guild. Will be created if it
	 * doesn't exist.
	 * 
	 * @param guildId
	 *            Guild ID.
	 * @return The {@link MusicPermissions} for the given {@code guild}
	 */
	public MusicPermissions getPermission(long guildId) {
		MusicState ms = this.states.computeIfAbsent(guildId, gid -> {
			return createMusicState(gid);
		});

		return ms.getPermissionManager();
	}

	private MusicState createMusicState(long guildId) {
		IGuild guild = discordClient.getGuildByID(guildId);

		// TODO: Permissions should be loaded from persistence store.
		MusicPermissions musicPermissions = new MusicPermissions(guild);
		musicPermissions.resetRules();
		return new MusicState(musicPermissions);
	}
}
