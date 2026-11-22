package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A steady frame of the World layout (a song playing, nothing changing but time) builds next to nothing on the heap:
 * each card keeps its colour's style and its letters as handed to the game, instead of new ones for every copy (shadow,
 * outline, glow, letters) of every word, every frame.
 */
class WorldFrameGarbageTest {
	/** The game keeps the text it is given until the frame is drawn, so it can't be thrown away at once. */
	private static final Object[] KEPT = new Object[64];
	private static int submits;
	/** What a frame drew while {@link #record} is on: each copy's colours and letters. */
	private static final StringBuilder DRAWN = new StringBuilder();
	private static boolean record;
	/** The texts a frame hands the game, in order, while {@link #collect} is on. */
	private static final List<Object> HANDED = new ArrayList<>();
	private static boolean collect;

	@BeforeEach
	void measureByLength() {
		WorldWords.measureHook = t -> t.length() * 4.0;
	}

	@AfterEach
	void done() {
		WorldWords.measureHook = null;
		record = false;
		collect = false;
		HANDED.clear();
	}

	/** A stand-in for the game's collector that keeps what it is handed; 1.21.8 and older draw at once, which needs a game. */
	private static SubmitNodeCollector collector() {
		//? if >=1.21.9 {
		return (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
				new Class<?>[] {SubmitNodeCollector.class}, (p, m, a) -> {
					if (m.getName().equals("submitText")) {
						submits++;
						KEPT[submits & 63] = a[3];
						if (collect) {
							HANDED.add(a[3]);
						}
						if (record) {
							((FormattedCharSequence) a[3]).accept((i, style, cp) -> {
								TextColor colour = style.getColor();
								DRAWN.append(colour == null ? "-" : Integer.toHexString(colour.getValue())).append(':')
										.append(style.isBold() ? "b" : "").appendCodePoint(cp).append(',');
								return true;
							});
							DRAWN.append(' ');
						}
					}
					return null;
				});
		//?} else {
		/*throw new org.opentest4j.TestAbortedException("1.21.8 and older draw the words straight into the game's buffers");
		*///?}
	}

	/** Two lines sung, so the first one's words are fading while the second one's are lit; the usual settings. */
	private static WorldWords sung() {
		WorldWords words = new WorldWords();
		words.seed(12345);
		words.tune(7, 1.2f, 1, 1, 1, false, true, false, 1);
		words.players(false, false); // no game, so no players to keep clear of
		Camera camera = new Camera();
		String first = "Neon skyline keep it spinning through the night", second = "Paper lanterns over silver water";
		long ns = 1_000_000_000L;
		for (int c = 0; c <= first.length(); c += 2) {
			words.update(0, first, c, false, Float.NaN, Float.NaN, camera, null, null, Style.EMPTY, ns + c * 50_000_000L);
		}
		for (int c = 0; c <= second.length(); c += 2) {
			words.update(1, second, c, false, Float.NaN, Float.NaN, camera, null, null, Style.EMPTY, 4 * ns + c * 50_000_000L);
		}
		return words;
	}

	private static final long NOW = 7_000_000_000L;

	private static void frame(WorldWords words, SubmitNodeCollector out, Style style, int rgb, float day, long now) {
		words.render(new PoseStack(), out, CAMERA, Font.DisplayMode.NORMAL, style, rgb, 1f, 0f, day, 1f, false, now);
	}

	private static final Camera CAMERA = new Camera();

	/**
	 * The texts one steady frame hands the game, after a few frames to settle. Each card's copies (shadow, outline,
	 * glow, letters) must come out as the very objects of the frame before: nothing rebuilt while nothing changes.
	 * (Counting the bytes a frame allocates instead depends on how far the JIT has got and on the stand-in collector's
	 * own boxing, so it differed between machines: GitHub's counted 1 to 4 KB where this PC counted 232 B.)
	 */
	private static List<Object> handed(WorldWords words, SubmitNodeCollector out, PoseStack pose, float day, long now) {
		HANDED.clear();
		collect = true;
		words.render(pose, out, CAMERA, Font.DisplayMode.NORMAL, Style.EMPTY, 0xFF88CC, 1f, 0f, day, 1f, false, now);
		collect = false;
		return new ArrayList<>(HANDED);
	}

	private static void sameTextsAsTheFrameBefore(float day, int atLeast) {
		WorldWords words = sung();
		SubmitNodeCollector out = collector();
		PoseStack pose = new PoseStack();
		for (int i = 0; i < 3; i++) {
			words.render(pose, out, CAMERA, Font.DisplayMode.NORMAL, Style.EMPTY, 0xFF88CC, 1f, 0f, day, 1f, false, NOW + i * 1000L);
		}
		List<Object> before = handed(words, out, pose, day, NOW + 3000L);
		List<Object> after = handed(words, out, pose, day, NOW + 4000L);
		assertTrue(before.size() >= atLeast, "texts drawn: " + before.size());
		assertEquals(before.size(), after.size(), "texts drawn");
		for (int i = 0; i < before.size(); i++) {
			assertSame(before.get(i), after.get(i), "text " + i + " of " + before.size() + " was built again");
		}
	}

	@Test
	void aSteadyNightFrameBuildsAlmostNothing() {
		sameTextsAsTheFrameBefore(0, 8);
	}

	@Test
	void aSteadyDayFrameWithItsOutlinesBuildsAlmostNothing() {
		sameTextsAsTheFrameBefore(1, 24);
	}

	private static String drawn(WorldWords words, SubmitNodeCollector out, Style style, int rgb, long now) {
		DRAWN.setLength(0);
		record = true;
		frame(words, out, style, rgb, 1, now);
		record = false;
		return DRAWN.toString();
	}

	@Test
	void theKeptStylesFollowTheColourAndTheFont() {
		WorldWords words = sung();
		SubmitNodeCollector out = collector();
		String pink = drawn(words, out, Style.EMPTY, 0xFF88CC, NOW);
		assertTrue(pink.contains("ff88cc:"), pink);
		assertTrue(pink.contains("5040c:"), "the shadow and outlines: " + pink);
		assertEquals(pink, drawn(words, out, Style.EMPTY, 0xFF88CC, NOW + 1000), "the same frame again");
		String green = drawn(words, out, Style.EMPTY, 0x55FF55, NOW + 2000);
		assertNotEquals(pink, green);
		assertEquals(pink.replace("ff88cc:", "55ff55:"), green, "only the lit words' colour changes");
		assertEquals(pink, drawn(words, out, Style.EMPTY, 0xFF88CC, NOW + 3000), "and back");
		// Another style for the words (a font setting): every copy is drawn in it, the shadow and outlines too
		String bold = drawn(words, out, Style.EMPTY.withBold(true), 0xFF88CC, NOW + 4000);
		assertEquals(pink.replace(":", ":b"), bold);
	}
}
