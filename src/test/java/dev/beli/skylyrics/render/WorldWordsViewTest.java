package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * How a flowing line follows what you see: the screen's size and the pitch you look at reach the line through the
 * words' placing, not only through the small helpers. Each test runs several seeds, so it holds for any random layout.
 */
class WorldWordsViewTest {
	private static final int SEEDS = 12;

	private static final class Run {
		WorldWords words;
		int mostPieces;
		float highestStart = Float.NEGATIVE_INFINITY;
		/** How far down each line's lowest word reaches (its pitch plus its half height), as the words landed. */
		final java.util.Map<WorldWords.Sentence, Float> lowest = new java.util.IdentityHashMap<>();
	}

	/** A long flowing line written a piece at a time while looking at {@code pitch}, on a screen {@code halfViewDeg} degrees tall either way (0: none captured). */
	private static Run write(long seed, float pitch, float halfViewDeg, int frames) throws Exception {
		WorldWords.measureHook = t -> t.length() * 4.0;
		WorldWords.buriedHook = pos -> false;
		try {
			if (halfViewDeg > 0) {
				ScreenLayer.capture(new Matrix4f(), new Matrix4f().perspective(2 * (float) Math.toRadians(halfViewDeg), 1.7f, 0.05f, 100f));
			}
			Run run = new Run();
			run.words = new WorldWords();
			run.words.seed(seed);
			run.words.tune(30, 6, 1, 1, 1, true, true, false, 1);
			run.words.players(false, false);
			Camera camera = new Camera();
			StringBuilder text = new StringBuilder();
			for (int w = 0; w < 34; w++) {
				text.append(w == 0 ? "" : " ").append("word").append(w);
			}
			java.lang.reflect.Field sentence = WorldWords.class.getDeclaredField("sentence");
			sentence.setAccessible(true);
			java.lang.reflect.Field cards = WorldWords.class.getDeclaredField("cards");
			cards.setAccessible(true);
			java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
			for (int k = 1; k <= frames; k++) {
				// A fixed yaw with a fixed pitch: the words gather round that pitch whatever the camera does
				run.words.update(0, text.toString(), (k - 1) * 3f, false, 0f, pitch, camera, null, null, net.minecraft.network.chat.Style.EMPTY, k * 100_000_000L);
				for (Object card : (java.util.List<?>) cards.get(run.words)) {
					if (seen.add(card)) {
						WorldWords.Sentence of = (WorldWords.Sentence) field(card, "of");
						Vec3 pos = (Vec3) field(card, "pos");
						float bottom = (float) Math.toDegrees(-Math.asin(pos.y / pos.length())) + WorldWords.halfAngle(5 * (float) field(card, "size"), pos.length());
						if (of != null) {
							run.lowest.merge(of, bottom, Math::max);
						}
					}
				}
				WorldWords.Sentence now = (WorldWords.Sentence) sentence.get(run.words);
				if (now != null) {
					run.mostPieces = Math.max(run.mostPieces, now.pieces);
					if (now.pieces == 1) {
						run.highestStart = Math.max(run.highestStart, now.top);
					}
				}
			}
			return run;
		} finally {
			WorldWords.measureHook = null;
			WorldWords.buriedHook = null;
			var captured = ScreenLayer.class.getDeclaredField("captured");
			captured.setAccessible(true);
			captured.setBoolean(null, false);
		}
	}

	private static Object field(Object o, String name) throws ReflectiveOperationException {
		java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(o);
	}

	@Test
	void aNarrowScreenMakesLinesStartOverHigherThanTheDefaultOneDoes() throws Exception {
		// 18 degrees either way, less the 2 degree margin: 16, where the default screen (35) would allow 33. Judged by
		// the old line's lowest word: a rising row's line starts lower at its left end than any word on it sits
		for (long seed = 1; seed <= SEEDS; seed++) {
			Run run = write(seed, 0, 18, 400);
			assertFalse(run.words.earlierSentences().isEmpty(), "the line started over, seed " + seed);
			for (WorldWords.Sentence old : run.words.earlierSentences()) {
				if (old.rows < 1) {
					continue;
				}
				assertTrue(run.lowest.containsKey(old), "the old line's words were seen landing, seed " + seed);
				float low = run.lowest.get(old);
				assertTrue(low <= 16.5f, "an old line's words reach " + low + ", below 16, seed " + seed);
			}
		}
	}

	@Test
	void lookingUpMovesWhereLinesStartOverUpToo() throws Exception {
		// Looking at -30 on the default screen (35 either way): the bottom is -30 + 35 - 2 = 3
		for (long seed = 1; seed <= SEEDS; seed++) {
			Run run = write(seed, -30, 35, 400);
			assertFalse(run.words.earlierSentences().isEmpty(), "the line started over, seed " + seed);
			for (WorldWords.Sentence old : run.words.earlierSentences()) {
				assertTrue(old.rows < 1 || old.top <= 3.5f, "an old line's rows reach " + old.top + ", below 3, seed " + seed);
			}
		}
	}

	@Test
	void lookingDownALineStartsLowerThanTheHorizonsStartAndKeepsGoing() throws Exception {
		for (long seed = 1; seed <= SEEDS; seed++) {
			Run run = write(seed, 75, 35, 400);
			assertTrue(run.highestStart > WorldWords.Sentence.START_DOWN + 5, "lines start at " + run.highestStart + " looking at 75, seed " + seed);
			assertTrue(run.mostPieces >= 8, "a line looked at from above is not dropped each piece: most pieces " + run.mostPieces + ", seed " + seed);
		}
	}

	@Test
	void whenEvenTheFirstRowIsOffTheScreenTheLineStillStartsInsteadOfLoopingForever() throws Exception {
		// Straight up on a tiny screen: even the highest a line may start is far below the view's bottom
		for (long seed = 1; seed <= 3; seed++) {
			Run run = write(seed, -90, 5, 40);
			assertTrue(run.mostPieces >= 2, "a line is being written and keeps its first row, seed " + seed);
		}
	}

	@Test
	void theBottomOfTheScreenKeepsItsMarginAndTheHotbarsShare() {
		assertEquals(16f, WorldWords.lowestSeen(0, 18), 0.01f);
		assertEquals(3f, WorldWords.lowestSeen(-30, 35), 0.01f);
		// 30 degrees tall either way, the bottom 12% kept clear: a row 25 degrees down shows, 27.5 does not
		assertTrue(WorldWords.rowFits(25, 0, 0, 30));
		assertFalse(WorldWords.rowFits(27.5f, 0, 0, 30));
	}

	@Test
	void theFirstRowNeverMakesALineFullHoweverLowTheScreenEnds() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, -80, 10, 0);
		line.rows = 0;
		assertFalse(line.full(-87), "a line that can't fit even one row stays where it is");
		line.rows = 1;
		assertTrue(line.full(-87));
	}

	@Test
	void lookingDownALineIsLostOnlyWhenTheViewLeavesItsRowsStartingPitchThatAwayFromWhereYouLook() {
		// Looking at 85 the lowest start is 65, not the horizon's 40: a line that started at 72 is only 7 degrees from it
		WorldWords.Sentence line = new WorldWords.Sentence(0, 72, 10, 0);
		line.width = 20;
		float liftedPitch = 85 - 12; // the pitch a line would start from while looking at 85 (the view less the lift)
		assertFalse(line.lost(10, liftedPitch, 0));
		WorldWords.Sentence far = new WorldWords.Sentence(0, 20, 10, 0);
		far.width = 20;
		assertTrue(far.lost(10, liftedPitch, 0), "a line that started far up is lost");
	}
}
