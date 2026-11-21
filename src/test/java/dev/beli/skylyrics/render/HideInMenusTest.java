package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.gui.SkyLyricsScreen;

/** Which open screens Hide in menus hides the lyrics for. */
class HideInMenusTest {
	@Test
	void pausingWithoutTheMenuIsNotAMenu() throws Exception {
		// F3 + Esc stops the game with no menu at all: only "Game Paused" at the top, and the world in full view
		assertFalse(Visibility.inMenu(pauseScreen(false)));
	}

	@Test
	void thePauseMenuIsAMenu() throws Exception {
		// Esc, or the game losing focus
		assertTrue(Visibility.inMenu(pauseScreen(true)));
	}

	@Test
	void chatIsAMenuButTheDeathScreenAndOurSettingsAreNot() throws Exception {
		assertFalse(Visibility.inMenu(null));
		assertTrue(Visibility.inMenu(make(ChatScreen.class)));
		// In bed the chat box is open too
		assertTrue(Visibility.inMenu(make(InBedChatScreen.class)));
		assertFalse(Visibility.inMenu(make(DeathScreen.class)));
		assertFalse(Visibility.inMenu(make(SkyLyricsScreen.class)));
	}

	private static PauseScreen pauseScreen(boolean menu) throws Exception {
		PauseScreen screen = make(PauseScreen.class);
		Field f = PauseScreen.class.getDeclaredField("showPauseMenu");
		f.setAccessible(true);
		f.setBoolean(screen, menu);
		return screen;
	}

	/** A screen made without its constructor, which needs a running game. */
	private static <T> T make(Class<T> type) throws Exception {
		Class<?> unsafe = Class.forName("sun.misc.Unsafe");
		Field f = unsafe.getDeclaredField("theUnsafe");
		f.setAccessible(true);
		return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(f.get(null), type));
	}
}
