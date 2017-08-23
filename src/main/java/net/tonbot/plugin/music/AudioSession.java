package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import lombok.Getter;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IUser;

class AudioSession extends AudioEventAdapter {

	private static final Logger LOG = LoggerFactory.getLogger(AudioSession.class);

	private final AudioPlayerManager audioPlayerManager;
	private final AudioPlayer audioPlayer;

	@Getter
	private final long defaultChannelId;

	private final BotUtils botUtils;

	private Playlist playlist;
	private PlayMode playMode;

	public AudioSession(
			AudioPlayerManager audioPlayerManager,
			AudioPlayer audioPlayer,
			long defaultChannelId,
			BotUtils botUtils) {
		this.audioPlayerManager = Preconditions.checkNotNull(audioPlayerManager,
				"audioPlayerManager must be non-null.");
		this.audioPlayer = Preconditions.checkNotNull(audioPlayer, "audioPlayer must be non-null.");
		this.defaultChannelId = defaultChannelId;
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.playlist = PlaylistSelector.sortedByAddTimestamp(new ArrayList<>());
		this.playMode = PlayMode.STANDARD;
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
			if (playlist.hasNext()) {
				AudioTrack track = playlist.next();
				playTrack(track);
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
	}

	@Override
	public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
		// Audio track has been unable to provide us any audio, might want to just start
		// a new track
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

		IChannel channel = guild.getClient().getChannelByID(defaultChannelId);

		try {
			audioPlayerManager.loadItem(identifier, new AudioLoadResultHandler() {

				@Override
				public void trackLoaded(AudioTrack audioTrack) {
					ExtraTrackInfo extraTrackInfo = ExtraTrackInfo.builder()
							.addedByUserId(user.getLongID())
							.addTimestamp(System.currentTimeMillis())
							.build();
					audioTrack.setUserData(extraTrackInfo);

					playlist.put(audioTrack);

					botUtils.sendMessage(channel, "Queued track: **" + audioTrack.getInfo().title + "** by **"
							+ user.getNicknameForGuild(channel.getGuild()) + "**");
				}

				@Override
				public void playlistLoaded(AudioPlaylist loadedPlaylist) {
					List<AudioTrack> tracks = loadedPlaylist.getTracks();
					if (tracks.isEmpty()) {
						botUtils.sendMessage(channel, "There were no songs in that playlist. :thinking:");
					} else {
						tracks.forEach(track -> {
							ExtraTrackInfo extraTrackInfo = ExtraTrackInfo.builder()
									.addedByUserId(user.getLongID())
									.addTimestamp(System.currentTimeMillis())
									.build();
							track.setUserData(extraTrackInfo);
						});

						playlist.putAll(tracks);

						StringBuffer sb = new StringBuffer();
						sb.append("Added ").append(tracks.size()).append(" tracks from playlist");

						if (!StringUtils.isBlank(loadedPlaylist.getName())) {
							sb.append(" **").append(loadedPlaylist.getName()).append("**");
						}

						sb.append(".");

						botUtils.sendMessage(channel, sb.toString());
					}
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
	 * If this player is paused, then it resumes. Then, if there is no playing
	 * track, it plays the next song, if one exists. If there is already a track
	 * playing, then no-op.
	 */
	public void play() {
		if (audioPlayer.isPaused()) {
			audioPlayer.setPaused(false);
		}

		if (audioPlayer.getPlayingTrack() == null) {
			AudioTrack nextTrack;
			try {
				nextTrack = playlist.next();
			} catch (NoSuchElementException e) {
				nextTrack = null;
			}

			if (nextTrack != null) {
				audioPlayer.startTrack(nextTrack, true);
			}
		}
	}

	/**
	 * Gets information about this {@link AudioSession}.
	 * 
	 * @return {@link AudioSessionStatus}. Never null.
	 */
	public AudioSessionStatus getStatus() {
		return AudioSessionStatus.builder()
				.nowPlaying(audioPlayer.getPlayingTrack())
				.upcomingTracks(playlist.getView())
				.playMode(playMode)
				.build();
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
	public void setMode(PlayMode mode) {
		Preconditions.checkNotNull(mode, "mode must be non-null.");

		if (mode == PlayMode.STANDARD) {
			this.playlist = PlaylistSelector.sortedByAddTimestamp(this.playlist.getView());
		} else if (mode == PlayMode.SHUFFLED) {
			this.playlist = PlaylistSelector.shuffled(this.playlist.getView());
		} else {
			throw new IllegalArgumentException("Unknown PlayMode " + mode);
		}

		this.playMode = mode;
	}

	private void playTrack(AudioTrack track) {
		audioPlayer.playTrack(track);
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

		try {
			AudioTrack nextTrack = playlist.next();
			this.playTrack(nextTrack);
		} catch (NoSuchElementException e) {
			this.stop();
		}

		return Optional.of(skipTrack);
	}

	/**
	 * Skips an item in the queue.
	 * 
	 * @param i
	 *            The index of the track to skip.
	 * @return The skipped {@link AudioTrack}.
	 * @throws IndexOutOfBoundsException
	 *             if the provided index is not within playlist bounds.
	 */
	public AudioTrack skip(int i) {
		AudioTrack trackToSkip = this.playlist.getView().get(i);
		this.playlist.remove(trackToSkip);
		return trackToSkip;
	}

	private static class PlaylistSelector {

		public static SortedPlaylist sortedByAddTimestamp(Collection<AudioTrack> tracks) {
			return new SortedPlaylist(tracks, new Comparator<AudioTrack>() {

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
		}

		public static ShuffledPlaylist shuffled(Collection<AudioTrack> tracks) {
			return new ShuffledPlaylist(tracks);
		}
	}
}
