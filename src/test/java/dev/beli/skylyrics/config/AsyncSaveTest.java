package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.util.SafeFile;

/** Saving is handed to the writer thread; the game thread only takes a snapshot. */
class AsyncSaveTest {
	@TempDir
	Path dir;

	private Consumer<Runnable> oldWriter;
	private Consumer<Runnable> oldMain;
	private final List<Runnable> held = new ArrayList<>();
	private final List<Runnable> mainQueue = new ArrayList<>();

	@BeforeEach
	void hold() {
		oldWriter = SkyLyricsConfig.writerExec;
		oldMain = SkyLyricsConfig.mainThread;
		SkyLyricsConfig.writerExec = held::add;
		SkyLyricsConfig.mainThread = mainQueue::add;
	}

	@AfterEach
	void restore() {
		SkyLyricsConfig.writerExec = oldWriter;
		SkyLyricsConfig.mainThread = oldMain;
	}

	private void runWriter() {
		List<Runnable> now = new ArrayList<>(held);
		held.clear();
		now.forEach(Runnable::run);
	}

	private void runMain() {
		List<Runnable> now = new ArrayList<>(mainQueue);
		mainQueue.clear();
		now.forEach(Runnable::run);
	}

	private SkyLyricsConfig open(Path file) {
		return SkyLyricsConfig.load(file, SafeFile::readText);
	}

	@Test
	void savingDoesNotTouchTheFileSystemUntilTheWriterRuns() throws Exception {
		Path file = dir.resolve("config").resolve("skylyrics.json");
		SkyLyricsConfig c = new SkyLyricsConfig();
		c.glow = 0.3f;
		c.saveLater(file);
		assertFalse(Files.exists(file));
		assertFalse(Files.exists(file.getParent()));
		assertEquals(1, held.size());
		runWriter();
		assertEquals(0.3f, open(file).glow, 1e-6f);
	}

	@Test
	void twoQuickSavesWriteOnceWithTheLatestValues() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		SkyLyricsConfig c = new SkyLyricsConfig();
		c.glow = 0.3f;
		c.saveLater(file);
		c.glow = 0.6f;
		c.saveLater(file);
		assertEquals(1, held.size());
		int[] handedBack = {0};
		SkyLyricsConfig.mainThread = r -> {
			handedBack[0]++;
			mainQueue.add(r);
		};
		runWriter();
		assertEquals(1, handedBack[0]);
		assertEquals(0.6f, open(file).glow, 1e-6f);
	}

	@Test
	void aSettingChangedAfterTheSnapshotIsNotOverwrittenByWhatCameBack() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		SkyLyricsConfig a = open(file);
		SkyLyricsConfig b = open(file);
		b.glow = 0.9f;
		b.save(file); // the other game changed glow and size
		a.glow = 0.3f;
		a.saveLater(file);
		a.glow = 0.5f; // pressed again before the writer ran
		runWriter();
		runMain();
		assertEquals(0.5f, a.glow, 1e-6f);
		a.saveLater(file);
		runWriter();
		runMain();
		assertEquals(0.5f, open(file).glow, 1e-6f);
	}

	@Test
	void aSettingOnlyTheOtherGameChangedComesFromTheFile() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		SkyLyricsConfig a = open(file);
		SkyLyricsConfig b = open(file);
		b.size = 2f;
		b.save(file);
		a.glow = 0.3f;
		a.saveLater(file);
		runWriter();
		assertEquals(1f, a.size, 1e-6f); // not applied until the game thread runs it
		runMain();
		assertEquals(2f, a.size, 1e-6f);
		assertEquals(0.3f, a.glow, 1e-6f);
	}

	@Test
	void theOtherGamesValueForAFieldChangedMeanwhileDoesNotComeBackOverTheNewValue() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		SkyLyricsConfig a = open(file);
		SkyLyricsConfig b = open(file);
		b.glow = 0.9f;
		b.save(file);
		a.size = 2f;
		a.saveLater(file);
		a.glow = 0.4f; // the player changes glow while the other game had set 0.9
		runWriter();
		runMain();
		assertEquals(0.4f, a.glow, 1e-6f);
		assertEquals(2f, a.size, 1e-6f);
	}

	@Test
	void aPendingSaveIsOnDiskAfterFlush() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		SkyLyricsConfig.writerExec = oldWriter;
		SkyLyricsConfig c = new SkyLyricsConfig();
		c.glow = 0.3f;
		c.saveLater(file);
		assertTrue(c.flush(10_000));
		assertEquals(0.3f, open(file).glow, 1e-6f);
	}

	@Test
	void anErrorInsideTheWriterDoesNotStopLaterSaves() {
		SkyLyricsConfig c = new SkyLyricsConfig();
		SkyLyricsConfig.mainThread = r -> {
			throw new OutOfMemoryError("test");
		};
		c.saveLater(dir.resolve("skylyrics.json"));
		assertEquals(1, held.size());
		try {
			runWriter();
		} catch (OutOfMemoryError expected) {
			// the writer task ends with the Error
		}
		c.glow = 0.3f;
		c.saveLater(dir.resolve("skylyrics.json"));
		assertEquals(1, held.size(), "a new writer is started for the next save");
	}

	@Test
	void aSaveQueuedWhileTheWriterDiesIsWrittenWithoutAnotherSave() {
		Path file = dir.resolve("skylyrics.json");
		SkyLyricsConfig c = new SkyLyricsConfig();
		boolean[] first = {true};
		SkyLyricsConfig.mainThread = r -> {
			if (first[0]) {
				first[0] = false;
				c.glow = 0.3f;
				c.saveLater(file); // arrives while the writer is still busy
				throw new OutOfMemoryError("test");
			}
			r.run();
		};
		c.saveLater(file);
		try {
			runWriter();
		} catch (OutOfMemoryError expected) {
			// the first writer ends with the Error
		}
		assertEquals(1, held.size(), "the writer is started again for the waiting save");
		runWriter();
		assertEquals(0.3f, open(file).glow, 1e-6f);
	}

	@Test
	void aSaveThatFailedIsTriedAgainOnFlush() throws Exception {
		Path blocker = dir.resolve("blocked");
		Files.writeString(blocker, "a file where the folder should be");
		Path file = blocker.resolve("skylyrics.json");
		SkyLyricsConfig c = new SkyLyricsConfig();
		c.glow = 0.3f;
		c.saveLater(file);
		runWriter(); // fails: the parent is a file
		assertFalse(Files.exists(file));
		Files.delete(blocker);
		assertTrue(c.flush(1000));
		assertEquals(0.3f, open(file).glow, 1e-6f);
	}

	@Test
	void aFailedSaveIsTriedOnlyOnceMoreOnFlush() throws Exception {
		Path blocker = dir.resolve("blocked");
		Files.writeString(blocker, "x");
		SkyLyricsConfig c = new SkyLyricsConfig();
		c.saveLater(blocker.resolve("skylyrics.json"));
		runWriter();
		assertFalse(c.flush(1000)); // the retry fails too
		assertTrue(c.flush(1000)); // and is not remembered again
	}

	@Test
	void flushGivesUpWhenTheWriterNeverRuns() {
		SkyLyricsConfig c = new SkyLyricsConfig();
		c.saveLater(dir.resolve("skylyrics.json"));
		assertFalse(c.flush(50));
	}

	private static final dev.beli.skylyrics.media.Track SONG = new dev.beli.skylyrics.media.Track("Paper Moon", "Nova Lane", "", 180_000, "", "spotify", "");

	@Test
	void aTimingSetAndResetWhileTheFirstWriteIsNotTakenOverYetIsResetOnDisk() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		SkyLyricsConfig c = open(file);
		c.setSongOffset(SONG, 100);
		c.saveLater(file);
		runWriter(); // A is written, its apply waits on the main queue
		c.setSongOffset(SONG, 0);
		c.saveLater(file);
		runWriter();
		assertFalse(Files.readString(file).contains("Paper Moon"), Files.readString(file));
		runMain();
		assertEquals(0, c.songOffset(SONG));
		assertFalse(Files.readString(file).contains("Paper Moon"));
	}

	@Test
	void aToggleOnThenOffBeforeTheFirstWriteIsTakenOverIsOffOnDisk() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		SkyLyricsConfig c = open(file);
		boolean start = c.hideWithF1;
		c.hideWithF1 = !start;
		c.saveLater(file);
		runWriter();
		c.hideWithF1 = start;
		c.saveLater(file);
		runWriter();
		runMain();
		assertEquals(start, c.hideWithF1);
		assertEquals(start, open(file).hideWithF1);
	}

	@Test
	void theOtherGamesChangeBetweenTheTwoWritesStillComesThrough() throws Exception {
		Path file = dir.resolve("skylyrics.json");
		new SkyLyricsConfig().save(file);
		SkyLyricsConfig a = open(file);
		SkyLyricsConfig b = open(file);
		a.glow = 0.3f;
		a.saveLater(file);
		runWriter();
		b.size = 2f;
		b.save(file);
		a.glow = 0.5f;
		a.saveLater(file);
		runWriter();
		runMain();
		assertEquals(0.5f, a.glow, 1e-6f);
		assertEquals(2f, a.size, 1e-6f);
		SkyLyricsConfig after = open(file);
		assertEquals(0.5f, after.glow, 1e-6f);
		assertEquals(2f, after.size, 1e-6f);
	}
}
