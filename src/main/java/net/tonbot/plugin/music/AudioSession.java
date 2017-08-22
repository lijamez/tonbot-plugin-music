package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.NoSuchElementException;

import com.google.common.base.Preconditions;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import lombok.Getter;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IUser;

class AudioSession extends AudioEventAdapter {

	private final AudioPlayer audioPlayer;

	@Getter
	private final long defaultChannelId;

	private final BotUtils botUtils;

	private Playlist playlist;
	private PlayMode playMode;
	private Track currentTrack = null;

	public AudioSession(AudioPlayer audioPlayer, long defaultChannelId, BotUtils botUtils) {
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
		this.currentTrack = null;

		if (endReason.mayStartNext) {
			if (playlist.hasNext()) {
				Track track = playlist.next();
				player.playTrack(track.getAudioTrack());
				this.currentTrack = track;
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

	public void enqueue(AudioTrack audioTrack, IChannel channel, IUser user) {
		Preconditions.checkNotNull(audioTrack, "audioTrack must be non-null.");
		Preconditions.checkNotNull(channel, "channel must be non-null.");
		Preconditions.checkNotNull(user, "user must be non-null.");

		Track newTrack = Track.builder()
				.addedByUserId(user.getLongID())
				.addTimestamp(System.currentTimeMillis())
				.audioTrack(audioTrack)
				.build();

		playlist.put(newTrack);

		botUtils.sendMessage(channel, "Queued track: **" + audioTrack.getInfo().title + "** by **"
				+ user.getNicknameForGuild(channel.getGuild()) + "**");
	}

	public void enqueuePlaylist(AudioPlaylist playlist) {
		Preconditions.checkNotNull(playlist, "playlist must be non-null.");
		// TODO
	}

	/**
	 * Plays the next song, if one exists. If there is already a track playing, then
	 * no-op.
	 */
	public void play() {
		if (!audioPlayer.isPaused() && audioPlayer.getPlayingTrack() != null) {
			return;
		}

		Track nextTrack;
		try {
			nextTrack = playlist.next();
		} catch (NoSuchElementException e) {
			nextTrack = null;
		}

		if (nextTrack != null) {
			audioPlayer.startTrack(nextTrack.getAudioTrack(), true);
			this.currentTrack = nextTrack;
		}
	}

	public AudioSessionStatus getStatus() {
		return AudioSessionStatus.builder()
				.nowPlaying(currentTrack)
				.upcomingTracks(playlist.getView())
				.playMode(playMode)
				.build();
	}

	public void destroy() {
		audioPlayer.destroy();
	}

	public void stopTrack() {
		audioPlayer.stopTrack();
	}

	public void setPaused(boolean paused) {
		audioPlayer.setPaused(paused);
	}

	public boolean isPaused() {
		return audioPlayer.isPaused();
	}

	public AudioTrack getPlayingTrack() {
		return audioPlayer.getPlayingTrack();
	}

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

	private static class PlaylistSelector {

		public static SortedPlaylist sortedByAddTimestamp(Collection<Track> tracks) {
			return new SortedPlaylist(tracks, new Comparator<Track>() {

				@Override
				public int compare(Track t1, Track t2) {
					long diff = t1.getAddTimestamp() - t2.getAddTimestamp();
					if (diff < 0) {
						return -1;
					} else if (diff > 0) {
						return 1;
					}

					return 0;
				}

			});
		}

		public static ShuffledPlaylist shuffled(Collection<Track> tracks) {
			return new ShuffledPlaylist(tracks);
		}

	}
}
