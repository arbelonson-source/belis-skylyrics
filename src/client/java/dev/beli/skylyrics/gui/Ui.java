package dev.beli.skylyrics.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** Colours and small drawing helpers shared by the settings screen. */
final class Ui {
	static final int PANEL = 0xFF141019;
	static final int SIDEBAR = 0xFF0F0C15;
	static final int CARD = 0xFF1C1725;
	static final int CARD_HOVER = 0xFF231D2E;
	static final int DIVIDER = 0xFF2A2433;
	static final int TEXT = 0xFFF2EEF8;
	static final int MUTED = 0xFF9C94AD;
	static final int DIM = 0xFF6D6680;
	static final int ACCENT = 0xFFA678FF;
	static final int ACCENT_SOFT = 0x40A678FF;
	static final int TRACK = 0xFF3A3346;
	static final int CONTROL = 0xFF2B2536;
	static final int CONTROL_HOVER = 0xFF372F45;

	private Ui() {
	}

	/** Filled rectangle with rounded corners, drawn without overlapping fills so alpha stays even. */
	static void rect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0 || ARGB.alpha(color) == 0) {
			return;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			float dy = r - i - 0.5f;
			int inset = Math.round(r - Mth.sqrt(r * r - dy * dy));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
		}
	}

	/** One pixel outline with rounded corners. */
	static void outline(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		r = Math.min(r, Math.min(w, h) / 2);
		int[] inset = new int[Math.max(r, 1)];
		for (int i = 0; i < r; i++) {
			float dy = r - i - 0.5f;
			inset[i] = Math.round(r - Mth.sqrt(r * r - dy * dy));
		}
		g.fill(x + inset[0], y, x + w - inset[0], y + 1, color);
		g.fill(x + inset[0], y + h - 1, x + w - inset[0], y + h, color);
		for (int i = 1; i < r; i++) {
			int from = inset[i];
			int to = Math.max(from + 1, inset[i - 1]);
			g.fill(x + from, y + i, x + to, y + i + 1, color);
			g.fill(x + w - to, y + i, x + w - from, y + i + 1, color);
			g.fill(x + from, y + h - 1 - i, x + to, y + h - i, color);
			g.fill(x + w - to, y + h - 1 - i, x + w - from, y + h - i, color);
		}
		g.fill(x, y + Math.max(r, 1), x + 1, y + h - Math.max(r, 1), color);
		g.fill(x + w - 1, y + Math.max(r, 1), x + w, y + h - Math.max(r, 1), color);
	}

	static int alpha(int argb, float a) {
		return ARGB.color(Math.round(ARGB.alpha(argb) * Mth.clamp(a, 0, 1)), argb & 0xFFFFFF);
	}

	static int lerp(float t, int a, int b) {
		return ARGB.srgbLerp(Mth.clamp(t, 0, 1), a, b);
	}

	static String clip(Font font, String text, int maxW) {
		if (maxW <= 0) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "…";
		return font.plainSubstrByWidth(text, Math.max(0, maxW - font.width(ellipsis))) + ellipsis;
	}

	static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Moves {@code value} towards {@code target} with a frame-rate independent ease. */
	static float approach(float value, float target, float dt, float speed) {
		float k = 1 - (float) Math.exp(-dt * speed);
		float next = value + (target - value) * k;
		return Math.abs(target - next) < 0.002f ? target : next;
	}
}
