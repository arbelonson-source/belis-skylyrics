package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VisibilityTest {
	@Test
	void openSkyIsNeverACave() {
		assertFalse(Visibility.underground(false, 15));
		assertFalse(Visibility.underground(true, 15));
	}

	@Test
	void darkSkyLightIsACave() {
		assertTrue(Visibility.underground(false, 0));
		assertTrue(Visibility.underground(false, Visibility.HIDE_AT));
	}

	@Test
	void edgeOfACaveDoesNotFlicker() {
		int between = (Visibility.HIDE_AT + Visibility.SHOW_AT) / 2;
		assertTrue(between > Visibility.HIDE_AT && between < Visibility.SHOW_AT);
		assertFalse(Visibility.underground(false, between));
		assertTrue(Visibility.underground(true, between));
		assertFalse(Visibility.underground(true, Visibility.SHOW_AT));
	}

	@Test
	void theNetherCountsAsUndergroundButTheEndDoesNot() {
		assertTrue(Visibility.canBeUnderground(true, false));
		// Nether: no sky light, bedrock ceiling
		assertTrue(Visibility.canBeUnderground(false, true));
		// End: no sky light, no ceiling
		assertFalse(Visibility.canBeUnderground(false, false));
	}

	@Test
	void divingDeepIsNotACave() {
		assertFalse(Visibility.underground(false, 2, true));
		assertFalse(Visibility.underground(true, 2, true));
		assertTrue(Visibility.underground(false, 2, false));
	}

	@Test
	void forgettingDropsTheOldWorldsCaveAnswer() throws Exception {
		set("checkedAt", System.nanoTime());
		set("underground", true);
		Visibility.forget();
		assertFalse((boolean) get("underground"));
		assertTrue(System.nanoTime() - (long) get("checkedAt") >= 250_000_000L, "the next frame must ask again");
	}

	@Test
	void joiningAWorldForgetsTheCaveAnswerAndTheArcSpots() throws Exception {
		LyricsRenderer r = new LyricsRenderer(null, null, null);
		java.lang.reflect.Field f = LyricsRenderer.class.getDeclaredField("arcAnchors");
		f.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.Map<Integer, Object> anchors = (java.util.Map<Integer, Object>) f.get(r);
		anchors.put(3, new Object());
		set("checkedAt", System.nanoTime());
		set("underground", true);
		r.onJoinWorld();
		assertTrue(anchors.isEmpty(), "the old world's arc spots stay");
		assertFalse((boolean) get("underground"), "the old world's cave answer stays");
	}

	private static void set(String name, Object v) throws Exception {
		java.lang.reflect.Field f = Visibility.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(null, v);
	}

	private static Object get(String name) throws Exception {
		java.lang.reflect.Field f = Visibility.class.getDeclaredField(name);
		f.setAccessible(true);
		return f.get(null);
	}
}
