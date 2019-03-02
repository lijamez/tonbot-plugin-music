package net.tonbot.plugin.music;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import net.tonbot.common.TonbotTechnicalFault;
import net.tonbot.plugin.music.permissions.Action;
import net.tonbot.plugin.music.permissions.MusicPermissions;
import sx.blah.discord.api.IDiscordClient;

class GuildMusicManager {

	private static final Logger LOG = LoggerFactory.getLogger(GuildMusicManager.class);

	private final IDiscordClient discordClient;
	private final AudioSessionFactory audioSessionFactory;
	private final File saveDir;
	private final ObjectMapper objectMapper;
	private final ConcurrentHashMap<Long, MusicState> states;
	private final Lock saveDirLock;

	@Inject
	public GuildMusicManager(IDiscordClient discordClient, AudioSessionFactory audioSessionFactory, File saveDir,
			ObjectMapper objectMapper) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.audioSessionFactory = Preconditions.checkNotNull(audioSessionFactory,
				"audioSessionFactory must be non-null.");
		this.saveDir = Preconditions.checkNotNull(saveDir, "saveDir must be non-null.");
		Preconditions.checkArgument(!saveDir.exists() || saveDir.isDirectory(), "saveDir must be a directory.");
		this.objectMapper = Preconditions.checkNotNull(objectMapper, "objectMapper must be non-null.");
		this.states = new ConcurrentHashMap<>();
		this.saveDirLock = new ReentrantLock();
	}

	/**
	 * Loads permissions from the directory at {@code saveDir}.
	 */
	public void load() {
		saveDirLock.lock();
		try {
			if (saveDir.exists()) {
				if (!saveDir.isDirectory()) {
					throw new TonbotTechnicalFault(
							"Unable to load. Save directory " + saveDir.getAbsolutePath() + " is not a directory.");
				}

				File[] files = saveDir.listFiles();
				for (File file : files) {
					long guildId;
					try {
						guildId = Long.parseLong(file.getName());
					} catch (IllegalArgumentException e) {
						continue;
					}

					try {
						Map<Long, Set<Action>> permissions = objectMapper.readValue(file,
								new TypeReference<Map<Long, Set<Action>>>() {
								});

						MusicState ms = this.states.computeIfAbsent(guildId,
								gid -> new MusicState(new MusicPermissions(discordClient, guildId)));
						ms.getPermissionManager().setPermissions(permissions);

						this.states.put(guildId, ms);
					} catch (IOException e) {
						LOG.warn("Could not load permissions from {}", file.getAbsolutePath(), e);
					}
				}
			}
		} finally {
			saveDirLock.unlock();
		}
	}

	/**
	 * Saves all guild states to the directory specified by {@code saveDir}.
	 * 
	 * @throws UncheckedIOException
	 *             if an IOException occurs.
	 */
	public void save() {
		try {
			// Write to a temp dir first and then atomically move it to the target
			File tmpDir = new File(Files.createTempDirectory("gmm").toString());
			LOG.info("Temp directory created at: " + tmpDir.getAbsolutePath());
			for (Entry<Long, MusicState> entry : states.entrySet()) {
				long guildId = entry.getKey();
				File guildFile = new File(tmpDir, Long.toString(guildId));
				guildFile.createNewFile();

				MusicState state = entry.getValue();
				objectMapper.writeValue(guildFile, state.getPermissionManager().getPermissions());
			}

			saveDirLock.lock();
			try {
				FileUtils.deleteDirectory(saveDir);
				try {
					Files.move(tmpDir.toPath(), saveDir.toPath(), StandardCopyOption.REPLACE_EXISTING,
							StandardCopyOption.ATOMIC_MOVE);
				} catch (AtomicMoveNotSupportedException e) {
					// Atomic move may not be supported on, say, network shares.
					Files.move(tmpDir.toPath(), saveDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
				
			} finally {
				saveDirLock.unlock();
			}

			LOG.info("Permissions saved to: " + saveDir.getAbsolutePath());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
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
	 * Stops playback and destroys the session for the guild. No-op if there is 
	 * no session for this guild.
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
		MusicPermissions musicPermissions = new MusicPermissions(discordClient, guildId);
		musicPermissions.resetRules();
		return new MusicState(musicPermissions);
	}
}
