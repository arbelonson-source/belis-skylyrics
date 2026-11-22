package dev.beli.skylyrics.gui;

/**
 * Keeps what the keyboard picked (a card in the song library, a row in the settings) in view when the window changes size
 * under it. The scroll is kept inside the content every frame, but a resize also moves the pick (fewer columns push a card
 * further down, narrower rows stack up, a shorter panel shows less), and nothing scrolled to it until the next key.
 */
final class PickInView {
	private boolean keys;
	private int width = -1, height = -1;

	/** The keys moved the pick (and scrolled to it): it is followed through a resize from now on. */
	void keyPicked() {
		keys = true;
	}

	/**
	 * The mouse picked or scrolled, or the keys scrolled the view on purpose (Page Down): the scroll stays where the player
	 * left it.
	 */
	void scrolledAway() {
		keys = false;
	}

	/**
	 * Called once a frame with the screen's size: true when the screen has just changed size and the keys made the pick,
	 * so the screen scrolls the pick back into view. Never on the first frame.
	 */
	boolean follow(int width, int height) {
		boolean resized = this.width >= 0 && (width != this.width || height != this.height);
		this.width = width;
		this.height = height;
		return resized && keys;
	}
}
