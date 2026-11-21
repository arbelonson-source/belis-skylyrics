package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * A flowing line that mixes reading directions (an English phrase in a Hebrew line, a Hebrew phrase in an English one)
 * keeps every row between the line's own two edges, as a line of one direction does: the phrase's words are never
 * walked back past the edge its rows start from (right across the kept-clear middle of the view), and a phrase wider
 * than a row does not run on past the far edge.
 */
class WorldFlowRowEdgeTest {
	private static final String HEBREW = "השירים שלנו עולים Under The Bright Night Sky כל הלילה עד הבוקר ועוד קצת אחרי זה כדי שהשורה תהיה ארוכה מאוד";
	private static final String ENGLISH = "We keep on singing שיר של לילה ארוך all the way across the sky until the morning comes back again";
	/** Lines of one direction, which already keep to their edges: the rule the mixed ones are held to. */
	private static final String PLAIN_HEBREW = "השירים שלנו עולים מעל הים כל הלילה עד הבוקר ועוד קצת אחרי זה כדי שהשורה תהיה ארוכה מאוד";
	private static final String PLAIN_ENGLISH = "We keep on singing under the bright night sky all the way across until the morning comes back again";
	private static final String[] EN = {"we", "keep", "on", "singing", "night", "light", "river", "over", "stars", "falling", "slowly", "again",
		"under", "bright", "sky", "morning", "across", "the", "way", "home"};
	private static final String[] HE = {"שיר", "של", "לילה", "ארוך", "כוכבים", "נופלים", "לאט", "מעל", "הים", "בבוקר", "שוב", "אור", "קטן", "רחוק"};
	/** How far, in degrees, a word may stick out past its line's edge. */
	private static final float SLACK = 1.5f;

	/** One piece of a flowing line as drawn: its line, the row it was written on, and its left and right edges in yaw. */
	private record Piece(WorldWords.Sentence of, int row, float left, float right, String text) {
	}

	@SuppressWarnings("unchecked")
	private static List<Piece> write(String line, long seed, float pitch) throws Exception {
		WorldWords.measureHook = t -> t.length() * 4.0;
		WorldWords.buriedHook = pos -> false;
		try {
			ScreenLayer.capture(new Matrix4f(), new Matrix4f().perspective((float) Math.toRadians(70), 1.7f, 0.05f, 100f));
			WorldWords words = new WorldWords();
			words.seed(seed);
			words.tune(30, 6, 1, 1, 1, true, true, false, 1);
			words.players(false, false);
			Camera camera = new Camera();
			Field cardsField = WorldWords.class.getDeclaredField("cards");
			cardsField.setAccessible(true);
			Map<Object, Piece> seen = new IdentityHashMap<>();
			List<Piece> out = new ArrayList<>();
			int length = line.codePointCount(0, line.length());
			// Half a letter at a time, so each update brings at most one new piece and its row is the line's row then
			for (int k = 0; k <= 2 * length + 2; k++) {
				words.update(0, line, k * 0.5f, false, 0f, pitch, camera, null, null, net.minecraft.network.chat.Style.EMPTY, (k + 1) * 50_000_000L);
				for (Object card : (List<Object>) cardsField.get(words)) {
					if (seen.containsKey(card)) {
						continue;
					}
					Class<?> c = card.getClass();
					Field of = c.getDeclaredField("of"), pos = c.getDeclaredField("pos"), at = c.getDeclaredField("at"), size = c.getDeclaredField("size");
					Field disp = c.getDeclaredField("display");
					for (Field f : new Field[] {of, pos, at, size, disp}) {
						f.setAccessible(true);
					}
					WorldWords.Sentence s = (WorldWords.Sentence) of.get(card);
					Vec3 p = (Vec3) pos.get(card);
					float[] a = (float[]) at.get(card);
					float half = a[a.length - 1] * size.getFloat(card) / 2;
					double flat = Math.sqrt(p.x * p.x + p.z * p.z);
					float yaw = (float) Math.toDegrees(Math.atan2(-p.x, p.z));
					float width = WorldWords.halfAngle(half, flat);
					int[] cps = ((LyricsRenderer.Display) disp.get(card)).cps();
					Piece piece = new Piece(s, s == null ? -1 : s.rows, yaw - width, yaw + width, new String(cps, 0, cps.length));
					seen.put(card, piece);
					out.add(piece);
				}
			}
			return out;
		} finally {
			WorldWords.measureHook = null;
			WorldWords.buriedHook = null;
			var captured = ScreenLayer.class.getDeclaredField("captured");
			captured.setAccessible(true);
			captured.setBoolean(null, false);
		}
	}

	/** What is wrong with the rows of one written line: each row's start and end edge against the line's own edges. */
	private static String rowEdges(List<Piece> pieces) {
		Map<WorldWords.Sentence, Map<Integer, List<Piece>>> lines = new LinkedHashMap<>();
		for (Piece p : pieces) {
			if (p.of != null) {
				lines.computeIfAbsent(p.of, s -> new LinkedHashMap<>()).computeIfAbsent(p.row, r -> new ArrayList<>()).add(p);
			}
		}
		StringBuilder bad = new StringBuilder();
		for (Map.Entry<WorldWords.Sentence, Map<Integer, List<Piece>>> line : lines.entrySet()) {
			WorldWords.Sentence s = line.getKey();
			for (Map.Entry<Integer, List<Piece>> row : line.getValue().entrySet()) {
				// Measured from the line's left edge, as drawn
				float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
				StringBuilder words = new StringBuilder();
				for (Piece p : row.getValue()) {
					lo = Math.min(lo, Mth.wrapDegrees(p.left - s.left));
					hi = Math.max(hi, Mth.wrapDegrees(p.right - s.left));
					words.append(" [").append(p.text).append(' ').append(Math.round(Mth.wrapDegrees(p.left - s.left))).append("..")
						.append(Math.round(Mth.wrapDegrees(p.right - s.left))).append(']');
				}
				// The row starts at the line's left edge when it reads left to right, at its right edge when right to left
				float start = s.rtl ? s.width - hi : lo, end = s.rtl ? s.width - lo : hi;
				if (start < -SLACK || end > s.width + SLACK) {
					bad.append(s.rtl ? "rtl" : "ltr").append(" row ").append(row.getKey()).append(" starts ").append(start).append(" ends ")
						.append(end).append(" of ").append(s.width).append(':').append(words).append('\n');
				}
			}
		}
		return bad.toString();
	}

	/** A made-up line in one direction with a phrase of two to five words in the other somewhere after its start. */
	private static String mixed(Random random) {
		boolean rtl = random.nextBoolean();
		String[] base = rtl ? HE : EN, other = rtl ? EN : HE;
		int words = 9 + random.nextInt(6), at = 2 + random.nextInt(words - 3), phrase = 2 + random.nextInt(4);
		StringBuilder line = new StringBuilder();
		for (int w = 0; w < words; w++) {
			if (w == at) {
				for (int k = 0; k < phrase; k++) {
					line.append(other[random.nextInt(other.length)]).append(' ');
				}
			}
			line.append(base[random.nextInt(base.length)]).append(' ');
		}
		return line.toString().trim();
	}

	@Test
	void everyRowOfAMixedLineStaysBetweenTheLinesEdges() throws Exception {
		List<String> lines = new ArrayList<>(List.of(HEBREW, ENGLISH, PLAIN_HEBREW, PLAIN_ENGLISH));
		Random random = new Random(83);
		for (int k = 0; k < 6; k++) {
			lines.add(mixed(random));
		}
		StringBuilder bad = new StringBuilder(), lineCounts = new StringBuilder();
		int written = 0, wrong = 0;
		for (String line : lines) {
			int before = wrong;
			for (float pitch : new float[] {0, -30, 30}) {
				for (long seed = 1; seed <= 8; seed++) {
					List<Piece> pieces = write(line, seed, pitch);
					written += pieces.size();
					String rows = rowEdges(pieces);
					// Every bad layout counts; the first few are shown
					if (!rows.isEmpty() && wrong++ < 12) {
						bad.append(line).append(" | pitch ").append(pitch).append(" seed ").append(seed).append('\n').append(rows);
					}
				}
			}
			if (wrong > before) {
				lineCounts.append(wrong - before).append(" of 24: ").append(line).append('\n');
			}
		}
		assertTrue(written > 500, "pieces written " + written);
		assertTrue(wrong == 0, wrong + " layouts with rows past their line's edges:\n" + lineCounts + "the first:\n" + bad);
	}

	@Test
	void aPhraseBesideTheKeptClearMiddleStaysInItsRoomOnItsLinesSide() {
		int[] runs = {WorldWords.RUN_FIRST, WorldWords.RUN_MIDDLE, WorldWords.RUN_LAST, WorldWords.RUN_NONE, WorldWords.RUN_NONE};
		// The kept-clear middle, as a firm footprint: 15 degrees either side of where you look, the row inside its height
		List<float[]> middle = List.of(new float[] {0, -18, 15, 11});
		for (boolean rtl : new boolean[] {false, true}) {
			// A line just beside the middle: on its right when it reads left to right, on its left when right to left
			WorldWords.Sentence s = new WorldWords.Sentence(rtl ? -40 : 6, -20, 10, 0, 0, rtl);
			float[] half = new float[runs.length];
			java.util.Arrays.fill(half, (float) (s.distance * Math.tan(Math.toRadians(3))));
			StringBuilder bad = new StringBuilder();
			for (int i = 0; i < runs.length; i++) {
				WorldWords.openRun(s, runs, half, i, 1f, 1f);
				float yaw = WorldWords.placeClear(s, 3, 1, 0, 0, middle)[0];
				WorldWords.closeRun(s, runs[i]);
				if (yaw - 3 < s.left - SLACK || yaw + 3 > s.left + s.width + SLACK || (rtl ? yaw > 0 : yaw < 0)) {
					bad.append("piece ").append(i).append(" at ").append(yaw).append("; ");
				}
			}
			assertTrue(bad.length() == 0, (rtl ? "rtl" : "ltr") + " line from " + s.left + " to " + (s.left + s.width) + ": " + bad);
		}
	}
}
