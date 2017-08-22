package net.tonbot.plugin.music;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

class SortedPlaylist extends Playlist {

	private final TreeSet<Track> tracks;

	/**
	 * Creates a playlist of the given tracks. The order will be determined by the comparator.
	 * @param tracks A collection of {@link Track}s. Non-null. 
	 */
	public SortedPlaylist(Collection<Track> tracks, Comparator<Track> comparator) {
		Preconditions.checkNotNull(tracks, "tracks must be non-null.");
		this.tracks = new TreeSet<>(comparator);
		this.tracks.addAll(tracks);
	}

	@Override
	public boolean hasNext() {
		return !tracks.isEmpty();
	}

	@Override
	public Track next() {
		return tracks.pollFirst();
	}

	@Override
	void put(Track track) {
		Preconditions.checkNotNull(track, "track must be non-null.");
		tracks.add(track);
		System.out.println(tracks);
	}

	@Override
	List<Track> getView() {
		return ImmutableList.copyOf(tracks);
	}

	@Override
	void remove(Track track) {
		tracks.remove(track);
	}

}
