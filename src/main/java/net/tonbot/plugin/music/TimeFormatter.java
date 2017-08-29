package net.tonbot.plugin.music;

import java.util.concurrent.TimeUnit;

import com.google.api.client.util.Preconditions;

class TimeFormatter {

	private TimeFormatter() {
	}

	/**
	 * Formats the time in DD:HH:MM:SS format.
	 * 
	 * @param time
	 *            The time. Must be non-negative.
	 * @param timeUnit
	 *            The time's unit. Non-null.
	 * @return A user friendly string for the given time.
	 */
	public static String toFriendlyString(long time, TimeUnit timeUnit) {
		Preconditions.checkArgument(time >= 0, "time must be non-negative.");
		Preconditions.checkNotNull(timeUnit, "timeUnit must be non-null.");

		StringBuilder sb = new StringBuilder();

		long days = timeUnit.toDays(time);
		if (days > 0) {
			sb.append(days).append(":");
		}

		long hours = timeUnit.toHours(time) - TimeUnit.DAYS.toHours(timeUnit.toDays(time));
		if (sb.length() > 0 || hours > 0) {
			sb.append(String.format("%02d", hours)).append(":");
		}

		long minutes = timeUnit.toMinutes(time) - TimeUnit.HOURS.toMinutes(timeUnit.toHours(time));
		long seconds = timeUnit.toSeconds(time) - TimeUnit.MINUTES.toSeconds(timeUnit.toMinutes(time));
		sb.append(String.format("%02d:%02d", minutes, seconds));

		return sb.toString();
	}
}
