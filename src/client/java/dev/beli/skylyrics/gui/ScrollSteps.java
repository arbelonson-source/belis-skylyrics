package dev.beli.skylyrics.gui;

/**
 * Turns the mouse wheel's scroll amounts into steps of one item, for the song library's carousel, as the game's hotbar
 * does: a notch of a wheel is a step, and the many small amounts a trackpad or a smooth wheel sends for one swipe (0.1 a
 * time) add up to a step a notch's worth, instead of a step each.
 */
final class ScrollSteps {
	/** What has been scrolled one way and not used for a step yet. */
	private double sum;

	/**
	 * The step for a scroll of {@code amount} (above 0 is up, towards the start): -1 back, 1 on, or 0 while less than a
	 * notch has been scrolled. At most one step for each scroll, however far it goes (a high Scroll Sensitivity).
	 */
	int step(double amount) {
		if (sum != 0 && Math.signum(amount) != Math.signum(sum)) {
			// Turning back: what was left the other way doesn't count
			sum = 0;
		}
		sum += amount;
		int whole = (int) sum;
		if (whole == 0) {
			return 0;
		}
		sum -= whole;
		return whole > 0 ? -1 : 1;
	}
}
