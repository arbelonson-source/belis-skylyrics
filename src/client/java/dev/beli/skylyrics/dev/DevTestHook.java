package dev.beli.skylyrics.dev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
//? if >=1.21.2 {
import net.minecraft.client.InactivityFpsLimit;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.MinecraftServer;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.gui.SkyLyricsScreen;
import dev.beli.skylyrics.lyrics.LrcParser;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.DemoSource;

/**
 * Development-only remote control, so the mod can be checked on a hidden screen without a keyboard.
 * Write commands, one per line, to {@code skylyrics-test.txt} in the game folder; the file is read
 * once and deleted. Commands: {@code look <yaw> <pitch>}, {@code gui [tab]}, {@code preview},
 * {@code close}, {@code press <button text>}, {@code hud on|off}, {@code view <0-2>} (first person, behind, in front),
 * {@code lines <a | b | c>} (test lines in place of the demo song's), {@code plain <a | b | c>} (the same as lyrics without timing), {@code cmd <server command>}, {@code guiscale <n>} (0 = auto), {@code lang <code>},
 * {@code shot <name>} (saves the game frame alone to {@code screenshots/<name>.png}, without anything
 * the desktop draws on top), {@code uncap} (no frame limit, vsync or idle slowdown), {@code fps [seconds]} (logs the frame
 * rate over that long), {@code key <NAME> [ctrl] [shift] [alt]} (a key press and release on the open screen, plus the
 * typed character for letters, digits and space; names: A-Z, 0-9, F1-F25, TAB, ENTER, SPACE, ESCAPE, UP, DOWN, LEFT,
 * RIGHT, PAGE_UP, PAGE_DOWN, HOME, END, BACKSPACE, INSERT, DELETE; before 1.21.9 Ctrl/Shift/Alt only reach code that
 * reads the real keyboard state, so they have no effect there), {@code type <text>} (typed characters on the open screen)
 * and {@code hover <x> <y>} (moves the mouse over the open screen, in scaled GUI pixels). These three do nothing but log
 * when no screen is open.
 * <p>
 * {@code burst <name> <count> <everyMs>} grabs the game frame {@code count} times (1 to 60), {@code everyMs} apart (0 = every
 * rendered frame), one grab per rendered frame, and saves them as {@code screenshots/<name>_00.png}, {@code _01}, ...
 * The frames are kept in memory and only written (on another thread) after the last grab, so encoding does not slow
 * the frames being caught. {@code burstat <name> <count> <everyMs>} first waits until the lyrics move to the next line
 * (so the first frame is the crosshair flash's peak, the start of a slide), and gives up after 10 s. One burst at a time.
 */
public final class DevTestHook {
	private final Path file = FabricLoader.getInstance().getGameDir().resolve("skylyrics-test.txt");
	private int countdown;
	/** Ticks left of a frame-rate measurement, and the rate it saw each second so far. */
	private int fpsLeft;
	private final List<Integer> fpsSeen = new ArrayList<>();

	/** The running hook, for the per-frame call; null whenever the hook is off. */
	private static volatile DevTestHook active;
	/** A burst in progress: waiting for a line change, then grabbing, then waiting for the pictures to arrive. */
	private BurstPlan.Burst burst;
	private boolean burstWaiting;
	private long burstWaitStart;
	private float lastSince = Float.NaN;
	private long burstStart;
	private int burstTaken;
	private NativeImage[] burstImages;
	private AtomicInteger burstArrived;

	public DevTestHook() {
		active = this;
	}

	/** Called once per rendered frame by the game loop; a no-op unless the test hook is on. */
	public static void frame(Minecraft client) {
		DevTestHook hook = active;
		if (hook != null) {
			try {
				hook.onFrame(client);
			} catch (RuntimeException e) {
				SkyLyricsClient.LOG.warn("Test hook burst failed", e);
				hook.abortBurst();
			}
		}
	}

	private void startBurst(String arg, boolean atLineChange) {
		BurstPlan.Burst parsed = BurstPlan.parse(arg);
		if (burst != null) {
			SkyLyricsClient.LOG.warn("Test hook: a burst is already running, {} ignored", parsed.name());
			return;
		}
		burst = parsed;
		burstTaken = 0;
		burstImages = new NativeImage[parsed.count()];
		burstArrived = new AtomicInteger();
		burstWaiting = atLineChange;
		burstWaitStart = System.nanoTime();
		lastSince = Float.NaN;
		if (atLineChange) {
			SkyLyricsClient.LOG.info("Test hook: burst {} waits for the next lyric line", parsed.name());
		}
	}

	private void abortBurst() {
		if (burstImages != null) {
			for (NativeImage image : burstImages) {
				if (image != null) {
					image.close();
				}
			}
		}
		burst = null;
		burstImages = null;
		burstWaiting = false;
	}

	private void onFrame(Minecraft client) {
		if (burst == null || burstTaken >= burst.count()) {
			return;
		}
		long now = System.nanoTime();
		if (burstWaiting) {
			float since = SkyLyricsClient.get().renderer().secondsSinceLineChange();
			boolean changed = BurstPlan.lineChanged(lastSince, since);
			lastSince = since;
			if (!changed) {
				if (BurstPlan.gaveUp(now - burstWaitStart)) {
					SkyLyricsClient.LOG.warn("Test hook: burst {} gave up, the lyrics did not move to a new line in 10 s", burst.name());
					burst = null;
					burstImages = null;
					burstWaiting = false;
				}
				return;
			}
			burstWaiting = false;
		}
		if (burstTaken == 0) {
			burstStart = now;
			SkyLyricsClient.LOG.info("Test hook: burst {} starts, {} frames every {} ms", burst.name(), burst.count(), burst.everyMs());
		}
		if (BurstPlan.due(burstTaken, now - burstStart, burst.everyMs())) {
			grabFrame(client, burstTaken++);
		}
	}

	/** Takes the game frame as the {@code shot} command does, keeping the picture in memory. */
	private void grabFrame(Minecraft client, int index) {
		NativeImage[] images = burstImages;
		AtomicInteger arrived = burstArrived;
		BurstPlan.Burst mine = burst;
		java.util.function.Consumer<NativeImage> keep = image -> {
			images[index] = image;
			if (arrived.incrementAndGet() == mine.count()) {
				writeBurst(client.gameDirectory.toPath().resolve("screenshots"), mine, images);
			}
		};
		//? if >=26.2 {
		/*Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), keep);
		*///?} else if >=1.21.5 {
		Screenshot.takeScreenshot(client.getMainRenderTarget(), keep);
		//?} else {
		/*keep.accept(Screenshot.takeScreenshot(client.getMainRenderTarget()));
		*///?}
		if (index == mine.count() - 1) {
			burst = null;
		}
	}

	/** Encodes and writes the pictures off the render thread, closing each one. */
	private static void writeBurst(Path dir, BurstPlan.Burst done, NativeImage[] images) {
		Thread writer = new Thread(() -> {
			int written = 0;
			try {
				Files.createDirectories(dir);
				for (int i = 0; i < images.length; i++) {
					try {
						images[i].writeToFile(dir.resolve(BurstPlan.fileName(done.name(), i) + ".png"));
						written++;
					} catch (IOException e) {
						SkyLyricsClient.LOG.warn("Test hook: could not write burst frame {}", i, e);
					} finally {
						images[i].close();
					}
				}
			} catch (IOException e) {
				SkyLyricsClient.LOG.warn("Test hook: could not create {}", dir, e);
				for (NativeImage image : images) {
					image.close();
				}
			}
			SkyLyricsClient.LOG.info("Test hook: burst {} wrote {} of {} files to {}", done.name(), written, images.length, dir);
		}, "skylyrics-burst-writer");
		writer.setDaemon(true);
		writer.start();
	}

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

	/**
	 * "lines a | b | c": swaps the demo song's lyrics for these test lines, one every four seconds, looping over the
	 * song, so other scripts (Arabic, Hebrew) can be checked. Only for short test phrases, never real lyrics.
	 */
	private static void lines(Minecraft client, String arg) {
		String[] parts = arg.split("\\s*\\|\\s*");
		StringBuilder lrc = new StringBuilder();
		for (int i = 0, ms = 0; ms < DemoSource.TRACK.lengthMs(); i++, ms += 4000) {
			lrc.append(String.format("[%02d:%02d.00]%s%n", ms / 60000, ms / 1000 % 60, parts[i % parts.length]));
		}
		try {
			java.lang.reflect.Field field = LyricsService.class.getDeclaredField("lyrics");
			field.setAccessible(true);
			field.set(SkyLyricsClient.get().lyrics(), LrcParser.parseSynced(lrc.toString()));
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException("could not swap the lyrics", e);
		}
	}

	/** "plain a | b | c": like "lines", but as lyrics without timing, spread over the demo song the way plain lyrics are. */
	private static void plain(String arg) {
		swapLyrics(LrcParser.parsePlain(String.join("\n", arg.split("\\s*\\|\\s*")), DemoSource.TRACK.lengthMs()));
	}

	private static void swapLyrics(dev.beli.skylyrics.lyrics.Lyrics lyrics) {
		try {
			java.lang.reflect.Field field = LyricsService.class.getDeclaredField("lyrics");
			field.setAccessible(true);
			field.set(SkyLyricsClient.get().lyrics(), lyrics);
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException("could not swap the lyrics", e);
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

	/** The open screen, or null in the game world (keys must never reach the world). */
	private static Screen currentScreen(Minecraft client) {
		//? if >=26.2 {
		/*return client.gui.screen();
		*///?} else {
		return client.screen;
		//?}
	}

	/** A typed character as the screen receives it: 1.21.9 to 1.21.11 also carry the modifiers, 26.1 on does not. */
	//? if >=1.21.9 {
	private static net.minecraft.client.input.CharacterEvent charEvent(int codepoint, int modifiers) {
		//? if >=26.1 {
		/*return new net.minecraft.client.input.CharacterEvent(codepoint);
		*///?} else {
		return new net.minecraft.client.input.CharacterEvent(codepoint, modifiers);
		//?}
	}

	//?}
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
					//? if >=26.2 {
					/*client.gui.setScreen(screen);
					*///?} else {
					client.setScreen(screen);
					//?}
					if (!arg.isEmpty()) {
						screen.selectTab(Integer.parseInt(arg));
					}
				}
				case "preview" -> {
					//? if >=26.2 {
					/*if (client.gui.screen() instanceof SkyLyricsScreen screen) {
					*///?} else {
					if (client.screen instanceof SkyLyricsScreen screen) {
					//?}
						screen.setPreview(true);
					}
				}
				//? if >=26.2 {
				/*case "close" -> client.gui.setScreen(null);
				*///?} else {
				case "close" -> client.setScreen(null);
				//?}
				case "screen" -> {
					// Opens another mod's screen by class name, e.g. Mod Menu's list
					try {
						//? if >=26.2 {
						/*client.gui.setScreen((Screen) Class.forName(arg).getConstructor(Screen.class).newInstance(client.gui.screen()));
						*///?} else {
						client.setScreen((Screen) Class.forName(arg).getConstructor(Screen.class).newInstance(client.screen));
						//?}
					} catch (ReflectiveOperationException e) {
						throw new IllegalStateException(e);
					}
				}
				case "search" -> {
					// Types into the settings screen's search box
					//? if >=26.2 {
					/*if (client.gui.screen() instanceof SkyLyricsScreen screen) {
					*///?} else {
					if (client.screen instanceof SkyLyricsScreen screen) {
					//?}
						screen.search(arg);
					}
				}
				case "scroll" -> {
					//? if >=26.2 {
					/*if (client.gui.screen() != null) {
						client.gui.screen().mouseScrolled(client.getWindow().getGuiScaledWidth() / 2.0, client.getWindow().getGuiScaledHeight() / 2.0, 0, Double.parseDouble(arg));
					*///?} else {
					if (client.screen != null) {
						client.screen.mouseScrolled(client.getWindow().getGuiScaledWidth() / 2.0, client.getWindow().getGuiScaledHeight() / 2.0, 0, Double.parseDouble(arg));
					//?}
					}
				}
				case "click" -> {
					// x y in scaled GUI pixels, a left click and release
					String[] xy = arg.split("\\s+");
					//? if >=26.2 {
					/*if (client.gui.screen() != null) {
					*///?} else {
					if (client.screen != null) {
					//?}
						//? if >=1.21.9 {
						var e = new net.minecraft.client.input.MouseButtonEvent(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]),
								new net.minecraft.client.input.MouseButtonInfo(0, 0));
						//? if >=26.2 {
						/*client.gui.screen().mouseClicked(e, false);
						client.gui.screen().mouseReleased(e);
						*///?} else {
						client.screen.mouseClicked(e, false);
						client.screen.mouseReleased(e);
						//?}
						//?} else {
						/*client.screen.mouseClicked(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]), 0);
						client.screen.mouseReleased(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]), 0);
						*///?}
					}
				}
				case "key" -> {
					DevKeys.Key key = DevKeys.parse(arg);
					Screen screen = currentScreen(client);
					if (screen == null) {
						SkyLyricsClient.LOG.info("Test hook: no screen is open, key not sent");
					} else {
						int typed = DevKeys.charFor(key);
						//? if >=1.21.9 {
						screen.keyPressed(new net.minecraft.client.input.KeyEvent(key.code(), 0, key.modifiers()));
						if (typed != 0) {
							screen.charTyped(charEvent(typed, key.modifiers()));
						}
						screen.keyReleased(new net.minecraft.client.input.KeyEvent(key.code(), 0, key.modifiers()));
						//?} else {
						/*screen.keyPressed(key.code(), 0, key.modifiers());
						if (typed != 0) {
							screen.charTyped((char) typed, key.modifiers());
						}
						screen.keyReleased(key.code(), 0, key.modifiers());
						*///?}
						SkyLyricsClient.LOG.info("Test hook: sent key {} to {}", arg, screen.getClass().getSimpleName());
					}
				}
				case "type" -> {
					Screen screen = currentScreen(client);
					if (screen == null) {
						SkyLyricsClient.LOG.info("Test hook: no screen is open, text not sent");
					} else {
						arg.codePoints().forEach(c -> {
							//? if >=1.21.9 {
							screen.charTyped(charEvent(c, 0));
							//?} else {
							/*screen.charTyped((char) c, 0);
							*///?}
						});
						SkyLyricsClient.LOG.info("Test hook: typed {} characters into {}", arg.codePointCount(0, arg.length()), screen.getClass().getSimpleName());
					}
				}
				case "burst" -> startBurst(arg, false);
				case "burstat" -> startBurst(arg, true);
				case "hover" -> {
					String[] xy = arg.split("\\s+");
					Screen screen = currentScreen(client);
					if (screen == null) {
						SkyLyricsClient.LOG.info("Test hook: no screen is open, mouse not moved");
					} else {
						screen.mouseMoved(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
						SkyLyricsClient.LOG.info("Test hook: mouse moved over {}", screen.getClass().getSimpleName());
					}
				}
				case "press" -> {
					// Presses the open screen's button whose label contains the text, e.g. "Respawn"
					//? if >=26.2 {
					/*Screen screen = client.gui.screen();
					*///?} else {
					Screen screen = client.screen;
					//?}
					var button = screen == null ? null : screen.children().stream()
							.filter(c -> c instanceof net.minecraft.client.gui.components.AbstractButton b
									&& b.getMessage().getString().contains(arg))
							.map(c -> (net.minecraft.client.gui.components.AbstractButton) c)
							.findFirst().orElseThrow(() -> new IllegalArgumentException("no button " + arg));
					//? if >=1.21.9 {
					button.onPress(new net.minecraft.client.input.MouseButtonEvent(button.getX() + 1, button.getY() + 1,
							new net.minecraft.client.input.MouseButtonInfo(0, 0)));
					//?} else {
					/*button.onPress();
					*///?}
				}
				//? if >=26.2 {
				/*case "hud" -> { if (client.gui.hud.isHidden() != arg.equals("off")) client.gui.hud.toggle(); }
				*///?} else {
				case "hud" -> client.options.hideGui = arg.equals("off");
				//?}
				case "view" -> client.options.setCameraType(CameraType.values()[Integer.parseInt(arg)]);
				case "lang" -> {
					client.getLanguageManager().setSelected(arg);
					client.options.languageCode = arg;
					client.reloadResourcePacks();
				}
				case "guiscale" -> {
					client.options.guiScale().set(Integer.parseInt(arg));
					//? if >=26.1 {
					/*client.resizeGui();
					*///?} else {
					client.resizeDisplay();
					//?}
				}
				case "cmd" -> {
					MinecraftServer server = client.getSingleplayerServer();
					if (server != null) {
						server.execute(() -> server.getCommands()
								.performPrefixedCommand(server.createCommandSourceStack(), arg));
					}
				}
				case "attack" -> client.options.keyAttack.setDown(arg.equals("on"));
				case "set" -> set(arg);
				case "lines" -> lines(client, arg);
			case "plain" -> plain(arg);
				case "uncap" -> {
					client.options.enableVsync().set(false);
					client.options.framerateLimit().set(260);
					// Otherwise the game drops to 30 fps after a minute without input, which spoils a measurement
					//? if >=1.21.2 {
					client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
					//?}
				}
				case "fps" -> {
					fpsSeen.clear();
					fpsLeft = ((arg.isEmpty() ? 5 : Integer.parseInt(arg)) + 1) * 20;
				}
				//? if >=26.2 {
				/*case "shot" -> Screenshot.grab(client.gameDirectory, arg + ".png", client.gameRenderer.mainRenderTarget(), 1,
				*///?} else if >=1.21.6 {
				case "shot" -> Screenshot.grab(client.gameDirectory, arg + ".png", client.getMainRenderTarget(), 1,
				//?} else {
				/*case "shot" -> Screenshot.grab(client.gameDirectory, arg + ".png", client.getMainRenderTarget(),
				*///?}
						message -> SkyLyricsClient.LOG.info("Test hook: {}", message.getString()));
				default -> SkyLyricsClient.LOG.warn("Test hook: unknown command {}", parts[0]);
			}
		} catch (RuntimeException e) {
			SkyLyricsClient.LOG.warn("Test hook command failed: {}", line, e);
		}
	}
}
