package net.tonbot.plugin.music;

import lombok.Data;
import lombok.NonNull;

@Data
class SpotifyCredentials {

	@NonNull
	private final String clientId;

	@NonNull
	private final String clientSecret;
}
