package net.tonbot.plugin.music;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

class SeekActivity extends AudioSessionActivity {

	private static final ActivityDescriptor DESCRIPTOR = ActivityDescriptor.builder()
			.route("music seek")
			.parameters(ImmutableList.of("time or offset"))
			.description(
					"Seeks within the current track.")
			.usageDescription(
					"**Jump to a position:**\n"
							+ "``${absoluteReferencedRoute} 2m30s``\n"
							+ "``${absoluteReferencedRoute} 1h20m45s``\n"
							+ "\n"
							+ "**Move the position forward and backward:**\n"
							+ "``${absoluteReferencedRoute} +30s``\n"
							+ "``${absoluteReferencedRoute} -1m10s``\n")
			.build();

	private BotUtils botUtils;

	@Inject
	public SeekActivity(
			DiscordAudioPlayerManager discordAudioPlayerManager,
			BotUtils botUtils) {
		super(discordAudioPlayerManager);

		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return DESCRIPTOR;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {

		try {
			Duration duration;
			SeekType seekType;
			if (args.startsWith("+")) {
				seekType = SeekType.DELTA;
				duration = Duration.parse("PT" + args.substring(1));
			} else if (args.startsWith("-")) {
				seekType = SeekType.DELTA;
				duration = Duration.parse("-PT" + args.substring(1));
			} else {
				seekType = SeekType.ABSOLUTE;
				duration = Duration.parse("PT" + args);
			}

			long timeMs = duration.getSeconds() * 1000;

			AudioTrack seekedTrack;
			try {
				seekedTrack = audioSession.seek(timeMs, seekType).orElse(null);
			} catch (IllegalStateException e) {
				botUtils.sendMessage(event.getChannel(), e.getMessage());
				return;
			}

			if (seekedTrack != null) {
				String progressBar = ProgressBarRenderer.render(seekedTrack.getPosition(),
						seekedTrack.getDuration());
				String positionTime = TimeFormatter.toFriendlyString(seekedTrack.getPosition(), TimeUnit.MILLISECONDS);
				String remainingTime = "-"
						+ TimeFormatter.toFriendlyString(seekedTrack.getDuration() - seekedTrack.getPosition(),
								TimeUnit.MILLISECONDS);

				botUtils.sendMessage(event.getChannel(), positionTime + " " + progressBar + " " + remainingTime + " ");
			} else {
				botUtils.sendMessage(event.getChannel(), "Can't seek because there's nothing playing,");
			}

		} catch (DateTimeParseException e) {
			botUtils.sendMessage(event.getChannel(), "Invalid time. See usage for examples.");
		}
	}
}
