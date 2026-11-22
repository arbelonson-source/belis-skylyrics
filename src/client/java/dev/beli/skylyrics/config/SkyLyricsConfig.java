package dev.beli.skylyrics.config;

import dev.beli.skylyrics.util.LogText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
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
		RAINBOW,
		/** Follows the sky: warm at sunrise and sunset, pale by day, lilac at night. */
		TIME,
		/** Each new line fades into the next of a few neon colours. */
		EACH_LINE
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

	/** World layout: how each new word appears. */
	public enum WorldArrival {
		/** Scattered words pop up and type in; a flowing line's words glide in from the side. */
		POP,
		/** Each word drops in from above and bounces a little as it lands. */
		DROP,
		/** Each word grows out of nothing, just past its size, and settles. */
		ZOOM,
		/** Each word slowly fades in where it stands. */
		FADE,
		/** Each word's letters appear one by one, in reading order, as if typed. */
		TYPE
	}

	/** World layout: how each word goes once it's been sung. */
	public enum WorldLeave {
		/** It fades and shrinks a little where it is, as if drifting off. */
		SHRINK,
		/** It floats up as it fades. */
		FLOAT,
		/** It blows away to the side, picking up speed like a leaf on the wind. */
		BLOW,
		/** It sinks down as it fades. */
		SINK,
		/** Its letters wink out one at a time, in no order, until nothing is left. */
		DISSOLVE
	}

	public enum Animation {
		WORDS, FADE, RISE, POP, TYPEWRITER,
		/** The old line slides off to one side as the new one slides in from the other. */
		SLIDE
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
		/** A slowly turning spiral galaxy, seen at a slant up and to the left of the lyrics. */
		GALAXY,
		/** A bright comet low on the right of the lyrics, with a warm dust tail and a blue gas tail climbing away. */
		COMET,
		/** One of the other skies, a different one for each song. */
		RANDOM
	}

	public enum BarPosition {
		TOP,
		BOTTOM
	}

	public enum CardCorner {
		BOTTOM_RIGHT,
		BOTTOM_LEFT,
		TOP_LEFT,
		TOP_RIGHT
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

	public enum Updates {
		/** Download a new version and swap it in; it starts on the next launch. */
		INSTALL,
		/** Only say that a new version is out. */
		NOTIFY,
		OFF
	}

	public enum Hat {
		NONE,
		/** A glowing ring floating above your head. */
		HALO,
		HEADPHONES,
		/** A wide straw cone. */
		CONICAL,
		CROWN,
		/** Two cat ears with glowing insides. */
		CAT_EARS,
		/** A carved pumpkin with a glowing face. */
		PUMPKIN,
		/** A tall top hat with a glowing band. */
		TOP_HAT,
		/** Two curled red horns with glowing tips. */
		DEVIL_HORNS,
		/** Glowing music notes circling the head. */
		MUSIC_NOTES,
		/** Dark sunglasses with glowing rims. */
		SHADES,
		/** Two thin antennae with glowing balls that wobble. */
		ANTENNAE,
		/** A leafy vine round the top of the head, set with small glowing flowers. */
		FLOWER_CROWN,
		/** A panelled cap with a peak at the front and a little propeller on top that whirls with each new line. */
		PROPELLER_CAP
	}

	/** Shapes for the crosshair. Every one but NORMAL is drawn in the lyrics colour with a thin dark edge. */
	public enum Crosshair {
		/** Minecraft's own. */
		NORMAL,
		DOT,
		/** A plus with a gap round a dot in the middle. */
		CROSS,
		/** A small ring round a dot. */
		CIRCLE,
		/** Four corners like a camera frame, round a dot. */
		CORNERS,
		/** A music note whose round head sits on the aim. */
		NOTE,
		/** A small solid heart, the aim near the top of it. */
		HEART,
		/** A diamond outline round a dot. */
		DIAMOND
	}

	public enum Layer {
		/** On screen, unless blocks should hide the text and no shaders or rain would dim it. */
		AUTO,
		/** In the world: hills can hide the text, shaders light it. */
		WORLD,
		/** Drawn over the finished picture, so shaders and clouds can't dim or cover it. */
		SCREEN
	}

	public enum LibraryView {
		/** Rows and columns of small covers. */
		GRID,
		/** Big covers standing in sideways-scrolling rows. */
		SHELF,
		/** One big centred cover with its neighbours turned away. */
		CAROUSEL
	}

	// Main
	public boolean enabled = true;
	public Placement placement = Placement.SKY;
	public Direction direction = Direction.FOLLOW;
	/** Follow waits until you turn away, then glides over, instead of staying right where you look. */
	public boolean lazyFollow = false;
	/** Pins the lyrics where you were looking when this turned on, instead of following Direction. */
	public boolean viewLock = false;
	public Layout layout = Layout.WORLD;
	/** World layout: how many word groups float at once, and how many seconds sung ones take to fade away. */
	public int worldWords = 7;
	public float worldFade = 1.2f;
	/** World layout: how far out the words float, times the usual 6 to 15 blocks; they grow to match, so they read the same. */
	public float worldDistance = 1f;
	/** World layout: how much the words turn sideways, nod and lean, times the usual; 0 has them face you straight on. */
	public float worldTilt = 1f;
	/** World layout: how far the words drift up as they float, times the usual; 0 keeps them still. */
	public float worldRise = 1f;
	/** World layout: a line's words read left to right in rows, like handwriting, instead of scattering. */
	public boolean worldFlow = false;
	/** World layout: how much a flowing line slopes and bends, times the usual; 0 keeps it straight. */
	public float worldCurve = 1f;
	/** World layout: a line's earlier words stay lit until the line is over, so you can read it whole. */
	public boolean worldHold = true;
	/** World layout: words that end up on top of each other glide apart. */
	public boolean worldNudge = true;
	/** World layout: how each new word appears. */
	public WorldArrival worldArrival = WorldArrival.POP;
	/** World layout: how each word goes once it's been sung. */
	public WorldLeave worldLeave = WorldLeave.SHRINK;
	/** World layout: letters with only their glow round them, no dark shadow or edge, like a soft neon sign. */
	public boolean worldSoft = false;
	/** World layout: one word in each line is drawn bigger than the rest, like a sung highlight. */
	public boolean worldBig = true;
	/** World layout: new words go to the side of where you look, keeping the middle of the view free for the game. */
	public boolean worldClearMiddle = true;
	/** World layout: words keep out of the way of other players, so they never cover someone in a fight. */
	public boolean worldAvoidPlayers = true;
	/** World layout: hitting a word knocks it away; it spins and springs back. */
	public boolean worldKnock = true;
	/** World layout: words take on a little of the sky's colour, as if lit by it. */
	public boolean worldTint = true;
	/** World layout: while you glide with an elytra, ride something or fly, the words travel with you instead of being left behind. */
	public boolean worldGlide = true;
	/** World layout: when you die, the words drop out of the sky. */
	public boolean worldDeath = true;
	/** World layout: a new song's name floats up in the sky for a few seconds. */
	public boolean worldTitle = true;

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
	/** With {@link #smoothFont} and not {@link #scriptFont}: the soft, rounded Nunito instead of Poppins. */
	public boolean roundFont = false;
	/** Solid darker and lighter copies of the letters just behind them, for a raised 3D look. */
	public boolean depth = true;
	public float glow = 0f;
	/** The glow takes its own colour instead of the words'. */
	public boolean glowPick = false;
	public int glowColor = 0xFF8FD8;
	/** Each line fades from its colour into this second one, from its first word to its last (the Two-tone setting). */
	public boolean twoTone = false;
	public int toneColor = 0xFF6EC7;
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
	/** The sky effect brightens for a moment as each new line starts, and sits a little dimmer in between. */
	public boolean skyPulse = false;
	/** Something glowing on your head, seen in third person. Only you can see it. */
	public Hat hat = Hat.NONE;
	/** The hat flares up with each new line. */
	public boolean hatPulse = true;
	/** The crosshair's shape. The attack meter under it stays as it is. */
	public Crosshair crosshair = Crosshair.NORMAL;
	/** Our crosshair shapes flash brighter for a moment as each new line starts. */
	public boolean crosshairPulse = false;

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
	/**
	 * Arc and Lines (Draw on top: Auto) go on the screen layer while you're under cover, so rock doesn't hide them. On by default: lyrics
	 * vanishing the moment you walk into a cave looks like a bug. Has no effect with Hide in caves on.
	 */
	public boolean showUnderground = true;
	public boolean hideInMultiplayer = false;

	// Now-playing card
	public boolean nowPlayingHud = true;
	public boolean hudOnlyOnChange = true;
	/** The top bar also shows the line being sung, with the current word highlighted. */
	public boolean barLyrics = true;
	/** The time, left of the top bar. */
	public boolean barClock = true;
	/** Frames per second, right of the top bar. */
	public boolean barStats = true;
	/** Signal bars for your connection, right of the top bar; a file from before this had its own switch follows the frame rate one. */
	public boolean barPing = true;
	public BarShow barShow = BarShow.ALWAYS;
	public BarPosition barPosition = BarPosition.TOP;
	public float barSize = 1f;
	/** How dark the bar's background is, from transparent to opaque. */
	public float barBackground = 1f;
	public BarAccent barAccent = BarAccent.FIXED;
	public int barAccentColor = 0x926CDE;
	public boolean barArt = true;
	/** The round cover turns slowly, like a record, while the song plays. */
	public boolean barSpin = false;
	public boolean barEqualiser = true;
	/** A thin line under the words that fills as the song plays. */
	public boolean barProgress = false;
	/** A small card in a corner with a spinning record, the song and how far along it is. */
	public boolean recordCard = false;
	public CardCorner cardCorner = CardCorner.BOTTOM_RIGHT;
	/** The record card also shows the line being sung, on a strip under the record. */
	public boolean cardLyrics = true;
	/** How many lines the card's strip shows: 1, or 3 (the line before, the current one and the line after). */
	public int cardLines = 1;

	// Music
	/** Part of the player name to prefer ("spotify", "firefox"…); empty = whatever is playing. */
	public String preferredPlayer = "";
	/** From the player's own app on the Spotify dashboard; only used when preferredPlayer is "spotify-web". */
	public String spotifyClientId = "";
	public boolean demoMode = false;

	/** Whether the first-run tour has been shown. Settings files from before the tour count as seen. */
	public boolean introSeen = false;
	/**
	 * Whether the one-time 0.6.2 move of the library key off Iris's shader toggle has run. The settings file has no version,
	 * so a file from before this field counts as not run yet: the move happens once, then this stays true.
	 */
	public boolean keysMigrated = false;
	/** How the song library shows its covers. */
	public LibraryView libraryView = LibraryView.GRID;
	/** Remember the songs that play, for the "Recently heard" list in the song library. Kept on this computer only. */
	public boolean recentSongs = true;

	/** Searches in the song library that led to a song, newest first. */
	public java.util.List<String> recentSearches = new java.util.ArrayList<>();

	// Updates
	public Updates updates = Updates.INSTALL;

	private static SkyLyricsConfig instance;

	public static SkyLyricsConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	/** Set when the settings file exists but couldn't be read: save() then leaves it alone, so the player's real settings aren't overwritten by defaults. */
	transient boolean saveBlocked;
	transient boolean blockedLogged;
	/**
	 * What the update check at start may do. While the settings file couldn't be read, the player's choice isn't known and
	 * the default would download and install, also for someone who turned updates off: no check this game instead. Not
	 * stored in {@link #updates}, so a file that has to be started anew doesn't keep updates off for ever.
	 */
	public Updates updatesAtStart() {
		return saveBlocked ? Updates.OFF : updates;
	}

	/** Failed saves are logged once, until one works again. */
	private static final dev.beli.skylyrics.util.OnceGate SAVE_LOG = new dev.beli.skylyrics.util.OnceGate();
	transient dev.beli.skylyrics.util.SafeFile.Reader reader;

	private static SkyLyricsConfig load() {
		Path file = file();
		SkyLyricsConfig cfg = load(file, dev.beli.skylyrics.util.SafeFile::readText);
		if (cfg.saveBlocked) {
			rereadSoon(cfg, file, REREAD_DELAYS_MS);
		}
		return cfg;
	}

	static SkyLyricsConfig load(Path file, dev.beli.skylyrics.util.SafeFile.Reader reader) {
		return load(file, reader, dev.beli.skylyrics.util.SafeFile.READ_TRIES, dev.beli.skylyrics.util.SafeFile.READ_PAUSE_MS);
	}

	static SkyLyricsConfig load(Path file, dev.beli.skylyrics.util.SafeFile.Reader reader, int tries, long pauseMs) {
		if (file.getParent() != null) {
			dev.beli.skylyrics.util.Leftovers.cleanTemps(file.getParent(), file.getFileName().toString(), System.currentTimeMillis());
		}
		if (!Files.isRegularFile(file)) {
			return new SkyLyricsConfig();
		}
		String text;
		try {
			text = dev.beli.skylyrics.util.SafeFile.readText(file, reader, tries, pauseMs);
		} catch (java.nio.file.NoSuchFileException e) {
			return new SkyLyricsConfig(); // gone since the check: no file
		} catch (java.nio.charset.CharacterCodingException e) {
			keepCopy(file); // not valid UTF-8: damaged, same as a file that isn't JSON
			LOG.warn("Settings file isn't valid text, kept a copy as skylyrics.json.bad and using defaults: {}", LogText.of(e));
			return new SkyLyricsConfig();
		} catch (IOException e) {
			// Locked just now (antivirus, OneDrive) or not allowed. The file itself may be fine: use defaults for now but never write over it.
			LOG.warn("Couldn't read {}; your settings won't be saved this session. Check the file's permissions or whether another program has it open: {}", LogText.of(file), LogText.of(e));
			SkyLyricsConfig blocked = new SkyLyricsConfig();
			blocked.saveBlocked = true;
			blocked.reader = reader;
			// The file is there, so this is no first run: no tour, and the one-time library key move waits for a game that can
			// read the file (on these defaults it would move a key the player chose). The file's own values come back when it reads.
			blocked.introSeen = true;
			blocked.keysMigrated = true;
			return blocked;
		}
		if (text.strip().isEmpty() || text.equals("\uFEFF")) {
			return new SkyLyricsConfig(); // nothing in it yet: not damaged, no copy
		}
		try {
			Parsed parsed = parse(text);
			if (parsed.damaged()) {
				keepCopy(file);
				LOG.warn("Settings file had problems, kept a copy as skylyrics.json.bad. {}", parsed.describe());
			}
			parsed.config().remember(text);
			return parsed.config();
		} catch (RuntimeException e) {
			LOG.warn("Couldn't read the settings file, using defaults: {}", LogText.of(e));
			keepCopy(file);
		}
		return new SkyLyricsConfig();
	}

	/** Copies the settings file to {@code skylyrics.json.bad} (or the next free {@code .bad.N}), so a reset never costs the original; an identical copy isn't made twice. */
	static void keepCopy(Path file) {
		try {
			dev.beli.skylyrics.util.SafeFile.keepBad(file);
		} catch (IOException | RuntimeException e) {
			LOG.warn("Couldn't keep a copy of the damaged settings file: {}", LogText.of(e));
		}
	}

	/**
	 * The result of reading a settings file: the settings, the names of the fields that couldn't be read (they keep their
	 * defaults), and whether the whole file was unusable (not JSON, or not an object).
	 */
	record Parsed(SkyLyricsConfig config, java.util.List<String> badFields, boolean unreadable) {
		boolean damaged() {
			return unreadable || !badFields.isEmpty();
		}

		String describe() {
			return unreadable ? "The file isn't valid settings JSON, using defaults." : "Reset to defaults: " + String.join(", ", badFields);
		}
	}

	/** Gson quietly wraps or rounds 1e10 or 1.5 into an int field; a whole-number setting should get a whole number that fits, and a yes/no setting a real true or false. */
	private static boolean wholeNumberFits(Class<?> type, com.google.gson.JsonElement value) {
		if (type == boolean.class || type == Boolean.class) {
			return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(); // Gson reads "yes" as false
		}
		boolean isInt = type == int.class || type == Integer.class;
		boolean isLong = type == long.class || type == Long.class;
		if (!(isInt || isLong) || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return true; // other types and wrong kinds are Gson's to judge
		}
		try {
			var number = new java.math.BigDecimal(value.getAsString());
			if (isInt) number.intValueExact(); else number.longValueExact();
			return true;
		} catch (ArithmeticException | NumberFormatException e) {
			return false;
		}
	}

	/** Reads settings from file text one field at a time, so one wrong value only resets that setting. Doesn't touch the disk. */
	static Parsed parse(String text) {
		SkyLyricsConfig config = new SkyLyricsConfig();
		java.util.List<String> bad = new java.util.ArrayList<>();
		JsonObject json;
		try {
			json = JsonParser.parseString(text).getAsJsonObject();
		} catch (RuntimeException e) {
			return new Parsed(config, bad, true);
		}
		for (var field : SkyLyricsConfig.class.getDeclaredFields()) {
			int mods = field.getModifiers();
			if (java.lang.reflect.Modifier.isStatic(mods) || java.lang.reflect.Modifier.isTransient(mods) || field.isSynthetic()) {
				continue;
			}
			var value = json.get(field.getName());
			if (value == null || value.isJsonNull()) {
				continue; // missing or null: keep the default (fix() fills any enum or list)
			}
			try {
				if (!wholeNumberFits(field.getType(), value)) {
					throw new NumberFormatException(value.toString());
				}
				Object read;
				if (field.getName().equals("songOffsets") && value.isJsonObject()) {
					java.util.Map<String, Integer> offsets = new LinkedHashMap<>();
					boolean skipped = false;
					for (var e : value.getAsJsonObject().entrySet()) {
						try {
							if (!wholeNumberFits(int.class, e.getValue())) {
								throw new NumberFormatException(e.getValue().toString());
							}
							Integer ms = GSON.fromJson(e.getValue(), Integer.class);
							if (ms == null) {
								throw new NumberFormatException("null");
							}
							offsets.put(e.getKey(), ms);
						} catch (Exception ex) {
							skipped = true; // only this song's timing is lost
						}
					}
					if (skipped) {
						bad.add(field.getName());
					}
					read = offsets;
				} else {
					read = GSON.fromJson(value, field.getGenericType());
				}
				if (read != null) {
					field.setAccessible(true);
					field.set(config, read);
				}
			} catch (Exception e) {
				bad.add(field.getName());
			}
		}
		if (!json.has("barPing")) config.barPing = config.barStats;
		if (!json.has("introSeen")) config.introSeen = true;
		if (!json.has("worldFade")) config.worldFade = fadeFrom(json, config.worldFade);
		config.fix();
		return new Parsed(config, bad, false);
	}

	/**
	 * The World fade from a file saved before it had its own name ({@code worldLinger}): one picked by hand is kept, but the
	 * old 2.2 s default, much slower than the original mod's lines, gives way to the new one.
	 */
	static float fadeFrom(JsonObject json, float fallback) {
		if (json.get("worldLinger") instanceof JsonPrimitive old && old.isNumber()) {
			float seconds = old.getAsFloat();
			return Math.abs(seconds - 2.2f) < 0.01f ? fallback : seconds;
		}
		return fallback;
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
		if (cardCorner == null) cardCorner = d.cardCorner;
		if (barAccent == null) barAccent = d.barAccent;
		if (barShow == null) barShow = d.barShow;
		if (skyColors == null) skyColors = d.skyColors;
		if (skySpeed == null) skySpeed = d.skySpeed;
		if (effects == null) effects = d.effects;
		if (updates == null) updates = d.updates;
		if (hat == null) hat = d.hat;
		if (worldArrival == null) worldArrival = d.worldArrival;
		if (worldLeave == null) worldLeave = d.worldLeave;
		if (crosshair == null) crosshair = d.crosshair;
		if (libraryView == null) libraryView = d.libraryView;
		recentSearches = cleanSearches(recentSearches);
		preferredPlayer = preferredPlayer == null ? "" : preferredPlayer.strip().toLowerCase(Locale.ROOT);
		spotifyClientId = spotifyClientId == null ? "" : spotifyClientId.strip();
		glow = clamp(glow, 0, 1, d.glow);
		opacity = clamp(opacity, 0.2f, 1, d.opacity);
		skyStrength = clamp(skyStrength, 0.1f, 1, d.skyStrength);
		size = clamp(size, 0.3f, 3, d.size);
		worldWords = Math.max(1, Math.min(10, worldWords));
		worldFade = clamp(worldFade, 0.8f, 6, d.worldFade);
		worldDistance = clamp(worldDistance, 0.5f, 2, d.worldDistance);
		worldTilt = clamp(worldTilt, 0, 2, d.worldTilt);
		worldRise = clamp(worldRise, 0, 2, d.worldRise);
		worldCurve = clamp(worldCurve, 0, 2, d.worldCurve);
		cardLines = cardLines >= 3 ? 3 : 1;
		barSize = clamp(barSize, 0.75f, 1.5f, d.barSize);
		barBackground = clamp(barBackground, 0, 1, d.barBackground);
		height = clamp(height, -10, 80, d.height);
		distance = clamp(distance, 8, 160, d.distance);
		syncOffsetMs = Math.max(-5000, Math.min(5000, syncOffsetMs));
		if (songOffsets == null) {
			songOffsets = new LinkedHashMap<>();
		}
		songOffsets.values().removeIf(v -> v == null || v == 0);
		songOffsets = rekeyed(songOffsets);
		songOffsets.replaceAll((k, v) -> Math.max(-MAX_SONG_OFFSET, Math.min(MAX_SONG_OFFSET, v)));
		// Like setSongOffset, the oldest (first in the file) go; the last MAX_SONGS stay.
		Iterator<String> oldest = songOffsets.keySet().iterator();
		for (int extra = songOffsets.size() - MAX_SONGS; extra > 0 && oldest.hasNext(); extra--) {
			oldest.next();
			oldest.remove();
		}
		color &= 0xFFFFFF;
		barAccentColor &= 0xFFFFFF;
		glowColor &= 0xFFFFFF;
		toneColor &= 0xFFFFFF;
	}

	/**
	 * Saved keys made through today's title cleaning, so a timing saved before a change in what {@link Track#clean} strips is still found.
	 * Order is kept, a later entry wins a collision, and a key that is already clean stays as it is.
	 */
	private static Map<String, Integer> rekeyed(Map<String, Integer> old) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (var e : old.entrySet()) {
			String key = Track.clean(e.getKey()).toLowerCase(Locale.ROOT).strip();
			if (key.isEmpty()) {
				continue;
			}
			out.remove(key); // the later entry wins and takes the later place
			out.put(key, e.getValue());
		}
		return out;
	}

	/** {@link #rekeyed(Map)} for the timings as they are in the file. */
	private static JsonObject rekeyed(JsonObject old) {
		JsonObject out = new JsonObject();
		for (var e : old.entrySet()) {
			String key = Track.clean(e.getKey()).toLowerCase(Locale.ROOT).strip();
			if (key.isEmpty()) {
				continue;
			}
			out.remove(key); // the later entry wins and takes the later place
			out.add(key, e.getValue());
		}
		return out;
	}

	/** How many recent library searches are kept, here and when the library remembers a new one. */
	public static final int MAX_RECENT_SEARCHES = 6;
	private static final int MAX_SEARCH_CHARS = 100;

	/** A hand-edited list made safe: no null or blank entries, trimmed and capped, no case-insensitive repeats, newest {@link #MAX_RECENT_SEARCHES} only. */
	static java.util.List<String> cleanSearches(java.util.List<String> raw) {
		java.util.List<String> out = new java.util.ArrayList<>();
		if (raw == null) {
			return out;
		}
		for (String s : raw) {
			if (s == null) continue;
			s = s.strip();
			if (s.length() > MAX_SEARCH_CHARS) {
				int end = Character.isHighSurrogate(s.charAt(MAX_SEARCH_CHARS - 1)) ? MAX_SEARCH_CHARS - 1 : MAX_SEARCH_CHARS;
				s = s.substring(0, end).strip();
			}
			if (s.isEmpty()) continue;
			boolean seen = false;
			for (String o : out) {
				if (o.equalsIgnoreCase(s)) {
					seen = true;
					break;
				}
			}
			if (!seen) {
				out.add(s);
				if (out.size() >= MAX_RECENT_SEARCHES) break;
			}
		}
		return out;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("skylyrics.json");
	}

	private static final int MAX_SONG_OFFSET = 5000;
	private static final int MAX_SONGS = 500;

	/** The same song from a different player or app still finds its timing. */
	public static String songKey(Track track) {
		return keyOf(track);
	}

	private record Keyed(Track track, String key) {}

	/** The last track a key was made for, so the string work isn't repeated every frame. A stale pair is harmless. */
	private static volatile Keyed keyed;

	private static String keyOf(Track track) {
		Keyed k = keyed;
		if (k != null && k.track == track) {
			return k.key;
		}
		String key = makeKey(track);
		keyed = new Keyed(track, key);
		return key;
	}

	/** Builds the key from scratch. */
	static String makeKey(Track track) {
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

	/** Held between min and max; a number that isn't finite (NaN, Infinity from a hand-edited file) gives the default. */
	private static float clamp(float v, float min, float max, float fallback) {
		return Float.isFinite(v) ? Math.max(min, Math.min(max, v)) : fallback;
	}

	/**
	 * Saves the settings without blocking: the caller only takes a snapshot, and one daemon writer thread does the reading,
	 * merging and writing. Several quick saves coalesce, only the latest snapshot is written.
	 */
	public void save() {
		saveLater(file());
	}

	/** What a queued save needs: where to write, the settings as they were, and the file state they were changed from. */
	private record Pending(Path file, JsonObject snapshot, JsonObject base, long baseGen) {}

	/** The queue between the game thread and the writer; one field so the settings class keeps a single piece of such state. */
	private static final class Queue {
		Pending pending;
		boolean draining;
		/** Numbers every base (load, adopt, each write), so the newest is known whichever thread saw it. */
		final java.util.concurrent.atomic.AtomicLong gens = new java.util.concurrent.atomic.AtomicLong();
		/** The snapshot of this game's last successful write and its number; only the writer (under the config's lock) touches them. */
		JsonObject lastSnapshot;
		long lastGen;
		/** The newest save whose write failed (disk full, file held); tried once more when the game shuts down. */
		Pending failed;
	}

	final transient Queue queue = new Queue();

	/** Runs the writer's work; a seam so tests can hold the writer. */
	static java.util.function.Consumer<Runnable> writerExec = new java.util.function.Consumer<>() {
		private final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "SkyLyrics settings writer");
			t.setDaemon(true);
			return t;
		});

		@Override
		public void accept(Runnable task) {
			pool.execute(task);
		}
	};

	void saveLater(Path file) {
		Pending now = new Pending(file, GSON.toJsonTree(this).getAsJsonObject(), baseTree, baseGen);
		boolean start;
		synchronized (queue) {
			queue.pending = now;
			start = !queue.draining;
			queue.draining = true;
		}
		if (start) {
			startWriter();
		}
		long wait = closeWaitMs;
		if (wait >= 0) {
			flush(wait); // the game is stopping: nothing would wait for this save later
		}
	}

	/** Hands the writer to its thread ({@code draining} is already set); if that is refused, the queue is idle again. */
	private void startWriter() {
		try {
			writerExec.accept(this::drain);
		} catch (RuntimeException e) {
			synchronized (queue) {
				queue.draining = false;
				queue.notifyAll();
			}
			LOG.warn("Couldn't start the settings writer: {}", e.toString());
		}
	}

	private void drain() {
		boolean clean = false;
		Pending next = null;
		try {
			while (true) {
				synchronized (queue) {
					next = queue.pending;
					queue.pending = null;
					if (next == null) {
						queue.draining = false;
						queue.notifyAll();
						clean = true;
						return;
					}
				}
				boolean written = false;
				try {
					written = write(next.file(), next.snapshot(), next.base(), next.baseGen(), task -> mainThread.accept(task));
				} catch (RuntimeException e) {
					saveFailed(e);
				}
				synchronized (queue) {
					queue.failed = written ? null : next;
				}
			}
		} catch (Throwable t) {
			LOG.warn("The settings writer stopped: {}", t.toString());
			synchronized (queue) {
				queue.failed = next; // the snapshot being written is not lost: flush tries it once more
			}
			throw t;
		} finally {
			if (!clean) {
				boolean restart;
				synchronized (queue) {
					restart = queue.pending != null; // a save that came in while this writer was dying
					if (!restart) {
						queue.draining = false; // else no later save would ever start the writer again
						queue.notifyAll();
					}
				}
				if (restart) {
					startWriter();
				}
			}
		}
	}

	/** Set by {@link #close}: how long each later save waits until it is on disk; below 0 while the game runs. */
	transient volatile long closeWaitMs = -1;

	/**
	 * For the game stopping: waits for the queued saves like {@link #flush}, and makes every later save wait too (at most
	 * {@code timeoutMs} each). Minecraft closes the open screen only after its stopping event, so the settings screen saves
	 * what was changed in it after this; left to the writer thread alone, that save races the exit and can be lost.
	 */
	public boolean close(long timeoutMs) {
		closeWaitMs = Math.max(0, timeoutMs);
		return flush(timeoutMs);
	}

	/** Waits until every queued save is written, at most {@code timeoutMs}; true when nothing is left. */
	public boolean flush(long timeoutMs) {
		long end = System.nanoTime() + timeoutMs * 1_000_000L;
		synchronized (queue) {
			while (queue.pending != null || queue.draining) {
				long left = (end - System.nanoTime()) / 1_000_000L;
				if (left <= 0) {
					return false;
				}
				try {
					queue.wait(left);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return false;
				}
			}
		}
		return retryFailed();
	}

	/** Tries the save that failed once more, on this thread and only once: a newer save that went through has cleared it. */
	private boolean retryFailed() {
		Pending again;
		synchronized (queue) {
			again = queue.failed;
			queue.failed = null; // not remembered again: one more try, no retry loop
		}
		if (again == null) {
			return true;
		}
		try {
			return write(again.file(), again.snapshot(), again.base(), again.baseGen(), Runnable::run);
		} catch (RuntimeException e) {
			saveFailed(e);
			return false;
		}
	}

	/** Logs a failed save once; the next failure is news again only after a save went through. */
	private static void saveFailed(Exception e) {
		if (SAVE_LOG.shouldLog()) {
			LOG.warn("Couldn't save settings (not logged again until a save works): {}", LogText.of(e));
		}
	}

	/** Saves now, on this thread (tests, and anything that must be on disk before it goes on). */
	void save(Path file) {
		write(file, GSON.toJsonTree(this).getAsJsonObject(), baseTree, baseGen, Runnable::run);
	}

	/**
	 * Reads, merges and writes the file for a snapshot. {@code applier} runs what changes the live settings (on the game's
	 * main thread when saving later): the other game's changes come in only for fields still equal to the snapshot.
	 */
	private boolean write(Path file, JsonObject mine, JsonObject base, long baseGen, java.util.function.Consumer<Runnable> applier) {
		synchronized (this) {
			if (saveBlocked && !reread(file, mine, applier)) {
				return true; // still unreadable, or just loaded: writing the in-memory defaults would replace the player's settings
			}
			Path tmp = null;
			try {
				Files.createDirectories(file.getParent());
				// A write of this game's own that the client thread hasn't taken over yet is newer than the snapshot's base: what the
				// player changed since is measured against that write's snapshot, else a change made and undone is lost to the file.
				JsonObject from = queue.lastSnapshot != null && queue.lastGen > baseGen ? queue.lastSnapshot : base;
				JsonObject out = mergeWithDisk(file, mine, from);
				String text = GSON.toJson(out == null ? mine : out);
				Path target = dev.beli.skylyrics.util.SafeFile.writeTarget(file); // a linked settings file stays linked
				tmp = Files.createTempFile(target.getParent(), file.getFileName() + ".", ".tmp");
				dev.beli.skylyrics.util.SafeFile.writeDurably(tmp, text);
				dev.beli.skylyrics.util.SafeFile.replace(tmp, target);
				tmp = null;
				Parsed parsed = parse(text); // once, for the base and for what this game takes over
				JsonObject tree = GSON.toJsonTree(parsed.config()).getAsJsonObject();
				boolean merged = out != null;
				long gen = queue.gens.incrementAndGet();
				queue.lastSnapshot = mine;
				queue.lastGen = gen;
				// The base moves with the apply, on the same thread as the snapshots, so a snapshot and its base always agree.
				applier.accept(() -> {
					if (merged) {
						copyFrom(parsed.config(), mine); // this game now also sees the other game's changes
					}
					setBase(text, tree, gen);
				});
				SAVE_LOG.reset();
				return true;
			} catch (IOException e) {
				saveFailed(e);
				return false;
			} finally {
				if (tmp != null) {
					try {
						Files.deleteIfExists(tmp); // only our own temp file
					} catch (IOException | RuntimeException ignored) {
					}
				}
			}
		}
	}

	/** The exact text this game last read from or wrote to the settings file, and its settings as a normalised JSON tree. */
	transient volatile String baseText;
	transient volatile JsonObject baseTree;
	transient volatile long baseGen;

	/** Moves the base forward; an older one (a slow apply arriving late) never replaces a newer. */
	private void setBase(String text, JsonObject tree, long gen) {
		if (gen > baseGen) {
			baseText = text;
			baseTree = tree;
			baseGen = gen;
		}
	}

	private void remember(String text) {
		setBase(text, GSON.toJsonTree(parse(text).config()).getAsJsonObject(), queue.gens.incrementAndGet());
	}

	/**
	 * When another game saved the file since this one last read or wrote it: the file's JSON with only the settings this game
	 * changed since then put over it (per song for the song timings). Null when nothing needs merging.
	 */
	private JsonObject mergeWithDisk(Path file, JsonObject mine, JsonObject baseOrNull) {
		String disk;
		try {
			disk = dev.beli.skylyrics.util.SafeFile.readText(file, reader != null ? reader : dev.beli.skylyrics.util.SafeFile::readText, 1, 0);
		} catch (IOException | RuntimeException e) {
			return null;
		}
		JsonObject theirs; // also when the file is unchanged since load: it may hold settings only a newer version knows
		try {
			theirs = JsonParser.parseString(disk).getAsJsonObject();
		} catch (RuntimeException e) {
			return null; // damaged: nothing worth keeping
		}
		// The file can still hold song timings under keys from before a title-cleaning change; this game's base and its own
		// timings use the cleaned keys, so merge against cleaned keys too (else a reset timing would come back from the old key)
		if (theirs.get("songOffsets") instanceof JsonObject offsets) {
			theirs.add("songOffsets", rekeyed(offsets));
		}
		JsonObject base = baseOrNull != null ? baseOrNull : GSON.toJsonTree(new SkyLyricsConfig()).getAsJsonObject();
		// A wrong-typed value loads as the default, so it looks unchanged and would be kept for ever: write this game's value over it.
		// Only fields this version knows are named here, so a newer version's fields still survive.
		for (String name : parse(disk).badFields()) {
			if (mine.has(name)) {
				theirs.add(name, mine.get(name));
			}
		}
		for (var entry : mine.entrySet()) {
			String name = entry.getKey();
			var now = entry.getValue();
			var then = base.get(name);
			if (now.equals(then)) {
				if (!theirs.has(name)) {
					theirs.add(name, now); // a file from an older version doesn't know this setting: keep this game's value
				}
				continue; // this game didn't change it: the file's value stays
			}
			var there = theirs.get(name);
			if (now.isJsonObject() && there != null && there.isJsonObject() && then != null && then.isJsonObject()) {
				JsonObject merged = there.getAsJsonObject();
				JsonObject before = then.getAsJsonObject();
				for (String key : new java.util.ArrayList<>(before.keySet())) {
					if (!now.getAsJsonObject().has(key)) {
						merged.remove(key);
					}
				}
				for (var e : now.getAsJsonObject().entrySet()) {
					if (!e.getValue().equals(before.get(e.getKey()))) {
						merged.remove(e.getKey()); // re-adding moves it to the end, like setSongOffset
						merged.add(e.getKey(), e.getValue());
					}
				}
			} else {
				theirs.add(name, now);
			}
		}
		return theirs;
	}

	/** True when writing is fine now: the file is gone or damaged, so there is nothing to protect. False while it is still unreadable, and when it was just loaded (the file wins). */
	private boolean reread(Path file, JsonObject snapshot, java.util.function.Consumer<Runnable> applier) {
		String text;
		try {
			text = dev.beli.skylyrics.util.SafeFile.readText(file, reader, 1, 0);
		} catch (java.nio.file.NoSuchFileException e) {
			saveBlocked = false;
			return true;
		} catch (java.nio.charset.CharacterCodingException e) {
			keepCopy(file);
			saveBlocked = false;
			return true;
		} catch (IOException e) {
			if (!blockedLogged) {
				blockedLogged = true;
				LOG.warn("Settings not saved: {} still can't be read ({})", LogText.of(file), LogText.of(e));
			}
			return false;
		}
		adopt(file, text, snapshot, applier);
		return false;
	}

	/** The file reads again: its settings become the live ones (the file wins; what was set meanwhile is lost) and saving is allowed. */
	private void adopt(Path file, String text) {
		adopt(file, text, null, Runnable::run);
	}

	/** With a snapshot (a save found the file readable again), only settings the player hasn't touched since it are replaced. */
	private void adopt(Path file, String text, JsonObject snapshot, java.util.function.Consumer<Runnable> applier) {
		Parsed parsed = parse(text);
		if (parsed.damaged()) {
			keepCopy(file);
		}
		JsonObject tree = GSON.toJsonTree(parsed.config()).getAsJsonObject();
		long gen = queue.gens.incrementAndGet();
		applier.accept(() -> {
			copyFrom(parsed.config(), snapshot);
			setBase(text, tree, gen);
		});
		saveBlocked = false;
		LOG.info("The settings file could be read again and was loaded.");
	}

	/** Takes over {@code other}'s settings; with a snapshot, only those still equal to it (the player hasn't changed them since). */
	private void copyFrom(SkyLyricsConfig other, JsonObject snapshot) {
		JsonObject now = snapshot == null ? null : GSON.toJsonTree(this).getAsJsonObject();
		for (var field : SkyLyricsConfig.class.getDeclaredFields()) {
			int mods = field.getModifiers();
			if (java.lang.reflect.Modifier.isStatic(mods) || java.lang.reflect.Modifier.isTransient(mods) || field.isSynthetic()) {
				continue;
			}
			try {
				if (now != null && !java.util.Objects.equals(now.get(field.getName()), snapshot.get(field.getName()))) {
					continue;
				}
				field.setAccessible(true);
				field.set(this, field.get(other));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/** Runs a task on the game's main thread; a seam so tests needn't start Minecraft. */
	static java.util.function.Consumer<Runnable> mainThread = task -> net.minecraft.client.Minecraft.getInstance().execute(task);

	/** Waits before each later try at a file that was locked at start. The read is on a helper thread; the loaded settings are applied on the main thread. */
	static final long[] REREAD_DELAYS_MS = {2_000, 10_000, 60_000};

	/** Whether the file is readable by permissions (a Windows sharing lock doesn't change that); a seam for tests. */
	static java.util.function.Predicate<Path> readable = Files::isReadable;

	static Thread rereadSoon(SkyLyricsConfig blocked, Path file, long[] delaysMs) {
		Thread t = new Thread(() -> {
			for (long delay : delaysMs) {
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					return;
				}
				try {
					String text = dev.beli.skylyrics.util.SafeFile.readText(file, blocked.reader, 1, 0);
					mainThread.accept(() -> {
						synchronized (blocked) {
							if (blocked.saveBlocked) {
								blocked.adopt(file, text);
							}
						}
					});
					return;
				} catch (IOException e) {
					// still locked: next delay
				}
			}
			blocked.giveUpOnUnreadable(file);
		}, "SkyLyrics settings reread");
		t.setDaemon(true);
		t.start();
		return t;
	}

	/**
	 * After the last re-read failed: a file that isn't readable by permissions (left owned by root, say) will never read, so
	 * move it aside and let saving start a new one. A sharing lock leaves the file readable and stays blocked.
	 */
	private synchronized void giveUpOnUnreadable(Path file) {
		if (!saveBlocked || readable.test(file)) {
			return;
		}
		try {
			Path moved = dev.beli.skylyrics.util.SafeFile.moveAside(file);
			saveBlocked = false;
			LOG.warn("The settings file {} can't be read (permissions), so it was moved to {} and your settings will be saved to a new one.", LogText.of(file), LogText.of(moved));
		} catch (IOException | RuntimeException e) {
			LOG.warn("The settings file {} can't be read and couldn't be moved aside, so settings stay unsaved this session: {}", LogText.of(file), LogText.of(e));
		}
	}

	/** Fields that are the player's own data or state, not look/behaviour settings; "Reset all" leaves them alone. */
	private static final java.util.Set<String> KEPT_ON_RESET = java.util.Set.of(
			"introSeen", "keysMigrated", "updates", "spotifyClientId", "songOffsets", "recentSearches");

	public void resetToDefaults() {
		SkyLyricsConfig d = new SkyLyricsConfig();
		for (var field : SkyLyricsConfig.class.getDeclaredFields()) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || java.lang.reflect.Modifier.isTransient(field.getModifiers()) || KEPT_ON_RESET.contains(field.getName())) {
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
