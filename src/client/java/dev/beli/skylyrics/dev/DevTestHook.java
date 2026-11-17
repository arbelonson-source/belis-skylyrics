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
 * {@code use} presses the Use key (right click) once, as a click would: with the player looking at a bed at night they
 * lie down, and an item like a spyglass starts to be used. {@code hold use on} keeps the Use key held down (a spyglass
 * stays up) and {@code hold use off} lets go. A held key is let go by itself when the world closes, so it can't stay stuck.
 * <p>
 * {@code hold <mapping> <ms>} holds a key binding down for that many milliseconds, then lets go by itself, as the player
 * holding the key would: {@code hold key.playerlist 2000} shows the player list, {@code hold key.sneak 500} sneaks (the full name
 * of the binding as options.txt has it without {@code key_}; {@code playerlist} alone also works). An unknown name logs
 * "Test hook: no key mapping ..." and nothing else happens.
 * <p>
 * {@code debug on|off} opens or closes the F3 debug screen, as the F3 key does.
 * <p>
 * World changes: {@code pause} opens the pause menu, as Escape does in a world. {@code quit} leaves the world to the title
 * screen, as the pause menu's "Save and Quit to Title" does (the single-player world is saved before the command returns).
 * {@code join <world folder name>} (e.g. {@code join SkyTest}) joins that single-player world from the title screen, as
 * quick play does. {@code inventory} opens the player's inventory screen (the survival one; the creative inventory shows no
 * paper doll, so run {@code cmd gamemode survival @p} first). Each only logs "Test hook: ... (not in a world)" or similar
 * when used in the wrong state: {@code pause}, {@code quit} and {@code inventory} need a world (and for the first and
 * last, no screen open), {@code join} needs the title screen or any state without a world.
 * <p>
 * {@code burst <name> <count> <everyMs>} grabs the game frame {@code count} times (1 to 60), {@code everyMs} apart (0 = every
 * rendered frame), one grab per rendered frame, and saves them as {@code screenshots/<name>_00.png}, {@code _01}, ...
 * The frames are kept in memory and only written (on another thread) after the last grab, so encoding does not slow
 * the frames being caught. {@code burstat <name> <count> <everyMs>} first waits until the lyrics move to the next line
 * (so the first frame is the crosshair flash's peak, the start of a slide), and gives up after 10 s. One burst at a time.
 * <p>
 * {@code click <x> <y>} left-clicks and releases at that spot of the open screen, in scaled GUI pixels. {@code scroll <amount>}
 * scrolls the open screen's centre by that much (negative scrolls the other way). {@code search <text>} types into the
 * settings screen's search box. {@code screen <class name>} opens another mod's screen by class name (e.g. Mod Menu's list).
 * {@code title} opens the title screen, which {@code screen} can't in the production client. {@code set <field> <value>} changes
 * one config field without saving it (the settings screen saves everything when it closes). {@code fastlines <ms> <a | b | c>} is {@code lines} with one line every
 * {@code ms} milliseconds. {@code attack on|off} holds the Attack key down or lets go.
 * <p>
 * {@code barprobe} logs, at INFO with "Test hook: barprobe" in each line, the numbers the top bar used for its next
 * frame that draws a lyric line: the line's layout, each word's measured and drawn width and glyph edges, the
 * highlight box and the pose matrices (pose and glyph edges only where the game version offers them).
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

	/** Whether {@code hold use on} has the Use key held down. */
	private boolean holdingUse;
	/** Key bindings held down by {@code hold <mapping> <ms>}, with their release times. */
	private final DevKeys.HoldSchedule<net.minecraft.client.KeyMapping> holds = new DevKeys.HoldSchedule<>();

	/** Called once per rendered frame by the game loop; a no-op unless the test hook is on. */
	public static void frame(Minecraft client) {
		DevTestHook hook = active;
		if (hook != null) {
			if (hook.holdingUse && client.level == null) {
				hook.holdingUse = false; // the world closed: never leave the key stuck down
				client.options.keyUse.setDown(false);
			}
			hook.keepHolds(client);
			try {
				hook.onFrame(client);
			} catch (RuntimeException e) {
				SkyLyricsClient.LOG.warn("Test hook burst failed", e);
				hook.abortBurst();
			}
		}
	}

	/**
	 * Lets go of the held key bindings whose time is up and presses the others again. Vanilla resets keys when a screen
	 * opens or closes and when the window changes focus, so a hold is pressed anew every frame and tick until its time ends.
	 */
	private void keepHolds(Minecraft client) {
		for (net.minecraft.client.KeyMapping mapping : holds.expire(System.nanoTime() / 1_000_000L)) {
			mapping.setDown(false);
			SkyLyricsClient.LOG.info("Test hook: released {}", mapping.getName());
		}
		for (net.minecraft.client.KeyMapping mapping : holds.held()) {
			mapping.setDown(true);
		}
	}

	/** "hold <mapping> <ms>": presses a key binding as its key would and lets go after the time, even if the script moves on. */
	private void hold(Minecraft client, String arg) {
		Boolean legacy = DevKeys.legacyUse(arg);
		if (legacy != null) {
			holdingUse = legacy;
			client.options.keyUse.setDown(holdingUse);
			return;
		}
		DevKeys.Hold hold = DevKeys.parseHold(arg);
		net.minecraft.client.KeyMapping found = null;
		for (net.minecraft.client.KeyMapping mapping : client.options.keyMappings) {
			if (mapping.getName().equals(hold.mapping())) {
				found = mapping;
				break;
			}
		}
		if (found == null) {
			SkyLyricsClient.LOG.warn("Test hook: no key mapping {}", hold.mapping());
			return;
		}
		if (!holds.held().contains(found)) {
			// As the keyboard handler does on a key press: the click is counted too (Use and Attack read clicks, not the down state)
			com.mojang.blaze3d.platform.InputConstants.Key key = com.mojang.blaze3d.platform.InputConstants.getKey(found.saveString());
			if (key.getValue() != com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue()) {
				net.minecraft.client.KeyMapping.click(key);
			}
		}
		holds.hold(found, System.nanoTime() / 1_000_000L, hold.ms());
		found.setDown(true);
		SkyLyricsClient.LOG.info("Test hook: holding {} for {} ms", hold.mapping(), hold.ms());
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
			float since = SkyLyricsClient.get().renderer().secondsSinceFlashStart();
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
		return TestHookSwitch.enabled();
	}

	public void tick(Minecraft client) {
		keepHolds(client);
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
		swapLyrics(LrcParser.parseSynced(TimedLrc.of(arg, 4000, DemoSource.TRACK.lengthMs())));
	}

	/** "fastlines ms a | b | c": like "lines", but one line every {@code ms} milliseconds (fast songs, rap). */
	private static void fastLines(String arg) {
		String[] stepAndLines = arg.split("\\s+", 2);
		swapLyrics(LrcParser.parseSynced(TimedLrc.of(stepAndLines.length > 1 ? stepAndLines[1] : "", Integer.parseInt(stepAndLines[0]),
				DemoSource.TRACK.lengthMs())));
	}

	/** "plain a | b | c": like "lines", but as lyrics without timing, spread over the demo song the way plain lyrics are. */
	private static void plain(String arg) {
		swapLyrics(LrcParser.parsePlain(String.join("\n", arg.split("\\s*\\|\\s*")), DemoSource.TRACK.lengthMs()));
	}

	private static void swapLyrics(dev.beli.skylyrics.lyrics.Lyrics lyrics) {
		SkyLyricsClient.get().lyrics().swapLyricsForTest(lyrics);
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
				value = SetValue.string(kv[1]);
			}
			field.set(SkyLyricsConfig.get(), value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException("no config field " + kv[0], e);
		}
	}

	/** The Minecraft key code of a {@link DevKeys.Key} name: a GLFW key code, or an SDL scancode on 26.3 and newer. */
	private static int keyCode(String name) {
		char first = name.charAt(0);
		if (name.length() == 1 && first >= 'a' && first <= 'z') {
			return com.mojang.blaze3d.platform.InputConstants.KEY_A + (first - 'a');
		}
		if (name.length() == 1) {
			int digit = first - '0';
			//? if >=26.3 {
			/*return digit == 0 ? com.mojang.blaze3d.platform.InputConstants.KEY_0 : com.mojang.blaze3d.platform.InputConstants.KEY_1 + digit - 1;
			*///?} else {
			return com.mojang.blaze3d.platform.InputConstants.KEY_0 + digit;
			//?}
		}
		if (first == 'f' && Character.isDigit(name.charAt(1))) {
			int n = Integer.parseInt(name.substring(1));
			return n <= 12 ? com.mojang.blaze3d.platform.InputConstants.KEY_F1 + n - 1 : com.mojang.blaze3d.platform.InputConstants.KEY_F13 + n - 13;
		}
		return switch (name) {
			case "space" -> com.mojang.blaze3d.platform.InputConstants.KEY_SPACE;
			case "escape" -> com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE;
			case "enter" -> com.mojang.blaze3d.platform.InputConstants.KEY_RETURN;
			case "tab" -> com.mojang.blaze3d.platform.InputConstants.KEY_TAB;
			case "backspace" -> com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE;
			case "insert" -> com.mojang.blaze3d.platform.InputConstants.KEY_INSERT;
			case "delete" -> com.mojang.blaze3d.platform.InputConstants.KEY_DELETE;
			case "right" -> com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT;
			case "left" -> com.mojang.blaze3d.platform.InputConstants.KEY_LEFT;
			case "down" -> com.mojang.blaze3d.platform.InputConstants.KEY_DOWN;
			case "up" -> com.mojang.blaze3d.platform.InputConstants.KEY_UP;
			case "page_up" -> com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP;
			case "page_down" -> com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN;
			case "home" -> com.mojang.blaze3d.platform.InputConstants.KEY_HOME;
			case "end" -> com.mojang.blaze3d.platform.InputConstants.KEY_END;
			default -> throw new IllegalArgumentException("unknown key " + name);
		};
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
	/** Shows a screen on every version (the screen moved to the Gui class in 26.2). */
	private static void showScreen(Minecraft client, Screen screen) {
		//? if >=26.2 {
		/*client.gui.setScreen(screen);
		*///?} else {
		client.setScreen(screen);
		//?}
	}

	/**
	 * "Save and Quit to Title" as the pause screen's button does it. 1.21.10 and newer call
	 * {@code Minecraft.disconnectFromWorld(Component)}, 1.21.8 the static {@code PauseScreen.disconnectFromWorld(Minecraft, Component)},
	 * and 1.21.5 and older the pause screen's private onDisconnect, whose steps are repeated here for the single-player case.
	 */
	private static void leaveWorld(Minecraft client) {
		//? if >=1.21.10 {
		client.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
		//?} else if >=1.21.8 {
		/*net.minecraft.client.gui.screens.PauseScreen.disconnectFromWorld(client, net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
		*///?} else {
		/*client.level.disconnect();
		if (client.isLocalServer()) {
			client.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(net.minecraft.network.chat.Component.translatable("menu.savingLevel")));
		} else {
			client.disconnect();
		}
		client.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
		*///?}
	}

	/** "join <folder>": opens a single-player world as quick play does: the same check that it exists, then WorldOpenFlows.openWorld. */
	private static void joinWorld(Minecraft client, String folder) {
		if (folder.isEmpty()) {
			SkyLyricsClient.LOG.warn("Test hook: join needs a world folder name");
		} else if (client.level != null || client.getSingleplayerServer() != null) {
			SkyLyricsClient.LOG.info("Test hook: join {} ignored (already in a world)", folder);
		} else if (!client.getLevelSource().levelExists(folder)) {
			SkyLyricsClient.LOG.warn("Test hook: join {} ignored (no such world folder)", folder);
		} else {
			client.createWorldOpenFlows().openWorld(folder, () -> {
				SkyLyricsClient.LOG.warn("Test hook: join {} was cancelled or failed", folder);
				showScreen(client, new net.minecraft.client.gui.screens.TitleScreen());
			});
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
				case "title" -> {
					// The title screen, which "screen" can't open in the production client (its class name is remapped there)
					//? if >=26.2 {
					/*client.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
					*///?} else {
					client.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
					//?}
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
								new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0));
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
						int code = keyCode(key.name());
						//? if >=26.3 {
						/*// SDL: the scancode, then the SDL keycode that Screen's Tab and arrow handling reads, and SDL modifier bits
						int mods = DevKeys.sdlModifiers(key);
						int keycode = DevKeys.sdlKeycode(key, code);
						screen.keyPressed(new net.minecraft.client.input.KeyEvent(code, keycode, mods));
						if (typed != 0) {
							screen.charTyped(charEvent(typed, mods));
						}
						screen.keyReleased(new net.minecraft.client.input.KeyEvent(code, keycode, mods));
						*///?} else if >=1.21.9 {
						int mods = DevKeys.glfwModifiers(key);
						screen.keyPressed(new net.minecraft.client.input.KeyEvent(code, 0, mods));
						if (typed != 0) {
							screen.charTyped(charEvent(typed, mods));
						}
						screen.keyReleased(new net.minecraft.client.input.KeyEvent(code, 0, mods));
						//?} else {
						/*int mods = DevKeys.glfwModifiers(key);
						screen.keyPressed(code, 0, mods);
						if (typed != 0) {
							screen.charTyped((char) typed, mods);
						}
						screen.keyReleased(code, 0, mods);
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
				case "pause" -> {
					if (client.level == null || client.player == null) {
						SkyLyricsClient.LOG.info("Test hook: pause ignored (not in a world)");
					} else if (currentScreen(client) != null) {
						SkyLyricsClient.LOG.info("Test hook: pause ignored (a screen is already open)");
					} else {
						showScreen(client, new net.minecraft.client.gui.screens.PauseScreen(true));
					}
				}
				case "quit" -> {
					if (client.level == null) {
						SkyLyricsClient.LOG.info("Test hook: quit ignored (not in a world)");
					} else {
						leaveWorld(client);
					}
				}
				case "join" -> joinWorld(client, arg);
				case "inventory" -> {
					if (client.level == null || client.player == null) {
						SkyLyricsClient.LOG.info("Test hook: inventory ignored (not in a world)");
					} else {
						showScreen(client, new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
					}
				}
				case "burst" -> startBurst(arg, false);
				case "burstat" -> startBurst(arg, true);
				case "barprobe" -> {
					dev.beli.skylyrics.render.BarProbe.request();
					SkyLyricsClient.LOG.info("Test hook: barprobe waits for the next top bar lyric frame");
				}
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
							new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)));
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
				case "debug" -> {
					boolean on = arg.equals("on");
					//? if >=1.21.11 {
					client.debugEntries.setOverlayVisible(on);
					//?} else if >=1.21.9 {
					/*client.debugEntries.setF3Visible(on);
					*///?} else {
					/*if (client.getDebugOverlay().showDebugScreen() != on) {
						client.getDebugOverlay().toggleOverlay();
					}
					*///?}
				}
				case "use" -> net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(client.options.keyUse.saveString()));
				case "hold" -> hold(client, arg);
				case "attack" -> client.options.keyAttack.setDown(arg.equals("on"));
				case "set" -> set(arg);
				case "lines" -> lines(client, arg);
				case "fastlines" -> fastLines(arg);
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
