package net.tonbot.plugin.music;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveRequestInitializer;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeRequestInitializer;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
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
import com.wrapper.spotify.exceptions.BadRequestException;
import com.wrapper.spotify.exceptions.WebApiException;

import net.tonbot.common.Activity;
import net.tonbot.common.BotUtils;
import net.tonbot.common.Prefix;
import net.tonbot.common.TonbotTechnicalFault;
import net.tonbot.plugin.music.googledrive.GoogleDriveSourceManager;
import net.tonbot.plugin.music.itunes.ITunesPlaylistSourceManager;
import net.tonbot.plugin.music.spotify.SpotifyCredentials;
import net.tonbot.plugin.music.spotify.SpotifySourceManager;
import sx.blah.discord.api.IDiscordClient;

class MusicModule extends AbstractModule {

	private static final String APPLICATION_NAME = "Tonbot";
	private static final Logger LOG = LoggerFactory.getLogger(MusicModule.class);
	private static final int MAX_SEARCH_RESULTS = 10;

	private final IDiscordClient discordClient;
	private final String prefix;
	private final BotUtils botUtils;
	private final Color color;
	private final File saveDir;
	private final String youtubeApiKey;
	private final String googleDriveApiKey;
	private final SpotifyCredentials spotifyCredentials;

	public MusicModule(
			IDiscordClient discordClient,
			String prefix,
			BotUtils botUtils,
			Color color,
			File saveDir,
			String youtubeApiKey,
			String googleDriveApiKey,
			SpotifyCredentials spotifyCredentials) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.prefix = Preconditions.checkNotNull(prefix, "prefix must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.color = Preconditions.checkNotNull(color, "color must be non-null.");
		this.saveDir = Preconditions.checkNotNull(saveDir, "saveDir must be non-null.");
		this.youtubeApiKey = youtubeApiKey;
		this.googleDriveApiKey = googleDriveApiKey;
		this.spotifyCredentials = spotifyCredentials;
	}

	@Override
	protected void configure() {
		bind(IDiscordClient.class).toInstance(discordClient);
		bind(String.class).annotatedWith(Prefix.class).toInstance(prefix);
		bind(BotUtils.class).toInstance(botUtils);
		bind(Color.class).toInstance(color);
		bind(AudioTrackFactory.class).to(LazyYoutubeAudioTrackFactory.class);
		bind(File.class).toInstance(saveDir);
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
			RoundRobinActivity roundRobinActivity,
			SeekActivity seekActivity,
			PermissionsListActivity permissionsListActivity,
			PermissionsAddActivity permissionsAddActivity,
			PermissionsRemoveActivity permissionsRemoveActivity) {
		return ImmutableSet.of(beckonActivity, dismissActivity, playActivity, stopActivity, pauseActivity, listActivity,
				skipActivity, repeatActivity, npActivity, shuffleActivity, roundRobinActivity, seekActivity,
				permissionsListActivity, permissionsAddActivity, permissionsRemoveActivity);
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
			@Nullable GoogleDriveSourceManager googleDriveSourceManager,
			HttpAudioSourceManager httpAudioSourceManager) {
		AudioPlayerManager apm = new DefaultAudioPlayerManager();

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

		if (googleDriveSourceManager != null) {
			apm.registerSourceManager(googleDriveSourceManager);
		}

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
	GoogleDriveSourceManager googleDriveSourceManager(
			@Nullable Drive drive,
			HttpAudioSourceManager httpAsm) {
		if (drive == null) {
			return null;
		}

		return new GoogleDriveSourceManager(drive, httpAsm);
	}

	@Provides
	@Singleton
	List<EmbedAppender> embedAppenders(@Nullable YouTubeVideoEmbedAppender ytEmbedAppender) {
		ImmutableList.Builder<EmbedAppender> builder = ImmutableList.builder();
		if (ytEmbedAppender != null) {
			builder.add(ytEmbedAppender);
		}

		return builder.build();
	}

	@Provides
	@Singleton
	YouTubeVideoEmbedAppender ytEmbedAppender(@Nullable YouTube yt) {
		if (yt == null) {
			return null;
		}

		return new YouTubeVideoEmbedAppender(yt);
	}

	@Provides
	@Singleton
	Api spotifyApi() {
		if (spotifyCredentials != null
				&& !StringUtils.isAnyBlank(spotifyCredentials.getClientId(), spotifyCredentials.getClientSecret())) {
			Api api = Api.builder()
					.clientId(spotifyCredentials.getClientId())
					.clientSecret(spotifyCredentials.getClientSecret())
					.build();

			// Use the Client Credentials Flow to get an access token
			// https://developer.spotify.com/web-api/authorization-guide/#client-credentials-flow
			try {
				String accessToken = api.clientCredentialsGrant().build().get().getAccessToken();
				api.setAccessToken(accessToken);
			} catch (IOException e) {
				throw new UncheckedIOException("Unable to contact Spotify Accounts Service.", e);
			} catch (BadRequestException e) {
				LOG.warn(
						"Unable to get access token from Spotify Accounts Service. Spotify support will "
								+ "not be available. Please check if the supplied credentials are valid.");
				return null;
			} catch (WebApiException e) {
				throw new TonbotTechnicalFault("Spotify Accounts Service returned an unknown error.", e);
			}

			return api;
		}

		LOG.warn("No Spotify credentials detected or they are invalid. Spotify support will be disabled.");
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

	@Provides
	@Singleton
	Drive drive() throws GeneralSecurityException, IOException {
		if (StringUtils.isBlank(googleDriveApiKey)) {
			LOG.warn("No Google Drive API key found. Google Drive support will be disabled.");
			return null;
		}

		HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

		return new Drive.Builder(httpTransport, jsonFactory, null)
				.setApplicationName(APPLICATION_NAME)
				.setDriveRequestInitializer(new DriveRequestInitializer(googleDriveApiKey))
				.build();
	}

	@Provides
	@Singleton
	YouTube youtube() {
		if (StringUtils.isBlank(youtubeApiKey)) {
			LOG.warn("No YouTube API key found. Enhanced Now Playing will be disabled.");
			return null;
		}

		return new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), new HttpRequestInitializer() {
			@Override
			public void initialize(HttpRequest request) throws IOException {
			}
		})
				.setApplicationName(APPLICATION_NAME)
				.setYouTubeRequestInitializer(new YouTubeRequestInitializer(youtubeApiKey))
				.build();
	}

	@Provides
	@Singleton
	TrackSearcher trackSearcher(YoutubeSearchProvider ytSearchProvider) {
		return new TrackSearcher(ytSearchProvider, MAX_SEARCH_RESULTS);
	}

	@Provides
	@Singleton
	GuildMusicManager guildMusicManager(
			IDiscordClient discordClient,
			AudioSessionFactory audioSessionFactory,
			File saveDir,
			ObjectMapper objectMapper) {
		GuildMusicManager gmm = new GuildMusicManager(
				discordClient,
				audioSessionFactory,
				saveDir,
				objectMapper);

		gmm.load();

		return gmm;
	}
}
