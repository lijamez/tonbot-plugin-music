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

public class RepeatActivity extends AudioSessionActivity {

	private static final List<String> FRIENDLY_REPEAT_MODES = Arrays.asList(RepeatMode.values())
			.stream()
			.map(m -> m.getFriendlyName())
			.collect(Collectors.toList());

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route("music repeat")
			.parameters(ImmutableList.of("mode"))
			.description("Sets the repeat mode to one of: " + FRIENDLY_REPEAT_MODES)
			.build();

	private final BotUtils botUtils;

	@Inject
	public RepeatActivity(GuildMusicManager guildMusicManager, BotUtils botUtils) {
		super(guildMusicManager);
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {
		RepeatMode targetRepeatMode;
		if (StringUtils.isBlank(args)) {
			// Scroll through the various modes.
			RepeatMode currentMode = audioSession.getStatus().getRepeatMode();
			int currentModeOrdinal = currentMode.ordinal();
			int nextModeOrdinal = (currentModeOrdinal + 1) % RepeatMode.values().length;
			targetRepeatMode = RepeatMode.values()[nextModeOrdinal];
		} else {
			// Find the desired repeat mode or else fail.
			targetRepeatMode = Arrays.asList(RepeatMode.values()).stream()
					.filter(repeatMode -> StringUtils.equalsIgnoreCase(repeatMode.getFriendlyName(), args.trim()))
					.findFirst()
					.orElseThrow(() -> new TonbotBusinessException(
							"Invalid repeat mode. You can enter one of: ``" + FRIENDLY_REPEAT_MODES + "``"));
		}

		audioSession.setLoopingMode(targetRepeatMode);
		botUtils.sendMessage(event.getChannel(), "Repeat mode has been changed to: "
				+ targetRepeatMode.getEmote().orElse("") + "``" + targetRepeatMode.getFriendlyName() + "``");
	}
}
