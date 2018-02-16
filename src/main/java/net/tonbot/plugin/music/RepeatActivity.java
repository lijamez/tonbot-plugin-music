package net.tonbot.plugin.music;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import net.tonbot.plugin.music.permissions.Action;
import net.tonbot.plugin.music.permissions.MusicPermissions;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

class RepeatActivity extends AudioSessionActivity<RepeatRequest> {

	private static final List<String> FRIENDLY_REPEAT_MODES = Arrays.asList(RepeatMode.values()).stream()
			.map(m -> m.getFriendlyName()).collect(Collectors.toList());

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder().route("music repeat")
			.parameters(ImmutableList.of("[mode]"))
			.description("Sets the repeat mode to one of: " + FRIENDLY_REPEAT_MODES).build();

	private final GuildMusicManager guildMusicManager;
	private final BotUtils botUtils;

	@Inject
	public RepeatActivity(GuildMusicManager guildMusicManager, BotUtils botUtils) {
		super(guildMusicManager);
		this.guildMusicManager = Preconditions.checkNotNull(guildMusicManager, "guildMusicManager must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public Class<?> getRequestType() {
		return RepeatRequest.class;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, RepeatRequest request, AudioSession audioSession) {
		MusicPermissions permissions = guildMusicManager.getPermission(event.getGuild().getLongID());
		permissions.checkPermission(event.getAuthor(), Action.REPEAT_MODE_CHANGE);

		RepeatMode targetRepeatMode;
		if (request.getRepeatMode() == null) {
			// Scroll through the various modes.
			RepeatMode currentMode = audioSession.getStatus().getRepeatMode();
			int currentModeOrdinal = currentMode.ordinal();
			int nextModeOrdinal = (currentModeOrdinal + 1) % RepeatMode.values().length;
			targetRepeatMode = RepeatMode.values()[nextModeOrdinal];
		} else {
			targetRepeatMode = request.getRepeatMode();
		}

		audioSession.setLoopingMode(targetRepeatMode);
		botUtils.sendMessage(event.getChannel(), "Repeat mode has been changed to: "
				+ targetRepeatMode.getEmote().orElse("") + "``" + targetRepeatMode.getFriendlyName() + "``");
	}
}
