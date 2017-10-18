package net.tonbot.plugin.music;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;

import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

abstract class BinaryModeChangingActivity extends AudioSessionActivity {

	private final BotUtils botUtils;

	public BinaryModeChangingActivity(GuildMusicManager guildMusicManager, BotUtils botUtils) {
		super(guildMusicManager);
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	abstract PlayMode onMode();

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {
		PlayMode currentMode = audioSession.getStatus().getPlayMode();
		PlayMode onMode = onMode();
		PlayMode targetPlayMode;
		if (StringUtils.isBlank(args)) {
			// Toggle shuffle on and off
			if (currentMode == onMode) {
				targetPlayMode = PlayMode.STANDARD;
			} else {
				targetPlayMode = onMode;
			}
		} else {
			if (StringUtils.equalsIgnoreCase(args, "on")) {
				targetPlayMode = onMode;
			} else if (StringUtils.equalsIgnoreCase(args, "off") && currentMode == onMode) {
				// By default, turning shuffle off means going to standard mode.
				targetPlayMode = PlayMode.STANDARD;
			} else {
				// Don't know what the user intended.
				return;
			}
		}

		if (currentMode != targetPlayMode) {
			audioSession.setPlayMode(targetPlayMode);
			botUtils.sendMessage(event.getChannel(),
					"Play mode changed from " + prettyMode(currentMode) + " to " + prettyMode(targetPlayMode));
		} else {
			botUtils.sendMessage(event.getChannel(),
					"Play mode is already set to " + prettyMode(targetPlayMode));
		}

	}

	private String prettyMode(PlayMode pm) {
		return pm.getEmote().orElse("") + "``" + pm.getFriendlyName() + "``";
	}
}
