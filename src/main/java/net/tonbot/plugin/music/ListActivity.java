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
import net.tonbot.common.TonbotBusinessException;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

class ListActivity extends AudioSessionActivity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "list"))
			.description("Displays the upcoming tracks.")
			.build();
	private static final int TRACKS_PER_PAGE = 10;

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
		IDiscordClient client = event.getClient();
		IGuild guild = event.getGuild();
		AudioSessionStatus sessionStatus = audioSession.getStatus();

		// Page numbers should always start at 1, but internally, page 1 is page 0.
		int requestedPage; // The zero-indexed requested page.
		if (!StringUtils.isBlank(args)) {
			try {
				requestedPage = Integer.parseInt(args.trim()) - 1;
			} catch (IllegalArgumentException e) {
				throw new TonbotBusinessException("You need to enter a page number.", e);
			}

			int numUpcomingTracks = sessionStatus.getUpcomingTracks().size();
			if (requestedPage < 0
					|| (numUpcomingTracks != 0 && requestedPage > (numUpcomingTracks - 1) / TRACKS_PER_PAGE)) {
				throw new TonbotBusinessException("Page doesn't exist.");
			}
		} else {
			requestedPage = 0;
		}

		EmbedBuilder embedBuilder = new EmbedBuilder();

		if (requestedPage == 0) {
			appendNowPlaying(embedBuilder, sessionStatus, client, guild);
		}

		appendUpNext(requestedPage, embedBuilder, sessionStatus, client, guild);

		if (requestedPage == 0) {
			embedBuilder.appendField("Play mode:", sessionStatus.getPlayMode().toString(), false);
		}

		botUtils.sendEmbed(event.getChannel(), embedBuilder.build());
	}

	private void appendNowPlaying(
			EmbedBuilder embedBuilder,
			AudioSessionStatus sessionStatus,
			IDiscordClient client,
			IGuild guild) {

		AudioTrack nowPlaying = sessionStatus.getNowPlaying().orElse(null);

		embedBuilder.withAuthorName("Now Playing");

		if (nowPlaying != null) {
			String title = nowPlaying.getInfo().title;
			if (!StringUtils.isBlank(title)) {
				embedBuilder.withTitle(title);
			} else {
				embedBuilder.withTitle("Untitled");
			}

			String trackUrl = nowPlaying.getInfo().uri;
			if (!StringUtils.isBlank(trackUrl)) {
				embedBuilder.withUrl(trackUrl);
			}

			StringBuffer descSb = new StringBuffer();

			StringBuffer timeSb = new StringBuffer();
			timeSb.append("``[").append(TimeFormatter.toFriendlyString(nowPlaying.getPosition())).append("/")
					.append(TimeFormatter.toFriendlyString(nowPlaying.getDuration())).append("]``");
			descSb.append(timeSb);

			ExtraTrackInfo extraTrackInfo = nowPlaying.getUserData(ExtraTrackInfo.class);
			IUser addedByUser = client.getUserByID(extraTrackInfo.getAddedByUserId());
			descSb.append(" added by **").append(addedByUser.getNicknameForGuild(guild)).append("**");

			embedBuilder.withDesc(descSb.toString());

		} else {
			embedBuilder.withTitle("-- Nothing --");
		}
	}

	private void appendUpNext(
			int page,
			EmbedBuilder embedBuilder,
			AudioSessionStatus sessionStatus,
			IDiscordClient client,
			IGuild guild) {

		List<AudioTrack> upcomingTracks = sessionStatus.getUpcomingTracks();

		StringBuffer sb = new StringBuffer();

		if (upcomingTracks.isEmpty()) {
			sb.append("-- Empty --");
		} else {
			List<String> trackStrings = new ArrayList<>();
			for (int i = page * TRACKS_PER_PAGE; i < Math.min(upcomingTracks.size(),
					(page * TRACKS_PER_PAGE) + TRACKS_PER_PAGE); i++) {
				AudioTrack track = upcomingTracks.get(i);
				ExtraTrackInfo extraTrackInfo = track.getUserData(ExtraTrackInfo.class);
				IUser addedByUser = client.getUserByID(extraTrackInfo.getAddedByUserId());

				String trackString = new StringBuffer()
						.append("``[").append(i + 1).append("]`` **")
						.append(track.getInfo().title)
						.append("** (").append(TimeFormatter.toFriendlyString(track.getDuration()))
						.append(") added by **").append(addedByUser.getNicknameForGuild(guild))
						.append("**")
						.toString();
				trackStrings.add(trackString);
			}

			sb.append(StringUtils.join(trackStrings, "\n"));

			int totalPagesCount = (upcomingTracks.isEmpty() ? 0 : (upcomingTracks.size() - 1) / TRACKS_PER_PAGE) + 1;

			if (totalPagesCount > 1) {
				sb.append("\n\nPage **").append(page + 1).append("** of **").append(totalPagesCount)
						.append("**. Total tracks: **").append(upcomingTracks.size()).append("**");
			}
		}

		embedBuilder.appendField("Next up:", sb.toString(), false);
	}
}
