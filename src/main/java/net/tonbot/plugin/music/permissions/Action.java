package net.tonbot.plugin.music.permissions;

public enum Action {

	PLAY_PAUSE("Play and Pause"),
	ADD_TRACKS("Add Tracks"),
	SKIP_ALL("Skip All Tracks"),
	SKIP_OTHERS("Skip Others' Tracks"),
	PLAY_MODE_CHANGE("Change Play Mode"),
	REPEAT_MODE_CHANGE("Change Repeat Mode");

	private final String description;

	private Action(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}

}
