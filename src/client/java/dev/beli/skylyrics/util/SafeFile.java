package dev.beli.skylyrics.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Moves a finished temp file over its real file. On Windows, antivirus, the search indexer or
 * OneDrive often hold the target open for a moment, which makes the move fail; trying again a
 * few times a few milliseconds later nearly always works. Some file systems (network drives,
 * FAT/exFAT sticks) can't do atomic moves at all, so those get a plain replace instead.
 */
public final class SafeFile {
	/** Total tries, including the first. */
	public static final int TRIES = 3;
	/** Pause between tries; the worst case is (TRIES - 1) pauses, about 40 ms. */
	public static final long PAUSE_MS = 20;

	/** The move itself, so tests can make it fail. */
	@FunctionalInterface
	public interface Mover {
		void move(Path from, Path to, CopyOption... options) throws IOException;
	}

	/** Reads a file's text; a seam so tests can make the read fail. */
	@FunctionalInterface
	public interface Reader {
		String read(Path file) throws IOException;
	}

	/** Tries when reading a file at start-up, and the pause between them (a lock often lets go within a moment). */
	public static final int READ_TRIES = 3;
	public static final long READ_PAUSE_MS = 150;

	private SafeFile() {
	}

	/** Reads a text file, trying again a few times when it can't be read (a lock on Windows). Throws the last error; a file that isn't valid UTF-8 or is missing throws at once. */
	public static String readText(Path file) throws IOException {
		return readText(file, f -> Files.readString(f, java.nio.charset.StandardCharsets.UTF_8), READ_TRIES, READ_PAUSE_MS);
	}

	public static String readText(Path file, Reader reader, int tries, long pauseMs) throws IOException {
		IOException last = null;
		for (int i = 0; i < tries; i++) {
			try {
				return reader.read(file);
			} catch (java.nio.charset.CharacterCodingException | java.nio.file.NoSuchFileException e) {
				throw e; // not a lock: the file is damaged (not UTF-8) or gone, trying again changes nothing
			} catch (IOException e) {
				last = e;
				if (i + 1 < tries && pauseMs > 0) {
					try {
						Thread.sleep(pauseMs);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}
		throw last != null ? last : new IOException("Couldn't read " + file);
	}

	/** The first free {@code <name>.bad}, {@code .bad.1} to {@code .bad.9}, or null when all ten are taken. */
	private static Path freeBadName(Path file) {
		Path target = file.resolveSibling(file.getFileName() + ".bad");
		for (int i = 1; Files.exists(target) && i <= 9; i++) {
			target = file.resolveSibling(file.getFileName() + ".bad." + i);
		}
		return Files.exists(target) ? null : target;
	}

	/**
	 * Moves a file nobody can read aside, to {@code <name>.bad} or, when that exists, the first free {@code <name>.bad.1} to
	 * {@code .bad.9}, so no earlier copy is destroyed. Returns where it went; throws when it can't be moved.
	 */
	public static Path moveAside(Path file) throws IOException {
		Path target = freeBadName(file);
		if (target == null) {
			throw new IOException("No free name to move " + file + " aside");
		}
		Files.move(file, target);
		return target;
	}

	/**
	 * Copies a damaged file to {@code <name>.bad} so a reset never costs the original. An earlier copy is never
	 * overwritten: the new one goes to the first free {@code .bad.1} to {@code .bad.9}, and when all ten are taken it
	 * replaces the oldest one (by modified time).
	 */
	public static void keepBad(Path file) throws IOException {
		Path target = freeBadName(file);
		if (target == null) {
			Path oldest = null;
			long oldestTime = Long.MAX_VALUE;
			for (int i = 0; i <= 9; i++) {
				Path p = file.resolveSibling(file.getFileName() + (i == 0 ? ".bad" : ".bad." + i));
				long time = Files.getLastModifiedTime(p).toMillis();
				if (time < oldestTime) {
					oldest = p;
					oldestTime = time;
				}
			}
			target = oldest;
		}
		Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
	}

	/** Replaces target with tmp. If it can't be done, tmp is deleted and the last error is thrown. */
	public static void replace(Path tmp, Path target) throws IOException {
		replace(tmp, target, Files::move, TRIES, PAUSE_MS);
	}

	public static void replace(Path tmp, Path target, Mover mover, int tries, long pauseMs) throws IOException {
		boolean atomic = true;
		IOException last = null;
		int failures = 0;
		while (failures < tries) {
			try {
				if (atomic) {
					mover.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} else {
					mover.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				}
				return;
			} catch (AtomicMoveNotSupportedException e) {
				last = e;
				if (!atomic) {
					failures++;
				}
				atomic = false; // this file system can't; don't spend a try on finding that out
			} catch (IOException e) {
				last = e;
				failures++;
				if (failures < tries && pauseMs > 0) {
					try {
						Thread.sleep(pauseMs);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}
		try {
			Files.deleteIfExists(tmp);
		} catch (IOException ignored) {
			// nothing more to do; the original error is the one worth reporting
		}
		throw last != null ? last : new IOException("Couldn't replace " + target);
	}
}
