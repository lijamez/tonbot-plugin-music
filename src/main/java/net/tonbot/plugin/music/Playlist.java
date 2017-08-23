package net.tonbot.plugin.music;

import java.util.Iterator;
import java.util.List;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

abstract class Playlist implements Iterator<AudioTrack> {

	abstract void put(AudioTrack track);

	/**
	 * Gets an immutable view of the list of upcoming tracks.
	 * 
	 * @return An immutable list of the upcoming tracks.
	 */
	abstract List<AudioTrack> getView();

	abstract void remove(AudioTrack track);
}
