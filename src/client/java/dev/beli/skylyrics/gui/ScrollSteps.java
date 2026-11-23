package dev.beli.skylyrics.gui;

/**
 * Turns the mouse wheel's scroll amounts into steps of one item, for the song library's carousel, as the game's hotbar
 * does: a notch of a wheel is a step, and the many small amounts a trackpad or a smooth wheel sends for one swipe (0.1 a
 * time) add up to a step a notch's worth, instead of a step each.
 */
final class ScrollSteps {
	/** Whether the game's sideways scroll is above 0 to the right (26.3, SDL) or to the left (before, GLFW). */
	//? if >=26.3 {
	/*static final boolean RIGHT_IS_POSITIVE = true;
	*///?} else {
	static final boolean RIGHT_IS_POSITIVE = false;
	//?}

	/**
	 * The scroll along a list that runs sideways (the shelf, the carousel), in the up-and-down wheel's sense: above 0
	 * towards the start. That is the up-and-down amount, or the sideways one (a trackpad swiped sideways, a tilting wheel,
	 * Shift and the wheel on a Mac) when that is the larger, so a sideways swipe that wobbles a little still goes sideways.
	 * The game gives a scroll to the left as above 0 up to 26.2 (GLFW) and as below 0 from 26.3 (SDL): {@code rightIsPositive}.
	 */
	static double along(double scrollX, double scrollY, boolean rightIsPositive) {
		if (Math.abs(scrollX) <= Math.abs(scrollY)) {
			return scrollY;
		}
		return rightIsPositive ? -scrollX : scrollX;
	}

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
