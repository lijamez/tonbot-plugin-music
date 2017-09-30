package net.tonbot.plugin.music;

import java.util.List;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

interface AudioTrackFactory {

	List<AudioTrack> getAudioTracks(List<SongMetadata> songMetadata);
}
