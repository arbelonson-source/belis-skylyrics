package dev.beli.skylyrics.render;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembered text widths, so the same line is not measured again every frame. A width depends on the font object, the
 * style, the text, the game's language object and the resource reload count, and all of those are in the key. Bounded:
 * the least recently used width goes first. Render thread only.
 */
final class WidthCache {
	/** What measures a text; given the same font and style objects the cache was asked with. */
	interface Measure {
		float of(Object font, Object style, String text);
	}

	private static final class Key {
		Object font;
		Object style;
		String text;
		Object language;
		long epoch;

		Key(Object font, Object style, String text, Object language, long epoch) {
			set(font, style, text, language, epoch);
		}

		void set(Object font, Object style, String text, Object language, long epoch) {
			this.font = font;
			this.style = style;
			this.text = text;
			this.language = language;
			this.epoch = epoch;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Key k && java.util.Objects.equals(font, k.font) && style.equals(k.style) && text.equals(k.text) && java.util.Objects.equals(language, k.language)
					&& epoch == k.epoch;
		}

		@Override
		public int hashCode() {
			return 31 * text.hashCode() + Long.hashCode(epoch);
		}
	}

	/** Refilled for every lookup so a hit builds no key; only a miss stores a copy. */
	private final Key probe = new Key(null, null, "", null, 0);

	private final int limit;
	private final LinkedHashMap<Key, Float> map;
	private long epoch;
	private float seen = Float.NaN;

	WidthCache(int limit) {
		this.limit = limit;
		this.map = new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Key, Float> eldest) {
				return size() > WidthCache.this.limit;
			}
		};
	}

	/** A resource reload happened: every width remembered so far may be wrong now. */
	void invalidate() {
		epoch++;
		map.clear();
	}

	/** How many reloads were seen; part of every key that is cached elsewhere on top of these widths. */
	long epoch() {
		return epoch;
	}

	/**
	 * Called once per frame with the width of a fixed probe text, measured fresh (not from this cache). A resource
	 * pack or font change moves it, which is how a reload is noticed even when nothing was drawing during it (a menu,
	 * the title screen, F1). The first call only remembers it.
	 */
	void probe(float canary) {
		if (!Float.isNaN(seen) && canary != seen) {
			invalidate();
		}
		seen = canary;
	}

	int size() {
		return map.size();
	}

	float get(Object font, Object style, String text, Object language, Measure measure) {
		probe.set(font, style, text, language, epoch);
		Float known = map.get(probe);
		if (known != null) {
			return known;
		}
		float w = measure.of(font, style, text);
		map.put(new Key(font, style, text, language, epoch), w);
		return w;
	}
}
