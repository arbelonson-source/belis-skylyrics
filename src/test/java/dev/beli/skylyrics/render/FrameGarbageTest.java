package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.config.SkyLyricsConfig;

class FrameGarbageTest {
	@Test
	void fontStylesAreSharedAndEqualToFreshOnes() {
		for (int bits = 0; bits < 16; bits++) {
			SkyLyricsConfig cfg = new SkyLyricsConfig();
			cfg.smoothFont = (bits & 1) != 0;
			cfg.boldFont = (bits & 2) != 0;
			cfg.scriptFont = (bits & 4) != 0;
			cfg.roundFont = (bits & 8) != 0;
			assertSame(LyricsFont.base(cfg), LyricsFont.base(cfg));
			assertSame(LyricsFont.sky(cfg), LyricsFont.sky(cfg));
			assertSame(LyricsFont.world(cfg), LyricsFont.world(cfg));
			Style base = cfg.smoothFont ? Style.EMPTY.withFont(LyricsFont.SMOOTH) : Style.EMPTY;
			assertEquals(base, LyricsFont.base(cfg));
			Style sky = cfg.smoothFont && cfg.scriptFont ? Style.EMPTY.withFont(LyricsFont.SCRIPT)
					: cfg.smoothFont && cfg.roundFont ? Style.EMPTY.withFont(LyricsFont.ROUND)
					: cfg.smoothFont && cfg.boldFont ? Style.EMPTY.withFont(LyricsFont.BOLD) : base;
			assertEquals(sky, LyricsFont.sky(cfg));
			Style world = !cfg.smoothFont ? Style.EMPTY : Style.EMPTY.withFont(cfg.scriptFont ? LyricsFont.WORLD_SCRIPT
					: cfg.roundFont ? LyricsFont.WORLD_ROUND : cfg.boldFont ? LyricsFont.WORLD_BOLD : LyricsFont.WORLD);
			assertEquals(world, LyricsFont.world(cfg));
		}
	}

	@Test
	void stripMarksKeepsTheSameInstanceWhenNothingToStrip() {
		String plain = "Hello ‍ wörld 🎵 ​";
		assertSame(plain, LyricsFont.stripMarks(plain));
		assertSame("", LyricsFont.stripMarks(""));
	}

	@Test
	void stripMarksDropsDirectionMarksOnly() {
		assertEquals("ab", LyricsFont.stripMarks("‎a‏b؜"));
		assertEquals("a‍b", LyricsFont.stripMarks("a‍‪b‮⁦⁩")); // ZWJ stays
		assertEquals("x🎵y", LyricsFont.stripMarks("x🎵‏y"));
		assertEquals("", LyricsFont.stripMarks("‎‏؜"));
	}

	@Test
	void theFontProbeRunsOncePerQuarterSecond() {
		StringSplitter splitter = new StringSplitter((cp, style) -> 5);
		LyricsFont.resetProbeGate();
		int before = LyricsFont.canaryMeasures;
		LyricsFont.checkFonts(splitter, 1_000_000_000L);
		LyricsFont.checkFonts(splitter, 1_000_000_000L + 16_000_000L);
		LyricsFont.checkFonts(splitter, 1_000_000_000L + 32_000_000L);
		assertEquals(5, LyricsFont.canaryMeasures - before);
		LyricsFont.checkFonts(splitter, 1_300_000_000L);
		assertEquals(10, LyricsFont.canaryMeasures - before);
		LyricsFont.fontsReloaded();
		LyricsFont.checkFonts(splitter, 1_301_000_000L);
		assertEquals(15, LyricsFont.canaryMeasures - before);
	}
}
