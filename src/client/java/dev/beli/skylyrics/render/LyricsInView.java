package dev.beli.skylyrics.render;

import java.util.function.LongSupplier;

import org.joml.Vector3f;

/**
 * Whether the lyrics in the world were on screen a moment ago, so the top bar can step aside
 * while they are. Every in-world lyrics renderer (the sky lyrics, and lyrics on blocks) calls
 * {@link #check} once a frame while it shows text. Render thread only.
 */
public final class LyricsInView {
	private static final long HOLD_NS = 300_000_000L;
	private static long seenAt = Long.MIN_VALUE;
	/** The clock in nanoseconds; tests swap in their own so they don't depend on how long a sleep really takes. */
	static LongSupplier clock = System::nanoTime;

	private LyricsInView() {}

	/** A camera-relative block of text, with its rough half width and height in blocks. */
	public static void check(Vector3f centre, float halfW, float halfH) {
		// Once seen, it has to move a bit further out before it counts as gone, so the edge doesn't flicker
		float margin = onScreen() ? 0.12f : 0;
		if (ScreenLayer.inView(centre, halfW, halfH, margin)) {
			seenAt = clock.getAsLong();
		}
	}

	/** Extra slack round the screen, as a fraction of it, before a line is skipped: generous so nothing pops at the edge. */
	static final float CULL_MARGIN = 0.3f;

	/**
	 * Whether a line block is worth drawing this frame. False only when it is clearly outside the view (behind you,
	 * or well past an edge, allowing for its size and the slack). Before the first world frame nothing is known, so draw.
	 */
	static boolean worthDrawing(Vector3f centre, float halfW, float halfH) {
		return !ScreenLayer.ready() || ScreenLayer.inView(centre, halfW, halfH, CULL_MARGIN);
	}

	public static boolean onScreen() {
		return seenAt != Long.MIN_VALUE && clock.getAsLong() - seenAt < HOLD_NS;
	}
}
