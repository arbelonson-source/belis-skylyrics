package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The update memory (update-refused.json) nested thousands of lists deep, within its 16 KB. On Minecraft 1.21.1 to
 * 1.21.10, whose Gson reads any depth but writes an element out by recursion, the error text for "this isn't an object"
 * (or "isn't a plain value") was a StackOverflowError: an Error the update thread doesn't catch, so the check died with a
 * long trace in the log and the player was never told a new version is out. As for GitHub's own answer
 * ({@link UpdateDeepNestingTest}), such a file now just counts as no memory.
 */
class RefusalDeepNestingTest {
	@TempDir
	Path dir;

	/** {@link Refusal#read} on a thread of its own, as the update check runs: what it gave, or what it threw. */
	private static Object readOnItsOwnThread(Path file) throws InterruptedException {
		AtomicReference<Object> out = new AtomicReference<>();
		Thread t = new Thread(() -> {
			try {
				out.set(java.util.Optional.ofNullable(Refusal.read(file)));
			} catch (Throwable e) {
				out.set(e);
			}
		}, "SkyLyrics-update test");
		t.start();
		t.join();
		return out.get();
	}

	private Path memory(String json) throws Exception {
		assertTrue(json.length() <= 16 * 1024, "within the size the memory is read at: " + json.length());
		Path file = dir.resolve("update-refused.json");
		Files.writeString(file, json);
		return file;
	}

	@Test
	void aMemoryThatIsOneDeepListIsNoMemory() throws Exception {
		Object read = readOnItsOwnThread(memory("[".repeat(8000) + "]".repeat(8000)));
		assertEquals(java.util.Optional.empty(), read, "no memory, not " + read);
	}

	@Test
	void aDeeplyNestedConstraintIsNoMemory() throws Exception {
		String deep = "[".repeat(7700) + "]".repeat(7700);
		String json = "{\"asset\":\"belis-skylyrics-0.2.0+1.21.1.jar\",\"sha256\":\"ab\",\"running\":{\"minecraft\":\"1.21.1\","
				+ "\"fabric-api\":\"0.116.0\",\"fabricloader\":\"0.17.2\",\"java\":\"21\"},\"name\":\"Fabric API\",\"constraints\":["
				+ deep + "]}";
		Object read = readOnItsOwnThread(memory(json));
		assertEquals(java.util.Optional.empty(), read, "no memory, not " + read);
	}

	@Test
	void aMemoryTheModWroteIsStillRead() throws Exception {
		Map<String, String> running = Map.of("minecraft", "1.21.1", "fabric-api", "0.116.0", "fabricloader", "0.17.2", "java", "21");
		Path file = dir.resolve("cache").resolve("update-refused.json");
		new Refusal("belis-skylyrics-0.2.0+1.21.1.jar", "ab", new java.util.LinkedHashMap<>(running), "Fabric API",
				List.of(">=0.140.0", ">=0.141.0")).write(file);
		Refusal read = Refusal.read(file);
		assertEquals("belis-skylyrics-0.2.0+1.21.1.jar", read.asset());
		assertEquals("ab", read.sha256());
		assertEquals(running, read.running());
		assertEquals("Fabric API", read.name());
		assertEquals(List.of(">=0.140.0", ">=0.141.0"), read.constraints());
	}
}
