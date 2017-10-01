package net.tonbot.plugin.music;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import com.wrapper.spotify.Api;
import com.wrapper.spotify.exceptions.WebApiException;
import com.wrapper.spotify.methods.PlaylistRequest;
import com.wrapper.spotify.models.Page;
import com.wrapper.spotify.models.Playlist;
import com.wrapper.spotify.models.PlaylistTrack;

import lombok.Data;
import lombok.NonNull;

class SpotifyPlaylistSourceManager implements AudioSourceManager {

	private static final String SPOTIFY_DOMAIN = "open.spotify.com";
	private static final int EXPECTED_PATH_COMPONENTS = 4;

	private final Api spotifyApi;
	private final AudioTrackFactory audioTrackFactory;

	@Inject
	public SpotifyPlaylistSourceManager(
			Api spotifyApi,
			AudioTrackFactory audioTrackFactory) {
		this.spotifyApi = Preconditions.checkNotNull(spotifyApi, "spotifyApi must be non-null.");
		this.audioTrackFactory = Preconditions.checkNotNull(audioTrackFactory, "audioTrackFactory must be non-null.");
	}

	@Override
	public String getSourceName() {
		return "Spotify Playlist";
	}

	@Override
	public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
		try {
			URL url = new URL(reference.identifier);

			PlaylistKey playlistKey;
			try {
				playlistKey = extractPlaylistId(url);
			} catch (IllegalArgumentException e) {
				return null;
			}

			PlaylistRequest playlistRequest = spotifyApi
					.getPlaylist(playlistKey.getUserId(), playlistKey.getPlaylistId()).build();

			Playlist playlist;
			try {
				playlist = playlistRequest.get();
			} catch (IOException | WebApiException e) {
				throw new IllegalStateException("Unable to fetch playlist from Spotify API.", e);
			}

			// TODO: Pagination or else we can only fetch the first 100 songs of a playlist.
			Page<PlaylistTrack> playlistTracksPage = playlist.getTracks();

			List<SongMetadata> songMetadata = getSongMetadata(playlistTracksPage);
			List<AudioTrack> audioTracks = audioTrackFactory.getAudioTracks(songMetadata);

			return new BasicAudioPlaylist(playlist.getName(), audioTracks, null, false);

		} catch (MalformedURLException e) {
			return null;
		}
	}

	private PlaylistKey extractPlaylistId(URL url) {
		if (!StringUtils.equals(url.getHost(), SPOTIFY_DOMAIN)) {
			throw new IllegalArgumentException("Domain is not a spotify domain.");
		}

		Path path = Paths.get(url.getPath());
		if (path.getNameCount() < EXPECTED_PATH_COMPONENTS) {
			throw new IllegalArgumentException("Not enough path components.");
		}

		if (!StringUtils.equals(path.getName(2).toString(), "playlist")) {
			throw new IllegalArgumentException("URL doesn't appear to be a playlist.");
		}

		String userId = path.getName(1).toString();
		if (StringUtils.isBlank(userId)) {
			throw new IllegalArgumentException("User ID is blank.");
		}

		String playlistId = path.getName(3).toString();
		if (StringUtils.isBlank(playlistId)) {
			throw new IllegalArgumentException("Playlist ID is blank.");
		}

		return new PlaylistKey(userId, playlistId);
	}

	private List<SongMetadata> getSongMetadata(Page<PlaylistTrack> playlistTracksPage) {

		List<SongMetadata> songMetadata = playlistTracksPage.getItems().stream()
				.map(playlistTrack -> playlistTrack.getTrack())
				.map(track -> {
					String firstArtistName = track.getArtists().isEmpty() ? "" : track.getArtists().get(0).getName();

					return new SongMetadata(track.getName(), firstArtistName, track.getDuration());
				})
				.collect(Collectors.toList());

		return songMetadata;
	}

	@Override
	public boolean isTrackEncodable(AudioTrack track) {
		return false;
	}

	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
		throw new UnsupportedOperationException("encodeTrack is unsupported.");
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		throw new UnsupportedOperationException("decodeTrack is unsupported.");
	}

	@Override
	public void shutdown() {

	}

	@Data
	private static class PlaylistKey {
		@NonNull
		private final String userId;

		@NonNull
		private final String playlistId;
	}
}
