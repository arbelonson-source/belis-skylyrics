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

	private SafeFile() {
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
