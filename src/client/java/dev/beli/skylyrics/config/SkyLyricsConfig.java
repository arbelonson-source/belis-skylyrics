package dev.beli.skylyrics.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.beli.skylyrics.media.Track;

/** All settings, saved as config/skylyrics.json. Fields are read every frame, so keep them plain. */
public final class SkyLyricsConfig {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

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

	/** How the words of a line are laid out. */
	public enum Layout {
		/** Only the line being sung, wrapped into rows along a gentle curve; each word tilted and sized a little differently. */
		ARC,
		/** The previous, current and next lines, straight and centred. */
		LINES,
		/** Each word or two placed in the world around you as it's sung, fading as newer ones arrive. */
		WORLD
	}

	public enum Animation {
		WORDS, FADE, RISE, POP, TYPEWRITER
	}

	public enum SkyEffect {
		OFF,
		AURORA,
		NEBULA,
		/** A purple gradient with a striped retro sun low in the sky. */
		SYNTHWAVE,
		/** A black sun with a glowing ring and a flare, under a faint milky way. */
		ECLIPSE,
		/** The aurora over a softer nebula. */
		BOTH,
		/** A big full moon with a soft glow over low clouds drifting round the horizon. */
		MOONLIGHT,
		/** One of the other skies, a different one for each song. */
		RANDOM
	}

	public enum BarPosition {
		TOP,
		BOTTOM
	}

	public enum BarShow {
		ALWAYS,
		LYRICS_OUT_OF_VIEW
	}

	public enum BarAccent {
		ALBUM,
		LYRICS,
		FIXED
	}

	public enum SkyColors {
		/** The aurora's own greens and violets with a touch of the lyrics colour. */
		CLASSIC,
		/** Recoloured to the album cover's colour. */
		ALBUM,
		/** Recoloured to the lyrics colour. */
		LYRICS
	}

	public enum SkySpeed {
		SLOW(0.5f), NORMAL(1f), FAST(2f);

		public final float factor;

		SkySpeed(float factor) {
			this.factor = factor;
		}
	}

	public enum Effects {
		/** Full effects, lighter while the frame rate is low. */
		AUTO,
		FULL,
		/** Fewer glow copies around the letters, always. */
		LIGHT
	}

	public enum Layer {
		/** On screen, unless blocks should hide the text and no shaders or rain would dim it. */
		AUTO,
		/** In the world: hills can hide the text, shaders light it. */
		WORLD,
		/** Drawn over the finished picture, so shaders and clouds can't dim or cover it. */
		SCREEN
	}

	// Main
	public boolean enabled = true;
	public Placement placement = Placement.SKY;
	public Direction direction = Direction.FOLLOW;
	/** Follow waits until you turn away, then glides over, instead of staying right where you look. */
	public boolean lazyFollow = false;
	public Layout layout = Layout.ARC;
	/** World layout: how many word groups float at once, and how many seconds sung ones take to fade away. */
	public int worldWords = 7;
	public float worldLinger = 2.2f;
	/** World layout: a line's words read left to right in rows, like handwriting, instead of scattering. */
	public boolean worldFlow = false;
	/** World layout: words take on a little of the sky's colour, as if lit by it. */
	public boolean worldTint = true;

	// Lyrics
	public boolean showPrevious = true;
	public boolean showNext = true;
	public boolean karaoke = true;
	/** With {@link #karaoke}: the word being sung grows a little. */
	public boolean growWord = false;
	public boolean uppercase = false;
	public Animation animation = Animation.WORDS;
	/** Positive shows lyrics earlier (if they lag behind the song), in milliseconds. */
	public int syncOffsetMs = 0;
	/** Extra timing for single songs, on top of {@link #syncOffsetMs}. Keyed by {@link #songKey}. */
	public Map<String, Integer> songOffsets = new LinkedHashMap<>();
	public boolean showPlainLyrics = true;

	// Style
	public ColorMode colorMode = ColorMode.FIXED;
	public int color = 0xC7D0F4;
	/** The bundled smooth font; off uses Minecraft's pixel font. */
	public boolean smoothFont = true;
	/** With {@link #smoothFont}: handwritten letters for the sky lyrics. */
	public boolean scriptFont = false;
	/** With {@link #smoothFont} and not {@link #scriptFont}: the heavier Poppins Bold. */
	public boolean boldFont = true;
	/** Solid darker and lighter copies of the letters just behind them, for a raised 3D look. */
	public boolean depth = true;
	public float glow = 0f;
	/** The glow flares up for a moment whenever a new line starts. */
	public boolean pulse = true;
	public boolean shadow = true;
	public float opacity = 1f;
	/** How much glow to draw: lighter costs fewer frames. */
	public Effects effects = Effects.AUTO;
	public SkyEffect sky = SkyEffect.AURORA;
	public float skyStrength = 0.7f;
	/** Star patterns joined by thin lines in the night sky. */
	public boolean constellations = true;
	public SkyColors skyColors = SkyColors.CLASSIC;
	public SkySpeed skySpeed = SkySpeed.NORMAL;
	public boolean shootingStars = false;
	public boolean twinkle = false;

	// Display
	public float size = 1f;
	/** Degrees above the horizon (Sky mode). */
	public float height = 22f;
	/** Blocks away from you. */
	public float distance = 64f;
	public boolean seeThroughBlocks = true;
	public Layer layer = Layer.AUTO;
	public boolean hideInMenus = false;
	/** The lines around the one being sung, under the buttons of the main menu. */
	public boolean titleScreenLyrics = true;
	public boolean hideWhenPaused = false;
	public boolean hideWithF1 = true;
	public boolean hideInNether = false;
	public boolean hideInEnd = false;
	public boolean hideUnderwater = false;
	/** Hide when there's no open sky above the player, like underground. */
	public boolean hideInCaves = false;
	public boolean hideInMultiplayer = false;

	// Now-playing card
	public boolean nowPlayingHud = true;
	public boolean hudOnlyOnChange = true;
	/** The top bar also shows the line being sung, with the current word highlighted. */
	public boolean barLyrics = true;
	/** The time, left of the top bar. */
	public boolean barClock = true;
	/** Frames per second and ping, right of the top bar. */
	public boolean barStats = true;
	public BarShow barShow = BarShow.ALWAYS;
	public BarPosition barPosition = BarPosition.TOP;
	public float barSize = 1f;
	/** How dark the bar's background is, from transparent to opaque. */
	public float barBackground = 1f;
	public BarAccent barAccent = BarAccent.FIXED;
	public int barAccentColor = 0x926CDE;
	public boolean barArt = true;
	public boolean barEqualiser = true;

	// Music
	/** Part of the player name to prefer ("spotify", "firefox"…); empty = whatever is playing. */
	public String preferredPlayer = "";
	/** From the player's own app on the Spotify dashboard; only used when preferredPlayer is "spotify-web". */
	public String spotifyClientId = "";
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
			if (Files.isRegularFile(file())) {
				SkyLyricsConfig loaded = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), SkyLyricsConfig.class);
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
		if (layer == null) layer = d.layer;
		if (sky == null) sky = d.sky;
		if (layout == null) layout = d.layout;
		if (barPosition == null) barPosition = d.barPosition;
		if (barAccent == null) barAccent = d.barAccent;
		if (barShow == null) barShow = d.barShow;
		if (skyColors == null) skyColors = d.skyColors;
		if (skySpeed == null) skySpeed = d.skySpeed;
		if (effects == null) effects = d.effects;
		if (preferredPlayer == null) preferredPlayer = "";
		if (spotifyClientId == null) spotifyClientId = "";
		glow = clamp(glow, 0, 1);
		opacity = clamp(opacity, 0.2f, 1);
		skyStrength = clamp(skyStrength, 0, 1);
		size = clamp(size, 0.3f, 3);
		worldWords = Math.max(1, Math.min(10, worldWords));
		worldLinger = clamp(worldLinger, 0.8f, 6);
		barSize = clamp(barSize, 0.75f, 1.5f);
		barBackground = clamp(barBackground, 0, 1);
		height = clamp(height, -10, 80);
		distance = clamp(distance, 8, 160);
		syncOffsetMs = Math.max(-5000, Math.min(5000, syncOffsetMs));
		if (songOffsets == null) {
			songOffsets = new LinkedHashMap<>();
		}
		songOffsets.values().removeIf(v -> v == null || v == 0);
		songOffsets.replaceAll((k, v) -> Math.max(-MAX_SONG_OFFSET, Math.min(MAX_SONG_OFFSET, v)));
		color &= 0xFFFFFF;
		barAccentColor &= 0xFFFFFF;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("skylyrics.json");
	}

	private static final int MAX_SONG_OFFSET = 5000;
	private static final int MAX_SONGS = 500;

	/** The same song from a different player or app still finds its timing. */
	public static String songKey(Track track) {
		return (track.artist() + " - " + track.title()).toLowerCase(Locale.ROOT).strip();
	}

	public int songOffset(Track track) {
		return track == Track.NONE ? 0 : songOffsets.getOrDefault(songKey(track), 0);
	}

	/** Global timing plus this song's own. */
	public long offsetFor(Track track) {
		return syncOffsetMs + songOffset(track);
	}

	public void setSongOffset(Track track, int ms) {
		if (track == Track.NONE) {
			return;
		}
		String key = songKey(track);
		songOffsets.remove(key); // re-adding moves it to the end, so the oldest songs get dropped first
		ms = Math.max(-MAX_SONG_OFFSET, Math.min(MAX_SONG_OFFSET, ms));
		if (ms != 0) {
			songOffsets.put(key, ms);
		}
		Iterator<String> oldest = songOffsets.keySet().iterator();
		while (songOffsets.size() > MAX_SONGS && oldest.hasNext()) {
			oldest.next();
			oldest.remove();
		}
	}

	private static float clamp(float v, float min, float max) {
		return Float.isNaN(v) ? min : Math.max(min, Math.min(max, v));
	}

	public void save() {
		try {
			Files.createDirectories(file().getParent());
			Path tmp = file().resolveSibling("skylyrics.json.tmp");
			Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
			Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
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
