package net.tonbot.plugin.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import sx.blah.discord.util.EmbedBuilder;

interface EmbedAppender {

	void appendDetails(AudioTrack audioTrack, EmbedBuilder embedBuilder);
}
