package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.config.SkyLyricsConfig;

class LyricsFontTest {
	@Test
	void worldWordsUseTheFinerCopyOfTheChosenFont() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		assertEquals(LyricsFont.WORLD_BOLD, LyricsFont.world(cfg).getFont());
		cfg.boldFont = false;
		assertEquals(LyricsFont.WORLD, LyricsFont.world(cfg).getFont());
		cfg.scriptFont = true;
		assertEquals(LyricsFont.WORLD_SCRIPT, LyricsFont.world(cfg).getFont());
	}

	@Test
	void roundLettersWinOverBoldButNotOverHandwriting() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.roundFont = true;
		assertEquals(LyricsFont.ROUND, LyricsFont.sky(cfg).getFont());
		assertEquals(LyricsFont.WORLD_ROUND, LyricsFont.world(cfg).getFont());
		cfg.scriptFont = true;
		assertEquals(LyricsFont.SCRIPT, LyricsFont.sky(cfg).getFont());
		cfg.smoothFont = false;
		assertEquals(Style.EMPTY.getFont(), LyricsFont.sky(cfg).getFont());
	}

	@Test
	void minecraftLettersStayMinecraftLetters() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.smoothFont = false;
		assertEquals(Style.EMPTY.getFont(), LyricsFont.world(cfg).getFont());
	}
}
