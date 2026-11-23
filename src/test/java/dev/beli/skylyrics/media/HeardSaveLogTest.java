package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.LogCapture;
import dev.beli.skylyrics.util.SafeFile;

/**
 * A game folder the Recently heard list can't be saved in (read-only, or a drive that refuses writes): the save is tried
 * on every song change, and the game log gets the warning once, not once per song; after a save that works, the next
 * failure is news again. Like the settings and the other savers.
 */
class HeardSaveLogTest {
	@TempDir
	Path dir;

	private volatile boolean refused = true;

	private static Track song(String title) {
		return new Track(title, "Made Up Band", "Album", 200_000, "", "mpv", "file:///music/" + title + ".mp3");
	}

	private static long saveWarnings(LogCapture log) {
		return log.lines().stream().filter(l -> l.startsWith("WARN") && l.contains("Couldn't save the recently heard songs")).count();
	}

	@Test
	void aSaveThatKeepsFailingIsLoggedOnceUntilOneWorks() {
		HeardLibrary heard = new HeardLibrary(dir.resolve("heard.json"), () -> true, HeardLibrary.NO_OPENER, SafeFile::readText,
				(from, to, options) -> {
					if (refused) {
						throw new AccessDeniedException(to.toString());
					}
					Files.move(from, to, options);
				});
		heard.savePauseMs = 0; // still all of SafeFile's tries, without its pauses between them (775 ms for each refused save)
		try (LogCapture log = new LogCapture()) {
			for (int i = 1; i <= 3; i++) {
				heard.record(song("Song " + i));
				heard.flush();
			}
			assertEquals(1, saveWarnings(log), "three song changes in a folder that refuses the save: " + log.lines());

			refused = false;
			heard.record(song("Song 4"));
			heard.flush();
			assertEquals(1, saveWarnings(log), "a save that works logs no warning");

			refused = true;
			heard.record(song("Song 5"));
			heard.flush();
			assertEquals(2, saveWarnings(log), "after a save went through, the next failure is news: " + log.lines());
		}
	}
}
