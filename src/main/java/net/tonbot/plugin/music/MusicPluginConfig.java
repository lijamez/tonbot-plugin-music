package net.tonbot.plugin.music;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
class MusicPluginConfig {

	private final String youTubeApiKey;
	private final SpotifyCredentials spotifyCredentials;

	@JsonCreator
	public MusicPluginConfig(
			@JsonProperty("youTubeApiKey") String youtubeApiKey,
			@JsonProperty("spotifyCredentials") SpotifyCredentials spotifyCredentials) {
		this.youTubeApiKey = youtubeApiKey;
		this.spotifyCredentials = spotifyCredentials;
	}
}
