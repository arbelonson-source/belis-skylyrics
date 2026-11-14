package dev.beli.skylyrics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeFileTest {
	@TempDir
	Path dir;

	private Path tmp() throws IOException {
		Path tmp = dir.resolve("a.tmp");
		Files.writeString(tmp, "new");
		return tmp;
	}

	@Test
	void realMoveReplacesTheTarget() throws IOException {
		Path target = dir.resolve("a.json");
		Files.writeString(target, "old");
		Path tmp = tmp();
		SafeFile.replace(tmp, target);
		assertEquals("new", Files.readString(target));
		assertFalse(Files.exists(tmp));
	}

	@Test
	void triesAgainWhenTheFileIsBusyAtFirst() throws IOException {
		Path tmp = tmp();
		int[] calls = {0};
		SafeFile.replace(tmp, dir.resolve("a.json"), (from, to, opts) -> {
			if (++calls[0] <= 2) {
				throw new AccessDeniedException(to.toString());
			}
			Files.move(from, to, opts);
		}, 3, 0);
		assertEquals(3, calls[0]);
		assertEquals("new", Files.readString(dir.resolve("a.json")));
	}

	@Test
	void fallsBackToAPlainReplaceWhenAtomicMovesAreNotSupported() throws IOException {
		Path tmp = tmp();
		List<List<CopyOption>> seen = new ArrayList<>();
		SafeFile.replace(tmp, dir.resolve("a.json"), (from, to, opts) -> {
			seen.add(Arrays.asList(opts));
			if (Arrays.asList(opts).contains(StandardCopyOption.ATOMIC_MOVE)) {
				throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "no");
			}
			Files.move(from, to, opts);
		}, 3, 0);
		assertEquals(2, seen.size());
		assertEquals(List.of(StandardCopyOption.REPLACE_EXISTING), seen.get(1));
		assertEquals("new", Files.readString(dir.resolve("a.json")));
	}

	@Test
	void givesUpAndRemovesTheTempFileWhenEveryTryFails() throws IOException {
		Path tmp = tmp();
		int[] calls = {0};
		assertThrows(AccessDeniedException.class, () -> SafeFile.replace(tmp, dir.resolve("a.json"), (from, to, opts) -> {
			calls[0]++;
			throw new AccessDeniedException(to.toString());
		}, 3, 0));
		assertEquals(3, calls[0]);
		assertFalse(Files.exists(tmp));
	}

	@Test
	void givesUpWhenEvenThePlainReplaceFails() throws IOException {
		Path tmp = tmp();
		assertThrows(IOException.class, () -> SafeFile.replace(tmp, dir.resolve("a.json"), (from, to, opts) -> {
			if (Arrays.asList(opts).contains(StandardCopyOption.ATOMIC_MOVE)) {
				throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "no");
			}
			throw new AccessDeniedException(to.toString());
		}, 3, 0));
		assertFalse(Files.exists(tmp));
	}

	@Test
	void totalPausesStayShortForTheGameThread() {
		assertTrue((SafeFile.TRIES - 1) * SafeFile.PAUSE_MS <= 100);
	}
}
