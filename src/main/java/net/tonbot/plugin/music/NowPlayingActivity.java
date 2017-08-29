package net.tonbot.plugin.music;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.util.EmbedBuilder;

class NowPlayingActivity extends AudioSessionActivity {

	private static final int PROGRESS_BAR_WIDTH = 28;

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "np"))
			.description("Shows what's playing.")
			.build();

	private final List<EmbedAppender> embedAppenders;
	private final BotUtils botUtils;

	@Inject
	public NowPlayingActivity(
			DiscordAudioPlayerManager discordAudioPlayerManager,
			List<EmbedAppender> embedAppenders,
			BotUtils botUtils) {
		super(discordAudioPlayerManager);
		this.embedAppenders = Preconditions.checkNotNull(embedAppenders, "embedAppenders must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {
		AudioSessionStatus status = audioSession.getStatus();

		AudioTrack npTrack = status.getNowPlaying().orElse(null);

		if (npTrack != null) {
			AudioTrackInfo info = npTrack.getInfo();
			EmbedBuilder eb = new EmbedBuilder();

			if (!StringUtils.isBlank(info.author)) {
				eb.withAuthorName(info.author);
			}

			if (!StringUtils.isBlank(info.title)) {
				eb.withTitle(info.title);
			}

			if (!StringUtils.isBlank(info.uri)) {
				eb.withUrl(info.uri);
			}

			// Track State
			eb.appendField("Time", renderPlaybackStatus(audioSession, npTrack), false);

			// Additional Embed Appenders
			for (EmbedAppender ea : embedAppenders) {
				Class<? extends AudioTrack> type = ea.getAppendableType();
				if (type.isAssignableFrom(npTrack.getClass())) {
					ea.appendDetails(npTrack, eb);
					break;
				}
			}

			botUtils.sendEmbed(event.getChannel(), eb.build());
		} else {
			botUtils.sendMessage(event.getChannel(), "There's nothing playing right now.");
		}
	}

	private String renderProgressBar(AudioTrack track) {

		long duration = track.getDuration();
		long position = track.getPosition();

		// Guarantees that position <= duration for rendering purposes.
		position = position > duration ? duration : position;

		// Subtracted by 2 because to take the square brackets into account.
		int totalSpaces = PROGRESS_BAR_WIDTH - 2;
		int numDots = (int) (totalSpaces * ((double) position / duration));
		int numSpaces = totalSpaces - numDots;

		StringBuffer sb = new StringBuffer();
		sb.append("``[");

		for (int i = 0; i < numDots; i++) {
			sb.append("•");
		}

		for (int i = 0; i < numSpaces; i++) {
			sb.append(" ");
		}

		sb.append("]``");

		return sb.toString();
	}

	private String renderPlaybackStatus(AudioSession audioSession, AudioTrack npTrack) {
		StringBuffer sb = new StringBuffer();
		String state = audioSession.isPaused() ? ":pause_button:" : ":arrow_forward:";
		sb.append(state + " ");

		String progressBar = renderProgressBar(npTrack);
		String positionTime = TimeFormatter.toFriendlyString(npTrack.getPosition(), TimeUnit.MILLISECONDS);
		String remainingTime = "-"
				+ TimeFormatter.toFriendlyString(npTrack.getDuration() - npTrack.getPosition(), TimeUnit.MILLISECONDS);

		sb.append(positionTime + " " + progressBar + " " + remainingTime + " ");

		String playbackModifiers = renderPlaybackModifiers(audioSession.getStatus());
		sb.append(playbackModifiers + "\n");

		return sb.toString();
	}

	private String renderPlaybackModifiers(
			AudioSessionStatus sessionStatus) {

		StringBuffer sb = new StringBuffer();

		// Play Mode
		PlayMode playMode = sessionStatus.getPlayMode();
		if (playMode != PlayMode.STANDARD) {
			sb.append(playMode.getEmote().orElse(playMode.getFriendlyName()));
		}

		// Loop Mode
		RepeatMode loopMode = sessionStatus.getLoopMode();
		if (loopMode != RepeatMode.OFF) {
			sb.append(loopMode.getEmote().orElse(loopMode.name()));
		}

		String playbackModifiers = sb.toString();
		return playbackModifiers;
	}
}
