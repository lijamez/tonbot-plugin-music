package net.tonbot.plugin.music;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;

import lombok.Data;
import sx.blah.discord.handle.obj.IMessage;

@Data
class SearchResults {

	private final AudioPlaylist audioPlaylist;
	private final IMessage message;
}
