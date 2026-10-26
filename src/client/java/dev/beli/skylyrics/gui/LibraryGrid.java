package dev.beli.skylyrics.gui;

import java.util.Locale;

import dev.beli.skylyrics.media.LibraryItem;
import dev.beli.skylyrics.media.Track;

/** The sums behind the song library's grid of covers, kept apart from drawing so they can be tested. */
final class LibraryGrid {
	static final int COVER = 56;
	static final int GAP = 8;

	private LibraryGrid() {
	}

	/** How many covers fit side by side in {@code width}, always at least one. */
	static int columns(int width) {
		return Math.max(1, (width + GAP) / (COVER + GAP));
	}

	/** How wide each cover gets once the row is stretched to fill {@code width}. */
	static int cellWidth(int width) {
		int cols = columns(width);
		return Math.max(1, (width - (cols - 1) * GAP) / cols);
	}

	/** Where the selection lands after moving by {@code step}, staying on the grid. */
	static int move(int selected, int step, int count) {
		if (count <= 0) {
			return -1;
		}
		if (selected < 0) {
			return 0;
		}
		int next = selected + step;
		return next < 0 || next >= count ? selected : next;
	}

	/** Eases 0..1 to 0..1, overshooting a little near the end, so covers pop in. */
	static float popIn(float t) {
		if (t <= 0) {
			return 0;
		}
		if (t >= 1) {
			return 1;
		}
		float c1 = 1.70158f;
		float u = t - 1;
		return 1 + (c1 + 1) * u * u * u + c1 * u * u;
	}

	/** A steady colour for a cover that has no picture yet, picked from the album or playlist name. */
	static float hue(String name) {
		return (name.hashCode() & 0xFFFF) / 65536f;
	}

	/**
	 * True if this is the song playing now. Players list several artists differently
	 * ("A, B", "A & B" or just "A"), so one artist in common is enough.
	 */
	static boolean isPlaying(LibraryItem item, Track track) {
		if (track.isNone() || !item.title().strip().equalsIgnoreCase(track.title().strip())) {
			return false;
		}
		if (track.artist().isBlank()) {
			return true;
		}
		String artists = item.artists().toLowerCase(Locale.ROOT);
		for (String name : track.artist().toLowerCase(Locale.ROOT).split(",|&|\\bfeat\\.")) {
			if (!name.isBlank() && artists.contains(name.strip())) {
				return true;
			}
		}
		return false;
	}

	/** Height, 0 to 1, of bar {@code i} of the little moving bars on the song playing now. */
	static float bar(int i, float clock) {
		return 0.3f + 0.7f * (float) Math.abs(Math.sin(clock * (4.3f + i * 1.9f) + i * 1.1f));
	}
}
