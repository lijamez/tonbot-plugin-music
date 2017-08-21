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
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;

import net.tonbot.common.Activity;
import net.tonbot.common.BotUtils;
import net.tonbot.common.Prefix;

class MusicModule extends AbstractModule {

	private final String prefix;
	private final BotUtils botUtils;
	
	public MusicModule(String prefix, BotUtils botUtils) {
		this.prefix = Preconditions.checkNotNull(prefix, "prefix must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}
	
	@Override
	protected void configure() {
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
			PauseActivity pauseActivity) {
		return ImmutableSet.of(beckonActivity, dismissActivity, playActivity, stopActivity, pauseActivity);
	}
	
	@Provides
	@Singleton
	AudioPlayerManager audioPlayerManager() {
		AudioPlayerManager apm = new DefaultAudioPlayerManager();
		apm.enableGcMonitoring();
		
		// Registers remote source handlers such as Youtube, SoundCloud, Bandcamp, etc.
		AudioSourceManagers.registerRemoteSources(apm);
		
		return apm;
	}
}
