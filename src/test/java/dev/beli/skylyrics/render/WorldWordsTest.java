package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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
		assertEquals(List.of("NEON", "SKYLINE,", "CAN YOU", "HEAR", "IT"), pieces("NEON SKYLINE, CAN YOU HEAR IT"));
		assertEquals(List.of("UP WHERE", "THE CLOUDS", "ARE SLOWLY", "DRIFTING"),
				pieces("UP WHERE THE CLOUDS ARE SLOWLY DRIFTING"));
		assertEquals(List.of("SING", "IT BACK", "TO ME", "FROM", "WHERE", "YOU ARE"),
				pieces("SING IT BACK TO ME FROM WHERE YOU ARE"));
	}

	@Test
	void neverMoreThanTwoWords() {
		assertEquals(List.of("HOLD", "THE BEAT", "AND FOLLOW", "ME"), pieces("Hold the beat and follow me").stream()
				.map(s -> s.toUpperCase(java.util.Locale.ROOT)).toList());
		assertEquals(List.of("STARS"), pieces("  stars ").stream().map(s -> s.toUpperCase(java.util.Locale.ROOT)).toList());
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
	void theSungWordsGlowMostAndLetGoWordsStopGlowing() {
		assertTrue(WorldWords.glowFor(true, -1) > WorldWords.glowFor(false, -1), "the words being sung shine brightest");
		assertEquals(WorldWords.glowFor(false, -1), WorldWords.glowFor(false, 0), 1e-6, "no jump when a lit card is let go");
		float last = 2;
		for (float s = 0; s <= WorldWords.GLOW_OUT; s += 0.05f) {
			float g = WorldWords.glowFor(false, s);
			assertTrue(g <= last, "goes out steadily");
			last = g;
		}
		assertEquals(0, WorldWords.glowFor(false, WorldWords.GLOW_OUT), 1e-6);
		assertTrue(WorldWords.GLOW_OUT < WorldWords.FADE, "the glow is gone well before the words");
	}

	@Test
	void lingerSettingStretchesTheFade() {
		assertEquals(0.75f, WorldWords.goneAlpha(2.5f, 5f), 1e-6);
		assertEquals(0, WorldWords.goneAlpha(5f, 5f), 1e-6);
		assertTrue(WorldWords.goneAlpha(1f, 5f) > WorldWords.goneAlpha(1f, 1f), "a longer linger fades slower");
	}

	@Test
	void onlyCardsThatCouldBeOnScreenAreDrawn() {
		Vec3 ahead = new Vec3(0, 0, -1);
		// Off to the side at 65°: past the edge of a normal view, so skipped
		ScreenLayer.capture(new Matrix4f(), new Matrix4f().perspective((float) Math.toRadians(70), 16 / 9f, 0.05f, 1000));
		Vector3f side = new Vector3f((float) Math.sin(Math.toRadians(65)) * 10, 0, (float) -Math.cos(Math.toRadians(65)) * 10);
		assertFalse(WorldWords.showing(side, 1, 0.06f, ahead));
		assertTrue(WorldWords.showing(new Vector3f(2, 1, -10), 1, 0.06f, ahead), "one in the middle shows");
		// At 78° a wide card still reaches into a very wide view, so it isn't cut off at the edge
		ScreenLayer.capture(new Matrix4f(), new Matrix4f().perspective((float) Math.toRadians(110), 16 / 9f, 0.05f, 1000));
		Vector3f wide = new Vector3f((float) Math.sin(Math.toRadians(78)) * 10, 0, (float) -Math.cos(Math.toRadians(78)) * 10);
		assertTrue(WorldWords.showing(wide, 3, 0.06f, ahead));
		assertFalse(WorldWords.showing(new Vector3f(0, 0, 10), 3, 0.06f, ahead), "one behind you never shows");
	}

	@Test
	void fartherWordsGrowSoTheyReadTheSameSize() {
		// The Distance setting pushes a card out and makes it as much bigger, so it takes up the same bit of the view
		for (float away : new float[] {0.5f, 1.5f, 2f}) {
			assertEquals(WorldWords.halfAngle(40 * 0.06f, 10), WorldWords.halfAngle(40 * 0.06f * away, 10 * away), 1e-4);
		}
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
		float[] follow = WorldWords.centre(Float.NaN, 37, -20, false);
		assertEquals(37, follow[0], 1e-6f);
		assertEquals(-20, follow[1], 1e-6f);
		// A fixed direction ignores where you look: same spot whichever way you turn
		float[] east = WorldWords.centre(-90, 37, -20, false);
		assertEquals(-90, east[0], 1e-6f);
		assertEquals(0, east[1], 1e-6f);
		assertEquals(east[0], WorldWords.centre(-90, 150, 60, false)[0], 1e-6f);
		// Flying with Elytra mode, new words come where you look even with a fixed direction, not off to the side
		float[] flying = WorldWords.centre(-90, 150, 30, true);
		assertEquals(150, flying[0], 1e-6f);
		assertEquals(30, flying[1], 1e-6f);
	}

	@Test
	void wordsFlyAlongOnlyWhileYouGlide() {
		Vec3 last = new Vec3(10, 80, 10), eye = new Vec3(12, 79, 10.5);
		Vec3 moved = WorldWords.carry(last, eye, true);
		assertEquals(2, moved.x, 1e-9);
		assertEquals(-1, moved.y, 1e-9);
		assertEquals(0.5, moved.z, 1e-9);
		// Walking, the first frame, and a teleport leave the words where they are
		assertEquals(Vec3.ZERO, WorldWords.carry(last, eye, false));
		assertEquals(Vec3.ZERO, WorldWords.carry(null, eye, true));
		assertEquals(Vec3.ZERO, WorldWords.carry(last, new Vec3(500, 80, 10), true));
	}

	@Test
	void steeringTurnsTheWordsWithYou() {
		// A word 10 blocks ahead stays 10 blocks ahead of a quarter turn
		Vec3 turned = WorldWords.swing(new Vec3(0, 0, 10), 90, 0);
		assertEquals(-10, turned.x, 1e-3);
		assertEquals(0, turned.y, 1e-3);
		assertEquals(0, turned.z, 1e-3);
		// Looking down brings it down with you, but never over the top
		Vec3 lower = WorldWords.swing(new Vec3(0, 0, 10), 0, 30);
		assertEquals(-5, lower.y, 1e-3);
		assertEquals(10, lower.length(), 1e-3);
		assertTrue(WorldWords.swing(new Vec3(0, 0, 10), 0, -200).y < 10);
		// The rest of a flowing line turns too
		WorldWords.Sentence line = new WorldWords.Sentence(-10, 5, 10, 0);
		line.swing(20, 3);
		assertEquals(10, line.left, 1e-6f);
		assertEquals(8, line.top, 1e-6f);
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
