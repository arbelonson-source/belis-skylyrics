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
}
