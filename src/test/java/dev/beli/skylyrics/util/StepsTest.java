package dev.beli.skylyrics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class StepsTest {
	@Test
	void aFailingStepDoesNotStopTheNextOnes() {
		List<String> ran = new ArrayList<>();
		List<String> failed = new ArrayList<>();
		java.util.function.BiConsumer<String, RuntimeException> onFailure = (name, e) -> failed.add(name + ":" + e.getMessage());
		assertFalse(Steps.run("heard", () -> {
			throw new IllegalStateException("boom");
		}, onFailure));
		assertTrue(Steps.run("lyrics", () -> ran.add("lyrics"), onFailure));
		assertTrue(Steps.run("cover", () -> ran.add("cover"), onFailure));
		assertEquals(List.of("lyrics", "cover"), ran);
		assertEquals(List.of("heard:boom"), failed);
	}
}
