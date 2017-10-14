package net.tonbot.plugin.music;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.repackaged.com.google.common.base.Preconditions;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;

/**
 * Plays a media file shared via Google Drive share link.
 */
public class GoogleDriveSourceManager implements AudioSourceManager {

	private static final Logger LOG = LoggerFactory.getLogger(GoogleDriveSourceManager.class);

	private static final String GOOGLE_DRIVE_HOST = "drive.google.com";
	private static final String OPEN_PATH = "/open";

	private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

	private final Drive drive;
	private final String apiKey;
	private final HttpAudioSourceManager httpAsm;

	@Inject
	public GoogleDriveSourceManager(Drive drive, @GoogleApiKey String apiKey, HttpAudioSourceManager httpAsm) {
		this.drive = Preconditions.checkNotNull(drive, "drive must be non-null.");
		this.apiKey = Preconditions.checkNotNull(apiKey, "apiKey must be non-null.");
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

			if (!StringUtils.equals(url.getHost(), GOOGLE_DRIVE_HOST)
					|| !StringUtils.startsWith(url.getPath(), OPEN_PATH)) {
				return null;
			}

			LOG.debug("Got a Google Drive link: {}", url);

			List<NameValuePair> queryPairs = URLEncodedUtils.parse(url.getQuery(), StandardCharsets.UTF_8);
			Map<String, String> params = queryPairs.stream()
					.collect(Collectors.toMap(kv -> kv.getName(), kv -> kv.getValue()));
			String rootFileId = params.get("id");

			if (rootFileId == null) {
				return null;
			}

			File rootFile = drive.files()
					.get(rootFileId)
					.setKey(apiKey)
					.execute();
			boolean rootIsFolder = StringUtils.equals(rootFile.getMimeType(), FOLDER_MIME_TYPE);

			LOG.debug("File with ID {} is a folder? {}", rootFileId, rootIsFolder);

			if (!rootIsFolder) {
				AudioItem audioItem = httpAsm.loadItem(manager,
						new AudioReference(rootFile.getWebContentLink(), rootFile.getTitle()));
				return audioItem;
			} else {
				List<File> files = getFilesRecursively(rootFileId);
				List<AudioTrack> audioTracks = files.stream()
						.map(file -> new LazyGoogleDriveAudioTrack(
								new AudioTrackInfo(file.getTitle(), "", Long.MAX_VALUE, file.getWebContentLink(), true,
										file.getWebContentLink()),
								httpAsm,
								manager))
						.collect(Collectors.toList());
				BasicAudioPlaylist playlist = new BasicAudioPlaylist(rootFile.getTitle(), audioTracks, null, false);
				return playlist;
			}

		} catch (MalformedURLException e) {
			return null;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private List<File> getFilesRecursively(String folderId) throws IOException {
		List<File> files = new ArrayList<>();
		getFilesRecursively(folderId, files);

		return files;
	}

	private void getFilesRecursively(String folderId, List<File> acc) throws IOException {

		StringBuffer qBuilder = new StringBuffer();
		qBuilder.append("'").append(folderId).append("' in parents");

		FileList fileList = drive.files().list()
				.setKey(apiKey)
				.setQ(qBuilder.toString())
				.setOrderBy("title")
				.execute();

		List<String> subfolderIds = new ArrayList<>();
		fileList.getItems().forEach(file -> {
			if (StringUtils.equals(file.getMimeType(), FOLDER_MIME_TYPE)) {
				subfolderIds.add(file.getId());
			} else {
				LOG.debug("Found a file: {}", file.getTitle());
				acc.add(file);
			}
		});

		for (String subfolderId : subfolderIds) {
			getFilesRecursively(subfolderId, acc);
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
