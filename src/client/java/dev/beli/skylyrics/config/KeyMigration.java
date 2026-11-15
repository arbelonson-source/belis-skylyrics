package dev.beli.skylyrics.config;

/** The decisions of the one-time library key move (see {@code SkyLyricsClient.moveOffShaderToggle}). */
public final class KeyMigration {
	private KeyMigration() {
	}

	/** The move runs only when it hasn't run before. */
	public static boolean due(boolean alreadyMigrated) {
		return !alreadyMigrated;
	}

	/** Move our key to its default only on the first run, when it shares the shader toggle's key and isn't the default. */
	public static boolean shouldMove(boolean alreadyMigrated, boolean sameKeyAsShaderToggle, boolean isDefault) {
		return due(alreadyMigrated) && sameKeyAsShaderToggle && !isDefault;
	}
}
