package net.tonbot.plugin.music;

class ProgressBarRenderer {

	// Good enough for mobile devices.
	private static final int PROGRESS_BAR_LENGTH = 26;
	private static final char SEGMENT = '•';

	public static String render(long currentValue, long totalValue) {

		// Guarantees that currentValue <= totalValue for rendering purposes.
		currentValue = currentValue > totalValue ? totalValue : currentValue;

		// Subtracted by 2 because to take the square brackets into account.
		int totalSpaces = PROGRESS_BAR_LENGTH - 2;
		int numDots = (int) (totalSpaces * ((double) currentValue / totalValue));
		int numSpaces = totalSpaces - numDots;

		StringBuffer sb = new StringBuffer();
		sb.append("``[");

		for (int i = 0; i < numDots; i++) {
			sb.append(SEGMENT);
		}

		for (int i = 0; i < numSpaces; i++) {
			sb.append(" ");
		}

		sb.append("]``");

		return sb.toString();
	}
}
