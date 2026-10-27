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

	/** Total width of the covers laid out in columns, without a trailing gap. */
	static int shelfWidth(int count, int rows, int stride, int gap) {
		if (count <= 0) {
			return 0;
		}
		int columns = (count + rows - 1) / rows;
		return columns * stride - gap;
	}

	/** Moves the selection by whole columns and rows, staying put at an edge. */
	static int shelfMove(int selected, int dCol, int dRow, int rows, int count) {
		if (count <= 0) {
			return -1;
		}
		if (selected < 0) {
			return 0;
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
