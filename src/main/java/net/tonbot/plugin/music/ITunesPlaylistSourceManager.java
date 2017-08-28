package net.tonbot.plugin.music;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSearchProvider;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
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
class ITunesPlaylistSourceManager implements AudioSourceManager {

	private static final Logger LOG = LoggerFactory.getLogger(ITunesPlaylistSourceManager.class);

	// Setting the size too large could cause YouTube to block our IP.
	private static final int THREAD_POOL_SIZE = 5;
	private static final char DELIMITER = '\t';
	private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/603.3.8 (KHTML, like Gecko) Version/10.1.2 Safari/603.3.8";

	private final CSVFormat csvFormat;
	private final YoutubeSearchProvider ytSearchProvider;
	private final ExecutorService executor;

	@Inject
	public ITunesPlaylistSourceManager(YoutubeSearchProvider ytSearchProvider) {
		this.csvFormat = CSVFormat.newFormat(DELIMITER)
				.withIgnoreEmptyLines(true)
				.withFirstRecordAsHeader()
				.withTrim();
		this.ytSearchProvider = Preconditions.checkNotNull(ytSearchProvider, "ytSearchProvider must be non-null.");
		this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
	}

	@Override
	public String getSourceName() {
		return "iTunes Playlist";
	}

	@Override
	public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
		System.out.println("ITunesPlaylistSourceManager was called!");
		try {
			URL url = new URL(reference.identifier);

			// Must be a discord attachment.
			if (!StringUtils.equals(url.getProtocol(), "https")
					|| !StringUtils.equals(url.getHost(), "cdn.discordapp.com")
					|| !StringUtils.startsWith(url.getPath(), "/attachments/")) {
				return null;
			}

			List<SongMetadata> songMetadata = getSongMetadata(url);
			List<AudioTrack> bestMatches = findBestMatches(songMetadata);

			return new BasicAudioPlaylist("iTunes Playlist", bestMatches, null, false);

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

	private List<AudioTrack> findBestMatches(List<SongMetadata> songMetadata) {
		List<CompletableFuture<AudioTrack>> futures = songMetadata.stream()
				.map(sm -> {
					return CompletableFuture.supplyAsync(() -> {
						LOG.debug("Looking up song for: {}", sm);

						String query = sm.getName() + " " + sm.getArtist();
						AudioItem audioItem = ytSearchProvider.loadSearchResult(query);

						if (audioItem == AudioReference.NO_TRACK) {
							return null;
						} else if (audioItem instanceof AudioPlaylist) {
							AudioPlaylist audioPlaylist = (AudioPlaylist) audioItem;

							// The best match is the one that has the smallest track duration delta.
							AudioTrack bestMatch = audioPlaylist.getTracks().stream()
									.sorted(new Comparator<AudioTrack>() {

										@Override
										public int compare(AudioTrack o1, AudioTrack o2) {
											long o1TimeDelta = Math.abs(o1.getDuration() - sm.getDuration());
											long o2TimeDelta = Math.abs(o2.getDuration() - sm.getDuration());

											return (int) (o1TimeDelta - o2TimeDelta);
										}

									})
									.filter(at -> at != null)
									.findFirst()
									.orElse(null);

							return bestMatch;
						} else if (audioItem instanceof AudioTrack) {
							return (AudioTrack) audioItem;
						} else {
							LOG.warn("Unknown AudioItem '{}' returned by YoutubeSearchProvider.", audioItem);
							return null;
						}
					}, executor);
				})
				.collect(Collectors.toList());

		// Due to the laziness of streams, we need to first collect all of the futures,
		// and then join them.
		return futures.stream()
				.map(future -> {
					try {
						return future.get();
					} catch (InterruptedException | ExecutionException e) {
						LOG.warn("Failed to find a track.", e);
						return null;
					}
				})
				.filter(at -> at != null)
				.collect(Collectors.toList());
	}

	@Override
	public boolean isTrackEncodable(AudioTrack track) {
		return false;
	}

	@Override
	public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
		throw new UnsupportedOperationException("encodeTrack is not supported.");
	}

	@Override
	public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
		throw new UnsupportedOperationException("decodeTrack is not supported.");
	}

	@Override
	public void shutdown() {
		executor.shutdown();
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
