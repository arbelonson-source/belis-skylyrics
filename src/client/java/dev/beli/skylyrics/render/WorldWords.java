package dev.beli.skylyrics.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.render.LyricsRenderer.Display;

/**
 * The "words in the world" layout: every word or two of a line pops up as it's sung, on its own card floating a
 * little above where you were looking, scattered so the cards don't cover each other. A card stays where it
 * appeared (drifting slowly upwards) while you move around it; once the line is over (or, without "Keep the whole
 * line", once the next words come), it greys and fades away over a couple of seconds. These are drawn in the world pass on purpose: each card has its own place and turn,
 * which the flat screen layer can't show.
 */
final class WorldWords {
	private static final float DEG = Mth.DEG_TO_RAD;
	/** Cards appear this far from your eyes, in blocks. */
	private static final float NEAR = 6.5f, FAR = 15f;
	/** Never closer than this, even when blocks are in the way. */
	private static final float CLOSEST = 3f;
	/** The middle of the scatter sits this many degrees above the crosshair. */
	private static final float LIFT = 12f;
	/** Half the width and half the height of the scatter around that middle, in degrees. */
	private static final float SPREAD_X = 19f, SPREAD_Y = 11f;
	/** Spots in the scatter, and how many of the last used ones a new card avoids. */
	static final int SLOTS = 12;
	private static final int RECENT = 2;
	/** How far a card may land from its spot, in degrees, so the pattern doesn't look like a grid. */
	private static final float WOBBLE_X = 3.5f, WOBBLE_Y = 3f;
	/** Spots tried for each new card; the one overlapping the other cards least wins. */
	private static final int TRIES = 24;
	/** How much further out than the spots the last tries may go, when a crowd (a whole line kept up) fills them. */
	private static final float REACH = 0.6f;
	/** Room kept around each card when judging overlap, in degrees. */
	private static final float PAD_X = 2f, PAD_Y = 1.7f;
	/** Cards turn sideways by between these many degrees, and nod and lean a little, so they don't look pasted on. */
	private static final float TURN_MIN = 10f, TURN_MAX = 30f, NOD = 7f, LEAN = 4f;
	/** Blocks per text unit: capital letters end up a bit under half a block tall, about two degrees across the scatter. */
	private static final float SIZE = 0.06f;
	/** Card size, times the setting, for short pieces and for long ones; sizes in between follow the letter count. */
	private static final float SIZE_SHORT = 0.72f, SIZE_LONG = 1.3f;
	/** Pieces with up to this many letters are the smallest, with this many more the biggest. */
	private static final int SHORT_LETTERS = 3, LONG_LETTERS = 6;
	/** Each card is randomly up to this much smaller or bigger than its letters say, so sizes don't look graded. */
	private static final float SIZE_JITTER = 0.1f;
	/** At most this many cards at once by default (the setting); past it the oldest is cut short. */
	static final int MAX_CARDS = 7;
	/** A card cut short to make room fades out over this many seconds. */
	private static final float CUT = 0.3f;
	/** After a jump in the song, at most this many of the skipped pieces are spawned (the latest ones). */
	private static final int CATCH_UP = 2;
	/** Once its words are over, a card fades out over this many seconds by default (the setting). */
	static final float FADE = 2.2f;
	/** ...and in the first of these seconds it dims towards grey, by this much. */
	private static final float DIM_TIME = 0.24f, DIM = 0.5f;
	private static final int DIM_RGB = 0x8E92AA;
	/** A new card fades in over this many seconds, and its letters type in over this many. */
	private static final float FADE_IN = 0.14f, TYPE = 0.23f;
	/** A new card starts this much bigger and settles back over this many seconds. */
	static final float POP = 0.22f, POP_TIME = 0.24f;
	/** Cards drift up this many blocks a second, up to this high. */
	private static final float RISE = 0.25f, RISE_MAX = 1f;
	/** Before the first frame's view is known, cards further from where you look than this (cosine of the angle) aren't drawn. */
	private static final float CULL = 0.3f;
	/** A card's half height, and how far its glow and shadow reach past the letters, in text units (for the on-screen check). */
	private static final float HALF_HEIGHT = 6f, EDGE = 3f;
	/** Cards this far away are dropped, in blocks. */
	private static final float GONE_DISTANCE = 48f;
	/** A jump further than this in one frame is a teleport, not flying, so the words stay behind (blocks). */
	private static final float GLIDE_JUMP = 16f;
	/** How far up or down, in degrees, cards turned along with your view may go, so they never flip over your head. */
	private static final float SWING_PITCH = 80f;
	/** Drop shadow: offset down-right in text units, colour, and opacity by night and by day (a bright sky needs it darker). */
	private static final float SHADOW_X = 0.7f, SHADOW_Y = 0.9f, SHADOW_NIGHT = 0.55f, SHADOW_DAY = 0.85f;
	private static final int SHADOW_RGB = 0x05040C;
	/** Soft glow: copies of the word around it, how far out (text units) and how strong at full glow. */
	private static final int GLOW_COPIES = 3, GLOW_LIGHT = 2;
	private static final float GLOW_RADIUS = 1.1f, GLOW_ALPHA = 0.4f;
	/** Glow on the words being sung and on the rest of the line still up, and how long (seconds) a let-go card's lasts. */
	private static final float GLOW_SUNG = 1.3f, GLOW_LIT = 0.8f;
	static final float GLOW_OUT = 0.6f;
	/** By day the glow goes this far toward black: a dark halo shows against a bright sky, a light one doesn't. */
	private static final float HALO_DAY = 0.75f;
	/** Front to back, in text units, so the layers don't flicker against each other. */
	private static final float SHADOW_Z = -0.5f, GLOW_Z = 0.1f, FACE_Z = 0.3f;
	/** From the top of a row of text to the middle of its capitals. */
	private static final float CAP_MIDDLE = 3.8f;
	/** Words with at most this many letters lean on the next word ("CAN YOU", "THE BEAT"). */
	static final int SHORT_WORD = 3;
	/** The scatter's spots, as degrees right of and above its middle. */
	private static final float[][] SPOTS = slots();
	/** How far a flowing line floats, and how far it may start off the middle of the view (degrees). */
	private static final float SENTENCE_NEAR = 8f, SENTENCE_FAR = 12f, SENTENCE_SHIFT_X = 6f, SENTENCE_SHIFT_Y = 5f;
	/** How far a flowing line turns towards you (degrees) and how much its pieces vary in distance (a fraction). */
	private static final float SENTENCE_TURN = 15f, SENTENCE_DEPTH = 0.08f;
	/** A flowing line's new rows start up to this far in, its pieces sit up to this far off the row (degrees). */
	private static final float SENTENCE_INDENT = 2.5f, SENTENCE_BOB = 1.2f;
	/** How much each piece of a flowing line turns differently from the rest (degrees). */
	private static final float SENTENCE_WOBBLE = 4f;

	/** One card: where it stands, what it shows, and how it's turned. */
	private static final class Card {
		final Display display;
		final float[] at;
		/** Where it stands; moves along with you while you glide, ride or fly (elytra mode). */
		Vec3 pos;
		final float nod, lean, size;
		/** The way the card faces, turned along with you while you travel. */
		float face;
		final long born;
		long goneAt = -1;
		/** When it was cut short to make room for newer cards, or -1. */
		long cutAt = -1;

		Card(Display display, float[] at, Vec3 pos, float face, float nod, float lean, float size, long born) {
			this.display = display;
			this.at = at;
			this.pos = pos;
			this.face = face;
			this.nod = nod;
			this.lean = lean;
			this.size = size;
			this.born = born;
		}
	}

	private final List<Card> cards = new ArrayList<>();
	private final int[] recent = {-1, -1};
	private int line = Integer.MIN_VALUE;
	private int spawned;
	/** Pieces read left to right in rows (the Flowing line setting) instead of scattering. */
	private boolean flow;
	private boolean hold = true;
	/** Where the camera was last frame, to carry the cards along while you glide. */
	private @Nullable Vec3 lastEye;
	private float lastYaw, lastPitch;
	/** Elytra mode is carrying the words this frame. */
	private boolean gliding;
	private @Nullable Sentence sentence;
	private int maxCards = MAX_CARDS;
	private float fade = FADE;
	/** How much further out than usual new cards float, and as much bigger, so they look the same size (the Distance setting). */
	private float away = 1;
	/** The line as given, to notice when it changes, and in capitals as shown. */
	private @Nullable String lineRaw, lineText;
	/** Whether new lines are written in capitals; a change shows from the next line on. */
	private boolean capitals;
	private List<int[]> lineChunks = List.of();
	private long rng = System.nanoTime();

	void clear() {
		cards.clear();
		line = Integer.MIN_VALUE;
		spawned = 0;
		lineRaw = null;
		lineText = null;
		sentence = null;
		recent[0] = recent[1] = -1;
	}

	/**
	 * Splits a line into the pieces that appear together: one word, or a short word with the next one, never more
	 * than two words. Returns each piece's first and past-last code point in the line.
	 */
	static List<int[]> chunks(String text) {
		int[] cps = text.codePoints().toArray();
		List<int[]> words = new ArrayList<>();
		for (int i = 0; i < cps.length; ) {
			if (cps[i] == ' ') {
				i++;
				continue;
			}
			int start = i;
			while (i < cps.length && cps[i] != ' ') {
				i++;
			}
			words.add(new int[] {start, i});
		}
		List<int[]> out = new ArrayList<>();
		for (int w = 0; w < words.size(); w++) {
			int[] word = words.get(w);
			if (w + 1 < words.size() && letters(cps, word) <= SHORT_WORD) {
				out.add(new int[] {word[0], words.get(++w)[1]});
			} else {
				out.add(word);
			}
		}
		return out;
	}

	private static int letters(int[] cps, int[] word) {
		int n = 0;
		for (int i = word[0]; i < word[1]; i++) {
			if (Character.isLetterOrDigit(cps[i])) {
				n++;
			}
		}
		return n;
	}

	/** How visible a card still is {@code seconds} after its words were over: slow at first, then quickly gone. */
	static float goneAlpha(float seconds) {
		return goneAlpha(seconds, FADE);
	}

	static float goneAlpha(float seconds, float fade) {
		float t = Mth.clamp(seconds / fade, 0, 1);
		return 1 - t * t;
	}

	/** How visible a card cut short {@code seconds} ago still is. */
	static float cutAlpha(float seconds) {
		return 1 - Mth.clamp(seconds / CUT, 0, 1);
	}

	/**
	 * How big a card is, times the setting: little words stay small and long ones get big, so they sit together like
	 * a collage instead of a row of equal labels. {@code roll} is a random number from 0 to 1.
	 */
	static float sizeFor(int letters, float roll) {
		float t = Mth.clamp((letters - SHORT_LETTERS) / (float) LONG_LETTERS, 0, 1);
		return Mth.lerp(t, SIZE_SHORT, SIZE_LONG) * (1 + (roll * 2 - 1) * SIZE_JITTER);
	}

	/**
	 * How many cards float at once, how many seconds sung ones take to fade, how far out new ones float and whether a
	 * line flows left to right (the World words settings).
	 */
	void tune(int maxCards, float fade, float away, boolean flow, boolean hold, boolean capitals) {
		this.away = Mth.clamp(away, 0.5f, 2);
		this.hold = hold;
		this.capitals = capitals;
		this.maxCards = Math.max(1, maxCards);
		this.fade = Math.max(0.1f, fade);
		if (flow != this.flow) {
			sentence = null;
		}
		this.flow = flow;
	}

	/** How much bigger than normal a card is {@code seconds} after it appeared: a quick pop that settles. */
	static float pop(float seconds) {
		float t = Mth.clamp(seconds / POP_TIME, 0, 1);
		float settle = 1 - (1 - t) * (1 - t) * (1 - t);
		return 1 + POP * (1 - settle);
	}

	/**
	 * The scatter: spots spread evenly over an oval, each one a golden angle around from the last, so neighbours in
	 * the list land on opposite sides. Degrees right of and above the middle.
	 */
	static float[][] slots() {
		float golden = Mth.PI * (3 - Mth.sqrt(5));
		float[][] s = new float[SLOTS][];
		for (int i = 0; i < SLOTS; i++) {
			float r = Mth.sqrt((i + 0.5f) / SLOTS);
			s[i] = new float[] {Mth.cos(i * golden) * r * SPREAD_X, Mth.sin(i * golden) * r * SPREAD_Y};
		}
		return s;
	}

	/**
	 * Spawns the cards that have started being sung, and lets the sung ones go.
	 *
	 * @param index     the line being sung, or -1 / a break when nothing is
	 * @param text      that line's text (null in a break)
	 * @param sungChars how many code points of the line have been sung so far
	 * @param over      whether the line has been over for a moment, so its last words can go too
	 * @param fixedYaw  the compass direction new words appear around, or NaN for wherever you look
	 */
	void update(int index, @Nullable String text, float sungChars, boolean over, float fixedYaw, Camera camera,
			@Nullable ClientLevel level, Font font, Style style, long now) {
		if (index != line || text != null && !text.equals(lineRaw)) {
			line = index;
			lineRaw = text;
			lineText = text == null || !capitals ? text : text.toUpperCase(Locale.ROOT);
			lineChunks = lineText == null ? List.of() : chunks(lineText);
			spawned = 0;
			sentence = null;
			if (hold) {
				letGo(now);
			}
		}
		int due = 0;
		while (due < lineChunks.size() && lineChunks.get(due)[0] <= sungChars) {
			due++;
		}
		if (due - spawned > CATCH_UP) {
			spawned = due - CATCH_UP;
		}
		for (; spawned < due; spawned++) {
			if (!hold) {
				letGo(now);
			}
			spawn(lineChunks.get(spawned), centre(fixedYaw, camera.yRot(), camera.xRot(), gliding), camera, level, font, style, now);
		}
		if (lineText == null || over) {
			letGo(now);
		}
		Vec3 eye = camera.position();
		for (Iterator<Card> it = cards.iterator(); it.hasNext(); ) {
			Card c = it.next();
			if (c.goneAt >= 0 && now - c.goneAt > fade * 1e9f || c.cutAt >= 0 && now - c.cutAt > CUT * 1e9f
					|| c.pos.distanceTo(eye) > GONE_DISTANCE) {
				it.remove();
			}
		}
	}

	/**
	 * Elytra mode: while you glide, ride or fly, every card moves and turns along with you, so the words travel with
	 * you instead of being left behind, and steering doesn't swing them (or the rest of the line) out of sight.
	 * Call once a frame, before {@link #update}.
	 */
	void glide(Vec3 eye, float yaw, float pitch, boolean gliding) {
		Vec3 moved = carry(lastEye, eye, gliding);
		this.gliding = gliding;
		float turn = Mth.wrapDegrees(yaw - lastYaw), tilt = pitch - lastPitch;
		lastEye = eye;
		lastYaw = yaw;
		lastPitch = pitch;
		if (moved == Vec3.ZERO) {
			return;
		}
		for (Card c : cards) {
			c.pos = eye.add(swing(c.pos.add(moved).subtract(eye), turn, tilt));
			c.face += turn;
		}
		if (sentence != null) {
			sentence.swing(turn, tilt);
		}
	}

	/** An offset from the eye turned with the view: {@code turn} degrees round and {@code tilt} degrees down. */
	static Vec3 swing(Vec3 offset, float turn, float tilt) {
		double far = offset.length();
		if (far < 1e-3 || turn == 0 && tilt == 0) {
			return offset;
		}
		float pitch = Mth.clamp(pitchOf(offset) + tilt, -SWING_PITCH, SWING_PITCH);
		return direction(yawOf(offset) + turn, pitch).scale(far);
	}

	/** How far the cards move with you this frame: as far as you went while travelling, otherwise nowhere. */
	static Vec3 carry(@Nullable Vec3 last, Vec3 eye, boolean gliding) {
		if (!gliding || last == null || last.distanceToSqr(eye) > GLIDE_JUMP * GLIDE_JUMP) {
			return Vec3.ZERO;
		}
		return eye.subtract(last);
	}

	/** Every card shown so far starts fading. */
	private void letGo(long now) {
		for (Card c : cards) {
			if (c.goneAt < 0) {
				c.goneAt = now;
			}
		}
	}

	/**
	 * Yaw and pitch new words gather round: where you look, or with a fixed direction that way, a little above the
	 * horizon, so you can look about and find them where you left them. While Elytra mode carries the words, always
	 * where you look: the fixed spot would be off to the side or behind as soon as you fly another way.
	 */
	static float[] centre(float fixedYaw, float yaw, float pitch, boolean gliding) {
		return gliding || Float.isNaN(fixedYaw) ? new float[] {yaw, pitch} : new float[] {fixedYaw, 0};
	}

	private void spawn(int[] chunk, float[] centre, Camera camera, @Nullable ClientLevel level, Font font, Style style,
			long now) {
		String piece = new String(lineText.codePoints().toArray(), chunk[0], chunk[1] - chunk[0]);
		Display d = Display.of(piece);
		float[] at = new float[d.cps().length + 1];
		for (int v = 1; v <= d.cps().length; v++) {
			at[v] = LyricsFont.width(font, style, new String(d.cps(), 0, v));
		}
		int letters = 0;
		for (int cp : d.cps()) {
			if (Character.isLetterOrDigit(cp)) {
				letters++;
			}
		}
		float size = SIZE * sizeFor(letters, next());
		Vec3 eye = camera.position();
		// Where the other cards are, as seen from here: yaw, pitch and half width/height, in degrees
		List<float[]> taken = new ArrayList<>(cards.size());
		for (Card c : cards) {
			Vec3 to = c.pos.subtract(eye);
			double far = to.length();
			if (far > 1e-3 && (c.goneAt < 0 || goneAlpha((now - c.goneAt) / 1e9f, fade) > 0.3f)) {
				taken.add(new float[] {yawOf(to), pitchOf(to), halfAngle(c.at[c.at.length - 1] * c.size / 2, far),
						halfAngle(5 * c.size, far)});
			}
		}
		float[] spot = flow ? follow(centre, at, size, taken) : scatter(centre, at, size, taken);
		Vec3 dir = direction(spot[0], spot[1]);
		// Step closer while the spot is inside a block, so the card doesn't hide in a wall
		float distance = spot[2] * away;
		while (level != null && distance > CLOSEST && !level.getBlockState(BlockPos.containing(eye.add(dir.scale(distance)))).isAir()) {
			distance -= 1;
		}
		Vec3 pos = eye.add(dir.scale(Math.max(CLOSEST, distance)));
		float turn = flow && sentence != null ? sentence.turn + (next() * 2 - 1) * SENTENCE_WOBBLE
				: Mth.lerp(next(), TURN_MIN, TURN_MAX) * (next() < 0.5f ? -1 : 1);
		float nod = (next() * 2 - 1) * NOD * (flow ? 0.5f : 1), lean = (next() * 2 - 1) * LEAN;
		cards.add(new Card(d, at, pos, spot[0] + turn, nod, lean, size * away, now));
		int kept = 0;
		for (int k = cards.size() - 1; k >= 0; k--) {
			Card c = cards.get(k);
			if (c.cutAt < 0 && ++kept > maxCards) {
				// A lit card over the limit fades like a sung one; one already fading goes quickly
				if (c.goneAt < 0) {
					c.goneAt = now;
				} else {
					c.cutAt = now;
				}
			}
		}
	}

	/**
	 * Yaw, pitch and distance for a card in the scatter: the free spot, of a few tried, that overlaps least. Later
	 * tries reach a little further out, so a crowded sky spreads wider instead of piling words up.
	 */
	private float[] scatter(float[] centre, float[] at, float size, List<float[]> taken) {
		float bestYaw = 0, bestPitch = 0, bestDistance = NEAR, bestCost = Float.MAX_VALUE;
		int bestSlot = -1;
		for (int t = 0; t < TRIES && bestCost > 0; t++) {
			int slot = (int) (next() * SLOTS);
			if (slot == recent[0] || slot == recent[1]) {
				continue;
			}
			float out = 1 + REACH * t / (TRIES - 1);
			float yaw = centre[0] - SPOTS[slot][0] * out + (next() * 2 - 1) * WOBBLE_X;
			float pitch = Mth.clamp(centre[1] - LIFT - SPOTS[slot][1] * out + (next() * 2 - 1) * WOBBLE_Y, -75, 45);
			float distance = Mth.lerp(next(), NEAR, FAR);
			float halfW = halfAngle(at[at.length - 1] * size / 2, distance) + PAD_X, halfH = halfAngle(5 * size, distance) + PAD_Y;
			float cost = 0;
			for (float[] o : taken) {
				float w = halfW + o[2] - Math.abs(Mth.wrapDegrees(yaw - o[0])), h = halfH + o[3] - Math.abs(pitch - o[1]);
				if (w > 0 && h > 0) {
					cost += w * h;
				}
			}
			if (cost < bestCost) {
				bestCost = cost;
				bestSlot = slot;
				bestYaw = yaw;
				bestPitch = pitch;
				bestDistance = distance;
			}
		}
		if (bestSlot < 0) { // every try hit a recent spot (unlikely); take the middle
			bestYaw = centre[0];
			bestPitch = centre[1] - LIFT;
		}
		recent[1] = recent[0];
		recent[0] = bestSlot;
		return new float[] {bestYaw, bestPitch, bestDistance};
	}

	/**
	 * Yaw, pitch and distance for the next piece of a flowing line. A line's first piece picks where the line goes
	 * (a little above and left of the middle, where it overlaps the fading words least); the rest follow it.
	 */
	private float[] follow(float[] centre, float[] at, float size, List<float[]> taken) {
		if (sentence == null) {
			float distance = Mth.lerp(next(), SENTENCE_NEAR, SENTENCE_FAR), bestCost = Float.MAX_VALUE;
			float halfW = Sentence.WIDTH / 2, halfH = halfAngle(10 * SIZE, distance) + PAD_Y;
			for (int t = 0; t < TRIES && bestCost > 0; t++) {
				float left = centre[0] - Sentence.WIDTH / 2 + (next() * 2 - 1) * SENTENCE_SHIFT_X;
				float top = Mth.clamp(centre[1] - LIFT + (next() * 2 - 1) * SENTENCE_SHIFT_Y, -70, 40);
				float cost = 0;
				for (float[] o : taken) {
					float w = halfW + o[2] - Math.abs(Mth.wrapDegrees(left + halfW - o[0])), h = halfH + o[3] - Math.abs(top + halfH / 2 - o[1]);
					if (w > 0 && h > 0) {
						cost += w * h;
					}
				}
				if (cost < bestCost) {
					bestCost = cost;
					sentence = new Sentence(left, top, distance, Mth.lerp(next(), 0, SENTENCE_TURN) * (next() < 0.5f ? -1 : 1));
				}
			}
		}
		float distance = sentence.distance * (1 + (next() * 2 - 1) * SENTENCE_DEPTH);
		float[] at2 = sentence.place(halfAngle(at[at.length - 1] * size / 2, distance), halfAngle(5 * size, distance),
				next() * SENTENCE_INDENT, (next() * 2 - 1) * SENTENCE_BOB);
		return new float[] {at2[0], at2[1], distance};
	}

	/**
	 * Where a flowing line's pieces go, in degrees: each one just right of the last, a little up or down, and on to
	 * a new row below once the row is full, like handwriting across the sky.
	 */
	static final class Sentence {
		/** How wide a row may get before the next piece starts a new one, in degrees. */
		static final float WIDTH = 26f;
		/** Space between pieces, in their own half heights (room for the pop-in), and between rows, in degrees. */
		static final float GAP = 1.6f, ROW_GAP = 0.4f;
		final float distance, turn;
		/** Where rows start, the pitch of the row being written (bigger is lower) and its right end so far. */
		float left, top, cursor;

		Sentence(float left, float top, float distance, float turn) {
			this.left = left;
			this.top = top;
			this.cursor = left;
			this.distance = distance;
			this.turn = turn;
		}

		/** Yaw and pitch of the next piece's middle, given its half size; {@code indent} and {@code bob} add a little chance. */
		float[] place(float halfW, float halfH, float indent, float bob) {
			float yaw = cursor + GAP * halfH + halfW;
			if (cursor > left && yaw + halfW > left + WIDTH) {
				top += 2 * halfH + ROW_GAP;
				yaw = left + indent + halfW;
			}
			cursor = yaw + halfW;
			return new float[] {yaw, top + bob};
		}

		/** Turns the rest of the line along with you (see {@link WorldWords#glide}). */
		void swing(float turn, float tilt) {
			left += turn;
			cursor += turn;
			top = Mth.clamp(top + tilt, -SWING_PITCH, SWING_PITCH);
		}
	}

	private static Vec3 direction(float yaw, float pitch) {
		return new Vec3(-Mth.sin(yaw * DEG) * Mth.cos(pitch * DEG), -Mth.sin(pitch * DEG), Mth.cos(yaw * DEG) * Mth.cos(pitch * DEG));
	}

	private static float yawOf(Vec3 v) {
		return (float) Math.toDegrees(Math.atan2(-v.x, v.z));
	}

	private static float pitchOf(Vec3 v) {
		return (float) -Math.toDegrees(Math.asin(Mth.clamp(v.y / v.length(), -1, 1)));
	}

	static float halfAngle(float half, double distance) {
		return (float) Math.toDegrees(Math.atan2(half, distance));
	}

	/** A number in [0, 1); cards only need to look scattered, not be reproducible. */
	private float next() {
		rng = rng * 6364136223846793005L + 1442695040888963407L;
		return (rng >>> 40) / (float) (1 << 24);
	}

	/**
	 * Draws the cards around the camera.
	 *
	 * @param rgb   the lyrics colour
	 * @param alpha overall opacity
	 * @param glow  glow strength from the settings, 0 for none
	 * @param day   how much it's daytime, 0 to 1; by day the shadow and glow go darker to stand out from the sky
	 * @param scale the size setting
	 * @param light fewer glow copies, of the same total strength, to save frames
	 */
	void render(PoseStack pose, SubmitNodeCollector out, Camera camera, Font.DisplayMode mode, Style style, int rgb, float alpha,
			float glow, float day, float scale, boolean light, long now) {
		int copies = light ? GLOW_LIGHT : GLOW_COPIES;
		Vec3 eye = camera.position();
		Vec3 look = direction(camera.yRot(), camera.xRot());
		Card sung = null;
		for (Card c : cards) {
			if (c.goneAt < 0 && c.cutAt < 0) {
				sung = c;
			}
		}
		for (Card c : cards) {
			float age = (now - c.born) / 1e9f;
			float gone = c.goneAt < 0 ? 0 : (now - c.goneAt) / 1e9f;
			float a = alpha * Mth.clamp(age / FADE_IN, 0, 1) * (c.goneAt < 0 ? 1 : goneAlpha(gone, fade))
					* (c.cutAt < 0 ? 1 : cutAlpha((now - c.cutAt) / 1e9f));
			if (a < 0.03f) {
				continue;
			}
			float x = (float) (c.pos.x - eye.x), z = (float) (c.pos.z - eye.z);
			float y = (float) (c.pos.y - eye.y) + Math.min(age * RISE, RISE_MAX);
			int n = c.display.cps().length;
			float grow = c.size * scale * pop(age);
			Vector3f centre = new Vector3f(x, y, z);
			if (!showing(centre, c.at[n] * grow / 2, grow, look)) {
				continue;
			}
			if (c.goneAt < 0) {
				LyricsInView.check(centre, c.at[n] * grow / 2, 5 * grow);
			}
			// A card seen from behind turns around, so its words never read mirrored
			float face = seenFromBehind(x, z, c.face) ? c.face + 180 : c.face;
			int shown = Mth.ceil(n * Math.min(1, age / TYPE));
			int colour = c.goneAt < 0 ? rgb : mix(rgb, DIM_RGB, DIM * Mth.clamp(gone / DIM_TIME, 0, 1));
			pose.pushPose();
			pose.translate(x, y, z);
			pose.mulPose(new Quaternionf().rotationY(Mth.PI - face * DEG).rotateX(c.nod * DEG).rotateZ(c.lean * DEG));
			pose.scale(grow, -grow, grow);
			float x0 = -c.at[n] / 2f, y0 = -CAP_MIDDLE;
			// A fading card loses its shadow sooner, so it melts into the sky instead of leaving a dark smudge
			float shade = a * shadowOpacity(day) * (c.goneAt < 0 ? 1 : goneAlpha(gone, fade));
			draw(pose, out, c, shown, style, SHADOW_RGB, shade, x0 + SHADOW_X, y0 + SHADOW_Y, SHADOW_Z, mode);
			float ring = a * GLOW_ALPHA * glow * glowFor(c == sung, c.goneAt < 0 ? -1 : gone) / 2 * GLOW_COPIES / copies;
			int halo = mix(colour, 0x000000, HALO_DAY * day);
			for (int k = 0; k < copies && ring * 255 >= 4; k++) {
				float turn = k * Mth.TWO_PI / copies + 0.5f;
				draw(pose, out, c, shown, style, halo, ring, x0 + Mth.cos(turn) * GLOW_RADIUS, y0 + Mth.sin(turn) * GLOW_RADIUS,
						GLOW_Z, mode);
			}
			draw(pose, out, c, shown, style, colour, a, x0, y0, FACE_Z, mode);
			pose.popPose();
		}
	}

	/**
	 * Whether any of a card could be on screen this frame, checked against the view the world was drawn with: a ball
	 * round it, big enough for the card however it's turned plus its glow and shadow. So cards just off the edge cost
	 * nothing, and a wide field of view doesn't cut off ones still in sight. Before the first frame, roughly whether
	 * it's ahead.
	 *
	 * @param halfW half the card's width, in blocks
	 * @param grow  blocks per text unit
	 */
	static boolean showing(Vector3f centre, float halfW, float grow, Vec3 look) {
		if (ScreenLayer.ready()) {
			float halfH = HALF_HEIGHT * grow;
			return ScreenLayer.ballInView(centre, Mth.sqrt(halfW * halfW + halfH * halfH) + EDGE * grow);
		}
		float far = centre.length();
		return far < 1e-3f || (centre.x * look.x + centre.y * look.y + centre.z * look.z) / far >= CULL;
	}

	/**
	 * How strongly a card glows, before the Glow setting: most on the words being sung, a little less on the rest of
	 * the line still up. Once a card is let go its glow goes out quickly as it greys, so fading words, a whole line of
	 * them at a time, cost no glow to draw.
	 *
	 * @param gone seconds since the card was let go, or a negative number while it's still lit
	 */
	static float glowFor(boolean sung, float gone) {
		if (gone < 0) {
			return sung ? GLOW_SUNG : GLOW_LIT;
		}
		return GLOW_LIT * (1 - Mth.clamp(gone / GLOW_OUT, 0, 1));
	}

	/** How dark the drop shadow is: stronger by day, when the sky behind the words is bright. */
	static float shadowOpacity(float day) {
		return Mth.lerp(Mth.clamp(day, 0, 1), SHADOW_NIGHT, SHADOW_DAY);
	}

	/**
	 * Whether a card turned to {@code face} degrees, standing {@code x, z} blocks from the eye, shows you its back.
	 * Its words read from the side its face points to: (sin face, 0, -cos face) after the turn used in render.
	 */
	static boolean seenFromBehind(float x, float z, float face) {
		return -x * Mth.sin(face * DEG) + z * Mth.cos(face * DEG) < 0;
	}

	/** Draws the card's typed-in letters once, in one colour; each unbroken run where it sits in the whole word. */
	private static void draw(PoseStack pose, SubmitNodeCollector out, Card c, int shown, Style style, int rgb, float alpha,
			float x0, float y0, float z, Font.DisplayMode mode) {
		int alphaByte = Math.round(alpha * 255);
		if (alphaByte < 4) { // text this faint would be drawn fully opaque
			return;
		}
		Display d = c.display;
		int n = d.cps().length;
		Style coloured = style.withColor(rgb);
		pose.pushPose();
		pose.translate(0, 0, z);
		for (int from = 0; from < n; ) {
			if (d.logical()[from] >= shown) {
				from++;
				continue;
			}
			int to = from + 1;
			while (to < n && d.logical()[to] < shown) {
				to++;
			}
			int a0 = from, a1 = to;
			FormattedCharSequence seq = sink -> {
				for (int v = a0; v < a1; v++) {
					if (!sink.accept(v, coloured, d.cps()[v])) {
						return false;
					}
				}
				return true;
			};
			out.submitText(pose, x0 + c.at[from], y0, seq, false, mode, LightTexture.FULL_BRIGHT, alphaByte << 24 | 0xFFFFFF, 0, 0);
			from = to;
		}
		pose.popPose();
	}

	private static int mix(int from, int to, float t) {
		int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}
}
