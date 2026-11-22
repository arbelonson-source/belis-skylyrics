package dev.beli.skylyrics.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A player can give Fabric a mods folder of its own with the Java setting {@code -Dfabric.modsFolder=...} (one game folder,
 * a mods folder per Minecraft version). The updater looks for the mod's jar where Fabric loaded it from, so "Install"
 * still installs there instead of only telling the player.
 */
class UpdaterModsFolderTest {
	@TempDir
	Path dir;

	@Test
	void withoutTheSettingItIsTheGameFoldersMods() {
		assertEquals(dir.resolve("mods"), Updater.modsFolder(dir, null));
	}

	@Test
	void theSettingNamesTheFolderFabricLoadsFrom() {
		Path own = dir.resolve("mods-1.21.11");
		assertEquals(own, Updater.modsFolder(dir.resolve("game"), own.toString()));
	}

	@Test
	void theJarInThatFolderIsFoundSoItCanBeUpdated() throws Exception {
		Path game = Files.createDirectories(dir.resolve("game"));
		Path own = Files.createDirectories(dir.resolve("mods-1.21.11"));
		Path jar = Files.writeString(own.resolve("belis-skylyrics-0.9.0+1.21.11.jar"), "jar");
		assertNotNull(Updater.ownJar(Updater.modsFolder(game, own.toString()), List.of(jar)));
	}
}
