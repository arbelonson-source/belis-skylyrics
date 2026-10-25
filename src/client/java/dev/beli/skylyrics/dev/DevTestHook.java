package dev.beli.skylyrics.dev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.gui.SkyLyricsScreen;

/**
 * Development-only remote control, so the mod can be checked on a hidden screen without a keyboard.
 * Write commands, one per line, to {@code skylyrics-test.txt} in the game folder; the file is read
 * once and deleted. Commands: {@code look <yaw> <pitch>}, {@code gui [tab]}, {@code preview},
 * {@code close}, {@code hud on|off}, {@code cmd <server command>}, {@code shot <name>} (saves the
 * game frame alone to {@code screenshots/<name>.png}, without anything the desktop draws on top).
 */
public final class DevTestHook {
	private final Path file = FabricLoader.getInstance().getGameDir().resolve("skylyrics-test.txt");
	private int countdown;

	public static boolean enabled() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	public void tick(Minecraft client) {
		if (--countdown > 0) {
			return;
		}
		countdown = 10;
		if (!Files.exists(file)) {
			return;
		}
		List<String> lines;
		try {
			lines = Files.readAllLines(file);
			Files.delete(file);
		} catch (IOException e) {
			SkyLyricsClient.LOG.warn("Test hook could not read {}", file, e);
			return;
		}
		for (String line : lines) {
			String trimmed = line.strip();
			if (!trimmed.isEmpty()) {
				run(client, trimmed);
			}
		}
	}

	private void run(Minecraft client, String line) {
		String[] parts = line.split("\\s+", 2);
		String arg = parts.length > 1 ? parts[1] : "";
		SkyLyricsClient.LOG.info("Test hook: {}", line);
		try {
			switch (parts[0]) {
				case "look" -> {
					String[] angles = arg.split("\\s+");
					if (client.player != null) {
						client.player.setYRot(Float.parseFloat(angles[0]));
						client.player.setXRot(Float.parseFloat(angles[1]));
					}
				}
				case "gui" -> {
					SkyLyricsScreen screen = new SkyLyricsScreen(null);
					client.setScreen(screen);
					if (!arg.isEmpty()) {
						screen.selectTab(Integer.parseInt(arg));
					}
				}
				case "preview" -> {
					if (client.screen instanceof SkyLyricsScreen screen) {
						screen.setPreview(true);
					}
				}
				case "close" -> client.setScreen(null);
				case "scroll" -> {
					if (client.screen != null) {
						client.screen.mouseScrolled(client.getWindow().getGuiScaledWidth() / 2.0, client.getWindow().getGuiScaledHeight() / 2.0, 0, Double.parseDouble(arg));
					}
				}
				case "hud" -> client.options.hideGui = arg.equals("off");
				case "cmd" -> {
					MinecraftServer server = client.getSingleplayerServer();
					if (server != null) {
						server.execute(() -> server.getCommands()
								.performPrefixedCommand(server.createCommandSourceStack(), arg));
					}
				}
				case "shot" -> Screenshot.grab(client.gameDirectory, arg + ".png", client.getMainRenderTarget(), 1,
						message -> SkyLyricsClient.LOG.info("Test hook: {}", message.getString()));
				default -> SkyLyricsClient.LOG.warn("Test hook: unknown command {}", parts[0]);
			}
		} catch (RuntimeException e) {
			SkyLyricsClient.LOG.warn("Test hook command failed: {}", line, e);
		}
	}
}
