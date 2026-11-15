package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** How much of the already placed words a new card would cover, in square degrees, across the +-180 degree seam. */
class WorldWordsCrowdingTest {
	/** A footprint is {yaw, pitch, half width, half height}. */
	private static List<float[]> taken(float yaw, float pitch) {
		return List.of(new float[] {yaw, pitch, 5f, 2f});
	}

	@Test
	void aCardJustAcrossTheSeamStillCrowdsAWordOnTheOtherSide() {
		float across = WorldWords.crowding(-179f, 0f, 5f, 2f, taken(179f, 0f));
		assertTrue(across > 0, "358 degrees apart on the dial is only 2 degrees apart in the sky");
		// The same pair without a seam between them covers exactly the same area
		float plain = WorldWords.crowding(1f, 0f, 5f, 2f, taken(-1f, 0f));
		assertEquals(plain, across, 1e-3f);
	}

	@Test
	void itDoesNotMatterWhichSideOfTheSeamTheCardIsOn() {
		assertEquals(WorldWords.crowding(179f, 0f, 5f, 2f, taken(-179f, 0f)),
				WorldWords.crowding(-179f, 0f, 5f, 2f, taken(179f, 0f)), 1e-3f);
	}

	@Test
	void yawsMoreThanAFullTurnApartCountAsTheSameDirection() {
		assertEquals(WorldWords.crowding(10f, 0f, 5f, 2f, taken(10f, 0f)),
				WorldWords.crowding(370f, 0f, 5f, 2f, taken(10f, 0f)), 1e-3f);
		assertEquals(WorldWords.crowding(10f, 0f, 5f, 2f, taken(10f, 0f)),
				WorldWords.crowding(-350f, 0f, 5f, 2f, taken(10f, 0f)), 1e-3f);
	}

	@Test
	void farApartCardsDoNotCrowdAndAnEmptySkyNeverDoes() {
		assertEquals(0f, WorldWords.crowding(0f, 0f, 5f, 2f, taken(180f, 0f)));
		assertEquals(0f, WorldWords.crowding(0f, 0f, 5f, 2f, taken(0f, 40f)));
		assertEquals(0f, WorldWords.crowding(0f, 0f, 5f, 2f, List.of()));
		assertTrue(WorldWords.clear(0f, 0f, 5f, 2f, taken(180f, 0f)));
	}

	@Test
	void theOverlapAddsUpOverEveryFootprint() {
		float one = WorldWords.crowding(0f, 0f, 5f, 2f, List.of(new float[] {3f, 0f, 5f, 2f}));
		float two = WorldWords.crowding(0f, 0f, 5f, 2f, List.of(new float[] {3f, 0f, 5f, 2f}, new float[] {-3f, 0f, 5f, 2f}));
		assertTrue(one > 0);
		assertEquals(2 * one, two, 1e-3f);
	}
}
