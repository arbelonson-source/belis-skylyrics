package dev.beli.skylyrics.render;

import java.util.function.Function;

/**
 * A few remembered results, found by value equality of the whole key and replaced oldest first, so it never grows.
 * Put every input the result depends on into the key: a hit is only ever the answer for exactly that key. Render
 * thread only.
 */
final class SmallCache<K, V> {
	private final Object[] keys;
	private final Object[] values;
	private int next;

	SmallCache(int slots) {
		keys = new Object[slots];
		values = new Object[slots];
	}

	/** The remembered result for a key equal to {@code probe}, or null. Allocates nothing, so a reused probe is fine. */
	@SuppressWarnings("unchecked")
	V find(Object probe) {
		for (int i = 0; i < keys.length; i++) {
			if (probe.equals(keys[i])) {
				return (V) values[i];
			}
		}
		return null;
	}

	/** Remembers a result; {@code key} must be an object of its own, never a probe that is reused. */
	void put(K key, V value) {
		keys[next] = key;
		values[next] = value;
		next = (next + 1) % keys.length;
	}

	@SuppressWarnings("unchecked")
	V get(K key, Function<K, V> make) {
		for (int i = 0; i < keys.length; i++) {
			if (key.equals(keys[i])) {
				return (V) values[i];
			}
		}
		V made = make.apply(key);
		keys[next] = key;
		values[next] = made;
		next = (next + 1) % keys.length;
		return made;
	}
}
