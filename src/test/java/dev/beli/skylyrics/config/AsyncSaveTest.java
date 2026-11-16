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
		assertTrue(Files.readString(file).contains("0.3"));
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
		int[] writes = {0};
		SkyLyricsConfig.mainThread = r -> {
			writes[0]++;
			mainQueue.add(r);
		};
		runWriter();
		assertEquals(1, writes[0]);
		String text = Files.readString(file);
		assertTrue(text.contains("0.6"));
		assertFalse(text.contains("0.3"));
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
		assertTrue(Files.readString(file).contains("0.5"));
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
		assertTrue(Files.readString(file).contains("0.3"));
	}

	@Test
	void flushGivesUpWhenTheWriterNeverRuns() {
		SkyLyricsConfig c = new SkyLyricsConfig();
		c.saveLater(dir.resolve("skylyrics.json"));
		assertFalse(c.flush(50));
	}
}
