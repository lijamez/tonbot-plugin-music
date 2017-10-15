package net.tonbot.plugin.music;

import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import lombok.Data;
import sx.blah.discord.handle.obj.IMessage;

@Data
class SearchResults {

	private final List<AudioTrack> hits;
	private IMessage message;

	/**
	 * Gets the {@link IMessage} that was used to send the search results.
	 * 
	 * @return
	 */
	public Optional<IMessage> getMessage() {
		return Optional.ofNullable(message);
	}

	/**
	 * Sets the {@link IMessage} that contains the search results.
	 * 
	 * @param message
	 *            {@link IMessage}. Non-null.
	 */
	public void setMessage(IMessage message) {
		this.message = Preconditions.checkNotNull(message, "message must not be null.");
	}
}
