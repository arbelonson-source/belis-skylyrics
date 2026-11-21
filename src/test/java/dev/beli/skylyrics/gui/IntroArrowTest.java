package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.client.resources.language.FormattedBidiReorder;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

/** The tour's "<" and ">" look buttons, as the game draws a button's label in an English game and in a Hebrew one. */
class IntroArrowTest {
	/** What the game draws from left to right for a label (a button's label goes through the game language's reorder). */
	private static String seen(String label, boolean rtlGame) {
		StringBuilder out = new StringBuilder();
		FormattedBidiReorder.reorder(FormattedText.of(label, Style.EMPTY), rtlGame).accept((i, style, cp) -> {
			out.appendCodePoint(cp);
			return true;
		});
		return out.toString();
	}

	@Test
	void theLookArrowsPointOutwardsInAHebrewGameToo() {
		for (boolean rtlGame : new boolean[] {false, true}) {
			String game = rtlGame ? "a Hebrew game" : "an English game";
			assertEquals("<", seen(IntroScreen.arrow(-1, rtlGame), rtlGame), "the previous look button, on the left, in " + game);
			assertEquals(">", seen(IntroScreen.arrow(1, rtlGame), rtlGame), "the next look button, on the right, in " + game);
		}
	}
}
