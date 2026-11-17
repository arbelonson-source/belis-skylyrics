package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Changing the computer's clock must not move the song position (pure: nothing here needs Windows). */
class WindowsClockTest {
	private static final long HOUR = 3_600_000L;

	private static JsonObject reply(long positionMs, long updatedMs) {
		return JsonParser.parseString("""
				{"seq":1,"ok":true,"sessions":[{"id":"Spotify.exe","title":"Song","status":"Playing","positionMs":%d,"startMs":0,"endMs":300000,"updatedMs":%d}]}"""
				.formatted(positionMs, updatedMs)).getAsJsonObject();
	}

	/** Polls at {@code steps} on a fake wall and steady clock; the wall clock is moved by {@code jump} after the first poll. */
	private static long[] run(long jump) throws Exception {
		AtomicLong wall = new AtomicLong(1_000_000_000L);
		AtomicLong mono = new AtomicLong(5_000_000_000_000L);
		// the player wrote position 60 s at wall 1_000_000_000 - 1 s, and keeps reporting the same thing
		WindowsMediaSource source = new WindowsMediaSource(() -> "", Path.of("unused"), (script, covers) -> null, 1000, 1000,
				() -> true, System::nanoTime, cmd -> reply(60_000, 1_000_000_000L - 1000));
		source.wallMillis = wall::get;
		source.monoNanos = mono::get;
		long[] out = new long[4];
		for (int i = 0; i < out.length; i++) {
			if (i == 1) {
				wall.addAndGet(jump);
			}
			out[i] = source.poll().positionMs();
			mono.addAndGet(2_000_000_000L); // 2 s pass on the steady clock between polls
			wall.addAndGet(2_000);
		}
		return out;
	}

	@Test
	void clockSetBackAnHourKeepsTheSongMoving() throws Exception {
		assertEquals(java.util.List.of(61_000L, 63_000L, 65_000L, 67_000L), toList(run(-HOUR)));
	}

	@Test
	void clockSetForwardAnHourDoesNotRunToTheEnd() throws Exception {
		assertEquals(java.util.List.of(61_000L, 63_000L, 65_000L, 67_000L), toList(run(HOUR)));
	}

	@Test
	void anUntouchedClockIsUnchanged() throws Exception {
		assertEquals(java.util.List.of(61_000L, 63_000L, 65_000L, 67_000L), toList(run(0)));
	}

	private static java.util.List<Long> toList(long[] a) {
		return java.util.Arrays.stream(a).boxed().toList();
	}
}
