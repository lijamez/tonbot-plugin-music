package net.tonbot.plugin.music;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

class SortingTrackManager implements TrackManager {

	private final LinkedList<AudioTrack> tracks;
	private final Comparator<AudioTrack> comparator;

	private final ReadWriteLock lock;

	/**
	 * Sorts the tracks using a given comparator.
	 * 
	 * @param tracks
	 *            A collection of {@link AudioTrack}s. Non-null.
	 * @param comparator
	 *            The comparator which will be used to sort the {@link AudioTrack}s.
	 *            Non-null.
	 */
	public SortingTrackManager(Collection<AudioTrack> tracks, Comparator<AudioTrack> comparator) {
		Preconditions.checkNotNull(tracks, "tracks must be non-null.");
		this.tracks = new LinkedList<>();
		this.tracks.addAll(tracks);
		this.comparator = Preconditions.checkNotNull(comparator, "comparator must be non-null.");
		this.lock = new ReentrantReadWriteLock();
	}

	@Override
	public Optional<AudioTrack> next() {
		lock.writeLock().lock();
		try {
			return Optional.ofNullable(tracks.pollFirst());
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void put(AudioTrack track) {
		Preconditions.checkNotNull(track, "track must be non-null.");

		lock.writeLock().lock();
		try {
			tracks.add(track);
			Collections.sort(tracks, comparator);
		} finally {
			lock.writeLock().unlock();
		}

	}

	@Override
	public void putAll(Collection<AudioTrack> inputTracks) {
		Preconditions.checkNotNull(inputTracks, "tracks must be non-null.");

		lock.writeLock().lock();
		try {
			tracks.addAll(inputTracks);
			Collections.sort(tracks, comparator);
		} finally {
			lock.writeLock().unlock();
		}

	}

	@Override
	public List<AudioTrack> getView() {
		lock.readLock().lock();
		try {
			return ImmutableList.copyOf(tracks);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public void remove(AudioTrack track) {
		Preconditions.checkNotNull(track, "track must be non-null.");

		lock.writeLock().lock();
		try {
			tracks.remove(track);
			Collections.sort(tracks, comparator);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void removeAll(Collection<AudioTrack> tracksToRemove) {
		Preconditions.checkNotNull(tracksToRemove, "tracksToRemove must be non-null.");

		lock.writeLock().lock();
		try {
			tracks.removeAll(tracksToRemove);
			Collections.sort(tracks, comparator);
		} finally {
			lock.writeLock().unlock();
		}
	}
}
