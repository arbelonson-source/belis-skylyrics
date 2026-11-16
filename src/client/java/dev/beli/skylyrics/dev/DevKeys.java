package dev.beli.skylyrics.dev;

/** Key names and modifiers of the test hook's {@code key} command, kept free of Minecraft so they can be tested. */
final class DevKeys {
	/** Portable modifier flags; the test hook converts them to the GLFW or SDL bits. */
	static final int SHIFT = 1;
	static final int CTRL = 2;
	static final int ALT = 4;

	private DevKeys() {
	}

	/**
	 * A key by lowercase name ("a", "7", "f5", "escape", "page_up", ...) with portable modifier flags. Turning the name into
	 * the key codes of the running Minecraft version (GLFW or SDL) is the test hook's job.
	 */
	record Key(String name, int modifiers) {
		boolean shift() {
			return (modifiers & SHIFT) != 0;
		}

		boolean ctrl() {
			return (modifiers & CTRL) != 0;
		}

		boolean alt() {
			return (modifiers & ALT) != 0;
		}
	}

	/** "NAME [ctrl] [shift] [alt]" into a key. Throws IllegalArgumentException on anything unknown. */
	static Key parse(String arg) {
		String[] words = arg.strip().split("\\s+");
		if (words[0].isEmpty()) {
			throw new IllegalArgumentException("no key name");
		}
		int modifiers = 0;
		for (int i = 1; i < words.length; i++) {
			modifiers |= switch (words[i].toLowerCase(java.util.Locale.ROOT)) {
				case "shift" -> SHIFT;
				case "ctrl", "control" -> CTRL;
				case "alt" -> ALT;
				default -> throw new IllegalArgumentException("unknown modifier " + words[i]);
			};
		}
		return new Key(name(words[0].toLowerCase(java.util.Locale.ROOT)), modifiers);
	}

	private static String name(String name) {
		if (name.length() == 1) {
			char c = name.charAt(0);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				return name;
			}
		}
		if (name.matches("f([1-9]|1[0-9]|2[0-4])")) {
			return name;
		}
		return switch (name) {
			case "space", "tab", "backspace", "insert", "delete", "right", "left", "down", "up", "home", "end" -> name;
			case "escape", "esc" -> "escape";
			case "enter", "return" -> "enter";
			case "page_up" -> "page_up";
			case "page_down" -> "page_down";
			default -> throw new IllegalArgumentException("unknown key " + name);
		};
	}

	/** The character a key press types, or 0 when it types none (control keys, Ctrl or Alt shortcuts, F-keys). */
	static int charFor(Key key) {
		if (key.ctrl() || key.alt()) {
			return 0;
		}
		String name = key.name();
		char c = name.charAt(0);
		if (name.length() == 1 && c >= 'a' && c <= 'z') {
			return key.shift() ? Character.toUpperCase(c) : c;
		}
		if (name.length() == 1 && c >= '0' && c <= '9') {
			return c;
		}
		return name.equals("space") ? ' ' : 0;
	}

	/** SDL modifier bits (left shift, left ctrl, left alt) for a key's flags. */
	static int sdlModifiers(Key key) {
		return (key.shift() ? 0x1 : 0) | (key.ctrl() ? 0x40 : 0) | (key.alt() ? 0x100 : 0);
	}

	/** GLFW modifier bits for a key's flags. */
	static int glfwModifiers(Key key) {
		return key.modifiers();
	}

	/** The SDL keycode (what Screen reads for Tab and arrows) of a key with the given SDL scancode. */
	static int sdlKeycode(Key key, int scancode) {
		String name = key.name();
		if (name.length() == 1) {
			return name.charAt(0);
		}
		return switch (name) {
			case "space" -> ' ';
			case "escape" -> 27;
			case "enter" -> '\r';
			case "tab" -> '\t';
			case "backspace" -> '\b';
			case "delete" -> 127;
			default -> scancode | 0x40000000;
		};
	}
}
