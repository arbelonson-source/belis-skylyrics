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

	private record Key(Object font, Object style, String text, Object language, long epoch) {}

	private final int limit;
	private final LinkedHashMap<Key, Float> map;
	private long epoch;

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

	int size() {
		return map.size();
	}

	float get(Object font, Object style, String text, Object language, Measure measure) {
		Key key = new Key(font, style, text, language, epoch);
		Float known = map.get(key);
		if (known != null) {
			return known;
		}
		float w = measure.of(font, style, text);
		map.put(key, w);
		return w;
	}
}
