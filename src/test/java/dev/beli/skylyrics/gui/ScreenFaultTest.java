package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.LogCapture;

/** What a screen does with an error the game would have ended on: log it, go back once, and keep later ones short. */
class ScreenFaultTest {
	private static List<String> at(LogCapture log, String level) {
		return log.lines().stream().filter(l -> l.startsWith(level)).toList();
	}

	@Test
	void theFirstErrorIsLoggedWithItsCauseAndTheScreenGoesBackOnce() {
		AtomicInteger left = new AtomicInteger();
		ScreenFault fault = new ScreenFault("the settings", left::incrementAndGet);
		try (LogCapture log = new LogCapture()) {
			assertFalse(fault.hasFailed());
			assertTrue(fault.failed("drawing", new IllegalStateException("test: a row broke")), "the event counts as used");
			assertTrue(fault.hasFailed());
			assertEquals(1, left.get());
			// The rest of the call that failed may run the screen again: its errors don't send it back a second time
			fault.failed("its narration", new IllegalStateException("test: again"));
			assertEquals(1, left.get());
			List<String> errors = at(log, "ERROR");
			assertEquals(1, errors.size(), "logged in full once: " + log.lines());
			assertTrue(errors.get(0).contains("Error in the settings (drawing)") && errors.get(0).contains("test: a row broke"), errors.get(0));
			List<String> warnings = at(log, "WARN");
			assertEquals(1, warnings.size(), log.lines().toString());
			assertTrue(warnings.get(0).contains("its narration") && warnings.get(0).contains("test: again"), warnings.get(0));
		}
	}

	@Test
	void laterErrorsAreOnlyNotedAFewTimes() {
		ScreenFault fault = new ScreenFault("the song library", () -> { });
		try (LogCapture log = new LogCapture()) {
			for (int i = 0; i < 50; i++) {
				fault.failed("drawing", new IllegalStateException("test: " + i));
			}
			assertEquals(1, at(log, "ERROR").size());
			assertEquals(ScreenFault.MORE_LOGGED, at(log, "WARN").size(), "a screen failing every frame must not flood the log");
		}
	}

	@Test
	void anErrorWhileGoingBackIsLoggedNotThrown() {
		ScreenFault fault = new ScreenFault("the tour", () -> {
			throw new IllegalStateException("test: the screen before is broken too");
		});
		try (LogCapture log = new LogCapture()) {
			assertDoesNotThrow(() -> fault.failed("a button", new IllegalStateException("test: first")));
			List<String> errors = at(log, "ERROR");
			assertEquals(2, errors.size(), log.lines().toString());
			assertTrue(errors.get(1).contains("could not close") && errors.get(1).contains("the screen before is broken too"), errors.get(1));
		}
	}

	@Test
	void anErrorWhileClosingDoesNotGoBackAgain() {
		AtomicInteger left = new AtomicInteger();
		ScreenFault fault = new ScreenFault("the settings", left::incrementAndGet);
		try (LogCapture log = new LogCapture()) {
			// removed() runs while the game is already switching to the next screen
			fault.failedClosing(new IllegalStateException("test: saving broke"));
			assertEquals(0, left.get());
			List<String> errors = at(log, "ERROR");
			assertEquals(1, errors.size(), log.lines().toString());
			assertTrue(errors.get(0).contains("while it closed") && errors.get(0).contains("test: saving broke"), errors.get(0));
		}
	}
}
