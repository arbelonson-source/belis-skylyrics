package dev.beli.skylyrics.dev;

/** Key names and modifiers of the test hook's {@code key} command, kept free of Minecraft so they can be tested. */
final class DevKeys {
	private static final int SHIFT = 1;
	private static final int CTRL = 2;
	private static final int ALT = 4;

	private DevKeys() {
	}

	/** A GLFW key code with its modifier bits. */
	record Key(int code, int modifiers) {
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
		return new Key(code(words[0].toUpperCase(java.util.Locale.ROOT)), modifiers);
	}

	private static int code(String name) {
		if (name.length() == 1) {
			char c = name.charAt(0);
			if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
				return c;
			}
		}
		if (name.matches("F([1-9]|1[0-9]|2[0-5])")) {
			return 289 + Integer.parseInt(name.substring(1));
		}
		return switch (name) {
			case "SPACE" -> 32;
			case "ESCAPE", "ESC" -> 256;
			case "ENTER", "RETURN" -> 257;
			case "TAB" -> 258;
			case "BACKSPACE" -> 259;
			case "INSERT" -> 260;
			case "DELETE" -> 261;
			case "RIGHT" -> 262;
			case "LEFT" -> 263;
			case "DOWN" -> 264;
			case "UP" -> 265;
			case "PAGE_UP" -> 266;
			case "PAGE_DOWN" -> 267;
			case "HOME" -> 268;
			case "END" -> 269;
			default -> throw new IllegalArgumentException("unknown key " + name);
		};
	}

	/** The character a key press types, or 0 when it types none (control keys, Ctrl or Alt shortcuts, F-keys). */
	static int charFor(Key key) {
		if ((key.modifiers() & (CTRL | ALT)) != 0) {
			return 0;
		}
		int code = key.code();
		if (code >= 'A' && code <= 'Z') {
			return (key.modifiers() & SHIFT) != 0 ? code : Character.toLowerCase(code);
		}
		return (code >= '0' && code <= '9') || code == 32 ? code : 0;
	}
}
