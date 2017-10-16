package net.tonbot.plugin.music;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;

import net.tonbot.common.PluginSetupException;

class MusicPluginConfigManager {

	private static final String INITIAL_CONFIG_FILE = "initial_config.config";

	/**
	 * Reads or creates a new config file.
	 * 
	 * @param configFile
	 *            The config {@link File}. Non-null.
	 * @return {@link MusicPluginConfig}. Non-null.
	 * @throws UncheckedIOException
	 *             if the {@code configFile} can't be read.
	 * @throws PluginSetupException
	 *             if the contents of the {@code configFile} is malformed.
	 */
	public MusicPluginConfig readOrCreateConfig(File configFile) {
		Preconditions.checkNotNull(configFile, "configFile must be non-null.");

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.enable(Feature.ALLOW_COMMENTS);
		objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

		if (!configFile.exists()) {
			URL defaultConfigJson = Resources.getResource(INITIAL_CONFIG_FILE);
			try {
				FileUtils.copyURLToFile(defaultConfigJson, configFile);
			} catch (IOException e) {
				throw new UncheckedIOException("Unable to create a new config file at " + configFile.getName(), e);
			}
		}

		try {
			MusicPluginConfig config = objectMapper.readValue(configFile, MusicPluginConfig.class);
			return config;
		} catch (JsonParseException e) {
			String message = String.format("The config file at %s is not valid JSON.", configFile.getAbsolutePath());
			throw new PluginSetupException(message);
		} catch (JsonMappingException e) {
			String message = String.format(
					"The config file at %s appears to be malformed. If you want you can delete it and re-run the bot so that a fresh one can be generated.",
					configFile.getAbsolutePath());
			throw new PluginSetupException(message, e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

	}
}
