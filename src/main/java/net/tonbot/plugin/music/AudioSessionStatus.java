package net.tonbot.plugin.music;

import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import lombok.Builder;
import lombok.Data;

@Data
class AudioSessionStatus {

	private final Track nowPlaying;
	private final List<Track> upcomingTracks;
	private final PlayMode playMode;
	
	@Builder
	private AudioSessionStatus(Track nowPlaying, List<Track> upcomingTracks, PlayMode playMode) {
		this.nowPlaying = nowPlaying;
		
		Preconditions.checkNotNull(upcomingTracks, "upcomingTracks must be non-null.");
		this.upcomingTracks = ImmutableList.copyOf(upcomingTracks);
		
		this.playMode = Preconditions.checkNotNull(playMode, "playMode must be non-null.");
	}
	
	public Optional<Track> getNowPlaying() {
		return Optional.ofNullable(nowPlaying);
	}
}
