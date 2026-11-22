package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/**
 * A player's speed that makes no sense (0, below 0, not a number) is taken as normal speed, as the song clock already
 * does: a reading that is carried on while the player is slow to answer must move the same way.
 */
class MprisSourceRateTest {
	private static final String BUS = "org.mpris.MediaPlayer2.someplayer";

	private static MprisSource.Reading reading(long positionMs, double rate) {
		return new MprisSource.Reading(BUS, new Track("Made Up Song", "Made Up Band", "", 200_000, "", "someplayer"),
				positionMs, true, "/id", rate);
	}

	@Test
	void aSpeedThatMakesNoSenseIsNormalSpeed() {
		for (double bad : new double[] {0, -0.0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
			MprisSource.Reading r = reading(5000, bad);
			assertEquals(1.0, r.rate(), "rate " + bad);
			assertEquals(6000, r.advancedBy(1000).positionMs(), "rate " + bad);
		}
		assertEquals(1.5, reading(5000, 1.5).rate());
		assertEquals(6500, reading(5000, 1.5).advancedBy(1000).positionMs());
	}

	@Test
	void aCarriedOnReadingMovesAsTheSongClockSays() throws Exception {
		for (double bad : new double[] {0, -1}) {
			AtomicBoolean busy = new AtomicBoolean();
			MprisSource source = new MprisSource(() -> "", new MprisSource.Reader() {
				@Override
				public List<String> names() {
					return List.of(BUS);
				}

				@Override
				public MprisSource.Reading read(String busName) {
					if (busy.get()) {
						throw new IllegalStateException("busy");
					}
					return reading(5000, bad);
				}
			});
			try {
				Snapshot first = source.poll();
				long giveUp = System.nanoTime() + 20_000_000_000L;
				while (first.track().isNone() && System.nanoTime() < giveUp) {
					Thread.sleep(20);
					first = source.poll();
				}
				assertEquals("Made Up Song", first.track().title());
				busy.set(true);
				Thread.sleep(700);
				Snapshot carried = source.poll();
				assertEquals("Made Up Song", carried.track().title(), "kept while the player doesn't answer");
				// Where the first reading's clock (at its rate) says the song is at the carried reading's time
				long expected = first.positionMs() + (long) ((carried.readAtNanos() - first.readAtNanos()) / 1e6 * first.rate());
				assertTrue(Math.abs(carried.positionMs() - expected) < 100,
						"rate " + bad + ": carried on to " + carried.positionMs() + " ms, the clock says " + expected + " ms");
			} finally {
				source.close();
			}
		}
	}
}
