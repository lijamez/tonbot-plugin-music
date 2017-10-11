package net.tonbot.plugin.music;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Track organizer. Must be thread-safe.
 */
interface TrackManager {

	void put(AudioTrack track);

	void putAll(Collection<AudioTrack> tracks);

	/**
	 * Gets an immutable view of the list of upcoming tracks.
	 * 
	 * @return An immutable list of the upcoming tracks.
	 */
	List<AudioTrack> getView();

	/**
	 * Removes all tracks that meet the given criteria.
	 * 
	 * @param predicate
	 *            If this {@link Predicate} returns true for a particular track,
	 *            then that track will be removed. Non-null.
	 * @return The tracks removed.
	 */
	List<AudioTrack> removeAll(Predicate<AudioTrack> predicate);

	/**
	 * Gets the next track, if any. The returned track will be removed.
	 * 
	 * @return The next track.
	 */
	Optional<AudioTrack> next();
}
