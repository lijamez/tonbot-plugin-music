package net.tonbot.plugin.music;

import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import lombok.Builder;
import lombok.Data;

@Data
class AudioSessionStatus {

	private final AudioTrack nowPlaying;
	private final List<AudioTrack> upcomingTracks;
	private final PlayMode playMode;
	private final RepeatMode repeatMode;

	@Builder
	private AudioSessionStatus(AudioTrack nowPlaying, List<AudioTrack> upcomingTracks, PlayMode playMode,
			RepeatMode repeatMode) {
		this.nowPlaying = nowPlaying;

		Preconditions.checkNotNull(upcomingTracks, "upcomingTracks must be non-null.");
		this.upcomingTracks = ImmutableList.copyOf(upcomingTracks);

		this.playMode = Preconditions.checkNotNull(playMode, "playMode must be non-null.");
		this.repeatMode = Preconditions.checkNotNull(repeatMode, "repeatMode must be non-null.");
	}

	public Optional<AudioTrack> getNowPlaying() {
		return Optional.ofNullable(nowPlaying);
	}
}
