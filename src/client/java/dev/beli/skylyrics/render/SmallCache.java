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
