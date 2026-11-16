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

	@Test
	void keepBadNeverSkipsABiggerEarlierCopy() throws IOException {
		Path file = dir.resolve("a.json");
		Path bad = dir.resolve("a.json.bad");
		Files.writeString(bad, "a much bigger earlier damaged file");
		Files.writeString(file, "{");
		SafeFile.keepBad(file);
		assertEquals("a much bigger earlier damaged file", Files.readString(bad));
		assertEquals("{", Files.readString(dir.resolve("a.json.bad.1")));
		Files.writeString(file, "{{");
		SafeFile.keepBad(file);
		assertEquals("{{", Files.readString(dir.resolve("a.json.bad.2")));
	}

	@Test
	void keepBadReplacesTheOldestWhenAllTenAreTaken() throws IOException {
		Path file = dir.resolve("a.json");
		for (int i = 0; i <= 9; i++) {
			Path p = dir.resolve(i == 0 ? "a.json.bad" : "a.json.bad." + i);
			Files.writeString(p, "copy " + i);
			Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.fromMillis(1_000_000L - (i == 4 ? 500 : 0) + i));
		}
		Files.writeString(file, "newest");
		SafeFile.keepBad(file);
		assertEquals("newest", Files.readString(dir.resolve("a.json.bad.4")));
		assertEquals("copy 0", Files.readString(dir.resolve("a.json.bad")));
		assertEquals("copy 9", Files.readString(dir.resolve("a.json.bad.9")));
	}

	@Test
	void keepBadTwiceOnTheSameFileLeavesOneCopy() throws IOException {
		Path file = dir.resolve("a.json");
		Files.writeString(file, "{ broken");
		SafeFile.keepBad(file);
		SafeFile.keepBad(file);
		SafeFile.keepBad(file);
		assertEquals("{ broken", Files.readString(dir.resolve("a.json.bad")));
		assertFalse(Files.exists(dir.resolve("a.json.bad.1")));
	}

	@Test
	void keepBadStillCopiesAChangedFileAndSkipsAnyEarlierIdenticalCopy() throws IOException {
		Path file = dir.resolve("a.json");
		Files.writeString(file, "{ one");
		SafeFile.keepBad(file);
		Files.writeString(file, "{ two");
		SafeFile.keepBad(file);
		assertEquals("{ two", Files.readString(dir.resolve("a.json.bad.1")));
		Files.writeString(file, "{ one"); // same as the first copy, which is not the last one
		SafeFile.keepBad(file);
		assertFalse(Files.exists(dir.resolve("a.json.bad.2")));
		Files.writeString(file, "{ 333");
		SafeFile.keepBad(file);
		assertEquals("{ 333", Files.readString(dir.resolve("a.json.bad.2")));
	}
}
