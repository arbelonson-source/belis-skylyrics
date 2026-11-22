package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** The width cache: the same text is measured once, and anything the width depends on gives a fresh measurement. */
class WidthCacheTest {
	private static final Object FONT = new Object(), STYLE = new Object(), LANG = new Object();

	private final AtomicInteger measures = new AtomicInteger();
	private final WidthCache.Measure measure = (font, style, text) -> {
		measures.incrementAndGet();
		return text.length() * 3.5f;
	};

	@Test
	void theSameTextIsMeasuredOnce() {
		WidthCache cache = new WidthCache(8);
		for (int i = 0; i < 100; i++) {
			assertEquals(17.5f, cache.get(FONT, STYLE, "hello", LANG, measure));
		}
		assertEquals(1, measures.get());
	}

	@Test
	void aDifferentTextStyleFontOrLanguageMisses() {
		WidthCache cache = new WidthCache(8);
		cache.get(FONT, STYLE, "hello", LANG, measure);
		cache.get(FONT, STYLE, "hello!", LANG, measure);
		cache.get(FONT, new Object(), "hello", LANG, measure);
		cache.get(new Object(), STYLE, "hello", LANG, measure);
		cache.get(FONT, STYLE, "hello", new Object(), measure);
		assertEquals(5, measures.get());
		// and every one of them is still remembered
		cache.get(FONT, STYLE, "hello", LANG, measure);
		cache.get(FONT, STYLE, "hello!", LANG, measure);
		assertEquals(5, measures.get());
	}

	@Test
	void aReloadGivesAFreshWidth() {
		WidthCache cache = new WidthCache(8);
		float[] glyph = {3.5f};
		WidthCache.Measure live = (f, s, t) -> t.length() * glyph[0];
		assertEquals(17.5f, cache.get(FONT, STYLE, "hello", LANG, live));
		glyph[0] = 4f; // a resource pack changed the glyphs
		assertEquals(17.5f, cache.get(FONT, STYLE, "hello", LANG, live), "still the remembered one until a reload is seen");
		long before = cache.epoch();
		cache.invalidate();
		assertEquals(before + 1, cache.epoch());
		assertEquals(20f, cache.get(FONT, STYLE, "hello", LANG, live));
		assertEquals(1, cache.size());
	}

	@Test
	void aNewLanguageObjectIsAReload() {
		WidthCache cache = new WidthCache(8);
		cache.language(LANG);
		long before = cache.epoch();
		cache.get(FONT, STYLE, "hello", LANG, measure);
		cache.language(LANG);
		assertEquals(before, cache.epoch(), "the same language object is no reload");
		assertEquals(1, cache.size());
		cache.language(new Object()); // what every resource reload makes
		assertEquals(before + 1, cache.epoch());
		assertEquals(0, cache.size());
	}

	@Test
	void stayBoundedAndKeepTheRecentlyUsed() {
		WidthCache cache = new WidthCache(4);
		for (int i = 0; i < 1000; i++) {
			cache.get(FONT, STYLE, "text " + i, LANG, measure);
			cache.get(FONT, STYLE, "always", LANG, measure);
			assertTrue(cache.size() <= 4);
		}
		// 1000 distinct texts plus the one asked for again and again, which was measured only once
		assertEquals(1001, measures.get());
	}

	@Test
	void theCutRoomIsSteadyWhileThePillAnimates() {
		// the room changes every frame for a moment after a song change; the keys must not
		for (int whole = 40; whole < 300; whole += 37) {
			for (float f = 0.01f; f < 1; f += 0.07f) {
				assertEquals(whole, NowPlayingHud.clipRoom(whole + f), "cut room at " + (whole + f));
				assertEquals(whole + 1, NowPlayingHud.titleRoom(whole + f), "title room at " + (whole + f));
			}
		}
		// never overflows: the cut room is at most the real room, the title room at least
		assertTrue(NowPlayingHud.clipRoom(120.9f) <= 120.9f);
		assertTrue(NowPlayingHud.titleRoom(120.1f) >= 120.1f);
	}

	@Test
	void aTranslatedTextIsMadeOncePerLanguage() {
		AtomicInteger made = new AtomicInteger();
		LangText text = new LangText(() -> made.incrementAndGet() > 0 ? "Unknown song!" : "");
		Object english = new Object(), hebrew = new Object();
		for (int i = 0; i < 50; i++) {
			assertEquals("Unknown song!", text.get(english));
		}
		assertEquals(1, made.get());
		text.get(hebrew);
		assertEquals(2, made.get());
		text.get(hebrew);
		assertEquals(2, made.get());
	}

	@Test
	void aReloadWhileNothingDrewIsNoticedByTheProbe() {
		WidthCache cache = new WidthCache(8);
		float[] glyph = {3.5f};
		WidthCache.Measure live = (f, st, t) -> t.length() * glyph[0];
		cache.probe(9 * glyph[0]);
		assertEquals(17.5f, cache.get(FONT, STYLE, "hello", LANG, live));
		cache.probe(9 * glyph[0]);
		assertEquals(1, cache.size(), "an unchanged probe keeps the widths");
		glyph[0] = 4f; // a resource pack changed the glyphs in a menu, with no frame of ours drawn
		cache.probe(9 * glyph[0]); // the next frame's probe
		assertEquals(20f, cache.get(FONT, STYLE, "hello", LANG, live));
	}

	@Test
	void aChangedGlyphModeDropsTheWidthsLikeAReload() {
		WidthCache cache = new WidthCache(8);
		cache.mode(0); // the first call only remembers it
		cache.get(FONT, STYLE, "hello", LANG, measure);
		long before = cache.epoch();
		cache.mode(0);
		cache.get(FONT, STYLE, "hello", LANG, measure);
		assertEquals(1, measures.get());
		assertEquals(before, cache.epoch());
		cache.mode(1); // Force Unicode Font toggled: no reload, same font and language objects
		assertEquals(before + 1, cache.epoch());
		cache.get(FONT, STYLE, "hello", LANG, measure);
		assertEquals(2, measures.get());
	}
}
