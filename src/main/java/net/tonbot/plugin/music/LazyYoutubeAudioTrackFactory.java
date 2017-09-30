package net.tonbot.plugin.music;

import java.util.List;
import java.util.stream.Collectors;

import com.google.api.client.util.Preconditions;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSearchProvider;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

class LazyYoutubeAudioTrackFactory implements AudioTrackFactory {

	private final YoutubeSearchProvider ytSearchProvider;
	private final YoutubeAudioSourceManager ytAudioSourceManager;

	@Inject
	public LazyYoutubeAudioTrackFactory(
			YoutubeSearchProvider ytSearchProvider,
			YoutubeAudioSourceManager ytAudioSourceManager) {
		this.ytSearchProvider = Preconditions.checkNotNull(ytSearchProvider, "ytSearchProvider must be non-null.");
		this.ytAudioSourceManager = Preconditions.checkNotNull(ytAudioSourceManager,
				"ytAudioSourceManager must be non-null.");
	}

	@Override
	public List<AudioTrack> getAudioTracks(List<SongMetadata> songMetadata) {
		return songMetadata.stream()
				.map(sm -> {
					AudioTrackInfo ati = new AudioTrackInfo(sm.getName(), sm.getArtist(), sm.getDuration(), "", false,
							"");
					return new LazyYoutubeAudioTrack(ati, ytAudioSourceManager, ytSearchProvider);
				})
				.collect(Collectors.toList());
	}
}
