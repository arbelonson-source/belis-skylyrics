package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The song's name after a song change stays up for its time times Minecraft's Notification Display Time (Accessibility),
 * as Minecraft's own music pop-up and the song library's messages do: on the top bar with Song name only on change, and
 * as the title in the World. It stayed 6 and 4 seconds whatever the option said.
 */
class NoticeTimeTest {
	private static final long S = 1_000_000_000L;

	@Test
	void theTopBarShowsTheSongNameForTheNotificationTime() {
		assertTrue(NowPlayingHud.songNameUp(5 * S, 1.0));
		assertFalse(NowPlayingHud.songNameUp(7 * S, 1.0));
		// Twice as long at 2, half at 0.5
		assertTrue(NowPlayingHud.songNameUp(11 * S, 2.0), "still up 11 s after the change at 2");
		assertFalse(NowPlayingHud.songNameUp(13 * S, 2.0));
		assertFalse(NowPlayingHud.songNameUp(4 * S, 0.5));
		// An option that can't be read counts as 1
		assertTrue(NowPlayingHud.songNameUp(5 * S, Double.NaN));
		assertFalse(NowPlayingHud.songNameUp(7 * S, Double.NaN));
	}

	@Test
	void theTopBarMovesOnToTheLineAfterTheNotificationTime() {
		assertTrue(NowPlayingHud.titleDone(4 * S, 1.0));
		assertFalse(NowPlayingHud.titleDone(6 * S, 2.0), "the name still shows 6 s after the change at 2");
		assertTrue(NowPlayingHud.titleDone(7 * S, 2.0));
	}

	@Test
	void theTitleInTheWorldStaysForTheNotificationTime() throws Exception {
		WorldWords words = new WorldWords();
		words.notices(2.0);
		Constructor<?> make = Class.forName("dev.beli.skylyrics.render.WorldWords$Card").getDeclaredConstructors()[0];
		make.setAccessible(true);
		Object title = make.newInstance(LyricsRenderer.Display.of("TITLE"), new float[] {1}, new Vec3(0, 0, 10), 0f, 0f, 0f, 1f, 0L);
		WorldWordsTravelTest.set(title, "title", true);
		WorldWordsTravelTest.cards(words).add(title);
		words.expire(Vec3.ZERO, 5 * S);
		assertEquals(-1L, (long) WorldWordsTravelTest.get(title, "goneAt"), "still up 5 s after the change at 2 (4 s at 1)");
		words.expire(Vec3.ZERO, 9 * S);
		assertEquals(9 * S, (long) WorldWordsTravelTest.get(title, "goneAt"), "fading once 8 s are over");
		assertEquals(4 * S, WorldWords.titleHoldNs(1.0));
	}
}
