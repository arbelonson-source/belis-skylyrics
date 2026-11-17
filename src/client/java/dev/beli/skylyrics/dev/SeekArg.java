package dev.beli.skylyrics.dev;

/** The argument of the test hook's {@code seek}: seconds, absolute, or relative with a leading + or -. */
final class SeekArg {
	private SeekArg() {}

	/** The target position in ms for "12.5", "-3" or "+3" when the song is at {@code nowMs}, kept inside 0..{@code lengthMs} - 1 (the demo wraps at its length, so the very end would read as 0). */
	static long targetMs(String arg, long nowMs, long lengthMs) {
		boolean relative = arg.startsWith("+") || arg.startsWith("-");
		double seconds;
		try {
			seconds = Double.parseDouble(arg);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("seek needs seconds like 12.5, -3 or +3: '" + arg + "'", e);
		}
		if (!Double.isFinite(seconds)) {
			throw new IllegalArgumentException("seek needs seconds like 12.5, -3 or +3: '" + arg + "'");
		}
		long ms = Math.round(seconds * 1000);
		long target = relative ? nowMs + ms : ms;
		return Math.max(0, Math.min(target, lengthMs - 1));
	}
}
