package dev.beli.skylyrics.gui;

import net.minecraft.client.Minecraft;
//? if <1.21.6 {
/*import com.mojang.blaze3d.systems.RenderSystem;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.Tr;

/**
 * An error in one of the mod's screens, in a method the game calls on it (drawing it, a key, a click, its narration).
 * Thrown out of there it ended the game with a crash report ("Rendering screen", "keyPressed event handler"); the screen
 * catches it and hands it here instead. The first error is written to the log with its stack trace, the screen goes back
 * to the one it was opened from, and a pop-up says something went wrong. Each screen has its own: the rest of the call
 * that failed may still run the screen, and later errors of it are only noted in a line.
 */
public final class ScreenFault {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	/** Later errors of a screen that are logged (in one line each) after its first. */
	static final int MORE_LOGGED = 5;

	private final String name;
	private final Runnable leave;
	private boolean failed;
	private int more;

	/** {@code name} is the screen as the log calls it; {@code leave} takes it back to where it came from and says so. */
	ScreenFault(String name, Runnable leave) {
		this.name = name;
		this.leave = leave;
	}

	/**
	 * Whether the screen has hit an error. Its drawing and narration then stop: the game may still finish the frame or
	 * the narration it was on after the screen closed.
	 */
	boolean hasFailed() {
		return failed;
	}

	/**
	 * The screen threw in {@code what} ("drawing", "a key"): logged, and the first error takes the screen back to where it
	 * was opened from. True, for a handler that answers whether it used the event (the event is used up).
	 */
	boolean failed(String what, Throwable error) {
		if (failed) {
			more(what, error);
			return true;
		}
		failed = true;
		LOG.error("Error in {} ({}); it closed back to the screen it was opened from", name, what, error);
		try {
			leave.run();
		} catch (RuntimeException | LinkageError e) {
			LOG.error("{} could not close after its error: {}", name, e.toString());
		}
		return true;
	}

	/** The screen threw while it was being closed: logged only, since it is leaving already. */
	void failedClosing(Throwable error) {
		if (failed) {
			more("closing", error);
			return;
		}
		LOG.error("Error in {} while it closed", name, error);
	}

	private void more(String what, Throwable error) {
		if (more < MORE_LOGGED) {
			more++;
			LOG.warn("Another error in {} ({}) after its first: {}", name, what, error.toString());
		}
	}

	/**
	 * Takes {@code screen} back to {@code parent} if it is still the one showing, and says in a pop-up (read out by the
	 * Narrator) that something went wrong there.
	 */
	static void leave(Screen screen, @Nullable Screen parent) {
		Minecraft mc = Minecraft.getInstance();
		//? if >=26.2 {
		/*if (mc.gui.screen() == screen) {
			mc.gui.setScreen(parent);
		}
		*///?} else {
		if (mc.screen == screen) {
			mc.setScreen(parent);
		}
		//?}
		SkyLyricsClient.toast(mc, screen.getTitle(), Component.literal(Tr.t("Something went wrong, try again")));
	}

	/**
	 * After a drawing error: before 1.21.6 a clip that the failed drawing opened stays on for whatever the game draws next
	 * (the game sets it at once and only takes it off when the screen closes the clip), so it is taken off here. From
	 * 1.21.6 on each frame's clips are kept with what they clip, and nothing is left behind.
	 */
	static void unclip() {
		//? if <1.21.6 {
		/*try {
			RenderSystem.disableScissor();
		} catch (RuntimeException | LinkageError e) {
			// no drawing state to put right (the tests have none), and this runs while handling an error already
		}
		*///?}
	}
}
