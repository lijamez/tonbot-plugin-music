package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import lombok.Getter;
import net.tonbot.common.BotUtils;
import net.tonbot.common.TonbotBusinessException;
import net.tonbot.common.TonbotTechnicalFault;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IUser;

class AudioSession extends AudioEventAdapter {

	private final IDiscordClient discordClient;
	private final AudioPlayerManager audioPlayerManager;
	private final AudioPlayer audioPlayer;

	@Getter
	private final long defaultChannelId;

	private final BotUtils botUtils;

	private TrackManager trackManager;
	private PlayMode playMode;
	private RepeatMode repeatMode;

	public AudioSession(IDiscordClient discordClient, AudioPlayerManager audioPlayerManager, AudioPlayer audioPlayer,
			long defaultChannelId, BotUtils botUtils) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.audioPlayerManager = Preconditions.checkNotNull(audioPlayerManager,
				"audioPlayerManager must be non-null.");
		this.audioPlayer = Preconditions.checkNotNull(audioPlayer, "audioPlayer must be non-null.");
		this.defaultChannelId = defaultChannelId;
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.repeatMode = RepeatMode.OFF;
		setPlayMode(PlayMode.STANDARD);
	}

	@Override
	public void onPlayerPause(AudioPlayer player) {
		// Player was paused
	}

	@Override
	public void onPlayerResume(AudioPlayer player) {
		// Player was resumed
	}

	@Override
	public void onTrackStart(AudioPlayer player, AudioTrack audioTrack) {
		// A track started playing
	}

	@Override
	public void onTrackEnd(AudioPlayer player, AudioTrack audioTrack, AudioTrackEndReason endReason) {
		if (endReason.mayStartNext) {

			if (repeatMode == RepeatMode.ONE) {
				// Play it again.
				player.playTrack(clone(audioTrack));
			} else {

				if (repeatMode == RepeatMode.ALL) {
					// Enqueue the track again.
					trackManager.put(clone(audioTrack));
				}

				playNext();
			}
		}

		// endReason == FINISHED: A track finished or died by an exception (mayStartNext
		// = true).
		// endReason == LOAD_FAILED: Loading of a track failed (mayStartNext = true).
		// endReason == STOPPED: The player was stopped.
		// endReason == REPLACED: Another track started playing while this had not
		// finished
		// endReason == CLEANUP: Player hasn't been queried for a while, if you want you
		// can put a
		// clone of this back to your queue
	}

	@Override
	public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
		// An already playing track threw an exception (track end event will still be
		// received separately)
		IChannel channel = discordClient.getChannelByID(defaultChannelId);
		botUtils.sendMessage(channel,
				"Failed to play **" + track.getInfo().title + "**: " + formatFriendlyException(exception));
	}

	@Override
	public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
		// Audio track has been unable to provide us any audio, might want to just start
		// a new track.

		IChannel channel = discordClient.getChannelByID(defaultChannelId);
		botUtils.sendMessage(channel, "Track **" + track.getInfo().title + "** is stuck. Moving right along...");

		skip();
	}

	/**
	 * Enqueues a song with an identifier. If the track is added successfully, it is
	 * up to the audio event adapter to determine what happens to the track (e.g. if
	 * it gets queued, or if it plays immediately, etc.).
	 * 
	 * @param identifier
	 *            An identifier for the track. Non-null.
	 * @param user
	 *            The {@link IUser} that queued the track. Non-null.
	 * @throws IllegalStateException
	 *             If there is no session.
	 * @return {@link AudioLoadResult}. Not null.
	 */
	public AudioLoadResult enqueue(String identifier, IUser user) {
		Preconditions.checkNotNull(identifier, "identifier must be non-null.");
		Preconditions.checkNotNull(user, "user must be non-null.");

		IChannel channel = discordClient.getChannelByID(defaultChannelId);

		TonbotAudioLoadResultHandler resultHandler = new TonbotAudioLoadResultHandler() {

			@Override
			public void trackLoaded(AudioTrack audioTrack) {
				enqueue(audioTrack, user);

				this.result = AudioLoadResult.builder().loadedTracks(ImmutableList.of(audioTrack)).build();
			}

			@Override
			public void playlistLoaded(AudioPlaylist loadedPlaylist) {
				List<AudioTrack> tracks = loadedPlaylist.getTracks();
				if (tracks.isEmpty()) {
					botUtils.sendMessage(channel, "There were no songs in that playlist. :thinking:");
				} else {
					tracks.forEach(track -> {
						ExtraTrackInfo extraTrackInfo = ExtraTrackInfo.builder().addedByUserId(user.getLongID())
								.addTimestamp(System.currentTimeMillis()).build();
						track.setUserData(extraTrackInfo);
					});

					trackManager.putAll(tracks);

					this.result = AudioLoadResult.builder().loadedTracks(tracks).playlistName(loadedPlaylist.getName())
							.build();
				}
			}

			@Override
			public void noMatches() {
				this.result = AudioLoadResult.builder().loadedTracks(ImmutableList.of()).build();
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				this.result = AudioLoadResult.builder().exception(exception).build();
			}

		};

		try {
			audioPlayerManager.loadItem(identifier, resultHandler).get();

			return resultHandler.getResult();
		} catch (InterruptedException | ExecutionException e) {
			throw new TonbotTechnicalFault("Failed to load track(s).", e);
		}
	}

	/**
	 * Enqueues a given track, while appending extra track info.
	 * 
	 * @param track
	 *            {@link AudioTrack}. Non-null.
	 * @param user
	 *            The {@link IUser} that enqueued the track. Non-null.
	 */
	public void enqueue(AudioTrack track, IUser user) {
		Preconditions.checkNotNull(track, "track must be non-null.");
		Preconditions.checkNotNull(user, "user must be non-null.");

		AudioTrack clonedTrack = track.makeClone();
		clonedTrack.setUserData(ExtraTrackInfo.builder().addedByUserId(user.getLongID())
				.addTimestamp(System.currentTimeMillis()).build());

		trackManager.put(clonedTrack);
	}

	/**
	 * If this player is paused, then it resumes. Then, if there is no playing
	 * track, it plays the next song, if one exists. If there is already a track
	 * playing, then no-op.
	 */
	public void play() {
		if (audioPlayer.isPaused()) {
			audioPlayer.setPaused(false);
		}

		if (audioPlayer.getPlayingTrack() == null) {
			playNext();
		}
	}

	private void playNext() {
		trackManager.next().ifPresent(nextTrack -> audioPlayer.playTrack(nextTrack));
	}

	/**
	 * Gets information about this {@link AudioSession}.
	 * 
	 * @return {@link AudioSessionStatus}. Never null.
	 */
	public AudioSessionStatus getStatus() {
		return AudioSessionStatus.builder().nowPlaying(audioPlayer.getPlayingTrack())
				.upcomingTracks(trackManager.getView()).playMode(playMode).repeatMode(repeatMode).build();
	}

	/**
	 * Destroys the audio player.
	 */
	public void destroy() {
		audioPlayer.destroy();
	}

	/**
	 * Stops playing the current track. The songs up next are preserved.
	 */
	public void stop() {
		audioPlayer.stopTrack();
	}

	/**
	 * Sets the pause state.
	 * 
	 * @param paused
	 *            True if the player should be paused. False to resume.
	 */
	public void setPaused(boolean paused) {
		audioPlayer.setPaused(paused);
	}

	/**
	 * Determines if the player is paused.
	 * 
	 * @return
	 */
	public boolean isPaused() {
		return audioPlayer.isPaused();
	}

	/**
	 * Sets the {@link PlayMode}.
	 * 
	 * @param mode
	 *            {@link PlayMode}. Non-null.
	 */
	public void setPlayMode(PlayMode mode) {
		Preconditions.checkNotNull(mode, "mode must be non-null.");

		List<AudioTrack> audioTracks;
		if (this.trackManager == null) {
			audioTracks = new ArrayList<>();
		} else {
			audioTracks = this.trackManager.getView();
		}

		if (mode == PlayMode.STANDARD) {
			this.trackManager = TrackManagers.sortedByAddTimestamp(audioTracks);
		} else if (mode == PlayMode.SHUFFLE) {
			this.trackManager = TrackManagers.shuffled(audioTracks);
		} else if (mode == PlayMode.ROUND_ROBIN) {
			// TODO: Switching mode away from Round robin will only grab a subset of tracks.
			this.trackManager = TrackManagers.roundRobin(audioTracks);
		} else {
			throw new IllegalArgumentException("Unknown PlayMode " + mode);
		}

		this.playMode = mode;
	}

	/**
	 * Sets the {@link RepeatMode}.
	 * 
	 * @param value
	 *            The {@link RepeatMode}. Non-null.
	 */
	public void setLoopingMode(RepeatMode mode) {
		Preconditions.checkNotNull(mode, "mode must be non-null.");

		this.repeatMode = mode;
	}

	/**
	 * Skips the currently playing track and moves onto the next one. If there is no
	 * next track, then the player is stopped. If there is no current track, then
	 * no-op.
	 * 
	 * @return The skipped track.
	 */
	public Optional<AudioTrack> skip() {
		AudioTrack skipTrack = audioPlayer.getPlayingTrack();
		if (skipTrack == null) {
			return Optional.empty();
		}

		Optional<AudioTrack> nextTrack = trackManager.next();
		if (nextTrack.isPresent()) {

			if (repeatMode == RepeatMode.ALL) {
				// Enqueue the skipped track again.
				trackManager.put(clone(skipTrack));
			}

			audioPlayer.playTrack(nextTrack.get());
		} else {
			this.stop();
		}

		return Optional.of(skipTrack);
	}

	/**
	 * Skips tracks by {@link Predicate}. Tracks which satisfy the predicate will be
	 * removed.
	 * 
	 * @param predicate
	 *            {@link Predicate}. Non-null.
	 * @return The skipped tracks.
	 */
	public List<AudioTrack> skip(Predicate<AudioTrack> predicate) {
		Preconditions.checkNotNull(predicate, "predicate must be non-null.");

		return this.trackManager.removeAll(predicate);
	}

	/**
	 * Seeks within the currently playing track. The track's new position is bounded
	 * by the beginning of the track and the duration of the track (as indicated by
	 * the AudioTrack's getDuration()). <br/>
	 * If there is no currently playing track, then no-op.
	 * 
	 * @param time
	 *            The time to seek by, in milliseconds. Must be non-negative if
	 *            SeekType is ABSOLUTE.
	 * @param seekType
	 *            {@link SeekType}. Non-null.<br/>
	 *            If set to DELTA, then the time is used to move forward and back by
	 *            that amount. Time may be positive (move forward) or negative (move
	 *            backward) <br/>
	 *            If set to ABSOLUTE, then the track's position will be set to the
	 *            given time. The time must also be non-negative in this case.
	 * @return The {@link AudioTrack} whose position was just moved. Will be empty
	 *         if there is no currently playing track.
	 * @throws IllegalArgumentException
	 *             If the time is negative and seekType is ABSOLUTE.
	 * @throws IllegalStateException
	 *             If the track is not seekable.
	 */
	public Optional<AudioTrack> seek(long time, SeekType seekType) {
		Preconditions.checkNotNull(seekType, "seekType must be non-null.");

		if (seekType == SeekType.ABSOLUTE) {
			Preconditions.checkArgument(time >= 0, "time must be non-negative when seekType is ABSOLUTE.");
		}

		AudioTrack nowPlaying = audioPlayer.getPlayingTrack();
		if (nowPlaying == null) {
			return Optional.empty();
		}

		Preconditions.checkState(nowPlaying.isSeekable(), "The currently playing track is not seekable.");

		long newPosition = seekType == SeekType.DELTA ? nowPlaying.getPosition() + time : time;

		newPosition = Math.max(0, newPosition);
		newPosition = Math.min(nowPlaying.getDuration(), newPosition);

		nowPlaying.setPosition(newPosition);

		return Optional.of(nowPlaying);
	}

	private AudioTrack clone(AudioTrack originalAudioTrack) {
		AudioTrack clonedAudioTrack = originalAudioTrack.makeClone();
		ExtraTrackInfo originalExtraTrackInfo = originalAudioTrack.getUserData(ExtraTrackInfo.class);

		clonedAudioTrack.setUserData(ExtraTrackInfo.builder().addedByUserId(originalExtraTrackInfo.getAddedByUserId())
				.addTimestamp(System.currentTimeMillis()).build());

		return clonedAudioTrack;
	}

	private String formatFriendlyException(FriendlyException friendlyException) {
		Throwable cause = friendlyException.getCause();
		if (cause != null && cause instanceof TonbotBusinessException) {
			return cause.getMessage();
		} else {
			return friendlyException.getMessage();
		}
	}

	private static class TrackManagers {

		public static SortingTrackManager sortedByAddTimestamp(Collection<AudioTrack> tracks) {
			SortingTrackManager trackManager = new SortingTrackManager(new Comparator<AudioTrack>() {

				@Override
				public int compare(AudioTrack t1, AudioTrack t2) {

					long diff = t1.getUserData(ExtraTrackInfo.class).getAddTimestamp()
							- t2.getUserData(ExtraTrackInfo.class).getAddTimestamp();
					if (diff < 0) {
						return -1;
					} else if (diff > 0) {
						return 1;
					}

					return 0;
				}
			});
			trackManager.putAll(tracks);

			return trackManager;
		}

		public static ShufflingTrackManager shuffled(Collection<AudioTrack> tracks) {
			ShufflingTrackManager trackManager = new ShufflingTrackManager();
			trackManager.putAll(tracks);
			return trackManager;
		}

		public static RoundRobinTrackManager roundRobin(Collection<AudioTrack> tracks) {
			RoundRobinTrackManager trackManager = new RoundRobinTrackManager();
			trackManager.putAll(tracks);
			return trackManager;
		}
	}
}
