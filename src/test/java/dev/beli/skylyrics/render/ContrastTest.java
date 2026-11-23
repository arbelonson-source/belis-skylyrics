package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import dev.beli.skylyrics.Wcag;
import org.junit.jupiter.api.Test;

class ContrastTest {
	@Test
	void ratiosAgreeWithTheTestsOwnSums() {
		Random random = new Random(169);
		for (int i = 0; i < 5000; i++) {
			int a = random.nextInt(1 << 24), b = random.nextInt(1 << 24);
			assertEquals(Wcag.ratio(a, b), Contrast.ratio(a, b), 0.01, String.format("%06X on %06X", a, b));
			int argb = random.nextInt();
			assertEquals(Wcag.over(argb, b), Contrast.over(argb, b), String.format("%08X over %06X", argb, b));
		}
		assertEquals(21, Contrast.ratio(0xFFFFFF, 0x000000), 0.01);
		assertEquals(1, Contrast.ratio(0x926CDE, 0x926CDE), 1e-9);
	}

	@Test
	void deepenAndLightenReachTheFloorAndStopThere() {
		Random random = new Random(1690);
		for (int i = 0; i < 2000; i++) {
			int c = random.nextInt(1 << 24);
			int deep = Contrast.deepen(c, 0xFCF8FF, Contrast.FLOOR);
			assertTrue(Wcag.ratio(0xFCF8FF, deep) >= Contrast.FLOOR, String.format("%06X -> %06X", c, deep));
			if (Wcag.ratio(0xFCF8FF, c) >= Contrast.FLOOR) {
				assertEquals(c, deep);
			} else {
				assertTrue(Wcag.ratio(0xFCF8FF, deep) < Contrast.FLOOR + 0.2, String.format("%06X -> %06X", c, deep));
			}
			int light = Contrast.lighten(c, 0x2A2734, Contrast.FLOOR);
			assertTrue(Wcag.ratio(light, 0x2A2734) >= Contrast.FLOOR, String.format("%06X -> %06X", c, light));
			if (Wcag.ratio(c, 0x2A2734) >= Contrast.FLOOR) {
				assertEquals(c, light);
			} else {
				assertTrue(Wcag.ratio(light, 0x2A2734) < Contrast.FLOOR + 0.2, String.format("%06X -> %06X", c, light));
			}
		}
	}
}
