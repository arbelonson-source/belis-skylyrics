package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Runs the real swap with the real Windows PowerShell, in a folder named like a hard Windows user folder. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class UpdaterSwapWindowsTest {
	@TempDir
	Path dir;

	/** Hebrew letters, spaces, a curly apostrophe, &, %, ^, ! and brackets. */
	private Path mods() throws Exception {
		return Files.createDirectories(dir.resolve("\u05de\u05d5\u05d3\u2019s test & (1) 100% ^! [x]"));
	}

	/** A process id that has already exited, as the game's is when the swap runs. */
	private static long finishedPid() throws Exception {
		Process p = new ProcessBuilder("cmd", "/c", "exit", "0").start();
		p.waitFor();
		return p.pid();
	}

	private static int run(Path old, Path waiting, Path target) throws Exception {
		ProcessBuilder builder = Updater.swapProcess(Updater.powershell(), old, waiting, target, finishedPid());
		Process p = builder.start();
		if (!p.waitFor(90, TimeUnit.SECONDS)) {
			p.destroyForcibly();
			throw new AssertionError("the swap did not finish");
		}
		return p.exitValue();
	}

	@Test
	void theStagedJarReplacesTheOldOneInAHardFolderName() throws Exception {
		Path mods = mods();
		Path old = Files.writeString(mods.resolve("belis-skylyrics-0.1.0+1.21.11.jar"), "old", StandardCharsets.UTF_8);
		Path target = mods.resolve("belis-skylyrics-0.2.0+1.21.11.jar");
		Path waiting = Files.writeString(mods.resolve("belis-skylyrics-0.2.0+1.21.11.jar.1.new"), "new", StandardCharsets.UTF_8);
		assertEquals(0, run(old, waiting, target));
		assertFalse(Files.exists(old), "the old jar is gone");
		assertEquals("new", Files.readString(target));
		assertFalse(Files.exists(waiting));
		try (var left = Files.list(mods)) {
			assertEquals(1, left.count(), "only the new jar is left, no .old");
		}
	}

	@Test
	void aMissingStagedJarLeavesTheOldOneAlone() throws Exception {
		Path mods = mods();
		Path old = Files.writeString(mods.resolve("belis-skylyrics-0.1.0+1.21.11.jar"), "old");
		Path target = mods.resolve("belis-skylyrics-0.2.0+1.21.11.jar");
		Path waiting = mods.resolve("belis-skylyrics-0.2.0+1.21.11.jar.1.new");
		assertEquals(0, run(old, waiting, target));
		assertEquals("old", Files.readString(old));
		assertFalse(Files.exists(target));
	}

	/** The new jar can't be put in place (a folder sits on its name): the old jar comes back, never an empty mods folder. */
	@Test
	void theOldJarIsPutBackWhenTheNewOneCannotBeMoved() throws Exception {
		Path mods = mods();
		Path old = Files.writeString(mods.resolve("belis-skylyrics-0.1.0+1.21.11.jar"), "old");
		Path target = Files.createDirectory(mods.resolve("belis-skylyrics-0.2.0+1.21.11.jar"));
		Files.writeString(target.resolve("keep"), "x");
		Path waiting = Files.writeString(mods.resolve("belis-skylyrics-0.2.0+1.21.11.jar.1.new"), "new");
		assertEquals(0, run(old, waiting, target));
		assertTrue(Files.isRegularFile(old), "the old jar is back");
		assertEquals("old", Files.readString(old));
	}
}
