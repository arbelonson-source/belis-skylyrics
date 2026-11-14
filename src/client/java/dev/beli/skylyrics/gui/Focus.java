package dev.beli.skylyrics.gui;

import net.minecraft.util.Mth;

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
}
