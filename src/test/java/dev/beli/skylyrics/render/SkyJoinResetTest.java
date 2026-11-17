package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyJoinResetTest {
	@Test
	void joiningAWorldClearsTheLastWorldsNightSky() throws Exception {
		SkyEffects sky = new SkyEffects(null, null, null, null);
		set(sky, "presence", 1f);
		set(sky, "skyStars", 0.5f);
		set(sky, "sparkleShow", 1f);
		set(sky, "meteorsAlive", 2);
		set(sky, "last", 12345L);
		set(sky, "corner", 3);
		sky.onJoinWorld();
		assertEquals(0f, (float) get(sky, "presence"));
		assertTrue(Float.isNaN((float) get(sky, "skyStars")));
		assertEquals(0f, (float) get(sky, "sparkleShow"));
		assertEquals(0, (int) get(sky, "meteorsAlive"));
		assertEquals(0L, (long) get(sky, "last"));
		assertEquals(0, (int) get(sky, "corner"));
	}

	private static void set(Object o, String name, Object v) throws Exception {
		java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, v);
	}

	private static Object get(Object o, String name) throws Exception {
		java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(o);
	}
}
