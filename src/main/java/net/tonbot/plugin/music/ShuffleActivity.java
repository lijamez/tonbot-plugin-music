package net.tonbot.plugin.music;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

class ShuffleActivity extends AudioSessionActivity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "shuffle"))
			.parameters(ImmutableList.of("on/off"))
			.description("Toggles shuffle mode.")
			.build();

	private final BotUtils botUtils;

	@Inject
	public ShuffleActivity(DiscordAudioPlayerManager discordAudioPlayerManager, BotUtils botUtils) {
		super(discordAudioPlayerManager);
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {
		PlayMode currentMode = audioSession.getStatus().getPlayMode();
		PlayMode targetPlayMode;
		if (StringUtils.isBlank(args)) {
			// Toggle shuffle on and off
			if (currentMode == PlayMode.SHUFFLE) {
				targetPlayMode = PlayMode.STANDARD;
			} else {
				targetPlayMode = PlayMode.SHUFFLE;
			}
		} else {
			if (StringUtils.equalsIgnoreCase(args, "on")) {
				targetPlayMode = PlayMode.SHUFFLE;
			} else if (StringUtils.equalsIgnoreCase(args, "off")) {
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
		}

	}

	private String prettyMode(PlayMode pm) {
		return pm.getEmote().orElse("") + "``" + pm.getFriendlyName() + "``";
	}
}
