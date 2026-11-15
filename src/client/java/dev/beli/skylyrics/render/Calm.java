package dev.beli.skylyrics.render;

import net.minecraft.client.Minecraft;

/**
 * Minecraft's own accessibility options, as the lyrics' flashes and motion read them: Hide Lightning Flashes turns the
 * line-start flashes off, Distortion Effects scales the motion. Everything here that takes the option as an argument is
 * plain maths, so it can be tested without a game.
 */
final class Calm {
	/** Line-start flashes begin at most this often (WCAG: no more than three flashes in any one second). */
	static final long FLASH_GAP_NS = 333_333_333L;

	private Calm() {
	}

	/** Whether the player turned on Hide Lightning Flashes. */
	static boolean hideFlashes() {
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.options != null && mc.options.hideLightningFlash().get();
	}

	/** Vanilla's Distortion Effects, 0 to 1 (1 when it can't be read). */
	static float motion() {
		Minecraft mc = Minecraft.getInstance();
		return mc == null || mc.options == null ? 1 : motion(mc.options.screenEffectScale().get());
	}

	/** The option's value as a 0 to 1 scale; anything unreadable counts as full motion. */
	static float motion(double option) {
		return Double.isNaN(option) ? 1 : (float) Math.max(0, Math.min(1, option));
	}

	/** Seconds since a line-start flash began, or NaN (the steady look every effect already has) when flashes are hidden. */
	static float flashSince(boolean hide, float since) {
		return hide ? Float.NaN : since;
	}

	/** A flash's strength, 0 when flashes are hidden. */
	static float flash(boolean hide, float strength) {
		return hide ? 0 : strength;
	}

	/** A motion's distance from where it rests, scaled: 0 leaves it still, 1 leaves it as it was. */
	static float around(float value, float rest, float scale) {
		return rest + (value - rest) * scale;
	}

	/** Whether a line starting at {@code now} may start a flash, given when the last one started. */
	static boolean flashDue(long lastStart, long now) {
		return now - lastStart >= FLASH_GAP_NS;
	}
}
