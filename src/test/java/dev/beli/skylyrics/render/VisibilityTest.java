package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
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

	/** Blocks of the Overworld go from y -64 to 319. */
	private static final int BOTTOM = -64, TOP = 319;

	/**
	 * What the game's sky light lookup says at height {@code y} over open Overworld ground: it reads the height from the
	 * block position's packed long, which keeps 12 bits of y, and finds full sky light down to the bedrock floor, none
	 * from there down.
	 */
	private static int game(int y) {
		return BlockPos.getY(BlockPos.asLong(0, y, 0)) <= BOTTOM ? 0 : 15;
	}

	private static int skyLight(int y) {
		return Visibility.skyLight(y, TOP, () -> game(y));
	}

	@Test
	void flyingAboveY2048IsNoCave() {
		// The lookup can only tell heights within 2048 of 0 apart: higher up it reads 4096 blocks lower
		assertEquals(3000 - 4096, BlockPos.getY(BlockPos.asLong(0, 3000, 0)));
		// Above the build limit nothing can be over you, at any height /tp, creative flight or an elytra reaches. Hide in
		// caves hid the lyrics, the top bar and the Record card from y 2048 up: the lookup read under the bedrock
		for (int y : new int[] {TOP + 1, 1000, 2047, 2048, 3000, 4000, 4096 + 100, 19_999_999}) {
			assertEquals(15, skyLight(y), "y " + y);
			assertFalse(Visibility.underground(false, skyLight(y)), "y " + y);
		}
	}

	@Test
	void insideTheWorldTheGameSaysWhatTheSkyLightIs() {
		assertEquals(0, Visibility.skyLight(10, TOP, () -> 0));
		assertEquals(0, Visibility.skyLight(TOP, TOP, () -> 0));
		assertEquals(15, Visibility.skyLight(TOP + 1, TOP, () -> 0));
		// Under the bedrock floor, down to where the void kills you, the game's answer stands: rock over you
		assertTrue(Visibility.underground(false, skyLight(BOTTOM - 1)));
		assertTrue(Visibility.underground(false, skyLight(BOTTOM - 64)));
		// A data pack's tallest world (-2032 to 2031) still fits in what the lookup can tell apart, to its top
		assertEquals(2031, BlockPos.getY(BlockPos.asLong(0, 2031, 0)));
		assertEquals(-2032, BlockPos.getY(BlockPos.asLong(0, -2032, 0)));
		assertEquals(7, Visibility.skyLight(2031, 2031, () -> 7));
	}

	@Test
	void divingDeepIsNotACave() {
		// From open water, however deep and dark it gets under the surface
		for (int light = 0; light <= 15; light++) {
			assertFalse(Visibility.underground(false, light, true));
		}
		assertTrue(Visibility.underground(false, 2, false));
	}

	@Test
	void aPoolInACaveIsStillACave() {
		// Dark cave, then the head goes under the water of a pool in it: still no open sky above, so Hide in caves must
		// not bring the lyrics, the top bar and the Record card back for as long as the head is under
		assertTrue(Visibility.underground(true, 0, true));
		assertTrue(Visibility.underground(true, Visibility.HIDE_AT, true));
		// Swimming out under open sky still ends it, before coming up
		assertFalse(Visibility.underground(true, Visibility.SHOW_AT, true));
		assertFalse(Visibility.underground(true, 15, true));
	}

	@Test
	void swimmingOutOfACaveIntoDeepOpenWaterEndsIt() {
		// Water dims sky light by 1 a block: 7 blocks under the surface of open water the head is at 8, below SHOW_AT. Out
		// of a cave through a flooded tunnel into the sea, the lyrics stayed hidden until the head rose to 6 blocks
		for (int depth = 1; depth <= 30; depth++) {
			assertFalse(Visibility.underground(true, Math.max(0, 15 - depth), true, 15), depth + " blocks under open water");
		}
		// The water's surface in the sun, behind a frozen sea's ice, or at a ravine floor open to the sky
		assertFalse(Visibility.underground(true, 0, true, Visibility.SHOW_AT));
		// A pool in a dark cave: the air over its water, or the rock it touches, has no sky light, so it stays a cave
		assertTrue(Visibility.underground(true, 0, true, 0));
		assertTrue(Visibility.underground(true, 0, true, Visibility.HIDE_AT));
		assertTrue(Visibility.underground(true, Visibility.HIDE_AT, true, Visibility.SHOW_AT - 1));
		// Diving from open sky still never starts it, and the dry rule is unchanged
		assertFalse(Visibility.underground(false, 0, true, 0));
		assertTrue(Visibility.underground(false, 2, false, 15));
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
