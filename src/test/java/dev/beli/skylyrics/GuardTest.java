package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.util.Guard;

class GuardTest {
	private final AtomicLong now = new AtomicLong(1_000_000_000L);
	private final List<String> log = new ArrayList<>();
	private final Guard guard = new Guard(now::get, new Guard.Sink() {
		@Override
		public void first(String what, Throwable error) {
			log.add("first " + what + " " + error.getMessage());
		}

		@Override
		public void count(String what, int errors) {
			log.add("count " + what + " " + errors);
		}
	});

	private static void boom() {
		throw new IllegalStateException("boom");
	}

	@Test
	void aThrowMutesForFiveSecondsThenRunsAgain() {
		AtomicInteger runs = new AtomicInteger();
		assertFalse(guard.attempt("hud", () -> {
			runs.incrementAndGet();
			boom();
		}));
		assertEquals(1, runs.get());
		now.addAndGet(Guard.MUTE_NANOS - 1);
		assertFalse(guard.attempt("hud", runs::incrementAndGet));
		assertEquals(1, runs.get(), "still muted");
		now.addAndGet(1);
		assertTrue(guard.attempt("hud", runs::incrementAndGet));
		assertEquals(2, runs.get());
	}

	@Test
	void onlyTheFirstErrorIsLoggedInFullThenACountEachMinute() {
		// a throw every 5 s: the first is logged in full, the next eleven are silent
		for (int i = 0; i < 12; i++) {
			guard.attempt("hud", GuardTest::boom);
			now.addAndGet(Guard.MUTE_NANOS);
		}
		assertEquals(List.of("first hud boom"), log);
		// the 13th throw comes a full minute after the first: one count of the 12 errors since the first
		guard.attempt("hud", GuardTest::boom);
		assertEquals(List.of("first hud boom", "count hud 12"), log);
	}

	@Test
	void entryPointsAreMutedSeparately() {
		guard.attempt("a", GuardTest::boom);
		AtomicInteger ran = new AtomicInteger();
		assertTrue(guard.attempt("b", ran::incrementAndGet));
		assertEquals(1, ran.get());
	}

	@Test
	void linkErrorsAreCaughtAndMute() {
		AtomicInteger runs = new AtomicInteger();
		assertFalse(guard.attempt("x", () -> {
			runs.incrementAndGet();
			throw new NoSuchMethodError("gone in this version");
		}));
		assertFalse(guard.attempt("x", runs::incrementAndGet));
		assertEquals(1, runs.get(), "muted after the link error");
		assertEquals(List.of("first x gone in this version"), log);
	}

	@Test
	void otherErrorsAreNotCaught() {
		assertThrows(OutOfMemoryError.class, () -> guard.attempt("x", () -> {
			throw new OutOfMemoryError();
		}));
		assertThrows(StackOverflowError.class, () -> guard.attempt("x", () -> {
			throw new StackOverflowError();
		}));
	}

	@Test
	void aBrokenLoggerCannotThrow() {
		Guard loud = new Guard(now::get, new Guard.Sink() {
			@Override
			public void first(String what, Throwable error) {
				throw new IllegalArgumentException("log broke");
			}

			@Override
			public void count(String what, int errors) {
			}
		});
		assertFalse(loud.attempt("x", GuardTest::boom));
	}
}
