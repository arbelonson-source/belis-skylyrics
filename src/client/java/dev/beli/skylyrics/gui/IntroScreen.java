package dev.beli.skylyrics.gui;

import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
	private int theme;
	private int px, py;
	/** The panel, fitted to the window (300 x 190 when there is room). */
	private int pw = PANEL_W, ph = PANEL_H;

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
		switch (page) {
			case 0 -> {
				add(Tr.t("Skip"), px + 12, bottom, bw(80, pw), this::finish);
				add(Tr.t("Show me around"), px + pw - 12 - bw(120, pw), bottom, bw(120, pw), () -> go(1));
				return;
			}
			case 1 -> {
				add("<", px + 40, mid, 20, () -> pickTheme(-1));
				add(">", px + pw - 60, mid, 20, () -> pickTheme(1));
			}
			case 2 -> {
				Layout[] layouts = {Layout.WORLD, Layout.ARC, Layout.LINES};
				int w3 = bw(84, pw);
				int x = px + (pw - 3 * w3 - 16) / 2;
				for (Layout layout : layouts) {
					String name = layoutName(layout);
					add(cfg.layout == layout ? "> " + name + " <" : name, x, mid - 24, w3, () -> {
						cfg.layout = layout;
						rebuildWidgets();
					});
					x += w3 + 8;
				}
			}
			case 3 -> add(cfg.demoMode ? Tr.t("Demo song: on") : Tr.t("Demo song: off"), px + 70 * pw / PANEL_W, Math.min(mid + 20, bottom - 24), pw - 140 * pw / PANEL_W, () -> {
				cfg.demoMode = !cfg.demoMode;
				SkyLyricsClient mod = SkyLyricsClient.get();
				if (mod != null) {
					mod.tracker().refresh();
				}
				rebuildWidgets();
			});
			case 4 -> {
				add(Tr.t("Back"), px + 12, bottom, bw(60, pw), () -> go(3));
				add(Tr.t("All settings"), px + pw / 2 - bw(100, pw) / 2, bottom, bw(100, pw), () -> {
					finish();
					setScreen(new SkyLyricsScreen(parent));
				});
				add(Tr.t("Done"), px + pw - 12 - bw(60, pw), bottom, bw(60, pw), this::finish);
				return;
			}
			default -> {
			}
		}
		add(Tr.t("Back"), px + 12, bottom, bw(60, pw), () -> go(page - 1));
		add(Tr.t("Next"), px + pw - 12 - bw(60, pw), bottom, bw(60, pw), () -> go(page + 1));
	}

	private void add(String label, int x, int y, int w, Runnable action) {
		addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, 20).build());
	}

	private void go(int to) {
		page = Mth.clamp(to, 0, PAGES - 1);
		rebuildWidgets();
	}

	private void pickTheme(int step) {
		theme = Math.floorMod(theme + step, Themes.ALL.size());
		Themes.apply(Themes.ALL.get(theme), cfg);
	}

	static String layoutName(Layout layout) {
		return switch (layout) {
			case WORLD -> Tr.t("World");
			case ARC -> Tr.t("Arc");
			case LINES -> Tr.t("Lines");
		};
	}

	private static String layoutText(Layout layout) {
		return switch (layout) {
			case WORLD -> Tr.t("Words float around you in the world and drift up into the sky.");
			case ARC -> Tr.t("The line being sung, on a gentle curve across the sky.");
			case LINES -> Tr.t("The last, current and next line, straight and centred.");
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
		switch (page) {
			case 0 -> {
				heading(g, "Beli's SkyLyrics", py + vy(22, ph));
				lines(g, py + vy(50, ph), Tr.t("The lyrics of the song you're playing, floating in the sky and sung along in time."),
						Tr.t("Let's set it up. It takes a few seconds."));
			}
			case 1 -> {
				heading(g, Tr.t("Pick a look"), py + vy(22, ph));
				lines(g, py + vy(42, ph), Tr.t("You can change every part of it later."));
				Theme t = Themes.ALL.get(theme);
				SkyLyricsConfig look = t.preview();
				String name = Ui.clip(font, t.name().get(), pw - 140 * pw / PANEL_W);
				int color = look.colorMode == ColorMode.FIXED ? look.color : 0xA678FF;
				Component styled = Component.literal(name).withStyle(LyricsFont.sky(look).withColor(color));
				g.drawString(font, styled, cx - font.width(styled) / 2, py + vy(106, ph), 0xFFFFFFFF, look.shadow);
			}
			case 2 -> {
				heading(g, Tr.t("Where should the lyrics go?"), py + vy(22, ph));
				lines(g, py + vy(106, ph), layoutText(cfg.layout));
			}
			case 3 -> {
				heading(g, Tr.t("Where the songs come from"), py + vy(22, ph));
				lines(g, py + vy(42, ph), Tr.t("Play a song in Spotify or your music player and its lyrics show up by themselves."),
						Tr.t("Signing in to Spotify in the Music tab helps with timing and album covers."),
						Tr.t("No music right now? Try the demo song."));
			}
			case 4 -> {
				heading(g, Tr.t("You're all set"), py + vy(22, ph));
				SkyLyricsClient mod = SkyLyricsClient.get();
				int y = lines(g, py + vy(40, ph),
						Tr.t("In a world, press these keys. You can change them in Minecraft's Controls settings."));
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
