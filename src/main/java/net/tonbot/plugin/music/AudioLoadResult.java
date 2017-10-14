package net.tonbot.plugin.music;

import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
class AudioLoadResult {

	private final List<AudioTrack> loadedTracks;
	private final String playlistName;
	private final FriendlyException exception;

	public AudioLoadResult(
			List<AudioTrack> loadedTracks,
			String playlistName,
			FriendlyException exception) {
		this.loadedTracks = loadedTracks != null ? ImmutableList.copyOf(loadedTracks) : null;
		this.playlistName = playlistName;
		this.exception = exception;

		if (this.loadedTracks != null) {
			Preconditions.checkArgument(exception == null, "exception must be null if loadedTracks is not null.");
		} else {
			Preconditions.checkArgument(exception != null, "exception must not be null if loadedTracks is null.");
		}
	}

	public Optional<String> getPlaylistName() {
		return Optional.ofNullable(playlistName);
	}

	public Optional<List<AudioTrack>> getLoadedTracks() {
		return Optional.ofNullable(loadedTracks);
	}

	public Optional<FriendlyException> getException() {
		return Optional.ofNullable(exception);
	}

}
