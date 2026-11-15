package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The aurora, nebula and constellations leave out what the camera can't see, keep everything it can, and the width of painted lines follows the live view. */
class AuroraCullTest {
	private static final float TAN_V = (float) Math.tan(Math.toRadians(35)), TAN_H = TAN_V * 16 / 9;
	private static final float DEG = (float) (Math.PI / 180);

	private static SkyView looking(float yawDeg, float pitchDeg) {
		double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
		return new SkyView().set((float) (-Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (Math.cos(y) * Math.cos(p)), TAN_H, TAN_V);
	}

	/** Whether a direction lands inside the screen itself (no margin) for a camera at Minecraft yaw/pitch. */
	private static boolean onScreen(double x, double y, double z, float yawDeg, float pitchDeg) {
		double yw = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
		double fx = -Math.sin(yw) * Math.cos(p), fy = Math.sin(p), fz = Math.cos(yw) * Math.cos(p);
		double rx = Math.cos(yw), rz = Math.sin(yw);
		double ux = fy * rz, uy = fz * rx - fx * rz, uz = -fy * rx;
		double depth = x * fx + y * fy + z * fz;
		if (depth <= 0) {
			return false;
		}
		double across = x * rx + z * rz, up = x * ux + y * uy + z * uz;
		return Math.abs(across) / depth <= TAN_H && Math.abs(up) / depth <= TAN_V;
	}

	/** The direction the aurora and nebula code draws a yaw and height at: (sin yaw, sin elev, -cos yaw) scaled by the height's cosine. */
	private static double[] drawn(float yaw, float elev) {
		double h = Math.cos(elev);
		return new double[] {h * Math.sin(yaw), Math.sin(elev), -h * Math.cos(yaw)};
	}

	private static boolean anyOnScreen(float yawDeg, float pitchDeg, float[][] yawElev) {
		for (float[] p : yawElev) {
			double[] d = drawn(p[0], p[1]);
			if (onScreen(d[0], d[1], d[2], yawDeg, pitchDeg)) {
				return true;
			}
		}
		return false;
	}

	@Test
	void ribbonYawConventionStraightAheadIsVisibleBehindIsCulled() {
		// The curtain at yaw 0 is drawn at (0, ., -1): north, which is Minecraft yaw 180
		float low = 30 * DEG, high = 50 * DEG;
		assertTrue(ribbonAt(looking(180, 20), 0, low, high), "facing the curtain");
		assertFalse(ribbonAt(looking(0, 20), 0, low, high), "turned away from it (it is behind)");
		// The curtain at yaw 90 degrees (+x, east) is Minecraft yaw 270 (-sin 270 = +1)
		assertTrue(ribbonAt(looking(270, 20), 90 * DEG, low, high));
		assertFalse(ribbonAt(looking(90, 20), 90 * DEG, low, high));
		// Not the mirrored or unturned reading: looking south must not see a curtain in the north
		assertFalse(ribbonAt(looking(0, 20), 180 * DEG, low, high) == ribbonAt(looking(0, 20), 0, low, high));
	}

	private static boolean ribbonAt(SkyView view, float yaw, float low, float high) {
		return SkyEffects.ribbonVisible(view, yaw - 0.01f, yaw + 0.01f, low, high);
	}

	@Test
	void ribbonEdgesKeepTheMargin() {
		// Look at north (yaw 180), level; the screen reaches atan(TAN_H) to each side, the margin adds 12 degrees
		float half = (float) Math.toDegrees(Math.atan(TAN_H));
		float low = 0, high = 5 * DEG;
		float inside = (half + SkyView.MARGIN - 1) * DEG, outside = (half + SkyView.MARGIN + 25) * DEG;
		SkyView view = looking(180, 0);
		assertTrue(ribbonAt(view, inside, low, high), "just within the margin stays");
		assertTrue(ribbonAt(view, -inside, low, high));
		assertFalse(ribbonAt(view, outside, low, high), "well past the margin goes");
		assertFalse(ribbonAt(view, -outside, low, high));
	}

	@Test
	void everyRibbonSegmentWithACornerOnScreenIsKept() {
		int seen = 0, kept = 0, total = 0;
		for (float t : new float[] {0, 37.5f, 400}) {
			for (int k = 0; k < 3; k++) {
				for (float yaw = 0; yaw < 360; yaw += 15) {
					for (float pitch = -60; pitch <= 80; pitch += 20) {
						SkyView view = looking(yaw, pitch);
						for (int i = 1; i <= 80; i++) {
							float u0 = (i - 1) / 80f, u1 = i / 80f;
							float y0 = SkyEffects.ribbonYaw(t, k, u0), y1 = SkyEffects.ribbonYaw(t, k, u1);
							float b0 = SkyEffects.ribbonBase(t, k, u0), b1 = SkyEffects.ribbonBase(t, k, u1);
							float t0 = SkyEffects.ribbonTop(t, k, u0, b0), t1 = SkyEffects.ribbonTop(t, k, u1, b1);
							boolean visible = SkyEffects.ribbonVisible(view, y0, y1, Math.min(b0, b1), Math.max(t0, t1));
							total++;
							kept += visible ? 1 : 0;
							if (anyOnScreen(yaw, pitch, new float[][] {{y0, b0}, {y1, b1}, {y1, t1}, {y0, t0}})) {
								seen++;
								assertTrue(visible, "dropped an on-screen segment " + i + " of curtain " + k + " at view " + yaw + "/" + pitch);
							}
						}
					}
				}
			}
		}
		assertTrue(seen > 100, "the test saw some");
		assertTrue(kept < total * 0.6, "culling must actually cut: kept " + kept + " of " + total);
	}

	@Test
	void cullingBeatsNothingLookingAtTheCurtain() {
		// Looking well to one side of the first curtain's middle, only part of its 80 segments can be seen
		int shown = 0;
		SkyView view = looking(180 + 80 + (float) Math.toDegrees(SkyEffects.ribbonYaw(0, 0, 0.5f)), 30);
		for (int i = 1; i <= 80; i++) {
			float y0 = SkyEffects.ribbonYaw(0, 0, (i - 1) / 80f), y1 = SkyEffects.ribbonYaw(0, 0, i / 80f);
			shown += SkyEffects.ribbonVisible(view, y0, y1, 20 * DEG, 60 * DEG) ? 1 : 0;
		}
		assertTrue(shown > 5 && shown < 70, "shown " + shown);
	}

	@Test
	void everyCloudWithAnEdgePointOnScreenIsKept() {
		float dist = 100;
		int seen = 0;
		for (float t : new float[] {0, 55, 900}) {
			for (int k = 0; k < 7; k++) {
				float yaw = SkyEffects.blobYaw(t, k), elev = SkyEffects.blobElev(t, k);
				float c = (float) Math.cos(elev);
				float dx = c * (float) Math.sin(yaw), dy = (float) Math.sin(elev), dz = -c * (float) Math.cos(yaw);
				float ux = -dz, uz = dx, ul = (float) Math.sqrt(ux * ux + uz * uz);
				ux /= ul;
				uz /= ul;
				float vx = dy * uz, vy = dz * ux - dx * uz, vz = -dy * ux;
				float size = dist * (0.36f + 0.08f * (float) Math.sin(k * 1.9f)) * (1 + 0.06f * (float) Math.sin(t * 0.12f + k));
				for (float vyaw = 0; vyaw < 360; vyaw += 20) {
					for (float pitch = -60; pitch <= 80; pitch += 20) {
						boolean visible = SkyEffects.blobVisible(looking(vyaw, pitch), dx, dy, dz, size, dist);
						for (int a = 0; a < 360; a += 10) {
							double ca = Math.cos(Math.toRadians(a)), sa = Math.sin(Math.toRadians(a));
							// Even the widest wobble stays inside what is checked
							double ex = dx * dist + (ca * ux + sa * vx) * size * SkyEffects.BLOB_WOBBLE_MAX;
							double ey = dy * dist + sa * vy * size * SkyEffects.BLOB_WOBBLE_MAX;
							double ez = dz * dist + (ca * uz + sa * vz) * size * SkyEffects.BLOB_WOBBLE_MAX;
							if (onScreen(ex, ey, ez, vyaw, pitch)) {
								seen++;
								assertTrue(visible, "dropped cloud " + k + " at view " + vyaw + "/" + pitch);
							}
						}
					}
				}
			}
		}
		assertTrue(seen > 100);
		// Straight ahead of the north the first clouds are skipped when you look south
		assertFalse(SkyEffects.blobVisible(looking(0, 0), 0, 0.6f, -0.8f, 30, 100));
	}

	@Test
	void everyNebulaStarOnScreenIsKept() {
		int seen = 0, kept = 0, total = 0;
		for (float yaw = 0; yaw < 360; yaw += 15) {
			for (float pitch = -60; pitch <= 80; pitch += 20) {
				SkyView view = looking(yaw, pitch);
				for (int i = 0; i < 60; i++) {
					float sy = SkyEffects.dotYaw(i), se = SkyEffects.dotElev(i);
					boolean visible = SkyEffects.dotVisible(view, sy, se);
					total++;
					kept += visible ? 1 : 0;
					double s = 0.5 / 100, r = 1, h = Math.cos(se);
					double x = h * Math.sin(sy), y = Math.sin(se), z = -h * Math.cos(sy);
					double rx = Math.cos(sy) * s * r, rz = Math.sin(sy) * s * r;
					boolean corner = onScreen(x - rx, y - s, z - rz, yaw, pitch) || onScreen(x + rx, y - s, z + rz, yaw, pitch)
							|| onScreen(x + rx, y + s, z + rz, yaw, pitch) || onScreen(x - rx, y + s, z - rz, yaw, pitch);
					if (corner) {
						seen++;
						assertTrue(visible, "dropped star " + i + " at view " + yaw + "/" + pitch);
					}
				}
			}
		}
		assertTrue(seen > 50);
		assertTrue(kept < total * 0.7, "kept " + kept + " of " + total);
	}

	/** Keeps every quad's corners as text, to compare the culled draw with the whole. */
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

	private static Recorder constellations(SkyView view) {
		Recorder r = new Recorder();
		new Constellations().draw(r, 80, 20, 1, 3, 0.001f, view);
		return r;
	}

	@Test
	void constellationsKeepWhatIsSeenInTheSameOrderAndDropTheRest() {
		Recorder all = constellations(SkyView.ALL);
		Recorder old = new Recorder();
		new Constellations().draw(old, 80, 20, 1, 3, 0.001f);
		assertEquals(old.quads, all.quads, "the call without a view is the call with everything");
		boolean dropped = false;
		for (float yaw = 0; yaw < 360; yaw += 20) {
			for (float pitch = -40; pitch <= 70; pitch += 22) {
				Recorder cut = constellations(looking(yaw, pitch));
				int next = 0;
				for (String q : cut.quads) {
					while (next < all.quads.size() && !all.quads.get(next).equals(q)) {
						next++;
					}
					assertTrue(next < all.quads.size(), "a quad changed at view " + yaw + "/" + pitch);
					next++;
				}
				for (int i = 0; i < all.quads.size(); i++) {
					float[] q = all.corners.get(i);
					boolean seen = false;
					for (int c = 0; c < 4; c++) {
						seen |= onScreen(q[c * 3], q[c * 3 + 1], q[c * 3 + 2], yaw, pitch);
					}
					if (seen) {
						assertTrue(cut.quads.contains(all.quads.get(i)), "dropped a visible quad at view " + yaw + "/" + pitch);
					}
				}
				dropped |= cut.quads.size() < all.quads.size();
			}
		}
		assertTrue(dropped, "some view drops a pattern");
		// Looking at the "W" pattern (yaw 20 + 16) leaves out most of the others
		Recorder one = constellations(looking(36, 27));
		assertTrue(one.quads.size() < all.quads.size() * 0.8, one.quads.size() + " of " + all.quads.size());
	}

	@Test
	void aLineIsAsWideAsTheLiveViewSays() {
		int height = 1080;
		// The slider says 70 but a spyglass narrows the live view to about 8: lines must be about 9 times finer
		float slider = SkyEffects.pixelAngle(0, 70, height);
		float zoomed = SkyEffects.pixelAngle((float) Math.tan(Math.toRadians(8) / 2), 70, height);
		assertEquals(2 * Math.tan(Math.toRadians(35)) / height, slider, 1e-7, "no frame yet: the slider stands in");
		assertEquals(2 * Math.tan(Math.toRadians(4)) / height, zoomed, 1e-7, "spyglass");
		assertTrue(slider / zoomed > 8);
		// Speed or sprinting widens the view: lines are wider then, not left at the slider's width
		assertTrue(SkyEffects.pixelAngle((float) Math.tan(Math.toRadians(80) / 2), 70, height) > slider);
		assertEquals(2 * TAN_V / 1, SkyEffects.pixelAngle(TAN_V, 70, 0), 1e-6, "a zero height can't divide");
		assertEquals(slider, SkyEffects.pixelAngle(Float.NaN, 70, height), 1e-7, "a bad projection falls back");
	}

	@Test
	void lazyFollowDoesNothingInTheWorldLayout() {
		for (Layout layout : Layout.values()) {
			assertEquals(layout != Layout.WORLD, FollowAnchor.lazy(true, layout), layout.name());
			assertFalse(FollowAnchor.lazy(false, layout), layout.name());
		}
		// Left on from another layout, the World sparkles snap to the view like the words do
		assertEquals(Float.POSITIVE_INFINITY, FollowAnchor.speed(FollowAnchor.lazy(true, Layout.WORLD)));
	}
}
