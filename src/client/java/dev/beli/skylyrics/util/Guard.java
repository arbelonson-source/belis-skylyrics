package dev.beli.skylyrics.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A safety net for the places the game calls into the mod every frame or tick. One unexpected RuntimeException
 * there would crash the whole game, so it is caught instead: the first one is logged with its stack trace, later
 * ones only as a count once a minute, and that entry point sits out the next few seconds before it runs again.
 * Errors (out of memory and the like) are never caught.
 */
public final class Guard {
	/** How long an entry point stays off after it threw. */
	public static final long MUTE_NANOS = 5_000_000_000L;
	/** How often the count of repeated errors is logged. */
	public static final long COUNT_EVERY_NANOS = 60_000_000_000L;

	/** Where the messages go; the test swaps it. */
	public interface Sink {
		void first(String what, RuntimeException error);

		void count(String what, int errors);
	}

	private static final class State {
		long mutedUntil;
		boolean muted;
		boolean logged;
		long lastLog;
		int unreported;
	}

	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	private static final Guard SHARED = new Guard(System::nanoTime, new Sink() {
		@Override
		public void first(String what, RuntimeException error) {
			LOG.error("Error in {}; its drawing pauses for a few seconds (later ones are only counted)", what, error);
		}

		@Override
		public void count(String what, int errors) {
			LOG.error("{} more error(s) in {} in the last minute", errors, what);
		}
	});

	private final LongSupplier clock;
	private final Sink sink;
	private final Map<String, State> states = new HashMap<>();

	public Guard(LongSupplier clock, Sink sink) {
		this.clock = clock;
		this.sink = sink;
	}

	/** Runs {@code body} unless this entry point is muted; a RuntimeException from it is logged and mutes it. */
	public static void run(String what, Runnable body) {
		SHARED.attempt(what, body);
	}

	/** Like {@link #run}, for a body that answers; false when it threw or was muted. */
	public static boolean test(String what, BooleanSupplier body) {
		boolean[] result = {false};
		SHARED.attempt(what, () -> result[0] = body.getAsBoolean());
		return result[0];
	}

	/** True if the body ran without throwing. */
	public boolean attempt(String what, Runnable body) {
		State state;
		synchronized (states) {
			state = states.computeIfAbsent(what, k -> new State());
			if (state.muted && clock.getAsLong() - state.mutedUntil < 0) {
				return false;
			}
			state.muted = false;
		}
		try {
			body.run();
			return true;
		} catch (RuntimeException e) {
			failed(what, state, e);
			return false;
		}
	}

	private void failed(String what, State state, RuntimeException e) {
		long now = clock.getAsLong();
		boolean first = false;
		int count = 0;
		synchronized (states) {
			state.muted = true;
			state.mutedUntil = now + MUTE_NANOS;
			if (!state.logged) {
				state.logged = true;
				state.lastLog = now;
				first = true;
			} else {
				state.unreported++;
				if (now - state.lastLog >= COUNT_EVERY_NANOS) {
					count = state.unreported;
					state.unreported = 0;
					state.lastLog = now;
				}
			}
		}
		try {
			if (first) {
				sink.first(what, e);
			} else if (count > 0) {
				sink.count(what, count);
			}
		} catch (RuntimeException ignored) {
			// logging must never be what throws
		}
	}
}
