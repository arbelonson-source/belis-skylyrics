package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class WorldWordsTest {
	private static List<String> pieces(String line) {
		int[] cps = line.codePoints().toArray();
		List<String> out = new ArrayList<>();
		for (int[] c : WorldWords.chunks(line)) {
			out.add(new String(cps, c[0], c[1] - c[0]));
		}
		return out;
	}

	@Test
	void shortWordsLeanOnTheNextOne() {
		assertEquals(List.of("STARS,", "I'M OUT", "TONIGHT"), pieces("STARS, I'M OUT TONIGHT"));
		assertEquals(List.of("THAT,", "YOU WANNA", "GET OUT"), pieces("THAT, YOU WANNA GET OUT"));
		assertEquals(List.of("THAT", "YOU NEED", "IT RIGHT", "NOW"), pieces("THAT YOU NEED IT RIGHT NOW"));
	}

	@Test
	void neverMoreThanTwoWords() {
		assertEquals(List.of("I AM", "SO ON", "MY WAY"), pieces("I am so on my way").stream()
				.map(s -> s.toUpperCase(java.util.Locale.ROOT)).toList());
		assertEquals(List.of("ALONE"), pieces("  alone ").stream().map(s -> s.toUpperCase(java.util.Locale.ROOT)).toList());
		assertTrue(WorldWords.chunks("   ").isEmpty());
	}

	@Test
	void chunksPointAtTheirLetters() {
		List<int[]> c = WorldWords.chunks("GO HOME NOW");
		assertEquals(0, c.get(0)[0], "starts with the first letter");
		assertEquals(7, c.get(0)[1]);
		assertEquals(8, c.get(1)[0]);
	}

	@Test
	void sungCardsFadeOutSlowlyThenQuickly() {
		assertEquals(1, WorldWords.goneAlpha(0));
		assertEquals(0.75f, WorldWords.goneAlpha(WorldWords.FADE / 2), 1e-6);
		assertEquals(0, WorldWords.goneAlpha(WorldWords.FADE), 1e-6);
		assertEquals(0, WorldWords.goneAlpha(10), 1e-6);
		float last = 2;
		for (float s = 0; s <= WorldWords.FADE; s += 0.05f) {
			float a = WorldWords.goneAlpha(s);
			assertTrue(a <= last, "fades steadily");
			last = a;
		}
	}

	@Test
	void lingerSettingStretchesTheFade() {
		assertEquals(0.75f, WorldWords.goneAlpha(2.5f, 5f), 1e-6);
		assertEquals(0, WorldWords.goneAlpha(5f, 5f), 1e-6);
		assertTrue(WorldWords.goneAlpha(1f, 5f) > WorldWords.goneAlpha(1f, 1f), "a longer linger fades slower");
	}

	@Test
	void longWordsAreBiggerThanLittleOnes() {
		assertTrue(WorldWords.sizeFor(9, 0) > WorldWords.sizeFor(2, 1), "a long word beats a little one even with bad luck");
		assertEquals(WorldWords.sizeFor(1, 0.5f), WorldWords.sizeFor(3, 0.5f), 1e-6);
		assertEquals(WorldWords.sizeFor(9, 0.5f), WorldWords.sizeFor(20, 0.5f), 1e-6);
		for (int n = 1; n < 12; n++) {
			assertTrue(WorldWords.sizeFor(n + 1, 0.5f) >= WorldWords.sizeFor(n, 0.5f), "grows with the letters");
		}
	}

	@Test
	void cardsCutShortVanishQuickly() {
		assertEquals(1, WorldWords.cutAlpha(0));
		assertTrue(WorldWords.cutAlpha(0.1f) < 1);
		assertEquals(0, WorldWords.cutAlpha(1), 1e-6);
	}

	@Test
	void newCardsPopThenSettle() {
		assertEquals(1 + WorldWords.POP, WorldWords.pop(0), 1e-6);
		assertEquals(1, WorldWords.pop(WorldWords.POP_TIME), 1e-6);
		assertEquals(1, WorldWords.pop(5), 1e-6);
		assertTrue(WorldWords.pop(WorldWords.POP_TIME / 2) < 1 + WorldWords.POP / 2, "settles fast, then eases in");
	}

	@Test
	void scatterSpotsAreSpreadOut() {
		float[][] s = WorldWords.slots();
		assertEquals(WorldWords.SLOTS, s.length);
		for (int i = 0; i < s.length; i++) {
			assertTrue(Math.abs(s[i][0]) <= 19 && Math.abs(s[i][1]) <= 11, "inside the oval");
			for (int j = i + 1; j < s.length; j++) {
				float dx = s[i][0] - s[j][0], dy = s[i][1] - s[j][1];
				assertTrue(dx * dx + dy * dy > 4 * 4, "spots " + i + " and " + j + " are apart");
			}
			if (i > 0) {
				float dx = s[i][0] - s[i - 1][0], dy = s[i][1] - s[i - 1][1];
				assertTrue(dx * dx + dy * dy > 8 * 8, "one spot to the next jumps across");
			}
		}
	}

	@Test
	void cardsReadTheRightWayRound() {
		for (float yaw = -180; yaw < 180; yaw += 15) {
			float rad = yaw * (float) Math.PI / 180;
			// Straight ahead of a camera looking along yaw, turned as spawned (towards that yaw, give or take 30 degrees)
			float x = -(float) Math.sin(rad) * 10, z = (float) Math.cos(rad) * 10;
			assertFalse(WorldWords.seenFromBehind(x, z, yaw), "card ahead at yaw " + yaw);
			assertFalse(WorldWords.seenFromBehind(x, z, yaw + 30), "turned card ahead at yaw " + yaw);
			// Walk past it and look back: now its back faces you
			assertTrue(WorldWords.seenFromBehind(-x, -z, yaw), "card behind at yaw " + yaw);
		}
	}

	@Test
	void shadowIsDarkerByDay() {
		assertTrue(WorldWords.shadowOpacity(1) > WorldWords.shadowOpacity(0));
		assertEquals(WorldWords.shadowOpacity(1), WorldWords.shadowOpacity(3), 1e-6f);
		assertTrue(WorldWords.shadowOpacity(1) <= 1);
	}

	@Test
	void wordsGatherWhereYouLookOrAtTheChosenDirection() {
		float[] follow = WorldWords.centre(Float.NaN, 37, -20);
		assertEquals(37, follow[0], 1e-6f);
		assertEquals(-20, follow[1], 1e-6f);
		// A fixed direction ignores where you look: same spot whichever way you turn
		float[] east = WorldWords.centre(-90, 37, -20);
		assertEquals(-90, east[0], 1e-6f);
		assertEquals(0, east[1], 1e-6f);
		assertEquals(east[0], WorldWords.centre(-90, 150, 60)[0], 1e-6f);
	}

	@Test
	void aFlowingLineReadsLeftToRight() {
		WorldWords.Sentence line = new WorldWords.Sentence(-10, 5, 10, 0);
		float[] first = line.place(3, 1, 0, 0), second = line.place(2, 1, 0, 0);
		assertEquals(-10 + WorldWords.Sentence.GAP + 3, first[0], 1e-4);
		assertEquals(first[0] + 3 + WorldWords.Sentence.GAP + 2, second[0], 1e-4);
		assertEquals(5, first[1], 1e-4);
		assertEquals(5, second[1], 1e-4);
	}

	@Test
	void aFullRowCarriesOnBelowFromTheLeft() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0);
		float[] last = line.place(5, 1, 0, 0);
		while (last[1] == 0) {
			last = line.place(5, 1, 1, 0);
		}
		assertEquals(2 + WorldWords.Sentence.ROW_GAP, last[1], 1e-4);
		assertEquals(1 + 5, last[0], 1e-4);
		assertTrue(line.place(5, 1, 0, 0)[0] > last[0]);
	}

	@Test
	void aPieceWiderThanARowStillGetsOneToItself() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0);
		float[] wide = line.place(WorldWords.Sentence.WIDTH, 1, 0, 0);
		assertEquals(0, wide[1], 1e-4);
		assertTrue(line.place(1, 1, 0, 0)[1] > 0);
	}
}
