package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** The song library's message line through the game's own narration, which says a line again only when its words change. */
class MessageNarrationTest {
	private static final String MADE_UP = "These songs are made up, so nothing plays";

	private final ScreenNarrationCollector narration = new ScreenNarrationCollector();
	private final MessageNarration message = new MessageNarration();

	/** One narration of the library as its screen builds it: title, the card the keyboard is on, the message showing. */
	private String narrate(String card, String showing) {
		java.util.function.Consumer<net.minecraft.client.gui.narration.NarrationElementOutput> screen = output -> {
			output.add(NarratedElementType.TITLE, Component.literal("Song library"));
			output.nest().add(NarratedElementType.TITLE, Component.literal(card));
			message.add(output, showing);
		};
		//? if >=26.3 {
		/*narration.update(screen, net.minecraft.client.gui.narration.NarrationTrigger.KEYBOARD);
		*///?} else {
		narration.update(screen);
		//?}
		return narration.collectNarrationText(false);
	}

	/** Enter on a card of the made-up library: "Starting…" shows, and a frame later why nothing plays takes its place. */
	private void enter() {
		message.shown();
		message.shown();
	}

	@Test
	void aMessageWithTheSameWordsIsSaidEachTimeItShowsAgain() {
		assertEquals("Song library. Neon Skyline", narrate("Neon Skyline", ""));
		enter();
		assertEquals(MADE_UP, narrate("Neon Skyline", MADE_UP));
		// Right while the message still shows: only the new card
		assertEquals("Glass Over Water", narrate("Glass Over Water", MADE_UP));
		enter();
		assertEquals(MADE_UP, narrate("Glass Over Water", MADE_UP), "Enter on the next card says why nothing plays again");
		enter();
		assertEquals(MADE_UP, narrate("Glass Over Water", MADE_UP), "and Enter on the same card again too");
	}

	@Test
	void aMessageStillShowingIsNotSaidAgainOnEveryKey() {
		narrate("Neon Skyline", "");
		message.shown();
		assertEquals("Playing Neon Skyline", narrate("Neon Skyline", "Playing Neon Skyline"));
		assertEquals("Glass Over Water", narrate("Glass Over Water", "Playing Neon Skyline"));
		assertEquals("Lantern Hours", narrate("Lantern Hours", "Playing Neon Skyline"));
		// gone, then a new one with other words
		assertEquals("", narrate("Lantern Hours", ""));
		message.shown();
		assertEquals("Playing Lantern Hours", narrate("Lantern Hours", "Playing Lantern Hours"));
	}
}
