package net.tonbot.plugin.music;

import java.util.Optional;

enum PlayMode {

	STANDARD(":arrow_right:", "Standard"), SHUFFLE(":twisted_rightwards_arrows:", "Shuffle");

	private final String emote;
	private final String friendlyName;

	private PlayMode(String emote, String friendlyName) {
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
