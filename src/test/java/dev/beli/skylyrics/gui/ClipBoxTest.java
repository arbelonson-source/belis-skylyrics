package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The mod's clip boxes never reach Minecraft 26.2/26.3 empty. Two test games on 26.3 stopped with "Scissor size must be
 * >0" the moment the test hook shrank the window: "was 0x34" with the song library open (1920x1080 to 640x480, GUI scale
 * 2) and "was 280x0" with no screen open (1920x1080 to 1280x720), each a box laid out for the old GUI past the new
 * window's right or bottom edge.
 */
class ClipBoxTest {
	/**
	 * What 26.3 hands the graphics card for a clip box drawn into (GUI units, after the pose): null when it drops the drawing
	 * (the box is off the GUI: GuiGraphicsExtractor's scissor stack makes it empty and the GUI render state skips anything
	 * drawn in an empty box), otherwise {x, y, width, height} in pixels from GuiRenderer.enableScissor, which cuts the box
	 * at the window's right and bottom edge. FrontendRenderPass.enableScissor throws when the width or height is 0.
	 */
	private static int[] game(int left, int top, int right, int bottom, int guiW, int guiH, int windowW, int windowH, int scale) {
		int l = Math.max(left, 0), t = Math.max(top, 0), r = Math.min(right, guiW), b = Math.min(bottom, guiH);
		if (l >= r || t >= b) {
			return null;
		}
		double pl = l * scale, pt = t * scale, pr = Math.min(r * scale, windowW), pb = Math.min(b * scale, windowH);
		return new int[] {(int) pl, windowH - (int) pb, Math.max(0, (int) (pr - pl)), Math.max(0, (int) (pb - pt))};
	}

	/** Whether 26.3 would stop the game over this box, had the mod opened it and drawn in it. */
	private static boolean throwsIn263(int left, int top, int right, int bottom, int guiW, int guiH, int windowW, int windowH,
			int scale) {
		int[] px = game(left, top, right, bottom, guiW, guiH, windowW, windowH, scale);
		return px != null && (px[2] <= 0 || px[3] <= 0);
	}

	@Test
	void theLibraryCrashBoxIsNotOpened() {
		// The top bar's text clip (17 GUI units tall) laid out for the 960x540 GUI, drawn into a 640x480 window: its left
		// edge is past the window's right edge
		assertArrayEquals(new int[] {690, 446, 0, 34}, game(345, 0, 615, 17, 960, 540, 640, 480, 2));
		assertFalse(ClipBox.opens(345, 0, 615, 17, 960, 540, 640, 480, 2));
	}

	@Test
	void theBottomCrashBoxIsNotOpened() {
		// A bottom box 140 wide laid out for the 960x540 GUI, drawn into a 1280x720 window: below the window's bottom edge
		assertArrayEquals(new int[] {200, 0, 280, 0}, game(100, 490, 240, 507, 960, 540, 1280, 720, 2));
		assertFalse(ClipBox.opens(100, 490, 240, 507, 960, 540, 1280, 720, 2));
	}

	@Test
	void emptyBoxesAreNotOpened() {
		assertFalse(ClipBox.opens(10, 10, 10, 30, 640, 360, 1280, 720, 2));
		assertFalse(ClipBox.opens(10, 30, 50, 30, 640, 360, 1280, 720, 2));
		assertFalse(ClipBox.opens(50, 10, 10, 30, 640, 360, 1280, 720, 2));
	}

	@Test
	void boxesOffTheGuiAreNotOpened() {
		// The GUI in step with the window: the bottom bar or the Record card gliding up from below the new bottom edge
		// after the window was made smaller, or a box left of or above the screen
		assertFalse(ClipBox.opens(100, 490, 240, 507, 640, 360, 1280, 720, 2));
		assertNull(game(100, 490, 240, 507, 640, 360, 1280, 720, 2));
		assertFalse(ClipBox.opens(-50, 10, -1, 30, 640, 360, 1280, 720, 2));
		assertFalse(ClipBox.opens(10, -40, 50, 0, 640, 360, 1280, 720, 2));
		assertFalse(ClipBox.opens(640, 10, 700, 30, 640, 360, 1280, 720, 2));
	}

	@Test
	void boxesPartlyInViewAreOpened() {
		// Cut at an edge, something still shows: the bar sliding in at the bottom, a list scrolled past the top
		assertTrue(ClipBox.opens(100, 350, 240, 367, 640, 360, 1280, 720, 2));
		assertTrue(ClipBox.opens(-20, -20, 30, 30, 640, 360, 1280, 720, 2));
		assertTrue(ClipBox.opens(600, 10, 700, 30, 640, 360, 1280, 720, 2));
		// The GUI a frame behind a smaller window, the box partly in the window
		assertTrue(ClipBox.opens(300, 10, 400, 30, 960, 540, 640, 480, 2));
		// An odd window size: the GUI's last column is partly in the window (641 / 2 rounds up to 321)
		assertTrue(ClipBox.opens(320, 10, 321, 30, 321, 240, 641, 480, 2));
	}

	/**
	 * Every box, GUI size and window size: the mod opens a box exactly when 26.3 would show part of it, so it never hands
	 * the game a box it would stop over, and never skips one that would show.
	 */
	@Test
	void opensExactlyWhatTheGameCanDraw() {
		int checked = 0;
		int[][] sizes = {{1920, 1080}, {1280, 720}, {640, 480}, {641, 481}, {800, 1000}, {3440, 1440}};
		for (int scale = 1; scale <= 4; scale++) {
			for (int[] gui : sizes) {
				for (int[] window : sizes) {
					int guiW = (gui[0] + scale - 1) / scale, guiH = (gui[1] + scale - 1) / scale;
					for (int left = -40; left <= guiW + 40; left += 37) {
						for (int top = -40; top <= guiH + 40; top += 41) {
							for (int w = 0; w <= 300; w += 60) {
								int l = left, t = top, r = left + w, b = top + Math.max(0, w / 3 - 20);
								int[] px = game(l, t, r, b, guiW, guiH, window[0], window[1], scale);
								boolean shows = px != null && px[2] > 0 && px[3] > 0;
								assertEquals(shows, ClipBox.opens(l, t, r, b, guiW, guiH, window[0], window[1], scale),
										() -> "box " + l + "," + t + " to " + r + "," + b + " on a " + guiW + "x" + guiH
												+ " GUI in a " + window[0] + "x" + window[1] + " window");
								checked++;
							}
						}
					}
				}
			}
		}
		assertTrue(checked > 10_000);
	}

	/**
	 * Why players don't hit this in 26.3 without the guard: with the GUI in step with the window (its size the window's,
	 * divided by the GUI scale and rounded up, as Window.setGuiScale makes it), no box the game keeps is cut to nothing.
	 * Only a GUI a frame behind the window, as the test hook's resize left it, gives the boxes above.
	 */
	@Test
	void aGuiInStepWithTheWindowNeverGivesAnEmptyBox() {
		int[][] windows = {{1920, 1080}, {1280, 720}, {640, 480}, {641, 481}, {857, 333}, {320, 240}};
		for (int scale = 1; scale <= 6; scale++) {
			for (int[] window : windows) {
				int guiW = (window[0] + scale - 1) / scale, guiH = (window[1] + scale - 1) / scale;
				for (int left = -10; left <= guiW + 10; left += 7) {
					for (int top = -10; top <= guiH + 10; top += 11) {
						assertFalse(throwsIn263(left, top, left + 1, top + 1, guiW, guiH, window[0], window[1], scale));
						assertFalse(throwsIn263(left, top, left + 90, top + 17, guiW, guiH, window[0], window[1], scale));
					}
				}
			}
		}
	}
}
