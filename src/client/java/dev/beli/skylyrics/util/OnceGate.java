package dev.beli.skylyrics.util;

/** Lets a repeating problem be logged once until it has gone away again. */
public final class OnceGate {
	private boolean open = true;

	/** True the first time after creation or {@link #reset()}, false while the problem goes on. */
	public synchronized boolean shouldLog() {
		boolean first = open;
		open = false;
		return first;
	}

	/** The thing worked again: the next failure is news. */
	public synchronized void reset() {
		open = true;
	}
}
