package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

/** Cache hits and idle checks that run every frame build nothing on the heap. */
class FrameKeyGarbageTest {
	private static final ThreadMXBean MX = (ThreadMXBean) ManagementFactory.getThreadMXBean();
	private static final Object FONT = new Object(), LANG = new Object();

	private static long bytes(Runnable r) {
		for (int i = 0; i < 20_000; i++) {
			r.run();
		}
		long id = Thread.currentThread().getId();
		long before = MX.getThreadAllocatedBytes(id);
		for (int i = 0; i < 10_000; i++) {
			r.run();
		}
		return MX.getThreadAllocatedBytes(id) - before;
	}

	@Test
	void widthHitAllocatesNothing() {
		WidthCache cache = new WidthCache(8);
		WidthCache.Measure measure = (f, s, t) -> t.length();
		cache.get(FONT, Style.EMPTY, "hello", LANG, measure);
		long used = bytes(() -> cache.get(FONT, Style.EMPTY, "hello", LANG, measure));
		System.out.println("WidthCache hit bytes per 10000: " + used);
		assertTrue(used < 20_000, "allocated " + used + " bytes for 10000 cache hits");
	}

	@Test
	void widthStillChangesWithItsInputs() {
		WidthCache cache = new WidthCache(8);
		WidthCache.Measure measure = (f, s, t) -> t.length();
		assertEquals(5f, cache.get(FONT, Style.EMPTY, "hello", LANG, measure));
		assertEquals(6f, cache.get(FONT, Style.EMPTY, "hello!", LANG, measure));
		assertEquals(5f, cache.get(FONT, Style.EMPTY, "hello", LANG, measure));
		assertEquals(2, cache.size());
		cache.get(FONT, Style.EMPTY.withBold(true), "hello", LANG, measure);
		assertEquals(3, cache.size());
	}

	@Test
	void visualHitAllocatesNothingAndFollowsItsInputs() {
		var a = NowPlayingHud.visual("12:34", Style.EMPTY);
		assertSame(a, NowPlayingHud.visual("12:34", Style.EMPTY));
		assertNotSame(a, NowPlayingHud.visual("12:35", Style.EMPTY));
		assertNotSame(a, NowPlayingHud.visual("12:34", Style.EMPTY.withBold(true)));
		assertSame(a, NowPlayingHud.visual("12:34", Style.EMPTY));
		long used = bytes(() -> NowPlayingHud.visual("12:34", Style.EMPTY));
		System.out.println("visual hit bytes per 10000: " + used);
		assertTrue(used < 20_000, "allocated " + used + " bytes for 10000 cache hits");
	}

	@Test
	void noStringIsBuiltWhileNoActionBarShows() {
		Component text = Component.literal("Now playing");
		assertFalse(NowPlayingHud.actionBarShowing(0, text));
		assertFalse(NowPlayingHud.actionBarShowing(40, (Component) null));
		assertFalse(NowPlayingHud.actionBarShowing(40, Component.literal("")));
		assertTrue(NowPlayingHud.actionBarShowing(40, text));
		long used = bytes(() -> NowPlayingHud.actionBarShowing(0, text));
		System.out.println("actionBar idle bytes per 10000: " + used);
		assertTrue(used < 20_000, "allocated " + used + " bytes for 10000 idle checks");
	}
}
