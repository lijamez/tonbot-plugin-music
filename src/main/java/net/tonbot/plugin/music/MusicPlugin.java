package net.tonbot.plugin.music;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
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
		if (!configFile.exists()) {
			// TODO: Create the config file.
			throw new IllegalStateException("Config file doesn't exist.");
		}

		ObjectMapper objectMapper = new ObjectMapper();

		try {
			MusicPluginConfig config = objectMapper.readValue(configFile, MusicPluginConfig.class);
			this.injector = Guice.createInjector(
					new MusicModule(
							pluginArgs.getDiscordClient(),
							pluginArgs.getPrefix(),
							pluginArgs.getBotUtils(),
							config.getGoogleApiKey(),
							config.getSpotifyCredentials()));
		} catch (IOException e) {
			throw new RuntimeException("Could not read configuration file.", e);
		}
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

}
