package dev.beli.skylyrics.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** All settings, saved as config/skylyrics.json. Fields are read every frame, so keep them plain. */
public final class SkyLyricsConfig {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skylyrics.json");

	public enum Placement {
		/** Huge letters high in the sky, fixed to a compass direction like the sun. */
		SKY,
		/** Closer, smaller text that drifts along in front of you. */
		FLOATING
	}

	public enum Direction {
		NORTH(180), EAST(-90), SOUTH(0), WEST(90), FOLLOW(Float.NaN);

		/** Minecraft yaw that looks this way. */
		public final float yaw;

		Direction(float yaw) {
			this.yaw = yaw;
		}
	}

	public enum ColorMode {
		/** A colour picked from the album cover. */
		ALBUM,
		/** One of the swatches, or a custom hex colour. */
		FIXED,
		/** Slowly cycles through the rainbow. */
		RAINBOW
	}

	public enum Animation {
		FADE, RISE, POP, TYPEWRITER
	}

	// Main
	public boolean enabled = true;
	public Placement placement = Placement.SKY;
	public Direction direction = Direction.FOLLOW;

	// Lyrics
	public boolean showPrevious = true;
	public boolean showNext = true;
	public boolean karaoke = true;
	public boolean uppercase = false;
	public Animation animation = Animation.RISE;
	/** Positive shows lyrics earlier (if they lag behind the song), in milliseconds. */
	public int syncOffsetMs = 0;
	public boolean showPlainLyrics = true;

	// Style
	public ColorMode colorMode = ColorMode.ALBUM;
	public int color = 0xB57BFF;
	/** The bundled smooth font; off uses Minecraft's pixel font. */
	public boolean smoothFont = true;
	public float glow = 0.7f;
	public boolean shadow = true;
	public float opacity = 1f;

	// Display
	public float size = 1f;
	/** Degrees above the horizon (Sky mode). */
	public float height = 22f;
	/** Blocks away from you. */
	public float distance = 64f;
	public boolean seeThroughBlocks = true;
	public boolean hideInMenus = false;
	public boolean hideWhenPaused = false;
	public boolean hideWithF1 = true;

	// Now-playing card
	public boolean nowPlayingHud = true;
	public boolean hudOnlyOnChange = true;

	// Music
	/** Part of the player name to prefer ("spotify", "firefox"…); empty = whatever is playing. */
	public String preferredPlayer = "";
	public boolean demoMode = false;

	private static SkyLyricsConfig instance;

	public static SkyLyricsConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static SkyLyricsConfig load() {
		try {
			if (Files.isRegularFile(FILE)) {
				SkyLyricsConfig loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), SkyLyricsConfig.class);
				if (loaded != null) {
					loaded.fix();
					return loaded;
				}
			}
		} catch (Exception e) {
			LOG.warn("Settings file was broken, using defaults: {}", e.toString());
		}
		return new SkyLyricsConfig();
	}

	/** Missing or out-of-range values from an old or hand-edited file. */
	private void fix() {
		SkyLyricsConfig d = new SkyLyricsConfig();
		if (placement == null) placement = d.placement;
		if (direction == null) direction = d.direction;
		if (colorMode == null) colorMode = d.colorMode;
		if (animation == null) animation = d.animation;
		if (preferredPlayer == null) preferredPlayer = "";
		glow = clamp(glow, 0, 1);
		opacity = clamp(opacity, 0.2f, 1);
		size = clamp(size, 0.3f, 3);
		height = clamp(height, -10, 80);
		distance = clamp(distance, 8, 160);
		syncOffsetMs = Math.max(-5000, Math.min(5000, syncOffsetMs));
		color &= 0xFFFFFF;
	}

	private static float clamp(float v, float min, float max) {
		return Float.isNaN(v) ? min : Math.max(min, Math.min(max, v));
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling("skylyrics.json.tmp");
			Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOG.warn("Couldn't save settings: {}", e.toString());
		}
	}

	public void resetToDefaults() {
		SkyLyricsConfig d = new SkyLyricsConfig();
		for (var field : SkyLyricsConfig.class.getDeclaredFields()) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			try {
				field.set(this, field.get(d));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
