package dev.beli.skylyrics.render;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.List;

/** Cuts a line into the pieces the Top bar draws one by one, and says where each one sits on screen. */
final class TopBarWords {
	private TopBarWords() {
	}

	/** One piece of the line; {@code gap} is false when it joins the piece before it without a space. */
	record Piece(String text, boolean gap) {}

	/** The line's words, separated by spaces. */
	static List<Piece> split(String text) {
		List<Piece> out = new ArrayList<>();
		for (String part : text.trim().split(" +")) {
			if (!part.isEmpty()) {
				out.add(new Piece(part, true));
			}
		}
		return out;
	}

	/** Whether the line reads right to left: its first strong letter decides, as in the game's own text. */
	static boolean baseRtl(String text) {
		return !text.isEmpty() && !new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight();
	}

	/** Logical piece numbers in the order they are seen from left to right. */
	static int[] visualOrder(List<Piece> pieces) {
		int n = pieces.size();
		int[] order = new int[n];
		if (n == 0) {
			return order;
		}
		StringBuilder joined = new StringBuilder();
		int[] at = new int[n];
		for (int i = 0; i < n; i++) {
			if (i > 0 && pieces.get(i).gap()) {
				joined.append(' ');
			}
			at[i] = joined.length();
			joined.append(pieces.get(i).text());
		}
		Bidi bidi = new Bidi(joined.toString(), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
		byte[] levels = new byte[n];
		Integer[] ids = new Integer[n];
		for (int i = 0; i < n; i++) {
			String t = pieces.get(i).text();
			int pick = at[i];
			for (int k = 0; k < t.length(); k++) {
				if (strong(t.charAt(k))) {
					pick = at[i] + k;
					break;
				}
			}
			levels[i] = (byte) bidi.getLevelAt(pick);
			ids[i] = i;
		}
		Bidi.reorderVisually(levels, 0, ids, 0, n);
		for (int i = 0; i < n; i++) {
			order[i] = ids[i];
		}
		return order;
	}

	private static boolean strong(char c) {
		byte d = Character.getDirectionality(c);
		return d == Character.DIRECTIONALITY_LEFT_TO_RIGHT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT
				|| d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC || d == Character.DIRECTIONALITY_EUROPEAN_NUMBER
				|| d == Character.DIRECTIONALITY_ARABIC_NUMBER;
	}

	/**
	 * Where each piece starts, counted from the left edge of the line, indexed by logical piece number. Pieces
	 * are placed in {@link #visualOrder}; {@code gap} separates two neighbours unless they are consecutive
	 * pieces that join without a space.
	 */
	static float[] leftEdges(List<Piece> pieces, float[] widths, float gap) {
		int[] order = visualOrder(pieces);
		float[] x = new float[order.length];
		float at = 0;
		for (int k = 0; k < order.length; k++) {
			int i = order[k];
			if (k > 0) {
				int before = order[k - 1];
				boolean joined = i == before + 1 && !pieces.get(i).gap() || before == i + 1 && !pieces.get(before).gap();
				at += joined ? 0 : gap;
			}
			x[i] = at;
			at += widths[i];
		}
		return x;
	}
}
