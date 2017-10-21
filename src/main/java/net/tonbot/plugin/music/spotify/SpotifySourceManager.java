package net.tonbot.plugin.music.spotify;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

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
import com.wrapper.spotify.methods.PlaylistTracksRequest.Builder;
import com.wrapper.spotify.models.Page;
import com.wrapper.spotify.models.Playlist;
import com.wrapper.spotify.models.PlaylistTrack;
import com.wrapper.spotify.models.Track;

import lombok.Data;
import lombok.NonNull;
import net.tonbot.plugin.music.AudioTrackFactory;
import net.tonbot.plugin.music.SongMetadata;

public class SpotifySourceManager implements AudioSourceManager {

	private static final String SPOTIFY_DOMAIN = "open.spotify.com";
	private static final int EXPECTED_PATH_COMPONENTS = 4;

	private final Api spotifyApi;
	private final AudioTrackFactory audioTrackFactory;

	@Inject
	public SpotifySourceManager(
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

			if (!StringUtils.equals(url.getHost(), SPOTIFY_DOMAIN)) {
				return null;
			}

			AudioItem audioItem = null;
			audioItem = handleAsPlaylist(url);

			if (audioItem == null) {
				audioItem = handleAsTrack(url);
			}

			return audioItem;

		} catch (MalformedURLException e) {
			return null;
		}
	}

	private AudioTrack handleAsTrack(URL url) {
		Path path = Paths.get(url.getPath());

		if (path.getNameCount() < 2) {
			return null;
		}

		if (!StringUtils.equals(path.getName(0).toString(), "track")) {
			return null;
		}

		String trackId = path.getName(1).toString();

		Track track;
		try {
			track = spotifyApi.getTrack(trackId).build().get();
		} catch (IOException | WebApiException e) {
			throw new IllegalStateException("Unable to fetch track from Spotify API.", e);
		}

		SongMetadata songMetadata = getSongMetadata(track);

		return audioTrackFactory.getAudioTrack(songMetadata);
	}

	private BasicAudioPlaylist handleAsPlaylist(URL url) {
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

		List<PlaylistTrack> playlistTracks = getAllPlaylistTracks(playlist);

		List<SongMetadata> songMetadata = getSongMetadata(playlistTracks);
		List<AudioTrack> audioTracks = audioTrackFactory.getAudioTracks(songMetadata);

		return new BasicAudioPlaylist(playlist.getName(), audioTracks, null, false);
	}

	private List<PlaylistTrack> getAllPlaylistTracks(Playlist playlist) {
		List<PlaylistTrack> playlistTracks = new ArrayList<>();

		Page<PlaylistTrack> currentPage = playlist.getTracks();

		do {
			playlistTracks.addAll(currentPage.getItems());

			if (currentPage.getNext() == null) {
				currentPage = null;
			} else {

				try {
					URI nextPageUri = new URI(currentPage.getNext());
					List<NameValuePair> queryPairs = URLEncodedUtils.parse(nextPageUri, StandardCharsets.UTF_8);

					Builder b = spotifyApi.getPlaylistTracks(playlist.getOwner().getId(), playlist.getId());
					for (NameValuePair queryPair : queryPairs) {
						b = b.parameter(queryPair.getName(), queryPair.getValue());
					}

					currentPage = b.build().get();
				} catch (IOException | WebApiException e) {
					throw new IllegalStateException("Unable to query Spotify for playlist tracks.", e);
				} catch (URISyntaxException e) {
					throw new IllegalStateException("Spotify returned an invalid 'next page' URI.", e);
				}
			}
		} while (currentPage != null);

		return playlistTracks;
	}

	private PlaylistKey extractPlaylistId(URL url) {
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

	private List<SongMetadata> getSongMetadata(List<PlaylistTrack> playlistTracks) {

		List<SongMetadata> songMetadata = playlistTracks.stream()
				.map(playlistTrack -> playlistTrack.getTrack())
				.map(track -> getSongMetadata(track))
				.collect(Collectors.toList());

		return songMetadata;
	}

	private SongMetadata getSongMetadata(Track track) {
		String firstArtistName = track.getArtists().isEmpty() ? "" : track.getArtists().get(0).getName();

		return new SongMetadata(track.getName(), firstArtistName, track.getDuration());
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
