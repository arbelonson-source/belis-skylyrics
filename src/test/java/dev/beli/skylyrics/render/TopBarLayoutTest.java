package dev.beli.skylyrics.render;

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
}
