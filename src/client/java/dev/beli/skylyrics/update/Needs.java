package dev.beli.skylyrics.update;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * Whether a downloaded jar will load on this game: its fabric.mod.json "depends" for Minecraft, Fabric API,
 * Fabric Loader and Java, matched with Fabric Loader's own version rules. Pure: the running versions come in as a lookup.
 */
final class Needs {
	/** The mod ids whose requirement is checked, with the name shown to the player. */
	private static final String[][] CHECKED = {
			{"minecraft", "Minecraft"}, {"fabric-api", "Fabric API"}, {"fabricloader", "Fabric Loader"}, {"java", "Java"}};

	/**
	 * What the jar needs that this game doesn't have: the thing's name, and the jar's own constraints for it. The plain
	 * words are made when asked, so they are in the game's language when the player is told, not in the one it had when
	 * the update was checked (the check runs before the loading screen has loaded the player's language).
	 */
	record Unmet(String name, List<String> constraints) {
		Unmet {
			constraints = List.copyOf(constraints);
		}

		/** What is needed in plain words, for example "0.137.0 or newer". */
		String needs() {
			return describe(constraints);
		}

		/** For example "Fabric API 0.137.0 or newer". */
		String text() {
			return name + " " + needs();
		}
	}

	private Needs() {
	}

	/**
	 * The first requirement of the jar that this game doesn't meet, or null when it meets all of them.
	 * {@code running} gives the running version of a mod id ("java" is the Java feature version), or null when it isn't there.
	 * An entry that can't be understood counts as unmet, so the jar is not installed.
	 */
	static Unmet unmet(JsonObject depends, UnaryOperator<String> running) {
		if (depends == null) {
			return null;
		}
		for (String[] entry : CHECKED) {
			if (!depends.has(entry[0])) {
				continue;
			}
			List<String> constraints = constraints(depends.get(entry[0]));
			String have = running.apply(entry[0]);
			if (!met(constraints, have)) {
				return new Unmet(entry[1], constraints);
			}
		}
		return null;
	}

	/** The constraints of one entry: a string, or an array of strings of which any one is enough. Empty when not understood. */
	private static List<String> constraints(JsonElement value) {
		List<String> out = new ArrayList<>();
		if (value != null && value.isJsonPrimitive()) {
			out.add(value.getAsString());
		} else if (value != null && value.isJsonArray()) {
			JsonArray array = value.getAsJsonArray();
			for (JsonElement e : array) {
				if (!e.isJsonPrimitive()) {
					return List.of();
				}
				out.add(e.getAsString());
			}
		}
		return out;
	}

	private static boolean met(List<String> constraints, String have) {
		if (constraints.isEmpty() || have == null) {
			return false;
		}
		Version version;
		try {
			version = Version.parse(have);
		} catch (Exception e) {
			return false;
		}
		for (String constraint : constraints) {
			try {
				if (VersionPredicate.parse(constraint).test(version)) {
					return true;
				}
			} catch (Exception e) {
				// not a constraint Fabric understands: that one never counts as met
			}
		}
		return false;
	}

	/** Plain words for the constraints: ">=0.137.0" is "0.137.0 or newer", several are joined with "or". */
	static String describe(List<String> constraints) {
		List<String> parts = new ArrayList<>();
		for (String c : constraints) {
			String s = c.trim();
			if (s.startsWith(">=") && s.indexOf(' ') < 0) {
				parts.add(dev.beli.skylyrics.Tr.t("%s or newer", s.substring(2)));
			} else if (s.startsWith("~") && s.indexOf(' ') < 0) {
				parts.add(s.substring(1) + ".x");
			} else {
				parts.add(s);
			}
		}
		if (parts.isEmpty()) {
			return dev.beli.skylyrics.Tr.t("a version it can't tell");
		}
		String all = parts.get(0);
		for (int i = 1; i < parts.size(); i++) {
			all = dev.beli.skylyrics.Tr.t("%s or %s", all, parts.get(i));
		}
		return all;
	}
}
