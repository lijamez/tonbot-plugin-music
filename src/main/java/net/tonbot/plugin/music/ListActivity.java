package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

class ListActivity extends AudioSessionActivity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "list"))
			.description("Displays the upcoming tracks.")
			.build();

	private final BotUtils botUtils;

	@Inject
	public ListActivity(DiscordAudioPlayerManager discordAudioPlayerManager, BotUtils botUtils) {
		super(discordAudioPlayerManager);
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");

	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, String args, AudioSession audioSession) {
		AudioSessionStatus sessionStatus = audioSession.getStatus();

		List<AudioTrack> upcomingTracks = sessionStatus.getUpcomingTracks();

		EmbedBuilder embedBuilder = new EmbedBuilder();

		// Now Playing

		AudioTrack nowPlaying = sessionStatus.getNowPlaying().orElse(null);

		if (nowPlaying != null) {
			String title = nowPlaying.getInfo().title;
			if (!StringUtils.isBlank(title)) {
				embedBuilder.withTitle(title);
			}

			String authorName = nowPlaying.getInfo().author;
			if (!StringUtils.isBlank(authorName)) {
				embedBuilder.withAuthorName(authorName);
			}

			String trackUrl = nowPlaying.getInfo().uri;
			if (!StringUtils.isBlank(trackUrl)) {
				embedBuilder.withUrl(trackUrl);
			}

			StringBuffer timeSb = new StringBuffer();
			timeSb.append(" ``[").append(TimeFormatter.toFriendlyString(nowPlaying.getPosition())).append("/")
					.append(TimeFormatter.toFriendlyString(nowPlaying.getDuration())).append("]``");
			embedBuilder.withDescription(timeSb.toString());

		} else {
			embedBuilder.withDescription("Not playing anything.");
		}

		// Upcoming tracks
		StringBuffer sb = new StringBuffer();

		if (upcomingTracks.isEmpty()) {
			sb.append("No tracks.");
		} else {
			List<String> trackStrings = new ArrayList<>();
			for (int i = 0; i < upcomingTracks.size(); i++) {
				AudioTrack track = upcomingTracks.get(i);
				ExtraTrackInfo extraTrackInfo = track.getUserData(ExtraTrackInfo.class);
				IUser addedByUser = event.getClient().getUserByID(extraTrackInfo.getAddedByUserId());

				String trackString = new StringBuffer()
						.append("``[").append(i).append("]`` **")
						.append(track.getInfo().title)
						.append("** ``[").append(TimeFormatter.toFriendlyString(track.getDuration()))
						.append("]`` added by **").append(addedByUser.getNicknameForGuild(event.getGuild()))
						.append("**")
						.toString();
				trackStrings.add(trackString);
			}

			sb.append(StringUtils.join(trackStrings, "\n"));
		}

		embedBuilder.appendField("Next up:", sb.toString(), false);
		// TODO: Pagination
		embedBuilder.appendField("Play mode:", sessionStatus.getPlayMode().toString(), false);

		botUtils.sendEmbed(event.getChannel(), embedBuilder.build());
	}
}
