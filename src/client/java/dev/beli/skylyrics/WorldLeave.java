package dev.beli.skylyrics;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leaving a world. Fabric says so on whichever thread notices (Netty's, when the connection closes or drops), but the
 * lyrics may only let go of the old world on the game thread, between frames. Not through the game thread's task queue:
 * Minecraft.disconnect empties it first thing, just after the connection closed, so a leave queued then was thrown away
 * and the old world stayed in memory on the title screen. So the leave is a flag, which the game thread takes at its next
 * tick, or at the next join if that comes first.
 */
final class WorldLeave {
	private final AtomicBoolean left = new AtomicBoolean();
	private final Runnable leave;

	WorldLeave(Runnable leave) {
		this.leave = leave;
	}

	/** Any thread: a world was left. */
	void left() {
		left.set(true);
	}

	/** Game thread, each tick: lets go of the world that was left, once. */
	void apply() {
		if (left.getAndSet(false)) {
			leave.run();
		}
	}

	/** Game thread: a world was joined. A leave not yet applied goes first, so it can't clear the new world's fresh state. */
	void joined(Runnable join) {
		apply();
		join.run();
	}
}
