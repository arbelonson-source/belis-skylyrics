package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** World words while you travel (Elytra mode): gliding, riding, flying, or moving very fast. */
class WorldWordsTravelTest {
	private static final long FRAME = 16_000_000L;

	/**
	 * With Elytra mode off the words stay where they were put, however fast you go: a rocket-boosted flight or a dive
	 * (well over 30 blocks a second) must not carry them along or swing them round as you steer, and new words keep
	 * to the World direction.
	 */
	@Test
	void withElytraModeOffTheWordsStayInTheWorldHoweverFastYouFly() throws Exception {
		WorldWords words = new WorldWords();
		words.travel(false);
		Vec3 start = new Vec3(0, 80, 0), spot = start.add(0, 0, 10);
		Object card = card(spot);
		cards(words).add(card);
		long now = 1_000_000_000L;
		// 1 block a frame (62 blocks a second) for half a second, steering a little each frame
		for (int frame = 0; frame < 30; frame++) {
			words.glide(start.add(frame, 0, 0), frame * 3, 0, false, now + frame * FRAME);
			assertEquals(spot, get(card, "pos"), "frame " + frame + ": the word stays where it was put");
			assertEquals(false, get(words, "gliding"), "frame " + frame + ": not travelling");
		}
		// Gliding or riding as the game says, too
		for (int frame = 30; frame < 40; frame++) {
			words.glide(start.add(frame, 0, 0), frame * 3, 0, true, now + frame * FRAME);
			assertEquals(spot, get(card, "pos"), "frame " + frame + ": gliding, the word still stays");
		}
	}

	/** With Elytra mode on, the same fast flight carries the words, and so does gliding as the game says. */
	@Test
	void withElytraModeOnAFastFlightCarriesTheWords() throws Exception {
		WorldWords words = new WorldWords();
		Vec3 start = new Vec3(0, 80, 0);
		Object card = card(start.add(0, 0, 10));
		cards(words).add(card);
		long now = 1_000_000_000L;
		for (int frame = 0; frame < 10; frame++) {
			words.glide(start.add(frame, 0, 0), 0, 0, false, now + frame * FRAME);
		}
		Vec3 pos = (Vec3) get(card, "pos");
		assertEquals(true, pos.x > 5, "carried along after the first fast frames: " + pos);
		words.glide(start.add(20, 0, 0), 0, 0, true, now + 20 * FRAME);
		assertEquals(true, get(words, "gliding"));
	}

	static Object card(Vec3 pos) throws Exception {
		Class<?> cardClass = Class.forName("dev.beli.skylyrics.render.WorldWords$Card");
		Constructor<?> make = cardClass.getDeclaredConstructors()[0];
		make.setAccessible(true);
		return make.newInstance(LyricsRenderer.Display.of("WORD"), new float[] {1}, pos, 0f, 0f, 0f, 1f, 0L);
	}

	/** Where a card is drawn: its spot plus how far it has slid so far. */
	static Vec3 drawn(Object card) throws Exception {
		return ((Vec3) get(card, "pos")).add((Vec3) get(card, "slid"));
	}

	@SuppressWarnings("unchecked")
	static List<Object> cards(WorldWords words) throws Exception {
		return (List<Object>) get(words, "cards");
	}

	static Object get(Object o, String name) throws Exception {
		Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(o);
	}

	static void set(Object o, String name, Object v) throws Exception {
		Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, v);
	}
}
