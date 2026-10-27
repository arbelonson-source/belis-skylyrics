package dev.beli.skylyrics.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
//? if >=26.3 {
/*import org.lwjgl.sdl.SDLMouse;
*///?} else {
import org.lwjgl.glfw.GLFW;
//?}

/**
 * Some mod packs swallow mouse clicks before they reach a screen, so hovering works but nothing can be
 * clicked. This watches the real left button every frame and replays a press, drag and release on the
 * screen when the game never delivered them.
 */
final class ClickRescue {
	interface Target {
		boolean click(MouseButtonEvent event);

		boolean drag(MouseButtonEvent event, double dx, double dy);

		boolean release(MouseButtonEvent event);
	}

	private static final int WAIT_FRAMES = 2;

	private boolean started;
	private boolean wasDown;
	private boolean sawPress;
	private boolean sawRelease;
	private boolean replaying;
	private boolean inside;
	private int waited;
	private double lastX;
	private double lastY;

	/** A left press reached the screen. Returns false when it came late, after this already replayed it. */
	boolean pressed() {
		if (inside) {
			return true;
		}
		sawPress = true;
		return !replaying;
	}

	/** A left release reached the screen. Returns false when this replays the click instead. */
	boolean released() {
		if (inside) {
			return true;
		}
		sawRelease = true;
		return !replaying;
	}

	/** Call once per frame with the mouse position, after the game has handled its input. */
	void poll(double mx, double my, Target target) {
		poll(mx, my, leftDown(), target);
	}

	void poll(double mx, double my, boolean down, Target target) {
		if (!started) {
			// A click that opened this screen may still be held: don't treat it as a click on it
			started = true;
			wasDown = down;
			sawPress = down;
		} else if (down && !sawPress && !replaying) {
			// Give the game a couple of frames to deliver the press, since some mods poll input on another thread
			if (++waited > WAIT_FRAMES) {
				replaying = true;
				inside = true;
				target.click(event(mx, my));
				inside = false;
			}
		} else if (down && replaying && (mx != lastX || my != lastY)) {
			inside = true;
			target.drag(event(mx, my), mx - lastX, my - lastY);
			inside = false;
		} else if (!down && wasDown && replaying && !sawRelease) {
			inside = true;
			target.release(event(mx, my));
			inside = false;
		}
		if (!down) {
			sawPress = false;
			sawRelease = false;
			replaying = false;
			waited = 0;
		}
		wasDown = down;
		lastX = mx;
		lastY = my;
	}

	private static boolean leftDown() {
		//? if >=26.3 {
		/*return (SDLMouse.nSDL_GetMouseState(0, 0) & SDLMouse.SDL_BUTTON_LMASK) != 0;
		*///?} else if >=1.21.9 {
		return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		//?} else {
		/*return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		*///?}
	}

	private static MouseButtonEvent event(double x, double y) {
		//? if >=1.21.9 {
		return new MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0));
		//?} else {
		/*return new MouseButtonEvent(x, y, 0);
		*///?}
	}
}
