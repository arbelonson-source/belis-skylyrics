package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.resources.language.FormattedBidiReorder;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The top bar's song name and the Record card's title, artist and lyric line are measured the way the game draws them:
 * it joins Arabic letters into the forms they take inside a word, and those are far narrower than the same letters
 * standing alone. Measured unjoined, an Arabic title left a wide empty gap before the "·  artist" after it, the bar
 * grew wider than its text, a title that fits was cut with "…", and the card's Arabic line sat off its middle.
 * <p>
 * The widths are the smooth fonts' real ones: the TrueType fonts the mod bundles, in the order lyrics.json and
 * bold.json list them (the first one that has a letter draws it), at their size of 10.5, like the game's TrueType
 * glyphs (which round each advance to a sixth of a unit; both sides here use the same widths, so that can't matter).
 */
class JoinedArabicWidthTest {
	/** Made up: "hello, green cat" and "band of the seven moons" (with a lam-alef, which joins two letters into one). */
	private static final String TITLE = "سلام يا قطة خضراء";
	private static final String ARTIST = "فرقة الأقمار السبعة";
	/** Persian with a zero-width non-joiner, which keeps two letters apart. */
	private static final String PERSIAN = "می‌خواهم";

	private static StringSplitter smooth, bold;

	private static InputStream open(String path) {
		InputStream in = JoinedArabicWidthTest.class.getResourceAsStream(path);
		if (in == null) {
			throw new IllegalStateException("missing resource " + path);
		}
		return in;
	}

	/** The fonts a font file lists, at the size it gives them. */
	private static List<Font> fonts(String name) throws Exception {
		JsonObject json;
		try (var r = new InputStreamReader(open("/assets/skylyrics/font/" + name + ".json"), StandardCharsets.UTF_8)) {
			json = JsonParser.parseReader(r).getAsJsonObject();
		}
		List<Font> fonts = new ArrayList<>();
		for (JsonElement e : json.getAsJsonArray("providers")) {
			JsonObject p = e.getAsJsonObject();
			if (!"ttf".equals(p.get("type").getAsString())) {
				continue;
			}
			String file = p.get("file").getAsString().replace("skylyrics:", "");
			try (InputStream in = open("/assets/skylyrics/font/" + file)) {
				fonts.add(Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(p.get("size").getAsFloat()));
			}
		}
		return fonts;
	}

	/** Letter widths from the first font that has the letter; one the test's letters never need is a plain 6. */
	private static StringSplitter splitter(List<Font> fonts) {
		FontRenderContext frc = new FontRenderContext(null, false, true);
		return new StringSplitter((cp, style) -> {
			for (Font f : fonts) {
				if (f.canDisplay(cp)) {
					return f.createGlyphVector(frc, Character.toChars(cp)).getGlyphMetrics(0).getAdvance();
				}
			}
			return 6;
		});
	}

	@BeforeAll
	static void readTheFonts() throws Exception {
		System.setProperty("java.awt.headless", "true");
		List<Font> lyrics = fonts("lyrics");
		smooth = splitter(lyrics);
		bold = splitter(fonts("bold"));
		// The letters really come from the bundled fonts, joined forms too: these widths are what the game draws
		String all = TITLE + ARTIST + PERSIAN + joined(TITLE + " " + ARTIST + " " + PERSIAN);
		all.codePoints().filter(cp -> cp != ' ' && cp != 0x200C)
				.forEach(cp -> assertTrue(lyrics.stream().anyMatch(f -> f.canDisplay(cp)), "no bundled font has U+" + Integer.toHexString(cp)));
	}

	private static String joined(String text) {
		StringBuilder out = new StringBuilder();
		FormattedBidiReorder.reorder(FormattedText.of(text, Style.EMPTY), false).accept((i, style, cp) -> {
			out.appendCodePoint(cp);
			return true;
		});
		return out.toString();
	}

	/** The width of what the game draws for this text: its visual order (Arabic joined), without the invisible marks. */
	private static float drawn(StringSplitter splitter, String text, boolean rtlGame) {
		return splitter.stringWidth(LyricsFont.dropMarks(FormattedBidiReorder.reorder(FormattedText.of(text, Style.EMPTY), rtlGame)));
	}

	private static void measuredAsDrawn(StringSplitter splitter, String text, String what) {
		for (boolean rtlGame : new boolean[] {false, true}) {
			float drawn = drawn(splitter, text, rtlGame);
			assertEquals(drawn, LyricsFont.drawnWidth(splitter, Style.EMPTY, text), 0.01f,
					what + (rtlGame ? " in a right-to-left game" : "") + ": measured unjoined it is "
							+ LyricsFont.width(splitter, Style.EMPTY, text) + " wide, drawn " + drawn);
		}
	}

	@Test
	void anArabicTitleIsAsWideAsTheGameDrawsIt() {
		measuredAsDrawn(smooth, TITLE, "an Arabic title");
		measuredAsDrawn(smooth, "Night Drive " + TITLE, "an English title with Arabic in it");
		measuredAsDrawn(smooth, PERSIAN, "a Persian title");
		measuredAsDrawn(smooth, "سلام", "a lam-alef");
	}

	@Test
	void theArtistAfterTheTitleIsMeasuredAsDrawnToo() {
		// The "·  artist" part, with the marks that keep the dot next to the title
		measuredAsDrawn(smooth, NowPlayingHud.dotted(ARTIST), "an Arabic artist after the dot");
		measuredAsDrawn(smooth, NowPlayingHud.dotted(NowPlayingHud.subText(ARTIST, "no lyrics", "Demo")), "an Arabic artist with the status");
	}

	@Test
	void theCardsBoldTitleIsMeasuredAsDrawn() {
		measuredAsDrawn(bold, TITLE, "the Record card's bold Arabic title");
	}

	@Test
	void theGapWasWideEnoughToSee() {
		// The bar draws its song name at 0.75: the dot sat this far past the title's end, in GUI units
		float gap = (LyricsFont.width(smooth, Style.EMPTY, TITLE) - drawn(smooth, TITLE, false)) * 0.75f;
		assertTrue(gap > 8, "gap " + gap);
		assertEquals(drawn(smooth, TITLE, false), LyricsFont.drawnWidth(smooth, Style.EMPTY, TITLE), 0.01f);
	}

	@Test
	void textWithoutArabicIsMeasuredAsBefore() {
		for (String text : new String[] {"Paper Lanterns", "שיר ארוך", "Mira Vale  ·  no lyrics", "夕焼け"}) {
			assertEquals(LyricsFont.width(smooth, Style.EMPTY, text), LyricsFont.drawnWidth(smooth, Style.EMPTY, text), text);
		}
		String plain = "Paper Lanterns";
		assertSame(plain, LyricsFont.joined(plain), "no copy for text without Arabic");
	}
}
