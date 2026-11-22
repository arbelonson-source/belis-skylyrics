package dev.beli.skylyrics.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class HoldPlanTest {
	@Test
	void parsesMappingAndDuration() {
		assertEquals(new DevKeys.Hold("key.playerlist", 1500), DevKeys.parseHold("key.playerlist 1500"));
		assertEquals(new DevKeys.Hold("key.sneak", 200), DevKeys.parseHold("  key.sneak   200 "));
	}

	@Test
	void mappingNameIsNormalised() {
		assertEquals("key.playerlist", DevKeys.mappingName("playerlist"));
		assertEquals("key.playerlist", DevKeys.mappingName("KEY.PlayerList"));
		assertEquals("key.playerlist", DevKeys.mappingName("key_key.playerlist"));
		assertEquals("key.sneak", DevKeys.mappingName("sneak"));
		assertEquals("key.hotbar.1", DevKeys.mappingName("key.hotbar.1"));
	}

	@Test
	void rejectsBadArguments() {
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parseHold(""));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parseHold("key.sneak"));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parseHold("key.sneak soon"));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parseHold("key.sneak 0"));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parseHold("key.sneak -5"));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parseHold("key.sneak 120001"));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parseHold("key.sneak 10 20"));
	}

	@Test
	void bindTakesAMappingAndAKey() {
		assertEquals(new DevKeys.Bind("key.skylyrics.lyrics_late", "key.keyboard.period"), DevKeys.parseBind("skylyrics.lyrics_late period"));
		assertEquals(new DevKeys.Bind("key.skylyrics.lyrics_early", "key.keyboard.comma"), DevKeys.parseBind(" key.skylyrics.lyrics_early  key.keyboard.comma "));
		assertEquals(new DevKeys.Bind("key.sneak", "key.mouse.4"), DevKeys.parseBind("sneak key.mouse.4"));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parseBind(""));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parseBind("skylyrics.lyrics_late"));
		assertThrows(IllegalArgumentException.class, () -> DevKeys.parseBind("skylyrics.lyrics_late period comma"));
	}

	@Test
	void legacyUseOnOffIsRecognised() {
		assertEquals(Boolean.TRUE, DevKeys.legacyUse("use on"));
		assertEquals(Boolean.FALSE, DevKeys.legacyUse("use off"));
		assertEquals(null, DevKeys.legacyUse("use 500"));
		assertEquals(null, DevKeys.legacyUse("key.use 500"));
	}

	@Test
	void keyStaysHeldUntilItsDeadlineThenIsReleasedOnce() {
		DevKeys.HoldSchedule<String> schedule = new DevKeys.HoldSchedule<>();
		schedule.hold("sneak", 1000, 500);
		assertEquals(List.of(), schedule.expire(1499));
		assertEquals(List.of("sneak"), schedule.held());
		assertEquals(List.of("sneak"), schedule.expire(1500));
		assertEquals(List.of(), schedule.held());
		assertEquals(List.of(), schedule.expire(9999));
	}

	@Test
	void holdingAgainMovesTheDeadlineAndKeysAreIndependent() {
		DevKeys.HoldSchedule<String> schedule = new DevKeys.HoldSchedule<>();
		schedule.hold("tab", 0, 300);
		schedule.hold("jump", 0, 100);
		schedule.hold("tab", 200, 300); // now until 500
		assertEquals(List.of("jump"), schedule.expire(150));
		assertEquals(List.of(), schedule.expire(400));
		assertEquals(List.of("tab"), schedule.expire(500));
	}

	@Test
	void releaseAllReturnsEverythingStillHeld() {
		DevKeys.HoldSchedule<String> schedule = new DevKeys.HoldSchedule<>();
		schedule.hold("a", 0, 100);
		schedule.hold("b", 0, 200);
		List<String> released = schedule.releaseAll();
		assertEquals(2, released.size());
		assertEquals(java.util.Set.of("a", "b"), new java.util.HashSet<>(released));
		assertEquals(List.of(), schedule.held());
	}
}
