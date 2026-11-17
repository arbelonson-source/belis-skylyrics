package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The built-in demo song's controls, which a player trying the mod without music relies on. */
class DemoSourceTest {
	@Test
	void pausingFreezesThePositionAndSaysNotPlaying() {
		DemoSource demo = new DemoSource();
		demo.seek(20_000);
		demo.playPause();
		Snapshot a = demo.poll();
		Snapshot b = demo.poll();
		assertFalse(a.playing());
		assertEquals(a.positionMs(), b.positionMs());
		assertTrue(a.positionMs() >= 20_000 && a.positionMs() < 20_000 + 60_000, "position " + a.positionMs());
	}

	@Test
	void seekingWhilePausedLandsExactlyAndStaysPaused() {
		DemoSource demo = new DemoSource();
		demo.playPause();
		demo.seek(33_000);
		Snapshot s = demo.poll();
		assertFalse(s.playing());
		assertEquals(33_000, s.positionMs());
	}

	@Test
	void playingAgainContinuesFromThePausedPosition() {
		DemoSource demo = new DemoSource();
		demo.playPause();
		demo.seek(33_000);
		demo.playPause();
		Snapshot s = demo.poll();
		assertTrue(s.playing());
		assertTrue(s.positionMs() >= 33_000 && s.positionMs() < 33_000 + 60_000, "position " + s.positionMs());
	}

	@Test
	void seekingWhilePlayingMovesTheClockThere() {
		DemoSource demo = new DemoSource();
		demo.seek(50_000);
		Snapshot s = demo.poll();
		assertTrue(s.playing());
		assertTrue(s.positionMs() >= 50_000 && s.positionMs() < 50_000 + 20_000, "position " + s.positionMs());
	}

	@Test
	void nextAndPreviousRestartTheSong() {
		DemoSource demo = new DemoSource();
		demo.playPause();
		demo.seek(40_000);
		demo.next();
		assertEquals(0, demo.poll().positionMs());
		demo.seek(40_000);
		demo.previous();
		assertEquals(0, demo.poll().positionMs());
	}

	@Test
	void pastTheEndTheDemoLoopsBackToTheStart() {
		DemoSource demo = new DemoSource();
		demo.playPause();
		demo.seek(DemoSource.TRACK.lengthMs() + 5_000);
		assertEquals(5_000, demo.poll().positionMs());
	}

	@Test
	void switchingTheDemoOnStartsItFromTheTopAndPlaying() throws InterruptedException {
		DemoSource demo = new DemoSource();
		demo.seek(40_000);
		demo.playPause(); // left paused mid-song
		demo.restart();
		Snapshot s = demo.poll();
		assertTrue(s.playing());
		assertTrue(s.positionMs() < 5_000, "position " + s.positionMs());
	}

	@Test
	void onlyTheFirstReadFromTheDemoRestartsIt() {
		DemoSource demo = new DemoSource();
		MediaSource player = new DemoSource();
		assertTrue(MediaTracker.switchedToDemo(demo, null, demo), "the game starts in demo mode");
		assertTrue(MediaTracker.switchedToDemo(demo, player, demo), "demo switched on");
		assertFalse(MediaTracker.switchedToDemo(demo, demo, demo), "the demo keeps playing");
		assertFalse(MediaTracker.switchedToDemo(player, demo, demo), "demo switched off");
		assertFalse(MediaTracker.switchedToDemo(player, null, demo), "the player's own first read");
		assertFalse(MediaTracker.switchedToDemo(player, player, demo), "the player keeps playing");
	}
}
