package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class MprisSourceTest {
	private static final String SLOW = "org.mpris.MediaPlayer2.slowapp";
	private static final String GOOD = "org.mpris.MediaPlayer2.mpv";

	private static MprisSource.Reading reading(String bus, String title, long positionMs) {
		return new MprisSource.Reading(bus, new Track(title, "Made Up Band", "", 200_000, "", bus.substring(bus.lastIndexOf('.') + 1)),
				positionMs, true, "/id");
	}

	@Test
	void oneStuckPlayerDoesNotHoldUpTheOthers() throws Exception {
		CountDownLatch unstick = new CountDownLatch(1);
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of(SLOW, GOOD);
			}

			@Override
			public MprisSource.Reading read(String busName) throws Exception {
				if (busName.equals(SLOW)) {
					// Stuck for far longer than any poll may take, until the test is over
					unstick.await(60, TimeUnit.SECONDS);
					return reading(SLOW, "Slow Song", 0);
				}
				return reading(GOOD, "Good Song", 5000);
			}
		});
		try {
			for (int i = 0; i < 3; i++) {
				long start = System.nanoTime();
				Snapshot s = source.poll();
				long tookMs = (System.nanoTime() - start) / 1_000_000;
				// Expected about ANSWER_WAIT_MS; ten times that is still far short of the 60 s the player is stuck for
				assertTrue(tookMs < 10 * MprisSource.ANSWER_WAIT_MS, "poll " + i + " took " + tookMs + " ms");
				assertEquals("Good Song", s.track().title());
			}
		} finally {
			unstick.countDown();
			source.close();
		}
	}

	@Test
	void aFailedReadKeepsTheLastGoodReadingForAMoment() throws Exception {
		AtomicBoolean failing = new AtomicBoolean();
		AtomicReference<List<String>> names = new AtomicReference<>(List.of(GOOD));
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return names.get();
			}

			@Override
			public MprisSource.Reading read(String busName) {
				if (failing.get()) {
					throw new IllegalStateException("busy");
				}
				return reading(GOOD, "Good Song", 5000);
			}
		});
		try {
			assertEquals("Good Song", source.poll().track().title());
			failing.set(true);
			assertEquals("Good Song", source.poll().track().title());
			// a player that left the bus is gone at once
			names.set(List.of());
			assertTrue(source.poll().track().isNone());
		} finally {
			source.close();
		}
	}

	@Test
	void aLateAnswerIsStampedWhenItWasAsked() throws Exception {
		long ms = 1_000_000L;
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of(GOOD);
			}

			@Override
			public MprisSource.Reading read(String busName) throws Exception {
				Thread.sleep(MprisSource.ANSWER_WAIT_MS + 250); // answers after the first poll has given up waiting
				return reading(GOOD, "Good Song", 5000);
			}
		});
		try {
			long asked = System.nanoTime();
			assertTrue(source.poll().track().isNone(), "no answer yet");
			Snapshot s = null;
			for (int i = 0; i < 20 && (s == null || s.track().isNone()); i++) {
				Thread.sleep(100);
				s = source.poll();
			}
			assertEquals("Good Song", s.track().title());
			// The answer was gathered at the first poll, a second or more before it was picked up
			assertTrue(s.readAtNanos() < asked + 200 * ms, "stamped " + (s.readAtNanos() - asked) / ms + " ms after the ask");
		} finally {
			source.close();
		}
	}
}
