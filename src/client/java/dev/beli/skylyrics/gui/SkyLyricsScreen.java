package dev.beli.skylyrics.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.function.BooleanSupplier;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.render.LyricsRenderer;
import dev.beli.skylyrics.media.SpotifyWebSource;
import dev.beli.skylyrics.config.SkyLyricsConfig.BarAccent;
import dev.beli.skylyrics.config.SkyLyricsConfig.BarPosition;
import dev.beli.skylyrics.config.SkyLyricsConfig.BarShow;
import dev.beli.skylyrics.config.SkyLyricsConfig.Animation;
import dev.beli.skylyrics.config.SkyLyricsConfig.ColorMode;
import dev.beli.skylyrics.config.SkyLyricsConfig.Direction;
import dev.beli.skylyrics.config.SkyLyricsConfig.Effects;
import dev.beli.skylyrics.config.SkyLyricsConfig.Updates;
import dev.beli.skylyrics.config.SkyLyricsConfig.Crosshair;
import dev.beli.skylyrics.config.SkyLyricsConfig.Hat;
import dev.beli.skylyrics.config.SkyLyricsConfig.Layer;
import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyColors;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyEffect;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkySpeed;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import dev.beli.skylyrics.config.SkyLyricsConfig.WorldArrival;
import dev.beli.skylyrics.config.SkyLyricsConfig.WorldLeave;
import dev.beli.skylyrics.gui.Controls.Button;
import dev.beli.skylyrics.gui.Controls.Buttons;
import dev.beli.skylyrics.gui.Controls.Card;
import dev.beli.skylyrics.gui.Controls.Cycle;
import dev.beli.skylyrics.gui.Controls.Frame;
import dev.beli.skylyrics.gui.Controls.Info;
import dev.beli.skylyrics.gui.Controls.Paragraph;
import dev.beli.skylyrics.gui.Controls.Row;
import dev.beli.skylyrics.gui.Controls.Segmented;
import dev.beli.skylyrics.gui.Controls.Slider;
import dev.beli.skylyrics.gui.Controls.Swatches;
import dev.beli.skylyrics.gui.Controls.Toggle;
import dev.beli.skylyrics.lyrics.Lyrics;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.render.AlbumArt;

/** The settings window. Drawn by hand (no vanilla buttons) so it can look like a modern app. */
public final class SkyLyricsScreen extends Screen {
	static final String GITHUB = "https://github.com/arbelonson-source/belis-skylyrics";
	/**
	 * Whether the Height setting does anything. World words don't use it, and Arc puts each line where you are looking when
	 * it starts (Follow, or Floating), so only a compass direction in the sky uses it there.
	 */
	static boolean heightApplies(Layout layout, Placement placement, Direction direction) {
		return switch (layout) {
			case WORLD -> false;
			case ARC -> placement == Placement.SKY && direction != Direction.FOLLOW;
			default -> true;
		};
	}

	/** Whether a layout is drawn by the flat lyrics code. The World layout has its own, which skips Draw on top, Depth, Shadow, Pulse and Bigger current word (and Karaoke fill, except for the top bar's line). */
	static boolean flatLayout(Layout layout) {
		return layout != Layout.WORLD;
	}

	/**
	 * Whether Karaoke fill does anything: the flat layouts use it for their words, and the top bar for its line, so World
	 * still has it while the bar shows lyrics.
	 */
	static boolean karaokeApplies(SkyLyricsConfig cfg) {
		return flatLayout(cfg.layout) || cfg.nowPlayingHud && cfg.barLyrics;
	}

	/** Whether Depth does anything: only the Arc layout draws it (Lines has Shadow, World neither). */
	static boolean depthApplies(SkyLyricsConfig cfg) {
		return cfg.layout == Layout.ARC;
	}

	/** Whether Shadow does anything: Arc leaves it off while Depth draws its solid edge, and World has no Shadow. */
	static boolean shadowApplies(SkyLyricsConfig cfg) {
		return flatLayout(cfg.layout) && !(cfg.layout == Layout.ARC && cfg.depth);
	}

	/**
	 * Whether Show underground too does anything: it only matters on Auto, with Hide in caves off, in the flat layouts, and
	 * See through blocks already puts the lyrics on the screen layer wherever you are.
	 */
	static boolean showUndergroundApplies(SkyLyricsConfig cfg) {
		return cfg.layer == Layer.AUTO && !cfg.hideInCaves && flatLayout(cfg.layout) && !cfg.seeThroughBlocks;
	}

	/** Whether any sky effect is on, so the sky settings (Strength and the rest) show. */
	static boolean anySkyEffect(SkyLyricsConfig cfg) {
		return cfg.sky != SkyEffect.OFF || cfg.constellations || cfg.shootingStars || cfg.twinkle;
	}

	/** Whether Sky speed does anything: the night sky, the constellations and the sparkles run on its clock; shooting stars don't. */
	static boolean skySpeedApplies(SkyLyricsConfig cfg) {
		return cfg.sky != SkyEffect.OFF || cfg.constellations || cfg.twinkle;
	}

	/**
	 * What the Distance slider reads, in real blocks. The stored value stays as it is: in the sky it is blocks (kept
	 * under 80% of the render distance, which {@code renderChunks} gives), floating it is a setting that maps to 4 to 16 blocks.
	 */
	static String distanceBlocks(Placement placement, double value, int renderChunks) {
		if (placement == Placement.SKY) {
			return String.valueOf(Math.round(Math.max(8, Math.min(value, renderChunks * 16 * 0.8f))));
		}
		// Past 64 a slider step moves under 0.1 block, so it needs the second decimal to read differently
		return String.format(Locale.ROOT, value > 64 ? "%.2f" : "%.1f", LyricsRenderer.floatingDistance((float) value));
	}

	/** The Player button's choices: Auto, then this system's players. */
	static List<String> playerChoices(String[] keys, List<String> names, String preferred, String auto) {
		List<String> all = Stream.concat(Stream.of(auto), names.stream()).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
		// A key from a shared settings file that this system doesn't list is still applied when a player matches, so show it
		if (!preferred.isBlank() && playerIndex(keys, preferred) >= keys.length) {
			all.add(shortKey(preferred));
		}
		return all;
	}

	private static final int PLAYER_LABEL_MAX = 24;

	/** A long hand-edited key would make the button as wide as the row, so show at most 24 characters (code points) and an ellipsis. */
	static String shortKey(String key) {
		if (key.codePointCount(0, key.length()) <= PLAYER_LABEL_MAX) {
			return key;
		}
		return key.substring(0, key.offsetByCodePoints(0, PLAYER_LABEL_MAX - 1)) + "\u2026";
	}

	/** Which choice is on: the one whose key is saved, else Auto. */
	static int playerIndex(String[] keys, String preferred) {
		for (int i = 0; i < keys.length; i++) {
			if (keys[i].equalsIgnoreCase(preferred)) {
				return i;
			}
		}
		return preferred.isBlank() ? 0 : keys.length;
	}

	private static final String LRCLIB = "https://lrclib.net";

	private static final int TAB_COUNT = 5;
	/** Not a real tab: the matches for what is typed in the search box. */
	private static final int SEARCH_TAB = TAB_COUNT;
	private static final String[] TAB_ICONS = {"☁", "✦", "♫", "◐", "ℹ"};
	private static String tabName(int i) {
		return switch (i) {
			case 0 -> Tr.t("Lyrics");
			case 1 -> Tr.t("Look");
			case 2 -> Tr.t("Music");
			case 3 -> Tr.t("Display");
			case SEARCH_TAB -> Tr.t("Search");
			default -> Tr.t("About");
		};
	}

	private static String tabSubtitle(int i) {
		return switch (i) {
			case 0 -> Tr.t("Where the lyrics go and how they move");
			case 1 -> Tr.t("Colours, glow and style");
			case 2 -> Tr.t("Your music player and lyrics");
			case 3 -> Tr.t("When to show things");
			case SEARCH_TAB -> Tr.t("Find any setting by its name");
			default -> Tr.t("About this mod");
		};
	}
	private static final int MUSIC_TAB = 2;
	/** The soft colours of the new look first (periwinkle, pink, lavender, sky blue, cream), then brighter ones. */
	private static final int[] SWATCHES = {
			0xC7D0F4, 0xEEBAE8, 0xAE90F6, 0x8CDAFF, 0xC8B29C,
			0xFF6EC7, 0xFF5A5A, 0xFFA24C, 0xFFE066, 0x6BFF8E, 0xFFFFFF,
	};
	/** What the Narrator calls each of {@link #SWATCHES}, in the same order. */
	private static List<String> swatchNames() {
		return List.of(Tr.t("Periwinkle"), Tr.t("Soft pink"), Tr.t("Lavender"), Tr.t("Sky blue"), Tr.t("Cream"),
				Tr.t("Hot pink"), Tr.t("Red"), Tr.t("Orange"), Tr.t("Yellow"), Tr.t("Green"), Tr.t("White"));
	}
	/** Night sky choices in {@link SkyEffect} order. Five names don't fit side by side, so this is a Cycle. */
	private static List<String> skyNames() {
		return List.of(Tr.t("Off"), Tr.t("Aurora"), Tr.t("Nebula"), Tr.t("Synthwave"), Tr.t("Eclipse"), Tr.t("Aurora + nebula"), Tr.t("Moonlight"), Tr.t("Galaxy"), Tr.t("Comet"), Tr.t("Random"));
	}
	/** Part of the player name to look for, next to the name shown; the first one is Auto. */
	private static final String[] PLAYER_KEYS = SkyLyricsClient.windows()
			? new String[] {"", "spotify", "chrom", "edge", "firefox", "media player", SpotifyWebSource.KEY}
			: SkyLyricsClient.mac()
					? new String[] {"", "spotify", "music", SpotifyWebSource.KEY}
					: new String[] {"", "spotify", "firefox", "chrom", "mpv", "vlc", SpotifyWebSource.KEY};
	private static final List<String> PLAYER_NAMES = SkyLyricsClient.windows()
			? List.of("Spotify", "Chrome", "Edge", "Firefox", "Media Player", "Spotify Web")
			: SkyLyricsClient.mac()
					? List.of("Spotify", "Music", "Spotify Web")
					: List.of("Spotify", "Firefox", "Chrome", "mpv", "VLC", "Spotify Web");

	private static final int SIDEBAR_W = 96;
	/** Width of the Lyrics switch under the tabs: room for a 40px name (the French "Paroles" is 39) beside the switch. */
	static final int MASTER_TOGGLE_W = SIDEBAR_W - 6;
	private static final int TAB_H = 20;
	private static final int SEARCH_Y = 35;
	private static final int SEARCH_H = 15;
	private static final int TABS_Y = 56;
	private static final int HEADER_H = 40;
	private static final int FOOTER_H = 32;
	private static final long SOLO_DELAY_NS = 180_000_000L;
	private static final long RESET_CONFIRM_NS = 3_000_000_000L;

	private static int lastTab;

	private final @Nullable Screen parent;
	private final SkyLyricsConfig cfg = SkyLyricsConfig.get();
	private final SkyLyricsClient mod = SkyLyricsClient.get();
	/** Why the last Spotify sign-in couldn't start, like the port being taken. */
	private String spotifyError = "";
	private final List<List<Card>> tabs = new ArrayList<>();
	private final Toggle masterToggle;
	private final float[] scroll = new float[TAB_COUNT + 1];
	private final float[] scrollTarget = new float[TAB_COUNT + 1];
	private String query = "";
	/** How many results the last search edit found, in words; the Narrator says it with the search box. */
	private String searchSaid = "";
	private boolean typing;
	/** The tab to go back to when the search is cleared. */
	private int searchFrom;

	private int tab = lastTab;
	private int px, py, pw, ph;
	private int viewX, viewY, viewW, viewH;
	private int contentH;
	/** Set by {@link #showSpotify}: the Music tab scrolls to its bottom the next time it is laid out. */
	private boolean scrollToEnd;
	private float indicatorY = Float.NaN;
	private float openAnim;
	private float tabAnim = 1;
	private long lastFrame;
	private final ClickRescue rescue = new ClickRescue();
	private final ClickRescue.Target rescueTarget = new ClickRescue.Target() {
		@Override
		public boolean click(MouseButtonEvent event) {
			return mouseClicked(event, false);
		}

		@Override
		public boolean drag(MouseButtonEvent event, double dx, double dy) {
			return mouseDragged(event, dx, dy);
		}

		@Override
		public boolean release(MouseButtonEvent event) {
			return mouseReleased(event);
		}
	};
	private boolean dirty;
	private boolean preview;
	private long resetArmedUntil;
	private @Nullable Row active;
	private long activeSince;
	private boolean draggingScrollbar;
	private double scrollbarGrab;
	private int previewX, previewW;
	private int pillX, pillW;
	private int resetW;

	/** The buttons outside the list of settings that the keyboard can reach. */
	private enum Chrome {
		PREVIEW, RESET, DONE
	}

	/** The row or {@link Chrome} button the keyboard is on, or null. The Lyrics switch in the sidebar is a row too. */
	private @Nullable Object focus;
	/** Where {@link #focus} was in the ring last time, to land nearby when its row is hidden. */
	private int focusAt;
	/** Whether to draw the focus ring: after keys, not after the mouse. */
	private boolean focusVisible;
	/** The row the keyboard was on in each tab. */
	private final @Nullable Row[] tabFocus = new Row[TAB_COUNT + 1];
	/** What the Narrator says instead of the focused row, after a tab changes. */
	private @Nullable String announcement;
	/** Whether the mouse has moved since the screen opened (or since the last tab switch by keyboard). */
	private boolean mouseMovedYet;
	private double mouseFromX = Double.NaN, mouseFromY;

	public SkyLyricsScreen(@Nullable Screen parent) {
		super(Component.literal("Beli's SkyLyrics"));
		this.parent = parent;
		KeyMapping openKey = mod.openSettingsKey();
		closeKeyHeld = openKey != null && openKey.isDown();
		masterToggle = new Toggle(Tr.t("Lyrics"), null, () -> cfg.enabled, v -> cfg.enabled = v);
		tabs.add(lyricsTab());
		tabs.add(lookTab());
		tabs.add(musicTab());
		tabs.add(displayTab());
		tabs.add(aboutTab());
		tabs.add(List.of());
	}

	// ---- Tabs -------------------------------------------------------------------------------------

	private List<Card> lyricsTab() {
		Row layout = new Segmented(Tr.t("Layout"), null,
				new String[] {Tr.t("Arc"), Tr.t("Lines"), Tr.t("World")}, () -> cfg.layout.ordinal(),
				i -> cfg.layout = Layout.values()[i])
				.describe(() -> switch (cfg.layout) {
					case ARC -> Tr.t("One line on a gentle curve");
					case LINES -> Tr.t("The previous, current and next line");
					case WORLD -> Tr.t("Words appear around you");
				});
		// World words float around you, so sky or floating doesn't apply; the direction says where they gather
		Row placement = new Segmented(Tr.t("Placement"), Tr.t("Far up in the sky, or close by"),
				new String[] {Tr.t("Sky"), Tr.t("Floating")}, () -> cfg.placement.ordinal(),
				i -> cfg.placement = Placement.values()[i])
				.when(() -> cfg.layout != Layout.WORLD);
		Row direction = new Segmented(Tr.t("Direction"), Tr.t("Follow keeps them where you look"),
				new String[] {Tr.t("Follow"), Tr.t("N"), Tr.t("E"), Tr.t("S"), Tr.t("W")},
				() -> cfg.direction == Direction.FOLLOW ? 0 : cfg.direction.ordinal() + 1,
				i -> cfg.direction = i == 0 ? Direction.FOLLOW : Direction.values()[i - 1])
				.when(() -> cfg.placement == Placement.SKY || cfg.layout == Layout.WORLD);
		Row lazyFollow = new Toggle(Tr.t("Lazy follow"), Tr.t("Wait until you turn away, then glide over"),
				() -> cfg.lazyFollow, v -> cfg.lazyFollow = v)
				.when(() -> cfg.placement == Placement.SKY && cfg.layout != Layout.WORLD && cfg.direction == Direction.FOLLOW);
		Row viewLock = new Toggle(Tr.t("View lock"), Tr.t("Pin the lyrics where you look now"),
				() -> cfg.viewLock, v -> cfg.viewLock = v)
				.when(() -> cfg.placement == Placement.SKY || cfg.layout == Layout.WORLD);
		Row height = new Slider(Tr.t("Height"), Tr.t("How high above the horizon"), -10, 80, 1,
				() -> cfg.height, v -> cfg.height = (float) v, v -> Math.round(v) + "°")
				.when(() -> heightApplies(cfg.layout, cfg.placement, cfg.direction));
		Row distance = new Slider(Tr.t("Distance"), Tr.t("How far away they float"), 8, 160, 1,
				() -> cfg.distance, v -> cfg.distance = (float) v, v -> Tr.t("%s blocks", distanceBlocks(cfg.placement, v, Minecraft.getInstance().options.getEffectiveRenderDistance())))
				.when(() -> cfg.layout != Layout.WORLD);
		// World words float at a mix of distances, so their setting is times the usual instead of blocks
		Row worldDistance = new Slider(Tr.t("Distance"), Tr.t("How far away they float"), 0.5, 2, 0.05,
				() -> cfg.worldDistance, v -> cfg.worldDistance = (float) v, v -> String.format(Locale.ROOT, "%.2f×", v))
				.when(() -> cfg.layout == Layout.WORLD);
		Row size = new Slider(Tr.t("Size"), null, 0.3, 3, 0.05,
				() -> cfg.size, v -> cfg.size = (float) v, v -> String.format(Locale.ROOT, "%.2f×", v));
		Row worldWords = new Slider(Tr.t("Words at once"), Tr.t("How many words float around you"), 1, 10, 1,
				() -> cfg.worldWords, v -> cfg.worldWords = (int) Math.round(v), v -> String.valueOf(Math.round(v)))
				.when(() -> cfg.layout == Layout.WORLD && !(cfg.worldFlow && cfg.worldHold));
		Row linger = new Slider(Tr.t("Linger"), Tr.t("How slowly sung words fade"), 0.8, 6, 0.1,
				() -> cfg.worldFade, v -> cfg.worldFade = (float) v, v -> String.format(Locale.ROOT, "%.1f s", v))
				.when(() -> cfg.layout == Layout.WORLD);
		Row tilt = new Slider(Tr.t("Tilt"), Tr.t("How much the words turn and lean, 0 faces you"), 0, 2, 0.05,
				() -> cfg.worldTilt, v -> cfg.worldTilt = (float) v, v -> Math.round(v * 100) + "%")
				.when(() -> cfg.layout == Layout.WORLD);
		Row rise = new Slider(Tr.t("Float up"), Tr.t("How far the words drift up, 0 keeps them still"), 0, 2, 0.05,
				() -> cfg.worldRise, v -> cfg.worldRise = (float) v, v -> Math.round(v * 100) + "%")
				.when(() -> cfg.layout == Layout.WORLD);
		Row flow = new Toggle(Tr.t("Flowing line"), Tr.t("Words read left to right instead of scattering"),
				() -> cfg.worldFlow, v -> cfg.worldFlow = v)
				.when(() -> cfg.layout == Layout.WORLD);
		Row curve = new Slider(Tr.t("Line curve"), Tr.t("How much a flowing line slopes and bends, 0 keeps it straight"), 0, 2, 0.05,
				() -> cfg.worldCurve, v -> cfg.worldCurve = (float) v, v -> Math.round(v * 100) + "%")
				.when(() -> cfg.layout == Layout.WORLD && cfg.worldFlow);
		Row hold = new Toggle(Tr.t("Keep the whole line"), Tr.t("Earlier words stay lit until the line ends"),
				() -> cfg.worldHold, v -> cfg.worldHold = v)
				.when(() -> cfg.layout == Layout.WORLD);
		Row nudge = new Toggle(Tr.t("Slide apart"), Tr.t("Words that overlap glide out of each other's way"),
				() -> cfg.worldNudge, v -> cfg.worldNudge = v)
				.when(() -> cfg.layout == Layout.WORLD);
		Row middle = new Toggle(Tr.t("Keep the middle clear"), Tr.t("Words float off to the side so you can always see the game"),
				() -> cfg.worldClearMiddle, v -> cfg.worldClearMiddle = v)
				.when(() -> cfg.layout == Layout.WORLD);
		Row avoid = new Toggle(Tr.t("Keep off players"), Tr.t("Words move out of the way of other players, for PvP"),
				() -> cfg.worldAvoidPlayers, v -> cfg.worldAvoidPlayers = v)
				.when(() -> cfg.layout == Layout.WORLD);
		Row knock = new Toggle(Tr.t("Hit to knock"), Tr.t("Hitting a word knocks it away and it springs back"),
				() -> cfg.worldKnock, v -> cfg.worldKnock = v)
				.when(() -> cfg.layout == Layout.WORLD);
		Row tint = new Toggle(Tr.t("Sky tint"), Tr.t("Words take on a little of the sky colour"),
				() -> cfg.worldTint, v -> cfg.worldTint = v)
				.when(() -> cfg.layout == Layout.WORLD);
		Row glide = new Toggle(Tr.t("Elytra mode"), Tr.t("Words travel with you when you glide, ride or fly"),
				() -> cfg.worldGlide, v -> cfg.worldGlide = v)
				.when(() -> cfg.layout == Layout.WORLD);
		Row death = new Toggle(Tr.t("Death animation"), Tr.t("Words drop out of the sky when you die"),
				() -> cfg.worldDeath, v -> cfg.worldDeath = v)
				.when(() -> cfg.layout == Layout.WORLD);
		Row songTitle = new Toggle(Tr.t("Song title"), Tr.t("A new song's name floats up in the sky"),
				() -> cfg.worldTitle, v -> cfg.worldTitle = v)
				.when(() -> cfg.layout == Layout.WORLD);

		Row previous = new Toggle(Tr.t("Previous line"), Tr.t("The line that just ended, faded"),
				() -> cfg.showPrevious, v -> cfg.showPrevious = v)
				.when(() -> cfg.layout == Layout.LINES);
		Row next = new Toggle(Tr.t("Next line"), Tr.t("What's coming up, dimmed"),
				() -> cfg.showNext, v -> cfg.showNext = v)
				.when(() -> cfg.layout == Layout.LINES);
		Row karaoke = new Toggle(Tr.t("Karaoke fill"), Tr.t("Words light up as they are sung, in the lyrics and in the top bar"),
				() -> cfg.karaoke, v -> cfg.karaoke = v)
				.when(() -> karaokeApplies(cfg));
		Row grow = new Toggle(Tr.t("Bigger current word"), Tr.t("The word being sung grows a little"),
				() -> cfg.growWord, v -> cfg.growWord = v)
				.when(() -> flatLayout(cfg.layout) && cfg.karaoke);
		Row animation = new Segmented(Tr.t("Animation"), Tr.t("How lines come in"),
				new String[] {Tr.t("Words"), Tr.t("Fade"), Tr.t("Rise"), Tr.t("Pop"), Tr.t("Type"), Tr.t("Slide")}, () -> cfg.animation.ordinal(),
				i -> cfg.animation = Animation.values()[i])
				.when(() -> cfg.layout == Layout.LINES); // the arc and World words have their own way of coming in
		// World words don't come in as lines; this says how each word shows up instead
		Row arrival = new Segmented(Tr.t("Arrival"), null,
				new String[] {Tr.t("Pop"), Tr.t("Drop"), Tr.t("Zoom"), Tr.t("Fade"), Tr.t("Type")}, () -> cfg.worldArrival.ordinal(),
				i -> cfg.worldArrival = WorldArrival.values()[i])
				.describe(() -> switch (cfg.worldArrival) {
					case POP -> Tr.t("Words pop up, or glide in on a flowing line");
					case DROP -> Tr.t("Each word drops in from above and bounces");
					case ZOOM -> Tr.t("Each word grows out of nothing");
					case FADE -> Tr.t("Each word slowly fades in");
					case TYPE -> Tr.t("Each word's letters appear one by one, as if typed");
				})
				.when(() -> cfg.layout == Layout.WORLD);
		Row leaving = new Segmented(Tr.t("Leaving"), null,
				new String[] {Tr.t("Shrink"), Tr.t("Float"), Tr.t("Blow"), Tr.t("Sink"), Tr.t("Dissolve")}, () -> cfg.worldLeave.ordinal(),
				i -> cfg.worldLeave = WorldLeave.values()[i])
				.describe(() -> switch (cfg.worldLeave) {
					case SHRINK -> Tr.t("Each sung word fades and shrinks a little");
					case FLOAT -> Tr.t("Each sung word floats up as it fades");
					case BLOW -> Tr.t("Each sung word blows away on the wind");
					case SINK -> Tr.t("Each sung word sinks down as it fades");
					case DISSOLVE -> Tr.t("Each sung word comes apart letter by letter");
				})
				.when(() -> cfg.layout == Layout.WORLD);
		Row soft = new Toggle(Tr.t("Soft edges"), Tr.t("Only a glow round the letters, no dark shadow or outline"),
				() -> cfg.worldSoft, v -> cfg.worldSoft = v)
				.when(() -> cfg.layout == Layout.WORLD);
		Row big = new Toggle(Tr.t("One big word"), Tr.t("One word in each line is drawn bigger than the rest"),
				() -> cfg.worldBig, v -> cfg.worldBig = v)
				.when(() -> cfg.layout == Layout.WORLD);
		Row pulse = new Toggle(Tr.t("Pulse"), Tr.t("The glow flares up as each new line starts"),
				() -> cfg.pulse, v -> cfg.pulse = v)
				.when(() -> flatLayout(cfg.layout));
		Row capitals = new Toggle(Tr.t("CAPITALS"), Tr.t("Write every lyric in capital letters"),
				() -> cfg.uppercase, v -> cfg.uppercase = v);

		return List.of(
				new Card(Tr.t("Position"), layout, placement, direction, viewLock, lazyFollow, height, distance, worldDistance, size, worldWords, linger, tilt, rise, flow, curve, hold, middle, nudge, avoid, knock, tint, glide, death, songTitle),
				new Card(Tr.t("Lines"), previous, next, karaoke, grow, animation, arrival, leaving, soft, big, pulse, capitals));
	}

	private List<Card> lookTab() {
		Row mode = new Segmented(Tr.t("Colour"), Tr.t("From the album cover, or your own"),
				new String[] {Tr.t("Album"), Tr.t("Pick"), Tr.t("Rainbow"), Tr.t("Time of day"), Tr.t("Each line")}, () -> cfg.colorMode.ordinal(),
				i -> cfg.colorMode = ColorMode.values()[i]);
		Row swatches = new Swatches(Tr.t("Pick a colour"), null, SWATCHES, swatchNames(),
				() -> cfg.colorMode == ColorMode.FIXED ? cfg.color : -1,
				c -> {
					cfg.color = c;
					cfg.colorMode = ColorMode.FIXED;
				}).narrateAs(Tr.t("Lyrics colour"));
		Row albumColor = new Info(Tr.t("Album colour"), null, () -> {
			Track track = mod.tracker().track();
			if (track == Track.NONE) {
				return Tr.t("no song playing");
			}
			return mod.art().hasTexture() ? String.format(Locale.ROOT, "#%06X", mod.art().color() & 0xFFFFFF) : Tr.t("no album cover");
		}).when(() -> cfg.colorMode == ColorMode.ALBUM);
		Row twoTone = new Toggle(Tr.t("Two-tone"), Tr.t("Each line fades into a second colour"),
				() -> cfg.twoTone, v -> cfg.twoTone = v);
		Row toneSwatches = new Swatches(Tr.t("Second colour"), null, SWATCHES, swatchNames(), () -> cfg.toneColor,
				c -> cfg.toneColor = c).when(() -> cfg.twoTone);

		// Five choices over four switches: Bold, Smooth and Round are all the smooth font, told apart by boldFont and roundFont
		Row font = new Segmented(Tr.t("Font"), Tr.t("Letter style"),
				new String[] {Tr.t("Bold"), Tr.t("Smooth"), Tr.t("Round"), Tr.t("Handwritten"), Tr.t("Minecraft")},
				() -> !cfg.smoothFont ? 4 : cfg.scriptFont ? 3 : cfg.roundFont ? 2 : cfg.boldFont ? 0 : 1,
				i -> {
					cfg.smoothFont = i != 4;
					cfg.scriptFont = i == 3;
					cfg.roundFont = i == 2;
					if (i < 2) {
						cfg.boldFont = i == 0;
					}
				});
		Row depth = new Toggle(Tr.t("Depth"), Tr.t("A solid edge that makes the letters look 3D"),
				() -> cfg.depth, v -> cfg.depth = v)
				.when(() -> depthApplies(cfg));
		Row glow = new Slider(Tr.t("Glow"), Tr.t("Soft light around the letters"), 0, 1, 0.05,
				() -> cfg.glow, v -> cfg.glow = (float) v, SkyLyricsScreen::percent);
		Row glowMode = new Segmented(Tr.t("Glow colour"), Tr.t("The words' colour, or one of its own"),
				new String[] {Tr.t("Same"), Tr.t("Pick")}, () -> cfg.glowPick ? 1 : 0, i -> cfg.glowPick = i == 1)
				.when(() -> cfg.glow > 0.01f);
		Row glowSwatches = new Swatches(Tr.t("Pick a colour"), null, SWATCHES, swatchNames(),
				() -> cfg.glowPick ? cfg.glowColor : -1,
				c -> { cfg.glowColor = c; cfg.glowPick = true; })
				.narrateAs(Tr.t("Glow colour"))
				.when(() -> cfg.glow > 0.01f && cfg.glowPick);
		Row shadow = new Toggle(Tr.t("Shadow"), Tr.t("A dark edge so text reads on bright skies"),
				() -> cfg.shadow, v -> cfg.shadow = v)
				.when(() -> shadowApplies(cfg));
		Row opacity = new Slider(Tr.t("Opacity"), null, 0.2, 1, 0.05,
				() -> cfg.opacity, v -> cfg.opacity = (float) v, SkyLyricsScreen::percent);
		Row seeThrough = new Toggle(Tr.t("See through blocks"), Tr.t("Stay visible behind hills and walls"),
				() -> cfg.seeThroughBlocks, v -> cfg.seeThroughBlocks = v);
		Row layer = new Segmented(Tr.t("Draw on top"), null,
				new String[] {Tr.t("Auto"), Tr.t("Always"), Tr.t("Never")}, () -> switch (cfg.layer) {
					case AUTO -> 0;
					case SCREEN -> 1;
					case WORLD -> 2;
				}, i -> cfg.layer = i == 0 ? Layer.AUTO : i == 1 ? Layer.SCREEN : Layer.WORLD)
				.describe(() -> switch (cfg.layer) {
					case AUTO -> Tr.t("On top when needed: shaders, rain, See through blocks");
					case SCREEN -> Tr.t("Over everything: shaders and clouds can't dim it");
					case WORLD -> Tr.t("In the world: hills hide it, shaders light it");
				})
				.when(() -> flatLayout(cfg.layout));

		Row night = new Cycle(Tr.t("Night sky"), Tr.t("Shows at night with music"), skyNames(),
				() -> cfg.sky.ordinal(), i -> cfg.sky = SkyEffect.values()[i]);
		BooleanSupplier anySky = () -> anySkyEffect(cfg);
		Row shootingStars = new Toggle(Tr.t("Shooting stars"), Tr.t("Now and then, more when the lyrics are busy"),
				() -> cfg.shootingStars, v -> cfg.shootingStars = v);
		Row twinkle = new Toggle(Tr.t("Twinkling stars"), Tr.t("Soft sparkles around the lyrics"),
				() -> cfg.twinkle, v -> cfg.twinkle = v);
		Row skyColors = new Segmented(Tr.t("Sky colours"), null,
				new String[] {Tr.t("Classic"), Tr.t("Album"), Tr.t("Lyrics")}, () -> cfg.skyColors.ordinal(),
				i -> cfg.skyColors = SkyColors.values()[i]).when(anySky);
		Row skySpeed = new Segmented(Tr.t("Sky speed"), null,
				new String[] {Tr.t("Slow"), Tr.t("Normal"), Tr.t("Fast")}, () -> cfg.skySpeed.ordinal(),
				i -> cfg.skySpeed = SkySpeed.values()[i]).when(() -> skySpeedApplies(cfg));
		Row skyStrength = new Slider(Tr.t("Strength"), null, 0.1, 1, 0.05,
				() -> cfg.skyStrength, v -> cfg.skyStrength = (float) v, SkyLyricsScreen::percent)
				.when(anySky);
		Row skyPulse = new Toggle(Tr.t("Pulse with the lyrics"), Tr.t("Brightens as each new line starts"),
				() -> cfg.skyPulse, v -> cfg.skyPulse = v).when(anySky);
		Row constellations = new Toggle(Tr.t("Constellations"), Tr.t("Star patterns joined by thin lines"),
				() -> cfg.constellations, v -> cfg.constellations = v);
		Row hat = new Cycle(Tr.t("Hat"), Tr.t("See it in third person (F5). Only you can see it"),
				List.of(Tr.t("None"), Tr.t("Halo"), Tr.t("Neon headphones"), Tr.t("Conical hat"), Tr.t("Crown"),
						Tr.t("Cat ears"), Tr.t("Pumpkin head"), Tr.t("Top hat"), Tr.t("Devil horns"),
						Tr.t("Music notes"), Tr.t("Neon shades"), Tr.t("Antennae"), Tr.t("Flower crown"),
						Tr.t("Propeller cap")),
				() -> cfg.hat.ordinal(), i -> cfg.hat = Hat.values()[i]);
		Row hatPulse = new Toggle(Tr.t("Glow with the lyrics"), Tr.t("Flares up with each new line"),
				() -> cfg.hatPulse, v -> cfg.hatPulse = v).when(() -> cfg.hat != Hat.NONE);
		Row crosshair = new Cycle(Tr.t("Crosshair"), Tr.t("Drawn in your lyrics colour"),
				List.of(Tr.t("Normal"), Tr.t("Dot"), Tr.t("Cross"), Tr.t("Circle"), Tr.t("Corners"),
						Tr.t("Note"), Tr.t("Heart"), Tr.t("Diamond")),
				() -> cfg.crosshair.ordinal(), i -> cfg.crosshair = Crosshair.values()[i]);
		Row crosshairPulse = new Toggle(Tr.t("Flash with the lyrics"), Tr.t("Brightens as each new line starts"),
				() -> cfg.crosshairPulse, v -> cfg.crosshairPulse = v).when(() -> cfg.crosshair != Crosshair.NORMAL);

		return List.of(
				new Card(Tr.t("Themes"), new ThemePicker(cfg)),
				new Card(Tr.t("Colour"), mode, swatches, albumColor, twoTone, toneSwatches),
				new Card(Tr.t("Style"), font, depth, glow, glowMode, glowSwatches, shadow, opacity, seeThrough, layer),
				new Card(Tr.t("Sky"), night, constellations, shootingStars, twinkle, skyColors, skySpeed, skyStrength, skyPulse),
				new Card(Tr.t("Hat"), hat, hatPulse),
				new Card(Tr.t("Crosshair"), crosshair, crosshairPulse));
	}

	private List<Card> musicTab() {
		MediaTracker tracker = mod.tracker();
		LyricsService service = mod.lyrics();
		Row status = new Buttons(Tr.t("Lyrics"), "",
				new Button(() -> Tr.t("Retry"), service::retry,
						() -> service.state() == LyricsService.State.NOT_FOUND || service.state() == LyricsService.State.ERROR,
						false))
				.describe(this::lyricsStatus);

		String savedPlayer = cfg.preferredPlayer;
		Row player = new Cycle(Tr.t("Player"), Tr.t("Which app to follow"), playerChoices(PLAYER_KEYS, PLAYER_NAMES, savedPlayer, Tr.t("Auto")),
				() -> playerIndex(PLAYER_KEYS, cfg.preferredPlayer),
				i -> {
					cfg.preferredPlayer = i < PLAYER_KEYS.length ? PLAYER_KEYS[i] : savedPlayer;
					tracker.refresh();
				});
		Row connection = new Info(Tr.t("Status"), null, () -> {
			if (cfg.demoMode) {
				return Tr.t("Playing the demo song");
			}
			if (!tracker.connected()) {
				String error = tracker.error();
				return error == null || error.isEmpty() ? Tr.t("Can't reach music players") : error;
			}
			return tracker.track() == Track.NONE
					? (SkyLyricsClient.mac() && !SpotifyWebSource.KEY.equals(cfg.preferredPlayer)
							? Tr.t("No Spotify or Music playing (browsers can't be read on a Mac)") : Tr.t("No music playing")) : Tr.t("Following %s", tracker.sourceName());
		});
		// The error often says what to allow or install, too long for the Status line
		Row connectionHelp = new Paragraph(() -> tracker.error())
				.when(() -> !cfg.demoMode && !tracker.connected() && !tracker.error().isEmpty());
		Row timing = new Slider(Tr.t("Timing"), Tr.t("Late? Move right. Early? Left"), -5000, 5000, 50,
				() -> cfg.syncOffsetMs, v -> cfg.syncOffsetMs = (int) Math.round(v), SkyLyricsScreen::seconds).narrateHint();
		Row songTiming = new Slider(Tr.t("This song"), Tr.t("Added to Timing, for this song only"), -5000, 5000, 50,
				() -> cfg.songOffset(tracker.track()), v -> cfg.setSongOffset(tracker.track(), (int) Math.round(v)),
				SkyLyricsScreen::seconds).when(() -> tracker.track() != Track.NONE).searchOnlyWhenShown();
		Row demo = new Toggle(Tr.t("Demo song"), Tr.t("A built-in song for trying things out"),
				() -> cfg.demoMode, v -> {
					cfg.demoMode = v;
					tracker.refresh();
				});
		Row plain = new Toggle(Tr.t("Untimed lyrics"), Tr.t("If a song has no timing, spread the lines out"),
				() -> cfg.showPlainLyrics, v -> cfg.showPlainLyrics = v);

		Row library = new Buttons(Tr.t("Song library"), Tr.t("Pick a song to play (key: %s)", keyName(mod.libraryKey())),
				//? if >=26.2 {
				/*new Button(Tr.t("Open"), () -> minecraft.gui.setScreen(new LibraryScreen(this))));
				*///?} else {
				new Button(Tr.t("Open"), () -> minecraft.setScreen(new LibraryScreen(this))));
				//?}
		Row recent = new Toggle(Tr.t("Recently heard"), Tr.t("Remember the songs you play, to pick from the song library. Kept on this computer only"),
				() -> cfg.recentSongs, v -> cfg.recentSongs = v);
		Row forget = new Buttons(Tr.t("Forget recently heard"), Tr.t("Empties the Recently heard list"),
				new Button(Tr.t("Clear"), () -> mod.heard().clear())).when(() -> cfg.recentSongs);

		return List.of(
				new Card(Tr.t("Now playing"), new NowPlayingRow(), status, library, recent, forget),
				new Card(Tr.t("Player"), player, connection, connectionHelp, timing, songTiming, demo, plain),
				spotifyCard());
	}

	/** Shown when "Spotify Web" is the player: the few steps to make a Spotify app and sign in with it. */
	private Card spotifyCard() {
		SpotifyWebSource spotify = mod.spotify();
		BooleanSupplier web = () -> SpotifyWebSource.KEY.equals(cfg.preferredPlayer);
		Row help = new Paragraph(() -> Tr.t("Follows your Spotify account on any device, even your phone. Spotify needs you to make a free app for this: open the dashboard, create an app with the Redirect URI below and Web API ticked, then paste its Client ID here."))
				.when(web);
		Row dashboard = new Buttons(Tr.t("Spotify dashboard"), Tr.t("Needs a Spotify Premium account"),
				new Button(Tr.t("Open"), () -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(SpotifyWebSource.DASHBOARD))))
				.when(web);
		Row redirect = new Buttons(Tr.t("Redirect URI"), SpotifyWebSource.REDIRECT,
				new Button(Tr.t("Copy"), () -> minecraft.keyboardHandler.setClipboard(SpotifyWebSource.REDIRECT)))
				.when(web);
		Row clientId = new Buttons(Tr.t("Client ID"), "",
				new Button(Tr.t("Paste"), () -> {
					String pasted = minecraft.keyboardHandler.getClipboard().strip();
					String next = pasted.length() > 64 ? pasted.substring(0, 64) : pasted;
					if (!next.equals(cfg.spotifyClientId)) {
						spotify.cancelLogin(); // a sign-in waiting in the browser belongs to the old ID
					}
					cfg.spotifyClientId = next;
					tracker().refresh();
				}))
				.describe(() -> {
					String id = cfg.spotifyClientId;
					if (id.isEmpty()) {
						return Tr.t("Not set yet");
					}
					return SpotifyWebSource.validClientId(id) ? id.substring(0, 4) + "…" + id.substring(28) : Tr.t("That isn't a Client ID");
				})
				.when(web);
		Row account = new Buttons(Tr.t("Account"), "",
				new Button(() -> switch (spotify.account()) {
					case SIGNED_IN -> Tr.t("Sign out");
					case WAITING -> Tr.t("Cancel");
					default -> Tr.t("Sign in");
				}, () -> {
					switch (spotify.account()) {
						case SIGNED_IN -> {
							spotifyError = "";
							spotify.signOut();
						}
						case WAITING -> spotify.cancelLogin();
						default -> {
							spotifyError = "";
							try {
								ConfirmLinkScreen.confirmLinkNow(this, URI.create(spotify.startLogin()));
							} catch (IOException e) {
								spotifyError = e.getMessage();
							}
						}
					}
					tracker().refresh();
				}, () -> spotify.account() != SpotifyWebSource.Account.NO_CLIENT_ID, true))
				.describe(() -> switch (spotify.account()) {
					case NO_CLIENT_ID -> Tr.t("Paste your Client ID first");
					case SIGNED_OUT -> Tr.t("Not signed in");
					case WAITING -> Tr.t("Waiting for your browser…");
					case SIGNED_IN -> Tr.t("Signed in");
				})
				.when(web);
		Row problem = new Paragraph(() -> spotify.problem().isEmpty() ? spotifyError : spotify.problem())
				.when(() -> web.getAsBoolean() && !(spotify.problem().isEmpty() && spotifyError.isEmpty())
						&& spotify.account() != SpotifyWebSource.Account.SIGNED_IN);
		return new Card(Tr.t("Spotify account"), help, dashboard, redirect, clientId, account, problem);
	}

	private MediaTracker tracker() {
		return mod.tracker();
	}

	public static String seconds(double ms) {
		return Math.abs(ms) < 5 ? "0 s" : String.format(Locale.ROOT, "%+.2f s", ms / 1000);
	}

	private List<Card> displayTab() {
		Row f1 = new Toggle(Tr.t("Hide with F1"), Tr.t("When you hide the HUD"), () -> cfg.hideWithF1, v -> cfg.hideWithF1 = v);
		Row menus = new Toggle(Tr.t("Hide in menus"), Tr.t("When a menu or chat is open"), () -> cfg.hideInMenus, v -> cfg.hideInMenus = v);
		Row paused = new Toggle(Tr.t("Hide when paused"), Tr.t("When the music is paused"), () -> cfg.hideWhenPaused, v -> cfg.hideWhenPaused = v);
		Row nether = new Toggle(Tr.t("Hide in the Nether"), null, () -> cfg.hideInNether, v -> cfg.hideInNether = v);
		Row end = new Toggle(Tr.t("Hide in the End"), null, () -> cfg.hideInEnd, v -> cfg.hideInEnd = v);
		Row water = new Toggle(Tr.t("Hide underwater"), Tr.t("While your head is underwater"),
				() -> cfg.hideUnderwater, v -> cfg.hideUnderwater = v);
		Row caves = new Toggle(Tr.t("Hide in caves"), Tr.t("When there's no open sky above you"),
				() -> cfg.hideInCaves, v -> cfg.hideInCaves = v);
		Row underground = new Toggle(Tr.t("Show underground too"), Tr.t("With See through blocks off, lyrics stay visible in caves and under roofs"),
				() -> cfg.showUnderground, v -> cfg.showUnderground = v)
				.when(() -> showUndergroundApplies(cfg));
		Row multiplayer = new Toggle(Tr.t("Hide in multiplayer"), Tr.t("Only show things in your own worlds"),
				() -> cfg.hideInMultiplayer, v -> cfg.hideInMultiplayer = v);

		Row card = new Toggle(Tr.t("Top bar"), Tr.t("Song and lyrics in a bar on screen"),
				() -> cfg.nowPlayingHud, v -> cfg.nowPlayingHud = v);
		Row barLyrics = new Toggle(Tr.t("Lyrics in the bar"), Tr.t("The line being sung, word by word"),
				() -> cfg.barLyrics, v -> cfg.barLyrics = v)
				.when(() -> cfg.nowPlayingHud);
		Row clock = new Toggle(Tr.t("Clock"), Tr.t("The time on the left of the bar"),
				() -> cfg.barClock, v -> cfg.barClock = v)
				.when(() -> cfg.nowPlayingHud);
		Row ping = new Toggle(Tr.t("Ping"), Tr.t("Signal bars for your connection to the server"),
				() -> cfg.barPing, v -> cfg.barPing = v)
				.when(() -> cfg.nowPlayingHud);
		Row frameRate = new Toggle(Tr.t("FPS"), Tr.t("Frames per second, right of the bar"),
				() -> cfg.barStats, v -> cfg.barStats = v)
				.when(() -> cfg.nowPlayingHud);
		Row onlyOnChange = new Toggle(Tr.t("Song name only on change"), Tr.t("The song name shows for a few seconds, then hides"),
				() -> cfg.hudOnlyOnChange, v -> cfg.hudOnlyOnChange = v)
				.when(() -> cfg.nowPlayingHud);
		Row showBar = new Segmented(Tr.t("Show bar"), Tr.t("Can hide while you see the lyrics in the world"),
				new String[] {Tr.t("Always"), Tr.t("Lyrics out of view")}, () -> cfg.barShow.ordinal(),
				i -> cfg.barShow = BarShow.values()[i]).when(() -> cfg.nowPlayingHud);
		Row barPosition = new Segmented(Tr.t("Bar position"), Tr.t("Bottom sits just above the hotbar"),
				new String[] {Tr.t("Top"), Tr.t("Bottom")}, () -> cfg.barPosition.ordinal(),
				i -> cfg.barPosition = BarPosition.values()[i]).when(() -> cfg.nowPlayingHud);
		Row barSize = new Slider(Tr.t("Bar size"), null, 0.75, 1.5, 0.05,
				() -> cfg.barSize, v -> cfg.barSize = (float) v, SkyLyricsScreen::percent).when(() -> cfg.nowPlayingHud);
		// The Record card uses the same darkness, so the row stays while either is on
		Row barBackground = new Slider(Tr.t("Background"), Tr.t("How dark the bar and the Record card are"), 0, 1, 0.05,
				() -> cfg.barBackground, v -> cfg.barBackground = (float) v, SkyLyricsScreen::percent)
				.when(() -> cfg.nowPlayingHud || cfg.recordCard);
		Row barAccent = new Segmented(Tr.t("Accent colour"), Tr.t("Highlight, equaliser, progress and signal bars"),
				new String[] {Tr.t("Album"), Tr.t("Lyrics"), Tr.t("Pick")}, () -> cfg.barAccent.ordinal(),
				i -> cfg.barAccent = BarAccent.values()[i]).when(() -> cfg.nowPlayingHud);
		Row barSwatches = new Swatches(Tr.t("Pick a colour"), null, SWATCHES, swatchNames(),
				() -> cfg.barAccent == BarAccent.FIXED ? cfg.barAccentColor : -1,
				c -> { cfg.barAccentColor = c; cfg.barAccent = BarAccent.FIXED; })
				.narrateAs(Tr.t("Accent colour"))
				.when(() -> cfg.nowPlayingHud && cfg.barAccent == BarAccent.FIXED);
		Row barArt = new Toggle(Tr.t("Album cover"), Tr.t("The round album cover on the left"),
				() -> cfg.barArt, v -> cfg.barArt = v).when(() -> cfg.nowPlayingHud);
		Row barSpin = new Toggle(Tr.t("Spin like a record"), Tr.t("The album cover turns slowly while the song plays"),
				() -> cfg.barSpin, v -> cfg.barSpin = v).when(() -> cfg.nowPlayingHud && cfg.barArt);
		Row barEq = new Toggle(Tr.t("Equaliser"), Tr.t("Bars that move with the music"),
				() -> cfg.barEqualiser, v -> cfg.barEqualiser = v).when(() -> cfg.nowPlayingHud);
		Row barProgress = new Toggle(Tr.t("Song progress"), Tr.t("A line that fills up as the song plays"),
				() -> cfg.barProgress, v -> cfg.barProgress = v).when(() -> cfg.nowPlayingHud);

		Row effects = new Segmented(Tr.t("Effects"), Tr.t("Less glow, more FPS"),
				new String[] {Tr.t("Auto"), Tr.t("Full"), Tr.t("Light")}, () -> cfg.effects.ordinal(),
				i -> cfg.effects = Effects.values()[i]);
		Row effectsNow = new Info(Tr.t("Right now"), null, () -> {
			int fps = Math.round(mod.renderer().fps());
			String state = mod.renderer().lightEffects() ? Tr.t("light effects") : Tr.t("full effects");
			return fps > 0 ? fps + " FPS · " + state : state;
		}).when(() -> cfg.effects == Effects.AUTO);

		Row title = new Toggle(Tr.t("Lyrics on the title screen"), Tr.t("Under the buttons of the main menu"),
				() -> cfg.titleScreenLyrics, v -> cfg.titleScreenLyrics = v);

		Row openKey = new Info(Tr.t("Open settings"), null, () -> keyName(mod.openSettingsKey()));
		Row controls = new Buttons(Tr.t("More keys"), Tr.t("Toggle lyrics, play/pause, skip songs"),
				//? if >=26.2 {
				/*new Button(Tr.t("Controls"), () -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options))));
				*///?} else {
				new Button(Tr.t("Controls"), () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options))));
				//?}

		Row record = new Toggle(Tr.t("Record card"), Tr.t("A small card in a corner with a spinning record, the song and how far it has played"),
				() -> cfg.recordCard, v -> cfg.recordCard = v);
		Row recordCorner = new Segmented(Tr.t("Corner"), Tr.t("Which corner of the screen the record card sits in"),
				new String[] {Tr.t("Bottom right"), Tr.t("Bottom left"), Tr.t("Top left"), Tr.t("Top right")},
				() -> cfg.cardCorner.ordinal(), i -> cfg.cardCorner = SkyLyricsConfig.CardCorner.values()[i]).when(() -> cfg.recordCard);
		Row recordLyrics = new Toggle(Tr.t("Lyric line"), Tr.t("The card also shows the line being sung, under the record"),
				() -> cfg.cardLyrics, v -> cfg.cardLyrics = v).when(() -> cfg.recordCard);
		Row recordLines = new Segmented(Tr.t("Card lines"), Tr.t("Just the line being sung, or the line before and after it too"),
				new String[] {Tr.t("1 line"), Tr.t("3 lines")},
				() -> cfg.cardLines >= 3 ? 1 : 0, i -> cfg.cardLines = i == 1 ? 3 : 1).when(() -> cfg.recordCard && cfg.cardLyrics);

		return List.of(
				new Card(Tr.t("Hide"), f1, menus, paused, nether, end, water, caves, underground, multiplayer),
				new Card(Tr.t("Top bar"), card, barLyrics, clock, ping, frameRate, onlyOnChange, showBar, barPosition, barSize,
						barBackground, barAccent, barSwatches, barArt, barSpin, barEq, barProgress),
				new Card(Tr.t("Record card"), record, recordCorner, recordLyrics, recordLines),
				new Card(Tr.t("Main menu"), title),
				new Card(Tr.t("Performance"), effects, effectsNow),
				new Card(Tr.t("Keys"), openKey, controls));
	}

	private List<Card> aboutTab() {
		String version = FabricLoader.getInstance().getModContainer(SkyLyricsClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		Row intro = new Paragraph(() -> Tr.t("Beli's SkyLyrics shows the words of whatever song you are playing, floating in the Minecraft sky. It is free and open source (AGPL licence)."));
		Row versionRow = new Info(Tr.t("Version"), null, () -> version);
		Row how = new Paragraph(() -> SkyLyricsClient.windows()
				? Tr.t("On Windows the song comes from the media controls that pop up when you press a media key, so Spotify, browsers and most players work. Lyrics come from LRCLIB, a free community lyrics library, or from NetEase Cloud Music when LRCLIB is down or doesn't have the song. They're saved on your computer for next time.")
				: SkyLyricsClient.mac()
				? Tr.t("On macOS the mod asks the Spotify and Music apps what's playing. The first time, macOS asks if your launcher may control them: click OK. Browsers and other players can't be seen on a Mac. Lyrics come from LRCLIB, a free community lyrics library, or from NetEase Cloud Music when LRCLIB is down or doesn't have the song. They're saved on your computer for next time.")
				: Tr.t("The song comes from your music app through MPRIS, the same Linux feature your media keys use, so Spotify, browsers and most players work. Lyrics come from LRCLIB, a free community lyrics library, or from NetEase Cloud Music when LRCLIB is down or doesn't have the song. They're saved on your computer for next time."));
		Row compat = new Paragraph(() -> Tr.t("Works with Sodium, Iris and shader packs."));
		Row links = new Buttons(Tr.t("Links"), null,
				new Button("GitHub", () -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(GITHUB))),
				new Button("LRCLIB", () -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(LRCLIB))));
		Row updates = new Segmented(Tr.t("Updates"), Tr.t("Checked once when the game starts"),
				new String[] {Tr.t("Install"), Tr.t("Tell me"), Tr.t("Off")}, () -> cfg.updates.ordinal(),
				i -> cfg.updates = Updates.values()[i])
				.describe(() -> switch (cfg.updates) {
					case INSTALL -> Tr.t("Download new versions by itself");
					case NOTIFY -> Tr.t("Only say when a new version is out");
					case OFF -> Tr.t("Never look for updates");
				});
		return List.of(
				new Card("Beli's SkyLyrics", intro, versionRow),
				new Card(Tr.t("How it works"), how, compat, links),
				new Card(Tr.t("Updates"), updates));
	}

	private String lyricsStatus() {
		if (mod.tracker().track() == Track.NONE) {
			return Tr.t("Waiting for a song");
		}
		LyricsService.Snapshot snap = mod.lyrics().snapshot();
		Lyrics lyrics = snap.lyrics();
		return switch (snap.state()) {
			case IDLE -> Tr.t("Waiting for a song");
			case LOADING -> Tr.t("Looking for lyrics…");
			case READY -> lyrics.instrumental() ? Tr.t("Instrumental, no words")
					: lyrics.synced() ? Tr.t("Found, with timing") : Tr.t("Found, without timing");
			case NOT_FOUND -> Tr.t("No lyrics found for this song. Try Retry.");
			case ERROR -> Tr.t("Couldn't reach the lyrics sites. Check your internet, then press Retry.");
		};
	}

	private static String percent(double v) {
		return Math.round(v * 100) + "%";
	}

	private static String keyName(@Nullable KeyMapping key) {
		if (key == null || key.isUnbound()) {
			return Tr.t("not set");
		}
		return key.getTranslatedKeyMessage().getString();
	}

	// ---- Layout -----------------------------------------------------------------------------------

	/** Width of the settings rows on a screen {@code screenW} wide: the panel (at most 440, 8 from each edge) less the sidebar. */
	static int rowWidth(int screenW) {
		return Math.max(1, Math.min(440, screenW - 16) - SIDEBAR_W - 24);
	}

	private void layout() {
		pw = Math.min(440, width - 16);
		ph = Math.min(272, height - 16);
		px = (width - pw) / 2;
		py = (height - ph) / 2 + Math.round((1 - openAnim) * 8);
		viewX = px + SIDEBAR_W + 12;
		viewW = rowWidth(width);
		viewY = py + HEADER_H;
		viewH = ph - HEADER_H - FOOTER_H;

		int y = viewY + 2 - Math.round(scroll[tab]);
		int x = viewX + Math.round((1 - tabAnim) * 8);
		int start = y;
		for (Card card : tabs.get(tab)) {
			List<Row> rows = shownRows(card);
			if (rows.isEmpty()) {
				continue;
			}
			y += 12;
			for (Row row : rows) {
				if (row instanceof Paragraph p) {
					p.measure(font, viewW);
				}
				row.x = x;
				row.w = viewW;
				row.y = y;
				row.prepare(font);
				y += row.height() + 1;
			}
			y += 9;
		}
		contentH = y - start;
		// Only now is this tab's height known (the last frame's belongs to another tab, or is 0 on the first frame)
		scrollTarget[tab] = Focus.settleScroll(scrollTarget[tab], scrollToEnd && tab == MUSIC_TAB, contentH, viewH);
		if (tab == MUSIC_TAB) {
			scrollToEnd = false;
		}
		masterToggle.x = px + 4;
		masterToggle.w = MASTER_TOGGLE_W;
		masterToggle.y = py + ph - 30;
	}

	private float maxScroll() {
		return Focus.maxScroll(contentH, viewH);
	}

	private List<Row> shownRows(Card card) {
		if (tab == SEARCH_TAB) {
			// Search results stay put while you change them, even if a change would hide one elsewhere
			return card.rows();
		}
		List<Row> rows = new ArrayList<>(card.rows().size());
		for (Row row : card.rows()) {
			if (row.shown()) {
				rows.add(row);
			}
		}
		return rows;
	}

	private boolean inView(double mx, double my) {
		return Ui.inside(mx, my, viewX - 2, viewY, viewW + 4, viewH);
	}

	private @Nullable Row rowAt(double mx, double my) {
		if (!inView(mx, my)) {
			return null;
		}
		for (Card card : tabs.get(tab)) {
			for (Row row : shownRows(card)) {
				if (Ui.inside(mx, my, row.x, row.y, row.w, row.height())) {
					return row;
				}
			}
		}
		return null;
	}

	private boolean solo() {
		return active instanceof Slider && System.nanoTime() - activeSince > SOLO_DELAY_NS;
	}

	// ---- Keyboard focus ---------------------------------------------------------------------------

	/** Every stop Tab visits, in order: the Lyrics switch, Preview, this tab's rows, Reset all, Done. */
	private record Ring(List<Object> stops, boolean[] available) {
	}

	private Ring ring() {
		List<Object> stops = new ArrayList<>();
		stops.add(masterToggle);
		stops.add(Chrome.PREVIEW);
		for (Card card : tabs.get(tab)) {
			stops.addAll(card.rows());
		}
		stops.add(Chrome.RESET);
		stops.add(Chrome.DONE);
		boolean[] available = new boolean[stops.size()];
		for (int i = 0; i < available.length; i++) {
			available[i] = !(stops.get(i) instanceof Row row) || row == masterToggle || tab == SEARCH_TAB || row.shown();
		}
		return new Ring(stops, available);
	}

	/** Moves the keyboard to the next ({@code dir} 1) or previous (-1) stop, skipping hidden rows and wrapping round. */
	private void moveFocus(int dir) {
		layout();
		Ring ring = ring();
		int from = focus == null ? -1 : ring.stops().indexOf(focus);
		if (from < 0 && dir > 0) {
			from = 1; // from nothing, going forward starts at the first row, after the Lyrics switch and Preview
		}
		int to = Focus.next(ring.available(), from, dir);
		if (to >= 0) {
			focus = ring.stops().get(to);
			focusAt = to;
			focusVisible = true;
			announcement = focus instanceof Row landed ? hintAnnouncement(landed) : null;
			if (focus instanceof Row row && row != masterToggle) {
				tabFocus[tab] = row;
				int top = row.y - (viewY + 2) + Math.round(scroll[tab]);
				scrollTarget[tab] = Focus.scrollIntoView(scrollTarget[tab], top, top + row.height(), viewH, 14, maxScroll());
			}
			afterKeyboardAction();
		}
	}

	/** Keeps the focus on a row that can still be reached: a hidden row hands it to the next one, a row of another tab drops it. */
	private void validateFocus() {
		if (!(focus instanceof Row row) || row == masterToggle) {
			return;
		}
		Ring ring = ring();
		int at = ring.stops().indexOf(row);
		if (at >= 0 && ring.available()[at]) {
			focusAt = at;
			return;
		}
		int to = at < 0 ? -1 : Focus.next(ring.available(), at, 1);
		focus = to < 0 ? null : ring.stops().get(to);
		focusAt = Math.max(to, 0);
		if (focus instanceof Row now && now != masterToggle) {
			tabFocus[tab] = now;
		}
	}

	/** Goes to the next or previous tab from the keyboard, back to the row the keyboard was on there. */
	private void switchTab(int dir) {
		int to = Math.floorMod((tab == SEARCH_TAB ? searchFrom : tab) + dir, TAB_COUNT);
		selectTab(to);
		if (focus == null) {
			focus = tabFocus[to];
		}
		focusVisible = true;
		// A new tab starts from the keyboard: the mouse has to move again before it picks a row
		mouseMovedYet = false;
		mouseFromX = Double.NaN;
		announcement = Narration.tab(tabName(to), Tr.t("tab"), Tr.t("%s of %s", to + 1, TAB_COUNT),
				focus instanceof Row row ? Narration.hinted(rowNarration(row), row.hint()) : "");
		afterKeyboardAction();
	}

	private void press(Chrome button) {
		switch (button) {
			case PREVIEW -> preview = true;
			case RESET -> pressReset();
			case DONE -> onClose();
		}
	}

	/** The first press of Reset all asks to be pressed again; the second resets. */
	private void pressReset() {
		if (System.nanoTime() < resetArmedUntil) {
			cfg.resetToDefaults();
			resetArmedUntil = 0;
			dirty = true;
			mod.tracker().refresh();
			announcement = Tr.t("Settings reset");
		} else {
			resetArmedUntil = System.nanoTime() + RESET_CONFIRM_NS;
		}
	}

	private int doneW() {
		return Controls.buttonW(font.width(Tr.t("Done")), 56);
	}

	/** x, y, width and height of a button outside the settings list. */
	private int[] chromeRect(Chrome button) {
		int fy = py + ph - FOOTER_H + 8;
		return switch (button) {
			case PREVIEW -> new int[] {previewX, py + 12, previewW, 16};
			case RESET -> new int[] {viewX, fy, resetW, 16};
			case DONE -> new int[] {px + pw - 12 - doneW(), fy, doneW(), 16};
		};
	}

	/** What the Narrator says for what has the keyboard now. */
	private String focusNarration() {
		if (announcement != null) {
			return announcement;
		}
		if (typing) {
			return Narration.join(Tr.t("Search"), Tr.t("text box"), query, searchSaid);
		}
		if (focus instanceof Row row) {
			return rowNarration(row);
		}
		if (focus instanceof Chrome button) {
			String name = switch (button) {
				case PREVIEW -> Tr.t("Preview");
				case RESET -> System.nanoTime() < resetArmedUntil ? Tr.t("Click again to reset") : Tr.t("Reset all");
				case DONE -> Tr.t("Done");
			};
			return Narration.join(name, Tr.t("button"));
		}
		return "";
	}

	/** What is said once when the keyboard lands on a row that has a hint; nothing for the others (their words speak as usual). */
	private @Nullable String hintAnnouncement(Row row) {
		return row.hint().isEmpty() ? null : Narration.hinted(rowNarration(row), row.hint());
	}

	/** A row's words; in the search results a row that its tab is hiding right now says so. */
	private String rowNarration(Row row) {
		return Narration.row(row.narration(), tab == SEARCH_TAB && row != masterToggle && !row.shown(), Tr.t("not in use now"));
	}

	@Override
	public void mouseMoved(double mx, double my) {
		if (Double.isNaN(mouseFromX)) {
			mouseFromX = mx;
			mouseFromY = my;
		} else if (Focus.mouseMoved(mouseFromX, mouseFromY, mx, my)) {
			mouseMovedYet = true;
		}
		super.mouseMoved(mx, my);
	}

	@Override
	protected void updateNarrationState(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getNarrationMessage());
		// Vanilla's own hint talks about Tab and arrows moving over widgets, which this screen doesn't have
		output.add(NarratedElementType.USAGE, Component.literal(
				Tr.t("Tab or Up and Down to move, Left and Right to change, Enter to press, Page Up and Page Down to scroll, Ctrl+Tab for the next tab, Ctrl+F to search")));
		String now = focusNarration();
		if (!now.isEmpty()) {
			output.nest().add(NarratedElementType.TITLE, Component.literal(now));
		}
	}

	// ---- Drawing ----------------------------------------------------------------------------------

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	*///?} else {
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	//?}
		// In a world, leave it fully visible so changes can be seen on the real lyrics.
		if (minecraft.level == null) {
			//? if >=26.1 {
			/*super.extractBackground(g, mouseX, mouseY, partialTick);
			*///?} else {
			super.renderBackground(g, mouseX, mouseY, partialTick);
			//?}
		}
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	//?}
		//? if <1.21.6 {
		/*// up to 1.21.5 the background is drawn from render() itself (which this screen replaces); 1.21.6+ draws it before calling render()
		renderBackground(g, mouseX, mouseY, partialTick);
		*///?}
		rescue.poll(mouseX, mouseY, rescueTarget);
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		openAnim = Ui.approach(openAnim, 1, dt, 12);
		tabAnim = Ui.approach(tabAnim, 1, dt, 14);
		scroll[tab] = Ui.approach(scroll[tab], scrollTarget[tab], dt, 18);
		layout();
		validateFocus();

		if (preview) {
			renderPreviewHint(g);
			return;
		}
		if (solo()) {
			Row row = active;
			Ui.rect(g, row.x - 4, row.y - 4, row.w + 8, row.height() + 8, 8, Ui.PANEL);
			row.render(new Frame(g, font, mouseX, mouseY, dt, true));
			return;
		}

		Frame frame = new Frame(g, font, mouseX, mouseY, dt, active == null);
		Ui.rect(g, px - 1, py - 1, pw + 2, ph + 2, 9, 0x50000000);
		Ui.rect(g, px, py, pw, ph, 8, Ui.PANEL);
		renderSidebar(g, frame);
		renderHeader(g, frame);
		renderContent(g, frame);
		renderFooter(g, frame);
		if (focusVisible && focus instanceof Chrome button) {
			int[] r = chromeRect(button);
			Ui.outline(g, r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, 6, Ui.TEXT);
		}
	}

	private void renderSidebar(GuiGraphics g, Frame f) {
		Ui.rect(g, px, py, SIDEBAR_W, ph, 8, Ui.SIDEBAR);
		g.fill(px + SIDEBAR_W - 8, py, px + SIDEBAR_W, py + ph, Ui.SIDEBAR);
		g.fill(px + SIDEBAR_W, py, px + SIDEBAR_W + 1, py + ph, Ui.DIVIDER);

		Ui.rect(g, px + 10, py + 11, 16, 16, 4, Ui.ACCENT);
		g.drawCenteredString(font, "♪", px + 18, py + 15, 0xFFFFFFFF);
		g.drawString(font, Ui.visual("SkyLyrics"), px + 31, py + 10, Ui.TEXT, false);
		g.drawString(font, Ui.visual("by Beli"), px + 31, py + 20, Ui.DIM, false);

		int tabsY = py + TABS_Y;
		renderSearch(g, f);
		float target = tabsY + tab * (TAB_H + 2);
		indicatorY = Float.isNaN(indicatorY) ? target : Ui.approach(indicatorY, target, f.dt(), 16);
		int iy = Math.round(indicatorY);
		if (tab != SEARCH_TAB) {
			Ui.rect(g, px + 6, iy, SIDEBAR_W - 12, TAB_H, 5, Ui.ACCENT_SOFT);
			Ui.rect(g, px + 6, iy + 5, 2, TAB_H - 10, 1, Ui.ACCENT);
		}
		for (int i = 0; i < TAB_COUNT; i++) {
			int ty = tabsY + i * (TAB_H + 2);
			boolean hover = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), px + 6, ty, SIDEBAR_W - 12, TAB_H);
			if (hover && i != tab) {
				Ui.rect(g, px + 6, ty, SIDEBAR_W - 12, TAB_H, 5, 0x14FFFFFF);
			}
			int color = i == tab ? 0xFFFFFFFF : hover ? Ui.TEXT : Ui.MUTED;
			g.drawString(font, Ui.visual(TAB_ICONS[i]), px + 14, ty + 6, i == tab ? Ui.ACCENT : color, false);
			g.drawString(font, Ui.visual(tabName(i)), px + 28, ty + 6, color, false);
		}

		g.fill(px + 10, py + ph - 36, px + SIDEBAR_W - 10, py + ph - 35, Ui.DIVIDER);
		masterToggle.render(f);
		if (focusVisible && focus == masterToggle) {
			masterToggle.renderFocus(f);
		}
	}

	private void renderSearch(GuiGraphics g, Frame f) {
		int sx = px + 8;
		int sy = py + SEARCH_Y;
		int sw = SIDEBAR_W - 16;
		boolean hover = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), sx, sy, sw, SEARCH_H);
		Ui.rect(g, sx, sy, sw, SEARCH_H, 4, typing || hover ? Ui.CONTROL_HOVER : Ui.CONTROL);
		if (typing) {
			Ui.outline(g, sx, sy, sw, SEARCH_H, 4, Ui.ACCENT);
		}
		g.drawString(font, "⌕", sx + 4, sy + 4, Ui.DIM, false);
		int textW = sw - 16 - (query.isEmpty() ? 0 : 10);
		boolean placeholder = query.isEmpty() && !typing;
		String shown = placeholder ? Tr.t("Search") : query;
		if (font.width(shown) > textW) {
			shown = placeholder ? Ui.clip(font, shown, textW) : "…" + tail(shown, textW - font.width("…"));
		}
		String caret = typing && System.currentTimeMillis() % 1000 < 550 ? "_" : "";
		g.drawString(font, Ui.visual(shown + caret), sx + 13, sy + 4, placeholder ? Ui.DIM : Ui.TEXT, false);
		if (!query.isEmpty()) {
			boolean clearHover = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), sx + sw - 12, sy, 12, SEARCH_H);
			g.drawString(font, "×", sx + sw - 9, sy + 4, clearHover ? Ui.TEXT : Ui.DIM, false);
		}
	}

	/** The end of {@code text} that fits in {@code maxW}, so the letters being typed stay in sight. */
	private String tail(String text, int maxW) {
		return dev.beli.skylyrics.util.TextCut.suffix(text, t -> font.width(t) <= maxW);
	}

	/** Every row whose name, description or card matches the search, in cards named after their tab. */
	/**
	 * Every setting whose name, description or choices match the search. Settings that are hidden right now
	 * because of another setting (like Glow colour while Glow is at zero) come last, under their own heading,
	 * so you still find them and can see they wait on something else.
	 */
	private List<Card> searchResults() {
		List<Card> found = new ArrayList<>();
		List<Card> waiting = new ArrayList<>();
		for (int i = 0; i < TAB_COUNT; i++) {
			for (Card card : tabs.get(i)) {
				List<Row> rows = new ArrayList<>();
				List<Row> hidden = new ArrayList<>();
				for (Row row : card.rows()) {
					String text = card.title() + " " + row.searchText() + " "
							+ dev.beli.skylyrics.Tr.english(card.title()) + " " + row.englishSearchText();
					if (row instanceof Paragraph || !SettingsSearch.matches(text, query)) {
						continue;
					}
					if (row.shown()) {
						rows.add(row);
					} else if (row.searchWhenHidden()) {
						hidden.add(row);
					}
				}
				String title = tabName(i) + " · " + card.title();
				if (!rows.isEmpty()) {
					found.add(new Card(title, rows));
				}
				if (!hidden.isEmpty()) {
					waiting.add(new Card(title + " · " + Tr.t("not in use now"), hidden));
				}
			}
		}
		found.addAll(waiting);
		return found;
	}

	private void editSearch(String text) {
		query = dev.beli.skylyrics.util.TextCut.limit(text, 60);
		if (tab != SEARCH_TAB) {
			searchFrom = tab;
			tab = SEARCH_TAB;
			tabAnim = 0;
		}
		tabs.set(SEARCH_TAB, query.isBlank() ? List.of() : searchResults());
		int found = tabs.get(SEARCH_TAB).stream().mapToInt(c -> c.rows().size()).sum();
		searchSaid = query.isBlank() ? "" : Narration.resultCount(true, false, found, "", Tr.t("Nothing matches, try another word"),
				Tr.t("1 result"), Tr.t("%s results", found));
		scrollTarget[SEARCH_TAB] = 0;
		scroll[SEARCH_TAB] = 0;
	}

	/** Puts {@code text} in the search box and shows what it finds, as if it had been typed. */
	public void search(String text) {
		typing = true;
		editSearch(text);
	}

	/** Empties the search box and goes back to the tab you were on. */
	private void clearSearch() {
		query = "";
		searchSaid = "";
		typing = false;
		tabs.set(SEARCH_TAB, List.of());
		if (tab == SEARCH_TAB) {
			tab = searchFrom;
			tabAnim = 0;
		}
	}

	private void renderHeader(GuiGraphics g, Frame f) {
		int bw = font.width(Tr.t("Preview")) + 16;
		int bx = px + pw - 12 - bw;
		int textRoom = Ui.headerRoom(viewX, bx);
		g.drawString(font, Ui.visual(Ui.clip(font, tabName(tab), textRoom)), viewX, py + 12, Ui.TEXT, false);
		g.drawString(font, Ui.visual(Ui.clip(font, tabSubtitle(tab), textRoom)), viewX, py + 23, Ui.MUTED, false);

		previewX = bx;
		previewW = bw;
		pillW = 0;
		boolean hoverPreview = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), bx, py + 12, bw, 16);
		Controls.drawButton(f, bx, py + 12, bw, 16, Tr.t("Preview"), hoverPreview, false, true);

		int titleW = Math.max(font.width(tabName(tab)), font.width(tabSubtitle(tab)));
		int room = bx - 6 - (viewX + titleW + 10);
		if (room < 60) {
			return;
		}
		Track track = mod.tracker().track();
		String text = track == Track.NONE ? Tr.t("Nothing playing")
				: track.artist().isEmpty() ? track.title() : track.title() + " · " + track.artist();
		pillW = Math.min(room, Math.min(150, font.width(text) + 22));
		pillX = bx - 6 - pillW;
		boolean hover = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), pillX, py + 12, pillW, 16);
		Ui.rect(g, pillX, py + 12, pillW, 16, 8, hover ? Ui.CONTROL_HOVER : Ui.CONTROL);
		int dot = track == Track.NONE ? Ui.DIM : mod.tracker().playing() ? 0xFF000000 | mod.art().color() : Ui.MUTED;
		Ui.rect(g, pillX + 7, py + 18, 4, 4, 2, dot);
		g.drawString(font, Ui.visual(Ui.clip(font, text, pillW - 22)), pillX + 15, py + 16, hover ? 0xFFFFFFFF : Ui.TEXT, false);
	}

	/** Widest a hovered description's tooltip gets before it wraps. */
	private static final int TOOLTIP_MAX_W = 240;
	/** Room kept between a tooltip's text and a screen edge: its box reaches 4 past the text, and 4 more stay clear. */
	private static final int TOOLTIP_EDGE = 8;

	/** A hovered description in lines no wider than {@link #TOOLTIP_MAX_W} that also fit a screen {@code screenW} wide. */
	static List<String> tooltipLines(net.minecraft.client.StringSplitter splitter, String text, int screenW) {
		int room = Math.max(1, Math.min(TOOLTIP_MAX_W, screenW - 2 * TOOLTIP_EDGE));
		return splitter.splitLines(text, room, net.minecraft.network.chat.Style.EMPTY).stream()
				.map(net.minecraft.network.chat.FormattedText::getString).toList();
	}

	/**
	 * Where a tooltip {@code w} by {@code h} goes for the mouse at {@code mx}, {@code my} on a screen {@code screenW} by
	 * {@code screenH}: {x, y} of its text. Like vanilla, right of the mouse or else left of it, and above the bottom; but
	 * its box stays clear of both side edges (vanilla's could reach 4 past the right one).
	 */
	static int[] tooltipPos(int screenW, int screenH, int mx, int my, int w, int h) {
		int x = mx + 12;
		int y = my - 12;
		if (x + w + TOOLTIP_EDGE > screenW) {
			x = mx - 12 - w;
		}
		x = Math.max(TOOLTIP_EDGE, Math.min(x, screenW - TOOLTIP_EDGE - w));
		if (y + h + 3 > screenH) {
			y = screenH - h - 3;
		}
		return new int[] {x, y};
	}

	private static final net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner TOOLTIP_PLACE =
			(screenW, screenH, mx, my, w, h) -> {
				int[] at = tooltipPos(screenW, screenH, mx, my, w, h);
				return new org.joml.Vector2i(at[0], at[1]);
			};

	/** The hovered row's cut texts as tooltip lines, one text after another, each in its own direction (right to left in Hebrew). */
	private List<net.minecraft.util.FormattedCharSequence> tooltip(List<String> texts) {
		List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
		for (String text : texts) {
			boolean rtl = Ui.paragraphRtl(text);
			tooltipLines(font.getSplitter(), text, width).forEach(line -> lines.add(Ui.visual(line, rtl)));
		}
		return lines;
	}

	private void renderContent(GuiGraphics g, Frame f) {
		boolean hoverOk = f.hoverAllowed() && inView(f.mx(), f.my());
		Frame inner = new Frame(g, font, f.mx(), f.my(), f.dt(), hoverOk);
		g.enableScissor(viewX - 4, viewY, px + pw - 4, viewY + viewH);
		for (Card card : tabs.get(tab)) {
			List<Row> rows = shownRows(card);
			if (rows.isEmpty()) {
				continue;
			}
			Row first = rows.getFirst();
			Row last = rows.getLast();
			int top = first.y;
			int bottom = last.y + last.height();
			if (bottom < viewY - 20 || top - 12 > viewY + viewH) {
				continue;
			}
			g.drawString(font, Ui.visual(Ui.clip(font, card.title().toUpperCase(Locale.ROOT), first.w - 4)), first.x + 2, top - 10, Ui.DIM, false);
			Ui.rect(g, first.x, top, first.w, bottom - top, 6, Ui.CARD);
			for (Row row : rows) {
				if (row != first) {
					g.fill(row.x + 10, row.y - 1, row.x + row.w - 10, row.y, Ui.DIVIDER);
				}
				if (!(row instanceof Paragraph) && (row.hovered(inner) || row == active)) {
					Ui.rect(g, row.x + 2, row.y + 1, row.w - 4, row.height() - 2, 4, Ui.CARD_HOVER);
				}
				row.render(inner);
				if (focusVisible && row == focus) {
					row.renderFocus(inner);
				}
				List<String> cut = row.hovered(inner) ? row.cutTexts(font) : List.of();
				if (!cut.isEmpty()) {
					List<net.minecraft.util.FormattedCharSequence> tip = tooltip(cut);
					//? if >=1.21.6 {
					g.setTooltipForNextFrame(font, tip, TOOLTIP_PLACE, f.mx(), f.my(), false);
					//?} else {
					/*// Drawn after render() returns, so the row clip above can't cut it (renderTooltip draws on the spot here)
					setTooltipForNextRenderPass(tip, TOOLTIP_PLACE, true);
					*///?}
				}
			}
		}
		g.disableScissor();
		if (tab == SEARCH_TAB && tabs.get(tab).stream().allMatch(c -> shownRows(c).isEmpty())) {
			String hint = query.isBlank() ? Tr.t("Type what you're looking for") : Tr.t("Nothing matches, try another word");
			g.drawCenteredString(font, Ui.visual(hint), viewX + viewW / 2, viewY + 30, Ui.DIM);
		}

		float max = maxScroll();
		if (max > 0) {
			int trackX = px + pw - 7;
			int barH = Math.max(16, Math.round(viewH * (viewH / (float) (viewH + max))));
			int barY = viewY + Math.round((viewH - barH) * (scroll[tab] / max));
			boolean hover = draggingScrollbar || f.hoverAllowed() && Ui.inside(f.mx(), f.my(), trackX - 3, viewY, 8, viewH);
			Ui.rect(g, trackX, barY, 2, barH, 1, hover ? Ui.MUTED : Ui.TRACK);
		}
	}

	/**
	 * Where the footer's buttons go in a panel {@code pw} wide, from its left edge: {Reset all x, Reset all width, Done x}.
	 * Reset all is as wide as its label ({@code resetLabelW}) needs, but stops 6 short of Done ({@code doneW} wide): in a
	 * small window the longer "Click again to reset" of some languages ran under Done, so a click on Done reset every
	 * setting. drawButton cuts the label to the button.
	 */
	static int[] footerButtons(int pw, int resetLabelW, int doneW) {
		int resetX = SIDEBAR_W + 12;
		int doneX = pw - 12 - doneW;
		return new int[] {resetX, Math.max(0, Math.min(resetLabelW + 16, doneX - 6 - resetX)), doneX};
	}

	private void renderFooter(GuiGraphics g, Frame f) {
		int fy = py + ph - FOOTER_H + 8;
		g.fill(viewX, fy - 8, px + pw - 12, fy - 7, Ui.DIVIDER);
		boolean armed = System.nanoTime() < resetArmedUntil;
		String reset = armed ? Tr.t("Click again to reset") : Tr.t("Reset all");
		int dw = doneW();
		int dx = px + pw - 12 - dw;
		int rw = footerButtons(pw, font.width(reset), dw)[1];
		resetW = rw;
		boolean hoverReset = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), viewX, fy, rw, 16);
		Controls.drawButton(f, viewX, fy, rw, 16, reset, hoverReset, false, true);
		if (armed) {
			Ui.outline(g, viewX, fy, rw, 16, 4, 0xFFFF6B6B);
		}

		boolean hoverDone = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), dx, fy, dw, 16);
		Controls.drawButton(f, dx, fy, dw, 16, Tr.t("Done"), hoverDone, true, true);

		KeyMapping openKey = mod.openSettingsKey();
		// With the key unbound, "not set or Esc to close" reads wrong: say only the Esc part
		String hint = openKey == null || openKey.isUnbound() ? Tr.t("Esc to close") : Tr.t("%s or Esc to close", keyName(openKey));
		int hintX = (viewX + rw + dx) / 2 - font.width(hint) / 2;
		if (hintX > viewX + rw + 6 && hintX + font.width(hint) < dx - 6) {
			g.drawString(font, Ui.visual(hint), hintX, fy + 4, Ui.DIM, false);
		}
	}

	/** Room the preview hint's pill takes beside its text (17 on the left with the dot, 7 on the right), and from each screen edge. */
	private static final int PREVIEW_PAD = 24;
	private static final int PREVIEW_EDGE = 8;

	/** The preview hint in lines that fit a screen {@code screenW} wide, pill and edges included. */
	static List<String> previewLines(net.minecraft.client.StringSplitter splitter, String text, int screenW) {
		int room = Math.max(1, screenW - 2 * PREVIEW_EDGE - PREVIEW_PAD);
		return splitter.splitLines(text, room, net.minecraft.network.chat.Style.EMPTY).stream()
				.map(net.minecraft.network.chat.FormattedText::getString).toList();
	}

	private void renderPreviewHint(GuiGraphics g) {
		boolean nothing = mod.tracker().track() == Track.NONE;
		String text = nothing ? Tr.t("Nothing is playing. Turn on the demo song on the Music tab to try it.")
				: Tr.t("Previewing. Click or press any key to go back.");
		List<String> lines = previewLines(font.getSplitter(), text, width);
		boolean rtl = Ui.paragraphRtl(text);
		int textW = 0;
		for (String line : lines) {
			textW = Math.max(textW, font.width(line));
		}
		int w = textW + PREVIEW_PAD;
		int h = 8 + 10 * lines.size();
		int x = (width - w) / 2;
		int y = height - 26 - h; // one line sits where it always did (18 high, 44 up); a longer hint grows upward from there
		Ui.rect(g, x, y, w, h, 9, 0xE0141019);
		Ui.rect(g, x + 8, y + 7, 4, 4, 2, Ui.ACCENT);
		for (int i = 0; i < lines.size(); i++) {
			g.drawString(font, Ui.visual(lines.get(i), rtl), x + 17, y + 5 + 10 * i, Ui.TEXT, false);
		}
	}

	// ---- Input ------------------------------------------------------------------------------------

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		announcement = null;
		if (event.button() == ClickRescue.left() && !rescue.pressed()) {
			return true;
		}
		double mx = event.x();
		double my = event.y();
		if (preview) {
			preview = false;
			return true;
		}
		if (event.button() != ClickRescue.left()) {
			return super.mouseClicked(event, doubleClick);
		}

		if (Ui.inside(mx, my, px + 8, py + SEARCH_Y, SIDEBAR_W - 16, SEARCH_H)) {
			if (!query.isEmpty() && mx >= px + SIDEBAR_W - 20) {
				clearSearch();
			} else {
				typing = true;
			}
			return true;
		}
		typing = false;
		focusVisible = false;
		int tabsY = py + TABS_Y;
		for (int i = 0; i < TAB_COUNT; i++) {
			if (Ui.inside(mx, my, px + 6, tabsY + i * (TAB_H + 2), SIDEBAR_W - 12, TAB_H)) {
				selectTab(i);
				return true;
			}
		}
		if (Ui.inside(mx, my, masterToggle.x, masterToggle.y, masterToggle.w, masterToggle.height())) {
			masterToggle.click(mx, my);
			dirty = true;
			return true;
		}

		if (Ui.inside(mx, my, previewX, py + 12, previewW, 16)) {
			preview = true;
			return true;
		}
		if (pillW > 0 && Ui.inside(mx, my, pillX, py + 12, pillW, 16)) {
			selectTab(MUSIC_TAB);
			return true;
		}

		int fy = py + ph - FOOTER_H + 8;
		boolean armed = System.nanoTime() < resetArmedUntil;
		int rw = footerButtons(pw, font.width(armed ? Tr.t("Click again to reset") : Tr.t("Reset all")), doneW())[1];
		if (Ui.inside(mx, my, viewX, fy, rw, 16)) {
			pressReset();
			return true;
		}
		if (Ui.inside(mx, my, px + pw - 12 - doneW(), fy, doneW(), 16)) {
			onClose();
			return true;
		}

		float max = maxScroll();
		if (max > 0 && Ui.inside(mx, my, px + pw - 10, viewY, 8, viewH)) {
			draggingScrollbar = true;
			scrollbarGrab = my;
			return true;
		}

		Row row = rowAt(mx, my);
		if (row != null) {
			// Clicking a row puts the keyboard on it, without a ring until a key is pressed
			focus = row;
			tabFocus[tab] = row;
		}
		if (row != null && row.click(mx, my)) {
			dirty = true;
			if (row instanceof Slider s && s.dragging) {
				active = row;
				activeSince = System.nanoTime();
			}
			return true;
		}
		return Ui.inside(mx, my, px, py, pw, ph) || super.mouseClicked(event, doubleClick);
	}

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (active != null) {
			active.drag(event.x(), event.y());
			dirty = true;
			return true;
		}
		if (draggingScrollbar) {
			float max = maxScroll();
			int barH = Math.max(16, Math.round(viewH * (viewH / (float) (viewH + max))));
			float perPixel = max / Math.max(1, viewH - barH);
			scrollTarget[tab] = Mth.clamp(scrollTarget[tab] + (float) (event.y() - scrollbarGrab) * perPixel, 0, max);
			scroll[tab] = scrollTarget[tab];
			scrollbarGrab = event.y();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean mouseReleased(MouseButtonEvent event) {
		if (!ClickRescue.endsDrag(event.button(), ClickRescue.left())) {
			return super.mouseReleased(event);
		}
		if (!rescue.released()) {
			return true;
		}
		if (active != null) {
			active.release();
			active = null;
			return true;
		}
		if (draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	//? if <1.21.9 {
	/*@Override
	public boolean mouseClicked(double x, double y, int button) {
		return mouseClicked(new MouseButtonEvent(x, y, button), false);
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		return mouseDragged(new MouseButtonEvent(x, y, button), dx, dy);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		return mouseReleased(new MouseButtonEvent(x, y, button));
	}

	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		return keyPressed(new KeyEvent(key, scancode, modifiers));
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		return charTyped(new CharacterEvent(c));
	}

	*///?}
	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (preview || active != null) {
			return true;
		}
		if (Ui.inside(mx, my, px + SIDEBAR_W, py, pw - SIDEBAR_W, ph)) {
			scrollTarget[tab] = Mth.clamp(scrollTarget[tab] - (float) scrollY * 24, 0, maxScroll());
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	/** The settings key was still down when this opened (it opened from that key): its repeats must not close it again. */
	private boolean closeKeyHeld;

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean keyReleased(KeyEvent event) {
		KeyMapping open = mod.openSettingsKey();
		if (open != null && open.matches(event)) {
			closeKeyHeld = false;
		}
		return false;
	}

	//? if <1.21.9 {
	/*@Override
	public boolean keyReleased(int key, int scancode, int modifiers) {
		return keyReleased(new KeyEvent(key, scancode, modifiers));
	}

	*///?}
	//? if >=1.21.9 {
	@Override
	//?}
	public boolean keyPressed(KeyEvent event) {
		announcement = null; // a tab switch (set again below) is said once; after that the focused row speaks
		if (preview) {
			preview = false;
			return true;
		}
		if (typing) {
			if (event.isEscape()) {
				clearSearch();
			} else if (event.isConfirmation()) {
				typing = false;
				if (!tabs.get(SEARCH_TAB).isEmpty()) {
					moveFocus(1);
				}
			} else if (event.isCycleFocus() || event.isDown() || event.isUp()) {
				// Tab or the arrows leave the box for the results
				typing = false;
				moveFocus(ScreenKeys.focusDir(event.isCycleFocus(), event.hasShiftDown(), event.isUp()));
			} else if (event.key() == InputConstants.KEY_BACKSPACE && !query.isEmpty()) {
				editSearch(event.hasControlDownWithQuirk() ? "" : query.substring(0, query.offsetByCodePoints(query.length(), -1)));
			} else if (event.isPaste()) {
				editSearch(SettingsSearch.pasted(query, minecraft.keyboardHandler.getClipboard()));
			}
			// Letters come in through charTyped; the rest must not close the screen or change a setting
			return true;
		}
		//? if >=26.3 {
		/*int letter = event.shortcutKey();
		*///?} else {
		int letter = event.key();
		//?}
		if (ScreenKeys.isCtrlF(letter, event.hasControlDownWithQuirk(), event.hasShiftDown(), event.hasAltDown())) {
			typing = true;
			afterKeyboardAction();
			return true;
		}
		if (tab == SEARCH_TAB && event.isEscape()) {
			clearSearch();
			return true;
		}
		KeyMapping open = mod.openSettingsKey();
		if (open != null && open.matches(event)) {
			if (!closeKeyHeld) {
				mod.closedWithKey(open);
				onClose();
			}
			return true;
		}
		if ((ScreenKeys.realCtrl(event) || event.hasControlDownWithQuirk()) && (event.isCycleFocus() || event.key() == InputConstants.KEY_PAGEUP
				|| event.key() == InputConstants.KEY_PAGEDOWN)) {
			// Ctrl+Tab, Ctrl+Shift+Tab and Ctrl+Page Up/Down change tab, like tabs in a browser
			boolean back = event.isCycleFocus() ? event.hasShiftDown() : event.key() == InputConstants.KEY_PAGEUP;
			switchTab(back ? -1 : 1);
			return true;
		}
		if (event.isCycleFocus() || event.isUp() || event.isDown()) {
			moveFocus(ScreenKeys.focusDir(event.isCycleFocus(), event.hasShiftDown(), event.isUp()));
			return true;
		}
		if (active == null && (event.key() == InputConstants.KEY_PAGEUP || event.key() == InputConstants.KEY_PAGEDOWN)) {
			scrollTarget[tab] = Focus.page(scrollTarget[tab], event.key() == InputConstants.KEY_PAGEUP ? -1 : 1, viewH, maxScroll());
			return true;
		}
		if (active == null && event.key() == InputConstants.KEY_HOME) {
			scrollTarget[tab] = 0;
			return true;
		}
		if (active == null && event.key() == InputConstants.KEY_END) {
			scrollTarget[tab] = maxScroll();
			return true;
		}
		if (active == null) {
			if (focus instanceof Chrome button && event.isSelection()) {
				press(button);
				afterKeyboardAction();
				return true;
			}
			// With the keyboard on a row it gets the keys; with nothing chosen, the row under the mouse does once the mouse
			// has moved. Before that the keyboard starts at the first row, so nothing changes without being said.
			Focus.KeyTarget target = Focus.keyTarget(focus != null, mouseMovedYet);
			if (target == Focus.KeyTarget.FIRST_ROW && (event.isLeft() || event.isRight() || event.isSelection())) {
				moveFocus(1);
				return true;
			}
			double mx = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
			double my = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
			Row row = focus instanceof Row focused ? focused : target == Focus.KeyTarget.MOUSE_ROW ? rowAt(mx, my) : null;
			if (row != null && row.key(event)) {
				dirty = true;
				if (row == focus) {
					focusVisible = true;
				} else {
					// The key acted on the row under the mouse; it takes no focus (the mouse keeps choosing), but its change is said
					announcement = Narration.hinted(rowNarration(row), row.hint());
				}
				afterKeyboardAction();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean charTyped(CharacterEvent event) {
		if (!typing || !event.isAllowedChatCharacter()) {
			return false;
		}
		if (query.length() < 60) {
			editSearch(query + event.codepointAsString());
		}
		return true;
	}

	/** At big GUI scales the panel reaches the top of the screen, where the top bar would peek out from under it. */
	public boolean coversTopBar() {
		return !preview && py < 30;
	}

	public void setPreview(boolean on) {
		preview = on;
	}

	public void selectTab(int index) {
		if (tab == SEARCH_TAB) {
			clearSearch();
		}
		if (index != tab) {
			tab = index;
			tabAnim = 0;
			if (focus instanceof Row row && row != masterToggle) {
				focus = null; // that row is on the tab we left
			}
		}
	}

	/** Opens the Music tab scrolled down to the Spotify card, where you sign in. */
	public void showSpotify() {
		selectTab(MUSIC_TAB);
		// The tab's height isn't known until it is laid out, so layout() scrolls to the bottom (the Spotify card is the last)
		scrollToEnd = true;
	}

	@Override
	public void onClose() {
		//? if >=26.2 {
		/*minecraft.gui.setScreen(parent);
		*///?} else {
		minecraft.setScreen(parent);
		//?}
	}

	@Override
	public void removed() {
		lastTab = tab == SEARCH_TAB ? searchFrom : tab;
		if (dirty) {
			cfg.save();
			dirty = false;
		}
	}

	// ---- Now playing card on the Music tab -------------------------------------------------------

	private final class NowPlayingRow extends Row {
		private static final int COVER = 40;

		NowPlayingRow() {
			super("", null);
		}

		@Override
		int height() {
			return 54;
		}

		@Override
		int controlWidth(net.minecraft.client.gui.Font font) {
			return 0;
		}

		@Override
		void renderControl(Frame f) {
		}

		private int buttonX(int i) {
			return x + w - 10 - (3 - i) * 20 + 2;
		}

		private int barX() {
			return x + 14 + COVER;
		}

		private int barW() {
			return w - 14 - COVER - 10;
		}

		@Override
		void render(Frame f) {
			GuiGraphics g = f.g();
			MediaTracker tracker = mod.tracker();
			dev.beli.skylyrics.media.Snapshot reading = tracker.snapshot();
			Track track = reading.track();
			int cx = x + 7;
			int cy = y + 7;
			AlbumArt art = mod.art();
			if (track != Track.NONE && art.hasTexture()) {
				g.blit(RenderPipelines.GUI_TEXTURED, AlbumArt.TEXTURE, cx, cy, 0, 0, COVER, COVER,
						AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE, AlbumArt.SIZE);
			} else {
				Ui.rect(g, cx, cy, COVER, COVER, 4, Ui.CONTROL);
				g.drawCenteredString(font, "♪", cx + COVER / 2, cy + COVER / 2 - 4, Ui.MUTED);
			}

			int tx = barX();
			if (track == Track.NONE) {
				g.drawString(font, Ui.visual(Tr.t("Nothing playing")), tx, y + 13, Ui.TEXT, false);
				g.drawString(font, Ui.visual(Ui.clip(font, MediaTracker.idleHint(tracker.connected(), SkyLyricsClient.mac(),
						SpotifyWebSource.KEY.equals(cfg.preferredPlayer)), w - tx + x - 10)),
						tx, y + 25, Ui.MUTED, false);
				return;
			}

			int textW = w - (tx - x) - 3 * 20 - 14;
			g.drawString(font, Ui.visual(Ui.clip(font, track.title(), textW)), tx, y + 8, Ui.TEXT, false);
			String sub = track.artist().isEmpty() ? tracker.sourceName() : track.artist();
			g.drawString(font, Ui.visual(Ui.clip(font, sub, textW)), tx, y + 19, Ui.MUTED, false);

			String[] glyphs = {"◀◀", reading.playing() ? "❚❚" : "▶", "▶▶"};
			for (int i = 0; i < 3; i++) {
				int bx = buttonX(i);
				boolean hover = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), bx, y + 7, 18, 18);
				if (hover || i == 1) {
					Ui.rect(g, bx, y + 7, 18, 18, 9, i == 1 ? (hover ? Ui.lerp(0.25f, Ui.ACCENT, 0xFFFFFFFF) : Ui.ACCENT) : Ui.CONTROL_HOVER);
				}
				int gw = font.width(glyphs[i]);
				g.drawString(font, Ui.visual(glyphs[i]), bx + 9 - gw / 2 + (gw % 2 == 0 ? 0 : 1), y + 12,
						i == 1 || hover ? 0xFFFFFFFF : Ui.MUTED, false);
			}

			long length = track.lengthMs();
			long pos = Math.max(0, tracker.positionMs(reading));
			float t = length > 0 ? Mth.clamp(pos / (float) length, 0, 1) : 0;
			int bx = barX();
			int bw = barW();
			int barY = y + 34;
			boolean hoverBar = length > 0 && f.hoverAllowed() && Ui.inside(f.mx(), f.my(), bx, barY - 3, bw, 9);
			Ui.rect(g, bx, barY, bw, hoverBar ? 4 : 3, 1, Ui.TRACK);
			Ui.rect(g, bx, barY, Math.round(bw * t), hoverBar ? 4 : 3, 1, 0xFF000000 | art.color());
			g.drawString(font, Ui.visual(time(pos)), bx, barY + 7, Ui.DIM, false);
			String total = length > 0 ? time(length) : "--:--";
			g.drawString(font, Ui.visual(total), bx + bw - font.width(total), barY + 7, Ui.DIM, false);
		}

		@Override
		boolean click(double mx, double my) {
			MediaTracker tracker = mod.tracker();
			if (tracker.track() == Track.NONE) {
				return false;
			}
			for (int i = 0; i < 3; i++) {
				if (Ui.inside(mx, my, buttonX(i), y + 7, 18, 18)) {
					switch (i) {
						case 0 -> tracker.previous();
						case 1 -> tracker.playPause();
						default -> tracker.next();
					}
					return true;
				}
			}
			long length = tracker.track().lengthMs();
			if (length > 0 && Ui.inside(mx, my, barX(), y + 31, barW(), 9)) {
				double t = Mth.clamp((mx - barX()) / barW(), 0, 1);
				tracker.seek(Math.round(t * length));
				return true;
			}
			return false;
		}

		@Override
		String narration() {
			MediaTracker tracker = mod.tracker();
			Track track = tracker.track();
			if (track == Track.NONE) {
				return Narration.join(Tr.t("Now playing"), Tr.t("Nothing playing"));
			}
			return Narration.join(Tr.t("Now playing"), track.title(), track.artist(),
					tracker.playing() ? Tr.t("Playing") : Tr.t("Paused"), Tr.t("button"));
		}

		@Override
		boolean key(KeyEvent event) {
			if (event.isSelection()) {
				mod.tracker().playPause();
				return true;
			}
			if (mod.tracker().track() != Track.NONE && (event.isLeft() || event.isRight())) {
				if (event.isLeft()) {
					mod.tracker().previous();
				} else {
					mod.tracker().next();
				}
				return true;
			}
			return false;
		}

		private static String time(long ms) {
			long s = ms / 1000;
			return s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60);
		}
	}
}
