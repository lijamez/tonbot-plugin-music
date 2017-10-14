package net.tonbot.plugin.music;

import java.util.List;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import lombok.Data;
import sx.blah.discord.handle.obj.IMessage;

@Data
class SearchResults {

	private final List<AudioTrack> hits;
	private final IMessage message;
}
