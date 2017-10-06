package net.tonbot.plugin.music;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSearchProvider;
import com.wrapper.spotify.Api;
import com.wrapper.spotify.exceptions.WebApiException;

import net.tonbot.common.Activity;
import net.tonbot.common.BotUtils;
import net.tonbot.common.Prefix;
import sx.blah.discord.api.IDiscordClient;

class MusicModule extends AbstractModule {

	private static final Logger LOG = LoggerFactory.getLogger(MusicModule.class);

	private final IDiscordClient discordClient;
	private final String prefix;
	private final BotUtils botUtils;
	private final String youTubeApiKey;
	private final SpotifyCredentials spotifyCredentials;

	public MusicModule(
			IDiscordClient discordClient,
			String prefix,
			BotUtils botUtils,
			String youTubeApiKey,
			SpotifyCredentials spotifyCredentials) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.prefix = Preconditions.checkNotNull(prefix, "prefix must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.youTubeApiKey = Preconditions.checkNotNull(youTubeApiKey, "youtubeApiKey must be non-null.");
		this.spotifyCredentials = spotifyCredentials;
	}

	@Override
	protected void configure() {
		bind(IDiscordClient.class).toInstance(discordClient);
		bind(String.class).annotatedWith(Prefix.class).toInstance(prefix);
		bind(BotUtils.class).toInstance(botUtils);
		bind(DiscordAudioPlayerManager.class).in(Scopes.SINGLETON);
		bind(String.class).annotatedWith(YouTubeApiKey.class).toInstance(youTubeApiKey);
		bind(AudioTrackFactory.class).to(LazyYoutubeAudioTrackFactory.class);
	}

	@Provides
	@Singleton
	Set<Activity> activities(
			BeckonActivity beckonActivity,
			DismissActivity dismissActivity,
			PlayActivity playActivity,
			StopActivity stopActivity,
			PauseActivity pauseActivity,
			ListActivity listActivity,
			SkipActivity skipActivity,
			RepeatActivity repeatActivity,
			NowPlayingActivity npActivity,
			ShuffleActivity shuffleActivity,
			RoundRobinActivity roundRobinActivity) {
		return ImmutableSet.of(beckonActivity, dismissActivity, playActivity, stopActivity, pauseActivity, listActivity,
				skipActivity, repeatActivity, npActivity, shuffleActivity, roundRobinActivity);
	}

	@Provides
	@Singleton
	Set<Object> eventListeners(VoiceChannelEventListener vcEventListener) {
		return ImmutableSet.of(vcEventListener);
	}

	@Provides
	@Singleton
	AudioPlayerManager audioPlayerManager(
			YoutubeAudioSourceManager yasm,
			ITunesPlaylistSourceManager itunesPlaylistSourceManager,
			@Nullable SpotifySourceManager spotifySourceManager,
			GoogleDriveSourceManager googleDriveSourceManager,
			HttpAudioSourceManager httpAudioSourceManager) {
		AudioPlayerManager apm = new DefaultAudioPlayerManager();
		apm.enableGcMonitoring();

		// Register remote source handlers such as Youtube, SoundCloud, Bandcamp, etc.
		// AudioSourceManagers.registerRemoteSources(apm);
		apm.registerSourceManager(yasm);
		apm.registerSourceManager(new SoundCloudAudioSourceManager());
		apm.registerSourceManager(new BandcampAudioSourceManager());
		apm.registerSourceManager(new VimeoAudioSourceManager());
		apm.registerSourceManager(new TwitchStreamAudioSourceManager());
		apm.registerSourceManager(new BeamAudioSourceManager());
		apm.registerSourceManager(itunesPlaylistSourceManager);

		if (spotifySourceManager != null) {
			apm.registerSourceManager(spotifySourceManager);
		}

		apm.registerSourceManager(googleDriveSourceManager);
		apm.registerSourceManager(httpAudioSourceManager);

		return apm;
	}

	@Provides
	@Singleton
	HttpAudioSourceManager httpAudioSourceManager() {
		return new HttpAudioSourceManager();
	}

	@Provides
	@Singleton
	YoutubeAudioSourceManager youtubeAudioSourceManager() {
		return new YoutubeAudioSourceManager(false);
	}

	@Provides
	@Singleton
	YoutubeSearchProvider youtubeSearchProvider(YoutubeAudioSourceManager yasm) {
		return new YoutubeSearchProvider(yasm);
	}

	@Provides
	@Singleton
	List<EmbedAppender> embedAppenders(YouTubeVideoEmbedAppender ytEmbedAppender) {
		return ImmutableList.of(ytEmbedAppender);
	}

	@Provides
	@Singleton
	Api spotifyApi() {
		if (spotifyCredentials != null) {
			Api api = Api.builder()
					.clientId(spotifyCredentials.getClientId())
					.clientSecret(spotifyCredentials.getClientSecret())
					.build();

			// Use the Client Credentials Flow to get an access token
			// https://developer.spotify.com/web-api/authorization-guide/#client-credentials-flow
			try {
				String accessToken = api.clientCredentialsGrant().build().get().getAccessToken();
				api.setAccessToken(accessToken);
			} catch (IOException | WebApiException e) {
				LOG.warn(
						"Unable to get access token from Spotify Accounts Service. Spotify support will "
								+ "not be available. Please check if the supplied credentials are valid.",
						e);
				return null;
			}

			return api;
		}

		LOG.warn("No Spotify credentials detected. Spotify will not be available.");
		return null;
	}

	@Provides
	@Singleton
	SpotifySourceManager spotifySourceManager(
			@Nullable Api spotifyApi,
			AudioTrackFactory audioTrackFactory) {
		if (spotifyApi == null) {
			return null;
		}

		return new SpotifySourceManager(spotifyApi, audioTrackFactory);
	}
}
