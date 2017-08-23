package net.tonbot.plugin.music;

@SuppressWarnings("serial")
class NoSessionException extends RuntimeException {

	public NoSessionException(String message) {
		super(message);
	}
}
