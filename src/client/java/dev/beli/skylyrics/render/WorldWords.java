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
 * appeared (drifting slowly upwards) while you move around it; once the next words come, it greys and fades away
 * over a couple of seconds. These are drawn in the world pass on purpose: each card has its own place and turn,
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
	private static final float SPREAD_X = 25f, SPREAD_Y = 15f;
	/** Spots in the scatter, and how many of the last used ones a new card avoids. */
	static final int SLOTS = 12;
	private static final int RECENT = 2;
	/** How far a card may land from its spot, in degrees, so the pattern doesn't look like a grid. */
	private static final float WOBBLE_X = 3.5f, WOBBLE_Y = 3f;
	/** Spots tried for each new card; the one overlapping the other cards least wins. */
	private static final int TRIES = 24;
	/** Room kept around each card when judging overlap, in degrees. */
	private static final float PAD_X = 2f, PAD_Y = 1.7f;
	/** Cards turn sideways by between these many degrees, and nod and lean a little, so they don't look pasted on. */
	private static final float TURN_MIN = 10f, TURN_MAX = 30f, NOD = 7f, LEAN = 4f;
	/** Blocks per text unit: capital letters end up about a third of a block tall. */
	private static final float SIZE = 0.046f;
	/** Each card's size, times the setting. */
	private static final float SIZE_MIN = 0.85f, SIZE_MAX = 1.3f;
	/** At most this many cards at once; the oldest goes first. */
	private static final int MAX_CARDS = 7;
	/** After a jump in the song, at most this many of the skipped pieces are spawned (the latest ones). */
	private static final int CATCH_UP = 2;
	/** Once its words are over, a card fades out over this many seconds. */
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
	/** Cards further from where you look than this (cosine of the angle) aren't drawn at all. */
	private static final float CULL = 0.3f;
	/** Cards this far away are dropped, in blocks. */
	private static final float GONE_DISTANCE = 48f;
	/** Drop shadow: offset down-right in text units, colour and opacity. */
	private static final float SHADOW_X = 0.7f, SHADOW_Y = 0.9f, SHADOW_ALPHA = 0.55f;
	private static final int SHADOW_RGB = 0x05040C;
	/** Soft glow: copies of the word around it, how far out (text units) and how strong at full glow. */
	private static final int GLOW_COPIES = 3;
	private static final float GLOW_RADIUS = 1.1f, GLOW_ALPHA = 0.4f;
	/** Front to back, in text units, so the layers don't flicker against each other. */
	private static final float SHADOW_Z = -0.5f, GLOW_Z = 0.1f, FACE_Z = 0.3f;
	/** From the top of a row of text to the middle of its capitals. */
	private static final float CAP_MIDDLE = 3.8f;
	/** Words with at most this many letters lean on the next word ("I'M HIGH", "GET OUT"). */
	static final int SHORT_WORD = 3;
	/** The scatter's spots, as degrees right of and above its middle. */
	private static final float[][] SPOTS = slots();

	/** One card: where it stands, what it shows, and how it's turned. */
	private static final class Card {
		final Display display;
		final float[] at;
		final Vec3 pos;
		final float face, nod, lean, size;
		final long born;
		long goneAt = -1;

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
	/** The line as given, to notice when it changes, and in capitals as shown. */
	private @Nullable String lineRaw, lineText;
	private List<int[]> lineChunks = List.of();
	private long rng = System.nanoTime();

	void clear() {
		cards.clear();
		line = Integer.MIN_VALUE;
		spawned = 0;
		lineRaw = null;
		lineText = null;
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
		float t = Mth.clamp(seconds / FADE, 0, 1);
		return 1 - t * t;
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
	 */
	void update(int index, @Nullable String text, float sungChars, boolean over, Camera camera, @Nullable ClientLevel level,
			Font font, Style style, long now) {
		if (index != line || text != null && !text.equals(lineRaw)) {
			line = index;
			lineRaw = text;
			lineText = text == null ? null : text.toUpperCase(Locale.ROOT);
			lineChunks = lineText == null ? List.of() : chunks(lineText);
			spawned = 0;
		}
		int due = 0;
		while (due < lineChunks.size() && lineChunks.get(due)[0] <= sungChars) {
			due++;
		}
		if (due - spawned > CATCH_UP) {
			spawned = due - CATCH_UP;
		}
		for (; spawned < due; spawned++) {
			letGo(now);
			spawn(lineChunks.get(spawned), camera, level, font, style, now);
		}
		if (lineText == null || over) {
			letGo(now);
		}
		Vec3 eye = camera.position();
		for (Iterator<Card> it = cards.iterator(); it.hasNext(); ) {
			Card c = it.next();
			if (c.goneAt >= 0 && now - c.goneAt > FADE * 1e9f || c.pos.distanceTo(eye) > GONE_DISTANCE) {
				it.remove();
			}
		}
	}

	/** Every card shown so far starts fading. */
	private void letGo(long now) {
		for (Card c : cards) {
			if (c.goneAt < 0) {
				c.goneAt = now;
			}
		}
	}

	private void spawn(int[] chunk, Camera camera, @Nullable ClientLevel level, Font font, Style style, long now) {
		String piece = new String(lineText.codePoints().toArray(), chunk[0], chunk[1] - chunk[0]);
		Display d = Display.of(piece);
		float[] at = new float[d.cps().length + 1];
		for (int v = 1; v <= d.cps().length; v++) {
			at[v] = LyricsFont.width(font, style, new String(d.cps(), 0, v));
		}
		float size = SIZE * Mth.lerp(next(), SIZE_MIN, SIZE_MAX);
		Vec3 eye = camera.position();
		// Where the other cards are, as seen from here: yaw, pitch and half width/height, in degrees
		List<float[]> taken = new ArrayList<>(cards.size());
		for (Card c : cards) {
			Vec3 to = c.pos.subtract(eye);
			double far = to.length();
			if (far > 1e-3 && (c.goneAt < 0 || goneAlpha((now - c.goneAt) / 1e9f) > 0.3f)) {
				taken.add(new float[] {yawOf(to), pitchOf(to), halfAngle(c.at[c.at.length - 1] * c.size / 2, far),
						halfAngle(5 * c.size, far)});
			}
		}
		float bestYaw = 0, bestPitch = 0, bestDistance = NEAR, bestCost = Float.MAX_VALUE;
		int bestSlot = -1;
		for (int t = 0; t < TRIES && bestCost > 0; t++) {
			int slot = (int) (next() * SLOTS);
			if (slot == recent[0] || slot == recent[1]) {
				continue;
			}
			float yaw = camera.yRot() - SPOTS[slot][0] + (next() * 2 - 1) * WOBBLE_X;
			float pitch = Mth.clamp(camera.xRot() - LIFT - SPOTS[slot][1] + (next() * 2 - 1) * WOBBLE_Y, -75, 45);
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
			bestYaw = camera.yRot();
			bestPitch = camera.xRot() - LIFT;
		}
		recent[1] = recent[0];
		recent[0] = bestSlot;
		Vec3 dir = direction(bestYaw, bestPitch);
		// Step closer while the spot is inside a block, so the card doesn't hide in a wall
		float distance = bestDistance;
		while (level != null && distance > CLOSEST && !level.getBlockState(BlockPos.containing(eye.add(dir.scale(distance)))).isAir()) {
			distance -= 1;
		}
		Vec3 pos = eye.add(dir.scale(Math.max(CLOSEST, distance)));
		float turn = Mth.lerp(next(), TURN_MIN, TURN_MAX) * (next() < 0.5f ? -1 : 1);
		float nod = (next() * 2 - 1) * NOD, lean = (next() * 2 - 1) * LEAN;
		cards.add(new Card(d, at, pos, bestYaw + turn, nod, lean, size, now));
		if (cards.size() > MAX_CARDS) {
			cards.remove(0);
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

	private static float halfAngle(float half, double distance) {
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
	 * @param scale the size setting
	 */
	void render(PoseStack pose, SubmitNodeCollector out, Camera camera, Font.DisplayMode mode, Style style, int rgb, float alpha,
			float glow, float scale, long now) {
		Vec3 eye = camera.position();
		Vec3 look = direction(camera.yRot(), camera.xRot());
		for (Card c : cards) {
			float age = (now - c.born) / 1e9f;
			float gone = c.goneAt < 0 ? 0 : (now - c.goneAt) / 1e9f;
			float a = alpha * Mth.clamp(age / FADE_IN, 0, 1) * (c.goneAt < 0 ? 1 : goneAlpha(gone));
			if (a < 0.03f) {
				continue;
			}
			float x = (float) (c.pos.x - eye.x), z = (float) (c.pos.z - eye.z);
			float y = (float) (c.pos.y - eye.y) + Math.min(age * RISE, RISE_MAX);
			float far = Mth.sqrt(x * x + y * y + z * z);
			if (far > 1e-3f && (x * look.x + y * look.y + z * look.z) / far < CULL) {
				continue;
			}
			int n = c.display.cps().length;
			float grow = c.size * scale * pop(age);
			if (c.goneAt < 0) {
				LyricsInView.check(new Vector3f(x, y, z), c.at[n] * grow / 2, 5 * grow);
			}
			// A card seen from behind turns around, so its words never read mirrored
			float face = c.face;
			if (-x * Mth.sin(face * DEG) + z * Mth.cos(face * DEG) > 0) {
				face += 180;
			}
			int shown = Mth.ceil(n * Math.min(1, age / TYPE));
			int colour = c.goneAt < 0 ? rgb : mix(rgb, DIM_RGB, DIM * Mth.clamp(gone / DIM_TIME, 0, 1));
			pose.pushPose();
			pose.translate(x, y, z);
			pose.mulPose(new Quaternionf().rotationY(Mth.PI - face * DEG).rotateX(c.nod * DEG).rotateZ(c.lean * DEG));
			pose.scale(grow, -grow, grow);
			float x0 = -c.at[n] / 2f, y0 = -CAP_MIDDLE;
			draw(pose, out, c, shown, style, SHADOW_RGB, a * SHADOW_ALPHA, x0 + SHADOW_X, y0 + SHADOW_Y, SHADOW_Z, mode);
			float ring = a * GLOW_ALPHA * glow * (c.goneAt < 0 ? 1.3f : 0.8f) / 2;
			for (int k = 0; k < GLOW_COPIES && ring * 255 >= 4; k++) {
				float turn = k * Mth.TWO_PI / GLOW_COPIES + 0.5f;
				draw(pose, out, c, shown, style, colour, ring, x0 + Mth.cos(turn) * GLOW_RADIUS, y0 + Mth.sin(turn) * GLOW_RADIUS,
						GLOW_Z, mode);
			}
			draw(pose, out, c, shown, style, colour, a, x0, y0, FACE_Z, mode);
			pose.popPose();
		}
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
