package net.tonbot.plugin.music;

import lombok.Data;

@Data
public class SongMetadata {

	private final String name;
	private final String artist;

	// The duration in milliseconds.
	private final long duration;
}
