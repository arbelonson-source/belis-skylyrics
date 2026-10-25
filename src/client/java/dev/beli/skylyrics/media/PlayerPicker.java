package dev.beli.skylyrics.media;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/** Decides which of several open players to follow. Shared by the Linux and Windows sources. */
final class PlayerPicker {
	private PlayerPicker() {
	}

	/**
	 * @param id        tells players apart (D-Bus name, Windows app id)
	 * @param player    short player name, matched against {@code preferred}
	 * @param preferred part of a player name from the settings; empty = whatever is playing
	 * @param current   the id followed until now, or null
	 */
	static <T> @Nullable T choose(List<T> players, Function<T, String> id, Function<T, String> player, Predicate<T> playing,
			String preferred, @Nullable String current) {
		if (players.isEmpty()) {
			return null;
		}
		preferred = preferred.toLowerCase(Locale.ROOT).trim();
		if (!preferred.isEmpty()) {
			for (T p : players) {
				if (player.apply(p).toLowerCase(Locale.ROOT).contains(preferred)) {
					return p;
				}
			}
		}
		// Stick with the current player while it's playing, so two tabs don't fight
		for (T p : players) {
			if (playing.test(p) && id.apply(p).equals(current)) {
				return p;
			}
		}
		for (T p : players) {
			if (playing.test(p)) {
				return p;
			}
		}
		for (T p : players) {
			if (id.apply(p).equals(current)) {
				return p;
			}
		}
		return players.getFirst();
	}
}
