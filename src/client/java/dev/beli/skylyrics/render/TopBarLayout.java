package dev.beli.skylyrics.render;

/**
 * The top bar's sizes and the sums behind them, kept apart from drawing so they can be tested. All lengths are
 * in GUI units, measured from the reference look at GUI scale 3 (where one unit is three screen pixels).
 */
final class TopBarLayout {
	/** The widest the pill gets; it is sized to its text, and longer lines scroll inside it. */
	static final float PILL_W = 270;
	/** The pill's height: 58 screen pixels at GUI scale 3. */
	static final float PILL_H = 58 / 3f;
	/** Below this widest size the clock and the stats step aside: the song matters more. */
	static final float MIN_PILL = 150;
	/** Kept free at the screen's edges. */
	static final float EDGE = 4;
	/** From the clock to the pill, and from the pill to the stats. */
	static final float CLOCK_GAP = 11;
	static final float STATS_GAP = 10;

	static final float ART = 12;
	/** From the pill's left end to the cover, and from the cover to the text. */
	static final float ART_INSET = 17 / 3f;
	static final float TEXT_GAP = 17 / 3f;
	/** Text is cut off this far right of the cover, so a scrolled line never runs under it. */
	static final float CLIP_GAP = 3;

	/** A spinning cover's top speed (turns a second) and how quickly it gets there or winds down (per second). */
	static final float SPIN_TURNS = 0.15f, SPIN_EASE = 2.5f;
	/** The hole in the middle of a spinning cover, as a share of its width. */
	static final float SPIN_HOLE = 0.16f;

	static final float BAR_W = 2;
	static final float BAR_GAP = 1;
	static final int BARS = 4;
	static final float EQ_W = BARS * BAR_W + (BARS - 1) * BAR_GAP;
	/** From the equaliser's last bar to the pill's right end. */
	static final float EQ_INSET = 9;
	/** The highlight stops this far before the equaliser. */
	static final float EQ_ROOM = 13 / 3f;

	/** The most the highlight reaches past its words on each side (the reference's, where it fits). */
	static final float HL_PAD = 10 / 3f;
	/** The highlight always stops at least this short of the next word, so it never touches a neighbour. */
	static final float HL_CLEAR = 0.8f;
	/** Added to the font's space between words: the reference's gaps are a little wider (3 to 3.7 units). */
	static final float WORD_EXTRA = 0.6f;

	private TopBarLayout() {}

	/** What fits: the pill's widest size, and whether the clock (left) and the stats (right) are shown. */
	record Fit(float pill, boolean clock, boolean stats) {}

	/**
	 * The pill stays centred, so the wider of the two sides decides how much room it has. On a narrow screen it
	 * shrinks; when even that isn't enough the stats go first, then the clock.
	 *
	 * @param clockW the clock's width, 0 when it is off
	 * @param statsW the stats' width, 0 when they are off
	 */
	static Fit fit(float guiWidth, float clockW, float statsW) {
		boolean clock = clockW > 0, stats = statsW > 0;
		float pill = pill(guiWidth, clock ? clockW : 0, stats ? statsW : 0);
		if (pill < MIN_PILL && stats) {
			stats = false;
			pill = pill(guiWidth, clock ? clockW : 0, 0);
		}
		if (pill < MIN_PILL && clock) {
			clock = false;
			pill = pill(guiWidth, 0, 0);
		}
		return new Fit(Math.max(pill, Math.min(PILL_W, ART_INSET + ART + TEXT_GAP)), clock, stats);
	}

	private static float pill(float guiWidth, float clockW, float statsW) {
		float left = clockW > 0 ? clockW + CLOCK_GAP : 0;
		float right = statsW > 0 ? STATS_GAP + statsW : 0;
		return Math.min(PILL_W, guiWidth - 2 * (Math.max(left, right) + EDGE));
	}

	/** How far the highlight reaches past its words, with words this far apart. */
	static float highlightPad(float wordGap) {
		return Math.clamp(wordGap - HL_CLEAR, 1, HL_PAD);
	}

	/** Everything in the pill around the text: the cover on the left, the highlight's end and the equaliser on the right. */
	static float chrome() {
		return ART_INSET + ART + TEXT_GAP + HL_PAD + EQ_ROOM + EQ_W + EQ_INSET;
	}

	/** The pill's width for text this wide: just enough, up to the widest that fits on screen. */
	static float pillFor(float textW, float widest) {
		return Math.min(widest, chrome() + Math.max(0, textW));
	}

	/** Room for the line between where the text starts and where the highlight must end. */
	static float textRoom(float pill) {
		return pill - chrome() + HL_PAD;
	}

	/**
	 * How far a line too long for the pill moves left, in reading order, so the word being sung (with its
	 * highlight) stays in view before the equaliser. A line that fits never moves, and a long one moves no
	 * further than to show its end.
	 *
	 * @param total the whole line's width
	 * @param curRight where the current word ends, from the line's start
	 * @param room {@link #textRoom}
	 */
	static float lineShift(float total, float curRight, float room, float pad) {
		float most = Math.max(0, total + pad - room);
		return Math.clamp(curRight + pad - room, 0, most);
	}

	/**
	 * How many of the four signal bars are lit for a ping, using the same steps as the player list's icon (which
	 * has five bars: under 150 ms is full). No ping yet lights none.
	 */
	static int signalBars(int latencyMs) {
		if (latencyMs < 0) {
			return 0;
		}
		if (latencyMs < 150) {
			return 4;
		}
		if (latencyMs < 300) {
			return 3;
		}
		return latencyMs < 600 ? 2 : 1;
	}

	/**
	 * A spinning cover's speed after {@code dt} seconds, from 0 (still) to 1 (full speed): it speeds up while the song
	 * plays and winds down like a record when it stops.
	 */
	static float spinSpeed(float speed, boolean turning, float dt) {
		float target = turning ? 1 : 0;
		return speed + (target - speed) * Math.min(1, dt * SPIN_EASE);
	}

	/** How much of the song has played, 0 to 1, or -1 when the player doesn't say how long it is. */
	static float progress(long positionMs, long lengthMs) {
		if (lengthMs <= 0) {
			return -1;
		}
		return Math.clamp(positionMs / (float) lengthMs, 0f, 1f);
	}
}
