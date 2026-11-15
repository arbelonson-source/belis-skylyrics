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
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicLong readerEntered = new java.util.concurrent.atomic.AtomicLong();
		MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public List<String> names() {
				return List.of(GOOD);
			}

			@Override
			public MprisSource.Reading read(String busName) throws Exception {
				readerEntered.set(System.nanoTime());
				entered.countDown();
				// Answers only once the test lets it, i.e. after the first poll has given up waiting
				release.await();
				return reading(GOOD, "Good Song", 5000);
			}
		});
		try {
			long asked = System.nanoTime();
			assertTrue(source.poll().track().isNone(), "no answer yet");
			assertTrue(entered.await(20, java.util.concurrent.TimeUnit.SECONDS), "the reader never started");
			release.countDown();
			long released = System.nanoTime();
			Snapshot s = null;
			long giveUp = System.nanoTime() + 20_000L * 1_000_000L;
			while ((s == null || s.track().isNone()) && System.nanoTime() < giveUp) {
				Thread.sleep(20);
				s = source.poll();
			}
			assertEquals("Good Song", s.track().title());
			// The answer was gathered when the read asked, before the first poll gave up, not when it was picked up
			assertTrue(readerEntered.get() != 0);
			assertTrue(s.readAtNanos() >= asked, "not before the poll");
			assertTrue(s.readAtNanos() <= readerEntered.get(), "stamped when asked, not when picked up");
			assertTrue(s.readAtNanos() < released, "stamped before the answer was let go");
		} finally {
			release.countDown();
			source.close();
		}
	}
}
