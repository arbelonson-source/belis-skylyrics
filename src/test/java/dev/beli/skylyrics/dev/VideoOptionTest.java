package dev.beli.skylyrics.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VideoOptionTest {
	@Test
	void distancesAreChunks() {
		assertEquals(new VideoOption.Setting(VideoOption.Kind.RENDER_DISTANCE, 4, null), VideoOption.parse("renderDistance 4"));
		assertEquals(new VideoOption.Setting(VideoOption.Kind.CLOUD_RANGE, 32, null), VideoOption.parse("  cloudRange   32 "));
	}

	@Test
	void cloudsAreOffFastOrFancyInAnyCase() {
		assertEquals("FANCY", VideoOption.parse("clouds fancy").clouds());
		assertEquals("OFF", VideoOption.parse("clouds OFF").clouds());
		assertEquals("FAST", VideoOption.parse("clouds Fast").clouds());
	}

	@Test
	void junkIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> VideoOption.parse("renderDistance"));
		assertThrows(IllegalArgumentException.class, () -> VideoOption.parse("renderDistance four"));
		assertThrows(IllegalArgumentException.class, () -> VideoOption.parse("renderDistance 1"));
		assertThrows(IllegalArgumentException.class, () -> VideoOption.parse("renderDistance 33"));
		assertThrows(IllegalArgumentException.class, () -> VideoOption.parse("clouds thick"));
		assertThrows(IllegalArgumentException.class, () -> VideoOption.parse("fov 90"));
		assertThrows(IllegalArgumentException.class, () -> VideoOption.parse(""));
	}
}
