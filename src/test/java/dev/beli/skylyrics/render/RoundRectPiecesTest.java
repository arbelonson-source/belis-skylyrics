package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Every piece of a rounded shape is a GUI element the game allocates and checks against the elements before it, each
 * frame. Pills, bars and discs need fewer pieces than four corners and their fills, and make the same picture.
 */
class RoundRectPiecesTest {
	/** A piece: a part of the disc texture, or a flat fill ({@code uw} below 0). */
	private record Piece(int x, int y, int w, int h, int u, int v, int uw, int vh) {
		boolean flat() {
			return uw < 0;
		}
	}

	private static List<Piece> pieces(int x0, int y0, int x1, int y1, int c, int q) {
		List<Piece> out = new ArrayList<>();
		RoundRect.pieces(x0, y0, x1, y1, c, q, new RoundRect.Pieces() {
			@Override
			public void disc(int x, int y, int w, int h, int u, int v, int uw, int vh) {
				out.add(new Piece(x, y, w, h, u, v, uw, vh));
			}

			@Override
			public void fill(int ax, int ay, int bx, int by) {
				out.add(new Piece(ax, ay, bx - ax, by - ay, 0, 0, -1, -1));
			}
		});
		return out;
	}

	/** The pieces as the bar drew them before: four corners and up to three fills, whatever the shape. */
	private static List<Piece> before(int x0, int y0, int x1, int y1, int c, int q) {
		List<Piece> out = new ArrayList<>();
		out.add(new Piece(x0, y0, c, c, 0, 0, q, q));
		out.add(new Piece(x1 - c, y0, c, c, q, 0, q, q));
		out.add(new Piece(x0, y1 - c, c, c, 0, q, q, q));
		out.add(new Piece(x1 - c, y1 - c, c, c, q, q, q, q));
		if (x1 - c > x0 + c) {
			out.add(new Piece(x0 + c, y0, x1 - c - (x0 + c), y1 - y0, 0, 0, -1, -1));
		}
		if (y1 - c > y0 + c) {
			out.add(new Piece(x0, y0 + c, c, y1 - c - (y0 + c), 0, 0, -1, -1));
			out.add(new Piece(x1 - c, y0 + c, c, y1 - c - (y0 + c), 0, 0, -1, -1));
		}
		return out;
	}

	/**
	 * What each pixel of the box shows: how many pieces cover its middle, and the point of the disc texture sampled
	 * there (the game stretches a piece's part of the texture over it) or "flat".
	 */
	private static String picture(List<Piece> pieces, int x0, int y0, int x1, int y1) {
		StringBuilder out = new StringBuilder();
		for (int py = y0 - 1; py <= y1; py++) {
			for (int px = x0 - 1; px <= x1; px++) {
				int covered = 0;
				String shows = "";
				for (Piece p : pieces) {
					if (px >= p.x && px < p.x + p.w && py >= p.y && py < p.y + p.h) {
						covered++;
						shows = p.flat() ? "flat" : String.format(Locale.ROOT, "%.6f,%.6f",
								p.u + (px + 0.5 - p.x) * p.uw / p.w, p.v + (py + 0.5 - p.y) * p.vh / p.h);
					}
				}
				out.append(covered).append(shows).append(px == x1 ? '\n' : ' ');
			}
		}
		return out.toString();
	}

	/**
	 * What {@link #picture} reads for each pixel, as numbers: how many pieces cover it, and whether the last one shows
	 * nothing, flat or the disc at which point. Pixels with the same numbers write the same part of the picture, so only
	 * shapes where some pixel differs are written out and compared as pictures (writing every picture took seconds).
	 */
	private static double[] samples(List<Piece> pieces, int x0, int y0, int x1, int y1) {
		double[] out = new double[(y1 - y0 + 2) * (x1 - x0 + 2) * 4];
		int k = 0;
		for (int py = y0 - 1; py <= y1; py++) {
			for (int px = x0 - 1; px <= x1; px++) {
				int covered = 0;
				double kind = 0, u = 0, v = 0;
				for (Piece p : pieces) {
					if (px >= p.x && px < p.x + p.w && py >= p.y && py < p.y + p.h) {
						covered++;
						kind = p.flat() ? 1 : 2;
						u = p.flat() ? 0 : p.u + (px + 0.5 - p.x) * p.uw / p.w;
						v = p.flat() ? 0 : p.v + (py + 0.5 - p.y) * p.vh / p.h;
					}
				}
				out[k++] = covered;
				out[k++] = kind;
				out[k++] = u;
				out[k++] = v;
			}
		}
		return out;
	}

	/** Whether every pixel writes the same in both pictures: the same numbers, or disc points written the same. */
	private static boolean samePixels(double[] a, double[] b) {
		if (a.length != b.length) {
			return false;
		}
		for (int k = 0; k < a.length; k += 4) {
			if (a[k] != b[k] || a[k + 1] != b[k + 1]) {
				return false;
			}
			for (int i = k + 2; i < k + 4; i++) {
				if (Double.doubleToLongBits(a[i]) != Double.doubleToLongBits(b[i])
						&& !String.format(Locale.ROOT, "%.6f", a[i]).equals(String.format(Locale.ROOT, "%.6f", b[i]))) {
					return false;
				}
			}
		}
		return true;
	}

	@Test
	void everyShapeLooksTheSameWithNoMorePiecesThanBefore() {
		int shapes = 0;
		for (int w = 1; w <= 40; w++) {
			for (int h = 1; h <= 40; h++) {
				for (float r : new float[] {w / 2f, h / 2f, Math.min(w, h) / 2f, 1, 3, 4.4f, 9, 100}) {
					int c = RoundRect.corner(w, h, r);
					if (c <= 0) {
						continue; // one plain fill, as before
					}
					int q = RoundRect.quarter(c);
					int x0 = 7, y0 = 3, x1 = x0 + w, y1 = y0 + h;
					List<Piece> now = pieces(x0, y0, x1, y1, c, q), old = before(x0, y0, x1, y1, c, q);
					String box = w + "x" + h + " corner " + c;
					if (!samePixels(samples(old, x0, y0, x1, y1), samples(now, x0, y0, x1, y1))) {
						assertEquals(picture(old, x0, y0, x1, y1), picture(now, x0, y0, x1, y1), box);
					}
					assertTrue(now.size() <= old.size(), box + ": " + now.size() + " pieces, " + old.size() + " before");
					shapes++;
				}
			}
		}
		assertTrue(shapes > 5000, "shapes checked: " + shapes);
	}

	private static int count(int w, int h, float r) {
		int c = RoundRect.corner(w, h, r);
		return c <= 0 ? 1 : pieces(0, 0, w, h, c, RoundRect.quarter(c)).size();
	}

	@Test
	void roundShapesTakeFewPieces() {
		assertEquals(1, count(36, 36, 18), "a disc (the cover without art, the record's rings)");
		assertEquals(3, count(600, 58, 29), "an even pill (the bar, its border)");
		assertEquals(3, count(6, 20, 3), "an upright bar (equaliser, signal)");
		assertEquals(7, count(120, 36, 9), "a box with small corners, as before");
	}

	/**
	 * The bar's shapes in one frame at GUI scale 3 with no cover art, as {@link NowPlayingHud} draws them: the border
	 * and fill of the pill, the round cover, the equaliser and signal bars, and the highlight behind the words.
	 */
	@Test
	void theBarsShapesInAFrameTakeFewPieces() {
		int s = 3;
		int ph = Math.round(TopBarLayout.PILL_H * s), pw = 600, b = Math.max(1, Math.round(2 / 3f * s));
		int barW = Math.round(TopBarLayout.BAR_W * s), art = Math.round(TopBarLayout.ART * s);
		int total = count(pw, ph, ph / 2f) + count(pw - 2 * b, ph - 2 * b, ph / 2f - b) + count(art, art, art / 2f);
		for (int bh : new int[] {6, 11, 16, 20}) { // equaliser bars, 2 to 20/3 GUI units tall
			total += count(barW, bh, barW / 2f);
		}
		for (float sh : new float[] {5 / 3f, 11 / 3f, 17 / 3f, 23 / 3f}) { // signal bars
			total += count(barW, Math.max(barW, Math.round(sh * s)), barW / 2f);
		}
		total += count(150, 12 * s, 3 * s); // highlight
		System.out.println("bar pieces per frame at GUI scale 3: " + total);
		assertTrue(total <= 40, total + " GUI elements a frame for the bar's shapes");
	}
}
