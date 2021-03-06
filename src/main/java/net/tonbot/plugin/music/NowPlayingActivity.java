package net.tonbot.plugin.music;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

class NowPlayingActivity extends AudioSessionActivity<Void> {

	private static final String STREAM_TIME = "STREAM";
	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder().route("music nowplaying")
			.description("Shows what's playing.").build();

	private final IDiscordClient discordClient;
	private final List<EmbedAppender> embedAppenders;
	private final BotUtils botUtils;
	private final Color color;

	@Inject
	public NowPlayingActivity(IDiscordClient discordClient, GuildMusicManager guildMusicManager,
			List<EmbedAppender> embedAppenders, BotUtils botUtils, Color color) {
		super(guildMusicManager);
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
		this.embedAppenders = Preconditions.checkNotNull(embedAppenders, "embedAppenders must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.color = Preconditions.checkNotNull(color);
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public Class<?> getRequestType() {
		return Void.class;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, Void request, AudioSession audioSession) {
		AudioSessionStatus status = audioSession.getStatus();

		AudioTrack npTrack = status.getNowPlaying().orElse(null);

		if (npTrack != null) {
			AudioTrackInfo info = npTrack.getInfo();
			EmbedBuilder eb = new EmbedBuilder();
			eb.withColor(color);

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

			// Apply the first applicable custom embed appender
			boolean embedAppenderUsed = false;
			for (EmbedAppender ea : embedAppenders) {

				for (Class<? extends AudioTrack> type : ea.getAppendableTypes()) {
					if (type.isAssignableFrom(npTrack.getClass())) {
						ea.appendDetails(npTrack, eb);
						embedAppenderUsed = true;
						break;
					}
				}

				if (embedAppenderUsed) {
					break;
				}
			}

			// The "added by" user goes in the footer.
			withAddedByUser(eb, npTrack, event);

			botUtils.sendEmbed(event.getChannel(), eb.build());
		} else {
			botUtils.sendMessage(event.getChannel(), "There's nothing playing right now.");
		}
	}

	private String renderPlaybackStatus(AudioSession audioSession, AudioTrack npTrack) {
		StringBuffer sb = new StringBuffer();
		String state = audioSession.isPaused() ? ":pause_button:" : ":arrow_forward:";
		sb.append(state + " ");

		if (npTrack.getInfo().isStream) {
			sb.append("``").append(STREAM_TIME).append("``");
		} else {
			String progressBar = ProgressBarRenderer.render(npTrack.getPosition(), npTrack.getDuration());
			String positionTime = TimeFormatter.toFriendlyString(npTrack.getPosition(), TimeUnit.MILLISECONDS);
			String remainingTime = "-" + TimeFormatter.toFriendlyString(npTrack.getDuration() - npTrack.getPosition(),
					TimeUnit.MILLISECONDS);

			sb.append(positionTime + " " + progressBar + " " + remainingTime + " ");
		}

		String playbackModifiers = renderPlaybackModifiers(audioSession.getStatus());
		sb.append(playbackModifiers + "\n");

		return sb.toString();
	}

	private String renderPlaybackModifiers(AudioSessionStatus sessionStatus) {

		StringBuffer sb = new StringBuffer();

		// Play Mode
		PlayMode playMode = sessionStatus.getPlayMode();
		if (playMode != PlayMode.STANDARD) {
			sb.append(playMode.getEmote().orElse(playMode.getFriendlyName()));
		}

		// Repeat Mode
		RepeatMode repeatMode = sessionStatus.getRepeatMode();
		if (repeatMode != RepeatMode.OFF) {
			sb.append(repeatMode.getEmote().orElse(repeatMode.name()));
		}

		String playbackModifiers = sb.toString();
		return playbackModifiers;
	}

	private void withAddedByUser(EmbedBuilder eb, AudioTrack npTrack, MessageReceivedEvent event) {
		if (npTrack.getUserData() != null && npTrack.getUserData() instanceof ExtraTrackInfo) {
			ExtraTrackInfo eti = (ExtraTrackInfo) npTrack.getUserData();

			IUser addedByUser = discordClient.getUserByID(eti.getAddedByUserId());
			if (addedByUser != null) {
				eb.withFooterText("Added by " + addedByUser.getDisplayName(event.getGuild()));
				eb.withFooterIcon(addedByUser.getAvatarURL());
			}
		}
	}
}
