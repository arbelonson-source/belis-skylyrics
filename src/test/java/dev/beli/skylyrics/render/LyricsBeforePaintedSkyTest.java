package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyEffect;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The painted skies (Synthwave, Moonlight) sit just short of the render-distance fog and are drawn before the lyrics, so
 * whatever of the lyrics lies past them is lost: behind the depth the sky leaves, or behind blocks the sky paints over.
 * The lyrics in the Sky hung at up to 0.8 of the render distance, nearly as far as the sky itself; their rows are flat,
 * so the ends lie farther still. At render distance 5 the sun, its halo and the ends of the rows covered them, and at 2
 * the whole sky was nearer than the lyrics (25.2 against 25.6 blocks).
 */
class LyricsBeforePaintedSkyTest {
	private static final int[] RENDER_DISTANCES = {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 16, 24, 32};
	/** LyricsRenderer.ROW_H: one row of the lyrics in text units. */
	private static final float ROW_H = 10;
	/** LyricsRenderer.ARC_SIDE: an Arc line sits up to this many degrees to one side of where you look. */
	private static final float ARC_SIDE = 6;
	/** The bigger current word grows by 14%; the glow reaches about 3 text units past the letters. */
	private static final float BIG_WORD = 1.14f, GLOW_UNITS = 3;
	/** The line being sung and its neighbours, each a row or two: rows above and below the middle of the block. */
	private static final float ROWS = 4;

	@Test
	void nothingEitherPaintedSkyDrawsIsNearerThanItsNearestLayer() throws Exception {
		for (int rd : RENDER_DISTANCES) {
			for (SkyEffect effect : new SkyEffect[] {SkyEffect.SYNTHWAVE, SkyEffect.MOONLIGHT}) {
				float far = SkyEffects.farRadius(rd);
				float nearest = nearestPoint(drawPainted(effect, far));
				assertTrue(nearest >= SkyEffects.paintedNearest(rd),
						effect + " at render distance " + rd + " comes as near as " + nearest + " blocks, nearer than "
								+ SkyEffects.paintedNearest(rd));
			}
		}
	}

	@Test
	void theDefaultLinesStayInFrontAtRenderDistanceFive() {
		float farthest = farthestCorner(false, 64, 5, 1, 70, 16 / 9.0);
		assertTrue(farthest < SkyEffects.paintedNearest(5),
				"the default lines reach " + farthest + " blocks, past the painted sky's nearest layer at " + SkyEffects.paintedNearest(5));
	}

	@Test
	void everyCornerOfTheLyricsStaysInFrontOfThePaintedSky() {
		for (int rd : RENDER_DISTANCES) {
			for (float distance : new float[] {8, 64, 160}) {
				for (float size : new float[] {0.3f, 1, 2, 3}) {
					for (double fov : new double[] {30, 70, 110}) {
						for (double aspect : new double[] {4 / 3.0, 16 / 9.0, 21 / 9.0}) {
							for (boolean arc : new boolean[] {false, true}) {
								float farthest = farthestCorner(arc, distance, rd, size, fov, aspect);
								assertTrue(farthest < SkyEffects.paintedNearest(rd),
										(arc ? "Arc" : "Lines") + " lyrics at Distance " + distance + ", Size " + size + ", FOV " + fov
												+ ", aspect " + aspect + " reach " + farthest + " blocks at render distance " + rd
												+ ", past the painted sky's nearest layer at " + SkyEffects.paintedNearest(rd));
							}
						}
					}
				}
			}
		}
	}

	@Test
	void atNineRenderDistanceAndMoreTheDefaultDistanceIsKept() {
		for (int rd : RENDER_DISTANCES) {
			if (rd < 8) {
				continue;
			}
			// At 8 an Arc line as wide as the view allows would reach the constellations: it comes in a few blocks
			for (boolean arc : rd == 8 ? new boolean[] {false} : new boolean[] {false, true}) {
				assertEquals(64, distance(arc, 64, rd, 1, 70, 16 / 9.0), 1e-4f, (arc ? "Arc" : "Lines") + " at render distance " + rd);
			}
		}
	}

	/** The distance the renderer hangs the lyrics at, worked out the way its render loop does. */
	private static float distance(boolean arc, float setting, int rd, float size, double fov, double aspect) {
		float angular = LyricsRenderer.rowAngle(true, size);
		double tanHalf = LyricsRenderer.tanHalfWide(fov, (int) Math.round(1080 * aspect), 1080);
		float wrap = arc ? 230 : LyricsRenderer.rowWidth(230, angular, tanHalf);
		return LyricsRenderer.skyDistance(setting, rd, LyricsRenderer.reach(LyricsRenderer.halfWide(arc, wrap, angular, tanHalf), angular));
	}

	/** How far from the camera the lyrics' farthest drawn point can be: the widest row's ends, some rows up or down. */
	private static float farthestCorner(boolean arc, float setting, int rd, float size, double fov, double aspect) {
		float angular = LyricsRenderer.rowAngle(true, size);
		double tan = Math.tan(Math.toRadians(angular));
		double tanHalf = LyricsRenderer.tanHalfWide(fov, (int) Math.round(1080 * aspect), 1080);
		double halfWide;
		if (arc) {
			// An Arc line fits in FIT_SHARE of the room beside where it sits
			halfWide = LyricsRenderer.FIT_SHARE * Math.tan(Math.atan(tanHalf) - Math.toRadians(ARC_SIDE)) * BIG_WORD
					+ GLOW_UNITS * tan / ROW_H;
		} else {
			float wrap = LyricsRenderer.rowWidth(230, angular, tanHalf);
			halfWide = (wrap / 2 * BIG_WORD + GLOW_UNITS) * tan / ROW_H;
		}
		double halfHigh = ROWS * tan;
		float d = distance(arc, setting, rd, size, fov, aspect);
		return (float) (d * Math.sqrt(1 + halfWide * halfWide + halfHigh * halfHigh));
	}

	/** The nearest any drawn triangle comes to the camera: each triangle's plane is no nearer than the triangle. */
	private static float nearestPoint(List<float[]> corners) {
		double nearest = Double.MAX_VALUE;
		for (int q = 0; q + 3 < corners.size(); q += 4) {
			nearest = Math.min(nearest, planeDistance(corners.get(q), corners.get(q + 1), corners.get(q + 2)));
			nearest = Math.min(nearest, planeDistance(corners.get(q), corners.get(q + 2), corners.get(q + 3)));
		}
		return (float) nearest;
	}

	private static double planeDistance(float[] a, float[] b, float[] c) {
		double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
		double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (n < 1e-9) {
			// Two corners in one place (a fan's tip): the nearest corner stands in
			return Math.min(length(a), Math.min(length(b), length(c)));
		}
		return Math.abs(nx * a[0] + ny * a[1] + nz * a[2]) / n;
	}

	private static double length(float[] p) {
		return Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
	}

	/** Every corner the real SkyEffects.drawPainted sends, constellations on and the sky fully shown. */
	private static List<float[]> drawPainted(SkyEffect effect, float far) throws Exception {
		SkyEffects e = new SkyEffects(null, null, null, null);
		set(e, "effect", effect);
		set(e, "starPatterns", true);
		set(e, "patternShow", 1f);
		set(e, "amount", 1f);
		set(e, "far", far);
		set(e, "baseYaw", 180f);
		set(e, "pixel", 0.002f);
		set(e, "clock", 12.0);
		// The view this frame was "already worked out": the fresh one, which counts the whole sky as on screen
		set(e, "viewFrame", ScreenLayer.frames());
		List<float[]> out = new ArrayList<>();
		Method m = SkyEffects.class.getDeclaredMethod("drawPainted", PoseStack.Pose.class, VertexConsumer.class);
		m.setAccessible(true);
		m.invoke(e, new PoseStack().last(), recorder(out));
		assertTrue(out.size() > 1000, "the painted sky drew only " + out.size() + " corners");
		return out;
	}

	private static void set(Object o, String name, Object v) throws Exception {
		Field f = SkyEffects.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, v);
	}

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
}
