package dev.beli.skylyrics.gui;

/** The layout maths of the library's shelf and carousel views, kept free of drawing so it can be tested. */
final class LibraryViews {
	private LibraryViews() {
	}

	/** Glides towards the target with the same ease at any frame rate (time-based, not frame-based). */
	static float ease(float value, float target, float dt, float speed) {
		return Ui.approach(value, target, dt, speed);
	}

	// Carousel: a cover at offset o sits o places from the centre (0 = the middle one, fractions while it glides)

	/** Sideways distance of the cover's middle from the carousel's middle; the first neighbours sit close, the rest bunch up. */
	static float spread(float o, int size) {
		float a = Math.abs(o);
		float d = a <= 1 ? a * 0.62f : 0.62f + (a - 1) * 0.3f;
		return Math.signum(o) * d * size;
	}

	/** Size of a cover next to the middle one: 1 in the middle, shrinking to 0.4. */
	static float scale(float o) {
		return Math.max(0.4f, Math.min(1, 1 - 0.26f * Math.min(Math.abs(o), 2.4f)));
	}

	/** How see-through a cover is: full in the middle, dimmer next to it, gone three places away. */
	static float alpha(float o) {
		float a = Math.abs(o);
		if (a >= 3) {
			return 0;
		}
		return a <= 1 ? 1 - 0.45f * a : 0.55f * (1 - (a - 1) / 2);
	}

	/** Width factor that fakes a cover turned away from you. */
	static float squash(float o) {
		return 1 - 0.2f * Math.min(Math.abs(o), 1);
	}

	// Shelf: covers fill each column top to bottom, then the next column to the right

	static final int BANNER_ONE = 16, BANNER_TWO = 26;

	/** Below this much room a wrapped line would be a few letters: the strip stays one clipped line. */
	static final int BANNER_MIN_ROOM = 30;

	/** The sign-in strip: one 16 px line when the text fits the room (or the room is too small to wrap into), else a 26 px strip for two lines. */
	static int bannerStripH(int textW, int room) {
		return textW <= room || room < BANNER_MIN_ROOM ? BANNER_ONE : BANNER_TWO;
	}

	/** How many rows of covers fit in the height; at least one. */
	static int shelfRows(int viewH, int rowH) {
		return Math.max(1, viewH / Math.max(1, rowH));
	}

	static int shelfCol(int index, int rows) {
		return index / rows;
	}

	static int shelfRow(int index, int rows) {
		return index % rows;
	}

	/** The item at that column and row, or -1 when there is none. */
	static int shelfIndex(int col, int row, int rows, int count) {
		if (col < 0 || row < 0 || row >= rows) {
			return -1;
		}
		int index = col * rows + row;
		return index < count ? index : -1;
	}

	/**
	 * The cover under the mouse, or -1. The shelf starts at {@code viewX} and {@code top}, is scrolled {@code left}
	 * pixels sideways, and has covers {@code cover} wide and tall, {@code stride} and {@code rowH} apart.
	 */
	static int shelfHit(double mx, double my, int viewX, int top, int left, int stride, int rowH, int cover, int rows, int count) {
		int c = (int) Math.floor((mx - viewX + left) / stride);
		int r = (int) Math.floor((my - top) / rowH);
		int hit = shelfIndex(c, r, rows, count);
		if (hit >= 0 && Ui.inside(mx, my, viewX + c * stride - left, top + r * rowH, cover, cover)) {
			return hit;
		}
		return -1;
	}

	/** Total width of the covers laid out in columns, without a trailing gap. */
	static int shelfWidth(int count, int rows, int stride, int gap) {
		if (count <= 0) {
			return 0;
		}
		int columns = (count + rows - 1) / rows;
		return columns * stride - gap;
	}

	/** Room between the shelf's last column and the line after it. */
	static final int TAIL_GAP = 8;

	/** The line after the shelf's last column: "Loading…" while the next page comes, else why it failed (a click on it, or Enter with the keyboard on it, tries again), else nothing. */
	static String tailText(boolean loading, String error, String loadingText) {
		return loading ? loadingText : error;
	}

	/** Where the line after the last column starts, for covers {@code width} wide from {@code viewX}, scrolled {@code left}. */
	static int tailX(int viewX, int left, int width) {
		return viewX - left + width + TAIL_GAP;
	}

	/** How far the shelf scrolls sideways: to the end of its covers, with room for a line {@code tailW} wide after them (0 for none). */
	static int shelfMaxScroll(int count, int rows, int stride, int gap, int tailW, int viewW) {
		// The whole line fits at the end, not just its first letters
		return Math.max(0, shelfWidth(count, rows, stride, gap) + (tailW > 0 ? TAIL_GAP + tailW : 0) - viewW);
	}

	/** Whether the mouse is on the line after the last column (drawn at {@code lineY}, {@code tailW} wide). */
	static boolean tailHit(double mx, double my, int viewX, int left, int width, int tailW, int lineY) {
		return tailHit(mx, my, viewX, left, width, tailW, lineY, 1);
	}

	/** The same for {@code lines} lines 10 apart, the first drawn at {@code lineY}. */
	static boolean tailHit(double mx, double my, int viewX, int left, int width, int tailW, int lineY, int lines) {
		// a little slack around the text, like the grid's error line
		return tailW > 0 && Ui.inside(mx, my, tailX(viewX, left, width) - 4, lineY - 4, tailW + 8, 17 + 10 * (lines - 1));
	}

	/** Moves the selection by whole columns and rows, staying put at an edge. */
	static int shelfMove(int selected, int dCol, int dRow, int rows, int count) {
		if (count <= 0) {
			return -1;
		}
		if (selected < 0) {
			return 0;
		}
		if (selected >= count) {
			// Left over from a longer list that was swapped for this one: come back to its end instead of staying out of reach
			return count - 1;
		}
		int col = selected / rows, row = selected % rows;
		int newRow = row + dRow;
		if (newRow < 0 || newRow >= rows || col * rows + newRow >= count) {
			return selected;
		}
		int newCol = col + dCol;
		if (newCol < 0) {
			return selected;
		}
		int index = newCol * rows + newRow;
		if (index >= count) {
			// A short last column: land on its last cover, but never move backwards
			return dCol > 0 && count - 1 > selected ? count - 1 : selected;
		}
		return index;
	}

	/** Scrolls the least that brings the span [pos, pos + size) into a view of that length. */
	static float reveal(float target, float pos, float size, float viewSize) {
		if (pos < target) {
			return pos;
		}
		if (pos + size > target + viewSize) {
			return pos + size - viewSize;
		}
		return target;
	}
}
