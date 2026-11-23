package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An answer from GitHub (the latest release, or the downloaded jar's fabric.mod.json) nested thousands of lists deep. On
 * Minecraft 1.21.1 to 1.21.10, whose Gson reads any depth but writes an element out by recursion, the error text for
 * "this isn't an object" was a StackOverflowError: an Error the update thread doesn't catch, so it died with a long
 * trace in the log. Now such an answer is just no release or no valid jar, like any unreadable one; newer Gson refuses
 * it while reading, which was already a quiet failure.
 */
class UpdateDeepNestingTest {
	private static final String DEEP = "[".repeat(20_000) + "]".repeat(20_000);

	@Test
	void aReleaseAnswerThatIsOneDeepListIsNoRelease() {
		assertNull(UpdateCheck.parse(DEEP));
	}

	@Test
	void aDeeplyNestedDraftFlagIsNoRelease() {
		assertNull(UpdateCheck.parse("{\"tag_name\": \"v9.9.9\", \"draft\": " + "[".repeat(20_000) + "true" + "]".repeat(20_000) + "}"));
	}

	@Test
	void aDeeplyNestedAssetIsSkipped() {
		UpdateCheck.Release r = UpdateCheck.parse("{\"tag_name\": \"v9.9.9\", \"assets\": [" + DEEP
				+ ", {\"name\": \"belis-skylyrics-9.9.9+1.21.11.jar\", \"browser_download_url\": \"https://x/b.jar\"}]}");
		assertTrue(r == null || r.assets().stream().allMatch(a -> a.name().equals("belis-skylyrics-9.9.9+1.21.11.jar")));
	}

	private static Path jar(Path dir, String modJson) throws IOException {
		Path jar = dir.resolve("belis-skylyrics-9.9.9+1.21.11.jar");
		try (OutputStream file = Files.newOutputStream(jar); ZipOutputStream out = new ZipOutputStream(file)) {
			out.putNextEntry(new ZipEntry("fabric.mod.json"));
			out.write(modJson.getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		return jar;
	}

	@Test
	void aJarWhoseModJsonIsOneDeepListIsNotValid(@TempDir Path dir) throws Exception {
		Path jar = jar(dir, DEEP);
		boolean valid;
		try {
			valid = Updater.valid(jar, null, "9.9.9+1.21.11");
		} catch (Exception unreadable) {
			valid = false; // newer Gson: refused while reading, which the update thread catches
		}
		assertFalse(valid);
		try {
			assertNull(Updater.dependsOf(jar));
		} catch (Exception unreadable) {
			// the same
		}
	}
}
