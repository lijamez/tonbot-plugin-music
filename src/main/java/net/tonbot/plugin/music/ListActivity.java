package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.tonbot.common.Activity;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

class ListActivity implements Activity {

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "list"))
			.description("Displays the upcoming tracks.")
			.build();

	private final DiscordAudioPlayerManager discordAudioPlayerManager;
	private final BotUtils botUtils;

	@Inject
	public ListActivity(DiscordAudioPlayerManager discordAudioPlayerManager, BotUtils botUtils) {
		this.discordAudioPlayerManager = Preconditions.checkNotNull(discordAudioPlayerManager,
				"discordAudioPlayerManager must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");

	}

	@Override
	public ActivityDescriptor getDescriptor() {
		return ACTIVITY_DESCRIPTOR;
	}

	@Override
	public void enact(MessageReceivedEvent event, String args) {

		Long defaultChannelId = discordAudioPlayerManager.getDefaultChannelId(event.getGuild()).orElse(null);
		if (defaultChannelId == null || defaultChannelId != event.getChannel().getLongID()) {
			return;
		}

		AudioSessionStatus sessionStatus = discordAudioPlayerManager.getSessionStatus(event.getGuild()).orElse(null);

		if (sessionStatus == null) {
			return;
		}

		List<Track> upcomingTracks = sessionStatus.getUpcomingTracks();

		EmbedBuilder embedBuilder = new EmbedBuilder();

		// Now Playing
		Track nowPlaying = sessionStatus.getNowPlaying().orElse(null);
		String nowPlayingStr;
		if (nowPlaying == null) {
			nowPlayingStr = "Nothing.";
		} else {
			AudioTrack currentAudioTrack = nowPlaying.getAudioTrack();
			StringBuffer sb = new StringBuffer();
			sb.append(currentAudioTrack.getInfo().title)
					.append(" ``[").append(friendlyTime(currentAudioTrack.getPosition())).append("/")
					.append(friendlyTime(currentAudioTrack.getDuration())).append("]``");

			nowPlayingStr = sb.toString();
		}

		embedBuilder.appendField("Now playing:", nowPlayingStr, false);

		// Upcoming tracks
		StringBuffer sb = new StringBuffer();

		if (upcomingTracks.isEmpty()) {
			sb.append("No tracks.");
		} else {
			List<String> trackStrings = new ArrayList<>();
			for (int i = 0; i < upcomingTracks.size(); i++) {
				Track track = upcomingTracks.get(i);
				IUser addedByUser = event.getClient().getUserByID(track.getAddedByUserId());

				String trackString = new StringBuffer()
						.append("``[").append(i).append("]`` **")
						.append(track.getAudioTrack().getInfo().title)
						.append("** ``[").append(friendlyTime(track.getAudioTrack().getDuration()))
						.append("]`` added by **").append(addedByUser.getNicknameForGuild(event.getGuild()))
						.append("**")
						.toString();
				trackStrings.add(trackString);
			}

			sb.append(StringUtils.join(trackStrings, "\n"));
		}

		embedBuilder.appendField("Next up:", sb.toString(), false);

		embedBuilder.appendField("Play mode:", sessionStatus.getPlayMode().toString(), false);

		botUtils.sendEmbed(event.getChannel(), embedBuilder.build());
	}

	private String friendlyTime(long millis) {
		if (TimeUnit.MILLISECONDS.toHours(millis) > 0) {
			return String.format("%02d:%02d:%02d",
					TimeUnit.MILLISECONDS.toHours(millis),
					TimeUnit.MILLISECONDS.toMinutes(millis)
							- TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
					TimeUnit.MILLISECONDS.toSeconds(millis)
							- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
		} else {
			return String.format("%02d:%02d",
					TimeUnit.MILLISECONDS.toMinutes(millis)
							- TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
					TimeUnit.MILLISECONDS.toSeconds(millis)
							- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
		}
	}
}
