package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
	void sungCardsEaseOut() {
		assertEquals(1, WorldWords.goneAlpha(0));
		assertTrue(WorldWords.goneAlpha(WorldWords.FADE / 8) > 0.9f, "barely dims at first");
		assertEquals(0.5f, WorldWords.goneAlpha(WorldWords.FADE / 2), 1e-6);
		assertTrue(WorldWords.goneAlpha(WorldWords.FADE * 0.95f) < 0.01f, "slips away softly, no blink at the end");
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
	void lightEffectsKeepTheGlowOnlyOnTheWordsBeingSung() {
		assertEquals(WorldWords.glowFor(true, -1), WorldWords.glowFor(true, -1, -1, true), 1e-6, "the sung word glows as before");
		assertEquals(WorldWords.glowFor(false, -1), WorldWords.glowFor(false, -1, 0, true), 1e-6, "no jump when the next word comes");
		assertEquals(0, WorldWords.glowFor(false, -1, WorldWords.GLOW_OUT, true), 1e-6, "then it goes out");
		assertEquals(WorldWords.glowFor(false, -1), WorldWords.glowFor(false, -1, 5, false), 1e-6, "full effects keep the line lit");
		assertEquals(0, WorldWords.glowFor(false, WorldWords.GLOW_OUT, 0, true), 1e-6, "a let-go word stays dark");
	}

	@Test
	void floatUpSettingScalesHowFarWordsDrift() {
		assertEquals(0, WorldWords.riseAfter(0, 1), 1e-6, "a new word starts where it was put");
		assertEquals(2 * WorldWords.riseAfter(1, 1), WorldWords.riseAfter(1, 2), 1e-6, "twice the setting, twice as far");
		assertEquals(0, WorldWords.riseAfter(30, 0), 1e-6, "at 0 words stay still");
		assertEquals(WorldWords.riseAfter(30, 1), WorldWords.riseAfter(60, 1), 1e-6, "they stop rising after a while");
	}

	@Test
	void cardsSwayOnTheirOwnTime() {
		long born = 123_456_789_000L;
		float start = WorldWords.swayAngle(0, born);
		assertEquals(Math.sin(start), Math.sin(WorldWords.swayAngle(5.5f, born)), 1e-4, "one sway takes 5.5 seconds");
		assertNotEquals(start, WorldWords.swayAngle(0, born + (300L << 16)), 1e-3, "cards that came at different times aren't in step");
	}

	@Test
	void letGoCardsShrinkALittleAsTheyFade() {
		assertEquals(1, WorldWords.leaveSize(0, 2.2f), 1e-6, "full size when let go");
		assertEquals(0.85f, WorldWords.leaveSize(2.2f, 2.2f), 1e-6, "a bit smaller once faded");
		assertEquals(0.85f, WorldWords.leaveSize(9f, 2.2f), 1e-6, "and no smaller after that");
	}

	@Test
	void lingerSettingStretchesTheFade() {
		assertEquals(0.5f, WorldWords.goneAlpha(2.5f, 5f), 1e-6);
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
		Vec3 moved = WorldWords.carry(last, eye, true, 0.016f);
		assertEquals(2, moved.x, 1e-9);
		assertEquals(-1, moved.y, 1e-9);
		assertEquals(0.5, moved.z, 1e-9);
		// Walking, the first frame, and a teleport leave the words where they are
		assertEquals(Vec3.ZERO, WorldWords.carry(last, eye, false, 0.016f));
		assertEquals(Vec3.ZERO, WorldWords.carry(null, eye, true, 0.016f));
		assertEquals(Vec3.ZERO, WorldWords.carry(last, new Vec3(500, 80, 10), true, 0.016f));
		// A lag spike mid-flight still carries them, however far a boosted flight went meanwhile
		Vec3 boosted = new Vec3(10 + 30, 80, 10);
		assertEquals(30, WorldWords.carry(last, boosted, true, 0.5f).x, 1e-9);
		assertEquals(Vec3.ZERO, WorldWords.carry(last, boosted, true, 0.05f));
		assertEquals(Vec3.ZERO, WorldWords.carry(last, new Vec3(500, 80, 10), true, 5f));
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
		float[] first = line.place(3, 1, 0, 0), second = line.place(2, 0.5f, 0, 0);
		assertEquals(-10 + WorldWords.Sentence.GAP + 3, first[0], 1e-4);
		assertEquals(first[0] + 3 + WorldWords.Sentence.GAP * 0.5f + 2, second[0], 1e-4);
		// A big word and a small one sit on the same line
		assertEquals(5, first[1] + 1, 1e-4);
		assertEquals(5, second[1] + 0.5f, 1e-4);
	}

	@Test
	void aNewWordLandsBrightThenSettlesIntoItsColour() {
		assertEquals(WorldWords.FLASH, WorldWords.flash(0), 1e-6);
		assertTrue(WorldWords.flash(WorldWords.FLASH_TIME / 2) < WorldWords.FLASH / 2);
		assertEquals(0, WorldWords.flash(WorldWords.FLASH_TIME), 1e-6);
		assertEquals(0, WorldWords.flash(10), 1e-6);
	}

	@Test
	void aFlowingWordGlidesIntoItsPlaceAndStops() {
		assertEquals(WorldWords.SLIDE, WorldWords.slideIn(0), 1e-5);
		assertTrue(WorldWords.slideIn(WorldWords.SLIDE_TIME / 2) < WorldWords.SLIDE / 2, "most of the way there by half time");
		assertTrue(WorldWords.slideIn(WorldWords.SLIDE_TIME / 2) > 0);
		assertEquals(0, WorldWords.slideIn(WorldWords.SLIDE_TIME), 1e-6);
		assertEquals(0, WorldWords.slideIn(5), 1e-6);
	}

	@Test
	void aSlopedLineRisesToTheRightRowAfterRow() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0, 5);
		float[] first = line.place(2, 1, 0, 0), second = line.place(2, 1, 0, 0);
		assertTrue(second[1] < first[1]);
		assertEquals((second[0] - first[0]) * Math.tan(Math.toRadians(5)), first[1] - second[1], 1e-4);
		float[] last = second;
		while (last[0] > first[0]) {
			last = line.place(2, 1, 0, 0);
		}
		// The next row starts from the left again, a row lower, on a line just as steep as the first
		assertEquals(2 + WorldWords.Sentence.ROW_GAP - 1 - last[0] * Math.tan(Math.toRadians(5)), last[1], 1e-4);
	}

	@Test
	void aRightToLeftLineIsWrittenFromTheRight() {
		assertTrue(WorldWords.rightToLeft("\u05e9\u05dc\u05d5\u05dd \u05e2\u05d5\u05dc\u05dd"));
		assertTrue(WorldWords.rightToLeft("\u05e9\u05dc\u05d5\u05dd OK"));
		assertFalse(WorldWords.rightToLeft("Neon skyline"));
		assertFalse(WorldWords.rightToLeft("OK \u05e9\u05dc\u05d5\u05dd"));
		WorldWords.Sentence ltr = new WorldWords.Sentence(-10, 5, 10, 0, 0, false), rtl = new WorldWords.Sentence(-10, 5, 10, 0, 0, true);
		float[] a = ltr.place(3, 1, 0, 0), b = rtl.place(3, 1, 0, 0);
		// Mirrored across the row: the first word at its right end, the next one left of it
		assertEquals(-10 + WorldWords.Sentence.WIDTH / 2 - a[0], b[0] - (-10 + WorldWords.Sentence.WIDTH / 2), 1e-4);
		assertEquals(a[1], b[1], 1e-4);
		assertTrue(rtl.place(2, 1, 0, 0)[0] < b[0]);
		// A full row carries on below, again from the right
		float[] last = b;
		while (last[1] == b[1]) {
			last = rtl.place(5, 1, 0, 0);
		}
		assertEquals(-10 + WorldWords.Sentence.WIDTH - 5, last[0], 1e-4);
	}

	@Test
	void overlappingWordsArePushedTheShortWay() {
		float[] a = {0, 0, 4, 1};
		assertArrayEquals(new float[] {0, 0}, WorldWords.push(a, new float[] {10, 0, 4, 1}));
		// Wide words side by side on one row: stepping down a row is shorter than going round
		float[] down = WorldWords.push(a, new float[] {1, -0.5f, 4, 1});
		assertEquals(0, down[0]);
		assertEquals(-(1.5f + WorldWords.SLIDE_ROOM), down[1], 1e-4f);
		// Tall thin words: sideways is shorter
		float[] side = WorldWords.push(new float[] {0, 0, 1, 4}, new float[] {-1.5f, 0, 1, 4});
		assertEquals(-(0.5f + WorldWords.SLIDE_ROOM), side[0], 1e-4f);
		assertEquals(0, side[1]);
		// Across the 180 degree seam
		float[] seam = WorldWords.push(new float[] {179, 0, 1, 4}, new float[] {-179.5f, 0, 1, 4});
		assertTrue(seam[0] > 0);
	}

	@Test
	void aLineStartsOverOnlyWhenYouTurnAway() {
		WorldWords.Sentence line = new WorldWords.Sentence(-13, 0, 10, 0);
		assertFalse(line.lost(0, 0));
		assertFalse(line.lost(25, 5));
		assertFalse(line.lost(-30, -10));
		assertTrue(line.lost(180, 0));
		assertTrue(line.lost(90, 0));
		assertTrue(line.lost(0, 60));
		// Wraps around: a line behind the seam is still in front of you
		WorldWords.Sentence seam = new WorldWords.Sentence(170, 0, 10, 0);
		assertFalse(seam.lost(-175, 0));
	}

	@Test
	void wordsOverTheMiddleOfTheViewGoSeeThrough() {
		Vector3f ahead = WorldWords.direction(20, 0).scale(10).toVector3f();
		Vector3f aside = WorldWords.direction(70, 0).scale(10).toVector3f();
		assertTrue(WorldWords.middleFade(ahead, 1, 0.5f, 20, 0) < 0.2f);
		assertEquals(1, WorldWords.middleFade(aside, 1, 0.5f, 20, 0), 1e-4f);
		// A long word reaching over the crosshair counts, even with its middle off to one side
		Vector3f longer = WorldWords.direction(32, 0).scale(10).toVector3f();
		assertTrue(WorldWords.middleFade(longer, 3, 0.5f, 20, 0) < 0.2f);
		assertTrue(WorldWords.middleFade(longer, 0.2f, 0.5f, 20, 0) > 0.5f);
	}

	@Test
	void aBentRowArchesAndItsWordsLeanAlongIt() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0, 0, false, 2);
		// One full row: words go right until the next one starts a new row
		List<float[]> row = new ArrayList<>();
		for (float[] at = line.place(2, 1, 0, 0); row.isEmpty() || at[0] > row.get(row.size() - 1)[0]; at = line.place(2, 1, 0, 0)) {
			row.add(at);
		}
		assertTrue(row.size() >= 3);
		float[] first = row.get(0), middle = row.get(row.size() / 2), last = row.get(row.size() - 1);
		// Bigger pitch is lower: the middle sits above both ends, the start leans up, the end leans down
		assertTrue(middle[1] < first[1] && middle[1] < last[1]);
		assertTrue(first[2] > 0 && last[2] < 0);
		assertTrue(Math.abs(middle[2]) < first[2]);
		// A straight line leans by just its slope
		assertEquals(3, new WorldWords.Sentence(0, 0, 10, 0, 3).place(2, 1, 0, 0)[2], 1e-3f);
	}

	@Test
	void aWordInACrowdedSkyStaysNearItsLine() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0);
		float[] first = WorldWords.placeClear(line, 5, 1, 0, 0, List.of());
		// Everything round the line is taken: the next word keeps the spot it would have had, not rows further down
		WorldWords.Sentence copy = new WorldWords.Sentence(0, 0, 10, 0);
		copy.place(5, 1, 0, 0);
		float[] natural = copy.place(5, 1, 0, 0);
		float[] crowded = WorldWords.placeClear(line, 5, 1, 0, 0, List.<float[]>of(new float[] {0, 0, 90, 60}));
		assertEquals(natural[0], crowded[0], 1e-4f);
		assertEquals(natural[1], crowded[1], 1e-4f);
		assertEquals(copy.rows, line.rows);
		// With an old word on its spot, it carries on along the row past it
		float[] blocked = copy.place(5, 1, 0, 0), past = copy.place(5, 1, 0, 0);
		float[] moved = WorldWords.placeClear(line, 5, 1, 0, 0, List.<float[]>of(new float[] {blocked[0], blocked[1], 5, 1}));
		assertEquals(past[0], moved[0], 1e-4f);
		assertEquals(past[1], moved[1], 1e-4f);
		assertTrue(moved[1] > first[1]);
	}

	@Test
	void aWordComesInFrontOfWhateverIsInTheWay() {
		// Open sky: it stays where it was meant to be
		assertEquals(20, WorldWords.clearReach(3, 20, 0.5f, d -> false), 1e-4f);
		// A tree 10 blocks out, with clear sky behind it: the word comes in front of the tree, not behind it
		assertEquals(9.5f, WorldWords.clearReach(3, 20, 0.5f, d -> d >= 10 && d <= 12), 1e-4f);
		// Blocked straight away: it stays at the closest spot and is lifted out from there
		assertEquals(3, WorldWords.clearReach(3, 20, 0.5f, d -> true), 1e-4f);
		// Meant to be closer than the closest spot
		assertEquals(3, WorldWords.clearReach(3, 2, 0.5f, d -> false), 1e-4f);
	}

	@Test
	void droppingAndZoomingWordsOvershootThenSettle() {
		assertEquals(WorldWords.DROP, WorldWords.dropIn(0), 1e-4f);
		assertEquals(0, WorldWords.dropIn(WorldWords.DROP_TIME), 1e-4f);
		assertEquals(0, WorldWords.dropIn(5), 1e-4f);
		assertEquals(WorldWords.ZOOM_FROM, WorldWords.zoomIn(0), 1e-4f);
		assertEquals(1, WorldWords.zoomIn(WorldWords.ZOOM_TIME), 1e-4f);
		float lowest = 1, biggest = 0;
		for (float t = 0; t < 1; t += 0.01f) {
			lowest = Math.min(lowest, WorldWords.dropIn(t));
			biggest = Math.max(biggest, WorldWords.zoomIn(t));
		}
		// A dropped word dips just below its place and a zooming one grows just past its size, a little, not a lot
		assertTrue(lowest < 0 && lowest > -WorldWords.DROP / 4);
		assertTrue(biggest > 1 && biggest < 1.15f);
	}

	@Test
	void aWideRowOffToTheSideStillEndsOnScreen() {
		// A row that would run past the right edge comes back in
		float left = WorldWords.onScreen(20, 60, 0);
		assertEquals(WorldWords.VIEW_HALF - 60, left, 1e-4f);
		// ... and one past the left edge too, wherever you look
		assertEquals(170 - WorldWords.VIEW_HALF, WorldWords.onScreen(100, 30, 170), 1e-4f);
		// A row that already fits stays where it was
		assertEquals(-10, WorldWords.onScreen(-10, 30, 0), 1e-4f);
	}

	@Test
	void aWiderLineFitsMoreWordsOnARow() {
		WorldWords.Sentence normal = new WorldWords.Sentence(0, 0, 10, 0), wide = new WorldWords.Sentence(0, 0, 10, 0);
		wide.width = 2 * WorldWords.Sentence.WIDTH;
		float half = WorldWords.Sentence.WIDTH / 4;
		float[] a = normal.place(half, 1, 0, 0), b = normal.place(half, 1, 0, 0);
		float[] c = wide.place(half, 1, 0, 0), d = wide.place(half, 1, 0, 0);
		// Two half-row words don't fit on a normal row with the space between them, but sit side by side on a wide one
		assertTrue(b[1] > a[1] + 1);
		assertEquals(c[1], d[1], 1e-4);
		assertTrue(d[0] > c[0] + 2 * half);
		// The next line of a verse keeps the width
		assertEquals(wide.width, wide.under().width, 1e-6);
	}

	@Test
	void aWordOnlyGoesWhereNothingElseIs() {
		List<float[]> taken = List.of(new float[] {0, 0, 2, 1});
		assertFalse(WorldWords.clear(1, 0.5f, 1, 1, taken));
		assertFalse(WorldWords.clear(358, 0, 1, 1, taken));
		assertTrue(WorldWords.clear(4, 0, 1, 1, taken));
		assertTrue(WorldWords.clear(0, 3, 1, 1, taken));
		// A bouncy word on the row below still clears the row above
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0);
		float[] up = line.place(5, 1, 0, 1.2f);
		while (line.place(5, 1, 0, 1.2f)[1] <= up[1] + 0.01f) {
		}
		float[] down = line.place(1, 1, 0, -1.2f);
		assertTrue(down[1] - up[1] >= 2 + WorldWords.Sentence.ROW_GAP - 1e-4f);
	}

	@Test
	void aFullRowCarriesOnBelowFromTheLeft() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0);
		float[] last = line.place(5, 1, 0, 0);
		float row = last[1];
		while (last[1] == row) {
			last = line.place(5, 1, 1, 0);
		}
		assertEquals(row + 2 + WorldWords.Sentence.ROW_GAP, last[1], 1e-4);
		assertEquals(1 + 5, last[0], 1e-4);
		assertTrue(line.place(5, 1, 0, 0)[0] > last[0]);
	}

	@Test
	void theNextLineOfAVerseStartsUnderTheLastOne() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0);
		line.rest = 200;
		// Two pieces fill the first row, two more start the second
		float lowest = 0, end = 0;
		for (int i = 0; i < 4; i++) {
			float[] at = line.place(5, 1, 0, i == 1 ? 1.2f : 0);
			lowest = Math.max(lowest, at[1] + 1);
			end = at[0] + 5;
		}
		WorldWords.Sentence next = line.under();
		next.rest = 10;
		float[] first = next.place(5, 1, 0, -1.2f);
		// A clear gap under the last line, even when the new word bobs up
		assertTrue(first[1] - 1 - lowest >= WorldWords.Sentence.ROW_GAP + WorldWords.Sentence.VERSE_GAP - 1e-4f);
		// A short next line sits in the middle under the last row
		assertEquals(end / 2, first[0], 1e-3);
	}

	@Test
	void aShortLastRowSitsCentredUnderTheRowAbove() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0);
		// Two pieces fill the first row, then about one more piece is left
		line.rest = 2 * (10 + WorldWords.Sentence.GAP) + 10;
		float[] last = line.place(5, 1, 0, 0);
		float row = last[1], edge = 0;
		while (last[1] == row) {
			edge = last[0] + 5;
			last = line.place(5, 1, 0, 0);
		}
		// What's left is about one piece wide, so it goes under the middle of the full row
		assertEquals(edge / 2, last[0], 1e-3);
		// A line with plenty still to come starts its next row at the left as usual
		WorldWords.Sentence longer = new WorldWords.Sentence(0, 0, 10, 0);
		longer.rest = 200;
		float[] l = longer.place(5, 1, 0, 0);
		while (l[1] == row) {
			l = longer.place(5, 1, 0, 0);
		}
		assertEquals(5, l[0], 1e-4);
	}

	@Test
	void aPieceWiderThanARowStillGetsOneToItself() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0);
		float[] wide = line.place(WorldWords.Sentence.WIDTH, 1, 0, 0);
		assertEquals(-1, wide[1], 1e-4);
		assertTrue(line.place(1, 1, 0, 0)[1] > wide[1]);
	}

	@Test
	void aRisenCardTakesUpTheSkyFromWhereItStartedToWhereItIsNow() {
		Vec3 to = new Vec3(0, 0, 10);
		float[] still = WorldWords.footprint(to, 0, 1, 0.3f);
		float[] risen = WorldWords.footprint(to, 1, 1, 0.3f);
		assertEquals(still[0], risen[0], 1e-4);
		assertEquals(still[2], risen[2], 1e-4);
		// The patch reaches from the start (pitch 0) up to where it is now (about 5.7 degrees up)
		float up = (float) Math.toDegrees(Math.atan2(1, 10));
		assertEquals(still[1] + still[3], risen[1] + risen[3], 1e-3);
		assertEquals(-up - still[3], risen[1] - risen[3], 1e-2);
	}

	@Test
	void aCardHighUpTakesUpMoreYawThanOneOnTheHorizon() {
		float level = WorldWords.footprint(new Vec3(0, 0, 10), 0, 1, 0.3f)[2];
		float high = WorldWords.footprint(new Vec3(0, Math.sqrt(50), Math.sqrt(50)), 0, 1, 0.3f)[2];
		// 45 degrees up and just as far away, the card is only about 7 blocks from the axis it turns around, not 10
		assertEquals(Math.toDegrees(Math.atan2(1, Math.sqrt(50))), high, 1e-3);
		assertTrue(high > level * 1.3f);
	}

	@Test
	void aSongTitleIsTidiedAndCutShortWhenLong() {
		assertEquals("Neon Skyline", WorldWords.titleText("  Neon   Skyline ", false));
		assertEquals("NEON SKYLINE", WorldWords.titleText("Neon Skyline", true));
		String cut = WorldWords.titleText("A very long song title that goes on and on and on", false);
		assertEquals(WorldWords.TITLE_MAX, cut.codePointCount(0, cut.length()));
		assertTrue(cut.endsWith("\u2026"));
		assertEquals("", WorldWords.titleText("   ", false));
	}

	@Test
	void lettersGetADarkEdgeOnlyByDay() {
		assertEquals(0, WorldWords.edgeOpacity(0), 1e-6);
		assertEquals(0, WorldWords.edgeOpacity(0.2f), 1e-6);
		assertTrue(WorldWords.edgeOpacity(0.6f) > 0);
		assertTrue(WorldWords.edgeOpacity(1) > WorldWords.edgeOpacity(0.6f));
		assertTrue(WorldWords.edgeOpacity(1) <= 1);
	}

	@Test
	void wordsDropFasterAndFasterWhenYouDie() {
		assertEquals(0, WorldWords.fallDrop(0), 1e-6);
		float first = WorldWords.fallDrop(0.5f), second = WorldWords.fallDrop(1f) - first;
		assertTrue(first > 0 && second > first, "falling speeds up");
		assertTrue(WorldWords.fallDrop(1.6f) > 3, "gone well below where it was by the time it fades");
	}

	@Test
	void aStraightLineKeepsEveryWordLevelAndUpright() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0, 0, false, 0);
		float[] first = line.place(1.5f, 1, 0, 0);
		for (int i = 0; i < 4; i++) {
			float[] word = line.place(1.5f, 1, 0, 0);
			assertEquals(first[1], word[1], 1e-4f);
			assertEquals(0, word[2], 1e-4f);
		}
	}

	@Test
	void wordsAtTheEndsOfADeepBowStillStandMostlyUpright() {
		WorldWords.Sentence line = new WorldWords.Sentence(0, 0, 10, 0, 5, false, 2.2f);
		float[] first = line.place(1, 1, 0, 0);
		assertTrue(first[2] > 0 && first[2] <= WorldWords.Sentence.CURVE_LEAN);
		float lowest = first[2];
		for (int i = 0; i < 12; i++) {
			lowest = Math.min(lowest, line.place(1, 1, 0, 0)[2]);
		}
		assertTrue(lowest >= -WorldWords.Sentence.CURVE_LEAN && lowest < first[2]);
	}
}
