package dev.beli.skylyrics.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;

/**
 * Where the debug screen's (F3) charts sit, as boxes (left, top, right, bottom) in GUI units: the frame time and tick time
 * charts (F3+2), the network charts (F3+3), the profiler's pie (F3+1) and, from 26.1, the light map (F3+4). The game draws
 * them along the bottom of the screen after the HUD, over a bar or a Record card at the bottom. Unlike F3's text their
 * places don't change with mods or with what you look at, so they are the game's own sums, per version.
 */
final class DebugCharts {
	/** A line chart: its plot (60 tall) and the min, avg and max line above it (9). */
	static final int CHART_H = 60 + 9;
	/** A line chart is at most this wide (240 samples and its frame), and at most half the screen. */
	static final int CHART_W = 240 + 2;
	/** How far above the screen's bottom the pie's box ends, alone and above a line chart. */
	static final int PIE_LIFT = 10;
	/** The least the pie's box reaches up from its bottom: the pie and its title, with no lines of names under it. */
	static final int PIE_H = 5 + 62 + 5 + 62 + 5;
	/** Half the pie's width: 105 up to 1.21.11, 130 from 26.1. */
	//? if >=26.1 {
	/*static final int PIE_HALF = 130;
	*///?} else {
	static final int PIE_HALF = 105;
	//?}

	private DebugCharts() {
	}

	/**
	 * F3+2's or F3+3's two charts: one from the left edge, one from the right, each {@link #CHART_W} wide (at most half the
	 * screen) and {@link #CHART_H} tall. The right one may be missing (no tick times on a server that sends none, no
	 * bandwidth in single player), but both are kept clear of.
	 */
	static float[][] lineCharts(int guiW, int guiH) {
		int w = Math.min(CHART_W, guiW / 2);
		return new float[][] {{0, guiH - CHART_H, w, guiH}, {guiW - w, guiH - CHART_H, guiW, guiH}};
	}

	/**
	 * The profiler's pie (F3+1) with its dark box, from 1.21.2: in the bottom right corner, {@code lift} above the bottom
	 * ({@link #PIE_LIFT} alone, {@link #CHART_H} over a line chart). The lines of names under it make it taller; the least
	 * it reaches up is taken, which already reaches past a bar at the bottom.
	 */
	static float[] pie(int guiW, int guiH, int lift, int half) {
		return new float[] {guiW - 10 - 2 * half - 5, guiH - lift - PIE_H, guiW - 5, guiH - lift};
	}

	/**
	 * 1.21.1's pie: drawn in the window's own pixels (not scaled with the GUI), without a box: centred 170 from the right
	 * and 320 from the bottom, 160 wide each way, its title 96 above the centre and its first line of names ending 109 below.
	 */
	static float[] pie121(int guiW, int guiH, double guiScale) {
		float s = (float) guiScale;
		return new float[] {guiW - 330 / s, guiH - 416 / s, guiW - 10 / s, guiH - 211 / s};
	}

	/** 26.1's light map (F3+4): 64 square, 2 in from the bottom right corner, in a 1 wide frame. */
	static float[] lightmap(int guiW, int guiH) {
		return new float[] {guiW - 67, guiH - 67, guiW - 1, guiH - 1};
	}

	/** The boxes of the charts F3 shows now (none while it is closed). */
	static float[][] shown(Minecraft mc, int guiW, int guiH) {
		DebugScreenOverlay f3 = mc.getDebugOverlay();
		boolean lines = f3.showFpsCharts() || f3.showNetworkCharts();
		//? if >=26.1 {
		/*boolean lightmap = f3.showLightmapTexture();
		*///?} else {
		boolean lightmap = false;
		//?}
		return boxes(guiW, guiH, mc.getWindow().getGuiScale(), lines, f3.showProfilerChart(), lightmap);
	}

	/** The boxes of the charts shown: the line charts, the pie and the light map, each only when it shows. */
	static float[][] boxes(int guiW, int guiH, double guiScale, boolean lines, boolean pie, boolean lightmap) {
		float[][] out = new float[(lines ? 2 : 0) + (pie ? 1 : 0) + (lightmap ? 1 : 0)][];
		int k = 0;
		if (lines) {
			float[][] two = lineCharts(guiW, guiH);
			out[k++] = two[0];
			out[k++] = two[1];
		}
		if (pie) {
			//? if <1.21.2 {
			/*out[k++] = pie121(guiW, guiH, guiScale);
			*///?} else {
			out[k++] = pie(guiW, guiH, lines ? CHART_H : PIE_LIFT, PIE_HALF);
			//?}
		}
		if (lightmap) {
			out[k++] = lightmap(guiW, guiH);
		}
		return out;
	}
}
