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
 * The "words in the world" layout: every word or two of a line pops up as it's sung, on its own upright card a few
 * blocks in front of where you were looking, and stays at that spot while you move around it. Older cards fade as
 * newer ones arrive, so only the last few are readable. These are drawn in the world pass on purpose: each card
 * has its own place and turn, which the flat screen layer can't show.
 */
final class WorldWords {
	private static final float DEG = Mth.DEG_TO_RAD;
	/** Cards appear this far from your eyes, in blocks. */
	private static final float NEAR = 4f, FAR = 8f;
	/** Never closer than this, even when blocks are in the way. */
	private static final float CLOSEST = 2.5f;
	/** Left/right spread around where you look, and how far below and above the crosshair, in degrees. */
	private static final float SPREAD = 18f, BELOW = 3f, ABOVE = 20f;
	/** Cards look straight at you when they appear, give or take this many degrees (turn and tilt). */
	private static final float TURN_JITTER = 10f, ROLL_JITTER = 6f;
	/** Blocks per text unit: capital letters end up about 0.27 blocks tall. */
	private static final float SIZE = 0.035f;
	/** Each card's size varies by this much either way. */
	private static final float SIZE_JITTER = 0.12f;
	/** Cards are dropped once they're this old (in chunks) or this far away (blocks). */
	private static final float GONE_AGE = 3f, GONE_DISTANCE = 48f;
	/** How fast the shown age catches up with the real one, per second: older cards fade over about 0.3 s. */
	private static final float AGE_SPEED = 3.5f;
	/** A new card fades and grows in over this many seconds. */
	private static final float APPEAR = 0.18f;
	/** Hard drop shadow: offset down-right in text units (about 1/8 of a capital) and its opacity. */
	private static final float SHADOW_OFFSET = 0.9f, SHADOW_ALPHA = 0.3f;
	/** From the top of a row of text to the middle of its capitals. */
	private static final float CAP_MIDDLE = 3.8f;
	/** Behind the text, in text units, so the shadow doesn't flicker against it. */
	private static final float BEHIND = 0.35f;
	/** Words with at most this many letters lean on the next word ("I'M HIGH", "GET OUT"). */
	static final int SHORT_WORD = 3;
	/** How many cards a line may spawn at once after a jump in the song; older ones would fade right away. */
	private static final int MAX_CATCH_UP = 3;

	/** One card: where it stands, which part of which line it shows, and how it's turned. */
	private static final class Card {
		final int line, order, from, to;
		final Display display;
		final float[] at;
		final Vec3 pos;
		final float yaw, roll, size;
		final long born;
		float shownAge;

		Card(int line, int order, int from, int to, Display display, float[] at, Vec3 pos, float yaw, float roll, float size,
				long born) {
			this.line = line;
			this.order = order;
			this.from = from;
			this.to = to;
			this.display = display;
			this.at = at;
			this.pos = pos;
			this.yaw = yaw;
			this.roll = roll;
			this.size = size;
			this.born = born;
		}
	}

	private final List<Card> cards = new ArrayList<>();
	private int line = Integer.MIN_VALUE;
	private int spawned;
	private int order;
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

	/** How visible a card is when {@code age} newer cards came after it: 1, then 0.7, 0.5 and gone. */
	static float ageAlpha(float age) {
		if (age <= 0) {
			return 1;
		}
		if (age < 1) {
			return Mth.lerp(age, 1, 0.7f);
		}
		if (age < 2) {
			return Mth.lerp(age - 1, 0.7f, 0.5f);
		}
		return Math.max(0, Mth.lerp(age - 2, 0.5f, 0f));
	}

	/**
	 * Spawns the cards that have started being sung.
	 *
	 * @param index     the line being sung, or -1 / a break when nothing is
	 * @param text      that line's text (null in a break)
	 * @param sungChars how many code points of the line have been sung so far
	 */
	void update(int index, @Nullable String text, float sungChars, Camera camera, @Nullable ClientLevel level, Font font,
			Style style, long now, float dt) {
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
		if (due - spawned > MAX_CATCH_UP) {
			spawned = due - MAX_CATCH_UP;
		}
		for (; spawned < due; spawned++) {
			spawn(lineChunks.get(spawned), camera, level, font, style, now);
		}

		boolean quiet = lineText == null;
		for (Iterator<Card> it = cards.iterator(); it.hasNext(); ) {
			Card c = it.next();
			// In a break between lines everything fades away
			float target = quiet ? GONE_AGE : order - 1 - c.order;
			c.shownAge = Math.min(target, c.shownAge + dt * AGE_SPEED);
			if (c.shownAge < target - 1) {
				c.shownAge = target - 1; // don't lag more than one step behind after a jump
			}
			if (c.shownAge >= GONE_AGE || c.pos.distanceTo(camera.position()) > GONE_DISTANCE) {
				it.remove();
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
		float yaw = camera.yRot() + (next() * 2 - 1) * SPREAD;
		float pitch = Mth.clamp(camera.xRot() + BELOW - next() * (BELOW + ABOVE), -60, 40);
		Vec3 dir = new Vec3(-Mth.sin(yaw * DEG) * Mth.cos(pitch * DEG), -Mth.sin(pitch * DEG), Mth.cos(yaw * DEG) * Mth.cos(pitch * DEG));
		Vec3 eye = camera.position();
		float distance = NEAR + next() * (FAR - NEAR);
		// Step closer while the spot is inside a block, so the card doesn't hide in a wall
		while (level != null && distance > CLOSEST && !level.getBlockState(BlockPos.containing(eye.add(dir.scale(distance)))).isAir()) {
			distance -= 1;
		}
		Vec3 pos = eye.add(dir.scale(Math.max(CLOSEST, distance)));
		float turn = yaw + (next() * 2 - 1) * TURN_JITTER;
		float roll = (next() * 2 - 1) * ROLL_JITTER;
		float size = SIZE * (1 + (next() * 2 - 1) * SIZE_JITTER);
		cards.add(new Card(line, order++, chunk[0], chunk[1], d, at, pos, turn, roll, size, now));
	}

	/** A number in [0, 1); cards only need to look scattered, not be reproducible. */
	private float next() {
		rng = rng * 6364136223846793005L + 1442695040888963407L;
		return (rng >>> 40) / (float) (1 << 24);
	}

	/**
	 * Draws the cards around the camera.
	 *
	 * @param sungChars letters of the current line sung so far, for typing its newest card out letter by letter
	 */
	void render(PoseStack pose, SubmitNodeCollector out, Camera camera, Font.DisplayMode mode, Style style, int rgb,
			float alpha, float sungChars, long now) {
		Vec3 eye = camera.position();
		for (Card c : cards) {
			float in = Mth.clamp((now - c.born) / 1e9f / APPEAR, 0, 1);
			float a = alpha * ageAlpha(c.shownAge) * in;
			if (a <= 0.01f) {
				continue;
			}
			int shown = c.line == line ? (int) Math.ceil(sungChars - c.from) : Integer.MAX_VALUE;
			if (shown <= 0) {
				continue;
			}
			float grow = c.size * (0.85f + 0.15f * (1 - (1 - in) * (1 - in)));
			int letters = c.display.cps().length;
			LyricsInView.check(new Vector3f((float) (c.pos.x - eye.x), (float) (c.pos.y - eye.y), (float) (c.pos.z - eye.z)),
					c.at[letters] * grow / 2, 5 * grow);
			pose.pushPose();
			pose.translate((float) (c.pos.x - eye.x), (float) (c.pos.y - eye.y), (float) (c.pos.z - eye.z));
			pose.mulPose(new Quaternionf().rotationY(Mth.PI - c.yaw * DEG).rotateZ(c.roll * DEG));
			pose.scale(grow, -grow, grow);
			Display d = c.display;
			int n = d.cps().length;
			float x0 = -c.at[n] / 2f, y0 = -CAP_MIDDLE;
			int shadow = Math.round(a * SHADOW_ALPHA * 255) << 24;
			int face = Math.max(4, Math.round(a * 255)) << 24 | 0xFFFFFF;
			// Letters show as they're sung; draw each unbroken run of shown letters where it sits in the whole word
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
						if (!sink.accept(v, style.withColor(rgb), d.cps()[v])) {
							return false;
						}
					}
					return true;
				};
				float x = x0 + c.at[from];
				if (shadow >>> 24 >= 2) {
					pose.pushPose();
					pose.translate(0, 0, -BEHIND);
					FormattedCharSequence dark = sink -> {
						for (int v = a0; v < a1; v++) {
							if (!sink.accept(v, style.withColor(0), d.cps()[v])) {
								return false;
							}
						}
						return true;
					};
					out.submitText(pose, x + SHADOW_OFFSET, y0 + SHADOW_OFFSET, dark, false, mode, LightTexture.FULL_BRIGHT, shadow, 0, 0);
					pose.popPose();
				}
				out.submitText(pose, x, y0, seq, false, mode, LightTexture.FULL_BRIGHT, face, 0, 0);
				from = to;
			}
			pose.popPose();
		}
	}
}
