package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * A word with no clear place on its way in (you stand by a wall or a hillside) is lifted out above where it was meant to
 * be. It was lifted up to 8 blocks with no regard for the top of the screen or the other words: by a wall on the left a
 * flowing line's words climbed up its side one above the other, piled onto each other, and the last ones (the big word
 * too) went off the top of the screen. Seen in a tall window, where the line was kept on screen beside the kept-clear
 * middle, right where the test world's sand wall was; it happens on any screen.
 */
class WorldWordsLiftTest {
	private static final int SEEDS = 12;
	private static final double FOV = 70;
	private static final String LINE = "lanterns drift over the harbour while the slow tide carries every little light away into the morning";

	/** Open sky over flat ground at the feet (the eye is 1.62 above it). */
	private static final Predicate<Vec3> FLAT = pos -> pos.y < -1.62;
	/** The same, with a wall 6 blocks high a block to the left: nothing on the left is clear even 3 blocks out. */
	private static final Predicate<Vec3> WALL = pos -> pos.y < -1.62 || pos.x > 1 && pos.y < 4.4;

	/** A word as it lands: its line, the middle of its box and half its size in degrees, and how high its top gets as it floats up. */
	private record Landed(Object line, String text, float yaw, float pitch, float halfW, float halfH, float highest) {
		/** Its top where it lands. */
		float top() {
			return pitch - halfH;
		}
	}

	private static Object get(Object o, String name) throws ReflectiveOperationException {
		Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(o);
	}

	private static float pitchOf(Vec3 v) {
		return (float) Math.toDegrees(-Math.asin(v.y / v.length()));
	}

	/** The test line sung half a letter at a time, looking level (yaw 0, pitch 0) on a screen of this shape. */
	@SuppressWarnings("unchecked")
	private static List<Landed> sing(Predicate<Vec3> world, boolean flow, boolean middle, float aspect, long seed) throws Exception {
		WorldWords.measureHook = t -> t.length() * 4.0;
		WorldWords.buriedHook = world;
		try {
			ScreenLayer.capture(new Matrix4f(), new Matrix4f().perspective((float) Math.toRadians(FOV), aspect, 0.05f, 100f));
			WorldWords words = new WorldWords();
			words.seed(seed);
			// The settings' defaults: 7 words, 1.2 seconds to fade, Distance, Tilt and Float up 1, One big word on
			words.tune(7, 1.2f, 1, 1, 1, flow, true, false, 1);
			words.clearMiddle(middle);
			words.players(false, false);
			Camera camera = new Camera();
			Field cardsField = WorldWords.class.getDeclaredField("cards");
			cardsField.setAccessible(true);
			Map<Object, Boolean> seen = new IdentityHashMap<>();
			List<Landed> out = new ArrayList<>();
			for (int k = 0; k <= 2 * LINE.length() + 2; k++) {
				words.update(0, LINE, k * 0.5f, false, 0f, 0f, camera, null, null, net.minecraft.network.chat.Style.EMPTY, (k + 1) * 50_000_000L);
				for (Object card : (List<Object>) cardsField.get(words)) {
					if (seen.put(card, true) != null) {
						continue;
					}
					Vec3 p = (Vec3) get(card, "pos");
					float size = (float) get(card, "size");
					float[] at = (float[]) get(card, "at");
					int[] cps = ((LyricsRenderer.Display) get(card, "display")).cps();
					float halfH = WorldWords.halfAngle(5 * size, p.length());
					// It floats up by as much as a block (times its lift) over its life
					Vec3 risen = p.add(0, (float) get(card, "lift"), 0);
					out.add(new Landed(get(card, "of"), new String(cps, 0, cps.length), (float) Math.toDegrees(Math.atan2(-p.x, p.z)), pitchOf(p),
							WorldWords.halfAngle(at[at.length - 1] * size / 2, Math.sqrt(p.x * p.x + p.z * p.z)), halfH,
							Math.min(pitchOf(p), pitchOf(risen)) - halfH));
				}
			}
			return out;
		} finally {
			WorldWords.measureHook = null;
			WorldWords.buriedHook = null;
			var captured = ScreenLayer.class.getDeclaredField("captured");
			captured.setAccessible(true);
			captured.setBoolean(null, false);
		}
	}

	/** How much of the smaller of two words' boxes the other covers, 0 to 1. */
	private static float covered(Landed a, Landed b) {
		float w = Math.min(a.halfW() + b.halfW() - Math.abs(a.yaw() - b.yaw()), 2 * Math.min(a.halfW(), b.halfW()));
		float h = Math.min(a.halfH() + b.halfH() - Math.abs(a.pitch() - b.pitch()), 2 * Math.min(a.halfH(), b.halfH()));
		return w <= 0 || h <= 0 ? 0 : w * h / (4 * Math.min(a.halfW() * a.halfH(), b.halfW() * b.halfH()));
	}

	/**
	 * What is wrong with the words, every seed: a word whose top reaches higher than {@code highest} degrees above the view
	 * (a flowing line's floated up, as its rows keep to; a scattered word's where it lands, as the scatter's own highest
	 * spots float off the top as they fade), and two words of one flowing line drawn over each other.
	 */
	private static String wrong(Predicate<Vec3> world, boolean flow, boolean middle, float aspect, float highest) throws Exception {
		StringBuilder bad = new StringBuilder();
		int words = 0;
		for (long seed = 1; seed <= SEEDS; seed++) {
			List<Landed> landed = sing(world, flow, middle, aspect, seed);
			words += landed.size();
			for (int i = 0; i < landed.size(); i++) {
				Landed a = landed.get(i);
				float top = flow ? a.highest() : a.top();
				if (top < -highest) {
					bad.append(String.format("seed %d '%s' at %.1f/%.1f reaches %.1f, over %.1f%n", seed, a.text(), a.yaw(), a.pitch(), top, -highest));
				}
				for (int j = 0; j < i; j++) {
					Landed b = landed.get(j);
					if (flow && a.line() != null && a.line() == b.line() && covered(a, b) > 0.2f) {
						bad.append(String.format("seed %d '%s' lies over '%s' (%.0f%%)%n", seed, a.text(), b.text(), 100 * covered(a, b)));
					}
				}
			}
		}
		assertTrue(words > 100, "words landed: " + words);
		return bad.toString();
	}

	@Test
	void inOpenSkyAFlowingLinesWordsStayUnderTheTopAndOffEachOther() throws Exception {
		for (float aspect : new float[] {0.8f, 1f, 16 / 9f}) {
			for (boolean middle : new boolean[] {true, false}) {
				String bad = wrong(FLAT, true, middle, aspect, WorldWords.VIEW_UP);
				assertTrue(bad.isEmpty(), "aspect " + aspect + " middle " + middle + ":\n" + bad);
			}
		}
	}

	@Test
	void byAWallAFlowingLinesLiftedWordsStayUnderTheTopAndOffEachOther() throws Exception {
		for (float aspect : new float[] {0.8f, 1f, 16 / 9f}) {
			for (boolean middle : new boolean[] {true, false}) {
				String bad = wrong(WALL, true, middle, aspect, WorldWords.VIEW_UP);
				assertTrue(bad.isEmpty(), "aspect " + aspect + " middle " + middle + ":\n" + bad);
			}
		}
	}

	@Test
	void scatteredWordsLandOnTheScreenInOpenSkyAndByAWall() throws Exception {
		// The screen's top at FOV 70
		for (Predicate<Vec3> world : List.of(FLAT, WALL)) {
			for (float aspect : new float[] {0.8f, 16 / 9f}) {
				String bad = wrong(world, false, true, aspect, (float) FOV / 2);
				assertTrue(bad.isEmpty(), (world == FLAT ? "open sky" : "by a wall") + ", aspect " + aspect + ":\n" + bad);
			}
		}
	}
}
