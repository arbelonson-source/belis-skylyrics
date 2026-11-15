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
		long kept = groups.stream().filter(g -> ArcLayout.worthDrawing(g, reach)).count();
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
		assertTrue(groups.stream().allMatch(g -> ArcLayout.worthDrawing(g, reach)));
		// even at the largest size the whole of a normal line stays in reach
		float big = ArcLayout.reach(0.7f * (float) Math.tan(Math.toRadians(3.4 * 3)) / 10f);
		assertTrue(groups.stream().allMatch(g -> ArcLayout.worthDrawing(g, big)));
	}

	@Test
	void theReachShrinksAsTheLettersGrow() {
		assertEquals(ArcLayout.reach(0.01f) * 2, ArcLayout.reach(0.005f), 1e-3);
	}
}
