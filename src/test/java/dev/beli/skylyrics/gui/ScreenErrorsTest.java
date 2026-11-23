package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.LogCapture;
import dev.beli.skylyrics.config.SkyLyricsConfig;

/**
 * The game calls the mod's screens to draw them, to hand them keys and clicks, to read them out and to close them, and
 * wraps each call so that anything thrown out of it ends the game with a crash report ("Rendering screen", "keyPressed
 * event handler", "Narrating screen"). Every such method of the mod's screens catches its own errors instead: the screen
 * goes back to the one it was opened from and says something went wrong (see {@link ScreenFault}).
 */
class ScreenErrorsTest {
	private static final Path SOURCES = Path.of("src/client/java");
	private static final List<Class<? extends Screen>> SCREENS = List.of(SkyLyricsScreen.class, LibraryScreen.class, IntroScreen.class);

	/** The methods of a screen that the game calls by itself, by every name they have had from 1.21.1 to 26.3. */
	private static final Set<String> GAME_CALLS = Set.of("render", "renderBackground", "extractRenderState", "extractBackground",
			"renderWithTooltip", "renderWithTooltipAndSubtitles", "extractRenderStateWithTooltipAndSubtitles", "tick", "init",
			"rebuildWidgets", "repositionElements", "resize", "setInitialFocus", "added", "removed", "onClose", "keyPressed",
			"keyReleased", "charTyped", "mouseClicked", "mouseReleased", "mouseDragged", "mouseScrolled", "mouseMoved",
			"updateNarrationState", "getNarrationMessage", "onFilesDrop", "isPauseScreen", "shouldCloseOnEsc");

	// ---- Each method, made to throw ------------------------------------------------------------------------------

	/** A screen made without its constructor, which needs a running game: its fields are empty, so its methods throw at once, as a bug would. */
	private static <T> T make(Class<T> type) throws Exception {
		Class<?> unsafe = Class.forName("sun.misc.Unsafe");
		Field f = unsafe.getDeclaredField("theUnsafe");
		f.setAccessible(true);
		return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(f.get(null), type));
	}

	/** Gives the screen the fault its constructor would have made, leaving by counting instead of going to another screen. */
	private static void giveFault(Screen screen, AtomicInteger left) throws IllegalAccessException {
		Field f;
		try {
			f = screen.getClass().getDeclaredField("fault");
		} catch (NoSuchFieldException e) {
			return; // a screen without one lets its errors out
		}
		f.setAccessible(true);
		f.set(screen, new ScreenFault(screen.getClass().getSimpleName(), left::incrementAndGet));
	}

	private static Object empty(Class<?> type) {
		if (type == boolean.class) {
			return false;
		}
		if (type == char.class) {
			return 'a';
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0f;
		}
		if (type == double.class) {
			return 0d;
		}
		if (type == byte.class || type == short.class || type == int.class) {
			return type == int.class ? (Object) 0 : type == short.class ? (Object) (short) 0 : (Object) (byte) 0;
		}
		return null;
	}

	/** The static fields the screens keep between openings (the last tab, the last section), so the test leaves them as it found them. */
	private static Map<Field, Object> statics() throws IllegalAccessException {
		Map<Field, Object> kept = new HashMap<>();
		for (Class<?> type : SCREENS) {
			for (Field f : type.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())) {
					f.setAccessible(true);
					kept.put(f, f.get(null));
				}
			}
		}
		return kept;
	}

	/** Swaps the settings the screens read for {@code cfg}, returning the old ones (null when none were loaded yet). */
	private static SkyLyricsConfig swapSettings(SkyLyricsConfig cfg) throws Exception {
		Field f = SkyLyricsConfig.class.getDeclaredField("instance");
		f.setAccessible(true);
		SkyLyricsConfig old = (SkyLyricsConfig) f.get(null);
		f.set(null, cfg);
		return old;
	}

	@Test
	void anErrorInAScreenClosesItInsteadOfEndingTheGame() throws Exception {
		// Settings that never reach the disk, in case a method gets as far as saving them
		SkyLyricsConfig settings = new SkyLyricsConfig();
		Field blocked = SkyLyricsConfig.class.getDeclaredField("saveBlocked");
		blocked.setAccessible(true);
		blocked.setBoolean(settings, true);
		SkyLyricsConfig oldSettings = swapSettings(settings);
		Map<Field, Object> kept = statics();
		List<String> escaped = new ArrayList<>();
		List<String> threw = new ArrayList<>();
		List<String> wrongLeaves = new ArrayList<>();
		try (LogCapture log = new LogCapture()) {
			for (Class<? extends Screen> type : SCREENS) {
				for (Method m : type.getDeclaredMethods()) {
					if (m.isSynthetic() || m.isBridge() || Modifier.isStatic(m.getModifiers()) || !GAME_CALLS.contains(m.getName())) {
						continue;
					}
					String name = type.getSimpleName() + "." + m.getName() + Stream.of(m.getParameterTypes())
							.map(Class::getSimpleName).collect(Collectors.joining(", ", "(", ")"));
					Screen screen = make(type);
					AtomicInteger left = new AtomicInteger();
					giveFault(screen, left);
					Object[] args = Stream.of(m.getParameterTypes()).map(ScreenErrorsTest::empty).toArray();
					int before = log.lines().size();
					m.setAccessible(true);
					try {
						m.invoke(screen, args);
					} catch (InvocationTargetException e) {
						escaped.add(name + ": " + e.getCause());
						continue;
					}
					if (log.lines().subList(before, log.lines().size()).stream().anyMatch(l -> l.startsWith("ERROR Error in"))) {
						threw.add(name);
						// Closing it is what removed() is part of: there it is only logged
						int expected = m.getName().equals("removed") ? 0 : 1;
						if (left.get() != expected) {
							wrongLeaves.add(name + " left " + left.get() + " times");
						}
					}
				}
			}
		} finally {
			swapSettings(oldSettings);
			for (Map.Entry<Field, Object> e : kept.entrySet()) {
				e.getKey().set(null, e.getValue());
			}
		}
		assertEquals(List.of(), escaped, "these let their error out to the game, which ends it with a crash report");
		assertEquals(List.of(), wrongLeaves, "a screen that hit an error goes back to the one before it once (not on closing)");
		// The test must have made them throw, or it shows nothing: drawing, keys, clicks, narration and closing of all three
		assertTrue(threw.size() >= 25, "only " + threw.size() + " methods were made to throw: " + threw);
	}

	// ---- Every version's code, read from the sources -------------------------------------------------------------

	/**
	 * A method of the screen class itself (one tab in), as any version has it: the other versions' lines are comments
	 * ("/*public void extractRenderState(...) {").
	 */
	private static final Pattern HEADER = Pattern.compile(
			"^\\t(?:/\\*)?(?:@Override\\s+)?(?:public|protected)\\s+(?:final\\s+)?[\\w.<>\\[\\]?, ]+?\\s+(\\w+)\\(([^)]*)\\)\\s*\\{\\s*$");
	/** A body that only hands the call on to another of the game's methods, with the event the newer versions pass. */
	private static final Pattern HANDS_ON = Pattern.compile("^return (\\w+)\\(new \\w+\\([^;]*\\)(?:, [\\w ,]+)?\\);$");

	private static boolean stonecutterOrComment(String line) {
		return line.isEmpty() || line.startsWith("//") || line.startsWith("*///") || line.startsWith("*/") || line.startsWith("/*")
				|| line.startsWith("*") || line.equals("@Override");
	}

	/** What is wrong with the method whose header is at {@code at}, or null when it catches its own errors (or can't throw). */
	private static String problem(List<String> lines, int at) {
		List<String> body = new ArrayList<>();
		for (int i = at + 1; i < lines.size() && !lines.get(i).equals("\t}"); i++) {
			String line = lines.get(i).strip();
			if (HEADER.matcher(lines.get(i)).matches() || stonecutterOrComment(line)) {
				continue; // another version's header for the same body, or a version switch
			}
			body.add(line);
		}
		if (body.size() == 1 && (body.get(0).equals("return true;") || body.get(0).equals("return false;"))) {
			return null; // a fixed answer can't throw
		}
		Matcher handOn = body.size() == 1 ? HANDS_ON.matcher(body.get(0)) : null;
		if (handOn != null && handOn.matches() && GAME_CALLS.contains(handOn.group(1))) {
			return null; // checked where it goes
		}
		if (body.isEmpty() || !body.get(0).equals("try {")) {
			return "does not start with try";
		}
		String text = String.join("\n", body);
		int caught = text.indexOf("} catch (RuntimeException | LinkageError e) {");
		if (caught < 0 || !text.substring(caught).contains("fault.failed")) {
			return "does not hand RuntimeException and LinkageError to its fault";
		}
		return null;
	}

	@Test
	void everyVersionsScreenMethodsCatchTheirErrors() throws IOException {
		List<Path> files;
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			files = walk.filter(f -> f.toString().endsWith(".java")).sorted().toList();
		}
		List<String> screens = new ArrayList<>();
		List<String> bare = new ArrayList<>();
		int checked = 0;
		for (Path f : files) {
			String text = Files.readString(f, StandardCharsets.UTF_8);
			if (!Pattern.compile("\\bclass \\w+ extends Screen\\b").matcher(text).find()) {
				continue;
			}
			screens.add(f.getFileName().toString());
			List<String> lines = text.lines().toList();
			for (int i = 0; i < lines.size(); i++) {
				Matcher m = HEADER.matcher(lines.get(i));
				if (!m.matches() || !GAME_CALLS.contains(m.group(1))) {
					continue;
				}
				checked++;
				String problem = problem(lines, i);
				if (problem != null) {
					bare.add(f.getFileName() + ":" + (i + 1) + " " + m.group(1) + ": " + problem);
				}
			}
		}
		assertTrue(screens.containsAll(List.of("SkyLyricsScreen.java", "LibraryScreen.java", "IntroScreen.java")), "screens found: " + screens);
		assertTrue(checked >= 40, "only " + checked + " screen methods found");
		assertEquals(List.of(), bare, "screen methods the game calls that let an error out (it ends the game)");
	}

	/** The argument list of the call whose bracket opens just before {@code open}, split at its top-level commas. */
	private static List<String> arguments(String text, int open) {
		List<String> out = new ArrayList<>();
		int depth = 0, start = open;
		for (int i = open; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == '(' || ch == '{' || ch == '[') {
				depth++;
			} else if (ch == ')' || ch == '}' || ch == ']') {
				if (depth == 0) {
					out.add(text.substring(start, i).strip());
					return out;
				}
				depth--;
			} else if (ch == ',' && depth == 0) {
				out.add(text.substring(start, i).strip());
				start = i + 1;
			}
		}
		return out;
	}

	@Test
	void theTourButtonsCatchTheirErrors() throws IOException {
		// A Minecraft button runs its action from the game's own click and key handling, outside the screen's methods
		String text = Files.readString(SOURCES.resolve("dev/beli/skylyrics/gui/IntroScreen.java"), StandardCharsets.UTF_8);
		Matcher m = Pattern.compile("Button\\.builder\\(").matcher(text);
		int found = 0;
		while (m.find()) {
			found++;
			List<String> args = arguments(text, m.end());
			assertEquals(2, args.size(), "Button.builder's arguments: " + args);
			String press = args.get(1);
			assertTrue(press.contains("} catch (RuntimeException | LinkageError e) {") && press.contains("fault.failed("),
					"a tour button's action lets its error out: " + press);
		}
		assertTrue(found > 0, "the tour's buttons should be found");
	}
}
