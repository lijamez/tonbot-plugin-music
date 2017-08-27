package net.tonbot.plugin.music;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Preconditions;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import sx.blah.discord.util.EmbedBuilder;

class YouTubeVideoEmbedAppender implements EmbedAppender {

	private static final int MAX_DESCRIPTION_LENGTH = 800;
	private static final String TRUNCATION_INDICATOR = "…";
	private static final String YOUTUBE_CHANNEL_URL_BASE = "http://www.youtube.com/channel/";

	private final String ytApiKey;
	private final YouTube yt;

	@Inject
	public YouTubeVideoEmbedAppender(@YouTubeApiKey String ytApiKey) {
		this.ytApiKey = Preconditions.checkNotNull(ytApiKey, "ytApiKey must be non-null.");

		this.yt = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), new HttpRequestInitializer() {
			@Override
			public void initialize(HttpRequest request) throws IOException {
			}
		})
				.setApplicationName("Tonbot")
				.build();
	}

	@Override
	public void appendDetails(AudioTrack audioTrack, EmbedBuilder embedBuilder) {
		Preconditions.checkNotNull(audioTrack, "audioTrack must be non-null.");
		Preconditions.checkNotNull(embedBuilder, "embedBuilder must be non-null.");

		String youtubeVideoId = audioTrack.getIdentifier();

		Video video = getVideo(youtubeVideoId);
		String description = video.getSnippet().getDescription();
		String videoThumbnailUrl = video.getSnippet().getThumbnails().getDefault().getUrl();
		Channel channel = getChannel(video.getSnippet().getChannelId());
		String channelThumbnail = channel.getSnippet().getThumbnails().getDefault().getUrl();

		embedBuilder.withThumbnail(videoThumbnailUrl);

		embedBuilder.withAuthorIcon(channelThumbnail);
		embedBuilder.withAuthorUrl(YOUTUBE_CHANNEL_URL_BASE + channel.getId());

		if (description.length() > MAX_DESCRIPTION_LENGTH) {
			description = StringUtils.truncate(description, MAX_DESCRIPTION_LENGTH - TRUNCATION_INDICATOR.length())
					+ TRUNCATION_INDICATOR;
		}

		embedBuilder.appendField("Description", description, false);
	}

	private Video getVideo(String youtubeVideoId) {
		try {
			VideoListResponse videoListResponse = yt.videos()
					.list("snippet")
					.setId(youtubeVideoId)
					.setKey(ytApiKey)
					.execute();

			List<Video> videoList = videoListResponse.getItems();

			if (videoList.isEmpty()) {
				throw new IllegalArgumentException("Video with ID '" + youtubeVideoId + "' could not be found.");
			}

			Video video = videoList.get(0);

			return video;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Channel getChannel(String channelId) {
		try {
			ChannelListResponse channelListResponse = yt.channels()
					.list("snippet")
					.setId(channelId)
					.setKey(ytApiKey)
					.execute();

			List<Channel> channelList = channelListResponse.getItems();

			if (channelList.isEmpty()) {
				throw new IllegalArgumentException("Channel with ID '" + channelId + "' could not be found.");
			}

			Channel channel = channelList.get(0);

			return channel;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
