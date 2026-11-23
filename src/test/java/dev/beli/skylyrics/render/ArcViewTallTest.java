package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * An Arc line taller than about nine rows scrolls so the words being sung stay in view ({@link ArcLayout#focus}). With a
 * big Size or a lower FOV far fewer rows fill the screen, and a long line cut into rows that fit across it is taller than
 * the screen: centred on its spot, its first words were sung above the screen's top edge, out of sight, and its last ones
 * below the bottom. This is geometry only: where the word being sung is drawn, as an angle from where you look.
 */
class ArcViewTallTest {
	private static final float SPACE = 3f;
	/** The renderer draws an Arc line at 0.7 of the stacked lines' size, a row of those {@code ROW_H} = 10 units tall. */
	private static final double ARC_SIZE = 0.7, ROW_H = 10;
	/** A line's middle sits 5 to 8 degrees above where you look. */
	private static final double ABOVE_MIN = 5, ABOVE_MAX = 8;

	private record Line(float[] widths, boolean[] pause, boolean[] tight, int[] rank, long seed) {}

	/** Lines of 3 to 30 words of 6 to 40 units (a short "a" to a long "lanterns"), some right to left. */
	private static List<Line> lines(int count) {
		Random rnd = new Random(20261123);
		List<Line> out = new ArrayList<>();
		for (int t = 0; t < count; t++) {
			int n = 3 + rnd.nextInt(28);
			float[] w = new float[n];
			boolean[] pause = new boolean[n];
			for (int i = 0; i < n; i++) {
				w[i] = 6 + rnd.nextInt(35);
				pause[i] = rnd.nextInt(7) == 0;
			}
			out.add(new Line(w, pause, new boolean[n], ArcLayout.straight(n, t % 5 == 1), ArcLayout.seed(t, "line " + t)));
		}
		return out;
	}

	/** The highest and lowest point of a group's tilted letters (8 tall at scale 1), with the block scrolled by {@code focus}. */
	private static double[] upDown(ArcLayout.Group g, float[] widths, float focus) {
		float w = ArcLayout.width(widths, g.from(), g.to(), SPACE) * g.scale() / 2, h = 4f * g.scale();
		double a = Math.toRadians(g.angle()), s = Math.sin(a), c = Math.cos(a);
		double top = Double.MAX_VALUE, bottom = -Double.MAX_VALUE;
		for (float[] p : new float[][] {{-w, -h}, {w, -h}, {w, h}, {-w, h}}) {
			double y = g.y() - focus + p[0] * s + p[1] * c;
			top = Math.min(top, y);
			bottom = Math.max(bottom, y);
		}
		return new double[] {top, bottom};
	}

	/** How many words of these lines are off the top or bottom of the screen while they are sung, and how many were sung. */
	private static int[] sungOffScreen(float size, double fov, int width, int height, boolean scrollForTheView) {
		float room = LyricsRenderer.arcRoom(true, size, LyricsRenderer.tanHalfWide(fov, width, height));
		float view = scrollForTheView ? LyricsRenderer.arcView(true, size, fov) : Float.POSITIVE_INFINITY;
		double unit = ARC_SIZE * Math.tan(Math.toRadians(LyricsRenderer.rowAngle(true, size))) / ROW_H;
		int off = 0, sung = 0;
		for (Line l : lines(300)) {
			List<ArcLayout.Group> groups = ArcLayout.fit(l.widths(), l.pause(), l.tight(), SPACE, l.seed(), l.rank(), room);
			for (ArcLayout.Group g : groups) {
				for (int w = g.from(); w < g.to(); w++) {
					for (float through : new float[] {0, 0.5f}) {
						sung++;
						double[] y = upDown(g, l.widths(), ArcLayout.focus(groups, w + through, view));
						// Above where you look by the line's height over it (8 at most), below it by 5 at least
						double up = Math.toDegrees(Math.atan(-y[0] * unit)) + ABOVE_MAX;
						double down = Math.toDegrees(Math.atan(y[1] * unit)) - ABOVE_MIN;
						if (up > fov / 2 || down > fov / 2) {
							off++;
						}
					}
				}
			}
		}
		return new int[] {off, sung};
	}

	@Test
	void withABigSizeOrALowerFovTheWordsBeingSungStayOnTheScreen() {
		int before = 0;
		for (float[] v : new float[][] {{2, 70, 1920, 1080}, {2, 70, 960, 1050}, {2.5f, 70, 1920, 1080}, {2.5f, 70, 3840, 1080},
				{1.5f, 50, 1920, 1080}, {1.5f, 50, 3840, 1080}, {3, 90, 960, 1050}}) {
			int[] off = sungOffScreen(v[0], v[1], (int) v[2], (int) v[3], true);
			int was = sungOffScreen(v[0], v[1], (int) v[2], (int) v[3], false)[0];
			// The scroll follows the groups round the one being sung, so a word at a row's raised end may still touch an edge
			assertTrue(off[0] * 1000 <= off[1], String.format("Size %.1f, FOV %.0f, %.0fx%.0f: %d of %d words sung off the screen (%d without scrolling)",
					v[0], v[1], v[2], v[3], off[0], off[1], was));
			before += was;
		}
		assertTrue(before > 300, "without scrolling for the view, " + before + " words were sung off the screen");
	}

	/** At the usual Size, the Karaoke look's 1.5 and any wider view, a line scrolls only past nine rows, as before. */
	@Test
	void whereTheLinesFitTheScreenNothingChanges() {
		for (float[] v : new float[][] {{1, 70}, {1, 90}, {1, 110}, {1.5f, 70}, {1.5f, 110}, {1, 60}}) {
			float view = LyricsRenderer.arcView(true, v[0], v[1]);
			float room = LyricsRenderer.arcRoom(true, v[0], LyricsRenderer.tanHalfWide(v[1], 1920, 1080));
			for (Line l : lines(200)) {
				List<ArcLayout.Group> groups = ArcLayout.fit(l.widths(), l.pause(), l.tight(), SPACE, l.seed(), l.rank(), room);
				for (int w = 0; w < l.widths().length; w++) {
					assertEquals(ArcLayout.focus(groups, w), ArcLayout.focus(groups, w, view), "Size " + v[0] + ", FOV " + v[1]);
				}
			}
		}
	}

	/** A view too small to measure (a broken FOV value) never scrolls a line that wouldn't scroll anyway. */
	@Test
	void anUnusableViewChangesNothing() {
		assertEquals(Float.POSITIVE_INFINITY, LyricsRenderer.arcView(true, 1, Double.NaN));
		float[] widths = {20, 30, 25, 40, 10, 35, 28, 31};
		List<ArcLayout.Group> groups = ArcLayout.place(widths, ArcLayout.rows(widths, new boolean[widths.length]), SPACE, 7, false);
		assertTrue(groups.size() > 1);
		assertEquals(0, ArcLayout.focus(groups, 1, Float.NaN));
		assertEquals(0, ArcLayout.focus(groups, 1, Float.POSITIVE_INFINITY));
	}
}
