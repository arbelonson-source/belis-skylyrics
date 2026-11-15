package dev.beli.skylyrics.render;

import java.util.List;

/**
 * Where the words of one top bar line sit, from the line's start edge, in GUI units. It depends only on the line's
 * text, how wide each word is and the gap between words, so it is built once per line and only the sung state is
 * worked out each frame ({@link #sung}).
 */
final class LineLayout {
	/** How wide a word is, before the text scale. */
	interface Measure {
		float width(String piece, boolean lineRtl);
	}

	final String[] texts;
	final float[] x;
	final float[] w;
	/** How many characters of the words come before each word. */
	private final int[] seen;
	private final int letters;
	final boolean rtl;
	/** How far the words reach from the line's start edge. */
	final float extent;
	/** The gap between the line's words. */
	final float gap;

	private LineLayout(String[] texts, float[] x, float[] w, int[] seen, int letters, boolean rtl, float extent, float gap) {
		this.texts = texts;
		this.x = x;
		this.w = w;
		this.seen = seen;
		this.letters = letters;
		this.rtl = rtl;
		this.extent = extent;
		this.gap = gap;
	}

	int size() {
		return texts.length;
	}

	/** Whether word {@code i} counts as sung when {@code sungShare} of the line's letters are. */
	boolean sung(int i, float sungShare) {
		return seen[i] <= sungShare * letters || sungShare >= 1;
	}

	static LineLayout build(String text, float scale, float gap, Measure measure) {
		int letters = 0;
		for (int i = 0; i < text.length(); i++) {
			letters += text.charAt(i) == ' ' ? 0 : 1;
		}
		List<TopBarWords.Piece> parts = TopBarWords.split(text);
		int n = parts.size();
		boolean rtl = n > 0 && TopBarWords.baseRtl(text);
		float[] widths = new float[n];
		for (int i = 0; i < n; i++) {
			widths[i] = measure.width(parts.get(i).text(), rtl) * scale;
		}
		// Placed as they are seen (a Hebrew word in an English line, numbers in a Hebrew one), then measured from
		// the start edge: the left for a left-to-right line, the right for a right-to-left one
		float[] left = n == 0 ? new float[0] : TopBarWords.leftEdges(parts, widths, gap);
		float total = 0;
		for (int i = 0; i < n; i++) {
			total = Math.max(total, left[i] + widths[i]);
		}
		String[] texts = new String[n];
		float[] x = new float[n];
		int[] seen = new int[n];
		int count = 0;
		float end = 0;
		for (int i = 0; i < n; i++) {
			texts[i] = parts.get(i).text();
			x[i] = rtl ? total - left[i] - widths[i] : left[i];
			seen[i] = count;
			count += texts[i].length();
			end = Math.max(end, x[i] + widths[i]);
		}
		return new LineLayout(texts, x, widths, seen, letters, rtl, end, gap);
	}
}
