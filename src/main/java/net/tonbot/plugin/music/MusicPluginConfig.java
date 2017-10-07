package net.tonbot.plugin.music;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
class MusicPluginConfig {

	private final String googleApiKey;
	private final SpotifyCredentials spotifyCredentials;

	@JsonCreator
	public MusicPluginConfig(
			@JsonProperty("googleApiKey") String googleApiKey,
			@JsonProperty("spotifyCredentials") SpotifyCredentials spotifyCredentials) {
		this.googleApiKey = googleApiKey;
		this.spotifyCredentials = spotifyCredentials;
	}
}
