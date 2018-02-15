package net.tonbot.plugin.music;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import net.tonbot.plugin.music.spotify.SpotifyCredentials;

@Data
class MusicPluginConfig {

	private final String youtubeApiKey;
	private final String googleDriveApiKey;
	private final SpotifyCredentials spotifyCredentials;

	@JsonCreator
	public MusicPluginConfig(@JsonProperty("youtubeApiKey") String youtubeApiKey,
			@JsonProperty("googleDriveApiKey") String googleDriveApiKey,
			@JsonProperty("spotifyCredentials") SpotifyCredentials spotifyCredentials) {
		this.youtubeApiKey = youtubeApiKey;
		this.googleDriveApiKey = googleDriveApiKey;
		this.spotifyCredentials = spotifyCredentials;
	}
}
