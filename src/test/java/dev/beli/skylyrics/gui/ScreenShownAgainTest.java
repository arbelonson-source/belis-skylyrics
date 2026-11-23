package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.LogCapture;

/**
 * An error can reach the settings or the song library while another screen is already in front of it: the settings'
 * Controls button opens Minecraft's own key list, the library's sign-in button opens the settings, and the error comes from
 * that screen opening or from what the click does after it. There is then nothing to close; but the screen behind
 * remembered the error and, when that other screen closed back to it, drew nothing and said nothing: an invisible
 * screen that still took the keys and the mouse, until Esc. Shown again, it is drawn again.
 */
class ScreenShownAgainTest {
	/** A screen made without its constructor, which needs a running game (as in {@link ScreenErrorsTest}). */
	private static <T> T make(Class<T> type) throws Exception {
		Class<?> unsafe = Class.forName("sun.misc.Unsafe");
		Field f = unsafe.getDeclaredField("theUnsafe");
		f.setAccessible(true);
		return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(f.get(null), type));
	}

	/** The screen's own drawing method, by its name on this Minecraft version. */
	private static Method drawing(Class<?> type) {
		for (Method m : type.getDeclaredMethods()) {
			if ((m.getName().equals("render") || m.getName().equals("extractRenderState")) && m.getParameterCount() == 4 && !m.isBridge()) {
				return m;
			}
		}
		return null;
	}

	@Test
	void aScreenThatHitAnErrorBehindAnotherIsDrawnWhenItComesBack() throws Exception {
		for (Class<? extends Screen> type : List.of(SkyLyricsScreen.class, LibraryScreen.class)) {
			Screen screen = make(type);
			// Another screen is in front: going back can't take this one away, so leaving only counts
			AtomicInteger left = new AtomicInteger();
			ScreenFault fault = new ScreenFault(type.getSimpleName(), left::incrementAndGet);
			Field f = type.getDeclaredField("fault");
			f.setAccessible(true);
			f.set(screen, fault);
			Method draw = drawing(type);
			assertNotNull(draw, type.getSimpleName() + " has no drawing method");
			draw.setAccessible(true);
			try (LogCapture log = new LogCapture()) {
				fault.failed("a click", new IllegalStateException("test: the screen it opened broke"));
				assertTrue(fault.hasFailed());
				// The game shows it again (Minecraft's setScreen calls added() every time a screen is put up)
				screen.added();
				assertFalse(fault.hasFailed(), type.getSimpleName() + ": shown again, it must be drawn and read out again");
				// Its drawing runs again: made without a game, it throws at once, and that error takes it back
				try {
					draw.invoke(screen, null, 0, 0, 0f);
				} catch (InvocationTargetException e) {
					throw new AssertionError(type.getSimpleName() + " let its drawing error out", e.getCause());
				}
				assertEquals(2, left.get(), type.getSimpleName() + ": its drawing should have run (and gone back after its error)");
			}
		}
	}
}
