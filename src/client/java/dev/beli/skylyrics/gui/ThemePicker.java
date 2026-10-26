package dev.beli.skylyrics.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.ColorMode;
import dev.beli.skylyrics.config.Themes;
import dev.beli.skylyrics.config.Themes.Theme;
import dev.beli.skylyrics.render.LyricsFont;

final class ThemePicker extends Controls.Row {
	private static final int COLS = 3;
	private static final int TILE_H = 24;
	private static final int GAP = 4;
	private static final int TOP = 24;
	private final SkyLyricsConfig cfg;

	ThemePicker(SkyLyricsConfig cfg) {
		super(Tr.t("Pick a look"), null);
		this.cfg = cfg;
	}

	@Override
	int height() {
		int rows = (Themes.ALL.size() + COLS - 1) / COLS;
		return TOP + rows * (TILE_H + GAP) - GAP + 8;
	}

	@Override
	int controlWidth(Font font) {
		return 0;
	}

	@Override
	void render(Controls.Frame f) {
		@Nullable Theme active = Themes.active(cfg);
		String current = active == null ? Tr.t("Custom") : active.name().get();
		int currentW = f.font().width(current);
		f.g().text(f.font(), Ui.clip(f.font(), label, w - 28 - currentW), x + 10, y + 8, Ui.TEXT, false);
		f.g().text(f.font(), current, x + w - 10 - currentW, y + 8, active == null ? Ui.MUTED : Ui.ACCENT, false);
		renderControl(f);
	}

	@Override
	void renderControl(Controls.Frame f) {
		@Nullable Theme active = Themes.active(cfg);
		for (int i = 0; i < Themes.ALL.size(); i++) {
			Theme theme = Themes.ALL.get(i);
			int tx = tileX(i), ty = tileY(i), tw = tileW();
			boolean hover = f.hoverAllowed() && Ui.inside(f.mx(), f.my(), tx, ty, tw, TILE_H);
			Ui.rect(f.g(), tx, ty, tw, TILE_H, 4, hover || theme == active ? Ui.CONTROL_HOVER : Ui.CONTROL);
			if (theme == active) {
				Ui.outline(f.g(), tx, ty, tw, TILE_H, 4, Ui.ACCENT);
			}
			SkyLyricsConfig look = theme.preview();
			String name = Ui.clip(f.font(), theme.name().get(), tw - 8);
			Style style = LyricsFont.sky(look);
			int nameW = Math.round(LyricsFont.width(f.font(), style, name));
			f.g().text(f.font(), styled(look, style, name), tx + (tw - nameW) / 2, ty + (TILE_H - 8) / 2, 0xFFFFFFFF,
					look.shadow);
		}
	}

	private static Component styled(SkyLyricsConfig look, Style style, String name) {
		if (look.colorMode != ColorMode.RAINBOW) {
			return Component.literal(name).withStyle(style.withColor(look.color));
		}
		MutableComponent out = Component.empty();
		float t = System.nanoTime() / 1e9f / 4f;
		int n = name.length();
		for (int i = 0; i < n; i++) {
			int rgb = java.awt.Color.HSBtoRGB((t + i / (float) Math.max(1, n)) % 1f, 0.55f, 1f) & 0xFFFFFF;
			out.append(Component.literal(String.valueOf(name.charAt(i))).withStyle(style.withColor(rgb)));
		}
		return out;
	}

	private int tileW() {
		return (w - 20 - GAP * (COLS - 1)) / COLS;
	}

	private int tileX(int i) {
		return x + 10 + i % COLS * (tileW() + GAP);
	}

	private int tileY(int i) {
		return y + TOP + i / COLS * (TILE_H + GAP);
	}

	@Override
	boolean click(double mx, double my) {
		for (int i = 0; i < Themes.ALL.size(); i++) {
			if (Ui.inside(mx, my, tileX(i), tileY(i), tileW(), TILE_H)) {
				Themes.apply(Themes.ALL.get(i), cfg);
				return true;
			}
		}
		return false;
	}
}
