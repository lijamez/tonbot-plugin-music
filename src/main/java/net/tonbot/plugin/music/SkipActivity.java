package net.tonbot.plugin.music;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

class SkipActivity extends AudioSessionActivity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "skip"))
			.parameters(ImmutableList.of("track number"))
			.description(
					"Skips the song with a given track number. If no track number is provided, then the currently playing song is skipped.")
			.build();

	private final BotUtils botUtils;

	@Inject
	public SkipActivity(DiscordAudioPlayerManager discordAudioPlayerManager, BotUtils botUtils) {
		super(discordAudioPlayerManager);
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {

		if (StringUtils.isBlank(args)) {
			// Skip the current track.
			audioSession.skip();
		} else {
			// Skip the specified track.
			try {
				int skipIndex = Integer.parseInt(args);
				AudioTrack skippedTrack = audioSession.skip(skipIndex);

				botUtils.sendMessage(event.getChannel(), "Skipped **" + skippedTrack.getInfo().title + "**");
			} catch (NumberFormatException e) {
				botUtils.sendMessage(event.getChannel(), "You need to specify a track number to skip.");
			} catch (IndexOutOfBoundsException e) {
				botUtils.sendMessage(event.getChannel(), "That track number doesn't exist.");
			}
		}
	}
}
