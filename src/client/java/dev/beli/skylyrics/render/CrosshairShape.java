package dev.beli.skylyrics.render;

import dev.beli.skylyrics.config.SkyLyricsConfig.Crosshair;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Our crosshair shapes, as pixels round the middle of the screen in GUI pixels, so they stay crisp at any GUI scale.
 * Each gets a thin dark edge, so it shows on snow as well as on the night sky.
 */
public final class CrosshairShape {
	/** How far a shape may reach from the middle pixel, in GUI pixels: Minecraft's own is 15 across. */
	static final int REACH = 7;
	/** The grid, with a pixel to spare on each side for the edge. */
	private static final int SIDE = REACH * 2 + 3, MIDDLE = REACH + 1;
	/** The dark edge round the shape. */
	private static final int EDGE = 0xB0000000;
	/** Ring radius for CIRCLE, in GUI pixels. */
	private static final float RING = 5f;
	private static final Map<Crosshair, List<int[]>> FACES = new EnumMap<>(Crosshair.class);
	private static final Map<Crosshair, List<int[]>> EDGES = new EnumMap<>(Crosshair.class);

	private CrosshairShape() {
	}

	/** Draws the shape with its middle pixel at x, y, in the given colour (packed RGB). */
	public static void draw(GuiGraphics g, int x, int y, Crosshair style, int rgb) {
		for (int[] run : EDGES.computeIfAbsent(style, s -> runs(edge(pixels(s))))) {
			g.fill(x + run[0], y + run[1], x + run[0] + run[2], y + run[1] + 1, EDGE);
		}
		for (int[] run : FACES.computeIfAbsent(style, s -> runs(pixels(s)))) {
			g.fill(x + run[0], y + run[1], x + run[0] + run[2], y + run[1] + 1, 0xFF000000 | rgb);
		}
	}

	/** Which pixels the shape covers, as [row][column] with the middle pixel at [MIDDLE][MIDDLE]. */
	static boolean[][] pixels(Crosshair style) {
		boolean[][] on = new boolean[SIDE][SIDE];
		for (int dy = -REACH; dy <= REACH; dy++) {
			for (int dx = -REACH; dx <= REACH; dx++) {
				int ax = Math.abs(dx), ay = Math.abs(dy);
				boolean dot = ax == 0 && ay == 0;
				on[dy + MIDDLE][dx + MIDDLE] = switch (style) {
					case NORMAL -> false;
					case DOT -> ax <= 1 && ay <= 1;
					case CROSS -> dot || ax == 0 && ay >= 2 && ay <= 5 || ay == 0 && ax >= 2 && ax <= 5;
					case CIRCLE -> dot || Math.abs((float) Math.sqrt(dx * dx + dy * dy) - RING) < 0.5f;
					case CORNERS -> dot || ay == 4 && ax >= 2 && ax <= 4 || ax == 4 && ay >= 2 && ay <= 4;
					case NOTE -> note(dx, dy);
				};
			}
		}
		return on;
	}

	/** An eighth note: a slanted round head on the aim, a stem up its right side and a flag curling down off the top. */
	private static boolean note(int dx, int dy) {
		boolean head = dy == -1 && dx >= 0 && dx <= 2 || dy == 0 && dx >= -1 && dx <= 2 || dy == 1 && dx >= -1 && dx <= 1;
		boolean stem = dx == 2 && dy >= -6 && dy <= -1;
		boolean flag = dy == -6 && dx == 3 || dy == -5 && (dx == 3 || dx == 4) || (dy == -4 || dy == -3) && dx == 5;
		return head || stem || flag;
	}

	/** The pixels touching the shape, corners included, that aren't part of it. */
	static boolean[][] edge(boolean[][] shape) {
		boolean[][] edge = new boolean[SIDE][SIDE];
		for (int r = 0; r < SIDE; r++) {
			for (int c = 0; c < SIDE; c++) {
				for (int k = 0; k < 9 && !shape[r][c] && !edge[r][c]; k++) {
					int nr = r + k / 3 - 1, nc = c + k % 3 - 1;
					edge[r][c] = nr >= 0 && nr < SIDE && nc >= 0 && nc < SIDE && shape[nr][nc];
				}
			}
		}
		return edge;
	}

	/** Rows of lit pixels joined into runs of {x from the middle, y from the middle, length}, so drawing takes few fills. */
	static List<int[]> runs(boolean[][] on) {
		List<int[]> runs = new ArrayList<>();
		for (int r = 0; r < SIDE; r++) {
			for (int c = 0; c < SIDE; c++) {
				if (!on[r][c]) continue;
				int start = c;
				while (c + 1 < SIDE && on[r][c + 1]) c++;
				runs.add(new int[] {start - MIDDLE, r - MIDDLE, c - start + 1});
			}
		}
		return runs;
	}
}
