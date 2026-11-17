package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LyricsInViewTest {
	/** Forgets that lyrics were ever seen, so each test starts from "nothing on screen". */
	@BeforeEach
	void forget() throws ReflectiveOperationException {
		Field seenAt = LyricsInView.class.getDeclaredField("seenAt");
		seenAt.setAccessible(true);
		seenAt.setLong(null, Long.MIN_VALUE);
		LyricsInView.clock = () -> now;
	}

	/** A test clock that only moves when a test moves it, so a slow machine can't stretch the waits. */
	private long now = 1_000_000_000L;

	private void after(long ms) {
		now += ms * 1_000_000L;
	}

	@AfterEach
	void realClock() {
		LyricsInView.clock = System::nanoTime;
	}

	private static void lookForward() {
		// Camera at the origin looking down -Z, 70 degree view, 16:9
		ScreenLayer.capture(new Matrix4f(), new Matrix4f().perspective((float) Math.toRadians(70), 16 / 9f, 0.05f, 1000));
	}

	@Test
	void textAheadIsInView() {
		lookForward();
		assertTrue(ScreenLayer.inView(new Vector3f(0, 5, -40), 6, 2, 0));
	}

	@Test
	void textBehindIsNot() {
		lookForward();
		assertFalse(ScreenLayer.inView(new Vector3f(0, 5, 40), 6, 2, 0));
	}

	@Test
	void textFarToTheSideIsNot() {
		lookForward();
		assertFalse(ScreenLayer.inView(new Vector3f(-200, 0, -40), 6, 2, 0));
	}

	@Test
	void textJustPastTheEdgeStillCountsWhileSomeOfItShows() {
		lookForward();
		// The centre is off the right edge, but the left half of the text reaches into the view
		float edge = 40 * (float) Math.tan(Math.toRadians(35)) * 16 / 9f;
		assertFalse(ScreenLayer.inView(new Vector3f(edge + 3, 0, -40), 0.5f, 1, 0));
		assertTrue(ScreenLayer.inView(new Vector3f(edge + 3, 0, -40), 6, 1, 0));
		// Once seen, the margin keeps it counted a little longer
		assertTrue(ScreenLayer.inView(new Vector3f(edge + 3, 0, -40), 0.5f, 1, 0.12f));
	}

	@Test
	void cullKeepsALineJustOffScreenButDropsOneFarAway() {
		lookForward();
		float edge = 40 * (float) Math.tan(Math.toRadians(35)) * 16 / 9f;
		// Just past the right edge (less than the margin): still drawn so it never pops
		assertTrue(LyricsInView.worthDrawing(new Vector3f(edge * 1.1f, 0, -40), 1, 1));
		assertTrue(LyricsInView.worthDrawing(new Vector3f(0, 5, -40), 6, 2));
		// Far to the side, and behind you: skipped
		assertFalse(LyricsInView.worthDrawing(new Vector3f(edge * 3, 0, -40), 1, 1));
		assertFalse(LyricsInView.worthDrawing(new Vector3f(0, 5, 40), 6, 2));
		// A wide line whose centre is off screen but whose body reaches in still draws
		assertTrue(LyricsInView.worthDrawing(new Vector3f(edge * 2, 0, -40), edge * 1.5f, 1));
	}

	@Test
	void lyricsAheadCountAsOnScreenAndOnesBehindDoNot() {
		lookForward();
		assertFalse(LyricsInView.onScreen(), "nothing has been checked yet");
		LyricsInView.check(new Vector3f(0, 5, 40), 6, 2);
		assertFalse(LyricsInView.onScreen(), "behind the camera");
		LyricsInView.check(new Vector3f(-200, 0, -40), 6, 2);
		assertFalse(LyricsInView.onScreen(), "far off to the side");
		LyricsInView.check(new Vector3f(0, 5, -40), 6, 2);
		assertTrue(LyricsInView.onScreen());
	}

	@Test
	void theyStayCountedForAMomentAfterTheyLeaveTheView() {
		lookForward();
		LyricsInView.check(new Vector3f(0, 5, -40), 6, 2);
		assertTrue(LyricsInView.onScreen());
		after(150);
		assertTrue(LyricsInView.onScreen(), "still counted well inside the hold time");
		after(400);
		assertFalse(LyricsInView.onScreen(), "gone once the hold time is up");
	}

	@Test
	void onceSeenItTakesALittleMoreToCountAsGone() {
		lookForward();
		float edge = 40 * (float) Math.tan(Math.toRadians(35)) * 16 / 9f;
		Vector3f justOff = new Vector3f(edge + 3, 0, -40);
		// unseen: just off the edge is not on screen
		LyricsInView.check(justOff, 0.5f, 1);
		assertFalse(LyricsInView.onScreen());
		// seen, then just off the edge: the margin keeps renewing the hold
		LyricsInView.check(new Vector3f(0, 5, -40), 6, 2);
		after(200);
		LyricsInView.check(justOff, 0.5f, 1);
		after(200);
		assertTrue(LyricsInView.onScreen(), "renewed 200 ms ago, held for 300");
	}

	@Test
	void beforeTheFirstWorldFrameNothingIsSkipped() throws ReflectiveOperationException {
		Field captured = ScreenLayer.class.getDeclaredField("captured");
		captured.setAccessible(true);
		boolean was = captured.getBoolean(null);
		try {
			captured.setBoolean(null, false);
			assertTrue(LyricsInView.worthDrawing(new Vector3f(0, 5, 40), 6, 2), "nothing is known yet, so draw");
		} finally {
			captured.setBoolean(null, was);
		}
		lookForward();
		assertEquals(false, LyricsInView.worthDrawing(new Vector3f(0, 5, 40), 6, 2));
	}
}
