package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The arc layout puts words where they are read on screen: a mixed-direction line keeps each phrase reading its own way. */
class ArcMixedOrderTest {
	private static List<TopBarWords.Piece> pieces(String... words) {
		List<TopBarWords.Piece> out = new ArrayList<>();
		for (String w : words) {
			out.add(new TopBarWords.Piece(w, true));
		}
		return out;
	}

	/** Each word's centre across the screen, by logical word number. */
	private static float[] xs(List<ArcLayout.Group> groups, int n) {
		float[] x = new float[n];
		for (ArcLayout.Group g : groups) {
			for (int w = g.from(); w < g.to(); w++) {
				x[w] = g.x() + g.wordX()[w - g.from()] * g.scale() * (float) Math.cos(Math.toRadians(g.angle()));
			}
		}
		return x;
	}

	private static List<ArcLayout.Group> lay(float[] widths, long seed, List<TopBarWords.Piece> pieces) {
		return ArcLayout.place(widths, ArcLayout.rows(widths, new boolean[widths.length]), 3f, seed, null,
				TopBarWords.visualRanks(pieces));
	}

	private static float[] equal(int n) {
		float[] w = new float[n];
		java.util.Arrays.fill(w, 12f); // narrow enough for one row
		return w;
	}

	@Test
	void hebrewLineKeepsAnEnglishPhraseReadingLeftToRight() {
		for (long seed = 1; seed <= 40; seed++) {
			float[] x = xs(lay(equal(5), seed, pieces("אני", "אוהב", "את", "NYC", "2024")), 5);
			assertTrue(x[3] < x[4] && x[4] < x[2] && x[2] < x[1] && x[1] < x[0], "seed " + seed + ": " + java.util.Arrays.toString(x));
		}
	}

	@Test
	void englishLineKeepsAHebrewPhraseReadingRightToLeft() {
		for (long seed = 1; seed <= 40; seed++) {
			float[] x = xs(lay(equal(4), seed, pieces("I", "love", "שלום", "עולם")), 4);
			assertTrue(x[0] < x[1] && x[1] < x[3] && x[3] < x[2], "seed " + seed + ": " + java.util.Arrays.toString(x));
		}
	}

	@Test
	void aGroupIsCutWhereTheNextWordIsNotBesideTheLast() {
		// "love" (left to right) then "שלום" (right to left, so seen after עולם): never one group, whatever the seed picks
		for (long seed = 1; seed <= 200; seed++) {
			for (ArcLayout.Group g : lay(equal(4), seed, pieces("I", "love", "שלום", "עולם"))) {
				assertTrue(!(g.from() <= 1 && g.to() > 2), "seed " + seed + " grouped " + g.from() + ".." + g.to());
			}
		}
	}

	/** A line of one direction comes out as it did when the whole line was simply mirrored. */
	@Test
	void plainLinesAreUnchanged() {
		for (boolean rtl : new boolean[] {false, true}) {
			for (int n : new int[] {1, 2, 5, 9, 14}) {
				float[] widths = new float[n];
				boolean[] tight = new boolean[n];
				for (int i = 0; i < n; i++) {
					widths[i] = 8 + (i * 7) % 13;
					tight[i] = i % 4 == 1;
				}
				int[] rows = ArcLayout.rows(widths, new boolean[n], tight);
				for (long seed = 1; seed <= 25; seed++) {
					List<ArcLayout.Group> now = ArcLayout.place(widths, rows, 3f, seed, rtl, tight);
					List<ArcLayout.Group> old = oldPlace(widths, rows, 3f, seed, rtl, tight);
					assertEquals(old.size(), now.size());
					for (int g = 0; g < old.size(); g++) {
						ArcLayout.Group a = old.get(g), b = now.get(g);
						assertEquals(a.from(), b.from());
						assertEquals(a.to(), b.to());
						assertEquals(a.x(), b.x(), 1e-3f);
						assertEquals(a.y(), b.y(), 1e-3f);
						assertEquals(a.angle(), b.angle(), 1e-3f);
						assertEquals(a.scale(), b.scale(), 1e-3f);
						for (int k = 0; k < a.wordX().length; k++) {
							assertEquals(a.wordX()[k], b.wordX()[k], 1e-3f, "rtl " + rtl + " n " + n + " seed " + seed + " group " + g);
						}
					}
				}
			}
		}
	}

	/** The placement from before: groups laid left to right in sung order, then every x mirrored for a right-to-left line. */
	private static List<ArcLayout.Group> oldPlace(float[] widths, int[] rowStarts, float space, long seed, boolean rtl, boolean[] tight) {
		int n = widths.length;
		List<ArcLayout.Group> out = new ArrayList<>();
		float flip = ArcLayout.unit(seed, 0, 6) < 0.5f ? 1 : -1;
		int groupNo = 0;
		float rowY = 0, above = 0;
		for (int r = 0; r < rowStarts.length; r++) {
			int from = rowStarts[r], to = r + 1 < rowStarts.length ? rowStarts[r + 1] : n;
			List<int[]> spans = new ArrayList<>();
			for (int i = from; i < to; ) {
				int end = Math.min(to, i + ArcLayout.groupSize(seed, i));
				spans.add(new int[] {i, end});
				i = end;
			}
			float[] scale = new float[spans.size()], advance = new float[spans.size()];
			float rowWidth = ArcLayout.GAP * (spans.size() - 1), biggest = 0;
			for (int g = 0; g < spans.size(); g++) {
				int[] s = spans.get(g);
				scale[g] = ArcLayout.groupScale(seed, s[0]);
				biggest = Math.max(biggest, scale[g]);
				advance[g] = ArcLayout.width(widths, tight, s[0], s[1], space) * (1 + (scale[g] - 1) * 0.5f);
				rowWidth += advance[g];
			}
			if (r > 0) {
				rowY += ArcLayout.ROW_PITCH * Math.max(1, (above + biggest) / 2);
			}
			above = biggest;
			float x = -rowWidth / 2, half = Math.max(1, rowWidth / 2), curve = ArcLayout.rise(rowWidth) / (half * half);
			for (int g = 0; g < spans.size(); g++, groupNo++) {
				int[] s = spans.get(g);
				float cx = x + advance[g] / 2;
				x += advance[g] + ArcLayout.GAP;
				if (rtl) {
					cx = -cx;
				}
				float y = rowY - curve * cx * cx + (ArcLayout.unit(seed, s[0], 5) * 2 - 1) * ArcLayout.STEP;
				float tangent = (float) Math.toDegrees(Math.atan(-2 * curve * cx));
				float own = (groupNo % 2 == 0 ? flip : -flip) * (3f + 7f * ArcLayout.unit(seed, s[0], 4));
				float angle = Math.clamp(ArcLayout.FOLLOW * tangent + own, -ArcLayout.ROLL_LIMIT, ArcLayout.ROLL_LIMIT);
				float inner = ArcLayout.width(widths, tight, s[0], s[1], space);
				float[] wordX = new float[s[1] - s[0]];
				float wx = -inner / 2;
				for (int i = s[0]; i < s[1]; i++) {
					float c = wx + widths[i] / 2;
					wordX[i - s[0]] = rtl ? -c : c;
					wx += widths[i] + (tight[i] ? 0 : space);
				}
				out.add(new ArcLayout.Group(s[0], s[1], cx, y, angle, scale[g], wordX));
			}
		}
		float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
		for (ArcLayout.Group g : out) {
			top = Math.min(top, g.y() - 4f * g.scale());
			bottom = Math.max(bottom, g.y() + 4f * g.scale());
		}
		float shift = (top + bottom) / 2;
		out.replaceAll(g -> new ArcLayout.Group(g.from(), g.to(), g.x(), g.y() - shift, g.angle(), g.scale(), g.wordX()));
		return out;
	}
}
