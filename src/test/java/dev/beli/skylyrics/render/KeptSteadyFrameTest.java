package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * A sky's kept quads count as placed only once the last one is in (so a placing an error cut short is done again). Each
 * sky must then place exactly as many quads as it keeps room for: one fewer, and every steady frame would place them all
 * again without anything looking different. The keep tests count the corners worked out through the canvas, which kept
 * quads never go through, so this counts the placings themselves.
 */
class KeptSteadyFrameTest {
	private static final float RADIUS = 80, BASE_YAW = 20, PIXEL = 0.002f;
	private static final int FRAMES = 30;

	private static KeptQuads kept(Object sky, String name) throws Exception {
		Field f = sky.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return (KeptQuads) f.get(sky);
	}

	/** Draws {@code FRAMES} frames where only time moves on, and checks each of {@code names} was placed on the first only. */
	private static <T> void check(String what, Supplier<T> make, BiConsumer<T, Double> frame, String... names) throws Exception {
		T sky = make.get();
		double time = 3;
		for (int f = 0; f < FRAMES; f++) {
			frame.accept(sky, time);
			time += 1 / 60.0;
		}
		for (String name : names) {
			assertEquals(1, kept(sky, name).layouts, what + " " + name + ": placed again on frames where nothing that places it changed");
		}
	}

	@Test
	void theEclipsePlacesItsQuadsOnce() throws Exception {
		check("eclipse", EclipseSky::new, (s, t) -> {
			SkyRecorder c = new SkyRecorder();
			s.draw(c, RADIUS, BASE_YAW, 1, t);
			s.drawDisc(c, RADIUS, BASE_YAW, 1);
		}, "coronaQuads", "bandQuads", "discQuads");
	}

	@Test
	void theCometPlacesItsQuadsOnce() throws Exception {
		check("comet", CometSky::new, (s, t) -> s.draw(new SkyRecorder(), RADIUS, BASE_YAW, 1, t), "dust", "ion", "head");
	}

	@Test
	void thePaintedSkiesPlaceTheirQuadsOnce() throws Exception {
		check("moonlight", MoonlitSky::new, (s, t) -> s.draw(new SkyRecorder(), RADIUS, BASE_YAW, 1, t, PIXEL), "starQuads");
		check("synthwave", SynthwaveSky::new, (s, t) -> s.draw(new SkyRecorder(), RADIUS, BASE_YAW, 1, t, PIXEL), "starQuads", "faceQuads");
	}
}
