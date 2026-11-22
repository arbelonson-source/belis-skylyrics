package dev.beli.skylyrics.gui;

import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Keyboard focus decisions of the settings screen, kept free of the screen so they can be tested. */
final class Focus {
	private Focus() {
	}

	/**
	 * The next stop that is available, going {@code dir} (+1 forward, -1 back) from {@code current} and wrapping round.
	 * From nothing ({@code current} below 0) it lands on the first stop going forward or the last going back. Returns -1
	 * when no stop is available; a lone available stop is its own next.
	 */
	static int next(boolean[] available, int current, int dir) {
		int n = available.length;
		if (n == 0) {
			return -1;
		}
		int from = current >= 0 ? current : dir > 0 ? -1 : n;
		for (int step = 1; step <= n; step++) {
			int i = Math.floorMod(from + dir * step, n);
			if (available[i]) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The stop Tab, Shift+Tab, Up or Down takes the keyboard to from {@code current} (-1 for nothing), skipping stops that
	 * are not available and wrapping round. From nothing, going forward starts after the first {@code lead} stops (the
	 * settings put the Lyrics switch and Preview before the rows), going back at the last stop.
	 */
	static int step(boolean[] available, int current, int dir, int lead) {
		return next(available, current < 0 && dir > 0 ? lead - 1 : current, dir);
	}

	/**
	 * The stop the keyboard starts from as it leaves the search box. While the search results show, nothing: they are a
	 * new list, so Tab, Down and Enter land on the first result (Shift+Tab and Up on the last stop), even when the keyboard
	 * was on Done or a result further down before. With nothing typed it carries on from the stop it was on.
	 */
	static int fromSearchBox(int current, boolean results) {
		return results ? -1 : current;
	}

	/**
	 * The row Ctrl+Tab puts the keyboard on in the tab it opens: the one it was on there ({@code remembered}, -1 for none)
	 * while that row shows, else the next one that does, wrapping round; -1 when no row of the tab shows. A setting on
	 * another tab, or a look, can hide the row in the meantime, and the Narrator must not name a row that isn't there.
	 * {@code rows} tells which stops are rows that show.
	 */
	static int backOnTab(boolean[] rows, int remembered) {
		if (remembered < 0) {
			return -1;
		}
		return rows[remembered] ? remembered : next(rows, remembered, 1);
	}

	/**
	 * Where the keyboard goes when the row it is on ({@code at}) stops showing under it, like This song when the song
	 * stops or a row hidden by a setting changed with the mouse: the next row that shows, wrapping round, so it stays in
	 * the list; Reset all or Done ({@code available}, every stop the keyboard can reach) only when no row of the tab shows.
	 * {@code rows} tells which stops are rows that show.
	 */
	static int afterHidden(boolean[] rows, boolean[] available, int at) {
		int row = backOnTab(rows, at);
		return row >= 0 ? row : next(available, at, 1);
	}

	/** Where a row sits among the rows that show: the {@code n}th of {@code of}. */
	record Place(int n, int of) {
	}

	/**
	 * Where the stop at {@code at} sits among the rows that show ({@code rows}), counted the way vanilla lists count their
	 * rows: on a tab its rows that show, in the search the results. Null for a stop that is no such row, and for a lone
	 * row (vanilla says no place in a list of one).
	 */
	static @Nullable Place place(boolean[] rows, int at) {
		if (at < 0 || at >= rows.length || !rows[at]) {
			return null;
		}
		int n = 0;
		int of = 0;
		for (int i = 0; i < rows.length; i++) {
			if (rows[i]) {
				of++;
				if (i <= at) {
					n++;
				}
			}
		}
		return of > 1 ? new Place(n, of) : null;
	}

	/**
	 * Whether the ring is drawn on the row or button the keyboard is on: after keys, not after the mouse
	 * ({@code keysUsed}), and not while the search box has the keyboard ({@code typing}): the box has its own outline
	 * then, and only one thing may look focused.
	 */
	static boolean ringShows(boolean keysUsed, boolean typing) {
		return keysUsed && !typing;
	}

	/**
	 * The scroll that shows a row spanning {@code top} to {@code bottom} (measured from the top of the content) in a view
	 * {@code viewH} tall, with {@code margin} of room around it. Stays put when it is already in view; a row taller than
	 * the view shows its top.
	 */
	static float scrollIntoView(float scroll, int top, int bottom, int viewH, int margin, float max) {
		float lo = top - margin;
		float hi = bottom + margin;
		float s = scroll;
		if (hi - lo >= viewH || lo < s) {
			s = lo;
		} else if (hi > s + viewH) {
			s = hi - viewH;
		}
		return Mth.clamp(s, 0, Math.max(0, max));
	}

	/** Page Up ({@code dir} -1) or Page Down (+1): a view's height less one line, kept inside the content. */
	static float page(float scroll, int dir, int viewH, float max) {
		return Mth.clamp(scroll + dir * Math.max(1, viewH - 24), 0, Math.max(0, max));
	}

	static float maxScroll(int contentH, int viewH) {
		return Math.max(0, contentH - viewH + 4);
	}

	/** Where a tab's scroll wants to be once its height is known: the bottom when asked, else kept within the content. */
	static float settleScroll(float target, boolean toEnd, int contentH, int viewH) {
		float max = maxScroll(contentH, viewH);
		return toEnd ? max : Mth.clamp(target, 0, max);
	}

	/** Who gets Left, Right, Enter or Space: the row the keyboard is on, the row under the mouse, or nobody yet. */
	enum KeyTarget { FOCUSED, MOUSE_ROW, FIRST_ROW }

	/**
	 * With the keyboard on a row it gets the keys. With nothing chosen, the row under the mouse gets them only once the
	 * mouse has moved; before that the keys would change a setting nobody has been told about, so focus goes to the first row.
	 */
	static KeyTarget keyTarget(boolean hasFocus, boolean mouseMoved) {
		return hasFocus ? KeyTarget.FOCUSED : mouseMoved ? KeyTarget.MOUSE_ROW : KeyTarget.FIRST_ROW;
	}

	/** Whether the mouse is somewhere else than where it was when the screen opened (a pixel of jitter doesn't count). */
	static boolean mouseMoved(double fromX, double fromY, double x, double y) {
		return Math.abs(x - fromX) >= 2 || Math.abs(y - fromY) >= 2;
	}
}
