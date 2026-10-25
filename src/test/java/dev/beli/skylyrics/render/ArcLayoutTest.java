package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ArcLayoutTest {
	private static final boolean[] NO_PAUSE = new boolean[20];

	private static float[] words(float... widths) {
		return widths;
	}

	@Test
	void shortLineStaysOnOneRow() {
		assertArrayEquals(new int[] {0}, ArcLayout.rows(words(20, 10, 30), NO_PAUSE));
		assertArrayEquals(new int[0], ArcLayout.rows(new float[0], NO_PAUSE));
	}

	@Test
	void longLineSplitsIntoEvenRows() {
		float[] w = words(30, 12, 22, 28, 35, 9, 30, 25, 18); // ~250 wide: two rows
		int[] rows = ArcLayout.rows(w, NO_PAUSE);
		assertEquals(2, rows.length);
		assertEquals(0, rows[0]);
		float first = ArcLayout.width(w, 0, rows[1], ArcLayout.GAP);
		float second = ArcLayout.width(w, rows[1], w.length, ArcLayout.GAP);
		assertTrue(Math.abs(first - second) < 40, first + " vs " + second);
	}

	@Test
	void veryLongLineGetsMoreRows() {
		float[] w = new float[30];
		java.util.Arrays.fill(w, 25);
		assertTrue(ArcLayout.rows(w, new boolean[30]).length >= 3);
	}

	@Test
	void prefersBreakingAtACommaWhenNearlyAsEven() {
		float[] w = words(30, 30, 30, 30, 36, 30, 30);
		boolean[] pause = new boolean[7];
		pause[2] = true; // "a b c, d e f g": the comma after the third word
		int[] rows = ArcLayout.rows(w, pause);
		assertEquals(3, rows[1]);
		assertEquals(4, ArcLayout.rows(w, NO_PAUSE)[1], "without the comma the longer half goes first");
	}

	@Test
	void anglesAndSizesStayInRange() {
		float[] w = words(30, 12, 22, 28, 35, 9, 30, 25, 18, 20, 33, 12);
		int bigger = 0, total = 0;
		for (int line = 0; line < 200; line++) {
			long seed = ArcLayout.seed(line, "line " + line);
			List<ArcLayout.Group> groups = ArcLayout.place(w, ArcLayout.rows(w, NO_PAUSE), 2.5f, seed, false);
			int covered = 0;
			for (ArcLayout.Group g : groups) {
				assertTrue(g.angle() >= -ArcLayout.ROLL_LIMIT && g.angle() <= ArcLayout.ROLL_LIMIT, "angle " + g.angle());
				assertTrue(g.scale() >= ArcLayout.MIN_SCALE && g.scale() <= ArcLayout.MAX_SCALE, "scale " + g.scale());
				assertTrue(g.to() - g.from() >= 1 && g.to() - g.from() <= 3, "group of 1 to 3 words");
				assertEquals(covered, g.from(), "groups follow each other");
				covered = g.to();
				total++;
				if (g.scale() >= 1.25f) {
					bigger++;
				}
			}
			assertEquals(w.length, covered);
		}
		assertTrue(bigger > total / 10 && bigger < total / 3, "a few groups clearly bigger: " + bigger + "/" + total);
	}

	@Test
	void sameLineSameShapeOtherLineOtherShape() {
		float[] w = words(30, 12, 22, 28, 35, 9, 30, 25);
		int[] rows = ArcLayout.rows(w, NO_PAUSE);
		List<ArcLayout.Group> a = ArcLayout.place(w, rows, 2.5f, ArcLayout.seed(4, "Same words"), false);
		List<ArcLayout.Group> b = ArcLayout.place(w, rows, 2.5f, ArcLayout.seed(4, "Same words"), false);
		List<ArcLayout.Group> c = ArcLayout.place(w, rows, 2.5f, ArcLayout.seed(5, "Same words"), false);
		assertEquals(a.size(), b.size());
		for (int i = 0; i < a.size(); i++) {
			assertEquals(a.get(i).angle(), b.get(i).angle());
			assertEquals(a.get(i).scale(), b.get(i).scale());
			assertEquals(a.get(i).y(), b.get(i).y());
		}
		assertNotEquals(angles(a), angles(c), "the next line tumbles differently");
	}

	private static String angles(List<ArcLayout.Group> groups) {
		StringBuilder s = new StringBuilder();
		groups.forEach(g -> s.append(g.from()).append(':').append(g.angle()).append(' '));
		return s.toString();
	}

	@Test
	void rowsSmileAndStack() {
		float[] w = words(30, 30, 30, 30, 30, 30, 30, 30);
		int[] rows = ArcLayout.rows(w, NO_PAUSE);
		List<ArcLayout.Group> groups = ArcLayout.place(w, rows, 2.5f, ArcLayout.seed(0, "x"), false);
		float firstRowMin = Float.MAX_VALUE, secondRowMin = Float.MAX_VALUE;
		for (ArcLayout.Group g : groups) {
			if (g.from() < rows[1]) {
				firstRowMin = Math.min(firstRowMin, g.y());
			} else {
				secondRowMin = Math.min(secondRowMin, g.y());
			}
		}
		assertTrue(firstRowMin < secondRowMin, "the first row is above the second");
		// Rows bend up at the ends, more for wide rows but within limits
		assertEquals(ArcLayout.MIN_RISE, ArcLayout.rise(20));
		assertEquals(ArcLayout.MAX_RISE, ArcLayout.rise(1000));
		assertTrue(ArcLayout.rise(160) > ArcLayout.rise(100));
		assertTrue(ArcLayout.rise(160) > 2 * ArcLayout.STEP, "the curve shows through the random steps");
	}

	@Test
	void rightToLeftMirrors() {
		float[] w = words(30, 20, 25);
		long seed = ArcLayout.seed(1, "abc");
		List<ArcLayout.Group> ltr = ArcLayout.place(w, new int[] {0}, 2.5f, seed, false);
		List<ArcLayout.Group> rtl = ArcLayout.place(w, new int[] {0}, 2.5f, seed, true);
		assertEquals(ltr.getFirst().x(), -rtl.getFirst().x(), 1e-4);
	}

	@Test
	void depthColoursAroundTheFace() {
		int face = 0xC7D0F4;
		int dark = LyricsRenderer.depthDark(face, 0);
		int light = LyricsRenderer.depthLight(face);
		// Measured from the night reference: dark #7785A7, light #CDD7FC
		assertClose(0x7785A7, dark, 14);
		assertClose(0xCDD7FC, light, 6);
		int darkByDay = LyricsRenderer.depthDark(face, 1);
		assertTrue((darkByDay & 0xFF) < (dark & 0xFF), "darker by day");
	}

	private static void assertClose(int want, int got, int tolerance) {
		for (int shift = 0; shift <= 16; shift += 8) {
			int a = want >> shift & 0xFF, b = got >> shift & 0xFF;
			assertTrue(Math.abs(a - b) <= tolerance, String.format("want %06X got %06X", want, got));
		}
	}
}
