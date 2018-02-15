package net.tonbot.plugin.music;

import java.io.File;
import java.util.Set;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import net.tonbot.common.Activity;
import net.tonbot.common.TonbotPlugin;
import net.tonbot.common.TonbotPluginArgs;

public class MusicPlugin extends TonbotPlugin {

	private final Injector injector;

	public MusicPlugin(TonbotPluginArgs pluginArgs) {
		super(pluginArgs);

		File configFile = pluginArgs.getConfigFile();

		MusicPluginConfigManager configMgr = new MusicPluginConfigManager();
		MusicPluginConfig config = configMgr.readOrCreateConfig(configFile);

		this.injector = Guice.createInjector(new MusicModule(pluginArgs.getDiscordClient(), pluginArgs.getPrefix(),
				pluginArgs.getBotUtils(), pluginArgs.getColor(), pluginArgs.getPluginDataDir(),
				config.getYoutubeApiKey(), config.getGoogleDriveApiKey(), config.getSpotifyCredentials()));
	}

	@Override
	public String getActionDescription() {
		return "Play Music";
	}

	@Override
	public String getFriendlyName() {
		return "Music Player";
	}

	@Override
	public Set<Activity> getActivities() {
		return injector.getInstance(Key.get(new TypeLiteral<Set<Activity>>() {
		}));
	}

	@Override
	public Set<Object> getRawEventListeners() {
		return injector.getInstance(Key.get(new TypeLiteral<Set<Object>>() {
		}));
	}

	@Override
	public void destroy() {
		GuildMusicManager gmm = injector.getInstance(GuildMusicManager.class);
		gmm.save();
	}
}
