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

	/**
	 * How far the bottom bar's lower edge sits above the screen's bottom: clear of the held item's name (which reaches
	 * 59 up), the air bubbles and the armour row.
	 */
	static final float BOTTOM_UP = 64;
	/** Without hearts and armour (creative, spectator) the game draws the held item's name this much lower, and so does the bar. */
	static final float NO_HEALTH_DROP = 14;

	private TopBarLayout() {}

	/** How far above the screen's bottom the action bar message's text reaches (its top), and the gap the bar keeps above it. */
	static final float ACTION_UP = 72, ACTION_GAP = 3;
	/** The bar keeps this far above the armour row (or the top row of hearts without armour). */
	static final float HEALTH_GAP = 4;

	/** The bar's top edge in GUI units: just below the boss bars, or above the hotbar's text for the bottom position. */
	static float bottomTop(int guiHeight) {
		return bottomTop(guiHeight, true);
	}

	/** The same, where {@code healthRows} says whether hearts and armour are drawn (survival and adventure). */
	static float bottomTop(int guiHeight, boolean healthRows) {
		return bottomTop(guiHeight, healthRows, 0, false);
	}

	/**
	 * The same, also clear of the top of the hearts and armour ({@link #healthTopUp}) and, while an action bar message
	 * shows, of that text.
	 */
	static float bottomTop(int guiHeight, boolean healthRows, float healthTopUp, boolean actionBar) {
		return Math.max(0, guiHeight - bottomUp(healthRows, healthTopUp, actionBar) - PILL_H);
	}

	/** How far above the screen's bottom the bar's lower edge sits; never lower than {@link #BOTTOM_UP} without health rows. */
	static float bottomUp(boolean healthRows, float healthTopUp, boolean actionBar) {
		float up = healthRows ? Math.max(BOTTOM_UP, healthTopUp + HEALTH_GAP) : BOTTOM_UP - NO_HEALTH_DROP;
		return actionBar ? Math.max(up, ACTION_UP + ACTION_GAP) : up;
	}

	/** The most the hearts and armour are taken to reach up, so absurd health can't push the bar off the screen. */
	static final float HEALTH_UP_MAX = 120;

	/** How many rows the game spaces the hearts by: from the health plus absorption (absorption rounds up), at least one. */
	private static int spacingRows(float maxHealth, float absorption) {
		double rows = Math.ceil((maxHealth + Math.ceil(absorption)) / 20.0);
		return rows >= 1000 ? 1000 : Math.max(1, (int) rows);
	}

	/** The rows of hearts and armour the game lays out: the larger of the spacing rows and the rows the drawn hearts fill. */
	static int heartRows(float maxHealth, float absorption) {
		return Math.max(spacingRows(maxHealth, absorption), drawnRows(maxHealth, absorption));
	}

	/** The rows the drawn hearts fill: the game draws ceil(max / 2) + ceil(ceil(absorption) / 2) hearts, ten to a row. */
	private static int drawnRows(float maxHealth, float absorption) {
		double hearts = Math.ceil(maxHealth / 2.0) + Math.ceil(Math.ceil(absorption) / 2.0);
		double rows = Math.ceil(hearts / 10);
		return rows >= 1000 ? 1000 : Math.max(1, (int) rows);
	}

	/** The distance between rows of hearts: 10, closing up by one a row from the third row on, to at least 3. */
	static int rowSpacing(int rows) {
		return Math.max(10 - (rows - 2), 3);
	}

	/**
	 * How far above the screen's bottom the top of the hearts and armour reaches: the armour row (10 above the hearts'
	 * top spacing row) when there is armour, else the top row of hearts. Never more than {@link #HEALTH_UP_MAX}.
	 */
	static float healthTopUp(float maxHealth, float absorption, int armour) {
		int spaced = spacingRows(maxHealth, absorption);
		int space = rowSpacing(spaced);
		float hearts = 39 + (drawnRows(maxHealth, absorption) - 1) * (float) space;
		float up = armour > 0 ? Math.max(hearts, 39 + (spaced - 1) * (float) space + 10) : hearts;
		return Math.min(up, HEALTH_UP_MAX);
	}

	/**
	 * The bottom bar's box in screen units (left, top, right, bottom) including the clock and the stats, for a bar drawn
	 * {@code size} times bigger about its bottom edge and the screen's middle, with its pill {@code pill} wide.
	 */
	static float[] barBox(float guiWidth, float size, float pill, float clockW, boolean clock, float statsW, boolean stats,
			float top) {
		float mid = guiWidth / 2f;
		float left = mid - pill / 2f - (clock ? CLOCK_GAP + clockW : 0);
		float right = mid + pill / 2f + (stats ? STATS_GAP + statsW : 0);
		float bottom = top + PILL_H;
		return new float[] {mid + (left - mid) * size, bottom - PILL_H * size, mid + (right - mid) * size, bottom};
	}

	/**
	 * The top bar's box in screen units (left, top, right, bottom), for a bar drawn {@code size} times bigger about its top
	 * edge and the screen's middle: the same widths as {@link #barBox}, hanging down from {@code top}.
	 */
	static float[] topBarBox(float guiWidth, float size, float pill, float clockW, boolean clock, float statsW, boolean stats,
			float top) {
		float[] b = barBox(guiWidth, size, pill, clockW, clock, statsW, stats, top);
		return new float[] {b[0], top, b[2], top + PILL_H * size};
	}

	/**
	 * The line the bar grows from when {@code barSize} scales it: its own edge on the side of the screen it sits on, so a
	 * bigger bar never grows off the screen or into the hotbar.
	 */
	static float pivotY(boolean atBottom, float top) {
		return atBottom ? top + PILL_H : top;
	}

	/**
	 * A clip box given in the bar's own units, as screen units, for a bar drawn with {@code size} about the pivot (the
	 * screen's middle, {@code pivotY}). Minecraft 1.21.1's scissor ignores the pose, so it needs the scaled box; the
	 * rounding is the one 1.21.4 and later do themselves. Returns left, top, right, bottom.
	 */
	static int[] scaledClip(float left, float top, float right, float bottom, float size, float guiWidth, float pivotY) {
		float dx = (1 - size) * guiWidth / 2f, dy = (1 - size) * pivotY;
		float x1 = dx + size * left, y1 = dy + size * top, x2 = dx + size * right, y2 = dy + size * bottom;
		int l = (int) Math.floor(x1), t = (int) Math.floor(y1);
		return new int[] {l, t, l + (int) Math.floor(x2 - x1), t + (int) Math.floor(y2 - y1)};
	}

	/**
	 * The least a top bar's top may be so it keeps clear of the potion effect icons Minecraft draws in the top right
	 * corner (25 units each, good effects in a row along the top, bad ones in a second row under it). Only a row the bar's
	 * right edge reaches is cleared; 0 when none. Like the Record card, the bar goes under the icons rather than over them.
	 *
	 * @param barRight the right edge of the bar with its stats, in screen units
	 * @param goodRow where the good effects' row ends, from the top; {@code badRow} the same for the bad ones
	 */
	static float effectsClear(float guiWidth, float barRight, int good, int bad, int goodRow, int badRow) {
		float clear = 0;
		if (good > 0 && barRight > guiWidth - EFFECT_W * good - EDGE) {
			clear = goodRow + 2;
		}
		if (bad > 0 && barRight > guiWidth - EFFECT_W * bad - EDGE) {
			clear = badRow + 2;
		}
		return clear;
	}

	/** The effect icon rows sit this much lower in the demo, under its timer text. */
	static final float DEMO_DROP = 15;
	/** The demo timer text (top right, from y 5) is clear of this line downwards. */
	static final float DEMO_TIMER_BOTTOM = 16;
	/** Kept between a card and the box it moved out of. */
	static final float DODGE_GAP = 3;
	/** The sidebar's most entries, and the subtitle box's narrowest width we assume (it is as wide as the longest line). */
	static final int SIDEBAR_MAX = 15;
	static final float SUBTITLE_MIN_W = 60;

	/** Where the demo's timer text starts, from its width: the game puts it 10 units from the right edge. */
	static float demoTimerLeft(float guiWidth, float timerW) {
		return guiWidth - timerW - 10;
	}

	/**
	 * How far down something at the top must start so it stays clear of the demo's timer text: {@link #DEMO_TIMER_BOTTOM}
	 * when this is the demo and the thing's right edge reaches the text, else 0.
	 */
	static float demoClear(boolean demo, float guiWidth, float timerW, float rightEdge) {
		return demo && rightEdge > demoTimerLeft(guiWidth, timerW) ? DEMO_TIMER_BOTTOM : 0;
	}

	/** How far the effect rows' ends move down: {@link #DEMO_DROP} in the demo. */
	static int effectRow(int row, boolean demo) {
		return demo ? row + (int) DEMO_DROP : row;
	}

	/** The widest line of the scoreboard sidebar, as the game works it out: the title, and each name with its score after a ": ". */
	static int sidebarWidth(int titleW, int colonW, int[] nameW, int[] scoreW) {
		int w = titleW;
		for (int i = 0; i < nameW.length; i++) {
			w = Math.max(w, nameW[i] + (scoreW[i] > 0 ? colonW + scoreW[i] : 0));
		}
		return w;
	}

	/**
	 * The scoreboard sidebar's box (left, top, right, bottom) in GUI units, as the game draws it: {@code entries} lines
	 * (at most {@link #SIDEBAR_MAX}) of 9 under a title line, centred a third of the entries' height under the screen's
	 * middle, its widest line {@code widest} wide, 5 from the right edge on the left and 1 short of it on the right.
	 */
	static float[] sidebarBox(int guiWidth, int guiHeight, int entries, int widest) {
		int m = Math.min(SIDEBAR_MAX, Math.max(0, entries));
		int bottom = guiHeight / 2 + m * 9 / 3;
		return new float[] {guiWidth - widest - 5, bottom - m * 9 - 10, guiWidth - 1, bottom};
	}

	/**
	 * The subtitle boxes' reach (left, top, right, bottom) as the game draws them: {@code count} boxes that are all as wide
	 * as the widest text plus {@code margin} (the "&lt; &gt;" marks and their spaces), halved down to whole units, centred 2
	 * short of the right edge; the first is 10 tall with its middle 35 above the bottom and each next 10 higher. Null when
	 * none show.
	 */
	static float[] subtitleBox(int guiWidth, int guiHeight, int count, int widest, int margin) {
		if (count <= 0) {
			return null;
		}
		int half = (widest + margin) / 2;
		return new float[] {guiWidth - 2 * half - 3, guiHeight - 40 - 10 * (count - 1), guiWidth - 1, guiHeight - 30};
	}

	/** When the live subtitles can't be read: one box of the narrowest width (the "&lt; &gt;" marks and their spaces alone). */
	static float[] subtitleBoxGuess(int guiWidth, int guiHeight) {
		return subtitleBox(guiWidth, guiHeight, 1, 0, (int) SUBTITLE_MIN_W);
	}

	/** The open chat's text field runs from 14 above the bottom edge, across nearly the whole width. */
	static final float CHAT_INPUT_TOP = 14;

	/** A bottom card's top, lifted to clear the open chat's text field when {@code chatOpen}. */
	static float aboveChatInput(boolean chatOpen, float y, float h, int guiHeight) {
		return chatOpen ? Math.min(y, guiHeight - CHAT_INPUT_TOP - 2 - h) : y;
	}

	/**
	 * How tall the open chat's box is drawn: the game puts its backdrop behind the lines it shows only, {@code lineH} high
	 * each times the chat's {@code scale}, {@code lines} of them up to a page ({@code linesPerPage}). A chat with a few
	 * lines is far lower than a full page, so it doesn't reach a top card.
	 */
	static float chatBoxH(int lines, int linesPerPage, int lineH, double scale) {
		return Math.max(0, Math.min(lines, linesPerPage)) * lineH * (float) scale;
	}

	/**
	 * Whether the open chat's box covers a card: not when the chat is set to Hidden, as the game then draws no box, nor
	 * when it has no lines to draw one behind.
	 */
	static boolean chatBoxCovers(boolean chatOpen, boolean chatHidden, int guiHeight, float chatW, float chatH, float x, float y,
			float w, float h) {
		return chatOpen && !chatHidden && chatH > 0 && chatCovers(guiHeight, chatW, chatH, x, y, w, h);
	}

	/** Whether a card with its top left at ({@code x}, {@code y}) and this size overlaps a box (left, top, right, bottom). */
	static boolean overlaps(float[] box, float x, float y, float w, float h) {
		return box != null && x < box[2] && x + w > box[0] && y < box[3] && y + h > box[1];
	}

	/**
	 * Where a card's top should be so it clears the boxes: unchanged when it overlaps none, else moved above or below
	 * the one it overlaps (the nearer way that fits between {@code minTop} and {@code maxBottom}). NaN when it can't
	 * fit, which the caller shows as the card faded out.
	 */
	static float dodgeY(float x, float y, float w, float h, float minTop, float maxBottom, float[]... boxes) {
		float cur = y;
		for (int pass = 0; pass <= boxes.length; pass++) {
			boolean moved = false;
			for (float[] box : boxes) {
				if (!overlaps(box, x, cur, w, h)) {
					continue;
				}
				float up = box[1] - DODGE_GAP - h, down = box[3] + DODGE_GAP;
				boolean upOk = up >= minTop, downOk = down + h <= maxBottom;
				if (!upOk && !downOk) {
					return Float.NaN;
				}
				cur = upOk && (!downOk || cur - up <= down - cur) ? up : down;
				moved = true;
			}
			if (!moved) {
				return cur;
			}
		}
		return Float.NaN;
	}

	/**
	 * Whether the open chat screen covers a card: the chat's box runs from the left edge, {@code chatW} wide (and a
	 * little more for its backdrop), up from 40 above the bottom for {@code chatH}, both already times the chat's scale.
	 */
	static boolean chatCovers(int guiHeight, float chatW, float chatH, float x, float y, float w, float h) {
		float[] box = {0, guiHeight - 40 - chatH, chatW + 12, guiHeight - 40};
		return overlaps(box, x, y, w, h);
	}

	/** How wide each effect icon's slot is. */
	static final float EFFECT_W = 25;

	/** The right edge of a bar at the top: the pill, then the stats if they show, scaled about the screen's middle by {@code size}. */
	static float barRight(float guiWidth, float size, float pill, float statsW, boolean stats) {
		float mid = guiWidth / 2f;
		return mid + (pill / 2f + (stats ? STATS_GAP + statsW : 0)) * size;
	}

	/** {@link #fit} for a bar drawn {@code size} times bigger: everything is measured in the bar's own, scaled units. */
	static Fit fit(float guiWidth, float size, float clockW, float statsW) {
		return fit(guiWidth / size, clockW, statsW);
	}

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
	 * Where the line's text is cut off on the left, in whole GUI units (the clip box takes whole units), for a pill whose
	 * left end is at {@code x}: rounded down from where the first word's highlight starts, so its round end is never cut
	 * off, and still clear of the cover, so a scrolled line never runs under it.
	 */
	static int textClipLeft(float x) {
		return (int) Math.floor(x + ART_INSET + ART + TEXT_GAP - HL_PAD);
	}

	/**
	 * Where it is cut off on the right, for a pill {@code pill} wide: rounded up from the end of the room, where the last
	 * word's highlight ends, and still short of the equaliser.
	 */
	static int textClipRight(float x, float pill) {
		return (int) Math.ceil(x + ART_INSET + ART + TEXT_GAP + textRoom(pill));
	}

	/**
	 * The same for a bar drawn {@code size} big about the middle of a GUI {@code guiWidth} wide, with the clip starting at
	 * {@code left}. The game takes the box through the bar's scale and then rounds its left edge and its width down to
	 * whole GUI units (on 1.21.1 {@link #scaledClip} does it the same way), which at any size but 100% could leave its
	 * right edge up to two units short of the room's end, on the highlight's round end. The right edge goes out until it
	 * still reaches the room's end after that rounding.
	 */
	static int textClipRight(float x, float pill, int left, float size, float guiWidth) {
		float end = x + ART_INSET + ART + TEXT_GAP + textRoom(pill);
		int right = textClipRight(x, pill);
		// Worked out as the pose does it: scaled, then moved by the bar's offset from the screen's middle
		float dx = (1 - size) * guiWidth / 2f;
		float x1 = size * left + dx, want = size * end + dx;
		for (int i = 0; i < 4 && Math.floor(x1) + Math.floor(size * right + dx - x1) < want; i++) {
			right++;
		}
		return right;
	}

	/**
	 * How far a line too long for the pill moves left, in reading order, so the word being sung (with its
	 * highlight) stays in view before the equaliser. A line that fits never moves, and a long one moves no
	 * further than to show its end. A word wider than the room keeps its start in view (never shifted past
	 * {@code curLeft}), since it is sung from its first letter.
	 *
	 * @param total the whole line's width
	 * @param curLeft where the current word starts, from the line's start
	 * @param curRight where the current word ends, from the line's start
	 * @param room {@link #textRoom}
	 */
	static float lineShift(float total, float curLeft, float curRight, float room, float pad) {
		float most = Math.max(0, Math.min(total + pad - room, curLeft));
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

	/** The record's angle after one frame, in radians: {@code motion} is Distortion Effects (0 stands still, 1 turns fully). */
	static float spinStep(float spin, float speed, float turnsPerSecond, float dt, float motion) {
		return (spin + speed * turnsPerSecond * (float) (2 * Math.PI) * dt * motion) % (float) (2 * Math.PI);
	}

	/**
	 * What a Record card or top bar draws this frame: {@code now} while it shows (or when it has nothing on screen yet), and
	 * {@code shown}, what it drew last, while it fades out. Once the music has stopped the player says nothing, which would
	 * read "Unknown song" for the third of a second the fade takes.
	 */
	static <T> T keptWhileFading(boolean showing, @org.jspecify.annotations.Nullable T shown, T now) {
		return showing || shown == null ? now : shown;
	}

	/** A song longer than this is a bogus length (a player's garbage value), treated as unknown. */
	static final long MAX_LENGTH_MS = 24L * 60 * 60 * 1000;

	/** How much of the song has played, 0 to 1, or -1 when the player doesn't say how long it is (or says over 24 hours). */
	static float progress(long positionMs, long lengthMs) {
		if (lengthMs <= 0 || lengthMs > MAX_LENGTH_MS) {
			return -1;
		}
		return Math.clamp(positionMs / (float) lengthMs, 0f, 1f);
	}
}
