package net.tonbot.plugin.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
class AudioSession {

	@NonNull
	private final AudioPlayer audioPlayer;
	
	@NonNull
	private final StandardAudioEventAdapter audioEventAdapter;
}
