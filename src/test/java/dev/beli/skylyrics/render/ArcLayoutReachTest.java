package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ArcLayoutReachTest {
	/** What one layout unit covers at the default size: the arc size 0.7, tan 3.4 degrees, over the 10-unit row. */
	private static final float UNIT_TAN = 0.7f * (float) Math.tan(Math.toRadians(3.4)) / 10f;

	@Test
	void aGiantLineDrawsOnlyTheGroupsNearItsMiddle() {
		float[] widths = new float[10_000];
		Arrays.fill(widths, 10.5f);
		boolean[] pause = new boolean[widths.length];
		boolean[] tight = new boolean[widths.length];
		Arrays.fill(tight, true);
		List<ArcLayout.Group> groups = ArcLayout.place(widths, ArcLayout.rows(widths, pause, tight), 3f, 7L, false, tight);
		float reach = ArcLayout.reach(UNIT_TAN);
		long kept = groups.stream().filter(g -> ArcLayout.worthDrawing(g, reach, 0)).count();
		assertTrue(groups.size() > 5_000, "a very long line has thousands of groups");
		assertTrue(kept > 0 && kept < groups.size() / 4, "only the groups near the middle are kept, not " + kept + " of " + groups.size());
		assertTrue(groups.stream().anyMatch(g -> Math.abs(g.y()) > 3 * reach), "the line really reaches far past the view");
	}

	@Test
	void aNormalLineIsAlwaysDrawnWhole() {
		float[] widths = new float[40];
		Arrays.fill(widths, 30f);
		boolean[] pause = new boolean[widths.length];
		List<ArcLayout.Group> groups = ArcLayout.place(widths, ArcLayout.rows(widths, pause), 3f, 3L, false);
		float reach = ArcLayout.reach(UNIT_TAN);
		assertTrue(groups.stream().allMatch(g -> ArcLayout.worthDrawing(g, reach, 0)));
		// even at the largest size the whole of a normal line stays in reach
		float big = ArcLayout.reach(0.7f * (float) Math.tan(Math.toRadians(3.4 * 3)) / 10f);
		assertTrue(groups.stream().allMatch(g -> ArcLayout.worthDrawing(g, big, 0)));
	}

	@Test
	void theReachShrinksAsTheLettersGrow() {
		assertEquals(ArcLayout.reach(0.01f) * 2, ArcLayout.reach(0.005f), 1e-3);
	}

	@Test
	void aTallLineScrollsToTheWordsBeingSungAndANormalOneDoesNot() {
		float[] widths = new float[400];
		Arrays.fill(widths, 30f);
		boolean[] pause = new boolean[widths.length];
		List<ArcLayout.Group> tall = ArcLayout.place(widths, ArcLayout.rows(widths, pause), 3f, 5L, false);
		float reach = ArcLayout.reach(UNIT_TAN);
		// Words come out from the top row, which sits far above the middle: the first group is out of reach without the scroll
		assertTrue(!ArcLayout.worthDrawing(tall.getFirst(), reach, 0));
		float start = ArcLayout.focus(tall, 0), end = ArcLayout.focus(tall, 399);
		assertTrue(ArcLayout.worthDrawing(tall.getFirst(), reach, start), "the first words are in view as they start");
		assertTrue(ArcLayout.worthDrawing(tall.getLast(), reach, end), "and the last as they finish");
		assertTrue(start < 0 && end > 0 && end > ArcLayout.focus(tall, 200) && ArcLayout.focus(tall, 200) > start, "it moves steadily down");
		float[] few = new float[30];
		Arrays.fill(few, 30f);
		List<ArcLayout.Group> normal = ArcLayout.place(few, ArcLayout.rows(few, new boolean[30]), 3f, 5L, false);
		for (int w = 0; w < 30; w++) {
			assertEquals(0f, ArcLayout.focus(normal, w), 0f);
		}
	}

	@Test
	void theScrollOfATallLineNeverHopsBetweenNeighbouringMoments() {
		float[] widths = new float[400];
		Arrays.fill(widths, 30f);
		List<ArcLayout.Group> tall = ArcLayout.place(widths, ArcLayout.rows(widths, new boolean[400]), 3f, 5L, false);
		float smooth = 0, stepped = 0, previous = ArcLayout.focus(tall, 0f), previousStep = previous;
		for (int i = 1; i <= 399 * 20; i++) {
			float word = i / 20f;
			float now = ArcLayout.focus(tall, word);
			smooth = Math.max(smooth, Math.abs(now - previous));
			previous = now;
			float old = ArcLayout.focus(tall, (float) Math.floor(word)); // what the whole-word stepping gave
			stepped = Math.max(stepped, Math.abs(old - previousStep));
			previousStep = old;
		}
		assertTrue(smooth < 0.5f, "a twentieth of a word moves the scroll by at most half a unit, not " + smooth);
		assertTrue(stepped > 1f, "the whole-word stepping did hop, by " + stepped);
		assertEquals(ArcLayout.focus(tall, 100f), ArcLayout.focus(tall, 100), 0f);
	}

	@Test
	void aNormalLineStaysAtZeroForFractionalPositionsToo() {
		float[] few = new float[30];
		Arrays.fill(few, 30f);
		List<ArcLayout.Group> normal = ArcLayout.place(few, ArcLayout.rows(few, new boolean[30]), 3f, 5L, false);
		for (int i = 0; i <= 600; i++) {
			assertEquals(0f, ArcLayout.focus(normal, i / 20f), 0f);
		}
	}
}
