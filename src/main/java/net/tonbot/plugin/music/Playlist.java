package net.tonbot.plugin.music;

import java.util.Iterator;
import java.util.List;

abstract class Playlist implements Iterator<Track> {

	abstract void put(Track track);
	
	/**
	 * Gets an immutable view of the list of upcoming tracks.
	 * @return An immutable list of the upcoming tracks.
	 */
	abstract List<Track> getView();
	
	abstract void remove(Track track);
}
