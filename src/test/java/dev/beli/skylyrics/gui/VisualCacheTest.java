package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VisualCacheTest {
	@Test
	void sameTextAndDirectionIsComputedOnce() {
		Ui.VisualCache<String> c = new Ui.VisualCache<>(8);
		Object lang = new Object();
		AtomicInteger n = new AtomicInteger();
		String a = c.get(lang, "x", false, () -> "v" + n.incrementAndGet());
		assertSame(a, c.get(lang, "x", false, () -> "v" + n.incrementAndGet()));
		assertEquals(1, n.get());
		c.get(lang, "x", true, () -> "v" + n.incrementAndGet());
		assertEquals(2, n.get());
	}

	@Test
	void aNewLanguageObjectDropsEverything() {
		Ui.VisualCache<String> c = new Ui.VisualCache<>(8);
		AtomicInteger n = new AtomicInteger();
		c.get(new Object(), "x", false, () -> "v" + n.incrementAndGet());
		c.get(new Object(), "x", false, () -> "v" + n.incrementAndGet());
		assertEquals(2, n.get());
		assertEquals(1, c.size());
	}

	@Test
	void leastRecentlyUsedGoesFirstAndSizeIsBounded() {
		Ui.VisualCache<String> c = new Ui.VisualCache<>(2);
		Object lang = new Object();
		AtomicInteger n = new AtomicInteger();
		c.get(lang, "a", false, () -> "a" + n.incrementAndGet());
		c.get(lang, "b", false, () -> "b" + n.incrementAndGet());
		c.get(lang, "a", false, () -> "a" + n.incrementAndGet()); // touch a: b is now eldest
		c.get(lang, "c", false, () -> "c" + n.incrementAndGet());
		assertEquals(2, c.size());
		assertEquals(3, n.get());
		c.get(lang, "a", false, () -> "a" + n.incrementAndGet());
		assertEquals(3, n.get()); // a survived
		c.get(lang, "b", false, () -> "b" + n.incrementAndGet());
		assertEquals(4, n.get()); // b was evicted
	}
}
