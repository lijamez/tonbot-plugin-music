package net.tonbot.plugin.music;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import net.tonbot.common.TonbotBusinessException;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

class ModeActivity extends AudioSessionActivity {

	private static final List<String> FRIENDLY_MODE_NAMES = Arrays.asList(PlayMode.values()).stream()
			.map(pm -> pm.getFriendlyName())
			.collect(Collectors.toList());

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "mode"))
			.parameters(ImmutableList.of("mode"))
			.description("Sets the play mode to one of: " + FRIENDLY_MODE_NAMES)
			.build();

	private final BotUtils botUtils;

	@Inject
	public ModeActivity(DiscordAudioPlayerManager discordAudioPlayerManager, BotUtils botUtils) {
		super(discordAudioPlayerManager);
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {
		PlayMode targetPlayMode;
		if (StringUtils.isBlank(args)) {
			// Scroll through the various modes.
			PlayMode currentMode = audioSession.getStatus().getPlayMode();
			int currentModeOrdinal = currentMode.ordinal();
			int nextModeOrdinal = (currentModeOrdinal + 1) % RepeatMode.values().length;
			targetPlayMode = PlayMode.values()[nextModeOrdinal];
		} else {
			// Find the desired repeat mode or else fail.
			targetPlayMode = Arrays.asList(PlayMode.values()).stream()
					.filter(repeatMode -> StringUtils.equalsIgnoreCase(repeatMode.getFriendlyName(), args.trim()))
					.findFirst()
					.orElseThrow(() -> new TonbotBusinessException(
							"Invalid play mode. You can enter one of: ``" + FRIENDLY_MODE_NAMES + "``"));
		}

		audioSession.setPlayMode(targetPlayMode);
		botUtils.sendMessage(event.getChannel(), "Play mode has been changed to: "
				+ targetPlayMode.getEmote().orElse("") + "``" + targetPlayMode.getFriendlyName() + "``");
	}
}
