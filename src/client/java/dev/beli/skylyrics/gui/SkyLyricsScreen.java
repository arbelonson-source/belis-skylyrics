package dev.beli.skylyrics.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.Animation;
import dev.beli.skylyrics.config.SkyLyricsConfig.ColorMode;
import dev.beli.skylyrics.config.SkyLyricsConfig.Direction;
import dev.beli.skylyrics.config.SkyLyricsConfig.Layer;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyEffect;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
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
	private static final String LRCLIB = "https://lrclib.net";

	private static final int TAB_COUNT = 5;
	private static final String[] TAB_ICONS = {"☁", "✦", "♫", "◐", "ℹ"};
	private static String tabName(int i) {
		return switch (i) {
			case 0 -> Tr.t("Lyrics");
			case 1 -> Tr.t("Look");
			case 2 -> Tr.t("Music");
			case 3 -> Tr.t("Display");
			default -> Tr.t("About");
		};
	}

	private static String tabSubtitle(int i) {
		return switch (i) {
			case 0 -> Tr.t("Where the lyrics go and how they move");
			case 1 -> Tr.t("Colours, glow and style");
			case 2 -> Tr.t("Your music player and lyrics");
			case 3 -> Tr.t("When to show things");
			default -> Tr.t("About this mod");
		};
	}
	private static final int MUSIC_TAB = 2;
	private static final int[] SWATCHES = {
			0xB57BFF, 0xFF6EC7, 0xFF5A5A, 0xFFA24C, 0xFFE066, 0x6BFF8E, 0x4FE3FF, 0x5B8CFF, 0xFFFFFF,
	};
	/** Part of the player name to look for, next to the name shown; the first one is Auto. */
	private static final String[] PLAYER_KEYS = SkyLyricsClient.windows()
			? new String[] {"", "spotify", "chrom", "edge", "firefox", "media player"}
			: new String[] {"", "spotify", "firefox", "chrom", "mpv", "vlc"};
	private static final List<String> PLAYER_NAMES = SkyLyricsClient.windows()
			? List.of("Spotify", "Chrome", "Edge", "Firefox", "Media Player")
			: List.of("Spotify", "Firefox", "Chrome", "mpv", "VLC");

	private static final int SIDEBAR_W = 96;
	private static final int TAB_H = 20;
	private static final int HEADER_H = 40;
	private static final int FOOTER_H = 32;
	private static final long SOLO_DELAY_NS = 180_000_000L;
	private static final long RESET_CONFIRM_NS = 3_000_000_000L;

	private static int lastTab;

	private final @Nullable Screen parent;
	private final SkyLyricsConfig cfg = SkyLyricsConfig.get();
	private final SkyLyricsClient mod = SkyLyricsClient.get();
	private final List<List<Card>> tabs = new ArrayList<>();
	private final Toggle masterToggle;
	private final float[] scroll = new float[TAB_COUNT];
	private final float[] scrollTarget = new float[TAB_COUNT];

	private int tab = lastTab;
	private int px, py, pw, ph;
	private int viewX, viewY, viewW, viewH;
	private int contentH;
	private float indicatorY = Float.NaN;
	private float openAnim;
	private float tabAnim = 1;
	private long lastFrame;
	private boolean dirty;
	private boolean preview;
	private long resetArmedUntil;
	private @Nullable Row active;
	private long activeSince;
	private boolean draggingScrollbar;
	private double scrollbarGrab;
	private int previewX, previewW;
	private int pillX, pillW;

	public SkyLyricsScreen(@Nullable Screen parent) {
		super(Component.literal("Beli's SkyLyrics"));
		this.parent = parent;
		masterToggle = new Toggle(Tr.t("Lyrics"), null, () -> cfg.enabled, v -> cfg.enabled = v);
		tabs.add(lyricsTab());
		tabs.add(lookTab());
		tabs.add(musicTab());
		tabs.add(displayTab());
		tabs.add(aboutTab());
	}

	// ---- Tabs -------------------------------------------------------------------------------------

	private List<Card> lyricsTab() {
		Row placement = new Segmented(Tr.t("Placement"), Tr.t("Far up in the sky, or close by"),
				new String[] {Tr.t("Sky"), Tr.t("Floating")}, () -> cfg.placement.ordinal(),
				i -> cfg.placement = Placement.values()[i]);
		Row direction = new Segmented(Tr.t("Direction"), Tr.t("Follow keeps them where you look"),
				new String[] {Tr.t("Follow"), Tr.t("N"), Tr.t("E"), Tr.t("S"), Tr.t("W")},
				() -> cfg.direction == Direction.FOLLOW ? 0 : cfg.direction.ordinal() + 1,
				i -> cfg.direction = i == 0 ? Direction.FOLLOW : Direction.values()[i - 1])
				.when(() -> cfg.placement == Placement.SKY);
		Row height = new Slider(Tr.t("Height"), Tr.t("How high above the horizon"), -10, 80, 1,
				() -> cfg.height, v -> cfg.height = (float) v, v -> Math.round(v) + "°");
		Row distance = new Slider(Tr.t("Distance"), Tr.t("How far away they float"), 8, 160, 1,
				() -> cfg.distance, v -> cfg.distance = (float) v, v -> Tr.t("%s blocks", Math.round(v)));
		Row size = new Slider(Tr.t("Size"), null, 0.3, 3, 0.05,
				() -> cfg.size, v -> cfg.size = (float) v, v -> String.format(Locale.ROOT, "%.2f×", v));

		Row previous = new Toggle(Tr.t("Previous line"), Tr.t("The line that just ended, faded"),
				() -> cfg.showPrevious, v -> cfg.showPrevious = v);
		Row next = new Toggle(Tr.t("Next line"), Tr.t("What's coming up, dimmed"),
				() -> cfg.showNext, v -> cfg.showNext = v);
		Row karaoke = new Toggle(Tr.t("Karaoke fill"), Tr.t("Words light up as they are sung"),
				() -> cfg.karaoke, v -> cfg.karaoke = v);
		Row animation = new Segmented(Tr.t("Animation"), Tr.t("How lines come in"),
				new String[] {Tr.t("Words"), Tr.t("Fade"), Tr.t("Rise"), Tr.t("Pop"), Tr.t("Type")}, () -> cfg.animation.ordinal(),
				i -> cfg.animation = Animation.values()[i]);
		Row pulse = new Toggle(Tr.t("Pulse"), Tr.t("The glow flares up as each new line starts"),
				() -> cfg.pulse, v -> cfg.pulse = v);
		Row capitals = new Toggle(Tr.t("CAPITALS"), Tr.t("Write every lyric in capital letters"),
				() -> cfg.uppercase, v -> cfg.uppercase = v);

		return List.of(
				new Card(Tr.t("Position"), placement, direction, height, distance, size),
				new Card(Tr.t("Lines"), previous, next, karaoke, animation, pulse, capitals));
	}

	private List<Card> lookTab() {
		Row mode = new Segmented(Tr.t("Colour"), Tr.t("From the cover, or your own"),
				new String[] {Tr.t("Album"), Tr.t("Pick"), Tr.t("Rainbow")}, () -> cfg.colorMode.ordinal(),
				i -> cfg.colorMode = ColorMode.values()[i]);
		Row swatches = new Swatches(Tr.t("Pick a colour"), null, SWATCHES,
				() -> cfg.colorMode == ColorMode.FIXED ? cfg.color : -1,
				c -> {
					cfg.color = c;
					cfg.colorMode = ColorMode.FIXED;
				});
		Row albumColor = new Info(Tr.t("Album colour"), null, () -> {
			Track track = mod.tracker().track();
			if (track == Track.NONE) {
				return Tr.t("no song playing");
			}
			return mod.art().hasTexture() ? String.format(Locale.ROOT, "#%06X", mod.art().color() & 0xFFFFFF) : Tr.t("no cover art");
		}).when(() -> cfg.colorMode == ColorMode.ALBUM);

		Row font = new Segmented(Tr.t("Font"), Tr.t("Letter style"),
				new String[] {Tr.t("Smooth"), Tr.t("Handwritten"), Tr.t("Minecraft")},
				() -> !cfg.smoothFont ? 2 : cfg.scriptFont ? 1 : 0,
				i -> {
					cfg.smoothFont = i != 2;
					cfg.scriptFont = i == 1;
				});
		Row glow = new Slider(Tr.t("Glow"), Tr.t("Soft light around the letters"), 0, 1, 0.05,
				() -> cfg.glow, v -> cfg.glow = (float) v, SkyLyricsScreen::percent);
		Row shadow = new Toggle(Tr.t("Shadow"), Tr.t("A dark edge so text reads on bright skies"),
				() -> cfg.shadow, v -> cfg.shadow = v);
		Row opacity = new Slider(Tr.t("Opacity"), null, 0.2, 1, 0.05,
				() -> cfg.opacity, v -> cfg.opacity = (float) v, SkyLyricsScreen::percent);
		Row seeThrough = new Toggle(Tr.t("See through blocks"), Tr.t("Stay visible behind hills and walls"),
				() -> cfg.seeThroughBlocks, v -> cfg.seeThroughBlocks = v);
		Row layer = new Segmented(Tr.t("Draw on top"), Tr.t("Stays bright with shaders"),
				new String[] {Tr.t("Auto"), Tr.t("Always"), Tr.t("Never")}, () -> switch (cfg.layer) {
					case AUTO -> 0;
					case SCREEN -> 1;
					case WORLD -> 2;
				}, i -> cfg.layer = i == 0 ? Layer.AUTO : i == 1 ? Layer.SCREEN : Layer.WORLD);

		Row night = new Segmented(Tr.t("Night sky"), Tr.t("Shows at night with music"),
				new String[] {Tr.t("Off"), Tr.t("Aurora"), Tr.t("Nebula")}, () -> cfg.sky.ordinal(),
				i -> cfg.sky = SkyEffect.values()[i]);
		Row skyStrength = new Slider(Tr.t("Strength"), null, 0.1, 1, 0.05,
				() -> cfg.skyStrength, v -> cfg.skyStrength = (float) v, SkyLyricsScreen::percent)
				.when(() -> cfg.sky != SkyEffect.OFF);

		return List.of(
				new Card(Tr.t("Colour"), mode, swatches, albumColor),
				new Card(Tr.t("Style"), font, glow, shadow, opacity, seeThrough, layer),
				new Card(Tr.t("Sky"), night, skyStrength));
	}

	private List<Card> musicTab() {
		MediaTracker tracker = mod.tracker();
		LyricsService service = mod.lyrics();
		Row status = new Buttons(Tr.t("Lyrics"), "",
				new Button(() -> Tr.t("Retry"), service::retry,
						() -> service.state() == LyricsService.State.NOT_FOUND || service.state() == LyricsService.State.ERROR,
						false))
				.describe(this::lyricsStatus);

		Row player = new Cycle(Tr.t("Player"), Tr.t("Which app to follow"), Stream.concat(Stream.of(Tr.t("Auto")), PLAYER_NAMES.stream()).toList(),
				() -> {
					for (int i = 0; i < PLAYER_KEYS.length; i++) {
						if (PLAYER_KEYS[i].equalsIgnoreCase(cfg.preferredPlayer)) {
							return i;
						}
					}
					return 0;
				},
				i -> {
					cfg.preferredPlayer = PLAYER_KEYS[i];
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
			return tracker.track() == Track.NONE ? Tr.t("No music playing") : Tr.t("Following %s", tracker.sourceName());
		});
		Row timing = new Slider(Tr.t("Timing"), Tr.t("Late? Move right. Early? Left"), -3000, 3000, 50,
				() -> cfg.syncOffsetMs, v -> cfg.syncOffsetMs = (int) Math.round(v), SkyLyricsScreen::seconds);
		Row songTiming = new Slider(Tr.t("This song"), Tr.t("Remembered for this song only"), -3000, 3000, 50,
				() -> cfg.songOffset(tracker.track()), v -> cfg.setSongOffset(tracker.track(), (int) Math.round(v)),
				SkyLyricsScreen::seconds).when(() -> tracker.track() != Track.NONE);
		Row demo = new Toggle(Tr.t("Demo song"), Tr.t("A built-in song for trying things out"),
				() -> cfg.demoMode, v -> {
					cfg.demoMode = v;
					tracker.refresh();
				});
		Row plain = new Toggle(Tr.t("Untimed lyrics"), Tr.t("If a song has no timing, spread the lines out"),
				() -> cfg.showPlainLyrics, v -> cfg.showPlainLyrics = v);

		return List.of(
				new Card(Tr.t("Now playing"), new NowPlayingRow(), status),
				new Card(Tr.t("Player"), player, connection, timing, songTiming, demo, plain));
	}

	public static String seconds(double ms) {
		return Math.abs(ms) < 1 ? "0 s" : String.format(Locale.ROOT, "%+.2f s", ms / 1000);
	}

	private List<Card> displayTab() {
		Row f1 = new Toggle(Tr.t("Hide with F1"), Tr.t("When you hide the HUD"), () -> cfg.hideWithF1, v -> cfg.hideWithF1 = v);
		Row menus = new Toggle(Tr.t("Hide in menus"), Tr.t("When a menu or chat is open"), () -> cfg.hideInMenus, v -> cfg.hideInMenus = v);
		Row paused = new Toggle(Tr.t("Hide when paused"), Tr.t("When the music is paused"), () -> cfg.hideWhenPaused, v -> cfg.hideWhenPaused = v);

		Row card = new Toggle(Tr.t("Top bar"), Tr.t("Song and lyrics at the top of the screen"),
				() -> cfg.nowPlayingHud, v -> cfg.nowPlayingHud = v);
		Row barLyrics = new Toggle(Tr.t("Lyrics in the bar"), Tr.t("The line being sung, word by word"),
				() -> cfg.barLyrics, v -> cfg.barLyrics = v)
				.when(() -> cfg.nowPlayingHud);
		Row onlyOnChange = new Toggle(Tr.t("Song name only on change"), Tr.t("Shows for a few seconds, then hides"),
				() -> cfg.hudOnlyOnChange, v -> cfg.hudOnlyOnChange = v)
				.when(() -> cfg.nowPlayingHud);

		Row openKey = new Info(Tr.t("Open settings"), null, () -> keyName(mod.openSettingsKey()));
		Row controls = new Buttons(Tr.t("More keys"), Tr.t("Toggle lyrics, play/pause, skip songs"),
				new Button(Tr.t("Controls"), () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options))));

		return List.of(
				new Card(Tr.t("Hide lyrics"), f1, menus, paused),
				new Card(Tr.t("Top bar"), card, barLyrics, onlyOnChange),
				new Card(Tr.t("Keys"), openKey, controls));
	}

	private List<Card> aboutTab() {
		String version = FabricLoader.getInstance().getModContainer(SkyLyricsClient.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
		Row intro = new Paragraph(() -> Tr.t("Beli's SkyLyrics shows the words of whatever song you are playing, floating in the Minecraft sky. It is free and open source (AGPL licence)."));
		Row versionRow = new Info(Tr.t("Version"), null, () -> version);
		Row how = new Paragraph(() -> SkyLyricsClient.windows()
				? Tr.t("On Windows the song comes from the media controls that pop up when you press a media key, so Spotify, browsers and most players work. Lyrics come from LRCLIB, a free community lyrics library, and are saved on your computer for next time.")
				: Tr.t("The song comes from your music app through MPRIS, the same Linux feature your media keys use, so Spotify, browsers and most players work. Lyrics come from LRCLIB, a free community lyrics library, and are saved on your computer for next time."));
		Row compat = new Paragraph(() -> Tr.t("Works with Sodium, Iris and shader packs."));
		Row links = new Buttons(Tr.t("Links"), null,
				new Button("GitHub", () -> ConfirmLinkScreen.confirmLinkNow(this, GITHUB)),
				new Button("LRCLIB", () -> ConfirmLinkScreen.confirmLinkNow(this, LRCLIB)));
		return List.of(
				new Card("Beli's SkyLyrics", intro, versionRow),
				new Card(Tr.t("How it works"), how, compat, links));
	}

	private String lyricsStatus() {
		if (mod.tracker().track() == Track.NONE) {
			return Tr.t("Waiting for a song");
		}
		Lyrics lyrics = mod.lyrics().lyrics();
		return switch (mod.lyrics().state()) {
			case IDLE -> Tr.t("Waiting for a song");
			case LOADING -> Tr.t("Looking for lyrics…");
			case READY -> lyrics.instrumental() ? Tr.t("Instrumental, no words")
					: lyrics.synced() ? Tr.t("Found, with timing") : Tr.t("Found, without timing");
			case NOT_FOUND -> Tr.t("No lyrics found for this song");
			case ERROR -> Tr.t("Couldn't reach LRCLIB. Offline?");
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

	private void layout() {
		pw = Math.min(440, width - 16);
		ph = Math.min(272, height - 16);
		px = (width - pw) / 2;
		py = (height - ph) / 2 + Math.round((1 - openAnim) * 8);
		viewX = px + SIDEBAR_W + 12;
		viewW = pw - SIDEBAR_W - 24;
		viewY = py + HEADER_H;
		viewH = ph - HEADER_H - FOOTER_H;

		float target = Mth.clamp(scrollTarget[tab], 0, maxScroll());
		scrollTarget[tab] = target;
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
				y += row.height() + 1;
			}
			y += 9;
		}
		contentH = y - start;
		masterToggle.x = px + 4;
		masterToggle.w = SIDEBAR_W - 8;
		masterToggle.y = py + ph - 30;
	}

	private float maxScroll() {
		return Math.max(0, contentH - viewH + 4);
	}

	private static List<Row> shownRows(Card card) {
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

	// ---- Drawing ----------------------------------------------------------------------------------

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// In a world, leave it fully visible so changes can be seen on the real lyrics.
		if (minecraft.level == null) {
			super.renderBackground(g, mouseX, mouseY, partialTick);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		openAnim = Ui.approach(openAnim, 1, dt, 12);
		tabAnim = Ui.approach(tabAnim, 1, dt, 14);
		scroll[tab] = Ui.approach(scroll[tab], scrollTarget[tab], dt, 18);
		layout();

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
	}

	private void renderSidebar(GuiGraphics g, Frame f) {
		Ui.rect(g, px, py, SIDEBAR_W, ph, 8, Ui.SIDEBAR);
		g.fill(px + SIDEBAR_W - 8, py, px + SIDEBAR_W, py + ph, Ui.SIDEBAR);
		g.fill(px + SIDEBAR_W, py, px + SIDEBAR_W + 1, py + ph, Ui.DIVIDER);

		Ui.rect(g, px + 10, py + 11, 16, 16, 4, Ui.ACCENT);
		g.drawCenteredString(font, "♪", px + 18, py + 15, 0xFFFFFFFF);
		g.drawString(font, "SkyLyrics", px + 31, py + 10, Ui.TEXT, false);
		g.drawString(font, "by Beli", px + 31, py + 20, Ui.DIM, false);

		int tabsY = py + 40;
		float target = tabsY + tab * (TAB_H + 2);
		indicatorY = Float.isNaN(indicatorY) ? target : Ui.approach(indicatorY, target, f.dt(), 16);
		int iy = Math.round(indicatorY);
		Ui.rect(g, px + 6, iy, SIDEBAR_W - 12, TAB_H, 5, Ui.ACCENT_SOFT);
		Ui.rect(g, px + 6, iy + 5, 2, TAB_H - 10, 1, Ui.ACCENT);
		for (int i = 0; i < TAB_COUNT; i++) {
			int ty = tabsY + i * (TAB_H + 2);
			boolean hover = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), px + 6, ty, SIDEBAR_W - 12, TAB_H);
			if (hover && i != tab) {
				Ui.rect(g, px + 6, ty, SIDEBAR_W - 12, TAB_H, 5, 0x14FFFFFF);
			}
			int color = i == tab ? 0xFFFFFFFF : hover ? Ui.TEXT : Ui.MUTED;
			g.drawString(font, TAB_ICONS[i], px + 14, ty + 6, i == tab ? Ui.ACCENT : color, false);
			g.drawString(font, tabName(i), px + 28, ty + 6, color, false);
		}

		g.fill(px + 10, py + ph - 36, px + SIDEBAR_W - 10, py + ph - 35, Ui.DIVIDER);
		masterToggle.render(f);
	}

	private void renderHeader(GuiGraphics g, Frame f) {
		g.drawString(font, tabName(tab), viewX, py + 12, Ui.TEXT, false);
		g.drawString(font, tabSubtitle(tab), viewX, py + 23, Ui.MUTED, false);

		int bw = font.width(Tr.t("Preview")) + 16;
		int bx = px + pw - 12 - bw;
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
		g.drawString(font, Ui.clip(font, text, pillW - 22), pillX + 15, py + 16, hover ? 0xFFFFFFFF : Ui.TEXT, false);
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
			g.drawString(font, card.title().toUpperCase(Locale.ROOT), first.x + 2, top - 10, Ui.DIM, false);
			Ui.rect(g, first.x, top, first.w, bottom - top, 6, Ui.CARD);
			for (Row row : rows) {
				if (row != first) {
					g.fill(row.x + 10, row.y - 1, row.x + row.w - 10, row.y, Ui.DIVIDER);
				}
				if (!(row instanceof Paragraph) && (row.hovered(inner) || row == active)) {
					Ui.rect(g, row.x + 2, row.y + 1, row.w - 4, row.height() - 2, 4, Ui.CARD_HOVER);
				}
				row.render(inner);
				if (row.descClipped && row.hovered(inner) && row.desc != null) {
					g.setTooltipForNextFrame(font, Component.literal(row.desc.get()), f.mx(), f.my());
				}
			}
		}
		g.disableScissor();

		float max = maxScroll();
		if (max > 0) {
			int trackX = px + pw - 7;
			int barH = Math.max(16, Math.round(viewH * (viewH / (float) (viewH + max))));
			int barY = viewY + Math.round((viewH - barH) * (scroll[tab] / max));
			boolean hover = draggingScrollbar || f.hoverAllowed() && Ui.inside(f.mx(), f.my(), trackX - 3, viewY, 8, viewH);
			Ui.rect(g, trackX, barY, 2, barH, 1, hover ? Ui.MUTED : Ui.TRACK);
		}
	}

	private void renderFooter(GuiGraphics g, Frame f) {
		int fy = py + ph - FOOTER_H + 8;
		g.fill(viewX, fy - 8, px + pw - 12, fy - 7, Ui.DIVIDER);
		boolean armed = System.nanoTime() < resetArmedUntil;
		String reset = armed ? Tr.t("Click again to reset") : Tr.t("Reset all");
		int rw = font.width(reset) + 16;
		boolean hoverReset = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), viewX, fy, rw, 16);
		Controls.drawButton(f, viewX, fy, rw, 16, reset, hoverReset, false, true);
		if (armed) {
			Ui.outline(g, viewX, fy, rw, 16, 4, 0xFFFF6B6B);
		}

		int dw = 56;
		int dx = px + pw - 12 - dw;
		boolean hoverDone = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), dx, fy, dw, 16);
		Controls.drawButton(f, dx, fy, dw, 16, Tr.t("Done"), hoverDone, true, true);

		String hint = Tr.t("%s or Esc to close", keyName(mod.openSettingsKey()));
		int hintX = (viewX + rw + dx) / 2 - font.width(hint) / 2;
		if (hintX > viewX + rw + 6 && hintX + font.width(hint) < dx - 6) {
			g.drawString(font, hint, hintX, fy + 4, Ui.DIM, false);
		}
	}

	private void renderPreviewHint(GuiGraphics g) {
		boolean nothing = mod.tracker().track() == Track.NONE;
		String text = nothing ? Tr.t("Nothing is playing. Turn on the demo song in Music to try it.")
				: Tr.t("Previewing. Click or press any key to go back.");
		int w = font.width(text) + 24;
		int x = (width - w) / 2;
		int y = height - 44;
		Ui.rect(g, x, y, w, 18, 9, 0xE0141019);
		Ui.rect(g, x + 8, y + 7, 4, 4, 2, Ui.ACCENT);
		g.drawString(font, text, x + 17, y + 5, Ui.TEXT, false);
	}

	// ---- Input ------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (preview) {
			preview = false;
			return true;
		}
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}

		int tabsY = py + 40;
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
		int rw = font.width(armed ? Tr.t("Click again to reset") : Tr.t("Reset all")) + 16;
		if (Ui.inside(mx, my, viewX, fy, rw, 16)) {
			if (armed) {
				cfg.resetToDefaults();
				resetArmedUntil = 0;
				dirty = true;
				mod.tracker().refresh();
			} else {
				resetArmedUntil = System.nanoTime() + RESET_CONFIRM_NS;
			}
			return true;
		}
		if (Ui.inside(mx, my, px + pw - 12 - 56, fy, 56, 16)) {
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

	@Override
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

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
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

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (preview) {
			preview = false;
			return true;
		}
		KeyMapping open = mod.openSettingsKey();
		if (open != null && open.matches(event)) {
			onClose();
			return true;
		}
		double mx = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
		double my = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
		Row row = active == null ? rowAt(mx, my) : null;
		if (row != null && row.key(event)) {
			dirty = true;
			return true;
		}
		if (event.isUp() || event.isDown()) {
			selectTab(Math.floorMod(tab + (event.isDown() ? 1 : -1), TAB_COUNT));
			return true;
		}
		return super.keyPressed(event);
	}

	public void setPreview(boolean on) {
		preview = on;
	}

	public void selectTab(int index) {
		if (index != tab) {
			tab = index;
			tabAnim = 0;
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		lastTab = tab;
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
			Track track = tracker.track();
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
				g.drawString(font, Tr.t("Nothing playing"), tx, y + 13, Ui.TEXT, false);
				g.drawString(font, Ui.clip(font, Tr.t("Play a song in Spotify, a browser or any player"), w - tx + x - 10),
						tx, y + 25, Ui.MUTED, false);
				return;
			}

			int textW = w - (tx - x) - 3 * 20 - 14;
			g.drawString(font, Ui.clip(font, track.title(), textW), tx, y + 8, Ui.TEXT, false);
			String sub = track.artist().isEmpty() ? tracker.sourceName() : track.artist();
			g.drawString(font, Ui.clip(font, sub, textW), tx, y + 19, Ui.MUTED, false);

			String[] glyphs = {"◀◀", tracker.playing() ? "❚❚" : "▶", "▶▶"};
			for (int i = 0; i < 3; i++) {
				int bx = buttonX(i);
				boolean hover = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), bx, y + 7, 18, 18);
				if (hover || i == 1) {
					Ui.rect(g, bx, y + 7, 18, 18, 9, i == 1 ? (hover ? Ui.lerp(0.25f, Ui.ACCENT, 0xFFFFFFFF) : Ui.ACCENT) : Ui.CONTROL_HOVER);
				}
				int gw = font.width(glyphs[i]);
				g.drawString(font, glyphs[i], bx + 9 - gw / 2 + (gw % 2 == 0 ? 0 : 1), y + 12,
						i == 1 || hover ? 0xFFFFFFFF : Ui.MUTED, false);
			}

			long length = track.lengthMs();
			long pos = Math.max(0, tracker.positionMs());
			float t = length > 0 ? Mth.clamp(pos / (float) length, 0, 1) : 0;
			int bx = barX();
			int bw = barW();
			int barY = y + 34;
			boolean hoverBar = length > 0 && f.hoverAllowed() && Ui.inside(f.mx(), f.my(), bx, barY - 3, bw, 9);
			Ui.rect(g, bx, barY, bw, hoverBar ? 4 : 3, 1, Ui.TRACK);
			Ui.rect(g, bx, barY, Math.round(bw * t), hoverBar ? 4 : 3, 1, 0xFF000000 | art.color());
			g.drawString(font, time(pos), bx, barY + 7, Ui.DIM, false);
			String total = length > 0 ? time(length) : "--:--";
			g.drawString(font, total, bx + bw - font.width(total), barY + 7, Ui.DIM, false);
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
		boolean key(KeyEvent event) {
			if (event.isSelection()) {
				mod.tracker().playPause();
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
