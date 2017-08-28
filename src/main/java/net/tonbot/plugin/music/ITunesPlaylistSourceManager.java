package net.tonbot.plugin.music;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSearchProvider;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import lombok.Data;
import net.tonbot.common.TonbotBusinessException;

/**
 * Retrieves iTunes playlist in Unicode format and then looks up the song in
 * YouTube. Tracks are returned as {@link LazyYoutubeAudioTracks} and hence
 * tracks will only be searched when they start to play. This is to prevent
 * YouTube from throttling us.
 */
class ITunesPlaylistSourceManager extends YoutubeAudioSourceManager {

	private static final Logger LOG = LoggerFactory.getLogger(ITunesPlaylistSourceManager.class);

	private static final char DELIMITER = '\t';

	private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/603.3.8 (KHTML, like Gecko) Version/10.1.2 Safari/603.3.8";

	private static final String DISCORD_HOST = "cdn.discordapp.com";
	private static final String ATTACHMENTS_PATH = "/attachments/";

	private static final String TRACK_TITLE_COLUMN = "Name";
	private static final String TRACK_ARTIST_COLUMN = "Artist";
	private static final String TRACK_DURATION_COLUMN = "Time";

	private final CSVFormat csvFormat;
	private final YoutubeSearchProvider ytSearchProvider;

	@Inject
	public ITunesPlaylistSourceManager(YoutubeSearchProvider ytSearchProvider) {
		this.csvFormat = CSVFormat.newFormat(DELIMITER)
				.withIgnoreEmptyLines(true)
				.withFirstRecordAsHeader()
				.withTrim();
		this.ytSearchProvider = Preconditions.checkNotNull(ytSearchProvider, "ytSearchProvider must be non-null.");
	}

	@Override
	public String getSourceName() {
		return "iTunes Playlist";
	}

	@Override
	public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
		try {
			URL url = new URL(reference.identifier);

			// Must be a discord attachment.
			if (!StringUtils.equals(url.getHost(), DISCORD_HOST)
					|| !StringUtils.startsWith(url.getPath(), ATTACHMENTS_PATH)) {
				return null;
			}

			List<SongMetadata> songMetadata = getSongMetadata(url);
			List<AudioTrack> tracks = getLazyTracks(songMetadata);

			return new BasicAudioPlaylist("iTunes Playlist", tracks, null, false);

		} catch (MalformedURLException e) {
			return null;
		}
	}

	private List<SongMetadata> getSongMetadata(URL url) {

		try {
			URLConnection connection = url.openConnection();

			// A user agent is required or else discord will return a 403.
			connection.setRequestProperty("User-Agent", USER_AGENT);
			connection.connect();

			// BOMInputStream is used to ignore the the Byte Order Mark (which is 2 bytes).
			Reader iTunesPlaylistReader = new InputStreamReader(
					new BOMInputStream(connection.getInputStream(), ByteOrderMark.UTF_16LE), StandardCharsets.UTF_16LE);

			CSVParser parser = csvFormat.parse(iTunesPlaylistReader);
			Map<String, Integer> headerMap = parser.getHeaderMap();

			if (!headerMap.containsKey(TRACK_TITLE_COLUMN) || !headerMap.containsKey(TRACK_ARTIST_COLUMN)
					|| !headerMap.containsKey(TRACK_DURATION_COLUMN)) {
				throw new TonbotBusinessException(
						"File appears to be invalid. Make sure the iTunes playlist was exported in Unicode format.");
			}

			List<SongMetadata> songMetadata = parser.getRecords().stream()
					.map(record -> {

						String title = record.get(TRACK_TITLE_COLUMN);
						String artist = record.get(TRACK_ARTIST_COLUMN);
						String timeInSecs = record.get(TRACK_DURATION_COLUMN);

						if (StringUtils.isAnyBlank(title, artist, timeInSecs)) {
							LOG.debug("A track had an empty title, artist, or time. The track will be ignored.");
							return null;
						}

						long timeInMs;
						try {
							timeInMs = Long.parseLong(timeInSecs) * 1000;
							Preconditions.checkArgument(timeInMs >= 0);
						} catch (IllegalArgumentException e) {
							LOG.debug("A track had an invalid time. The track will be ignored.");
							return null;
						}

						return new SongMetadata(
								title,
								artist,
								timeInMs);
					})
					.filter(sm -> sm != null)
					.collect(Collectors.toList());

			return songMetadata;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private List<AudioTrack> getLazyTracks(List<SongMetadata> songMetadata) {
		return songMetadata.stream()
				.map(sm -> {
					AudioTrackInfo ati = new AudioTrackInfo(sm.getName(), sm.getArtist(), sm.getDuration(), "", true,
							"");
					return new LazyYoutubeAudioTrack(ati, this, ytSearchProvider);
				})
				.collect(Collectors.toList());
	}

	@Override
	public boolean isTrackEncodable(AudioTrack track) {
		return false;
	}

	@Override
	public void shutdown() {

	}

	@Data
	private static class SongMetadata {

		private final String name;
		private final String artist;

		// The duration in milliseconds.
		private final long duration;
	}
}
