package dev.beli.skylyrics.render;

import java.util.List;
import java.util.function.ToIntFunction;

import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.BarPosition;
import net.minecraft.client.Minecraft;

/**
 * The debug screen's (F3) text as the game drew it last: one box per line (left, top, right, bottom, in GUI units), for
 * its left and its right column, so a bar or a Record card at the bottom can tell whether it would sit on that text, and a
 * bar or card anywhere can keep clear of the lines a player pinned to show with F3 closed (1.21.9 and later). How much F3 shows
 * depends on the game version and the mods (on 1.21.1 its left column nearly fills a GUI scale 3 screen, on 1.21.11 it
 * stops well above a bottom bar at GUI scale 2), so it is measured as the game draws each column, not guessed.
 */
public final class DebugText {
	/** The game puts each line this far below the one before, its first 2 down and 2 in from its side of the screen. */
	static final int LINE_H = 9, INSET = 2;

	private static volatile float[][] left = new float[0][], right = new float[0][];
	/** When each column was drawn last and the time before, for {@link DrawnLately}. */
	private static volatile long leftAt, rightAt, leftBefore, rightBefore;

	private DebugText() {
	}

	/**
	 * Called as the game draws one of F3's columns (DebugScreenOverlayMixin), with its lines, the screen's width and the
	 * game's font. It only measures while a bar or a Record card could need it ({@link #measures}).
	 */
	public static void drawn(List<String> lines, boolean leftColumn, int guiWidth, ToIntFunction<String> width) {
		if (!measures(SkyLyricsConfig.get(), NowPlayingHud.f3Open(Minecraft.getInstance()))) {
			return;
		}
		int[] widths = new int[lines.size()];
		for (int i = 0; i < widths.length; i++) {
			String line = lines.get(i);
			widths[i] = line == null || line.isEmpty() ? -1 : width.applyAsInt(line);
		}
		record(boxes(widths, leftColumn, guiWidth), leftColumn, System.nanoTime());
	}

	/** Keeps one column's line boxes as the game drew them at {@code now}. */
	static void record(float[][] boxes, boolean leftColumn, long now) {
		if (leftColumn) {
			left = boxes;
			leftBefore = leftAt;
			leftAt = now;
		} else {
			right = boxes;
			rightBefore = rightAt;
			rightAt = now;
		}
	}

	/**
	 * Whether F3's text is measured as the game draws it. With F3 open, while a bar or a Record card at the bottom could need
	 * it (at the top they hide for F3 anyway). With F3 closed the game draws only the lines a player pinned to show always
	 * (1.21.9 and later, in the debug options screen), in the top corners, so then for a bar or a card anywhere.
	 */
	static boolean measures(SkyLyricsConfig cfg, boolean f3Open) {
		boolean bar = cfg.nowPlayingHud && (!f3Open || cfg.barPosition == BarPosition.BOTTOM);
		boolean card = cfg.recordCard && (!f3Open || !RecordCard.topCorner(cfg.cardCorner));
		return cfg.enabled && (bar || card);
	}

	/**
	 * The boxes of one column's lines as the game draws them: line {@code i} at 2 + 9i down, 2 in from the left edge (or
	 * right-aligned 2 in from the right one), on a background one unit wider than its text all round and 9 tall. A line
	 * of width -1 is empty: the game skips it, but it still takes its row.
	 */
	static float[][] boxes(int[] widths, boolean leftColumn, int guiWidth) {
		int count = 0;
		for (int w : widths) {
			count += w >= 0 ? 1 : 0;
		}
		float[][] out = new float[count][];
		int k = 0;
		for (int i = 0; i < widths.length; i++) {
			int w = widths[i];
			if (w < 0) {
				continue;
			}
			int x = leftColumn ? INSET : guiWidth - INSET - w;
			int y = INSET + LINE_H * i;
			out[k++] = new float[] {x - 1, y - 1, x + w + 1, y + LINE_H - 1};
		}
		return out;
	}

	/**
	 * Both columns' line boxes, or null when the game hasn't drawn F3's text lately: a column it stopped drawing is taken
	 * as gone (F3 closed, or a mod draws F3 its own way), one it drew in its last frame still counts on a slow game
	 * ({@link DrawnLately}). The game draws F3 after the HUD, so the bar and the card always read the last frame's.
	 */
	static float @Nullable [][] recent(long now) {
		boolean l = DrawnLately.drawn(now, leftAt, leftBefore), r = DrawnLately.drawn(now, rightAt, rightBefore);
		if (!l && !r) {
			return null;
		}
		float[][] a = l ? left : new float[0][], b = r ? right : new float[0][];
		float[][] both = new float[a.length + b.length][];
		System.arraycopy(a, 0, both, 0, a.length);
		System.arraycopy(b, 0, both, a.length, b.length);
		return both;
	}

	/**
	 * The least top at which something spanning {@code left} to {@code right} keeps {@link TopBarLayout#DODGE_GAP} below
	 * every line box its width reaches, so it sits under them however far down it starts; 0 when it reaches none.
	 */
	static float clearBelow(float[][] lines, float left, float right) {
		float clear = 0;
		for (float[] line : lines) {
			if (line[0] < right && line[2] > left) {
				clear = Math.max(clear, line[3] + TopBarLayout.DODGE_GAP);
			}
		}
		return clear;
	}

	/** Whether any of the line boxes overlaps the box (left, top, right, bottom). */
	static boolean covers(float[] box, float[][] lines) {
		for (float[] line : lines) {
			if (TopBarLayout.overlaps(line, box[0], box[1], box[2] - box[0], box[3] - box[1])) {
				return true;
			}
		}
		return false;
	}
}
