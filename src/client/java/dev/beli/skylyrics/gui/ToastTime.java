package dev.beli.skylyrics.gui;

import net.minecraft.client.Minecraft;

/** How long the song library's messages stay up: its own time times Minecraft's Notification Display Time. */
final class ToastTime {
	private ToastTime() {
	}

	/** Minecraft's Notification Display Time (Accessibility), 1 when it can't be read. */
	static double multiplier() {
		Minecraft mc = Minecraft.getInstance();
		return mc == null || mc.options == null ? 1 : mc.options.notificationDisplayTime().get();
	}

	/** {@code base} seconds scaled by the option's value, which stays in Minecraft's 0 to 10; unreadable counts as 1. */
	static float shownFor(float base, double multiplier) {
		if (Double.isNaN(multiplier)) {
			multiplier = 1;
		}
		return base * (float) Math.max(0, Math.min(10, multiplier));
	}
}
