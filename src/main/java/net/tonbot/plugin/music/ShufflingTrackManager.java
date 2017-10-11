package net.tonbot.plugin.music;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

class ShufflingTrackManager implements TrackManager {

	private final LinkedList<AudioTrack> tracks;
	private final ReadWriteLock lock;

	/**
	 * Creates a playlist of the given tracks. Track ordering will be randomized.
	 * 
	 * @param tracks
	 *            A collection of {@link AudioTrack}s. Non-null.
	 */
	public ShufflingTrackManager() {
		this.tracks = new LinkedList<>();
		this.lock = new ReentrantReadWriteLock();
	}

	@Override
	public Optional<AudioTrack> next() {
		lock.writeLock().lock();
		try {
			return Optional.of(tracks.removeFirst());
		} catch (NoSuchElementException e) {
			return Optional.empty();
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Adds a track to a random position in the list. The entire list isn't
	 * re-shuffled.
	 * 
	 * @param The
	 *            {@link ExtraTrackInfo} to be added. Non-null.
	 */
	@Override
	public void put(AudioTrack track) {
		Preconditions.checkNotNull(track, "track must be non-null.");

		int index = ThreadLocalRandom.current().nextInt(0, tracks.size() + 1);

		lock.writeLock().lock();
		try {
			tracks.add(index, track);
		} finally {
			lock.writeLock().unlock();
		}

	}

	@Override
	public void putAll(Collection<AudioTrack> inputTracks) {
		Preconditions.checkNotNull(inputTracks, "inputTracks must be non-null.");

		// Puts each track at some random index.
		inputTracks.forEach(track -> put(track));
	}

	@Override
	public List<AudioTrack> getView() {

		lock.readLock().lock();
		try {
			return ImmutableList.copyOf(tracks.stream()
					.collect(Collectors.toList()));
		} finally {
			lock.readLock().unlock();
		}

	}

	@Override
	public List<AudioTrack> removeAll(Predicate<AudioTrack> predicate) {
		Preconditions.checkNotNull(predicate, "predicate must be non-null.");

		lock.writeLock().lock();
		try {
			List<AudioTrack> tracksToRemove = this.getView().stream()
					.filter(predicate)
					.collect(Collectors.toList());

			tracks.removeAll(tracksToRemove);

			return ImmutableList.copyOf(tracksToRemove);
		} finally {
			lock.writeLock().unlock();
		}
	}
}
