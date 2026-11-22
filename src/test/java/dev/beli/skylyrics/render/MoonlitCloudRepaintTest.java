package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Moonlight clouds drift, so their band is repainted a bite a frame. A bite used to work out each point's whole
 * colour again (an arcsine, an arctangent, an arccosine, a square root and up to three exponentials, the clouds' noise
 * on top), though only the noise moves with the drift. It must keep painting exactly the same clouds.
 */
class MoonlitCloudRepaintTest {
	private static final float RADIUS = 80, DEG = (float) (Math.PI / 180);

	/** The corners of one layer of the sky (its distance from the camera), as bits so that nothing may differ at all. */
	private static final class Layer extends SkyCanvas {
		final List<int[]> corners = new ArrayList<>();
		private final float want;

		Layer(float want) {
			this.want = want;
		}

		@Override
		protected void put(float x, float y, float z, int argb) {
			if (Math.abs(Math.sqrt(x * x + y * y + z * z) - want) < 1e-2) {
				corners.add(new int[] {Float.floatToIntBits(x), Float.floatToIntBits(y), Float.floatToIntBits(z), argb});
			}
		}
	}

	/** The cloud band as it was repainted before: each point's whole colour through {@link MoonlitSky#background}. */
	private static final class OldClouds {
		final SkyMesh mesh = SkyMesh.dome(MoonlitSky.CLOUD_ROWS, MoonlitSky.CLOUD_COLUMNS, MoonlitSky::background);
		final DriftRepaint plan = new DriftRepaint(MoonlitSky.REPAINT_STEP, MoonlitSky.REPAINT_PARTS);

		void frame(float baseYaw, double time, SkyView view) {
			float cos = (float) Math.cos(baseYaw * DEG), sin = (float) Math.sin(baseYaw * DEG);
			int part = plan.advance((float) ((time * MoonlitSky.CLOUD_DRIFT) % 360), mesh.anyVisible(cos, sin, view));
			if (part == DriftRepaint.NONE) {
				return;
			}
			float drift = plan.target();
			int rows = MoonlitSky.CLOUD_ROWS.length, parts = MoonlitSky.REPAINT_PARTS;
			int from = part == DriftRepaint.ALL ? 0 : part * rows / parts, to = part == DriftRepaint.ALL ? rows : (part + 1) * rows / parts;
			for (int k = from * mesh.cols; k < to * mesh.cols; k++) {
				mesh.rgb[k] = MoonlitSky.background(mesh.x[k], mesh.y[k], mesh.z[k], drift);
			}
		}

		List<int[]> drawn(float baseYaw, float level, SkyView view) {
			Layer layer = new Layer(RADIUS * MoonlitSky.CLOUDS);
			layer.fadeBelowHorizon(MoonlitSky.HORIZON_FADE);
			mesh.draw(layer, (float) Math.cos(baseYaw * DEG), (float) Math.sin(baseYaw * DEG), RADIUS * MoonlitSky.CLOUDS, level, view);
			return layer.corners;
		}
	}

	private static SkyView looking(float yawDeg, float pitchDeg) {
		float tanV = (float) Math.tan(Math.toRadians(35)), tanH = tanV * 16 / 9;
		double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
		return new SkyView().set((float) (-Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (Math.cos(y) * Math.cos(p)), tanH, tanV);
	}

	@Test
	void repaintsWorkOutNoWholeColourAndPaintTheSameClouds() {
		MoonlitSky sky = new MoonlitSky();
		OldClouds old = new OldClouds();
		float baseYaw = 20, level = 1;
		double time = 40;
		int frames = 600, now = 0, before = 0, changed = 0;
		int[] last = sky.clouds.rgb.clone();
		for (int f = 0; f < frames; f++) {
			// Mostly looking at the clouds; for a while straight up, where none is on screen, then back
			SkyView view = f >= 200 && f < 260 ? looking(0, 90) : f % 2 == 0 ? SkyView.ALL : looking(200, 5);
			int start = MoonlitSky.wholeColours;
			Layer layer = new Layer(RADIUS * MoonlitSky.CLOUDS);
			sky.draw(layer, RADIUS, baseYaw, level, time, 0.001f, view);
			now += MoonlitSky.wholeColours - start;
			start = MoonlitSky.wholeColours;
			old.frame(baseYaw, time, view);
			before += MoonlitSky.wholeColours - start;
			assertArrayEquals(old.mesh.rgb, sky.clouds.rgb, "cloud colours at frame " + f);
			List<int[]> drawn = old.drawn(baseYaw, level, view);
			assertEquals(drawn.size(), layer.corners.size(), "cloud corners at frame " + f);
			for (int k = 0; k < drawn.size(); k++) {
				assertArrayEquals(drawn.get(k), layer.corners.get(k), "cloud corner " + k + " at frame " + f);
			}
			changed += java.util.Arrays.equals(last, sky.clouds.rgb) ? 0 : 1;
			last = sky.clouds.rgb.clone();
			time += 1 / 60.0;
		}
		System.out.printf("Moonlight clouds over %d frames: whole colours worked out before %d (%.1f a frame), now %d; %d frames repainted%n",
				frames, before, before / (float) frames, now, changed);
		assertTrue(changed > 20, "the clouds must have drifted and been repainted: " + changed);
		assertTrue(before > 0, "the old repaint works out whole colours");
		assertEquals(0, now, "a repaint must work out no whole colour, only the clouds' noise");
	}
}
