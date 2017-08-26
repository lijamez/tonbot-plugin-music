package net.tonbot.plugin.music;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
class MusicPluginConfig {

	private final String youTubeApiKey;

	public MusicPluginConfig(@JsonProperty("youTubeApiKey") String youtubeApiKey) {
		this.youTubeApiKey = youtubeApiKey;
	}
}
