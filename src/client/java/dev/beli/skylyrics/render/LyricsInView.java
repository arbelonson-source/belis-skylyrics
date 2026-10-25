package dev.beli.skylyrics.render;

import org.joml.Vector3f;

/**
 * Whether the lyrics in the world were on screen a moment ago, so the top bar can step aside
 * while they are. Every in-world lyrics renderer (the sky lyrics, and lyrics on blocks) calls
 * {@link #check} once a frame while it shows text. Render thread only.
 */
public final class LyricsInView {
	private static final long HOLD_NS = 300_000_000L;
	private static long seenAt = Long.MIN_VALUE;

	private LyricsInView() {}

	/** A camera-relative block of text, with its rough half width and height in blocks. */
	public static void check(Vector3f centre, float halfW, float halfH) {
		// Once seen, it has to move a bit further out before it counts as gone, so the edge doesn't flicker
		float margin = onScreen() ? 0.12f : 0;
		if (ScreenLayer.inView(centre, halfW, halfH, margin)) {
			seenAt = System.nanoTime();
		}
	}

	public static boolean onScreen() {
		return seenAt != Long.MIN_VALUE && System.nanoTime() - seenAt < HOLD_NS;
	}
}
