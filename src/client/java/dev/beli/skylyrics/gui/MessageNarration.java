package dev.beli.skylyrics.gui;

import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * The song library's message ("Starting…", "Playing…", why a song didn't start) as the Narrator gets it, under the
 * screen's title and the line for what has the keyboard. Kept apart from the screen so it can be tested with the game's
 * own narration.
 */
final class MessageNarration {
	/** Messages shown so far, and how many of them the Narrator has been given. */
	private int shown, given;
	private boolean otherSlot;

	/** A message has just appeared, even one with the same words as the one before. */
	void shown() {
		shown++;
	}

	/**
	 * Adds the message that is showing now ({@code message}, empty for none) to the screen's narration. The game says a
	 * line again only when its words differ from what that line said the time before, so a new message with the same words
	 * as the last one ("These songs are made up, so nothing plays" on the next card, the same error twice) would be
	 * skipped: a new message takes the other of two lines instead, while one still showing stays on its line.
	 */
	void add(NarrationElementOutput output, String message) {
		if (message.isEmpty()) {
			return;
		}
		if (given != shown) {
			given = shown;
			otherSlot = !otherSlot;
		}
		NarrationElementOutput line = output.nest().nest();
		(otherSlot ? line.nest() : line).add(NarratedElementType.TITLE, Component.literal(message));
	}
}
