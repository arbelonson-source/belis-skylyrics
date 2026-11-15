package dev.beli.skylyrics.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyMigrationTest {
	@Test
	void movesOnceWhenSharingTheShaderKey() {
		assertTrue(KeyMigration.due(false));
		assertTrue(KeyMigration.shouldMove(false, true, false));
	}

	@Test
	void neverMovesAgainOnceItRan() {
		assertFalse(KeyMigration.due(true));
		assertFalse(KeyMigration.shouldMove(true, true, false));
	}

	@Test
	void leavesOtherKeysAlone() {
		assertFalse(KeyMigration.shouldMove(false, false, false));
		assertFalse(KeyMigration.shouldMove(false, true, true));
	}

	@Test
	void oldFileWithoutTheFlagGetsTheMoveOnce() {
		SkyLyricsConfig old = SkyLyricsConfig.parse("{\"introSeen\":true}").config();
		assertTrue(KeyMigration.due(old.keysMigrated));
		SkyLyricsConfig saved = SkyLyricsConfig.parse("{\"keysMigrated\":true}").config();
		assertFalse(KeyMigration.due(saved.keysMigrated));
	}

	@Test
	void resetAllKeepsTheFlag() {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.keysMigrated = true;
		cfg.resetToDefaults();
		assertTrue(cfg.keysMigrated);
	}
}
