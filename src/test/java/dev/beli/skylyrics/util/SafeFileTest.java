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
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Assumptions;
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
		// the schedule is 25, 50, 100, 200, 400 ms between six tries; written out here so changing it has to change this too
		assertEquals(6, SafeFile.TRIES);
		assertEquals(25, SafeFile.PAUSE_MS);
		long total = 0;
		for (int i = 0; i < SafeFile.TRIES - 1; i++) {
			total += SafeFile.PAUSE_MS << i;
		}
		assertEquals(775, total);
		assertTrue(total <= 1000, "total pause " + total);
		assertTrue(total >= 500, "long enough for an antivirus scan: " + total);
	}

	/** A scan that holds the file for about half a second must not make the save fail (it gave up after 40 ms before). */
	@Test
	void defaultsRideOutAHoldOfHalfASecond() throws IOException {
		Path tmp = tmp();
		long start = System.nanoTime();
		int[] calls = {0};
		SafeFile.replace(tmp, dir.resolve("a.json"), (from, to, opts) -> {
			if (++calls[0] <= 5) {
				throw new AccessDeniedException(to.toString());
			}
			Files.move(from, to, opts);
		}, SafeFile.TRIES, SafeFile.PAUSE_MS);
		long ms = (System.nanoTime() - start) / 1_000_000;
		assertEquals(6, calls[0]);
		assertTrue(ms >= 600, "the pauses grow 25, 50, 100, 200, 400: " + ms);
		assertEquals("new", Files.readString(dir.resolve("a.json")));
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
			Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.fromMillis(1_000_000_000L - (i == 4 ? 60_000 : 0) + i * 5_000L));
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

	@Test
	void keepBadStillKeepsACopyWhenAnEarlierCopyCantBeRead() throws IOException {
		Assumptions.assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"), "needs file permissions");
		Path file = dir.resolve("a.json");
		Files.writeString(file, "{ same");
		Path locked = dir.resolve("a.json.bad");
		Files.writeString(locked, "{ size"); // same length, so its bytes would have to be compared
		Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
		Assumptions.assumeFalse(Files.isReadable(locked), "running as a user who can read anything");
		try {
			SafeFile.keepBad(file);
			assertEquals("{ same", Files.readString(dir.resolve("a.json.bad.1")));
		} finally {
			Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rw-------"));
		}
	}

	@Test
	void writeDurablyWritesTheExactBytesOverWhatWasThere() throws IOException {
		Path tmp = dir.resolve("d.tmp");
		Files.writeString(tmp, "a much longer old text than the new one");
		SafeFile.writeDurably(tmp, "h\u00e9llo \u4e16\u754c");
		assertEquals("h\u00e9llo \u4e16\u754c", Files.readString(tmp));
		assertEquals("h\u00e9llo \u4e16\u754c".getBytes(java.nio.charset.StandardCharsets.UTF_8).length, Files.size(tmp));
	}

	/** The tests' way round the forced write (a slow disk) still writes the same bytes, so what those tests read back is real. */
	@Test
	void writeDurablyWithoutForcingWritesTheSameBytes() throws IOException {
		Path tmp = dir.resolve("d.tmp");
		Files.writeString(tmp, "a much longer old text than the new one");
		SafeFile.writeDurably(tmp, "héllo 世界", false);
		assertEquals("héllo 世界", Files.readString(tmp));
		assertEquals("héllo 世界".getBytes(java.nio.charset.StandardCharsets.UTF_8).length, Files.size(tmp));
	}

	@Test
	void writeDurablyThenReplaceLeavesOnlyTheTarget() throws IOException {
		Path target = dir.resolve("t.json");
		Files.writeString(target, "old");
		Path tmp = dir.resolve("t.json.tmp");
		Files.createFile(tmp);
		SafeFile.writeDurably(tmp, "new");
		SafeFile.replace(tmp, target);
		assertEquals("new", Files.readString(target));
		assertEquals(List.of("t.json"), Arrays.stream(dir.toFile().list()).sorted().toList());
	}

	@Test
	void writeDurablyThatFailsThrowsAndCreatesNothing() {
		Path missing = dir.resolve("nope.tmp");
		assertThrows(IOException.class, () -> SafeFile.writeDurably(missing, "x"));
		assertFalse(Files.exists(missing));
	}
}
