package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** The top bar's per-frame caches: the same inputs give the same layout, any changed input a new one, and the layout equals the one made without a cache. */
class TopBarCacheTest {
	private static final float GAP = 3.1f + TopBarLayout.WORD_EXTRA;

	/** Stands in for the font: a width per character, wider for non-Latin ones, the same every time. */
	private static float width(String piece, boolean lineRtl) {
		float w = 0;
		for (int i = 0; i < piece.length(); i++) {
			char c = piece.charAt(i);
			w += c > 0x2E80 ? 9.5f : c >= 0x0590 ? 4.25f : 3.5f + (c % 5) * 0.5f;
		}
		return lineRtl ? w + 0.25f : w;
	}

	/** The words as the bar laid them out before the cache existed (NowPlayingHud.words), copied here as the reference. */
	private record Ref(String[] texts, float[] x, float[] w, boolean[] sung, float extent) {}

	private static Ref reference(String text, float scale, float share) {
		int letters = 0;
		for (int i = 0; i < text.length(); i++) {
			letters += text.charAt(i) == ' ' ? 0 : 1;
		}
		float at = share * letters;
		List<TopBarWords.Piece> parts = TopBarWords.split(text);
		int n = parts.size();
		float[] widths = new float[n];
		boolean rtl = TopBarWords.baseRtl(text);
		for (int i = 0; i < n; i++) {
			widths[i] = width(parts.get(i).text(), rtl) * scale;
		}
		float[] left = n == 0 ? new float[0] : TopBarWords.leftEdges(parts, widths, GAP);
		float total = 0;
		for (int i = 0; i < n; i++) {
			total = Math.max(total, left[i] + widths[i]);
		}
		String[] texts = new String[n];
		float[] x = new float[n];
		boolean[] sung = new boolean[n];
		int seen = 0;
		float end = 0;
		for (int i = 0; i < n; i++) {
			sung[i] = seen <= at || share >= 1;
			texts[i] = parts.get(i).text();
			x[i] = rtl ? total - left[i] - widths[i] : left[i];
			seen += texts[i].length();
			end = Math.max(end, x[i] + widths[i]);
		}
		return new Ref(texts, x, widths, sung, end);
	}

	@Test
	void layoutMatchesTheUncachedOneForEveryScript() {
		String[] lines = {
				"I see the sky tonight and it is wide",
				"שלום world 5 אור שמיים כחול,",
				"أنا أحب السماء الزرقاء 123",
				"空が青い 夜に歌う ありがとう",
				"  ",
				"",
				"one",
		};
		for (String line : lines) {
			for (float scale : new float[] {0.75f, 1}) {
				LineLayout got = LineLayout.build(line, scale, GAP, TopBarCacheTest::width);
				Ref want = reference(line, scale, 1);
				assertEquals(want.texts().length, got.size(), line);
				for (int i = 0; i < got.size(); i++) {
					assertEquals(want.texts()[i], got.texts[i], line);
					assertEquals(want.x()[i], got.x[i], line + " x" + i);
					assertEquals(want.w()[i], got.w[i], line + " w" + i);
				}
				assertEquals(want.extent(), got.extent, line);
				for (float share : new float[] {0, 0.1f, 0.33f, 0.5f, 0.99f, 1}) {
					Ref r = reference(line, scale, share);
					for (int i = 0; i < got.size(); i++) {
						assertEquals(r.sung()[i], got.sung(i, share), line + " word " + i + " share " + share);
					}
				}
			}
		}
	}

	private static final Object FONT = new Object(), STYLE = new Object(), LANG = new Object();

	private static LineKey key() {
		return new LineKey("hello there", FONT, STYLE, 0.75f, LANG, 3, 0);
	}

	@Test
	void sameInputsGiveTheSameLayoutInstance() {
		SmallCache<LineKey, Object> cache = new SmallCache<>(3);
		AtomicInteger builds = new AtomicInteger();
		Object first = cache.get(key(), k -> { builds.incrementAndGet(); return new Object(); });
		for (int i = 0; i < 50; i++) {
			assertSame(first, cache.get(key(), k -> { builds.incrementAndGet(); return new Object(); }));
		}
		assertEquals(1, builds.get());
	}

	@Test
	void eachChangedInputGivesANewLayout() {
		LineKey base = key();
		LineKey[] changed = {
				new LineKey("hello there!", FONT, STYLE, 0.75f, LANG, 3, 0),
				new LineKey("hello there", new Object(), STYLE, 0.75f, LANG, 3, 0),
				new LineKey("hello there", FONT, new Object(), 0.75f, LANG, 3, 0),
				new LineKey("hello there", FONT, STYLE, 1f, LANG, 3, 0),
				new LineKey("hello there", FONT, STYLE, 0.75f, new Object(), 3, 0),
				new LineKey("hello there", FONT, STYLE, 0.75f, LANG, 4, 0),
				new LineKey("hello there", FONT, STYLE, 0.75f, LANG, 3, 1),
		};
		for (LineKey other : changed) {
			assertNotEquals(base, other);
			SmallCache<LineKey, Object> cache = new SmallCache<>(3);
			Object a = cache.get(base, k -> new Object());
			Object b = cache.get(other, k -> new Object());
			assertNotSame(a, b, other.toString());
			// and asking for the first again still gives the first, not the other's
			assertSame(a, cache.get(base, k -> new Object()));
		}
	}

	@Test
	void cacheStaysSmallAndDropsTheOldest() {
		SmallCache<Integer, Object> cache = new SmallCache<>(3);
		AtomicInteger builds = new AtomicInteger();
		for (int round = 0; round < 3; round++) {
			for (int i = 0; i < 100; i++) {
				cache.get(i, k -> { builds.incrementAndGet(); return new Object(); });
			}
		}
		// 100 distinct keys cycling through 3 slots never hit; nothing is kept past the slots
		assertEquals(300, builds.get());
		Object kept = cache.get(1000, k -> new Object());
		assertSame(kept, cache.get(1000, k -> new Object()));
		assertTrue(builds.get() == 300);
	}
}
