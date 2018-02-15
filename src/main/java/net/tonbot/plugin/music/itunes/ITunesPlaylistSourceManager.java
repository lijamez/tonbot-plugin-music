package net.tonbot.plugin.music.itunes;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

import net.tonbot.plugin.music.AudioTrackFactory;
import net.tonbot.plugin.music.SongMetadata;

/**
 * Retrieves iTunes playlist in plain text or Unicode format (ie. UTF-8 or
 * UTF-16LE) and then looks up the song in YouTube. Tracks are returned as
 * {@link LazyYoutubeAudioTracks} and hence tracks will only be searched when
 * they start to play. This is to prevent YouTube from throttling us.
 */
public class ITunesPlaylistSourceManager implements AudioSourceManager {

	private static final Logger LOG = LoggerFactory.getLogger(ITunesPlaylistSourceManager.class);

	private static final String DEFAULT_PLAYLIST_NAME = "iTunes Playlist";

	private static final char DELIMITER = '\t';

	private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/603.3.8 (KHTML, like Gecko) Version/10.1.2 Safari/603.3.8";

	private static final String DISCORD_HOST = "cdn.discordapp.com";
	private static final String ATTACHMENTS_PATH = "/attachments/";

	private static final String TRACK_TITLE_COLUMN = "Name";
	private static final String TRACK_ARTIST_COLUMN = "Artist";
	private static final String TRACK_DURATION_COLUMN = "Time";

	private final CSVFormat csvFormat;
	private final AudioTrackFactory audioTrackFactory;

	@Inject
	public ITunesPlaylistSourceManager(AudioTrackFactory audioTrackFactory) {
		this.csvFormat = CSVFormat.newFormat(DELIMITER).withIgnoreEmptyLines(true).withFirstRecordAsHeader().withTrim();
		this.audioTrackFactory = Preconditions.checkNotNull(audioTrackFactory, "audioTrackFactory must be non-null.");
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
			List<AudioTrack> tracks = audioTrackFactory.getAudioTracks(songMetadata);

			String playlistName = FilenameUtils.getBaseName(url.getPath());
			if (StringUtils.isEmpty(playlistName)) {
				playlistName = DEFAULT_PLAYLIST_NAME;
			}

			return new BasicAudioPlaylist(playlistName, tracks, null, false);

		} catch (MalformedURLException | InvalidItunesPlaylistException e) {
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
			BOMInputStream bomStream = new BOMInputStream(connection.getInputStream(), ByteOrderMark.UTF_16LE);
			Charset charset;
			if (bomStream.getBOM() == ByteOrderMark.UTF_16LE) {
				charset = StandardCharsets.UTF_16LE;
			} else {
				charset = StandardCharsets.UTF_8;
			}
			LOG.debug("Detected character set: " + charset);

			Reader iTunesPlaylistReader = new InputStreamReader(bomStream, charset);

			CSVParser parser = csvFormat.parse(iTunesPlaylistReader);
			Map<String, Integer> headerMap = parser.getHeaderMap();

			if (!headerMap.containsKey(TRACK_TITLE_COLUMN) || !headerMap.containsKey(TRACK_ARTIST_COLUMN)
					|| !headerMap.containsKey(TRACK_DURATION_COLUMN)) {
				throw new InvalidItunesPlaylistException("File doesn't appear to be an iTunes playlist.");
			}

			List<SongMetadata> songMetadata = parser.getRecords().stream().map(record -> {

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

				return new SongMetadata(title, artist, timeInMs);
			}).filter(sm -> sm != null).collect(Collectors.toList());

			return songMetadata;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean isTrackEncodable(AudioTrack track) {
		return false;
	}

	@Override
	public void shutdown() {

	}

	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
		throw new UnsupportedOperationException("encodeTrack is unsupported.");
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		throw new UnsupportedOperationException("decodeTrack is unsupported.");
	}

	@SuppressWarnings("serial")
	private static class InvalidItunesPlaylistException extends RuntimeException {
		public InvalidItunesPlaylistException(String message) {
			super(message);
		}
	}
}
