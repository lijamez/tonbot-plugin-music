package net.tonbot.plugin.music;

import java.util.Set;

import com.google.common.base.Preconditions;
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

import net.tonbot.common.Activity;
import net.tonbot.common.BotUtils;
import net.tonbot.common.Prefix;
import sx.blah.discord.api.IDiscordClient;

class MusicModule extends AbstractModule {

	private final IDiscordClient discordClient;
	private final String prefix;
	private final BotUtils botUtils;

	public MusicModule(IDiscordClient discordClient, String prefix, BotUtils botUtils) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.prefix = Preconditions.checkNotNull(prefix, "prefix must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	protected void configure() {
		bind(IDiscordClient.class).toInstance(discordClient);
		bind(String.class).annotatedWith(Prefix.class).toInstance(prefix);
		bind(BotUtils.class).toInstance(botUtils);
		bind(DiscordAudioPlayerManager.class).in(Scopes.SINGLETON);
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
			ModeActivity modeActivity,
			SkipActivity skipActivity,
			RepeatActivity repeatActivity,
			NowPlayingActivity npActivity) {
		return ImmutableSet.of(beckonActivity, dismissActivity, playActivity, stopActivity, pauseActivity, listActivity,
				modeActivity, skipActivity, repeatActivity, npActivity);
	}

	@Provides
	@Singleton
	Set<Object> eventListeners(VoiceChannelEventListener vcEventListener) {
		return ImmutableSet.of(vcEventListener);
	}

	@Provides
	@Singleton
	AudioPlayerManager audioPlayerManager(YoutubeAudioSourceManager yasm) {
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
		apm.registerSourceManager(new HttpAudioSourceManager());

		return apm;
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
}
