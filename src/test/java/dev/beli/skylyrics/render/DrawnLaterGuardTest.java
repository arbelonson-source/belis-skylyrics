package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.beli.skylyrics.LogCapture;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyEffect;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.junit.jupiter.api.Test;

/**
 * From 1.21.9 on the game draws the shapes the mod hands it (submitCustomGeometry) later in the frame, after the mod's own
 * drawing has returned and outside its Guard. An error there used to end the game; on 1.21.1 to 1.21.8 the same error
 * only paused that effect for a few seconds. Every shape handed over now runs under its effect's Guard itself.
 */
class DrawnLaterGuardTest {
	private static final Path SOURCES = Path.of("src/client/java");
	/**
	 * A shape type that runs under Guard itself: an interface on the game's shape type whose body calls Guard.run. The
	 * collector's name is left open: before 1.21.9 the build renames it in this file too (to LegacyCollector), not in the
	 * sources read here.
	 */
	private static final Pattern GUARDED = Pattern.compile("interface (\\w+) extends \\w*Collector\\.CustomGeometryRenderer\\b[^{]*\\{([^}]*)\\}");
	private static final Pattern SUBMIT = Pattern.compile("\\.submitCustomGeometry\\(");

	/** The third argument of the call whose argument list starts at {@code open} (just past its bracket). */
	private static String thirdArgument(String text, int open) {
		int depth = 0, arg = 0, start = open;
		for (int i = open; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == '(' || ch == '{' || ch == '[') {
				depth++;
			} else if (ch == ')' || ch == '}' || ch == ']') {
				if (depth == 0) {
					return arg == 2 ? text.substring(start, i).strip() : "";
				}
				depth--;
			} else if (ch == ',' && depth == 0) {
				if (arg == 2) {
					return text.substring(start, i).strip();
				}
				arg++;
				start = i + 1;
			}
		}
		return "";
	}

	@Test
	void everyShapeHandedToTheGameGuardsItself() throws IOException {
		List<Path> files;
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			files = walk.filter(f -> f.toString().endsWith(".java")).sorted().toList();
		}
		Set<String> guarded = new HashSet<>();
		List<String> texts = new ArrayList<>();
		for (Path f : files) {
			String text = Files.readString(f, StandardCharsets.UTF_8);
			texts.add(text);
			Matcher m = GUARDED.matcher(text);
			while (m.find()) {
				if (m.group(2).contains("Guard.run(")) {
					guarded.add(m.group(1));
				}
			}
		}
		List<String> bare = new ArrayList<>();
		int calls = 0;
		for (int n = 0; n < files.size(); n++) {
			Path f = files.get(n);
			// Before 1.21.9 it is the mod's own stand-in, which draws at once, inside the caller's Guard
			if (f.endsWith("LegacyCollector.java")) {
				continue;
			}
			String text = texts.get(n);
			Matcher m = SUBMIT.matcher(text);
			while (m.find()) {
				calls++;
				String shape = thirdArgument(text, m.end());
				boolean ok = shape.matches("\\w+") && guarded.stream()
						.anyMatch(type -> Pattern.compile("\\b" + type + "\\s+" + shape + "\\b").matcher(text).find());
				if (!ok) {
					bare.add(SOURCES.relativize(f) + ": " + shape);
				}
			}
		}
		assertTrue(calls >= 5, "the sky's four layers and the hat should be found, found " + calls + " calls");
		assertEquals(List.of(), bare, "shapes handed to the game unguarded (an error in them ends the game on 1.21.9+); guarded types: " + guarded);
	}

	/** A vertex consumer whose every call throws, as a broken buffer or a bug in the shape would; counts the calls. */
	private static VertexConsumer broken(AtomicInteger calls) {
		return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class<?>[] {VertexConsumer.class},
				(proxy, method, args) -> {
					if (method.getDeclaringClass() == Object.class) {
						return method.invoke(calls, args);
					}
					calls.incrementAndGet();
					throw new IllegalStateException("test: the sky's buffer is broken");
				});
	}

	private static Object get(Object o, String name) throws Exception {
		Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(o);
	}

	private static void set(Object o, String name, Object v) throws Exception {
		Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, v);
	}

	@Test
	void aSkyLayerThatThrowsPausesTheSkyInsteadOfEndingTheGame() throws Exception {
		SkyEffects e = new SkyEffects(null, null, null, null);
		set(e, "effect", SkyEffect.ECLIPSE);
		set(e, "amount", 1f);
		set(e, "far", 100f);
		set(e, "baseYaw", 0f);
		set(e, "pixel", 0.002f);
		set(e, "clock", 12.0);
		// The view this frame was "already worked out": the fresh one, which counts the whole sky as on screen
		set(e, "viewFrame", ScreenLayer.frames());
		PoseStack.Pose pose = new PoseStack().last();
		try (LogCapture log = new LogCapture()) {
			AtomicInteger glow = new AtomicInteger();
			SubmitNodeCollector.CustomGeometryRenderer drawer = (SubmitNodeCollector.CustomGeometryRenderer) get(e, "drawer");
			assertDoesNotThrow(() -> drawer.render(pose, broken(glow)), "the eclipse's glow let its error out to the game");
			assertTrue(glow.get() > 0, "the glow should have drawn (and so thrown)");
			// The sky sits out for a few seconds: the rest of this frame's layers don't run at all
			for (String name : new String[] {"painter", "discDrawer", "frontDrawer"}) {
				AtomicInteger calls = new AtomicInteger();
				set(e, "corner", 3);
				SubmitNodeCollector.CustomGeometryRenderer layer = (SubmitNodeCollector.CustomGeometryRenderer) get(e, name);
				assertDoesNotThrow(() -> layer.render(pose, broken(calls)), name + " let its error out to the game");
				assertEquals(0, calls.get(), name + " should sit out while the sky is paused");
				assertEquals(3, get(e, "corner"), name + " should not have started a frame while the sky is paused");
			}
			List<String> errors = log.lines().stream().filter(l -> l.startsWith("ERROR")).toList();
			assertEquals(1, errors.size(), "logged once: " + errors);
			assertTrue(errors.get(0).contains("Error in sky effects") && errors.get(0).contains("the sky's buffer is broken"), errors.get(0));
		}
	}

	@Test
	void aHatPartThatThrowsPausesTheHatInsteadOfEndingTheGame() {
		// The hat's parts are lambdas made in its drawing, so they are checked through the type every one of them takes
		PoseStack.Pose pose = new PoseStack().last();
		AtomicInteger ran = new AtomicInteger();
		HatLayer.HatPart brokenPart = (p, vc) -> {
			ran.incrementAndGet();
			throw new IllegalStateException("test: a hat part broke");
		};
		HatLayer.HatPart nextPart = (p, vc) -> ran.incrementAndGet();
		try (LogCapture log = new LogCapture()) {
			assertDoesNotThrow(() -> brokenPart.render(pose, broken(new AtomicInteger())), "a hat part let its error out to the game");
			nextPart.render(pose, broken(new AtomicInteger()));
			assertEquals(1, ran.get(), "the rest of the hat should sit out while it is paused");
			List<String> errors = log.lines().stream().filter(l -> l.startsWith("ERROR")).toList();
			assertEquals(1, errors.size(), "logged once: " + errors);
			assertTrue(errors.get(0).contains("Error in hat") && errors.get(0).contains("a hat part broke"), errors.get(0));
		}
	}
}
