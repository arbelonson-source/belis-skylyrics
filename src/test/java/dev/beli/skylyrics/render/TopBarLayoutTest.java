package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TopBarLayoutTest {
	@Test
	void fullWidthOnABigScreen() {
		TopBarLayout.Fit fit = TopBarLayout.fit(533, 25, 40);
		assertEquals(TopBarLayout.PILL_W, fit.pill());
		assertTrue(fit.clock());
		assertTrue(fit.stats());
	}

	@Test
	void shrinksOnASmallScreenAndStaysOnIt() {
		// The narrowest GUI Minecraft makes is 320 wide
		TopBarLayout.Fit fit = TopBarLayout.fit(320, 30, 45);
		assertTrue(fit.pill() < TopBarLayout.PILL_W);
		assertTrue(fit.clock() && fit.stats());
		// Centred, with the wider side's items and the edge margin still on screen
		float side = Math.max(30 + TopBarLayout.CLOCK_GAP, TopBarLayout.STATS_GAP + 45);
		assertTrue(fit.pill() / 2 + side + TopBarLayout.EDGE <= 160.01f, "pill " + fit.pill());
	}

	@Test
	void statsThenClockStepAsideWhenTooNarrow() {
		TopBarLayout.Fit fit = TopBarLayout.fit(240, 30, 45);
		assertFalse(fit.stats());
		assertTrue(fit.clock());
		TopBarLayout.Fit tiny = TopBarLayout.fit(170, 30, 45);
		assertFalse(tiny.stats());
		assertFalse(tiny.clock());
		assertEquals(170 - 2 * TopBarLayout.EDGE, tiny.pill(), 0.01f);
	}

	@Test
	void pillFitsItsTextUpToTheWidest() {
		float small = TopBarLayout.pillFor(80, 270);
		assertEquals(80 + TopBarLayout.chrome(), small, 0.01f);
		assertEquals(80 + TopBarLayout.HL_PAD, TopBarLayout.textRoom(small), 0.01f, "room for the text and the highlight's end");
		assertEquals(270, TopBarLayout.pillFor(400, 270));
	}

	@Test
	void lineThatFitsNeverScrolls() {
		assertEquals(0, TopBarLayout.lineShift(100, 100, 120, 3));
		assertEquals(0, TopBarLayout.lineShift(100, 40, 103, 3));
	}

	@Test
	void longLineKeepsTheCurrentWordInView() {
		float room = 150, pad = 3;
		// Early words are already in view
		assertEquals(0, TopBarLayout.lineShift(300, 60, room, pad));
		// Later, the word's end (with its highlight) sits right at the room's end
		float shift = TopBarLayout.lineShift(300, 200, room, pad);
		assertEquals(200 + pad - shift, room, 0.01f);
		// At the end, the line's end shows and no more
		assertEquals(300 + pad - room, TopBarLayout.lineShift(300, 300, room, pad), 0.01f);
	}

	@Test
	void highlightNeverReachesTheNextWord() {
		assertEquals(3 - TopBarLayout.HL_CLEAR, TopBarLayout.highlightPad(3), 0.001f);
		// Wide gaps get the reference's padding, never more; tiny ones still get a little
		assertEquals(TopBarLayout.HL_PAD, TopBarLayout.highlightPad(10));
		assertEquals(1, TopBarLayout.highlightPad(1));
	}

	@Test
	void signalBarsFollowThePlayerListSteps() {
		assertEquals(0, TopBarLayout.signalBars(-1));
		assertEquals(4, TopBarLayout.signalBars(20));
		assertEquals(3, TopBarLayout.signalBars(150));
		assertEquals(2, TopBarLayout.signalBars(450));
		assertEquals(1, TopBarLayout.signalBars(2000));
	}

	@Test
	void theCoverSpinsUpWhilePlayingAndWindsDownWhenPaused() {
		float speed = 0;
		for (int i = 0; i < 60; i++) {
			speed = TopBarLayout.spinSpeed(speed, true, 1 / 60f);
		}
		// About a second in it's well on its way, but not yet at full speed
		assertTrue(speed > 0.8f && speed < 1, "speed " + speed);
		float paused = TopBarLayout.spinSpeed(speed, false, 1 / 60f);
		assertTrue(paused < speed && paused > 0);
		// A long hitch never overshoots
		assertEquals(1, TopBarLayout.spinSpeed(0, true, 5), 1e-6);
		assertEquals(0, TopBarLayout.spinSpeed(1, false, 5), 1e-6);
	}

	@Test
	void progressFillsAsTheSongPlaysAndHidesWithoutALength() {
		assertEquals(-1, TopBarLayout.progress(5_000, 0));
		assertEquals(0, TopBarLayout.progress(-200, 10_000));
		assertEquals(0.25f, TopBarLayout.progress(2_500, 10_000), 1e-6f);
		assertEquals(1, TopBarLayout.progress(12_000, 10_000));
	}

	@Test
	void aBiggerBarFitsInItsOwnScaledUnits() {
		// At 150% the bar is drawn 1.5 times wider, so its pill may only be as wide as the screen allows after that
		TopBarLayout.Fit big = TopBarLayout.fit(320, 1.5f, 0, 0);
		assertTrue(big.pill() * 1.5f <= 320 - 2 * TopBarLayout.EDGE + 0.01f, "pill " + big.pill());
		assertTrue(big.pill() < TopBarLayout.fit(320, 1f, 0, 0).pill());
	}

	@Test
	void sizeOneChangesNothing() {
		assertEquals(TopBarLayout.fit(320, 30, 45), TopBarLayout.fit(320, 1f, 30, 45));
	}

	@Test
	void scaledClipMatchesThePoseTheBarIsDrawnWith() {
		// Full size changes nothing
		assertArrayEquals(new int[] {100, 20, 220, 36}, TopBarLayout.scaledClip(100, 20, 220, 36, 1f, 320, 18));
		// 150% about the screen's middle and the top edge: the middle stays, the box grows with it
		assertArrayEquals(new int[] {70, 20, 250, 44}, TopBarLayout.scaledClip(100, 20, 220, 36, 1.5f, 320, 20));
		// 75% about a bottom pivot: the pivot row stays put
		int[] box = TopBarLayout.scaledClip(100, 200, 220, 216, 0.75f, 320, 216);
		assertEquals(216, box[3]);
		assertEquals(115, box[0]);
		assertEquals(205, box[2]);
	}

	@Test
	void aBottomBarGrowsUpFromItsBottomEdge() {
		assertEquals(100f, TopBarLayout.pivotY(false, 100f));
		assertEquals(100f + TopBarLayout.PILL_H, TopBarLayout.pivotY(true, 100f));
		// Scaled about the pivot, the edge on the screen's side stays put: ask the real clip for the bottom edge
		float top = TopBarLayout.bottomTop(240);
		float bottom = top + TopBarLayout.PILL_H;
		for (float size : new float[] {0.75f, 1.5f, 2f}) {
			int[] box = TopBarLayout.scaledClip(100, top, 220, bottom, size, 320, TopBarLayout.pivotY(true, top));
			// the clip is whole pixels, so within one of the edge (a wrong pivot is off by several)
			assertEquals(bottom, box[3], 1f, "bottom edge at size " + size);
		}
		// A top bar grows down from its top edge instead
		assertEquals(top, TopBarLayout.scaledClip(100, top, 220, bottom, 1.5f, 320, TopBarLayout.pivotY(false, top))[1], 1f);
	}

	@Test
	void theBottomBarSitsAboveTheHeldItemName() {
		// The held item's name is drawn from 59 above the bottom
		assertTrue(240 - (TopBarLayout.bottomTop(240) + TopBarLayout.PILL_H) >= 59);
	}

	@Test
	void inCreativeTheBottomBarDropsWithTheItemName() {
		// The game draws the held item's name 14 lower when there are no hearts; the bar follows, keeping the same gap
		assertEquals(TopBarLayout.bottomTop(240, true) + 14, TopBarLayout.bottomTop(240, false));
		assertEquals(TopBarLayout.bottomTop(240), TopBarLayout.bottomTop(240, true));
	}

	@Test
	void theBarClearsTheArmourRowOfTallHealth() {
		// 3 rows of hearts (e.g. 40 health and a golden apple's absorption) put the armour row 67 up, over the bar's 64
		float tall = TopBarLayout.healthTopUp(40, 4, 10);
		assertEquals(67, tall);
		assertTrue(TopBarLayout.bottomUp(true, tall, false) >= tall + 4);
		// ordinary health keeps today's place
		assertEquals(TopBarLayout.BOTTOM_UP, TopBarLayout.bottomUp(true, TopBarLayout.healthTopUp(20, 0, 10), false));
		assertEquals(TopBarLayout.BOTTOM_UP, TopBarLayout.bottomUp(true, TopBarLayout.healthTopUp(20, 4, 10), false));
		assertTrue(TopBarLayout.bottomTop(240, true, tall, false) < TopBarLayout.bottomTop(240, true));
	}

	@Test
	void heartRowsFollowVanilla() {
		assertEquals(1, TopBarLayout.heartRows(20, 0));
		assertEquals(2, TopBarLayout.heartRows(20, 4));
		assertEquals(3, TopBarLayout.heartRows(40, 4));
		assertEquals(10, TopBarLayout.rowSpacing(2));
		assertEquals(9, TopBarLayout.rowSpacing(3));
		assertEquals(3, TopBarLayout.rowSpacing(20));
		assertEquals(39, TopBarLayout.healthTopUp(20, 0, 0));
		assertEquals(49, TopBarLayout.healthTopUp(20, 0, 5));
		assertEquals(59, TopBarLayout.healthTopUp(20, 4, 5));
	}

	@Test
	void theBarStepsAboveTheActionBarText() {
		for (boolean hurt : new boolean[] {true, false}) {
			float top = TopBarLayout.bottomTop(240, hurt, 49, true);
			float bottom = top + TopBarLayout.PILL_H;
			assertTrue(240 - bottom >= TopBarLayout.ACTION_UP + TopBarLayout.ACTION_GAP);
			assertTrue(top < TopBarLayout.bottomTop(240, hurt, 49, false));
		}
		assertEquals(TopBarLayout.bottomTop(240, true), TopBarLayout.bottomTop(240, true, 49, false));
	}

	@Test
	void barBoxCoversClockAndStatsAndGrowsFromItsBottom() {
		float[] one = TopBarLayout.barBox(640, 1, 270, 30, true, 40, true, 200);
		assertEquals(320 - 135 - 11 - 30, one[0], 1e-3f);
		assertEquals(320 + 135 + 10 + 40, one[2], 1e-3f);
		assertEquals(200, one[1], 1e-3f);
		assertEquals(200 + TopBarLayout.PILL_H, one[3], 1e-3f);
		float[] big = TopBarLayout.barBox(640, 2, 270, 30, true, 40, true, 200);
		assertEquals(one[3], big[3], 1e-3f);
		assertEquals(one[3] - 2 * TopBarLayout.PILL_H, big[1], 1e-3f);
		assertEquals(320 + 2 * (one[2] - 320), big[2], 1e-3f);
		float[] bare = TopBarLayout.barBox(640, 1, 200, 30, false, 40, false, 200);
		assertEquals(220, bare[0], 1e-3f);
		assertEquals(420, bare[2], 1e-3f);
	}

	@Test
	void oddHealthFollowsTheHeartsTheGameDraws() {
		// 19 health and 1 absorption: 10 + 1 hearts are drawn, so two rows, though 20 / 20 is one
		assertEquals(2, TopBarLayout.heartRows(19, 1));
		// the game spaces the rows by its own count of one row here: 11 apart
		assertEquals(50, TopBarLayout.healthTopUp(19, 1, 0));
		assertEquals(50, TopBarLayout.healthTopUp(19, 1, 5));
		assertEquals(1, TopBarLayout.heartRows(0, 0));
		assertEquals(1, TopBarLayout.heartRows(Float.NaN, Float.NaN));
	}

	@Test
	void absurdHealthCantPushTheBarOffTheScreen() {
		for (float health : new float[] {1e6f, Float.POSITIVE_INFINITY, Float.MAX_VALUE}) {
			assertEquals(TopBarLayout.HEALTH_UP_MAX, TopBarLayout.healthTopUp(health, 0, 5));
			assertEquals(TopBarLayout.HEALTH_UP_MAX, TopBarLayout.healthTopUp(20, health, 0));
		}
		assertTrue(TopBarLayout.bottomTop(100, true, 400, true) >= 0);
		assertEquals(0f, TopBarLayout.bottomTop(100, true, 400, true));
	}

	@Test
	void theActionBarLiftIsPinnedToTheGamesTextPlusAGap() {
		// the text's top is 68 + 4 up; the bar keeps 3 above it
		assertEquals(75f, TopBarLayout.bottomUp(true, 0, true));
		assertEquals(75f, TopBarLayout.bottomUp(false, 0, true));
	}

	@Test
	void aBiggerBarsBoxReachesFurtherOutAtItsLeftEdge() {
		float[] two = TopBarLayout.barBox(640, 2, 270, 30, true, 40, true, 200);
		assertEquals(320 + 2 * ((320 - 135 - 11 - 30) - 320), two[0], 1e-3f);
	}
}
