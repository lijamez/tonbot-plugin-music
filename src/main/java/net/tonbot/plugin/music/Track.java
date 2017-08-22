package net.tonbot.plugin.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
class Track {

	@NonNull
	private final AudioTrack audioTrack;
	
	@NonNull
	private final Long addedByUserId;
	
	@NonNull
	private final Long addTimestamp;
}
