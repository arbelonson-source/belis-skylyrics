package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class ArcGarbageTest {
	@Test
	void sameBaseAndColourGiveTheSameStyle() {
		LyricsRenderer.ColorStyles styles = new LyricsRenderer.ColorStyles();
		Style base = Style.EMPTY;
		Style a = styles.of(base, 0x123456);
		assertEquals(base.withColor(0x123456), a);
		assertSame(a, styles.of(base, 0x123456));
		Style b = styles.of(base, 0x654321);
		assertNotSame(a, b);
		assertSame(a, styles.of(base, 0x123456));
		assertSame(b, styles.of(base, 0x654321));
	}

	@Test
	void manyColoursStayCorrect() {
		LyricsRenderer.ColorStyles styles = new LyricsRenderer.ColorStyles();
		for (int round = 0; round < 3; round++) {
			for (int c = 0; c < 40; c++) {
				assertEquals(Style.EMPTY.withColor(c * 1000), styles.of(Style.EMPTY, c * 1000));
			}
		}
	}

	@Test
	void anotherBaseIsNotMixedUp() {
		LyricsRenderer.ColorStyles styles = new LyricsRenderer.ColorStyles();
		Style bold = Style.EMPTY.withBold(true);
		styles.of(Style.EMPTY, 0xABCDEF);
		assertEquals(bold.withColor(0xABCDEF), styles.of(bold, 0xABCDEF));
	}

	@Test
	void displayOfIsTheSameObjectAndAllocatesNothingOnAHit() {
		for (boolean rtl : new boolean[] {false, true}) {
			LyricsRenderer.Display first = LyricsRenderer.Display.of("garbage", rtl);
			assertSame(first, LyricsRenderer.Display.of("garbage", rtl));
			assertNotSame(LyricsRenderer.Display.of("garbage", !rtl), LyricsRenderer.Display.of("garbage", rtl));
		}
		ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
		long id = Thread.currentThread().getId();
		boolean flip = false;
		for (int i = 0; i < 20_000; i++) { // warm up so the JIT has removed boxing
			LyricsRenderer.Display.of("garbage", flip = !flip);
		}
		long before = mx.getThreadAllocatedBytes(id);
		for (int i = 0; i < 10_000; i++) {
			LyricsRenderer.Display.of("garbage", flip = !flip);
		}
		long used = mx.getThreadAllocatedBytes(id) - before;
		assertTrue(used < 20_000, "allocated " + used + " bytes for 10000 cache hits");
	}
}
