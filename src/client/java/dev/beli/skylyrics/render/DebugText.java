package dev.beli.skylyrics.render;

import java.util.List;
import java.util.function.ToIntFunction;

import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.BarPosition;

/**
 * The debug screen's (F3) text as the game drew it last: one box per line (left, top, right, bottom, in GUI units), for
 * its left and its right column, so a bar at the bottom can tell whether it would sit on that text. How much F3 shows
 * depends on the game version and the mods (on 1.21.1 its left column nearly fills a GUI scale 3 screen, on 1.21.11 it
 * stops well above a bottom bar at GUI scale 2), so it is measured as the game draws each column, not guessed.
 */
public final class DebugText {
	/** A column not drawn for this long is taken as gone (F3 closed, or a mod draws F3 its own way). */
	private static final long STALE_NS = 250_000_000L;
	/** The game puts each line this far below the one before, its first 2 down and 2 in from its side of the screen. */
	static final int LINE_H = 9, INSET = 2;

	private static volatile float[][] left = new float[0][], right = new float[0][];
	private static volatile long leftAt, rightAt;

	private DebugText() {
	}

	/**
	 * Called as the game draws one of F3's columns (DebugScreenOverlayMixin), with its lines, the screen's width and the
	 * game's font. It only measures while a bar at the bottom could need it.
	 */
	public static void drawn(List<String> lines, boolean leftColumn, int guiWidth, ToIntFunction<String> width) {
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		if (!cfg.enabled || !cfg.nowPlayingHud || cfg.barPosition != BarPosition.BOTTOM) {
			return;
		}
		int[] widths = new int[lines.size()];
		for (int i = 0; i < widths.length; i++) {
			String line = lines.get(i);
			widths[i] = line == null || line.isEmpty() ? -1 : width.applyAsInt(line);
		}
		float[][] boxes = boxes(widths, leftColumn, guiWidth);
		long now = System.nanoTime();
		if (leftColumn) {
			left = boxes;
			leftAt = now;
		} else {
			right = boxes;
			rightAt = now;
		}
	}

	/**
	 * The boxes of one column's lines as the game draws them: line {@code i} at 2 + 9i down, 2 in from the left edge (or
	 * right-aligned 2 in from the right one), on a background one unit wider than its text all round and 9 tall. A line
	 * of width -1 is empty: the game skips it, but it still takes its row.
	 */
	static float[][] boxes(int[] widths, boolean leftColumn, int guiWidth) {
		int count = 0;
		for (int w : widths) {
			count += w >= 0 ? 1 : 0;
		}
		float[][] out = new float[count][];
		int k = 0;
		for (int i = 0; i < widths.length; i++) {
			int w = widths[i];
			if (w < 0) {
				continue;
			}
			int x = leftColumn ? INSET : guiWidth - INSET - w;
			int y = INSET + LINE_H * i;
			out[k++] = new float[] {x - 1, y - 1, x + w + 1, y + LINE_H - 1};
		}
		return out;
	}

	/** Both columns' line boxes, or null when the game hasn't drawn F3's text in the last moment. */
	static float @Nullable [][] recent(long now) {
		boolean l = now - leftAt <= STALE_NS, r = now - rightAt <= STALE_NS;
		if (!l && !r) {
			return null;
		}
		float[][] a = l ? left : new float[0][], b = r ? right : new float[0][];
		float[][] both = new float[a.length + b.length][];
		System.arraycopy(a, 0, both, 0, a.length);
		System.arraycopy(b, 0, both, a.length, b.length);
		return both;
	}

	/** Whether any of the line boxes overlaps the box (left, top, right, bottom). */
	static boolean covers(float[] box, float[][] lines) {
		for (float[] line : lines) {
			if (TopBarLayout.overlaps(line, box[0], box[1], box[2] - box[0], box[3] - box[1])) {
				return true;
			}
		}
		return false;
	}
}
