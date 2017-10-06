package net.tonbot.plugin.music;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;

import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

/**
 * Plays a media file shared via Google Drive share link.
 */
class GoogleDriveSourceManager implements AudioSourceManager {

	private static final String GOOGLE_DRIVE_HOST = "drive.google.com";
	private static final String OPEN_PATH = "/open";

	private static final String FILE_DOWNLOAD_PATH = "https://drive.google.com/uc";

	private final HttpAudioSourceManager httpAsm;

	@Inject
	public GoogleDriveSourceManager(HttpAudioSourceManager httpAsm) {
		this.httpAsm = Preconditions.checkNotNull(httpAsm, "httpAsm must be non-null.");
	}

	@Override
	public String getSourceName() {
		return "Google Drive";
	}

	@Override
	public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
		try {
			URL url = new URL(reference.identifier);

			// Must be a discord attachment.
			if (!StringUtils.equals(url.getHost(), GOOGLE_DRIVE_HOST)
					|| !StringUtils.startsWith(url.getPath(), OPEN_PATH)) {
				return null;
			}

			List<NameValuePair> queryPairs = URLEncodedUtils.parse(url.getQuery(), StandardCharsets.UTF_8);
			Map<String, String> params = queryPairs.stream()
					.collect(Collectors.toMap(kv -> kv.getName(), kv -> kv.getValue()));
			String id = params.get("id");

			if (id == null) {
				return null;
			}

			URI downloadUri = getDownloadUri(id);

			return httpAsm.loadItem(manager, new AudioReference(downloadUri.toString(), "Google Drive File"));

		} catch (MalformedURLException e) {
			return null;
		}
	}
	
	private URI getDownloadUri(String id) {
		try {
			URI uri = new URIBuilder(FILE_DOWNLOAD_PATH)
					.addParameter("authuser", "0")
					.addParameter("id", id)
					.addParameter("export", "download")
					.build();

			return uri;
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Unable to construct download URL.", e);
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
}
