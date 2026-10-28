package dev.beli.skylyrics.lyrics;

import java.util.List;

/**
 * Lyrics for one song. Synced lyrics have real timestamps; plain lyrics are spread evenly
 * over the song so they still scroll along.
 */
public final class Lyrics {
	public static final Lyrics NONE = new Lyrics(List.of(), false, false);
	public static final Lyrics INSTRUMENTAL = new Lyrics(List.of(), false, true);

	private final List<LyricLine> lines;
	private final boolean synced;
	private final boolean instrumental;

	public Lyrics(List<LyricLine> lines, boolean synced, boolean instrumental) {
		this.lines = List.copyOf(lines);
		this.synced = synced;
		this.instrumental = instrumental;
	}

	public List<LyricLine> lines() {
		return lines;
	}

	public boolean synced() {
		return synced;
	}

	public boolean instrumental() {
		return instrumental;
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	/** Index of the line being sung at {@code timeMs}, or -1 before the first line. */
	public int indexAt(long timeMs) {
		int lo = 0;
		int hi = lines.size() - 1;
		int found = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= timeMs) {
				found = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return found;
	}

	public LyricLine get(int index) {
		return index >= 0 && index < lines.size() ? lines.get(index) : null;
	}

	// A line lasts till the next one starts, but the last line gets this long, and none longer than LONGEST_MS
	static final long LAST_MS = 5_000;
	static final long LONGEST_MS = 12_000;
	// Even slow singing gets through a line quicker than this, so the words of a line sung just before a long
	// instrumental part come out as they're sung instead of trickling out across the whole gap
	static final long SLOWEST_MS_PER_CHAR = 220;
	static final long SLOWEST_EXTRA_MS = 1_000;

	/** When line {@code index} is over: the next line's start, within a last-line and longest-line limit. */
	public long lineEnd(int index) {
		long start = get(index).timeMs();
		return Math.min(endOf(index, start + LAST_MS), start + LONGEST_MS);
	}

	/**
	 * When the singing of line {@code index} is likely done, for spreading its words over its time: its end, or
	 * sooner when that's more time than even slow singing would take for its length.
	 */
	public long sungEnd(int index) {
		LyricLine line = get(index);
		long start = line.timeMs();
		long slowest = SLOWEST_EXTRA_MS + SLOWEST_MS_PER_CHAR * line.text().codePointCount(0, line.text().length());
		return Math.min(lineEnd(index), start + slowest);
	}

	/** When the line after {@code index} starts, or {@code fallback} if it's the last line. */
	public long endOf(int index, long fallback) {
		LyricLine next = get(index + 1);
		return next != null ? next.timeMs() : fallback;
	}
}
