package dev.beli.skylyrics.dev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.MinecraftServer;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.gui.SkyLyricsScreen;

/**
 * Development-only remote control, so the mod can be checked on a hidden screen without a keyboard.
 * Write commands, one per line, to {@code skylyrics-test.txt} in the game folder; the file is read
 * once and deleted. Commands: {@code look <yaw> <pitch>}, {@code gui [tab]}, {@code preview},
 * {@code close}, {@code hud on|off}, {@code cmd <server command>}, {@code guiscale <n>} (0 = auto), {@code lang <code>},
 * {@code shot <name>} (saves the game frame alone to {@code screenshots/<name>.png}, without anything
 * the desktop draws on top), {@code uncap} (no frame limit, vsync or idle slowdown), {@code fps [seconds]} (logs the frame
 * rate over that long).
 */
public final class DevTestHook {
	private final Path file = FabricLoader.getInstance().getGameDir().resolve("skylyrics-test.txt");
	private int countdown;
	/** Ticks left of a frame-rate measurement, and the rate it saw each second so far. */
	private int fpsLeft;
	private final List<Integer> fpsSeen = new ArrayList<>();

	/** On in the dev game, and in runProductionClient, which sets -Dskylyrics.testHook=true. */
	public static boolean enabled() {
		return FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("skylyrics.testHook");
	}

	public void tick(Minecraft client) {
		if (fpsLeft > 0 && --fpsLeft % 20 == 0) {
			fpsSeen.add(client.getFps());
			if (fpsLeft == 0) {
				// The first second still counts frames from before the command
				List<Integer> seen = fpsSeen.subList(1, fpsSeen.size());
				SkyLyricsClient.LOG.info("Test hook: fps average {} lowest {} over {}s",
						seen.stream().mapToInt(Integer::intValue).average().orElse(0),
						seen.stream().mapToInt(Integer::intValue).min().orElse(0), seen.size());
			}
		}
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

	/** "set field value": changes one config field for this run (not saved). */
	private static void set(String arg) {
		String[] kv = arg.split("\\s+", 2);
		try {
			java.lang.reflect.Field field = SkyLyricsConfig.class.getField(kv[0]);
			Class<?> type = field.getType();
			Object value;
			if (type == boolean.class) {
				value = Boolean.parseBoolean(kv[1]);
			} else if (type == int.class) {
				value = Integer.parseInt(kv[1]);
			} else if (type == float.class) {
				value = Float.parseFloat(kv[1]);
			} else if (type.isEnum()) {
				value = java.util.Arrays.stream(type.getEnumConstants())
						.filter(c -> ((Enum<?>) c).name().equals(kv[1]))
						.findFirst().orElseThrow();
			} else {
				value = kv[1];
			}
			field.set(SkyLyricsConfig.get(), value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException("no config field " + kv[0], e);
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
				case "screen" -> {
					// Opens another mod's screen by class name, e.g. Mod Menu's list
					try {
						client.setScreen((Screen) Class.forName(arg).getConstructor(Screen.class).newInstance(client.screen));
					} catch (ReflectiveOperationException e) {
						throw new IllegalStateException(e);
					}
				}
				case "scroll" -> {
					if (client.screen != null) {
						client.screen.mouseScrolled(client.getWindow().getGuiScaledWidth() / 2.0, client.getWindow().getGuiScaledHeight() / 2.0, 0, Double.parseDouble(arg));
					}
				}
				case "click" -> {
					// x y in scaled GUI pixels, a left click and release
					String[] xy = arg.split("\\s+");
					if (client.screen != null) {
						var e = new net.minecraft.client.input.MouseButtonEvent(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]),
								new net.minecraft.client.input.MouseButtonInfo(0, 0));
						client.screen.mouseClicked(e, false);
						client.screen.mouseReleased(e);
					}
				}
				case "hud" -> client.options.hideGui = arg.equals("off");
				case "lang" -> {
					client.getLanguageManager().setSelected(arg);
					client.options.languageCode = arg;
					client.reloadResourcePacks();
				}
				case "guiscale" -> {
					client.options.guiScale().set(Integer.parseInt(arg));
					client.resizeDisplay();
				}
				case "cmd" -> {
					MinecraftServer server = client.getSingleplayerServer();
					if (server != null) {
						server.execute(() -> server.getCommands()
								.performPrefixedCommand(server.createCommandSourceStack(), arg));
					}
				}
				case "set" -> set(arg);
				case "uncap" -> {
					client.options.enableVsync().set(false);
					client.options.framerateLimit().set(260);
					// Otherwise the game drops to 30 fps after a minute without input, which spoils a measurement
					client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
				}
				case "fps" -> {
					fpsSeen.clear();
					fpsLeft = ((arg.isEmpty() ? 5 : Integer.parseInt(arg)) + 1) * 20;
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
