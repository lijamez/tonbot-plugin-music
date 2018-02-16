package net.tonbot.plugin.music;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

class ListActivity extends AudioSessionActivity<ListRequest> {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder().route("music list")
			.parameters(ImmutableList.of("[page number]")).description("Displays the upcoming tracks.")
			.usageDescription(
					"Displays the currently playing track and upcoming tracks. If there are too many upcoming tracks, then you need to specify a page number to see more.")
			.build();
	private static final String NOW_PLAYING_HEADER = "Now Playing";
	private static final String UNTITLED_TRACK_NAME = "Untitled";
	private static final int TRACKS_PER_PAGE = 10;
	private static final String STREAM_TIME = "STREAM";

	private final BotUtils botUtils;
	private final Color color;

	@Inject
	public ListActivity(GuildMusicManager guildMusicManager, BotUtils botUtils, Color color) {
		super(guildMusicManager);
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.color = Preconditions.checkNotNull(color, "color must be non-null.");
	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public Class<?> getRequestType() {
		return ListRequest.class;
	}

	@Override
	protected void enactWithSession(MessageReceivedEvent event, ListRequest request, AudioSession audioSession) {
		IDiscordClient client = event.getClient();
		IGuild guild = event.getGuild();
		AudioSessionStatus sessionStatus = audioSession.getStatus();

		// Page numbers should always start at 1, but internally, page 1 is page 0.
		int requestedPage; // The zero-indexed requested page.
		if (request.getPageNumber() != null) {
			requestedPage = request.getPageNumber() - 1;

			int numUpcomingTracks = sessionStatus.getUpcomingTracks().size();
			if (requestedPage < 0
					|| (numUpcomingTracks != 0 && requestedPage > (numUpcomingTracks - 1) / TRACKS_PER_PAGE)) {
				throw new TonbotBusinessException("Page doesn't exist.");
			}
		} else {
			requestedPage = 0;
		}

		EmbedBuilder embedBuilder = new EmbedBuilder();
		embedBuilder.withColor(color);

		if (requestedPage == 0) {
			appendNowPlaying(embedBuilder, sessionStatus, client, guild);
		}

		appendUpNext(requestedPage, embedBuilder, sessionStatus, client, guild);

		botUtils.sendEmbed(event.getChannel(), embedBuilder.build());
	}

	private void appendNowPlaying(EmbedBuilder embedBuilder, AudioSessionStatus sessionStatus, IDiscordClient client,
			IGuild guild) {

		AudioTrack nowPlaying = sessionStatus.getNowPlaying().orElse(null);

		embedBuilder.withAuthorName(NOW_PLAYING_HEADER);

		if (nowPlaying != null) {
			String title = nowPlaying.getInfo().title;
			if (!StringUtils.isBlank(title)) {
				embedBuilder.withTitle(title);
			} else {
				embedBuilder.withTitle(UNTITLED_TRACK_NAME);
			}

			String trackUrl = nowPlaying.getInfo().uri;
			if (!StringUtils.isBlank(trackUrl)) {
				embedBuilder.withUrl(trackUrl);
			}

			StringBuffer descSb = new StringBuffer();

			StringBuffer timeSb = new StringBuffer();
			timeSb.append("``[");

			if (nowPlaying.getInfo().isStream) {
				timeSb.append(STREAM_TIME);
			} else {
				timeSb.append(TimeFormatter.toFriendlyString(nowPlaying.getPosition(), TimeUnit.MILLISECONDS))
						.append("/")
						.append(TimeFormatter.toFriendlyString(nowPlaying.getDuration(), TimeUnit.MILLISECONDS));
			}

			timeSb.append("]``");

			descSb.append(timeSb);

			ExtraTrackInfo extraTrackInfo = nowPlaying.getUserData(ExtraTrackInfo.class);
			IUser addedByUser = client.fetchUser(extraTrackInfo.getAddedByUserId());
			descSb.append(" added by **").append(addedByUser.getDisplayName(guild)).append("**");

			embedBuilder.withDesc(descSb.toString());

		} else {
			embedBuilder.withTitle("-- Nothing --");
		}
	}

	private void appendUpNext(int page, EmbedBuilder embedBuilder, AudioSessionStatus sessionStatus,
			IDiscordClient client, IGuild guild) {

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
				IUser addedByUser = client.fetchUser(extraTrackInfo.getAddedByUserId());

				StringBuffer trackSb = new StringBuffer().append("``[").append(i + 1).append("]`` **")
						.append(track.getInfo().title).append("**");

				if (!track.getInfo().isStream) {
					trackSb.append(" (")
							.append(TimeFormatter.toFriendlyString(track.getDuration(), TimeUnit.MILLISECONDS))
							.append(")");
				}

				trackSb.append(" added by **").append(addedByUser.getDisplayName(guild)).append("**");

				trackStrings.add(trackSb.toString());
			}

			sb.append(StringUtils.join(trackStrings, "\n"));

			int totalPagesCount = (upcomingTracks.isEmpty() ? 0 : (upcomingTracks.size() - 1) / TRACKS_PER_PAGE) + 1;

			if (totalPagesCount > 1) {
				sb.append("\n\nPage **").append(page + 1).append("** of **").append(totalPagesCount).append("**.");
			}

			List<AudioTrack> nonStreamTracks = upcomingTracks.stream().filter(track -> !track.getInfo().isStream)
					.collect(Collectors.toList());

			long totalNonStreamDuration = nonStreamTracks.stream().map(track -> track.getDuration()).reduce(0L,
					(i, j) -> i + j);

			long streamCount = upcomingTracks.stream().filter(track -> track.getInfo().isStream).count();

			sb.append("\n\nThe queue contains ");

			List<String> fragments = new ArrayList<>();
			if (nonStreamTracks.size() > 0) {
				fragments.add(String.format("**%d** track(s), which are **%s** long", nonStreamTracks.size(),
						TimeFormatter.toFriendlyString(totalNonStreamDuration, TimeUnit.MILLISECONDS)));
			}

			if (streamCount > 0) {
				fragments.add(String.format("**%d** streams", streamCount));
			}

			sb.append(StringUtils.join(fragments, " and ")).append(".");

		}

		embedBuilder.appendField("Next Up", sb.toString(), false);
	}
}
