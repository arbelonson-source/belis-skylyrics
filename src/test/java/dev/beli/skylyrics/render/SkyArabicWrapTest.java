package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Lines layout (and the title screen's lyrics) cut a long line into rows, and the sky draws each row with its Arabic
 * letters joined ({@link LyricsRenderer.Display}, which also centres the row on that width). Joined letters are far
 * narrower than the same letters standing alone, and the rows were measured standing alone: an Arabic line that fits the
 * row broke in two, and a long one took a row more than it needs, each row much shorter than the room.
 * <p>
 * The widths are the bundled fonts' real ones, read as {@link JoinedArabicWidthTest} reads them.
 */
class SkyArabicWrapTest {
	/** Made up: "we walk together under the light of the calm moon in the long night". */
	private static final String LINE = "نمشي معا تحت ضوء القمر الهادئ في الليل الطويل";
	/** The same without its last word. */
	private static final String SHORTER = "نمشي معا تحت ضوء القمر الهادئ في الليل";
	/** Made up, longer: "... at night until the morning comes back to the sleeping city and the birds sing over the sea". */
	private static final String LONG = "نمشي معا تحت ضوء القمر الهادئ في الليل حتى يعود الصباح إلى المدينة النائمة وتغني الطيور فوق البحر";
	/** How wide a row may be in the sky, and in front of you (Floating), in text units. */
	private static final float SKY_ROW = 230, FLOATING_ROW = 190;

	private static StringSplitter smooth, bold;

	@BeforeAll
	static void readTheFonts() throws Exception {
		System.setProperty("java.awt.headless", "true");
		smooth = JoinedArabicWidthTest.splitter(JoinedArabicWidthTest.fonts("lyrics"));
		bold = JoinedArabicWidthTest.splitter(JoinedArabicWidthTest.fonts("bold"));
	}

	/** How wide the sky draws a row, which it is centred on: its letters as drawn, in order, Arabic joined. */
	private static float drawn(StringSplitter splitter, String row) {
		int[] cps = LyricsRenderer.Display.of(row, WorldWords.rightToLeft(row)).cps();
		return LyricsFont.width(splitter, Style.EMPTY, new String(cps, 0, cps.length));
	}

	/** The letters measured as written, each standing alone. */
	private static float alone(StringSplitter splitter, String text) {
		return LyricsFont.width(splitter, Style.EMPTY, text);
	}

	@Test
	void anArabicLineThatFitsTheRowStaysWhole() {
		for (StringSplitter s : new StringSplitter[] {bold, smooth}) {
			// It fits as drawn, but not letter by letter: so the old measure broke it (else this test could not fail)
			assertTrue(drawn(s, LINE) < SKY_ROW && alone(s, LINE) > SKY_ROW, "drawn " + drawn(s, LINE) + ", alone " + alone(s, LINE));
			assertEquals(List.of(LINE), LyricsRenderer.wrap(s, Style.EMPTY, LINE, SKY_ROW));
			assertTrue(drawn(s, SHORTER) < FLOATING_ROW && alone(s, SHORTER) > FLOATING_ROW, "drawn " + drawn(s, SHORTER));
			assertEquals(List.of(SHORTER), LyricsRenderer.wrap(s, Style.EMPTY, SHORTER, FLOATING_ROW));
		}
	}

	@Test
	void aLongArabicLineTakesOnlyTheRowsItNeeds() {
		for (StringSplitter s : new StringSplitter[] {bold, smooth}) {
			List<String> rows = LyricsRenderer.wrap(s, Style.EMPTY, LONG, SKY_ROW);
			StringBuilder widths = new StringBuilder();
			for (String row : rows) {
				widths.append(' ').append(drawn(s, row));
				assertTrue(drawn(s, row) <= SKY_ROW, "row drawn " + drawn(s, row) + " wide: " + row);
			}
			// About 430 units as drawn: two rows; measured letter by letter (about 570) it took three
			assertEquals(2, rows.size(), "rows drawn" + widths + " wide, line " + drawn(s, LONG) + " (alone " + alone(s, LONG) + ")");
			assertEquals(LONG, String.join(" ", rows));
		}
	}

	@Test
	void linesWithoutArabicWrapAsBefore() {
		String[] lines = {
				"We keep on singing all the way across the sky until the morning comes back again",
				"השירים שלנו עולים כל הלילה עד הבוקר ועוד קצת אחרי זה כדי שהשורה תהיה ארוכה מאוד",
				"We keep on singing שיר של לילה ארוך all the way across the sky until the morning",
				"夕焼けの空に歌が流れて 星が一つずつ灯る夜の道を歩いていく"};
		for (String line : lines) {
			for (float row : new float[] {SKY_ROW, FLOATING_ROW, 120}) {
				assertEquals(LyricsRenderer.wrap(t -> alone(bold, t), line, row), LyricsRenderer.wrap(bold, Style.EMPTY, line, row), line);
			}
		}
	}
}
