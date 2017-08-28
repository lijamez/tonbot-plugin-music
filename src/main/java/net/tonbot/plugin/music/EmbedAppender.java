package net.tonbot.plugin.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import sx.blah.discord.util.EmbedBuilder;

interface EmbedAppender {

	/**
	 * The class type which this embed can handle.
	 * 
	 * @return The class type which this embed can handle.
	 */
	Class<? extends AudioTrack> getAppendableType();

	/**
	 * Appends additional information to the given {@link EmbedBuilder}.
	 * 
	 * @param audioTrack
	 *            {@link AudioTrack}. Non-null.
	 * @param embedBuilder
	 *            {@link EmbedBuilder}. Non-null.
	 */
	void appendDetails(AudioTrack audioTrack, EmbedBuilder embedBuilder);
}
