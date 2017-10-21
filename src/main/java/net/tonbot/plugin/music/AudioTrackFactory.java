package net.tonbot.plugin.music;

import java.util.List;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public interface AudioTrackFactory {

	List<AudioTrack> getAudioTracks(List<SongMetadata> songMetadata);

	AudioTrack getAudioTrack(SongMetadata songMetadata);
}
