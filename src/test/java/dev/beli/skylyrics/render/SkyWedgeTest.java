package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Polar meshes and the eclipse corona drop segments the camera can't see, and never one it can. */
class SkyWedgeTest {
	private static final float TAN_V = (float) Math.tan(Math.toRadians(35)), TAN_H = TAN_V * 16 / 9;
	private static final float BASE_YAW = 20, RADIUS = 80;

	private static final class Recorder extends SkyCanvas {
		final List<String> quads = new ArrayList<>();
		final List<float[]> corners = new ArrayList<>();
		private final StringBuilder quad = new StringBuilder();
		private final float[] at = new float[12];
		private int n;

		@Override
		protected void put(float x, float y, float z, int argb) {
			at[n * 3] = x;
			at[n * 3 + 1] = y;
			at[n * 3 + 2] = z;
			quad.append(x).append(',').append(y).append(',').append(z).append(',').append(argb).append(';');
			if (++n == 4) {
				quads.add(quad.toString());
				corners.add(at.clone());
				quad.setLength(0);
				n = 0;
			}
		}
	}

	private static SkyView looking(float yawDeg, float pitchDeg) {
		double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
		return new SkyView().set((float) (-Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (Math.cos(y) * Math.cos(p)), TAN_H, TAN_V);
	}

	/** True when some corner of the quad, seen from the origin, lands inside the screen (no margin). */
	private static boolean onScreen(float[] q, float yawDeg, float pitchDeg) {
		double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
		double fx = -Math.sin(y) * Math.cos(p), fy = Math.sin(p), fz = Math.cos(y) * Math.cos(p);
		double rx = Math.cos(y), rz = Math.sin(y);
		double ux = fy * rz, uy = fz * rx - fx * rz, uz = -fy * rx;
		for (int i = 0; i < 4; i++) {
			double x = q[i * 3], yy = q[i * 3 + 1], z = q[i * 3 + 2];
			double depth = x * fx + yy * fy + z * fz;
			if (depth <= 0) {
				continue;
			}
			double across = x * rx + z * rz, up = x * ux + yy * uy + z * uz;
			if (Math.abs(across) / depth <= TAN_H && Math.abs(up) / depth <= TAN_V) {
				return true;
			}
		}
		return false;
	}

	private interface Shape {
		/** Draws the shape; only quads inside {@code region} count (so other layers of the same sky don't). */
		void draw(Recorder r, SkyView view);

		boolean inRegion(float[] q);
	}

	private static Shape swirl() {
		SkyCanvas.SkyFrame f = new SkyCanvas.SkyFrame().aim(0, 62);
		float[] rings = new float[58];
		for (int i = 0; i < rings.length; i++) {
			rings[i] = (float) Math.tan(Math.toRadians(i * 0.7f));
		}
		SkyMesh mesh = SkyMesh.polar(f, rings, 40, (x, y, z) -> 0x302060);
		float cos = (float) Math.cos(Math.toRadians(BASE_YAW)), sin = (float) Math.sin(Math.toRadians(BASE_YAW));
		return new Shape() {
			@Override
			public void draw(Recorder r, SkyView view) {
				mesh.draw(r, cos, sin, RADIUS, 1, view);
			}

			@Override
			public boolean inRegion(float[] q) {
				return true;
			}
		};
	}

	private static Shape corona() {
		SkyCanvas.SkyFrame sun = new SkyCanvas.SkyFrame().aim(BASE_YAW + EclipseSky.SUN_YAW, EclipseSky.SUN_ELEV);
		float reach = (float) Math.atan(7.2f * Math.tan(Math.toRadians(EclipseSky.SUN_RADIUS))) + 0.02f;
		return new Shape() {
			@Override
			public void draw(Recorder r, SkyView view) {
				new EclipseSky().draw(r, RADIUS, BASE_YAW, 1, 3, view);
			}

			@Override
			public boolean inRegion(float[] q) {
				// Only the corona's own layer: the milky way sits one layer out
				float l = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
				return Math.abs(l - RADIUS * EclipseSky.CORONA) < 1e-2 && (q[0] * sun.dx + q[1] * sun.dy + q[2] * sun.dz) / l > Math.cos(reach);
			}
		};
	}

	private static final float[][] VIEWS = {{0, 0}, {BASE_YAW, 0}, {-90, 10}, {180, 30}, {45, -20}, {0, 90}, {0, -90}, {150, 60},
		{-170, 5}, {BASE_YAW + 26, 6}, {BASE_YAW + 3, 13}, {BASE_YAW, 62}, {BASE_YAW + 30, 40}, {BASE_YAW - 40, 62}};

	private static void check(Shape shape, boolean[] sawPartial) {
		Recorder all = new Recorder();
		shape.draw(all, SkyView.ALL);
		int whole = 0;
		for (float[] q : all.corners) {
			whole += shape.inRegion(q) ? 1 : 0;
		}
		assertTrue(whole > 500);
		for (float[] v : VIEWS) {
			for (float d = -40; d <= 40; d += 5) {
				float yaw = v[0] + d, pitch = Math.max(-90, Math.min(90, v[1] + d / 2));
				Recorder cut = new Recorder();
				shape.draw(cut, looking(yaw, pitch));
				int next = 0, kept = 0;
				for (int i = 0; i < cut.quads.size(); i++) {
					while (next < all.quads.size() && !all.quads.get(next).equals(cut.quads.get(i))) {
						next++;
					}
					assertTrue(next < all.quads.size(), "a quad changed at view " + yaw + "/" + pitch);
					next++;
					kept += shape.inRegion(cut.corners.get(i)) ? 1 : 0;
				}
				for (int i = 0; i < all.quads.size(); i++) {
					if (shape.inRegion(all.corners.get(i)) && onScreen(all.corners.get(i), yaw, pitch)) {
						assertTrue(cut.quads.contains(all.quads.get(i)), "dropped a visible quad at view " + yaw + "/" + pitch);
					}
				}
				sawPartial[0] |= kept > 0 && kept < whole;
			}
		}
	}

	@Test
	void swirlLosesSegmentsOutOfViewButKeepsEverythingOnScreen() {
		boolean[] partial = new boolean[1];
		check(swirl(), partial);
		assertTrue(partial[0], "some view should draw part of the swirl, not all or nothing");
	}

	@Test
	void coronaLosesSegmentsOutOfViewButKeepsEverythingOnScreen() {
		boolean[] partial = new boolean[1];
		check(corona(), partial);
		assertTrue(partial[0], "some view should draw part of the corona, not all or nothing");
	}

	@Test
	void lookingStraightUpOrAtTheHorizonLosesNothingWithinReach() {
		Recorder all = new Recorder(), up = new Recorder();
		Shape s = swirl();
		s.draw(all, SkyView.ALL);
		s.draw(up, looking(BASE_YAW, 90));
		// The swirl is centred 62 degrees up, so straight up it is well inside the margin: every segment shows
		assertEquals(all.quads.size(), up.quads.size());
	}
}
