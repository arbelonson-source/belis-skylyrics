package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.List;

import com.sun.management.ThreadMXBean;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.lyrics.LyricLine;
import dev.beli.skylyrics.lyrics.Lyrics;

/**
 * A steady frame of the Lines and Arc layouts (a song playing, nothing changing but time) builds little on the heap: the
 * colours' styles are kept from frame to frame, and a cached row is found without building anything.
 */
class LinesFrameGarbageTest {
	/** Every letter 6 wide. */
	static final class SixFont extends Font {
		SixFont() {
			//? if >=1.21.9 {
			super(null);
			//?} else {
			/*super(id -> null, false);
			*///?}
		}

		final StringSplitter six = new StringSplitter((cp, style) -> 6);

		@Override
		public StringSplitter getSplitter() {
			return six;
		}
	}

	/** The game keeps the text it is given until the frame is drawn, and walks it once as it draws it. */
	private static final Object[] KEPT = new Object[256];
	private static int texts;
	private static final StringBuilder DRAWN = new StringBuilder();
	private static boolean record;

	private static final class Screen implements LyricsRenderer.Canvas {
		@Override
		public void push(float x, float y, float scale, float angle) {
		}

		@Override
		public void pop() {
		}

		@Override
		public void letters(float x, float y, FormattedCharSequence seq, boolean shadow, int argb, int behind) {
			seq.accept((i, style, cp) -> {
				if (record) {
					TextColor colour = style.getColor();
					DRAWN.append(colour == null ? "-" : Integer.toHexString(colour.getValue())).append(':').appendCodePoint(cp).append(',');
				}
				return true;
			});
			if (record) {
				DRAWN.append(Integer.toHexString(argb)).append(' ');
			}
			KEPT[texts++ & 255] = seq;
		}
	}

	private static final Lyrics SONG = new Lyrics(List.of(new LyricLine(1000, "Neon skyline over the water tonight"),
			new LyricLine(5000, "Every word is written in the stars above"), new LyricLine(9000, "Paper lanterns drifting slowly home"),
			new LyricLine(13000, "Silver rivers singing in the dark"), new LyricLine(17000, "One more chorus")), true, false);
	private static final Font FONT = new SixFont();
	private static final Screen SCREEN = new Screen();
	/** Halfway through the second line, the song paused there. */
	private static final long TIME = 7000, NOW = 50_000_000_000L;

	/** A frame of one layout: a new Draw, as each frame makes, and the lines or the arc line drawn with it. */
	private static final class Frames {
		final LyricsRenderer lyrics = new LyricsRenderer(null, null, null);
		final SkyLyricsConfig cfg;
		final MethodHandle draw, content;
		final Object shot;

		Frames(SkyLyricsConfig cfg) throws Exception {
			this.cfg = cfg;
			Field instance = SkyLyricsConfig.class.getDeclaredField("instance");
			instance.setAccessible(true);
			instance.set(null, cfg);
			set("shown", SONG);
			set("visibility", 1f);
			MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(LyricsRenderer.class, MethodHandles.lookup());
			Class<?> drawClass = Class.forName("dev.beli.skylyrics.render.LyricsRenderer$Draw");
			draw = lookup.unreflectConstructor(drawClass.getDeclaredConstructor(LyricsRenderer.class, LyricsRenderer.Canvas.class,
							Font.class, SkyLyricsConfig.class, int.class, float.class))
					.asType(MethodType.methodType(Object.class, LyricsRenderer.class, LyricsRenderer.Canvas.class, Font.class,
							SkyLyricsConfig.class, int.class, float.class));
			if (cfg.layout == SkyLyricsConfig.Layout.ARC) {
				Class<?> shotClass = Class.forName("dev.beli.skylyrics.render.LyricsRenderer$ArcShot");
				var make = shotClass.getDeclaredConstructor(int.class, float.class, float.class);
				make.setAccessible(true);
				shot = make.newInstance(1, -1f, 1f); // the line being sung, in place
				content = lookup.unreflect(drawClass.getDeclaredMethod("arcLine", shotClass, long.class, long.class))
						.asType(MethodType.methodType(void.class, Object.class, Object.class, long.class, long.class));
			} else {
				shot = null;
				content = lookup.unreflect(LyricsRenderer.class.getDeclaredMethod("drawContent", drawClass, SkyLyricsConfig.class,
								long.class, long.class))
						.asType(MethodType.methodType(void.class, LyricsRenderer.class, Object.class, SkyLyricsConfig.class, long.class,
								long.class));
			}
		}

		private void set(String name, Object value) throws Exception {
			Field f = LyricsRenderer.class.getDeclaredField(name);
			f.setAccessible(true);
			f.set(lyrics, value);
		}

		void frame(int rgb, long now) throws Throwable {
			Object d = draw.invokeExact(lyrics, (LyricsRenderer.Canvas) SCREEN, FONT, cfg, rgb, 190f);
			if (shot != null) {
				content.invokeExact(d, shot, TIME, now);
			} else {
				content.invokeExact(lyrics, d, cfg, TIME, now);
			}
		}

		String drawn(int rgb, long now) throws Throwable {
			DRAWN.setLength(0);
			record = true;
			frame(rgb, now);
			record = false;
			return DRAWN.toString();
		}

		/** Bytes a frame allocates once the JIT has settled, and the texts it hands the game. */
		long[] perFrame(String what) throws Throwable {
			for (int i = 0; i < 30_000; i++) {
				frame(0xFF88CC, NOW + i * 16_000_000L);
			}
			texts = 0;
			ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
			long id = Thread.currentThread().getId();
			long before = mx.getThreadAllocatedBytes(id);
			int frames = 5000;
			for (int i = 0; i < frames; i++) {
				frame(0xFF88CC, NOW + (30_000 + i) * 16_000_000L);
			}
			long used = (mx.getThreadAllocatedBytes(id) - before) / frames;
			System.out.println(what + ": " + used + " bytes a frame, " + texts / frames + " texts");
			return new long[] {used, texts / frames};
		}
	}

	@AfterEach
	void defaults() throws Exception {
		record = false;
		Field instance = SkyLyricsConfig.class.getDeclaredField("instance");
		instance.setAccessible(true);
		instance.set(null, null);
	}

	private static SkyLyricsConfig cfg(SkyLyricsConfig.Layout layout, float glow) {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.layout = layout;
		cfg.glow = glow;
		return cfg;
	}

	@Test
	void aSteadyLinesFrame() throws Throwable {
		long[] plain = new Frames(cfg(SkyLyricsConfig.Layout.LINES, 0)).perFrame("Lines");
		assertTrue(plain[1] >= 5, "rows drawn: " + plain[1]);
		assertTrue(plain[0] < 1800, plain[0] + " bytes a frame");
		long[] glow = new Frames(cfg(SkyLyricsConfig.Layout.LINES, 0.5f)).perFrame("Lines with glow");
		assertTrue(glow[1] > plain[1], "glow copies drawn: " + glow[1]);
		assertTrue(glow[0] < 2100, glow[0] + " bytes a frame");
	}

	@Test
	void aSteadyArcFrame() throws Throwable {
		long[] frame = new Frames(cfg(SkyLyricsConfig.Layout.ARC, 0)).perFrame("Arc");
		assertTrue(frame[1] >= 9, "words drawn with their depth: " + frame[1]);
		assertTrue(frame[0] < 600, frame[0] + " bytes a frame");
	}

	/** The kept styles change nothing: a renderer that has drawn for a while draws what a new one draws. */
	@Test
	void keptColoursDrawTheSameAsNewOnes() throws Throwable {
		for (SkyLyricsConfig.Layout layout : new SkyLyricsConfig.Layout[] {SkyLyricsConfig.Layout.LINES, SkyLyricsConfig.Layout.ARC}) {
			SkyLyricsConfig cfg = cfg(layout, 0.5f);
			cfg.twoTone = true;
			Frames old = new Frames(cfg);
			long at = NOW + 1000 * 16_000_000L;
			for (int i = 0; i < 1000; i++) {
				old.frame(0xFF88CC, NOW + i * 16_000_000L);
			}
			String kept = old.drawn(0xFF88CC, at);
			Frames fresh = new Frames(cfg);
			fresh.frame(0xFF88CC, NOW); // the first frame sets the line clock as the old one's did
			assertEquals(fresh.drawn(0xFF88CC, at), kept, layout.name());
			String green = old.drawn(0x55FF55, at);
			assertNotEquals(kept, green, layout.name());
			Frames freshGreen = new Frames(cfg);
			freshGreen.frame(0x55FF55, NOW);
			assertEquals(freshGreen.drawn(0x55FF55, at), green, layout.name() + " in another colour");
		}
	}
}
