package net.tonbot.plugin.music;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

class ShuffledPlaylist extends Playlist {

	public LinkedList<Track> tracks;

	/**
	 * Creates a playlist of the given tracks. Track ordering will be randomized.
	 * @param tracks A collection of {@link Track}s. Non-null. 
	 */
	public ShuffledPlaylist(Collection<Track> tracks) {
		Preconditions.checkNotNull(tracks, "tracks must be non-null.");
		
		List<Track> randomizedTracklist = Arrays.asList(tracks.toArray(new Track[0]));
		Collections.shuffle(randomizedTracklist);
		
		this.tracks = new LinkedList<>(randomizedTracklist);
	}

	@Override
	public boolean hasNext() {
		return tracks.peekFirst() != null;
	}

	@Override
	public Track next() {
		return tracks.removeFirst();
	}

	/**
	 * Adds a track to a random position in the list.
	 * @param The {@link Track} to be added. Non-null.
	 */
	@Override
	void put(Track track) {
		Preconditions.checkNotNull(track, "track must be non-null.");
		
		int index = ThreadLocalRandom.current().nextInt(0, tracks.size() + 1);
		tracks.add(index, track);
	}

	@Override
	List<Track> getView() {
		return ImmutableList.copyOf(tracks.stream()
				.collect(Collectors.toList()));
	}

	@Override
	void remove(Track track) {
		tracks.remove(track);
	}

}
