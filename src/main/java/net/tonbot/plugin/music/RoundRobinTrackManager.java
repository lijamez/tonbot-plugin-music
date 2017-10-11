package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

class RoundRobinTrackManager implements TrackManager {

	private final Map<Long, LinkedList<AudioTrack>> tracksByUserId;
	private final List<Long> userIdOrder;

	private final ReadWriteLock lock;

	private Long currentUserId;
	private LinkedList<AudioTrack> upNext;

	public RoundRobinTrackManager() {
		this.lock = new ReentrantReadWriteLock();
		this.tracksByUserId = new HashMap<>();
		this.userIdOrder = new ArrayList<>();
		this.upNext = new LinkedList<>();
	}

	@Override
	public void put(AudioTrack track) {
		lock.writeLock().lock();
		try {
			putInternal(track);

			generateQueue();
		} finally {
			lock.writeLock().unlock();
		}
	}

	private void putInternal(AudioTrack track) {
		ExtraTrackInfo eti = track.getUserData(ExtraTrackInfo.class);
		Long addedByUserId = eti.getAddedByUserId();
		Preconditions.checkNotNull(addedByUserId, "addedByUserId must be non-null.");

		LinkedList<AudioTrack> tracksFromUserId = tracksByUserId.get(addedByUserId);
		if (tracksFromUserId == null) {
			// New user!
			tracksFromUserId = new LinkedList<>();
			tracksByUserId.put(addedByUserId, tracksFromUserId);
			addUserIdBehindCurrent(addedByUserId);
		}
		tracksFromUserId.add(track);
	}

	private void addUserIdBehindCurrent(Long userId) {
		if (currentUserId == null) {
			// The first user.
			userIdOrder.add(userId);
			currentUserId = userId;
		} else {
			int currentUserIdIdx = userIdOrder.indexOf(currentUserId);
			int targetUserIdx = currentUserIdIdx == 0 ? userIdOrder.size() : currentUserIdIdx - 1;
			userIdOrder.add(targetUserIdx, userId);
		}
	}

	@Override
	public void putAll(Collection<AudioTrack> tracks) {
		lock.writeLock().lock();
		try {
			tracks.forEach(track -> putInternal(track));
			generateQueue();
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public List<AudioTrack> getView() {
		lock.readLock().lock();
		try {
			return ImmutableList.copyOf(upNext);
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

			tracksToRemove.forEach(track -> removeInternal(track));

			return ImmutableList.copyOf(tracksToRemove);
		} finally {
			lock.writeLock().unlock();
		}
	}

	private void removeInternal(AudioTrack track) {
		ExtraTrackInfo eti = track.getUserData(ExtraTrackInfo.class);

		if (eti == null) {
			return;
		}

		Long addedByUserId = eti.getAddedByUserId();

		if (addedByUserId == null) {
			return;
		}

		List<AudioTrack> userTracks = tracksByUserId.get(addedByUserId);

		if (userTracks == null) {
			return;
		}

		userTracks.remove(track);

		if (userTracks.isEmpty()) {
			// This user should be removed from the rotation.
			tracksByUserId.remove(addedByUserId);
			if (addedByUserId.equals(currentUserId)) {
				shiftCurrentUserId();
				if (addedByUserId.equals(currentUserId)) {
					// If the new currentUserId is STILL this user, then we know that this is the
					// ONLY user in the rotation. And since we are about to remove this user, we
					// need to set currentUserID to null.
					currentUserId = null;
				}
			}

			userIdOrder.remove(addedByUserId);
		}
	}

	private void shiftCurrentUserId() {
		if (this.currentUserId == null) {
			return;
		}

		int currentUserIdIdx = userIdOrder.indexOf(currentUserId);
		if (currentUserIdIdx == -1) {
			// This is actually an invalid state, since the current user ID should always be
			// in the rotation.
			throw new IllegalStateException("currentUserId is not a part of the rotation.");
		}

		int nextCurrentUserIdIdx = (currentUserIdIdx + 1) % userIdOrder.size();
		Long nextCurrentUserId = userIdOrder.get(nextCurrentUserIdIdx);

		this.currentUserId = nextCurrentUserId;
	}

	private void generateQueue() {
		this.upNext.clear();

		if (currentUserId == null) {
			return;
		}

		int currentUserIdIdx = userIdOrder.indexOf(currentUserId);
		if (currentUserIdIdx == -1) {
			throw new IllegalStateException("currentUserId is not a part of the rotation.");
		}

		// Sweep over tracks in tracksByUserId, rotating by user.
		int longestQueueSize = tracksByUserId.values().stream()
				.mapToInt(list -> list.size())
				.max()
				.orElse(0);

		for (int i = 0; i < longestQueueSize; i++) {

			int userIdIdx = currentUserIdIdx;
			do {
				Long userId = userIdOrder.get(userIdIdx);
				List<AudioTrack> userTracks = tracksByUserId.get(userId);

				if (i < userTracks.size()) {
					AudioTrack at = tracksByUserId.get(userId).get(i);
					this.upNext.add(at);
				}

				userIdIdx = (userIdIdx + 1) % userIdOrder.size();
			} while (userIdIdx != currentUserIdIdx);
		}
	}

	@Override
	public Optional<AudioTrack> next() {
		lock.writeLock().lock();
		try {
			AudioTrack nextTrack = upNext.removeFirst();
			removeInternal(nextTrack);
			shiftCurrentUserId();
			generateQueue();
			return Optional.of(nextTrack);
		} catch (NoSuchElementException e) {
			return Optional.empty();
		} finally {
			lock.writeLock().unlock();
		}
	}

}
