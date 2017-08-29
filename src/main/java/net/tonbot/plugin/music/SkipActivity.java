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

	private static final ActivityDescriptor ACTIVITY_DESCRIPTOR = ActivityDescriptor.builder()
			.route(ImmutableList.of("music", "skip"))
			.parameters(ImmutableList.of("track numbers"))
			.description(
					"Skips tracks with comma-separated track numbers or ranges. If none are provided, then the current track is skipped.")
			.build();

	private static final Pattern RANGE_PATTERN = Pattern.compile("^([0-9]+)-([0-9]+)$");

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
		// TODO: Add support for skipping ranges.

		List<Integer> skipIndexes = parseSkipIndexes(args);

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
	 * Generates a list of track indexes to skip. Will be returned in DESCENDING
	 * order. The order is important because tracks should be skipped in this order
	 * to prevent index shifting.
	 * 
	 * @param args
	 *            The range as a user supplied string.
	 * @return A list of zero-indexed track indexes to skip, sorted in descending
	 *         order. Guaranteed to have no duplicates.
	 * @throws TonbotBusinessException
	 *             If ranges could not be cleanly parsed.
	 */
	private List<Integer> parseSkipIndexes(String args) {
		List<Integer> skipIndexes;

		if (!StringUtils.isBlank(args)) {
			skipIndexes = Arrays.asList(StringUtils.split(args, ',')).stream()
					.map(String::trim)
					.map(rangeStr -> {
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
					.flatMap(range -> IntStream.range(range.getFrom(), range.getTo() + 1).mapToObj(i -> i))
					.map(i -> i - 1)
					.distinct()
					.sorted(new Comparator<Integer>() {

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
