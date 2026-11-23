package dev.beli.skylyrics.gui;

import net.minecraft.client.Minecraft;

/**
 * How long the mod's notices stay up: their own time times Minecraft's Notification Display Time. The song library's
 * messages, and the song's name on the top bar and in the World after a song change, as Minecraft's own music pop-up.
 */
public final class ToastTime {
	private ToastTime() {
	}

	/** Minecraft's Notification Display Time (Accessibility), 1 when it can't be read. */
	public static double multiplier() {
		Minecraft mc = Minecraft.getInstance();
		return mc == null || mc.options == null ? 1 : mc.options.notificationDisplayTime().get();
	}

	/** {@code base} seconds scaled by the option's value, which stays in Minecraft's 0 to 10; unreadable counts as 1. */
	public static float shownFor(float base, double multiplier) {
		if (Double.isNaN(multiplier)) {
			multiplier = 1;
		}
		return base * (float) Math.max(0, Math.min(10, multiplier));
	}
}
