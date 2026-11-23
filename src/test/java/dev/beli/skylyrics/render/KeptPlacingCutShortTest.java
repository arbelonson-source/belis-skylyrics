package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * A sky keeps its placed quads until the direction, the distance or the fade changes. If placing them is cut short by an
 * error (a Windows test run once threw at the eclipse corona's last quad), the next frame must place them all again: keeping
 * what was placed would draw half a corona, or half a comet, until the lyrics' direction or the render distance changed.
 */
class KeptPlacingCutShortTest {
	private static final float RADIUS = 80, BASE_YAW = 20, PIXEL = 0.002f;

	/** Keeps every quad handed on, corners and colours, in order. */
	private static final class Recorder extends SkyCanvas {
		final List<String> quads = new ArrayList<>();
		private final StringBuilder quad = new StringBuilder();
		private int n;

		@Override
		protected void put(float x, float y, float z, int argb) {
			quad.append(x).append(',').append(y).append(',').append(z).append(',').append(Integer.toHexString(argb)).append(';');
			if (++n == 4) {
				quads.add(quad.toString());
				quad.setLength(0);
				n = 0;
			}
		}
	}

	private static final String[] CORNERS = {"x", "y", "z", "keep", "colour", "faded"};

	private static Field field(Class<?> type, String name) throws Exception {
		Field f = type.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	/** Leaves room for only {@code room} quads, so placing them all throws halfway, as running out of room did on Windows. */
	private static void cutShort(KeptQuads q, int room) throws Exception {
		for (String name : CORNERS) {
			Field f = field(KeptQuads.class, name);
			f.set(q, Array.newInstance(f.getType().getComponentType(), room * 4));
		}
	}

	/** Gives back the full room, keeping what was placed before the throw. */
	private static void restore(KeptQuads q) throws Exception {
		int corners = field(KeptQuads.class, "quads").getInt(q) * 4;
		for (String name : CORNERS) {
			Field f = field(KeptQuads.class, name);
			Object old = f.get(q), full = Array.newInstance(f.getType().getComponentType(), corners);
			System.arraycopy(old, 0, full, 0, Array.getLength(old));
			f.set(q, full);
		}
	}

	private static <T> void check(String what, Supplier<T> make, String kept, BiConsumer<T, SkyCanvas> draw) throws Exception {
		Recorder reference = new Recorder();
		draw.accept(make.get(), reference);
		T sky = make.get();
		KeptQuads q = (KeptQuads) field(sky.getClass(), kept).get(sky);
		cutShort(q, field(KeptQuads.class, "quads").getInt(q) / 2);
		assertThrows(ArrayIndexOutOfBoundsException.class, () -> draw.accept(sky, new Recorder()), what + ": placing should have run out of room");
		restore(q);
		Recorder next = new Recorder();
		draw.accept(sky, next);
		assertEquals(2, q.layouts, what + ": the frame after the error should place every quad again");
		assertEquals(reference.quads, next.quads, what + ": the frame after the error should draw exactly what a fresh sky draws");
	}

	@Test
	void theEclipsePlacesAgainAfterAPlacingCutShort() throws Exception {
		check("corona", EclipseSky::new, "coronaQuads", (s, c) -> s.draw(c, RADIUS, BASE_YAW, 1, 3));
		check("milky way", EclipseSky::new, "bandQuads", (s, c) -> s.draw(c, RADIUS, BASE_YAW, 1, 3));
		check("black disc", EclipseSky::new, "discQuads", (s, c) -> s.drawDisc(c, RADIUS, BASE_YAW, 1));
	}

	@Test
	void theCometPlacesAgainAfterAPlacingCutShort() throws Exception {
		for (String part : new String[] {"dust", "ion", "head"}) {
			check("comet " + part, CometSky::new, part, (s, c) -> s.draw(c, RADIUS, BASE_YAW, 1, 3));
		}
	}

	@Test
	void thePaintedSkiesPlaceAgainAfterAPlacingCutShort() throws Exception {
		check("moonlight stars", MoonlitSky::new, "starQuads", (s, c) -> s.draw(c, RADIUS, BASE_YAW, 1, 3, PIXEL));
		check("synthwave stars", SynthwaveSky::new, "starQuads", (s, c) -> s.draw(c, RADIUS, BASE_YAW, 1, 3, PIXEL));
		check("synthwave sun face", SynthwaveSky::new, "faceQuads", (s, c) -> s.draw(c, RADIUS, BASE_YAW, 1, 3, PIXEL));
	}

	@Test
	void aMeshPlacesAgainAfterAPlacingCutShort() throws Exception {
		SkyCanvas.SkyFrame f = new SkyCanvas.SkyFrame().aim(0, 62);
		float[] rings = new float[20];
		for (int i = 0; i < rings.length; i++) {
			rings[i] = (float) Math.tan(Math.toRadians(i * 1.5f));
		}
		float cos = (float) Math.cos(Math.toRadians(BASE_YAW)), sin = (float) Math.sin(Math.toRadians(BASE_YAW));
		check("mesh", () -> SkyMesh.polar(f, rings, 40, (x, y, z) -> 0x302060), "kept", (m, c) -> {
			c.fadeBelowHorizon(3);
			m.draw(c, cos, sin, RADIUS, 1);
		});
	}
}
