package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class LyricsInViewTest {
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
}
