package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The curtains, clouds and stars are written once, with the winding that faces the camera, while the sparkles and shooting
 * stars still go out both ways. Drives the real drawing code against a recording vertex consumer.
 */
class SkyWindingTest {
	private static final int SEGMENTS = 80, SLICES = 24, DOTS = 60;

	private static SkyEffects effects() throws Exception {
		SkyEffects e = new SkyEffects(null, null, null, null);
		set(e, "radius", 100f);
		set(e, "fade", 1f);
		set(e, "cloudAlpha", 1f);
		set(e, "starColor", 0xFFFFFF);
		set(e, "meteorColor", 0xFFFFFF);
		set(e, "auroraLow", 0x30C0A0);
		set(e, "auroraHigh", 0x8040C0);
		set(e, "clock", 12f);
		return e;
	}

	private static void set(Object o, String name, Object v) throws Exception {
		Field f = SkyEffects.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, v);
	}

	/** A consumer that keeps the position of every vertex it is given. */
	private static VertexConsumer recorder(List<float[]> out) {
		VertexConsumer[] self = new VertexConsumer[1];
		self[0] = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class<?>[] {VertexConsumer.class},
				(proxy, m, args) -> {
					if (m.getName().equals("addVertex") && args.length == 4) {
						out.add(new float[] {(Float) args[1], (Float) args[2], (Float) args[3]});
					}
					return m.getReturnType() == VertexConsumer.class ? self[0] : null;
				});
		return self[0];
	}

	private static List<float[]> draw(String method, Object... extra) throws Exception {
		SkyEffects e = effects();
		List<float[]> out = new ArrayList<>();
		for (Method m : SkyEffects.class.getDeclaredMethods()) {
			if (m.getName().equals(method)) {
				m.setAccessible(true);
				Object[] args = new Object[2 + extra.length];
				args[0] = new PoseStack().last();
				args[1] = recorder(out);
				System.arraycopy(extra, 0, args, 2, extra.length);
				m.invoke(e, args);
				return out;
			}
		}
		throw new AssertionError(method);
	}

	/** Every six vertices are one quad written as the triangles 0 1 2, 0 2 3: its four corners. */
	private static List<float[][]> quads(List<float[]> v) {
		List<float[][]> q = new ArrayList<>();
		for (int i = 0; i + 5 < v.size(); i += 6) {
			q.add(new float[][] {v.get(i), v.get(i + 1), v.get(i + 2), v.get(i + 5)});
		}
		return q;
	}

	private static boolean faces(float[][] q) {
		float[] x = new float[4], y = new float[4], z = new float[4];
		for (int i = 0; i < 4; i++) {
			x[i] = q[i][0];
			y[i] = q[i][1];
			z[i] = q[i][2];
		}
		return SkyCanvas.facesOrigin(x, y, z);
	}

	private static void assertAllFaceOrigin(List<float[]> v) {
		assertTrue(v.size() > 0);
		for (float[][] q : quads(v)) {
			assertTrue(faces(q), "a quad is wound away from the camera");
		}
	}

	@Test
	void aCurtainIsWrittenOnceFacingTheCamera() throws Exception {
		List<float[]> v = draw("ribbon", 0);
		assertEquals(SEGMENTS * 2 * 6, v.size());
		assertAllFaceOrigin(v);
	}

	@Test
	void aCloudIsWrittenOnceFacingTheCamera() throws Exception {
		// The cloud's quads have a fan tip (two equal corners), which facesOrigin handles
		for (int k = 0; k < 7; k++) {
			List<float[]> v = draw("blob", k);
			assertEquals(SLICES * 2 * 6, v.size(), "cloud " + k);
			assertAllFaceOrigin(v);
		}
	}

	@Test
	void starsAreWrittenOnceFacingTheCamera() throws Exception {
		List<float[]> v = draw("dots");
		assertEquals(DOTS * 6, v.size());
		assertAllFaceOrigin(v);
	}

	@Test
	void shootingStarsKeepBothWindings() throws Exception {
		SkyEffects e = effects();
		set(e, "meteorsAlive", 1);
		for (String f : new String[] {"meteorYaw", "meteorElev", "meteorAngle", "meteorSpeed", "meteorLength", "meteorAge", "meteorLife"}) {
			Field fl = SkyEffects.class.getDeclaredField(f);
			fl.setAccessible(true);
			float[] a = (float[]) fl.get(e);
			a[0] = switch (f) {
				case "meteorAge" -> 1f;
				case "meteorLife" -> 3f;
				case "meteorSpeed" -> 0.2f;
				case "meteorLength" -> 0.1f;
				case "meteorYaw" -> 1f;
				case "meteorElev" -> 0.5f;
				default -> 0.3f;
			};
		}
		List<float[]> out = new ArrayList<>();
		Method m = SkyEffects.class.getDeclaredMethod("meteors", PoseStack.Pose.class, VertexConsumer.class);
		m.setAccessible(true);
		m.invoke(e, new PoseStack().last(), recorder(out));
		assertEquals(12, out.size(), "one quad, both windings");
		// The two windings are opposite, so exactly one of them faces the camera
		assertTrue(faces(quads(out.subList(0, 6)).get(0)) != faces(quads(out.subList(6, 12)).get(0)));
	}
}
