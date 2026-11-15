package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

class WrapTest {
	/** 6 units per character, nothing for the marks that sit on a Thai letter or under a Latin one. */
	private static final ToDoubleFunction<String> WIDTH = s -> {
		int n = 0;
		for (int cp : s.codePoints().toArray()) {
			int type = Character.getType(cp);
			if (type != Character.NON_SPACING_MARK) {
				n++;
			}
		}
		return n * 6.0;
	};

	/** The wrap as it was before Thai and long words were handled, kept here to show normal lines come out the same. */
	private static List<String> oldWrap(String text, float maxWidth) {
		List<String> rows = new ArrayList<>();
		if (WIDTH.applyAsDouble(text) <= maxWidth) {
			rows.add(text);
			return rows;
		}
		int rowsNeeded = (int) Math.ceil(WIDTH.applyAsDouble(text) / maxWidth);
		float target = (float) Math.min(maxWidth, WIDTH.applyAsDouble(text) / rowsNeeded + 12);
		StringBuilder row = new StringBuilder();
		for (String word : text.split(" ")) {
			if (word.isEmpty()) {
				continue;
			}
			List<String> pieces = LyricsRenderer.breakPoints(word);
			for (int p = 0; p < pieces.size(); p++) {
				String piece = pieces.get(p);
				String candidate = row.isEmpty() ? piece : row + (p == 0 ? " " : "") + piece;
				if (!row.isEmpty() && WIDTH.applyAsDouble(candidate) > target) {
					rows.add(row.toString());
					row.setLength(0);
					row.append(piece);
				} else {
					row.setLength(0);
					row.append(candidate);
				}
			}
		}
		if (!row.isEmpty()) {
			rows.add(row.toString());
		}
		return rows;
	}

	@Test
	void normalLinesWrapExactlyAsBefore() {
		List<String> lines = List.of(
				"I've been walking down this road for such a long time, well-known faces gone",
				"Don't stop believin', hold on to that feelin' - street lights, people",
				"שלום עולם יפה שלי אני אוהב אותך מאוד מאוד היום ותמיד",
				"مرحبا بالعالم الجميل أنا أحبك كثيرا اليوم وكل يوم",
				"我爱你 baby 我爱你我爱你我爱你我爱你我爱你我爱你我爱你");
		for (String line : lines) {
			for (float width : new float[] {120, 200, 300}) {
				assertEquals(oldWrap(line, width), LyricsRenderer.wrap(WIDTH, line, width), line + " @ " + width);
			}
		}
	}

	@Test
	void thaiLineWrapsToTheWidthAndKeepsEveryLetter() {
		String line = "ฉันรักเธอมากกว่าสิ่งใดในโลกนี้ทั้งหมดเลยนะ";
		for (float width : new float[] {40, 60, 90}) {
			List<String> rows = LyricsRenderer.wrap(WIDTH, line, width);
			assertTrue(rows.size() > 1, "wrapped at " + width);
			for (String r : rows) {
				assertTrue(WIDTH.applyAsDouble(r) <= width, "'" + r + "' too wide at " + width);
			}
			assertEquals(line, String.join("", rows));
		}
	}

	@Test
	void aLongWordWithNoSpacesIsCutAtTheWidth() {
		String word = "a".repeat(60);
		List<String> rows = LyricsRenderer.wrap(WIDTH, word, 100);
		assertTrue(rows.size() >= 4, "rows " + rows.size());
		for (String r : rows) {
			assertTrue(WIDTH.applyAsDouble(r) <= 100, r);
		}
		assertEquals(word, String.join("", rows));
	}

	@Test
	void aLongHyphenatedWordFitsToo() {
		List<String> rows = LyricsRenderer.wrap(WIDTH, "ab-" + "c".repeat(20) + " x", 100);
		for (String r : rows) {
			assertTrue(WIDTH.applyAsDouble(r) <= 100, "'" + r + "'");
		}
		assertEquals("ab-" + "c".repeat(20) + "x", String.join("", rows).replace(" ", ""));
	}

	@Test
	void aSingleWideLetterStillComesOut() {
		// an accented letter never splits, even when it alone is wider than the row
		List<String> rows = LyricsRenderer.wrap(s -> s.length() * 50.0, "ééé", 40);
		assertEquals("ééé", String.join("", rows));
	}
}
