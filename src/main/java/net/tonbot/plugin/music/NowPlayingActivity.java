package net.tonbot.plugin.music;

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

	private final BotUtils botUtils;

	@Inject
	public NowPlayingActivity(DiscordAudioPlayerManager discordAudioPlayerManager, BotUtils botUtils) {
		super(discordAudioPlayerManager);
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

			// Youtube Specific
			if (StringUtils.equals(npTrack.getSourceManager().getSourceName(), "youtube")) {
				eb.withThumbnail("https://img.youtube.com/vi/" + npTrack.getIdentifier() + "/hqdefault.jpg");
			}

			// Track State
			String state = audioSession.isPaused() ? ":pause_button:" : ":arrow_forward:";
			eb.appendDescription(state + " ");

			String progressBar = renderProgressBar(npTrack);
			String positionTime = TimeFormatter.toFriendlyString(npTrack.getPosition());
			String remainingTime = "-" + TimeFormatter.toFriendlyString(npTrack.getDuration() - npTrack.getPosition());

			eb.appendDescription(positionTime + " " + progressBar + " " + remainingTime + " ");

			String playbackModifiers = renderPlaybackModifiers(status);
			eb.appendDescription(playbackModifiers + "\n");

			botUtils.sendEmbed(event.getChannel(), eb.build());
		} else {
			botUtils.sendMessage(event.getChannel(), "There's nothing playing right now.");
		}
	}

	private String renderProgressBar(AudioTrack track) {

		long duration = track.getDuration();
		long position = track.getPosition();

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
