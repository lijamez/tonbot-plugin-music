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
 * YouTube.
 */
class ITunesPlaylistSourceManager extends YoutubeAudioSourceManager {

	private static final char DELIMITER = '\t';
	private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/603.3.8 (KHTML, like Gecko) Version/10.1.2 Safari/603.3.8";

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
			if (!StringUtils.equals(url.getProtocol(), "https")
					|| !StringUtils.equals(url.getHost(), "cdn.discordapp.com")
					|| !StringUtils.startsWith(url.getPath(), "/attachments/")) {
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

			if (!headerMap.containsKey("Name") || !headerMap.containsKey("Artist") || !headerMap.containsKey("Album")
					|| !headerMap.containsKey("Time")) {
				throw new TonbotBusinessException(
						"File appears to be invalid. Make sure the playlist was exported in Unicode format.");
			}

			List<SongMetadata> songMetadata = parser.getRecords().stream()
					.map(record -> new SongMetadata(record.get("Name"), record.get("Artist"), record.get("Album"),
							Long.parseLong(record.get("Time")) * 1000))
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
		private final String album;

		// The duration in milliseconds.
		private final long duration;
	}
}
