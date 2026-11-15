package dev.beli.skylyrics.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import dev.beli.skylyrics.SkyLyricsClient;

/**
 * Development only ({@code barprobe} in the test hook): logs the numbers the top bar used to place its highlight and
 * words in one frame. Until requested, the bar pays one static boolean check per frame.
 */
public final class BarProbe {
	private static boolean next;
	private static boolean on;

	private BarProbe() {
	}

	/** Asks for the next top bar frame that draws a lyric line to be logged. */
	public static void request() {
		next = true;
	}

	/** True for the one line drawn while probing; {@link #end} must follow. */
	static boolean begin(boolean live) {
		if (!next || !live) {
			return false;
		}
		next = false;
		on = true;
		return true;
	}

	static boolean on() {
		return on;
	}

	static void end() {
		on = false;
	}

	private static void log(String format, Object... args) {
		SkyLyricsClient.LOG.info("Test hook: barprobe " + format, args);
	}

	static void line(String text, boolean rtl, float scale, float start, float shift, float pad, float gap, float total, GuiGraphics g) {
		log("line \"{}\" rtl={} scale={} start={} shift={} pad={} wordGap={} total={}", text, rtl, scale, start, shift, pad, gap, total);
		log("pose (line level) {} guiScale={} guiWidth={}", pose(g), net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale(), g.guiWidth());
	}

	static void highlight(int current, float left, float right, float hlLeft, float hlRight, float x0) {
		log("highlight current={} left={} right={} hlLeft={} hlRight={} x0={}", current, left, right, hlLeft, hlRight, x0);
	}

	/** Inside {@code roundRectGui}: the box as passed and the pose before and after its scale. */
	static void box(float x, float w, String before, String after) {
		log("box left={} right={} (as passed to roundRectGui) pose before={} after 1/guiScale={}", x, x + w, before, after);
	}

	static void word(Font font, Style style, String text, boolean rtl, float x, float w, float wx, float scale) {
		FormattedCharSequence seq = NowPlayingHud.wordSeq(text, rtl, style);
		float split = font.getSplitter().stringWidth(seq) * scale;
		log("word \"{}\" x={} w={} wx={} advance(splitter)={} advance(Font.width)={}", text, x, w, wx, split, font.width(seq) * scale);
		//? if >=1.21.11 && <26.1 {
		net.minecraft.client.gui.navigation.ScreenRectangle b = font.prepareText(seq, 0, 0, 0xFFFFFFFF, false, false, 0).bounds();
		if (b == null) {
			log("word \"{}\" glyph edges: none", text);
		} else {
			log("word \"{}\" glyph edges (whole px, font units) left={} right={}; drawn at {} .. {}", text, b.left(), b.right(),
					wx + b.left() * scale, wx + b.right() * scale);
		}
		//?} else {
		/*log("word \"{}\" glyph edges: n/a", text);
		*///?}
	}

	/** Inside {@code word()} or {@code text()}, after the translate and scale. */
	static void wordPose(GuiGraphics g) {
		log("pose in word {}", pose(g));
	}

	static String pose(GuiGraphics g) {
		//? if >=1.21.6 {
		var m = g.pose();
		return "translate(" + m.m20() + ", " + m.m21() + ") scale(" + m.m00() + ", " + m.m11() + ")";
		//?} else {
		/*return "n/a";
		*///?}
	}
}
