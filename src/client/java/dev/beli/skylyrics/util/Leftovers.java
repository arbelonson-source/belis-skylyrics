package dev.beli.skylyrics.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Cleans up the unique temp files a killed game left behind (Files.createTempFile names: base, a number, suffix). */
public final class Leftovers {
	public static final long AGE_MS = 24L * 60 * 60 * 1000;

	private Leftovers() {
	}

	/**
	 * Deletes files in the folder named exactly {@code <base>.<digits>.tmp} that are over a day old. A fresh one may be
	 * another game's save in progress, and nothing else is touched. Returns how many were deleted; never throws.
	 */
	public static int cleanTemps(Path dir, String base, long nowMs) {
		Pattern name = Pattern.compile(Pattern.quote(base) + "\\.\\d+\\.tmp");
		int deleted = 0;
		try (var files = Files.newDirectoryStream(dir)) {
			for (Path f : files) {
				if (!name.matcher(f.getFileName().toString()).matches()) {
					continue;
				}
				try {
					if (Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS) && nowMs - Files.getLastModifiedTime(f).toMillis() > AGE_MS
							&& Files.deleteIfExists(f)) {
						deleted++;
					}
				} catch (IOException | RuntimeException e) {
					// in use or not ours to remove: next start
				}
			}
		} catch (IOException | RuntimeException e) {
			// no folder yet
		}
		return deleted;
	}
}
