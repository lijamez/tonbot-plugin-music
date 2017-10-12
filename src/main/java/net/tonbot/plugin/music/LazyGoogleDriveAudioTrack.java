package net.tonbot.plugin.music;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

/**
 * A Google Drive audio track should be lazy because:
 * <ol>
 * <li>Direct links (ie. no redirects) are short lived. We should keep the "web
 * content link" in memory for a long as the track is queued and then resolve to
 * the short lived direct download URL.</li>
 * <li>Loading a folder with a lot of tracks is slow. If the
 * GoogleDriveSourceManager tried to fetch metadata for all tracks at loadItem
 * time, it could take several minutes to load a folder.</li>
 * <ol>
 */
public class LazyGoogleDriveAudioTrack extends DelegatedAudioTrack {

	private static final Logger LOG = LoggerFactory.getLogger(LazyGoogleDriveAudioTrack.class);

	private final AudioTrackInfo initialAudioTrackInfo;
	private final HttpAudioSourceManager httpAudioSourceManager;
	private final DefaultAudioPlayerManager audioPlayerManager;

	private InternalAudioTrack realAudioTrack;

	public LazyGoogleDriveAudioTrack(
			AudioTrackInfo initialAudioTrackInfo,
			HttpAudioSourceManager httpAudioSourceManager,
			DefaultAudioPlayerManager audioPlayerManager) {
		super(initialAudioTrackInfo);
		this.initialAudioTrackInfo = Preconditions.checkNotNull(initialAudioTrackInfo,
				"initialAudioTrackInfo must be non-null.");
		this.httpAudioSourceManager = Preconditions.checkNotNull(httpAudioSourceManager,
				"httpAudioSourceManager must be non-null.");
		this.audioPlayerManager = Preconditions.checkNotNull(audioPlayerManager,
				"audioPlayerManager must be non-null.");
	}

	@Override
	public void process(LocalAudioTrackExecutor executor) throws Exception {
		AudioItem audioItem = new AudioReference(trackInfo.identifier, trackInfo.title);
		// Go through all of the redirects.
		// FIXME: Possible infinite redirect. Unlikely since it's Google Drive, but you
		// never know.
		do {
			try {
				audioItem = httpAudioSourceManager.loadItem(audioPlayerManager, (AudioReference) audioItem);
			} catch (FriendlyException e) {
				// Unfortunately, the HTTPAudioSourceManager throws an exception when it
				// encounters unknown files instead of just returning null.
				LOG.debug("File {} cannot be played back: {}", trackInfo.title, e.getMessage());
				audioItem = null;
			}
		} while (audioItem instanceof AudioReference);

		if (audioItem instanceof InternalAudioTrack) {
			this.realAudioTrack = (InternalAudioTrack) audioItem;
			this.processDelegate(realAudioTrack, executor);
		} else {
			throw new FriendlyException("File is unplayable.", Severity.COMMON, null);
		}
	}

	@Override
	public AudioTrackInfo getInfo() {
		if (realAudioTrack != null) {
			return realAudioTrack.getInfo();
		}

		return initialAudioTrackInfo;
	}

	@Override
	public boolean isSeekable() {
		if (realAudioTrack != null) {
			return realAudioTrack.isSeekable();
		}

		return false;
	}

	@Override
	public AudioTrack makeClone() {
		LazyGoogleDriveAudioTrack clone = new LazyGoogleDriveAudioTrack(this.getInfo(), httpAudioSourceManager,
				audioPlayerManager);
		clone.setUserData(this.getUserData());

		return clone;
	}
}
