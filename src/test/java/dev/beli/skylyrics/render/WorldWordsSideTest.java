package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * The World layout's words land across the screen's width as it really is. They used to be spread as if every screen
 * were 16:9: up to 40 degrees either side of where you look for a flowing line's rows, and the scatter's spots out to
 * about 33 degrees. A tall or square window (a window tiled to half the screen, 800x1000, 1000x1000) shows less than
 * that at the usual FOV, so the words at the sides came up off its left and right edges.
 */
class WorldWordsSideTest {
	private static final int SEEDS = 12;
	private static final double FOV = 70;

	/** Each word as it first lands: how far its middle is from where you look, and half its width, in degrees. */
	private record Landed(float yaw, float half, String text) {
	}

	/** A long line sung half a letter at a time, looking at the horizon (yaw 0), on a screen of this shape (0: none captured). */
	@SuppressWarnings("unchecked")
	private static List<Landed> sing(boolean flow, float aspect, long seed) throws Exception {
		WorldWords.measureHook = t -> t.length() * 4.0;
		WorldWords.buriedHook = pos -> false;
		try {
			if (aspect > 0) {
				ScreenLayer.capture(new Matrix4f(), new Matrix4f().perspective((float) Math.toRadians(FOV), aspect, 0.05f, 100f));
			}
			WorldWords words = new WorldWords();
			words.seed(seed);
			words.tune(30, 6, 1, 1, 1, flow, true, false, 1);
			words.players(false, false);
			Camera camera = new Camera();
			StringBuilder text = new StringBuilder();
			for (int w = 0; w < 34; w++) {
				text.append(w == 0 ? "" : " ").append(w % 3 == 0 ? "lanterns" : w % 3 == 1 ? "sky" : "over").append(w);
			}
			String line = text.toString();
			Field cardsField = WorldWords.class.getDeclaredField("cards");
			cardsField.setAccessible(true);
			Map<Object, Landed> seen = new IdentityHashMap<>();
			List<Landed> out = new ArrayList<>();
			int length = line.length();
			for (int k = 0; k <= 2 * length + 2; k++) {
				words.update(0, line, k * 0.5f, false, 0f, 0f, camera, null, null, net.minecraft.network.chat.Style.EMPTY, (k + 1) * 50_000_000L);
				for (Object card : (List<Object>) cardsField.get(words)) {
					if (seen.containsKey(card)) {
						continue;
					}
					Class<?> c = card.getClass();
					Field pos = c.getDeclaredField("pos"), at = c.getDeclaredField("at"), size = c.getDeclaredField("size"), disp = c.getDeclaredField("display");
					for (Field f : new Field[] {pos, at, size, disp}) {
						f.setAccessible(true);
					}
					Vec3 p = (Vec3) pos.get(card);
					float[] a = (float[]) at.get(card);
					double flat = Math.sqrt(p.x * p.x + p.z * p.z);
					int[] cps = ((LyricsRenderer.Display) disp.get(card)).cps();
					Landed landed = new Landed((float) Math.toDegrees(Math.atan2(-p.x, p.z)),
							WorldWords.halfAngle(a[a.length - 1] * size.getFloat(card) / 2, flat), new String(cps, 0, cps.length));
					seen.put(card, landed);
					out.add(landed);
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

	/** Half the screen's width in degrees, for a level look: a point {@code y} degrees to the side sits tan(y) across. */
	private static double halfWide(float aspect) {
		return Math.toDegrees(Math.atan(Math.tan(Math.toRadians(FOV / 2)) * aspect));
	}

	/** The words of every seed that reach past the screen's sides, and how many words landed altogether. */
	private static String offTheSides(boolean flow, float aspect, int[] count) throws Exception {
		double edge = halfWide(aspect);
		StringBuilder bad = new StringBuilder();
		for (long seed = 1; seed <= SEEDS; seed++) {
			for (Landed w : sing(flow, aspect, seed)) {
				count[0]++;
				double reach = Math.abs(w.yaw()) + w.half();
				if (reach > edge) {
					bad.append(String.format("seed %d '%s' at %.1f reaches %.1f of %.1f%n", seed, w.text(), w.yaw(), reach, edge));
				}
			}
		}
		return bad.toString();
	}

	@Test
	void onATallWindowTheScatteredWordsStayInsideTheScreensWidth() throws Exception {
		int[] count = {0};
		// 800x1000 at FOV 70: 29.2 degrees either side, where a 16:9 screen shows 51
		String bad = offTheSides(false, 0.8f, count);
		assertTrue(count[0] > 100, "words landed: " + count[0]);
		assertTrue(bad.isEmpty(), "scattered words run off the side:\n" + bad);
	}

	@Test
	void onATallWindowAFlowingLinesRowsStayInsideTheScreensWidth() throws Exception {
		int[] count = {0};
		String bad = offTheSides(true, 0.8f, count);
		assertTrue(count[0] > 100, "words landed: " + count[0]);
		assertTrue(bad.isEmpty(), "a flowing line's words run off the side:\n" + bad);
	}

	@Test
	void onASquareWindowTheWordsStayInsideTheScreensWidth() throws Exception {
		int[] count = {0};
		// 1000x1000, or a window tiled to half of a 16:10 screen: 35 degrees either side
		String bad = offTheSides(false, 1f, count) + offTheSides(true, 1f, count);
		assertTrue(bad.isEmpty(), "words run off the side:\n" + bad);
	}

	@Test
	void theRoomEitherSideComesFromTheScreensShape() {
		float wide = (float) (Math.tan(Math.toRadians(FOV / 2)) * 16 / 9), tall = (float) (Math.tan(Math.toRadians(FOV / 2)) * 0.8);
		assertEquals(halfWide(16 / 9f) - WorldWords.SIDE_MARGIN, WorldWords.sideView(wide), 1e-3);
		assertEquals(WorldWords.VIEW_HALF, WorldWords.rowSide(WorldWords.sideView(wide)), "a 16:9 screen keeps the rows as they were");
		assertEquals(halfWide(0.8f) - WorldWords.SIDE_MARGIN, WorldWords.rowSide(WorldWords.sideView(tall)), 1e-3);
		assertEquals(Float.POSITIVE_INFINITY, WorldWords.sideView(0), "nothing captured yet: no limit");
		assertEquals(WorldWords.MIN_SIDE, WorldWords.rowSide(1), "a row always holds a word or two");
		// A word 4 degrees wide either way at 30 to the right of 350, on a screen 27 either way, comes in to 23
		assertEquals(373, WorldWords.inSide(380, 350, 4, 27), 1e-3);
		assertEquals(327, WorldWords.inSide(320, 350, 4, 27), 1e-3);
		assertEquals(360, WorldWords.inSide(360, 350, 4, 27), 1e-3, "one that fits stays");
		assertEquals(350, WorldWords.inSide(380, 350, 30, 27), 1e-3, "one wider than the screen goes in the middle");
	}

	/** On a 16:9 screen at the usual FOV, and an ultrawide one, the words land exactly where they did with no screen known. */
	@Test
	void onAWideScreenTheWordsLandWhereTheyAlwaysDid() throws Exception {
		for (float aspect : new float[] {16 / 9f, 21 / 9f, 32 / 9f}) {
			for (boolean flow : new boolean[] {false, true}) {
				for (long seed = 1; seed <= 4; seed++) {
					assertEquals(sing(flow, 0, seed), sing(flow, aspect, seed), "aspect " + aspect + " flow " + flow + " seed " + seed);
				}
			}
		}
	}
}
