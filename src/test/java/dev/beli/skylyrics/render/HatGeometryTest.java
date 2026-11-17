package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The hats' vertices (position, colour, light, order) stay exactly as they were, and a quad costs no garbage. */
class HatGeometryTest {
	/** Folds every vertex it is given into a hash, allocating nothing itself. */
	private static final class Hasher implements VertexConsumer {
		long hash = 1125899906842597L;
		int vertices;

		private void mix(int v) {
			hash = 31 * hash + v;
		}

		@Override
		public VertexConsumer addVertex(com.mojang.blaze3d.vertex.PoseStack.Pose pose, float x, float y, float z) {
			return addVertex(x, y, z);
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			mix(Float.floatToIntBits(x));
			mix(Float.floatToIntBits(y));
			mix(Float.floatToIntBits(z));
			vertices++;
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			mix(r << 24 | g << 16 | b << 8 | a);
			return this;
		}

		@Override
		public VertexConsumer setColor(int argb) {
			mix(argb);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			mix(Float.floatToIntBits(u));
			mix(Float.floatToIntBits(v));
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setLight(int light) {
			mix(light);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			return this;
		}

		// No @Override on these two: only some versions' VertexConsumer has them (setUv3 is new in 26.3)
		public VertexConsumer setUv3(float u, float v) {
			return this;
		}

		public VertexConsumer setLineWidth(float w) {
			return this;
		}
	}

	private static final String[] HATS = {"headphones", "conical", "crown", "catEars", "pumpkin", "topHat", "horns", "shades",
			"propellerCap"};

	private static Method find(String name) {
		for (Method m : HatLayer.class.getDeclaredMethods()) {
			if (m.getName().equals(name)) {
				m.setAccessible(true);
				return m;
			}
		}
		throw new AssertionError(name);
	}

	private static String fingerprint() throws Exception {
		List<String> out = new ArrayList<>();
		for (String hat : HATS) {
			Hasher h = new Hasher();
			find(hat).invoke(null, new PoseStack().last(), h, 0x7000A0, 0xFF80C0FF);
			out.add(hat + ":" + h.vertices + ":" + h.hash);
		}
		Hasher h = new Hasher();
		find("tube").invoke(null, new PoseStack().last(), h, 0.27f, 0.02f, 0f, 0xFF80C0FF, 0xF000F0);
		out.add("tube:" + h.vertices + ":" + h.hash);
		h = new Hasher();
		find("shine").invoke(null, new PoseStack().last(), h, 0.27f, 0.1f, 0x9680C0FF);
		out.add("shine:" + h.vertices + ":" + h.hash);
		h = new Hasher();
		find("flowerCrown").invoke(null, new PoseStack().last(), h, 0x7000A0, 0xFF80C0FF, 0xFFC0E0FF, 0.7f, 1.1f);
		out.add("flower:" + h.vertices + ":" + h.hash);
		return String.join("\n", out);
	}

	@Test
	void everyHatEmitsTheSameVertices() throws Exception {
		assertEquals(EXPECTED, fingerprint());
	}

	@Test
	void aQuadAllocatesNothing() {
		Hasher h = new Hasher();
		PoseStack.Pose pose = new PoseStack().last();
		ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
		long id = Thread.currentThread().getId();
		for (int i = 0; i < 200_000; i++) { // warm up so the JIT has removed boxing
			HatLayer.face(pose, h, 1, 2, 3, 0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f);
		}
		long before = mx.getThreadAllocatedBytes(id);
		for (int i = 0; i < 10_000; i++) {
			HatLayer.face(pose, h, 1, 2, 3, 0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f);
		}
		long used = mx.getThreadAllocatedBytes(id) - before;
		assertTrue(used < 100_000, "allocated " + used + " bytes for 10000 quads");
	}

	private static final String EXPECTED = "headphones:896:-4383612270723956635\n" + "conical:1920:1906977578429016421\n" + "crown:448:949804495659095717\n"
			+ "catEars:32:9111017345221581989\n" + "pumpkin:280:-4035086064048593211\n" + "topHat:2560:8522529602080991589\n"
			+ "horns:1280:1552910835880054501\n" + "shades:288:4775260355629271493\n" + "propellerCap:3328:3269606867118089829\n"
			+ "tube:1280:5525312193883335653\n" + "shine:1280:-6004133728785465627\n" + "flower:2048:7630636214195288197";
}
