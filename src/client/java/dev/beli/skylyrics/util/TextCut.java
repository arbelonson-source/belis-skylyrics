package dev.beli.skylyrics.util;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Cuts text on whole characters as people see them, never inside an emoji or a letter with its accents. */
public final class TextCut {
	private TextCut() {
	}

	/** Every index where a cut keeps whole characters together, from 0 to the length. */
	private static List<Integer> boundaries(String text) {
		List<Integer> out = new ArrayList<>();
		BreakIterator it = BreakIterator.getCharacterInstance();
		it.setText(text);
		out.add(0);
		int b;
		while ((b = it.next()) != BreakIterator.DONE) {
			out.add(b);
		}
		return out;
	}

	/** The longest start of {@code text} that fits, cut only between whole characters. */
	public static String prefix(String text, Predicate<String> fits) {
		List<Integer> cuts = boundaries(text);
		int lo = 0, hi = cuts.size() - 1;
		while (lo < hi) {
			int mid = (lo + hi + 1) / 2;
			if (fits.test(text.substring(0, cuts.get(mid)))) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return text.substring(0, cuts.get(lo));
	}

	/** At most {@code max} UTF-16 units, cut only between whole characters. */
	public static String limit(String text, int max) {
		return text.length() <= max ? text : prefix(text, t -> t.length() <= max);
	}

	/** The longest end of {@code text} that fits, cut only between whole characters. */
	public static String suffix(String text, Predicate<String> fits) {
		for (int cut : boundaries(text)) {
			String s = text.substring(cut);
			if (fits.test(s)) {
				return s;
			}
		}
		return "";
	}

	/** The first whole character people see in {@code text} (a flag, an emoji with its joiners, a letter with its accents), after leading space is dropped; "" if there is none. */
	public static String first(String text) {
		String s = text.strip();
		return s.isEmpty() ? "" : firstOf(s);
	}

	/** Longest first character kept whole: a family emoji is 11 units, so a bigger one is a pile of marks. */
	public static final int MAX_INITIAL = 12;

	/** {@link #first}, but a character with more than {@link #MAX_INITIAL} units (hundreds of combining marks) is cut to its base character. */
	public static String initial(String text) {
		String s = text.strip();
		if (s.isEmpty()) {
			return "";
		}
		String f = firstOf(s.length() > 64 ? s.substring(0, 64) : s);
		return f.length() <= MAX_INITIAL ? f : f.substring(0, f.offsetByCodePoints(0, 1));
	}

	private static String firstOf(String s) {
		BreakIterator it = BreakIterator.getCharacterInstance();
		it.setText(s);
		return s.substring(0, it.next());
	}
}
