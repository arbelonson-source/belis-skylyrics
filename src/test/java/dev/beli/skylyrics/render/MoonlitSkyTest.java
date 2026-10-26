package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MoonlitSkyTest {
	private static int sum(int rgb) {
		return (rgb >> 16 & 255) + (rgb >> 8 & 255) + (rgb & 255);
	}

	@Test
	void skyDarkensUpwards() {
		assertTrue(sum(MoonlitSky.skyColor(0)) > sum(MoonlitSky.skyColor(30)));
		assertTrue(sum(MoonlitSky.skyColor(30)) > sum(MoonlitSky.skyColor(90)));
	}

	@Test
	void moonOutshinesTheSky() {
		int sky = sum(MoonlitSky.skyColor(MoonlitSky.MOON_ELEV));
		for (float a = -0.9f; a <= 0.9f; a += 0.3f) {
			assertTrue(sum(MoonlitSky.moonColor(a, 0)) > 3 * sky);
		}
		// Darker towards the rim
		assertTrue(sum(MoonlitSky.moonColor(0, 0.99f)) < sum(MoonlitSky.moonColor(0, 0)));
	}

	@Test
	void glowFadesOutwards() {
		assertEquals(1, MoonlitSky.glow(0.5f));
		assertTrue(MoonlitSky.glow(1.05f) > MoonlitSky.glow(2));
		assertTrue(MoonlitSky.glow(2) > MoonlitSky.glow(3));
		assertEquals(0, MoonlitSky.glow(MoonlitSky.GLOW_END));
		for (float r = 1.01f; r < MoonlitSky.GLOW_END; r += 0.05f) {
			assertTrue(MoonlitSky.glow(r + 0.05f) <= MoonlitSky.glow(r));
		}
	}

	@Test
	void cloudsStayLow() {
		float total = 0;
		for (int yaw = 0; yaw < 360; yaw += 3) {
			assertEquals(0, MoonlitSky.cloud(yaw, MoonlitSky.CLOUD_TOP, 0));
			assertEquals(0, MoonlitSky.cloud(yaw, 40, 0));
			total += MoonlitSky.cloud(yaw, 5, 0);
		}
		// Broken, not a solid wall
		assertTrue(total > 10 && total < 100, "cloud cover " + total);
	}

	@Test
	void cloudsJoinUpAfterAFullTurn() {
		for (float elev = 1; elev <= 12; elev += 1.5f) {
			assertEquals(MoonlitSky.cloud(-180, elev, 0), MoonlitSky.cloud(180, elev, 0), 1e-4f);
			assertEquals(MoonlitSky.cloud(10, elev, 0), MoonlitSky.cloud(10, elev, 360), 1e-4f);
		}
	}

	@Test
	void cloudsDrift() {
		boolean moved = false;
		for (int yaw = 0; yaw < 360; yaw += 5) {
			moved |= Math.abs(MoonlitSky.cloud(yaw, 5, 0) - MoonlitSky.cloud(yaw, 5, 20)) > 0.1f;
			assertEquals(MoonlitSky.cloud(yaw + 20, 5, 0), MoonlitSky.cloud(yaw, 5, 20), 1e-4f);
		}
		assertTrue(moved);
	}

	@Test
	void cloudsNearTheMoonCatchItsLight() {
		// Brightest thick cloud on the moon's side against the far side
		float near = 0, far = 0;
		SkyCanvas.SkyFrame f = new SkyCanvas.SkyFrame();
		for (int yaw = -180; yaw < 180; yaw += 2) {
			if (MoonlitSky.cloud(yaw, 6, 0) < 0.6f) {
				continue;
			}
			f.aim(yaw, 6);
			int c = sum(MoonlitSky.background(f.dx, f.dy, f.dz));
			if (Math.abs(yaw - MoonlitSky.MOON_YAW) < 25) {
				near = Math.max(near, c);
			} else if (Math.abs(yaw - MoonlitSky.MOON_YAW - 180) < 40 || Math.abs(yaw - MoonlitSky.MOON_YAW + 180) < 40) {
				far = Math.max(far, c);
			}
		}
		assertTrue(near > far, near + " vs " + far);
	}
}
