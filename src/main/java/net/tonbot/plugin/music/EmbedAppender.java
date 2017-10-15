package net.tonbot.plugin.music;

import java.util.List;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import sx.blah.discord.util.EmbedBuilder;

interface EmbedAppender {

	/**
	 * The class types which this embed can handle.
	 * 
	 * @return The class types which this embed can handle. Non-null.
	 */
	List<Class<? extends AudioTrack>> getAppendableTypes();

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
