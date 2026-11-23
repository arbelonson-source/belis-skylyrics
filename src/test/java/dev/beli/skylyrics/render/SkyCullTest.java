package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Culling must drop vertices nobody can see and change nothing else. */
class SkyCullTest {
	private static final float TAN_V = (float) Math.tan(Math.toRadians(35)), TAN_H = TAN_V * 16 / 9;
	private static final float BASE_YAW = 20, RADIUS = 80;

	/** Keeps every quad handed on, as twelve numbers plus the colour. */
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

		int vertices() {
			return quads.size() * 4;
		}
	}

	private static SkyView looking(float yawDeg, float pitchDeg) {
		double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
		return new SkyView().set((float) (-Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (Math.cos(y) * Math.cos(p)), TAN_H, TAN_V);
	}

	private interface Sky {
		void draw(Recorder r, SkyView view);
	}

	private static final Sky MOONLIT = (r, v) -> new MoonlitSky().draw(r, RADIUS, BASE_YAW, 1, 3, 0.001f, v);
	private static final Sky SYNTHWAVE = (r, v) -> new SynthwaveSky().draw(r, RADIUS, BASE_YAW, 1, 3, 0.001f, v);
	private static final Sky GALAXY = (r, v) -> new GalaxySky().draw(r, RADIUS, BASE_YAW, 1, 3, v);
	private static final Sky COMET = (r, v) -> new CometSky().draw(r, RADIUS, BASE_YAW, 1, 3, v);
	private static final Sky ECLIPSE = (r, v) -> {
		new EclipseSky().draw(r, RADIUS, BASE_YAW, 1, 3, v);
		new EclipseSky().drawDisc(r, RADIUS, BASE_YAW, 1, v);
	};

	private static Recorder run(Sky sky, SkyView view) {
		Recorder r = new Recorder();
		sky.draw(r, view);
		return r;
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

	@Test
	void whatIsKeptIsUnchangedAndEverythingOnScreenIsKept() {
		Sky[] skies = {MOONLIT, SYNTHWAVE, GALAXY, COMET, ECLIPSE};
		float[][] views = {{0, 0}, {BASE_YAW, 0}, {-90, 10}, {180, 30}, {BASE_YAW - 32, 25}, {45, -20}, {0, 90}, {0, -90},
			{150, 60}, {-170, 5}, {BASE_YAW + 26, 6}, {BASE_YAW - 38, 46}, {BASE_YAW + 30, 18}, {BASE_YAW + 3, 13}};
		for (Sky sky : skies) {
			Recorder all = run(sky, SkyView.ALL);
			assertTrue(all.vertices() > 100);
			for (float[] v : views) {
				for (float dy = -12; dy <= 12; dy += 12) {
					float yaw = v[0] + dy, pitch = Math.max(-90, Math.min(90, v[1] + dy / 2));
					Recorder cut = run(sky, looking(yaw, pitch));
					// Same corners, same order, just fewer
					int next = 0;
					for (String q : cut.quads) {
						while (next < all.quads.size() && !all.quads.get(next).equals(q)) {
							next++;
						}
						assertTrue(next < all.quads.size(), "a quad changed or moved at view " + yaw + "/" + pitch);
						next++;
					}
					// Nothing that is on screen was dropped (looked up in a set: a list's contains made this check take seconds)
					Set<String> drawn = new HashSet<>(cut.quads);
					for (int i = 0; i < all.quads.size(); i++) {
						if (onScreen(all.corners.get(i), yaw, pitch)) {
							assertTrue(drawn.contains(all.quads.get(i)), "dropped a visible quad at view " + yaw + "/" + pitch);
						}
					}
				}
			}
		}
	}

	@Test
	void cullingDropsMostOfMoonlitLookingAtTheHorizon() {
		StringBuilder report = new StringBuilder("sky vertices drawn per frame (16:9, 70 degree vertical field of view)\n");
		String[] names = {"Moonlit", "Synthwave", "Galaxy", "Comet", "Eclipse"};
		Sky[] skies = {MOONLIT, SYNTHWAVE, GALAXY, COMET, ECLIPSE};
		float[][] views = {{BASE_YAW, 0}, {BASE_YAW, 90}, {BASE_YAW + MoonlitSky.MOON_YAW, MoonlitSky.MOON_ELEV}, {BASE_YAW + 180, 0}};
		String[] viewNames = {"horizon (lyrics direction)", "straight up", "at the moon", "behind the lyrics"};
		int[][] counts = new int[skies.length][views.length];
		for (int s = 0; s < skies.length; s++) {
			int before = run(skies[s], SkyView.ALL).vertices();
			report.append(names[s]).append(": before ").append(before);
			for (int v = 0; v < views.length; v++) {
				counts[s][v] = run(skies[s], looking(views[v][0], views[v][1])).vertices();
				report.append("; ").append(viewNames[v]).append(" ").append(counts[s][v]);
			}
			report.append('\n');
		}
		int before = run(MOONLIT, SkyView.ALL).vertices();
		// Behind the lyrics (nothing but dome, clouds and stars on the far side) most of the sky goes
		assertTrue(counts[0][3] < before * 0.25, report.toString());
		// At the horizon the moon and its glow stay (they are in view), the rest of the dome and clouds thin out
		// (about 72 % today; the margin must not creep up unnoticed)
		assertTrue(counts[0][0] < before * 0.75, report.toString());
		assertTrue(counts[0][1] < before, report.toString());
		// Looking behind the lyrics drops more than looking at them
		assertTrue(counts[0][3] < counts[0][0], report.toString());
		// Culling only ever takes shapes away
		for (int s = 0; s < skies.length; s++) {
			for (int v = 0; v < views.length; v++) {
				assertTrue(counts[s][v] <= run(skies[s], SkyView.ALL).vertices(), names[s] + " " + viewNames[v] + " drew more than the full sky");
			}
		}
	}
}
