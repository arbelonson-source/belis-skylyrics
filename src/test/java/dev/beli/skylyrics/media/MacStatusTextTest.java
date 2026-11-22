package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.beli.skylyrics.InLanguage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * When osascript itself fails, the Music tab's Status line shows why (MediaTracker passes the message on as it is), so
 * that message is in the game's language like the rest of the tab. No Mac needed: osascript's answers are made up.
 */
class MacStatusTextTest {
	/** Each start takes the next plan: a run, or an IOException to throw. */
	private static final class Runs implements MacMediaSource.Runner {
		final Deque<Object> plans = new ArrayDeque<>();

		@Override
		public MacMediaSource.Run start(List<String> command) throws IOException {
			Object plan = plans.poll();
			if (plan instanceof IOException e) {
				throw e;
			}
			if (plan == null) {
				throw new AssertionError("osascript started unexpectedly");
			}
			return (MacMediaSource.Run) plan;
		}
	}

	/** An osascript that ends with {@code result}, or never when it is null. */
	private static MacMediaSource.Run run(MacMediaSource.@Nullable Result result) {
		return new MacMediaSource.Run() {
			@Override
			public MacMediaSource.@Nullable Result await(long ms) {
				return result;
			}

			@Override
			public void kill() {
			}
		};
	}

	@Test
	void osascriptThatWontStartIsSaidInTheGamesLanguage() throws Exception {
		try (InLanguage de = InLanguage.of("de_de")) {
			Runs runs = new Runs();
			runs.plans.add(new IOException("error=2, No such file or directory"));
			MacMediaSource source = new MacMediaSource(() -> "", runs, 0);
			String german = de.says("Couldn't start osascript: %s", "error=2, No such file or directory");
			assertEquals(german, assertThrows(IOException.class, source::poll).getMessage());
			// it waits a while before trying again, and says the same meanwhile
			assertEquals(german, assertThrows(IOException.class, source::poll).getMessage());
		}
	}

	@Test
	void osascriptThatFailsIsSaidInTheGamesLanguage() throws Exception {
		try (InLanguage de = InLanguage.of("de_de")) {
			Runs runs = new Runs();
			runs.plans.add(run(new MacMediaSource.Result(1, "", "execution error: -1708\nmore")));
			runs.plans.add(run(new MacMediaSource.Result(1, "", "")));
			MacMediaSource source = new MacMediaSource(() -> "", runs, 0);
			assertEquals(de.says("osascript failed: %s", "execution error: -1708"),
					assertThrows(IOException.class, source::poll).getMessage());
			assertEquals(de.says("osascript failed: %s", de.says("exit code %s", 1)),
					assertThrows(IOException.class, source::poll).getMessage());
		}
	}

	@Test
	void osascriptThatHangsIsSaidInTheGamesLanguage() throws Exception {
		try (InLanguage de = InLanguage.of("de_de")) {
			Runs runs = new Runs();
			runs.plans.add(run(null));
			MacMediaSource source = new MacMediaSource(() -> "", runs, 0);
			// A poll gives up the same way, only after waiting a minute for the permission question to be answered
			assertEquals(de.says("osascript didn't answer in time"),
					assertThrows(IOException.class, source::checkScript).getMessage());
		}
	}
}
