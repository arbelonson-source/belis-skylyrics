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

	/** When the line after {@code index} starts, or {@code fallback} if it's the last line. */
	public long endOf(int index, long fallback) {
		LyricLine next = get(index + 1);
		return next != null ? next.timeMs() : fallback;
	}
}
