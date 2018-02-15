package net.tonbot.plugin.music.spotify;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

import lombok.Data;

@Data
public class SpotifyCredentials {

	private final String clientId;
	private final String clientSecret;

	@JsonCreator
	public SpotifyCredentials(@JsonProperty("clientId") String clientId,
			@JsonProperty("clientSecret") String clientSecret) {
		this.clientId = Preconditions.checkNotNull(clientId, "clientId must be non-null.");
		this.clientSecret = Preconditions.checkNotNull(clientSecret, "clientSecret must be non-null.");
	}
}
