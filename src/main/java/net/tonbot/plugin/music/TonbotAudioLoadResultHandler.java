package net.tonbot.plugin.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

/**
 * An {@link AudioLoadResultHandler} that is expected to return an
 * {@link AudioLoadResult}.
 */
abstract class TonbotAudioLoadResultHandler implements AudioLoadResultHandler {

	protected AudioLoadResult result;

	/**
	 * Gets the result of loading audio.
	 * 
	 * @return {@link AudioLoadResult}. Will not be null if any of
	 *         {@link AudioLoadResultHandler}'s handler methods was called.
	 */
	public AudioLoadResult getResult() {
		return result;
	}

}
