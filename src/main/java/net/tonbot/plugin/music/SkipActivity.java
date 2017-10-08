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
			.routeAliases(ImmutableList.of(
					"skip",
					"s"))
			.parameters(ImmutableList.of("track numbers"))
			.description("Skips the currently playing track or several tracks in the Up Next queue.")
			.usageDescription(
					"This command skips the current track.\n\n"
							+ "To skip tracks in the Up Next queue, specify a track number or a comma separated list of track numbers.\n"
							+ "``${absoluteReferencedRoute} 3``\n"
							+ "``${absoluteReferencedRoute} 1, 2, 3, 4, 7``\n\n"
							+ "You can also skip a range of tracks.\n"
							+ "``${absoluteReferencedRoute} 1-7``\n"
							+ "``${absoluteReferencedRoute} 1, 2-4, 7``\n\n"
							+ "Or you can just skip everything in the Up Next queue with:\n"
							+ "``${absoluteReferencedRoute} all``")
			.build();

	private static final Pattern RANGE_PATTERN = Pattern.compile("^([0-9]+)-([0-9]+)$");
	private static final String ALL_KEYWORD = "ALL";

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

		List<Integer> skipIndexes = getSkipIndexes(args, audioSession);

		LOG.debug("User {} entered args '{}' to skip the following indexes: {}", event.getAuthor().getName(), args,
				skipIndexes);

		if (skipIndexes.isEmpty()) {
			// Skip the current track.
			Optional<AudioTrack> optSkippedTrack = audioSession.skip();
			optSkippedTrack.ifPresent(skippedTrack -> {
				botUtils.sendMessage(event.getChannel(), "Skipped **" + skippedTrack.getInfo().title + "**");
			});
		} else {
			// We need to ensure that the largest skip index is within range.
			// We can take advantage of the fact that skipIndexes is sorted.
			if (skipIndexes.get(0) >= audioSession.getStatus().getUpcomingTracks().size()
					|| skipIndexes.get(skipIndexes.size() - 1) < 0) {
				String message = skipIndexes.size() == 1 ? "That's an invalid track number."
						: "Range includes an invalid track number.";

				throw new TonbotBusinessException(message);
			}

			// Skip the specified track(s).
			if (skipIndexes.size() == 1) {
				AudioTrack skippedTrack = audioSession.skip(skipIndexes.get(0));
				botUtils.sendMessage(event.getChannel(), "Skipped **" + skippedTrack.getInfo().title + "**");
			} else {
				audioSession.skipAll(skipIndexes);

				botUtils.sendMessage(event.getChannel(), "Skipped **" + skipIndexes.size() + "** tracks.");
			}
		}
	}

	/**
	 * Parses the user-supplied arguments to get a list of track numbers to skip.
	 * 
	 * @param args The user-supplied arguments.
	 * @param audioSession {@link AudioSession}
	 * @return A list of zero-indexed indexes in the {@link AudioSession}'s upcoming tracks to skip.
	 */
	private List<Integer> getSkipIndexes(String args, AudioSession audioSession) {
		List<Integer> skipIndexes;

		List<AudioTrack> upcomingTracks = audioSession.getStatus().getUpcomingTracks();

		if (StringUtils.equalsIgnoreCase(args, ALL_KEYWORD)) {
			skipIndexes = IntStream.range(0, upcomingTracks.size())
					.mapToObj(i -> i)
					.collect(Collectors.toList());
		} else {
			skipIndexes = parseSkipIndexes(args, upcomingTracks.size());
		}

		return skipIndexes;
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
		List<Integer> skipIndexes;

		if (!StringUtils.isBlank(args)) {
			skipIndexes = Arrays.asList(StringUtils.split(args, ',')).stream()
					.map(String::trim)
					.map(rangeStr -> {
						// Interprets the 1-indexed input and returns a stream of 1-indexed Ranges.

						// Maybe it's just a single number
						Range range;
						try {
							int skipIndex = Integer.parseInt(args);
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
		} else {
			skipIndexes = new ArrayList<>();
		}

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
