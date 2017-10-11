package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import lombok.Data;
import net.tonbot.common.ActivityDescriptor;
import net.tonbot.common.BotUtils;
import net.tonbot.common.TonbotBusinessException;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;

class SkipActivity extends AudioSessionActivity {

	private static final Logger LOG = LoggerFactory.getLogger(SkipActivity.class);

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route("music skip")
			.parameters(ImmutableList.of("track numbers/mine/all"))
			.description("Skips the currently playing track or several tracks in the Up Next queue.")
			.usageDescription(
					"This command skips the current track.\n\n"
							+ "To skip tracks in the Up Next queue, specify a track number or a comma separated list of track numbers.\n"
							+ "``${absoluteReferencedRoute} 3``\n"
							+ "``${absoluteReferencedRoute} 1, 2, 3, 4, 7``\n\n"
							+ "You can also skip a range of tracks.\n"
							+ "``${absoluteReferencedRoute} 1-7``\n"
							+ "``${absoluteReferencedRoute} 1, 2-4, 7``\n\n"
							+ "To skip the tracks that you added to the Up Next queue:\n"
							+ "``${absoluteReferencedRoute} mine``"
							+ "To skip all tracks in the Up Next queue:\n"
							+ "``${absoluteReferencedRoute} all``")
			.build();

	private static final Pattern RANGE_PATTERN = Pattern.compile("^([0-9]+)-([0-9]+)$");
	private static final String ALL_KEYWORD = "ALL";
	private static final String MINE_KEYWORD = "MINE";

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

		List<AudioTrack> skippedTracks;
		if (StringUtils.isBlank(args)) {
			// Skip the current track.
			skippedTracks = new ArrayList<>();
			Optional<AudioTrack> skippedTrack = audioSession.skip();
			if (skippedTrack.isPresent()) {
				skippedTracks.add(skippedTrack.get());
			}
		} else if (StringUtils.equalsIgnoreCase(args, ALL_KEYWORD)) {
			skippedTracks = audioSession.skip(at -> true);
		} else if (StringUtils.equalsIgnoreCase(args, MINE_KEYWORD)) {
			long userId = event.getAuthor().getLongID();
			skippedTracks = audioSession.skip(at -> {
				ExtraTrackInfo eti = (ExtraTrackInfo) at.getUserData();
				return eti != null && eti.getAddedByUserId() == userId;
			});
		} else {
			skippedTracks = removeTracksByIndices(event, args, audioSession);
		}

		if (skippedTracks.size() == 1) {
			AudioTrack skippedTrack = skippedTracks.get(0);
			botUtils.sendMessage(event.getChannel(), "Skipped **" + skippedTrack.getInfo().title + "**");
		} else {
			botUtils.sendMessage(event.getChannel(), "Skipped **" + skippedTracks.size() + "** tracks.");
		}
	}

	private List<AudioTrack> removeTracksByIndices(MessageReceivedEvent event, String args, AudioSession audioSession) {
		List<AudioTrack> upcomingTracks = audioSession.getStatus().getUpcomingTracks();

		List<Integer> skipIndexes = parseSkipIndexes(args, upcomingTracks.size());

		LOG.debug("User {} entered args '{}' to skip the following indexes: {}", event.getAuthor().getName(), args,
				skipIndexes);

		List<AudioTrack> removeTracks = skipIndexes.stream()
				.map(i -> upcomingTracks.get(i))
				.collect(Collectors.toList());

		if (removeTracks.isEmpty()) {
			throw new TonbotBusinessException("You didn't specify any valid track numbers to skip.");
		}

		return audioSession.skip(at -> removeTracks.contains(at));
	}

	/**
	 * Generates a list of track indexes to skip. Will be returned in DESCENDING
	 * order. The order is important because tracks should be skipped in this order
	 * to prevent index shifting.
	 * 
	 * @param args
	 *            The range as a user supplied string.
	 * @param maxIndex
	 *            A zero-indexed, exclusive upper bound on the indexes to return.
	 * @return A list of zero-indexed track indexes to skip, sorted in descending
	 *         order. Guaranteed to have no duplicates.
	 * @throws TonbotBusinessException
	 *             If ranges could not be cleanly parsed.
	 */
	private List<Integer> parseSkipIndexes(String args, int maxIndex) {

		List<Integer> skipIndexes = Arrays.asList(StringUtils.split(args, ',')).stream()
				.map(String::trim)
				.map(rangeStr -> {
					// Interprets the 1-indexed input and returns a stream of 1-indexed Ranges.

					// Maybe it's just a single number
					Range range;
					try {
						int skipIndex = Integer.parseInt(rangeStr);
						range = new Range(skipIndex, skipIndex);
					} catch (IllegalArgumentException e) {
						// Ok, fine it's not a single number.
						range = null;
					}

					if (range == null) {
						// Maybe its actually a range.
						Matcher matcher = RANGE_PATTERN.matcher(rangeStr);
						if (matcher.find()) {
							range = new Range(Integer.parseInt(matcher.group(1)),
									Integer.parseInt(matcher.group(2)));
						}
					}

					if (range == null) {
						throw new TonbotBusinessException("Couldn't parse the ranges.");
					}

					return range;

				})
				.flatMap(range -> IntStream.range(range.getFrom(), Math.min(range.getTo() + 1, maxIndex + 1))
						.mapToObj(i -> i))
				.map(i -> i - 1)
				.distinct()
				.sorted(new Comparator<Integer>() {
					// Sorts the indexes in descending order. This is necessary to prevent incorrect
					// track skips due to index shifting.
					@Override
					public int compare(Integer a, Integer b) {
						return b - a;
					}
				})
				.collect(Collectors.toList());

		return skipIndexes;
	}

	@Data
	private static class Range {

		private final int from;
		private final int to;

		public Range(int a, int b) {
			if (a < b) {
				this.from = a;
				this.to = b;
			} else {
				this.from = b;
				this.to = a;
			}
		}
	}
}
