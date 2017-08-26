package net.tonbot.plugin.music;

import java.util.Optional;

enum RepeatMode {
	/**
	 * Repeating is disabled.
	 */
	OFF(null, "Off"),

	/**
	 * Loop the whole playlist.
	 */
	ALL(":repeat:", "All"),

	/**
	 * Look a single track.
	 */
	ONE(":repeat_one:", "One");

	private final String emote;
	private final String friendlyName;

	private RepeatMode(String emote, String friendlyName) {
		this.emote = emote;
		this.friendlyName = friendlyName;
	}

	public Optional<String> getEmote() {
		return Optional.ofNullable(emote);
	}

	public String getFriendlyName() {
		return friendlyName;
	}
}
