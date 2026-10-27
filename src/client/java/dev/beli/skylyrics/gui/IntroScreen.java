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

	private final @Nullable Screen parent;
	private final SkyLyricsConfig cfg = SkyLyricsConfig.get();
	private int page;
	private int theme;
	private int px, py;

	public IntroScreen(@Nullable Screen parent) {
		super(Component.literal("Beli's SkyLyrics"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		px = (width - PANEL_W) / 2;
		py = Math.max(4, (height - PANEL_H) / 2);
		int bottom = py + PANEL_H - 28;
		int mid = py + 100;
		switch (page) {
			case 0 -> {
				add(Tr.t("Skip"), px + 12, bottom, 80, this::finish);
				add(Tr.t("Show me around"), px + PANEL_W - 132, bottom, 120, () -> go(1));
				return;
			}
			case 1 -> {
				add("<", px + 40, mid, 20, () -> pickTheme(-1));
				add(">", px + PANEL_W - 60, mid, 20, () -> pickTheme(1));
			}
			case 2 -> {
				Layout[] layouts = {Layout.WORLD, Layout.ARC, Layout.LINES};
				int bw = 84;
				int x = px + (PANEL_W - 3 * bw - 16) / 2;
				for (Layout layout : layouts) {
					String name = layoutName(layout);
					add(cfg.layout == layout ? "> " + name + " <" : name, x, mid - 24, bw, () -> {
						cfg.layout = layout;
						rebuildWidgets();
					});
					x += bw + 8;
				}
			}
			case 3 -> add(cfg.demoMode ? Tr.t("Demo song: on") : Tr.t("Demo song: off"), px + 70, mid + 20, PANEL_W - 140, () -> {
				cfg.demoMode = !cfg.demoMode;
				SkyLyricsClient mod = SkyLyricsClient.get();
				if (mod != null) {
					mod.tracker().refresh();
				}
				rebuildWidgets();
			});
			case 4 -> {
				add(Tr.t("Back"), px + 12, bottom, 60, () -> go(3));
				add(Tr.t("All settings"), px + PANEL_W / 2 - 50, bottom, 100, () -> {
					finish();
					setScreen(new SkyLyricsScreen(parent));
				});
				add(Tr.t("Done"), px + PANEL_W - 72, bottom, 60, this::finish);
				return;
			}
			default -> {
			}
		}
		add(Tr.t("Back"), px + 12, bottom, 60, () -> go(page - 1));
		add(Tr.t("Next"), px + PANEL_W - 72, bottom, 60, () -> go(page + 1));
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
		Ui.rect(g, px, py, PANEL_W, PANEL_H, 8, Ui.PANEL);
		Ui.outline(g, px, py, PANEL_W, PANEL_H, 8, Ui.DIVIDER);
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
	//?}
		int cx = px + PANEL_W / 2;
		switch (page) {
			case 0 -> {
				heading(g, "Beli's SkyLyrics", py + 22);
				lines(g, py + 50, Tr.t("The lyrics of the song you're playing, floating in the sky and sung along in time."),
						Tr.t("Let's set it up. It takes a few seconds."));
			}
			case 1 -> {
				heading(g, Tr.t("Pick a look"), py + 22);
				lines(g, py + 42, Tr.t("You can change every part of it later."));
				Theme t = Themes.ALL.get(theme);
				SkyLyricsConfig look = t.preview();
				String name = Ui.clip(font, t.name().get(), PANEL_W - 140);
				int color = look.colorMode == ColorMode.FIXED ? look.color : 0xA678FF;
				Component styled = Component.literal(name).withStyle(LyricsFont.sky(look).withColor(color));
				g.drawString(font, styled, cx - font.width(styled) / 2, py + 106, 0xFFFFFFFF, look.shadow);
			}
			case 2 -> {
				heading(g, Tr.t("Where should the lyrics go?"), py + 22);
				lines(g, py + 106, layoutText(cfg.layout));
			}
			case 3 -> {
				heading(g, Tr.t("Where the songs come from"), py + 22);
				lines(g, py + 42, Tr.t("Play a song in Spotify or your music player and its lyrics show up by themselves."),
						Tr.t("Signing in to Spotify in the Music tab helps with timing and covers."),
						Tr.t("No music right now? Try the demo song."));
			}
			case 4 -> {
				heading(g, Tr.t("You're all set"), py + 22);
				SkyLyricsClient mod = SkyLyricsClient.get();
				int y = py + 50;
				if (mod != null) {
					y = keyLine(g, Tr.t("Settings"), mod.openSettingsKey(), y);
					y = keyLine(g, Tr.t("Song library"), mod.libraryKey(), y);
				}
				lines(g, y + 8, Tr.t("You can change the keys in Minecraft's Controls settings."));
			}
			default -> {
			}
		}
		dots(g, cx, py + PANEL_H - 40);
	}

	private void heading(GuiGraphics g, String text, int y) {
		g.drawCenteredString(font, Ui.visual(text), px + PANEL_W / 2, y, Ui.TEXT);
	}

	/** Centred paragraphs, wrapped to the panel. */
	private void lines(GuiGraphics g, int y, String... paragraphs) {
		for (String p : paragraphs) {
			List<String> wrapped = wrap(p, PANEL_W - 40);
			for (String line : wrapped) {
				g.drawCenteredString(font, Ui.visual(line), px + PANEL_W / 2, y, Ui.MUTED);
				y += 11;
			}
			y += 4;
		}
	}

	private int keyLine(GuiGraphics g, String what, @Nullable KeyMapping key, int y) {
		String name = key == null ? "-" : key.getTranslatedKeyMessage().getString();
		String label = Ui.clip(font, what, PANEL_W / 2 - 30);
		g.drawString(font, Ui.visual(label), px + PANEL_W / 2 - 10 - font.width(label), y, Ui.MUTED, false);
		int w = font.width(name) + 10;
		Ui.rect(g, px + PANEL_W / 2 + 4, y - 3, w, 14, 3, Ui.CONTROL);
		g.drawString(font, Ui.visual(name), px + PANEL_W / 2 + 9, y, Ui.TEXT, false);
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
