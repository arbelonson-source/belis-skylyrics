package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * An Arc line is cut into rows about {@link ArcLayout#ROW_WIDTH} units wide. With a big Size, a low FOV or a narrow
 * window (a window tiled to half the screen) such a row is wider than the view, and the first and last words of the line
 * are off the left and right edges. This is geometry only: how far across the view a placed line reaches, in the
 * layout's own units, whatever font its words were measured in.
 */
class ArcViewFitTest {
	private static final float SPACE = 3f;
	/** The renderer draws an Arc line at 0.7 of the stacked lines' size, a row of those {@code ROW_H} = 10 units tall. */
	private static final double ARC_SIZE = 0.7, ROW_H = 10;
	/** Each Arc line sits up to this many degrees left or right of where you look. */
	private static final double SIDE = 6;

	/** What one layout unit covers, as a tangent of the view, in the sky at this Size. */
	private static double unitTan(float size) {
		return ARC_SIZE * Math.tan(Math.toRadians(LyricsRenderer.rowAngle(true, size))) / ROW_H;
	}

	/** How far left or right of the block's middle any group is drawn (its tilted box), in layout units. */
	private static float reach(List<ArcLayout.Group> groups, float[] widths, boolean[] tight) {
		float worst = 0;
		for (ArcLayout.Group g : groups) {
			float half = ArcLayout.width(widths, tight, g.from(), g.to(), SPACE) * g.scale() / 2;
			double a = Math.toRadians(g.angle());
			float across = (float) (half * Math.cos(a) + 4 * g.scale() * Math.abs(Math.sin(a)));
			worst = Math.max(worst, Math.abs(g.x()) + across);
		}
		return worst;
	}

	private record Line(float[] widths, boolean[] pause, boolean[] tight, int[] rank, long seed) {
		float widest() {
			float w = 0;
			for (float x : widths) {
				w = Math.max(w, x);
			}
			return w;
		}
	}

	/** Lines of 3 to 30 words of 6 to 40 units (a short "a" to a long "lanterns"), some right to left, some Chinese. */
	private static List<Line> lines(int count) {
		Random rnd = new Random(20261122);
		List<Line> out = new ArrayList<>();
		for (int t = 0; t < count; t++) {
			boolean cjk = t % 7 == 3;
			int n = cjk ? 8 + rnd.nextInt(30) : 3 + rnd.nextInt(28);
			float[] w = new float[n];
			boolean[] pause = new boolean[n], tight = new boolean[n];
			for (int i = 0; i < n; i++) {
				w[i] = cjk ? 10.5f : 6 + rnd.nextInt(35);
				pause[i] = rnd.nextInt(7) == 0;
				tight[i] = cjk && i + 1 < n && rnd.nextInt(9) != 0;
			}
			out.add(new Line(w, pause, tight, ArcLayout.straight(n, t % 5 == 1), ArcLayout.seed(t, "line " + t)));
		}
		return out;
	}

	private static List<ArcLayout.Group> fitted(Line l, float room) {
		return ArcLayout.fit(l.widths(), l.pause(), l.tight(), SPACE, l.seed(), l.rank(), room);
	}

	/** Every group's words, place, tilt and size, and where its words sit in it. */
	private static String dump(List<ArcLayout.Group> groups) {
		StringBuilder b = new StringBuilder();
		for (ArcLayout.Group g : groups) {
			b.append(g.from()).append('-').append(g.to()).append(' ').append(g.x()).append(',').append(g.y()).append(' ')
					.append(g.angle()).append(' ').append(g.scale()).append(' ').append(Arrays.toString(g.wordX())).append('\n');
		}
		return b.toString();
	}

	private static List<ArcLayout.Group> asBefore(Line l) {
		return ArcLayout.place(l.widths(), ArcLayout.rows(l.widths(), l.pause(), l.tight()), SPACE, l.seed(), l.tight(), l.rank());
	}

	@Test
	void longLinesStayOnTheScreenAtABigSizeALowFovOrInANarrowWindow() {
		int checked = 0, wouldRunOff = 0;
		List<Line> lines = lines(300);
		for (float size : new float[] {1, 1.5f, 2, 2.5f, 3}) {
			for (double fov : new double[] {50, 70, 90}) {
				for (int[] window : new int[][] {{1920, 1080}, {1280, 720}, {1024, 768}, {960, 1050}, {800, 800}}) {
					double tanHalf = LyricsRenderer.tanHalfWide(fov, window[0], window[1]);
					double halfView = Math.toDegrees(Math.atan(tanHalf));
					float room = LyricsRenderer.arcRoom(true, size, tanHalf);
					for (Line l : lines) {
						// A single word wider than the view can't be helped by cutting the line; leave those out
						if (l.widest() * ArcLayout.MAX_SCALE / 2 + 2 > room) {
							continue;
						}
						List<ArcLayout.Group> groups = fitted(l, room);
						int covered = 0;
						for (ArcLayout.Group g : groups) {
							assertEquals(covered, g.from(), "every word stays, in order");
							covered = g.to();
						}
						assertEquals(l.widths().length, covered);
						double end = SIDE + Math.toDegrees(Math.atan(reach(groups, l.widths(), l.tight()) * unitTan(size)));
						assertTrue(end <= halfView, String.format("size %.1f, FOV %.0f, %dx%d: the line reaches %.1f degrees out, the view %.1f",
								size, fov, window[0], window[1], end, halfView));
						checked++;
						if (SIDE + Math.toDegrees(Math.atan(reach(asBefore(l), l.widths(), l.tight()) * unitTan(size))) > halfView) {
							wouldRunOff++;
						}
					}
				}
			}
		}
		System.out.println("lines checked " + checked + ", of them cut as before would run off " + wouldRunOff);
		assertTrue(checked > 10_000 && wouldRunOff > 1_000, checked + " checked, " + wouldRunOff + " ran off before");
	}

	/** Where the usual rows already fit (the usual Size, the Karaoke look's 1.5, a wide window), a line comes out exactly as before. */
	@Test
	void whereTheUsualRowsFitNothingChanges() {
		for (float[] view : new float[][] {{1, 70, 1920, 1080}, {1, 70, 1280, 720}, {1, 70, 1024, 768}, {1.5f, 70, 1920, 1080}, {1.15f, 90, 1280, 720}}) {
			float room = LyricsRenderer.arcRoom(true, view[0], LyricsRenderer.tanHalfWide(view[1], (int) view[2], (int) view[3]));
			for (Line l : lines(400)) {
				assertEquals(dump(asBefore(l)), dump(fitted(l, room)), Arrays.toString(view));
			}
		}
		for (Line l : lines(50)) {
			assertEquals(dump(asBefore(l)), dump(fitted(l, Float.POSITIVE_INFINITY)), "no limit");
		}
	}

	/** However cramped the view, the line keeps every word whole: at worst one word to a row, never a word cut up. */
	@Test
	void aCrampedViewStacksTheWordsInsteadOfLosingAny() {
		float room = LyricsRenderer.arcRoom(true, 3, LyricsRenderer.tanHalfWide(30, 600, 800));
		for (Line l : lines(100)) {
			List<ArcLayout.Group> groups = fitted(l, room);
			int covered = 0;
			for (ArcLayout.Group g : groups) {
				assertEquals(covered, g.from());
				covered = g.to();
			}
			assertEquals(l.widths().length, covered);
		}
		assertTrue(room > 0, "room " + room);
	}
}
