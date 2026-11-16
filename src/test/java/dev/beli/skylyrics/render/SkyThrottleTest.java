package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The cloud repaint and the comet streamers are spread out or slowed down, and still follow the clock. */
class SkyThrottleTest {
	private static final float RADIUS = 80, BASE_YAW = 20;

	/** The corners of the quads at one distance from the camera (a layer), as positions and colours. */
	private static final class Layer extends SkyCanvas {
		final List<Integer> colours = new ArrayList<>();
		private final float want;

		Layer(float want) {
			this.want = want;
		}

		@Override
		protected void put(float x, float y, float z, int argb) {
			if (Math.abs(Math.sqrt(x * x + y * y + z * z) - want) < 1e-2) {
				colours.add(argb);
			}
		}
	}

	private static List<Integer> cloudsAt(MoonlitSky sky, float time) {
		Layer layer = new Layer(RADIUS * MoonlitSky.CLOUDS);
		sky.draw(layer, RADIUS, BASE_YAW, 1, time, 0.001f, SkyView.ALL);
		return layer.colours;
	}

	private static int differences(List<Integer> a, List<Integer> b) {
		assertEquals(a.size(), b.size());
		int n = 0;
		for (int i = 0; i < a.size(); i++) {
			n += a.get(i).equals(b.get(i)) ? 0 : 1;
		}
		return n;
	}

	@Test
	void cloudRepaintIsSpreadOverSeveralFrames() {
		MoonlitSky sky = new MoonlitSky();
		float t = 40;
		List<Integer> last = cloudsAt(sky, t);
		// What one whole repaint changes: the clouds a repaint-step of drift apart
		int one = differences(cloudsAt(new MoonlitSky(), t), cloudsAt(new MoonlitSky(), t + 0.135f));
		assertTrue(one > 20, "the clouds should visibly move in a step");
		int worst = 0, total = 0;
		for (int f = 1; f <= 120; f++) {
			List<Integer> now = cloudsAt(sky, t + f / 60f);
			int d = differences(last, now);
			worst = Math.max(worst, d);
			total += d;
			last = now;
		}
		assertTrue(total > 4 * one, "two seconds should move the clouds many steps");
		// No single frame repaints a whole step
		assertTrue(worst * 10 <= one * 7, "a frame changed " + worst + " corners; a whole repaint changes about " + one);
	}

	@Test
	void cloudsStillFollowTheClock() {
		MoonlitSky sky = new MoonlitSky();
		cloudsAt(sky, 40);
		List<Integer> end = null;
		for (int f = 1; f <= 600; f++) {
			end = cloudsAt(sky, 40 + f / 60f);
		}
		// Ten seconds later it matches a sky painted at that time, but for a hair of lag: less than a tenth of a degree
		int off = differences(end, cloudsAt(new MoonlitSky(), 50));
		int tenth = differences(cloudsAt(new MoonlitSky(), 50), cloudsAt(new MoonlitSky(), 49.75f));
		assertTrue(tenth > 20);
		assertTrue(off <= tenth, "the clouds lag behind the clock: " + off + " corners off, a tenth of a degree is " + tenth);
	}

	private static List<Integer> ionAt(CometSky sky, float time) {
		Layer layer = new Layer(RADIUS * CometSky.radii(1, false)[1]);
		sky.draw(layer, RADIUS, BASE_YAW, 1, time, SkyView.ALL);
		return layer.colours;
	}

	@Test
	void streamersHoldBetweenUpdatesAndCatchUpAfter() {
		CometSky sky = new CometSky();
		List<Integer> first = ionAt(sky, 3);
		assertTrue(first.size() > 100);
		// A hundredth of a second later the streamers have moved a hair: the last picture is reused
		assertEquals(first, ionAt(sky, 3.01f));
		// Half a second on they are recomputed, exactly as a fresh comet draws them at that time
		List<Integer> later = ionAt(sky, 3.5f);
		assertNotEquals(first, later);
		assertEquals(ionAt(new CometSky(), 3.5f), later);
		// Time running backwards (a seek) updates at once
		assertEquals(first, ionAt(sky, 3));
	}
}
