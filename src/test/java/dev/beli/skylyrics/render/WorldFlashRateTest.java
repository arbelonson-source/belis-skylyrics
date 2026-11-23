package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The World words' white flashes (a word landing, a word you hit) start at most three times a second, as the line-start
 * flashes do (WCAG 2.3.1; the FAQ promises it). A fast line put a flash on every word, five to eight a second, and
 * clicking at a word flashed it white on every click.
 */
class WorldFlashRateTest {
	private static final long MS = 1_000_000L;
	/** How long after its start a flash is looked at: a frame in. */
	private static final long FRAME = 16 * MS;

	/** Flashes that began in one second starting at {@code from}. */
	private static int inOneSecond(List<Long> starts, long from) {
		int n = 0;
		for (long t : starts) {
			if (t >= from && t < from + 1_000 * MS) {
				n++;
			}
		}
		return n;
	}

	@Test
	void aFastLinePutsAtMostThreeFlashesInASecond() throws Exception {
		WorldWords words = new WorldWords();
		List<Long> flashed = new ArrayList<>();
		// A rap line: a word every eighth of a second, for two seconds
		for (int i = 0; i < 16; i++) {
			long born = 5_000 * MS + i * 125 * MS;
			Object card = card(born);
			land(words, card, born);
			if (WorldWords.landingFlash((boolean) WorldWordsTravelTest.get(card, "flashes"), FRAME / 1e9f) > 0) {
				flashed.add(born);
			}
		}
		for (long from = 5_000 * MS; from < 6_000 * MS; from += 125 * MS) {
			assertTrue(inOneSecond(flashed, from) <= 3, "flashes in the second from " + from / MS + " ms: " + flashed);
		}
		// Still some, and a word on its own (half a second after the last) still lands with one
		assertTrue(flashed.size() >= 5, "flashes " + flashed);
		Object alone = card(8_000 * MS);
		land(words, alone, 8_000 * MS);
		assertEquals(true, WorldWordsTravelTest.get(alone, "flashes"));
	}

	@Test
	void wordsThatLandTogetherFlashTogether() throws Exception {
		// A song's title and artist, or the pieces caught up after a jump, go up in the same frame: one flash, not one each
		WorldWords words = new WorldWords();
		Object title = card(5_000 * MS), artist = card(5_000 * MS);
		land(words, title, 5_000 * MS);
		land(words, artist, 5_000 * MS);
		assertEquals(true, WorldWordsTravelTest.get(title, "flashes"));
		assertEquals(true, WorldWordsTravelTest.get(artist, "flashes"));
	}

	@Test
	void clickingAtAWordFlashesItAtMostThreeTimesASecond() throws Exception {
		WorldWords words = new WorldWords();
		Vec3 eye = new Vec3(0, 80, 0), look = WorldWords.direction(0, 0);
		Object card = card(5_000 * MS);
		WorldWordsTravelTest.cards(words).add(card);
		Method knock = WorldWords.class.getDeclaredMethod("knockAt", Vec3.class, Vec3.class, long.class);
		knock.setAccessible(true);
		List<Long> flashed = new ArrayList<>();
		float before = 0;
		// Ten clicks a second at the word, for two seconds
		for (int i = 0; i < 20; i++) {
			long now = 5_000 * MS + i * 100 * MS;
			knock.invoke(words, eye, look, now);
			assertEquals(now, (long) WorldWordsTravelTest.get(card, "hitAt"), "every click still knocks it");
			float white = WorldWords.hitFlash((long) WorldWordsTravelTest.get(card, "hitFlashAt"), now + FRAME);
			if (white > before + 0.05f) {
				flashed.add(now);
			}
			before = WorldWords.hitFlash((long) WorldWordsTravelTest.get(card, "hitFlashAt"), now + 100 * MS);
		}
		assertTrue(flashed.size() >= 2, "the first click flashes, and later ones now and then: " + flashed);
		for (long from = 5_000 * MS; from < 6_000 * MS; from += 100 * MS) {
			assertTrue(inOneSecond(flashed, from) <= 3, "flashes in the second from " + from / MS + " ms: " + flashed);
		}
	}

	@Test
	void hitAndLandingFlashesShareTheLimit() throws Exception {
		WorldWords words = new WorldWords();
		Vec3 eye = new Vec3(0, 80, 0), look = WorldWords.direction(0, 0);
		Object hit = card(5_000 * MS);
		WorldWordsTravelTest.cards(words).add(hit);
		Method knock = WorldWords.class.getDeclaredMethod("knockAt", Vec3.class, Vec3.class, long.class);
		knock.setAccessible(true);
		// A word lands, and a tenth of a second later you hit one: the hit doesn't start a second flash yet
		Object landed = card(5_000 * MS);
		land(words, landed, 5_000 * MS);
		knock.invoke(words, eye, look, 5_100 * MS);
		assertEquals(0f, WorldWords.hitFlash((long) WorldWordsTravelTest.get(hit, "hitFlashAt"), 5_100 * MS + FRAME), 0f);
	}

	/** A card that goes up at {@code born}. */
	private static Object card(long born) throws Exception {
		Constructor<?> make = Class.forName("dev.beli.skylyrics.render.WorldWords$Card").getDeclaredConstructors()[0];
		make.setAccessible(true);
		return make.newInstance(LyricsRenderer.Display.of("WORD"), new float[] {1}, new Vec3(0, 80, 10), 0f, 0f, 0f, 1f, born);
	}

	/** Puts a card up as a new word or title does ({@code land}; the card's class is private, so through reflection). */
	private static void land(WorldWords words, Object card, long now) throws Exception {
		Method land = WorldWords.class.getDeclaredMethod("land", Class.forName("dev.beli.skylyrics.render.WorldWords$Card"), long.class);
		land.setAccessible(true);
		land.invoke(words, card, now);
	}
}
