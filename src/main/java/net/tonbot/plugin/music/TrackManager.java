package net.tonbot.plugin.music;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

	void remove(AudioTrack track);

	/**
	 * Gets the next track, if any. The returned track will be removed.
	 * 
	 * @return The next track.
	 */
	Optional<AudioTrack> next();
}
