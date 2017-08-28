package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSearchProvider;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import lombok.Getter;
import net.tonbot.common.BotUtils;
import net.tonbot.common.TonbotBusinessException;
import net.tonbot.common.TonbotTechnicalFault;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;
import sx.blah.discord.util.RequestBuffer;

class AudioSession extends AudioEventAdapter {

	private final IDiscordClient discordClient;
	private final AudioPlayerManager audioPlayerManager;
	private final AudioPlayer audioPlayer;
	private final YoutubeSearchProvider ytSearchProvider;
	private final Map<Long, SearchResults> searchResultsByUserId;

	@Getter
	private final long defaultChannelId;

	private final BotUtils botUtils;

	private TrackManager trackManager;
	private PlayMode playMode;
	private RepeatMode repeatMode;

	public AudioSession(
			IDiscordClient discordClient,
			AudioPlayerManager audioPlayerManager,
			AudioPlayer audioPlayer,
			YoutubeSearchProvider ytSearchProvider,
			long defaultChannelId,
			BotUtils botUtils) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.audioPlayerManager = Preconditions.checkNotNull(audioPlayerManager,
				"audioPlayerManager must be non-null.");
		this.audioPlayer = Preconditions.checkNotNull(audioPlayer, "audioPlayer must be non-null.");
		this.ytSearchProvider = Preconditions.checkNotNull(ytSearchProvider, "ytSearchProvider must be non-null.");
		this.searchResultsByUserId = new HashMap<>();
		this.defaultChannelId = defaultChannelId;
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.trackManager = TrackManagers.sortedByAddTimestamp(new ArrayList<>());
		this.playMode = PlayMode.STANDARD;
		this.repeatMode = RepeatMode.OFF;
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
	 */
	public void enqueue(String identifier, IUser user) {
		Preconditions.checkNotNull(identifier, "identifier must be non-null.");
		Preconditions.checkNotNull(user, "user must be non-null.");

		IChannel channel = discordClient.getChannelByID(defaultChannelId);

		Future<IMessage> ackMessageFuture = RequestBuffer.request(() -> {
			return channel.sendMessage("Finding tracks for ``" + identifier + "``...");
		});

		try {
			audioPlayerManager.loadItem(identifier, new AudioLoadResultHandler() {

				@Override
				public void trackLoaded(AudioTrack audioTrack) {
					enqueue(audioTrack, user);

					botUtils.sendMessage(channel, "**" + audioTrack.getInfo().title + "** was queued by **"
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

						trackManager.putAll(tracks);

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
					// Instead of simply failing. We should try to look it up on Youtube.
					AudioItem audioItem = ytSearchProvider.loadSearchResult(identifier);
					if (audioItem == AudioReference.NO_TRACK) {
						botUtils.sendMessage(channel, "I couldn't find anything. :shrug:");
					} else if (audioItem instanceof AudioPlaylist) {
						// So we found some results. Show them to the user and then let them pick.
						AudioPlaylist queryResults = (AudioPlaylist) audioItem;

						StringBuffer sb = new StringBuffer();
						sb.append("**Search Results:**\n");

						for (int i = 0; i < queryResults.getTracks().size(); i++) {
							AudioTrack track = queryResults.getTracks().get(i);
							sb.append("``[").append(i + 1)
									.append("]`` **").append(track.getInfo().title)
									.append("** (").append(TimeFormatter.toFriendlyString(track.getInfo().length))
									.append(")\n");
						}

						IMessage messageWithSearchResults = botUtils.sendMessageSync(channel, sb.toString());

						SearchResults searchResults = new SearchResults(queryResults, messageWithSearchResults);

						searchResultsByUserId.put(user.getLongID(), searchResults);
					} else if (audioItem instanceof AudioTrack) {
						// Found an exact match. Queue it.
						trackLoaded((AudioTrack) audioItem);
					}
				}

				@Override
				public void loadFailed(FriendlyException exception) {
					botUtils.sendMessage(channel, formatFriendlyException(exception));
				}

			}).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new TonbotTechnicalFault("Failed to load track(s).", e);
		} finally {
			try {
				IMessage ackMessage = ackMessageFuture.get();
				ackMessage.delete();
			} catch (InterruptedException | ExecutionException | DiscordException | RateLimitException
					| MissingPermissionsException e) {
				// NBD if the ack message failed to send or if the ack message couldn't be
				// deleted.
			}

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
		clonedTrack.setUserData(ExtraTrackInfo.builder()
				.addedByUserId(user.getLongID())
				.addTimestamp(System.currentTimeMillis())
				.build());

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
		return AudioSessionStatus.builder()
				.nowPlaying(audioPlayer.getPlayingTrack())
				.upcomingTracks(trackManager.getView())
				.playMode(playMode)
				.loopMode(repeatMode)
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
	public void setPlayMode(PlayMode mode) {
		Preconditions.checkNotNull(mode, "mode must be non-null.");

		if (mode == PlayMode.STANDARD) {
			this.trackManager = TrackManagers.sortedByAddTimestamp(this.trackManager.getView());
		} else if (mode == PlayMode.SHUFFLE) {
			this.trackManager = TrackManagers.shuffled(this.trackManager.getView());
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
	 * Skips an item in the queue.
	 * 
	 * @param i
	 *            The index of the track to skip.
	 * @return The skipped {@link AudioTrack}.
	 * @throws IndexOutOfBoundsException
	 *             if the provided index is not within playlist bounds.
	 */
	public AudioTrack skip(int i) {
		AudioTrack trackToSkip = this.trackManager.getView().get(i);
		this.trackManager.remove(trackToSkip);
		return trackToSkip;
	}

	public Optional<SearchResults> getSearchResults(IUser user) {
		Preconditions.checkNotNull(user, "user must be non-null.");

		return Optional.ofNullable(searchResultsByUserId.get(user.getLongID()));
	}

	public void clearSearchResult(IUser user) {
		Preconditions.checkNotNull(user, "user must be non-null.");

		searchResultsByUserId.remove(user.getLongID());
	}

	private AudioTrack clone(AudioTrack originalAudioTrack) {
		AudioTrack clonedAudioTrack = originalAudioTrack.makeClone();
		ExtraTrackInfo originalExtraTrackInfo = originalAudioTrack.getUserData(ExtraTrackInfo.class);

		clonedAudioTrack.setUserData(ExtraTrackInfo.builder()
				.addedByUserId(originalExtraTrackInfo.getAddedByUserId())
				.addTimestamp(System.currentTimeMillis())
				.build());

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
			return new SortingTrackManager(tracks, new Comparator<AudioTrack>() {

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

		public static ShufflingTrackManager shuffled(Collection<AudioTrack> tracks) {
			return new ShufflingTrackManager(tracks);
		}
	}
}
