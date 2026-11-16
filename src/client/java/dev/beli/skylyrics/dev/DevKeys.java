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

	/** The longest {@code hold} the hook accepts, in milliseconds. */
	static final int MAX_HOLD_MS = 120_000;

	/** A key binding to hold down, by its full name ("key.playerlist"), for this many milliseconds. */
	record Hold(String mapping, int ms) {
	}

	/** "key.playerlist 1500": a mapping name (see {@link #mappingName}) and a duration of 1 to 120000 ms. */
	static Hold parseHold(String arg) {
		String[] words = arg.strip().split("\\s+");
		if (words.length != 2 || words[0].isEmpty()) {
			throw new IllegalArgumentException("expected: hold <mapping> <ms>");
		}
		int ms;
		try {
			ms = Integer.parseInt(words[1]);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("bad duration " + words[1]);
		}
		if (ms < 1 || ms > MAX_HOLD_MS) {
			throw new IllegalArgumentException("duration must be 1 to " + MAX_HOLD_MS + " ms");
		}
		return new Hold(mappingName(words[0]), ms);
	}

	/** A mapping name as written in options.txt ("key_key.sneak"), as the KeyMapping's own name ("key.sneak") or short ("sneak"). */
	static String mappingName(String raw) {
		String name = raw.strip().toLowerCase(java.util.Locale.ROOT);
		if (name.startsWith("key_")) {
			name = name.substring(4);
		}
		return name.startsWith("key.") ? name : "key." + name;
	}

	/** The old "use on" or "use off" form of {@code hold}: TRUE for on, FALSE for off, null when it is not that form. */
	static Boolean legacyUse(String arg) {
		return switch (arg.strip()) {
			case "use on" -> Boolean.TRUE;
			case "use off" -> Boolean.FALSE;
			default -> null;
		};
	}

	/**
	 * Which held keys are due to be let go, by time in milliseconds. Holding a key again moves its deadline.
	 * The test hook lets go of what {@link #expire} returns, and presses everything {@link #held} returns each tick.
	 */
	static final class HoldSchedule<T> {
		private final java.util.Map<T, Long> deadlines = new java.util.LinkedHashMap<>();

		void hold(T key, long nowMs, long ms) {
			deadlines.put(key, nowMs + ms);
		}

		/** Takes out and returns the keys whose time is up at {@code nowMs}. */
		java.util.List<T> expire(long nowMs) {
			java.util.List<T> done = new java.util.ArrayList<>();
			for (var it = deadlines.entrySet().iterator(); it.hasNext();) {
				var entry = it.next();
				if (nowMs >= entry.getValue()) {
					done.add(entry.getKey());
					it.remove();
				}
			}
			return done;
		}

		java.util.List<T> held() {
			return new java.util.ArrayList<>(deadlines.keySet());
		}

		java.util.List<T> releaseAll() {
			java.util.List<T> all = held();
			deadlines.clear();
			return all;
		}
	}
}
