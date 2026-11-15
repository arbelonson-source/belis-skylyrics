package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/** The limits a loaded settings file is held to. */
class ConfigClampTest {
	private static SkyLyricsConfig load(String json) {
		return SkyLyricsConfig.parse("{" + json + "}").config();
	}

	@Test
	void opacityNeverGoesBelowTwoTenthsOrAboveOne() {
		assertEquals(0.2f, load("\"opacity\": 0").opacity, 1e-6f);
		assertEquals(0.2f, load("\"opacity\": -3").opacity, 1e-6f);
		assertEquals(0.2f, load("\"opacity\": 0.19").opacity, 1e-6f);
		assertEquals(0.5f, load("\"opacity\": 0.5").opacity, 1e-6f);
		assertEquals(1f, load("\"opacity\": 4").opacity, 1e-6f);
	}

	@Test
	void sizeStaysBetweenAThirdAndThree() {
		assertEquals(3f, load("\"size\": 3.5").size, 1e-6f);
		assertEquals(3f, load("\"size\": 100").size, 1e-6f);
		assertEquals(2.9f, load("\"size\": 2.9").size, 1e-6f);
		assertEquals(0.3f, load("\"size\": 0.1").size, 1e-6f);
		assertEquals(0.3f, load("\"size\": -1").size, 1e-6f);
	}

	@Test
	void worldWordsStaysBetweenOneAndTen() {
		assertEquals(1, load("\"worldWords\": 0").worldWords);
		assertEquals(1, load("\"worldWords\": -5").worldWords);
		assertEquals(10, load("\"worldWords\": 11").worldWords);
		assertEquals(10, load("\"worldWords\": 500").worldWords);
		assertEquals(1, load("\"worldWords\": 1").worldWords);
		assertEquals(10, load("\"worldWords\": 10").worldWords);
		assertEquals(7, load("\"worldWords\": 7").worldWords);
	}

	@Test
	void theOtherRangesHoldAtBothEnds() {
		SkyLyricsConfig low = load("\"glow\": -1, \"skyStrength\": 0, \"worldFade\": 0, \"worldDistance\": 0, \"worldTilt\": -1,"
				+ " \"worldRise\": -1, \"worldCurve\": -1, \"barSize\": 0, \"barBackground\": -1, \"height\": -99, \"distance\": 0");
		assertEquals(0f, low.glow, 1e-6f);
		assertEquals(0.1f, low.skyStrength, 1e-6f);
		assertEquals(0.8f, low.worldFade, 1e-6f);
		assertEquals(0.5f, low.worldDistance, 1e-6f);
		assertEquals(0f, low.worldTilt, 1e-6f);
		assertEquals(0f, low.worldRise, 1e-6f);
		assertEquals(0f, low.worldCurve, 1e-6f);
		assertEquals(0.75f, low.barSize, 1e-6f);
		assertEquals(0f, low.barBackground, 1e-6f);
		assertEquals(-10f, low.height, 1e-6f);
		assertEquals(8f, low.distance, 1e-6f);
		SkyLyricsConfig high = load("\"glow\": 9, \"skyStrength\": 9, \"worldFade\": 99, \"worldDistance\": 99, \"worldTilt\": 99,"
				+ " \"worldRise\": 99, \"worldCurve\": 99, \"barSize\": 9, \"barBackground\": 9, \"height\": 999, \"distance\": 9999");
		assertEquals(1f, high.glow, 1e-6f);
		assertEquals(1f, high.skyStrength, 1e-6f);
		assertEquals(6f, high.worldFade, 1e-6f);
		assertEquals(2f, high.worldDistance, 1e-6f);
		assertEquals(2f, high.worldTilt, 1e-6f);
		assertEquals(2f, high.worldRise, 1e-6f);
		assertEquals(2f, high.worldCurve, 1e-6f);
		assertEquals(1.5f, high.barSize, 1e-6f);
		assertEquals(1f, high.barBackground, 1e-6f);
		assertEquals(80f, high.height, 1e-6f);
		assertEquals(160f, high.distance, 1e-6f);
	}

	@Test
	void aNotANumberFloatIsBroughtBackIntoItsRangeByTheCheckOnLoad() throws Exception {
		SkyLyricsConfig c = new SkyLyricsConfig();
		c.opacity = Float.NaN;
		c.size = Float.NaN;
		c.glow = Float.NaN;
		c.worldFade = Float.NaN;
		c.distance = Float.NaN;
		c.height = Float.NaN;
		Method fix = SkyLyricsConfig.class.getDeclaredMethod("fix");
		fix.setAccessible(true);
		fix.invoke(c);
		for (float v : new float[] {c.opacity, c.size, c.glow, c.worldFade, c.distance, c.height}) {
			assertFalse(Float.isNaN(v));
		}
		assertTrue(c.opacity >= 0.2f && c.opacity <= 1f);
		assertTrue(c.size >= 0.3f && c.size <= 3f);
		assertTrue(c.glow >= 0f && c.glow <= 1f);
		assertTrue(c.worldFade >= 0.8f && c.worldFade <= 6f);
		assertTrue(c.distance >= 8f && c.distance <= 160f);
		assertTrue(c.height >= -10f && c.height <= 80f);
	}

	@Test
	void aNaNWrittenInTheFileNeverReachesTheSettings() {
		for (String nan : new String[] {"\"NaN\"", "NaN"}) {
			SkyLyricsConfig c = load("\"opacity\": " + nan + ", \"size\": " + nan);
			assertFalse(Float.isNaN(c.opacity), nan);
			assertFalse(Float.isNaN(c.size), nan);
			assertTrue(c.opacity >= 0.2f && c.opacity <= 1f, nan);
			assertTrue(c.size >= 0.3f && c.size <= 3f, nan);
		}
	}
}
