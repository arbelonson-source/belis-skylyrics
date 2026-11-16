package dev.beli.skylyrics.gui;

import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.ColorMode;
import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import dev.beli.skylyrics.config.Themes;
import dev.beli.skylyrics.config.Themes.Theme;
import dev.beli.skylyrics.render.LyricsFont;

/**
 * A few pages shown the first time the game starts with the mod: what it does, a look, where the
 * lyrics go, where the songs come from and the keys. Built from Minecraft's own buttons, so it
 * works the same on every version.
 */
public final class IntroScreen extends Screen {
	static final int PAGES = 5;
	private static final int PANEL_W = 300;
	private static final int PANEL_H = 190;
	private static final int MARGIN = 4;

	private final @Nullable Screen parent;
	private final SkyLyricsConfig cfg = SkyLyricsConfig.get();
	private int page;
	/** The look being shown: an index into {@link Themes#ALL}, or -1 for Custom (the player's own settings, no theme matches). */
	private int theme = startLook(cfg);
	private int px, py;
	/** The panel, fitted to the window (300 x 190 when there is room). */
	private int pw = PANEL_W, ph = PANEL_H;
	/** The buttons of the page by name, so the keyboard can be put back on the one it was on after the page is rebuilt. */
	private final java.util.Map<String, Button> buttons = new java.util.HashMap<>();
	private @Nullable String keepFocus;

	public IntroScreen(@Nullable Screen parent) {
		super(Component.literal("Beli's SkyLyrics"));
		this.parent = parent;
	}

	/** The panel's size on a window of this many scaled pixels: the usual size, or the window minus a small margin. */
	static int fit(int window, int wanted) {
		return Math.max(1, Math.min(wanted, window - 2 * MARGIN));
	}

	/** A vertical offset from the panel's top, squeezed when the panel is shorter than usual. */
	static int vy(int offset, int panelH) {
		return panelH >= PANEL_H ? offset : Math.round(offset * (float) panelH / PANEL_H);
	}

	/** A button width, narrowed when the panel is narrower than usual. */
	static int bw(int wanted, int panelW) {
		return panelW >= PANEL_W ? wanted : Math.max(20, Math.round(wanted * (float) panelW / PANEL_W));
	}

	/** The look the page starts on: the theme the settings match, or -1 (Custom) when none does. */
	static int startLook(SkyLyricsConfig cfg) {
		Theme active = Themes.active(cfg);
		return active == null ? -1 : Themes.ALL.indexOf(active);
	}

	/** The look one step on from {@code at}; from Custom (-1) the first going on and the last going back. */
	static int stepLook(int at, int step) {
		return LibraryKeys.nextButton(at, Themes.ALL.size(), step);
	}

	/** Whether to open the tour: only over the very title screen that asked, and not over a tour already open. */
	public static boolean shouldOpen(@Nullable Object current, Object titleScreen) {
		return current == titleScreen && !(current instanceof IntroScreen);
	}

	/** Where the last line of text may be drawn: above the page dots and the buttons. */
	private int textLimit() {
		return py + ph - 46;
	}

	@Override
	protected void init() {
		pw = fit(width, PANEL_W);
		ph = fit(height, PANEL_H);
		px = (width - pw) / 2;
		py = Math.max(MARGIN, (height - ph) / 2);
		int bottom = py + ph - 28;
		int mid = py + vy(100, ph);
		buttons.clear();
		switch (page) {
			case 0 -> {
				add("skip", Tr.t("Skip"), null, px + 12, bottom, bw(80, pw), this::finish);
				add("next", Tr.t("Show me around"), null, px + pw - 12 - bw(120, pw), bottom, bw(120, pw), () -> go(1));
				return;
			}
			case 1 -> {
				add("look-", "<", Tr.t("Previous look"), px + 40, mid, 20, () -> pickTheme(-1));
				add("look+", ">", Tr.t("Next look"), px + pw - 60, mid, 20, () -> pickTheme(1));
			}
			case 2 -> {
				Layout[] layouts = {Layout.WORLD, Layout.ARC, Layout.LINES};
				int w3 = bw(84, pw);
				int x = px + (pw - 3 * w3 - 16) / 2;
				for (Layout layout : layouts) {
					String name = layoutName(layout);
					add("layout" + layout.ordinal(), cfg.layout == layout ? "> " + name + " <" : name,
							Narration.join(name, cfg.layout == layout ? Tr.t("selected") : ""), x, mid - 24, w3, () -> {
						cfg.layout = layout;
						rebuild();
					});
					x += w3 + 8;
				}
			}
			case 3 -> add("demo", cfg.demoMode ? Tr.t("Demo song: on") : Tr.t("Demo song: off"), null, px + 70 * pw / PANEL_W, Math.min(mid + 28, bottom - 24), pw - 140 * pw / PANEL_W, () -> {
				cfg.demoMode = !cfg.demoMode;
				SkyLyricsClient mod = SkyLyricsClient.get();
				if (mod != null) {
					mod.tracker().refresh();
				}
				rebuild();
			});
			case 4 -> {
				add("back", Tr.t("Back"), null, px + 12, bottom, bw(60, pw), () -> go(3));
				add("settings", Tr.t("All settings"), null, px + pw / 2 - bw(100, pw) / 2, bottom, bw(100, pw), () -> {
					finish();
					setScreen(new SkyLyricsScreen(parent));
				});
				add("done", Tr.t("Done"), null, px + pw - 12 - bw(60, pw), bottom, bw(60, pw), this::finish);
				return;
			}
			default -> {
			}
		}
		add("back", Tr.t("Back"), null, px + 12, bottom, bw(60, pw), () -> go(page - 1));
		add("next", Tr.t("Next"), null, px + pw - 12 - bw(60, pw), bottom, bw(60, pw), () -> go(page + 1));
	}

	/**
	 * A button of the page. {@code tag} names its place, so the keyboard can stay on it when the page is rebuilt;
	 * {@code spoken} is what the Narrator calls it when the label is only a sign.
	 */
	private void add(String tag, String label, @Nullable String spoken, int x, int y, int w, Runnable action) {
		Button.Builder builder = Button.builder(Component.literal(label), b -> {
			if (getFocused() == b) {
				keepFocus = tag;
			}
			action.run();
			afterKeyboardAction(); // say what changed
		}).bounds(x, y, w, 20);
		if (spoken != null) {
			builder.createNarration(say -> Component.translatable("gui.narrate.button", Component.literal(spoken)));
		}
		Button button = builder.build();
		buttons.put(tag, button);
		addRenderableWidget(button);
	}

	/** Rebuilds the page's buttons and puts the keyboard back on the one that was pressed (a rebuild would drop it to the first). */
	private void rebuild() {
		rebuildWidgets();
		Button button = keepFocus == null ? null : buttons.get(keepFocus);
		keepFocus = null;
		if (button != null) {
			setFocused(button);
		}
	}

	private void go(int to) {
		page = Mth.clamp(to, 0, PAGES - 1);
		rebuild();
	}

	private void pickTheme(int step) {
		theme = stepLook(theme, step);
		Themes.apply(Themes.ALL.get(theme), cfg);
	}

	static String layoutName(Layout layout) {
		return switch (layout) {
			case WORLD -> Tr.t("World");
			case ARC -> Tr.t("Arc");
			case LINES -> Tr.t("Lines");
		};
	}

	/** The words of a page, for drawing and for the Narrator. {@code keys} are the settings and library keys' names (null when unbound). */
	static IntroPage content(int page, boolean mac, @Nullable Theme look, Layout layout, @Nullable String settingsKey, @Nullable String libraryKey) {
		return switch (page) {
			case 0 -> new IntroPage("Beli's SkyLyrics", List.of(
					Tr.t("The lyrics of the song you're playing, floating in the sky and sung along in time."),
					Tr.t("Let's set it up. It takes a few seconds.")), List.of(), "");
			case 1 -> new IntroPage(Tr.t("Pick a look"), List.of(Tr.t("You can change every part of it later.")), List.of(), look == null ? Tr.t("Custom") : look.name().get());
			case 2 -> new IntroPage(Tr.t("Where should the lyrics go?"), List.of(), List.of(), Narration.join(layoutName(layout), layoutText(layout)));
			case 3 -> new IntroPage(Tr.t("Where the songs come from"), List.of(mac
					? Tr.t("Play a song in the Music or Spotify app (browsers can't be read on a Mac) and its lyrics show up. If macOS asks if your launcher may control them, click OK.")
					: Tr.t("Play a song in Spotify or your music player and its lyrics show up by themselves."),
					Tr.t("Optional: signing in to Spotify (needs Premium and a free developer app) can help with timing."),
					Tr.t("No music right now? Try the demo song.")), List.of(), "");
			default -> new IntroPage(Tr.t("You're all set"), List.of(
					Tr.t("In a world, press these keys. You can change them in Minecraft's Controls settings.")),
					settingsKey == null && libraryKey == null ? List.of() : List.of(
							Tr.t("Settings") + ": " + (settingsKey == null ? "-" : settingsKey),
							Tr.t("Song library") + ": " + (libraryKey == null ? "-" : libraryKey)), "");
		};
	}

	private IntroPage content() {
		SkyLyricsClient mod = SkyLyricsClient.get();
		return content(page, SkyLyricsClient.mac(), theme < 0 ? null : Themes.ALL.get(theme), cfg.layout,
				mod == null || mod.openSettingsKey() == null ? null : mod.openSettingsKey().getTranslatedKeyMessage().getString(),
				mod == null || mod.libraryKey() == null ? null : mod.libraryKey().getTranslatedKeyMessage().getString());
	}

	/** What the Narrator says for the screen: its name, then the page's words once when the page shows. */
	@Override
	public Component getNarrationMessage() {
		return Component.literal(Narration.join(getTitle().getString(), content().spoken()));
	}

	@Override
	protected void updateNarrationState(NarrationElementOutput output) {
		super.updateNarrationState(output);
		// The choice made on the page (the look, the layout) is its own line, so a change says only that
		String chosen = content().chosen();
		if (!chosen.isEmpty()) {
			output.add(NarratedElementType.HINT, Component.literal(chosen));
		}
	}

	private static String layoutText(Layout layout) {
		return switch (layout) {
			case WORLD -> Tr.t("Words float around you in the world and drift up into the sky.");
			case ARC -> Tr.t("The line being sung, on a gentle curve across the sky.");
			case LINES -> Tr.t("The previous, current and next line, straight and centred.");
		};
	}

	/** Remembers that the tour was seen, so it never opens again. */
	private void finish() {
		cfg.introSeen = true;
		cfg.save();
		setScreen(parent);
	}

	@Override
	public void onClose() {
		finish();
	}

	private void setScreen(@Nullable Screen screen) {
		//? if >=26.2 {
		/*minecraft.gui.setScreen(screen);
		*///?} else {
		minecraft.setScreen(screen);
		//?}
	}

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
	//?}
		Ui.rect(g, px, py, pw, ph, 8, Ui.PANEL);
		Ui.outline(g, px, py, pw, ph, 8, Ui.DIVIDER);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
	//?}
		int cx = px + pw / 2;
		IntroPage text = content();
		switch (page) {
			case 0 -> {
				heading(g, text.heading(), py + vy(22, ph));
				lines(g, py + vy(50, ph), text.paragraphs().toArray(new String[0]));
			}
			case 1 -> {
				heading(g, text.heading(), py + vy(22, ph));
				lines(g, py + vy(42, ph), text.paragraphs().toArray(new String[0]));
				if (theme < 0) {
					String name = Ui.clip(font, Tr.t("Custom"), pw - 140 * pw / PANEL_W);
					g.drawString(font, Ui.visual(name), cx - font.width(name) / 2, py + vy(106, ph), Ui.MUTED, false);
					break;
				}
				Theme t = Themes.ALL.get(theme);
				SkyLyricsConfig look = t.preview();
				String name = Ui.clip(font, t.name().get(), pw - 140 * pw / PANEL_W);
				int color = look.colorMode == ColorMode.FIXED ? look.color : 0xA678FF;
				Component styled = Component.literal(name).withStyle(LyricsFont.sky(look).withColor(color));
				g.drawString(font, styled, cx - font.width(styled) / 2, py + vy(106, ph), 0xFFFFFFFF, look.shadow);
			}
			case 2 -> {
				heading(g, text.heading(), py + vy(22, ph));
				lines(g, py + vy(106, ph), layoutText(cfg.layout));
			}
			case 3 -> {
				heading(g, text.heading(), py + vy(22, ph));
				lines(g, py + vy(38, ph), text.paragraphs().toArray(new String[0]));
			}
			case 4 -> {
				heading(g, text.heading(), py + vy(22, ph));
				SkyLyricsClient mod = SkyLyricsClient.get();
				int y = lines(g, py + vy(40, ph), text.paragraphs().toArray(new String[0]));
				if (mod != null) {
					y = keyLine(g, Tr.t("Settings"), mod.openSettingsKey(), y + 2);
					keyLine(g, Tr.t("Song library"), mod.libraryKey(), y);
				}
			}
			default -> {
			}
		}
		dots(g, cx, py + ph - 40);
	}

	private void heading(GuiGraphics g, String text, int y) {
		g.drawCenteredString(font, Ui.visual(text), px + pw / 2, y, Ui.TEXT);
	}

	/** Centred paragraphs, wrapped to the panel. */
	private int lines(GuiGraphics g, int y, String... paragraphs) {
		for (String p : paragraphs) {
			List<String> wrapped = wrap(p, pw - 40);
			for (String line : wrapped) {
				if (y + 8 > textLimit()) {
					return y; // no room left: stop rather than run into the buttons
				}
				g.drawCenteredString(font, Ui.visual(line), px + pw / 2, y, Ui.MUTED);
				y += 11;
			}
			y += 4;
		}
		return y;
	}

	private int keyLine(GuiGraphics g, String what, @Nullable KeyMapping key, int y) {
		String name = key == null ? "-" : key.getTranslatedKeyMessage().getString();
		String label = Ui.clip(font, what, pw / 2 - 30);
		g.drawString(font, Ui.visual(label), px + pw / 2 - 10 - font.width(label), y, Ui.MUTED, false);
		int w = font.width(name) + 10;
		Ui.rect(g, px + pw / 2 + 4, y - 3, w, 14, 3, Ui.CONTROL);
		g.drawString(font, Ui.visual(name), px + pw / 2 + 9, y, Ui.TEXT, false);
		return y + 18;
	}

	private void dots(GuiGraphics g, int cx, int y) {
		int x = cx - (PAGES * 10 - 4) / 2;
		for (int i = 0; i < PAGES; i++) {
			Ui.rect(g, x + i * 10, y, 6, 6, 3, i == page ? Ui.ACCENT : Ui.TRACK);
		}
	}

	/** Splits on spaces so each line fits {@code maxW}. */
	private List<String> wrap(String text, int maxW) {
		List<String> out = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (font.width(next) > maxW && !line.isEmpty()) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}
}
