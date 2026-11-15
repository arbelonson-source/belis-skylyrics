package dev.beli.skylyrics.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.config.SkyLyricsConfig.Animation;
import dev.beli.skylyrics.config.SkyLyricsConfig.ColorMode;
import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyEffect;

public final class Themes {
	public record Theme(String id, Supplier<String> name, Consumer<SkyLyricsConfig> look) {
		public SkyLyricsConfig preview() {
			SkyLyricsConfig cfg = new SkyLyricsConfig();
			look.accept(cfg);
			return cfg;
		}
	}

	public static final List<String> LOOK = List.of("layout", "colorMode", "color", "smoothFont", "scriptFont", "boldFont", "roundFont",
			"twoTone", "toneColor", "depth", "glow", "glowPick", "glowColor", "pulse", "shadow", "opacity", "sky", "skyStrength", "size", "animation", "karaoke", "uppercase",
			"worldFlow");

	public static final List<Theme> ALL = List.of(
			new Theme("neon", () -> Tr.t("Neon"), c -> {
			}),
			new Theme("minimal", () -> Tr.t("Minimal"), c -> {
				c.layout = Layout.LINES;
				c.colorMode = ColorMode.FIXED;
				c.color = 0xFFFFFF;
				c.smoothFont = false;
				c.scriptFont = false;
				c.boldFont = false;
				c.roundFont = false;
				c.depth = false;
				c.glow = 0;
				c.pulse = false;
				c.size = 0.75f;
				c.animation = Animation.FADE;
				c.sky = SkyEffect.OFF;
				c.skyStrength = 0.7f;
				c.opacity = 0.85f;
			}),
			new Theme("karaoke", () -> Tr.t("Karaoke"), c -> {
				c.layout = Layout.LINES;
				c.colorMode = ColorMode.FIXED;
				c.color = 0xFFE066;
				c.smoothFont = true;
				c.scriptFont = false;
				c.boldFont = true;
				c.roundFont = false;
				c.depth = true;
				c.size = 1.5f;
				c.glow = 0.9f;
				c.animation = Animation.RISE;
				c.sky = SkyEffect.SYNTHWAVE;
				c.skyStrength = 0.65f;
				c.uppercase = true;
			}),
			new Theme("retro", () -> Tr.t("Retro"), c -> {
				c.layout = Layout.ARC;
				c.colorMode = ColorMode.FIXED;
				c.color = 0x6BFF8E;
				c.smoothFont = false;
				c.scriptFont = false;
				c.boldFont = false;
				c.roundFont = false;
				c.depth = false;
				c.glow = 0;
				c.pulse = false;
				c.animation = Animation.TYPEWRITER;
				c.sky = SkyEffect.OFF;
				c.skyStrength = 0.7f;
			}),
			new Theme("dreamy", () -> Tr.t("Dreamy"), c -> {
				c.layout = Layout.ARC;
				c.colorMode = ColorMode.FIXED;
				c.color = 0xF4A6FF;
				c.smoothFont = true;
				c.boldFont = false;
				c.roundFont = false;
				c.depth = true;
				c.scriptFont = true;
				c.glow = 0.4f;
				c.opacity = 0.9f;
				c.size = 1.15f;
				c.animation = Animation.FADE;
				c.sky = SkyEffect.NEBULA;
				c.skyStrength = 0.85f;
			}),
			new Theme("rainbow", () -> Tr.t("Rainbow"), c -> {
				c.layout = Layout.WORLD;
				c.colorMode = ColorMode.RAINBOW;
				c.smoothFont = true;
				c.scriptFont = false;
				c.boldFont = true;
				c.roundFont = false;
				c.depth = true;
				c.glow = 0.85f;
				c.animation = Animation.POP;
				c.sky = SkyEffect.SYNTHWAVE;
				c.skyStrength = 0.9f;
			}),
			// Capital words popping up round you under the painted moon, pale as moonlight
			new Theme("moonlit", () -> Tr.t("Moonlit"), c -> {
				c.layout = Layout.WORLD;
				c.colorMode = ColorMode.FIXED;
				c.color = 0xDCE6FF;
				c.smoothFont = true;
				c.scriptFont = false;
				c.boldFont = true;
				c.roundFont = false;
				c.depth = true;
				c.glow = 0.45f;
				c.animation = Animation.POP;
				c.sky = SkyEffect.MOONLIGHT;
				c.skyStrength = 0.9f;
				c.uppercase = true;
			}),
			// The line drifting past as one flowing row of words, in the song's own colour under the aurora
			new Theme("drift", () -> Tr.t("Drift"), c -> {
				c.layout = Layout.WORLD;
				c.worldFlow = true;
				c.colorMode = ColorMode.ALBUM;
				c.smoothFont = true;
				c.scriptFont = false;
				c.boldFont = false;
				c.roundFont = false;
				c.depth = false;
				c.glow = 0.3f;
				c.pulse = false;
				c.size = 1.1f;
				c.animation = Animation.FADE;
				c.sky = SkyEffect.AURORA;
				c.skyStrength = 0.8f;
			}),
			// The line written out word by word across a plain night sky, lilac with a soft pink glow
			new Theme("lilac", () -> Tr.t("Lilac"), c -> {
				c.layout = Layout.WORLD;
				c.worldFlow = true;
				c.colorMode = ColorMode.FIXED;
				c.color = 0xD9B6F2;
				c.smoothFont = true;
				c.scriptFont = false;
				c.boldFont = false;
				c.roundFont = false;
				c.depth = false;
				c.glow = 0.7f;
				c.glowPick = true;
				c.glowColor = 0xFF8FD8;
				c.pulse = false;
				c.size = 1.1f;
				c.animation = Animation.FADE;
				c.sky = SkyEffect.OFF;
				c.skyStrength = 0.7f;
				c.uppercase = false;
			}),
			// Big golden lines under the black sun, like light round its rim
			new Theme("corona", () -> Tr.t("Corona"), c -> {
				c.layout = Layout.ARC;
				c.colorMode = ColorMode.FIXED;
				c.color = 0xFFD27A;
				c.smoothFont = true;
				c.scriptFont = false;
				c.boldFont = true;
				c.roundFont = false;
				c.depth = true;
				c.glow = 0.6f;
				c.size = 1.25f;
				c.animation = Animation.FADE;
				c.sky = SkyEffect.ECLIPSE;
				c.skyStrength = 1f;
			}));

	private static final List<Field> FIELDS = new ArrayList<>();
	/** The look fields at least one theme sets, worked out from the themes themselves; the rest are the player's own and a theme leaves them alone. */
	private static final List<Field> THEMED = new ArrayList<>();

	static {
		try {
			for (String name : LOOK) {
				FIELDS.add(SkyLyricsConfig.class.getField(name));
			}
			// A field is set by a theme when running its look changes it from a config where every field has an odd value
			// (two tries, so a theme that sets a field to the value of one of them is still seen)
			for (Field f : FIELDS) {
				boolean set = false;
				for (int round = 0; round < 2 && !set; round++) {
					SkyLyricsConfig odd = new SkyLyricsConfig();
					f.set(odd, other(f.getType(), f.get(odd), round));
					Object before = f.get(odd);
					for (Theme theme : ALL) {
						theme.look().accept(odd);
						set |= !Objects.equals(before, f.get(odd));
						f.set(odd, before);
					}
				}
				if (set) {
					THEMED.add(f);
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object other(Class<?> type, Object now, int round) {
		if (type == boolean.class) {
			return round == 0 ? !(Boolean) now : now;
		} else if (type == float.class) {
			return (Float) now + (round == 0 ? 0.123f : -0.211f);
		} else if (type == int.class) {
			return (Integer) now ^ (round == 0 ? 0x123456 : 0x654321);
		} else if (type.isEnum()) {
			Object[] all = type.getEnumConstants();
			return all[(((Enum<?>) now).ordinal() + 1 + round) % all.length];
		}
		throw new IllegalStateException("A look setting of type " + type + " needs a way to differ");
	}

	/** The names of the look settings some theme sets; applying a theme resets exactly these. */
	public static List<String> themed() {
		return THEMED.stream().map(Field::getName).toList();
	}

	private Themes() {
	}

	public static void apply(Theme theme, SkyLyricsConfig cfg) {
		SkyLyricsConfig look = theme.preview();
		try {
			for (Field f : THEMED) {
				f.set(cfg, f.get(look));
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	public static @Nullable Theme active(SkyLyricsConfig cfg) {
		for (Theme theme : ALL) {
			if (sameLook(theme.preview(), cfg)) {
				return theme;
			}
		}
		return null;
	}

	static boolean sameLook(SkyLyricsConfig a, SkyLyricsConfig b) {
		try {
			for (Field f : THEMED) {
				if (f.getName().equals("color") && a.colorMode != ColorMode.FIXED || f.getName().equals("glowColor") && !a.glowPick
						|| f.getName().equals("toneColor") && !a.twoTone) {
					continue;
				}
				Object x = f.get(a), y = f.get(b);
				boolean same = x instanceof Float fx && y instanceof Float fy ? Math.abs(fx - fy) < 1e-3f : Objects.equals(x, y);
				if (!same) {
					return false;
				}
			}
			return true;
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}
}
