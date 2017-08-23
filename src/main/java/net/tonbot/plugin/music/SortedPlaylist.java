package net.tonbot.plugin.music;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

class SortedPlaylist extends Playlist {

	private final LinkedList<AudioTrack> tracks;
	private final Comparator<AudioTrack> comparator;

	/**
	 * Creates a playlist of the given tracks. The order will be determined by the
	 * comparator.
	 * 
	 * @param tracks
	 *            A collection of {@link ExtraTrackInfo}s. Non-null.
	 */
	public SortedPlaylist(Collection<AudioTrack> tracks, Comparator<AudioTrack> comparator) {
		Preconditions.checkNotNull(tracks, "tracks must be non-null.");
		this.tracks = new LinkedList<>();
		this.tracks.addAll(tracks);
		this.comparator = Preconditions.checkNotNull(comparator, "comparator must be non-null.");
	}

	@Override
	public boolean hasNext() {
		return !tracks.isEmpty();
	}

	@Override
	public AudioTrack next() {
		return tracks.pollFirst();
	}

	@Override
	void put(AudioTrack track) {
		Preconditions.checkNotNull(track, "track must be non-null.");
		tracks.add(track);
		Collections.sort(tracks, comparator);
	}

	@Override
	void putAll(Collection<AudioTrack> inputTracks) {
		Preconditions.checkNotNull(inputTracks, "tracks must be non-null.");
		tracks.addAll(inputTracks);
		Collections.sort(tracks, comparator);
	}

	@Override
	List<AudioTrack> getView() {
		return ImmutableList.copyOf(tracks);
	}

	@Override
	void remove(AudioTrack track) {
		tracks.remove(track);
	}
}
