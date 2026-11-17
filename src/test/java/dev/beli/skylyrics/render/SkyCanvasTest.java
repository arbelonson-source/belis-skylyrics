package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyCanvasTest {
	/** Keeps every corner it is handed. */
	private static final class Recorder extends SkyCanvas {
		final List<float[]> corners = new ArrayList<>();

		@Override
		protected void put(float x, float y, float z, int argb) {
			corners.add(new float[] {x, y, z});
		}
	}

	@Test
	void quadsComeOutFacingTheCamera() {
		// A square 5 blocks north of the camera, given once each way round
		float[][] square = {{-1, -1, -5}, {1, -1, -5}, {1, 1, -5}, {-1, 1, -5}};
		for (boolean reverse : new boolean[] {false, true}) {
			Recorder r = new Recorder();
			for (int i = 0; i < 4; i++) {
				float[] p = square[reverse ? 3 - i : i];
				r.vertex(p[0], p[1], p[2], 0);
			}
			assertEquals(4, r.corners.size());
			float[] xs = new float[4], ys = new float[4], zs = new float[4];
			for (int i = 0; i < 4; i++) {
				xs[i] = r.corners.get(i)[0];
				ys[i] = r.corners.get(i)[1];
				zs[i] = r.corners.get(i)[2];
			}
			assertTrue(SkyCanvas.facesOrigin(xs, ys, zs));
			// Looking north (east on the right) the corners must run counter-clockwise: x grows, then y
			float area = 0;
			for (int i = 0; i < 4; i++) {
				int j = (i + 1) % 4;
				area += xs[i] * ys[j] - xs[j] * ys[i];
			}
			assertTrue(area > 0);
		}
	}

	@Test
	void fanTipsStillGetAFacing() {
		float[] x = {0, 0, 1, -1}, y = {0, 0, 1, 1}, z = {-5, -5, -5, -5};
		assertTrue(SkyCanvas.facesOrigin(x, y, z));
		float[] x2 = {0, 0, -1, 1};
		assertFalse(SkyCanvas.facesOrigin(x2, y, z));
	}

	@Test
	void yawFollowsMinecraft() {
		Recorder r = new Recorder();
		r.sky(0, 0, 10, 0); // south: +z
		r.sky(90, 0, 10, 0); // west: -x
		r.sky(0, 90, 10, 0); // straight up
		r.sky(180, 30, 10, 0); // north, raised
		float[][] byInput = new float[4][];
		// The recorder may have reversed the order; find each point by what makes it unique
		for (float[] p : r.corners) {
			if (p[2] > 9) byInput[0] = p;
			else if (p[0] < -9) byInput[1] = p;
			else if (p[1] > 9) byInput[2] = p;
			else byInput[3] = p;
		}
		for (float[] p : byInput) {
			assertTrue(p != null);
		}
		assertEquals(5, byInput[3][1], 1e-4f);
		assertTrue(byInput[3][2] < 0);
	}

	@Test
	void frameIsUprightAndSquare() {
		SkyCanvas.SkyFrame f = new SkyCanvas.SkyFrame().aim(37, 25);
		assertEquals(1, f.dx * f.dx + f.dy * f.dy + f.dz * f.dz, 1e-5f);
		assertEquals(1, f.ux * f.ux + f.uy * f.uy + f.uz * f.uz, 1e-5f);
		assertEquals(1, f.vx * f.vx + f.vy * f.vy + f.vz * f.vz, 1e-5f);
		assertEquals(0, f.dx * f.ux + f.dy * f.uy + f.dz * f.uz, 1e-5f);
		assertEquals(0, f.dx * f.vx + f.dy * f.vy + f.dz * f.vz, 1e-5f);
		assertEquals(0, f.uy, 1e-6f);
		assertTrue(f.vy > 0);
		// Facing south, "right" on screen is west (-x)
		SkyCanvas.SkyFrame south = new SkyCanvas.SkyFrame().aim(0, 0);
		assertEquals(-1, south.ux, 1e-6f);
	}

	/** Keeps the colour of every corner it is handed, with the height it sits at. */
	private static final class Coloured extends SkyCanvas {
		final List<float[]> corners = new ArrayList<>();
		final List<Integer> colours = new ArrayList<>();

		@Override
		protected void put(float x, float y, float z, int argb) {
			corners.add(new float[] {x, y, z});
			colours.add(argb);
		}

		/** The alpha of the four corners of a quad drawn at this height. */
		int alphaAt(float elevDeg) {
			int from = colours.size();
			for (int i = 0; i < 4; i++) {
				sky(i * 10, elevDeg, 10, 0xFF123456);
			}
			assertEquals(from + 4, colours.size());
			int alpha = colours.get(from) >>> 24;
			for (int i = from; i < from + 4; i++) {
				assertEquals(alpha, colours.get(i) >>> 24, "one quad, one alpha");
				assertEquals(0x123456, colours.get(i) & 0xFFFFFF, "the colour itself is not touched");
			}
			return alpha;
		}
	}

	@Test
	void shapesFadeOutBelowTheHorizonOnlyWhenAskedTo() {
		Coloured c = new Coloured();
		c.fadeBelowHorizon(3);
		assertEquals(255, c.alphaAt(5), "above the horizon nothing fades");
		assertEquals(255, c.alphaAt(0), "on the horizon neither");
		int half = c.alphaAt(-1.5f);
		assertTrue(half > 100 && half < 155, "half way down the fade: " + half);
		assertEquals(0, c.alphaAt(-3), "gone at the end of the fade");
		assertEquals(0, c.alphaAt(-20), "and below it");
		c.fadeBelowHorizon(0);
		assertEquals(255, c.alphaAt(-20), "no fade asked for: the quad is left alone");
	}

	@Test
	void pointsSitWhereTheFrameSaysTheyDo() {
		// Facing south: right on the screen is -x, up is +y, ahead is +z
		Coloured c = new Coloured();
		SkyCanvas.SkyFrame south = new SkyCanvas.SkyFrame().aim(0, 0);
		for (int i = 0; i < 4; i++) {
			c.at(south, 0.5f, 0.25f, 4, 0);
		}
		float len = (float) Math.sqrt(0.5 * 0.5 + 0.25 * 0.25 + 1);
		float[] p = c.corners.get(0);
		assertEquals(-0.5f / len * 4, p[0], 1e-4f);
		assertEquals(0.25f / len * 4, p[1], 1e-4f);
		assertEquals(1f / len * 4, p[2], 1e-4f);
		// Facing west: right on the screen is -z (north), ahead is -x
		Coloured w = new Coloured();
		SkyCanvas.SkyFrame west = new SkyCanvas.SkyFrame().aim(90, 0);
		for (int i = 0; i < 4; i++) {
			w.at(west, 0.5f, 0.25f, 4, 0);
		}
		float[] q = w.corners.get(0);
		assertEquals(-1f / len * 4, q[0], 1e-4f);
		assertEquals(0.25f / len * 4, q[1], 1e-4f);
		assertEquals(-0.5f / len * 4, q[2], 1e-4f);
	}

	@Test
	void pointsLandOnTheSphere() {
		Recorder r = new Recorder();
		SkyCanvas.SkyFrame f = new SkyCanvas.SkyFrame().aim(-60, 40);
		r.at(f, 0.3f, -0.2f, 50, 0);
		r.at(f, 0, 0.1f, 50, 0);
		r.at(f, -0.2f, 0, 50, 0);
		r.at(f, 0, 0, 50, 0);
		for (float[] p : r.corners) {
			assertEquals(50, Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]), 1e-3);
		}
	}
}
