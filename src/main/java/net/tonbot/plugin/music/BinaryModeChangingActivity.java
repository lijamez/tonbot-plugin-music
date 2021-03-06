package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;

import net.tonbot.common.BotUtils;
import net.tonbot.plugin.music.permissions.Action;
import net.tonbot.plugin.music.permissions.MusicPermissions;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

abstract class BinaryModeChangingActivity extends AudioSessionActivity<BinaryModeChangeRequest> {

	private final GuildMusicManager guildMusicManager;
	private final BotUtils botUtils;

	public BinaryModeChangingActivity(GuildMusicManager guildMusicManager, BotUtils botUtils) {
		super(guildMusicManager);
		this.guildMusicManager = Preconditions.checkNotNull(guildMusicManager, "guildMusicManager must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	abstract PlayMode onMode();

	@Override
	public Class<?> getRequestType() {
		return BinaryModeChangeRequest.class;
	}

	@Override
	public void enactWithSession(MessageReceivedEvent event, BinaryModeChangeRequest request,
			AudioSession audioSession) {

		MusicPermissions permissions = guildMusicManager.getPermission(event.getGuild().getLongID());
		permissions.checkPermission(event.getAuthor(), Action.PLAY_MODE_CHANGE);

		PlayMode currentMode = audioSession.getStatus().getPlayMode();
		PlayMode onMode = onMode();
		PlayMode targetPlayMode;

		if (request.getStatus() == null) {
			// Toggle shuffle on and off
			if (currentMode == onMode) {
				targetPlayMode = PlayMode.STANDARD;
			} else {
				targetPlayMode = onMode;
			}
		} else {
			if (request.getStatus() == ToggleStatus.ON) {
				targetPlayMode = onMode;
			} else if (currentMode == onMode) {
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
			botUtils.sendMessage(event.getChannel(), "Play mode is already set to " + prettyMode(targetPlayMode));
		}

	}

	private String prettyMode(PlayMode pm) {
		return pm.getEmote().orElse("") + "``" + pm.getFriendlyName() + "``";
	}
}
