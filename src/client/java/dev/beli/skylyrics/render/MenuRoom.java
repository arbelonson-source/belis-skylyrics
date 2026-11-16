package dev.beli.skylyrics.render;

/**
 * Where the title screen's lyrics go: under the buttons in the middle of the screen, and no wider than the room beside
 * them. A button another mod puts out to the side (a corner) doesn't push the lyrics below it, it only keeps them to
 * the middle column's width, so they neither vanish for lack of height nor run into it.
 */
public final class MenuRoom {
	/** Half the lyrics' wrap width at size 1: the column the lyrics fill. */
	static final int HALF_COLUMN = 130;
	/** Kept clear at each side of the screen. */
	static final int SIDE = 8;

	private MenuRoom() {
	}

	/** Top of the room and the widest the lyrics may be. */
	public record Room(int top, float maxWidth) {
	}

	/**
	 * @param buttons each visible button as {x, width, bottom}
	 * @param bottom  where the footer (version and copyright text) starts; buttons reaching it are left out
	 */
	public static Room of(int screenWidth, int bottom, int[][] buttons) {
		int center = screenWidth / 2;
		int top = 0;
		for (int[] b : buttons) {
			if (b[2] < bottom && middle(b, center)) {
				top = Math.max(top, b[2]);
			}
		}
		boolean beside = false;
		for (int[] b : buttons) {
			if (b[2] < bottom && b[2] > top && !middle(b, center)) {
				beside = true; // lower than the middle buttons: it sits next to the lyrics
			}
		}
		float width = Math.max(0, screenWidth - 2 * SIDE);
		return new Room(top, beside ? Math.min(width, 2 * HALF_COLUMN) : width);
	}

	private static boolean middle(int[] b, int center) {
		return b[0] < center + HALF_COLUMN && b[0] + b[1] > center - HALF_COLUMN;
	}
}
