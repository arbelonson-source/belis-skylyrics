package dev.beli.skylyrics.dev;

/** The arguments and timing of the test hook's {@code burst} and {@code burstat}, kept free of Minecraft so they can be tested. */
final class BurstPlan {
	static final int MAX_COUNT = 60;
	/** How long {@code burstat} waits for the lyrics to move to the next line. */
	static final long WAIT_NS = 10_000_000_000L;
	/** A line change shows as the clock dropping to under this; a first line "settling" lands above it. */
	private static final float FRESH_SECONDS = 0.25f;

	private BurstPlan() {
	}

	record Burst(String name, int count, int everyMs) {
	}

	/** "name count everyMs": count 1 to 60, everyMs 0 or more (0 = every frame). Throws IllegalArgumentException otherwise. */
	static Burst parse(String arg) {
		String[] words = arg.strip().split("\\s+");
		if (words.length != 3) {
			throw new IllegalArgumentException("expected: <name> <count> <everyMs>");
		}
		String name = words[0];
		if (name.contains("/") || name.contains("\\") || name.contains("..")) {
			throw new IllegalArgumentException("bad name " + name);
		}
		int count;
		int everyMs;
		try {
			count = Integer.parseInt(words[1]);
			everyMs = Integer.parseInt(words[2]);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("count and everyMs must be whole numbers", e);
		}
		if (count < 1 || count > MAX_COUNT) {
			throw new IllegalArgumentException("count must be 1 to " + MAX_COUNT);
		}
		if (everyMs < 0) {
			throw new IllegalArgumentException("everyMs must be 0 or more");
		}
		return new Burst(name, count, everyMs);
	}

	/**
	 * Whether the next grab (index {@code taken}) is due on this rendered frame. Times are on the burst's own start
	 * clock, so a slow frame does not push the later ones back; at most one grab happens per frame.
	 */
	static boolean due(int taken, long elapsedNs, int everyMs) {
		return elapsedNs >= (long) taken * everyMs * 1_000_000L;
	}

	static String fileName(String name, int index) {
		return String.format("%s_%02d", name, index);
	}

	/** True when the lyrics clock (seconds since the line changed) fell to a fresh value since the last frame. */
	static boolean lineChanged(float before, float now) {
		return now < before && now < FRESH_SECONDS;
	}

	static boolean gaveUp(long waitedNs) {
		return waitedNs >= WAIT_NS;
	}
}
