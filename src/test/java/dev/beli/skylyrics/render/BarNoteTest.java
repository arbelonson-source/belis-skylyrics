package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;

import com.sun.management.ThreadMXBean;
import net.minecraft.client.resources.language.FormattedBidiReorder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

/**
 * The note in the bar's round space for a missing cover is laid out once per language, not every frame: the game lays a
 * string out afresh each time it draws one, with its right-to-left reordering and Arabic shaping.
 */
class BarNoteTest {
	/** Lays text out as the game's own languages do, and counts how often. */
	private static final class Counting extends Language {
		int layouts;

		@Override
		public String getOrDefault(String key, String fallback) {
			return fallback;
		}

		@Override
		public boolean has(String key) {
			return false;
		}

		@Override
		public boolean isDefaultRightToLeft() {
			return false;
		}

		@Override
		public FormattedCharSequence getVisualOrder(FormattedText text) {
			layouts++;
			return FormattedBidiReorder.reorder(text, false);
		}
	}

	@Test
	void theNoteIsLaidOutOncePerLanguage() {
		Counting english = new Counting(), other = new Counting();
		for (int frame = 0; frame < 1000; frame++) {
			NowPlayingHud.note(english);
		}
		assertEquals(1, english.layouts, "layouts in 1000 frames");
		NowPlayingHud.note(other);
		NowPlayingHud.note(other);
		assertEquals(1, other.layouts, "another language lays it out again, once");
	}

	@Test
	void itIsTheNoteAsTheGameLaysItOut() {
		StringBuilder drawn = new StringBuilder();
		NowPlayingHud.note(new Counting()).accept((i, style, cp) -> {
			assertEquals(Style.EMPTY, style);
			drawn.appendCodePoint(cp);
			return true;
		});
		assertEquals("♪", drawn.toString());
	}

	@Test
	void aFrameOfTheNoteBuildsNothing() {
		ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
		Counting english = new Counting();
		Object[] kept = new Object[1];
		for (int i = 0; i < 200_000; i++) {
			kept[0] = NowPlayingHud.note(english);
		}
		long id = Thread.currentThread().getId();
		long before = mx.getThreadAllocatedBytes(id);
		for (int i = 0; i < 10_000; i++) {
			kept[0] = NowPlayingHud.note(english);
		}
		long used = mx.getThreadAllocatedBytes(id) - before;
		System.out.println("note bytes per frame: " + used / 10_000);
		assertTrue(used < 100_000, used + " bytes in 10000 frames");
	}
}
