package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.Fuzz;
import dev.beli.skylyrics.lyrics.LrcParser;
import dev.beli.skylyrics.lyrics.Lyrics;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.util.TextCut;

/**
 * Every helper that measures, cuts, wraps or orders a line (the Lines rows, the Arc words, the World pieces, the top
 * bar's words and the card and bar text) gets lines no song should have but some will: emoji and joiner sequences,
 * piles of combining marks, right-to-left and mixed text, zero-width characters, lone surrogates, one 10,000-letter
 * word, nothing, only spaces. Each must end quickly, lose no letter and give positions inside the text.
 */
class TextLayoutFuzzTest {
	private static final Duration BOUND = Duration.ofSeconds(60);
	/** A stand-in font: every letter 6 units wide, a mark 0 (the real font is narrower). */
	private static final ToDoubleFunction<String> SIX = s -> s.codePoints().filter(cp -> Character.getType(cp) != Character.NON_SPACING_MARK).count() * 6.0;
	private static final StringSplitter SPLITTER = new StringSplitter((cp, style) -> Character.getType(cp) == Character.NON_SPACING_MARK ? 0 : 6);
	/** One case may take this long at most (a slow one says which it is); they usually take well under a millisecond. */
	private static final Duration CASE = Duration.ofSeconds(5);
	/** What the lyrics parser cuts a line to and a song name is cut to (LrcParser.MAX_LINE, Track.MAX_TEXT). */
	private static final int CUT = 500;
	private static final float[] ROOMS = {0, 0.5f, 6, 40, 110, 160, 320, 1e9f};

	/**
	 * A line as it reaches the layouts: through the lyrics parser's tidying, through a song name's, or raw but no longer
	 * than the {@link LrcParser#MAX_LINE} letters both cut lines and names to (see {@link #aHugeWordReachesTheLayoutsCutAndWrapsQuickly}).
	 */
	private static String line(Fuzz f) {
		String raw = f.nasty(80);
		return switch (f.random.nextInt(4)) {
			case 0 -> {
				Lyrics l = LrcParser.parsePlain(raw, 60_000);
				yield l.isEmpty() ? raw : l.lines().get(0).text();
			}
			case 1 -> new Track(raw, "", "", 0, "", "").title();
			default -> TextCut.limit(raw, CUT);
		};
	}

	private static String noSpaces(String s) {
		return s.replace(" ", "");
	}

	/**
	 * A 10,000-letter word in a lyrics file or a song name reaches the layouts cut to 500 letters, and wraps quickly even
	 * into rows of one letter. The cut matters: each row's cut measures the rest of the word again, so the wrap's time grows
	 * with the square of the word's length (lines that share a stamp join to at most three times the cut).
	 */
	@Test
	void aHugeWordReachesTheLayoutsCutAndWrapsQuickly() {
		String word = "x".repeat(10_000);
		String lyric = LrcParser.parseSynced("[00:01.00]" + word).lines().get(0).text();
		String title = new Track(word, "", "", 0, "", "").title();
		assertTrue(lyric.length() <= CUT && title.length() <= Track.MAX_TEXT);
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
			for (String s : new String[] {lyric, title, "\u05e9".repeat(CUT), "e\u0301".repeat(CUT / 2)}) {
				for (float room : new float[] {0, 6, 160}) {
					assertEquals(noSpaces(s), noSpaces(String.join("", LyricsRenderer.wrap(SPLITTER, Style.EMPTY, s, room))));
				}
			}
		});
	}

	@Test
	void cutsKeepWholeCharactersAndEnd() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(50, 1500, CASE, (f, i) -> {
			String s = f.nasty(80); // these cuts take raw text of any length (the library's search box, say)
			float room = ROOMS[f.random.nextInt(ROOMS.length)];
			String head = TextCut.prefix(s, t -> SIX.applyAsDouble(t) <= room);
			assertTrue(s.startsWith(head));
			assertTrue(head.isEmpty() || SIX.applyAsDouble(head) <= room);
			int max = f.random.nextInt(120);
			assertTrue(TextCut.limit(s, max).length() <= max);
			String tail = TextCut.suffix(s, t -> t.length() <= max);
			assertTrue(s.endsWith(tail));
			String first = TextCut.first(s);
			assertTrue(s.strip().startsWith(first));
			assertTrue(TextCut.initial(s).length() <= Math.max(TextCut.MAX_INITIAL, 2));
		}));
	}

	@Test
	void linesRowsLoseNoLetterAndEnd() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(51, 400, CASE, (f, i) -> {
			String s = line(f);
			float room = ROOMS[f.random.nextInt(ROOMS.length)];
			List<String> rows = LyricsRenderer.wrap(SIX, s, room);
			assertEquals(noSpaces(s), noSpaces(String.join("", rows)), "letters lost wrapping " + Fuzz.show(s));
			// Measured as drawn (shaped, mirrored); a line of only spaces gives no rows, which the drawing takes as nothing to draw
			List<String> measured = LyricsRenderer.wrap(SPLITTER, Style.EMPTY, s, room);
			assertEquals(noSpaces(s), noSpaces(String.join("", measured)), "letters lost wrapping as drawn " + Fuzz.show(s));
			int[] starts = LyricsRenderer.rowStarts(s, rows);
			int cps = s.codePointCount(0, s.length());
			for (int start : starts) {
				assertTrue(start >= 0 && start <= cps, "row start " + start + " of " + cps);
			}
			LyricsRenderer.LineInfo.of(s, rows);
			for (String row : rows) {
				// Each drawn letter names the letter of the row it came from (marks and direction marks are left out)
				LyricsRenderer.Display d = LyricsRenderer.Display.of(row);
				int letters = row.codePointCount(0, row.length());
				assertEquals(d.cps().length, d.logical().length);
				for (int k : d.logical()) {
					assertTrue(k >= 0 && k < letters, "drawn letter from " + k + " of " + letters + " in " + Fuzz.show(row));
				}
				LyricsRenderer.Display.of(row, f.random.nextBoolean());
				LyricsRenderer.Display.shown(row, f.random.nextBoolean());
				LyricsRenderer.computeWordRanks(row);
				LyricsRenderer.wordCount(row);
			}
		}));
	}

	@Test
	void arcWordsAndRowsCoverTheLineAndEnd() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(52, 800, CASE, (f, i) -> {
			String s = line(f);
			LyricsRenderer.ArcWords split = LyricsRenderer.arcWords(s);
			int n = split.words().size();
			float[] widths = new float[n];
			boolean[] pause = new boolean[n];
			List<TopBarWords.Piece> pieces = new ArrayList<>();
			for (int w = 0; w < n; w++) {
				String word = split.words().get(w);
				widths[w] = (float) SIX.applyAsDouble(LyricsRenderer.Display.shown(word, f.random.nextBoolean()));
				pause[w] = LyricsRenderer.endsWithPause(word);
				pieces.add(new TopBarWords.Piece(word, w == 0 || !split.tight()[w - 1]));
			}
			int[] rows = ArcLayout.rows(widths, pause, split.tight());
			for (int r = 0; r < rows.length; r++) {
				assertTrue(rows[r] >= 0 && rows[r] < Math.max(1, n) && (r == 0 || rows[r] > rows[r - 1]), "row starts " + java.util.Arrays.toString(rows));
			}
			float room = ROOMS[f.random.nextInt(ROOMS.length)];
			List<ArcLayout.Group> groups = ArcLayout.fit(widths, pause, split.tight(), 3f, ArcLayout.seed(i, s),
					TopBarWords.visualRanks(pieces), f.random.nextBoolean() ? room : Float.NaN);
			ArcLayout.focus(groups, f.random.nextFloat() * (n + 2) - 1);
		}));
	}

	@Test
	void worldPiecesCoverTheLineAndEnd() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(53, 1500, CASE, (f, i) -> {
			String s = line(f);
			List<int[]> chunks = WorldWords.chunks(s);
			int cps = s.codePointCount(0, s.length());
			for (int[] c : chunks) {
				assertTrue(c[0] >= 0 && c[1] <= cps && c[0] <= c[1], "chunk " + c[0] + ".." + c[1] + " of " + cps);
			}
			WorldWords.pieces(s, chunks);
			if (!chunks.isEmpty()) {
				int big = WorldWords.bigPiece(s, chunks, f.random.nextFloat());
				assertTrue(big >= -1 && big < chunks.size(), "big piece " + big);
			}
			WorldWords.capitals(s);
			WorldWords.rightToLeft(s);
			WorldWords.titleText(s, f.random.nextBoolean());
			int[] points = s.codePoints().toArray();
			float[] prefix = WorldWords.prefixWidths(points, SIX);
			assertEquals(points.length + 1, prefix.length);
			WorldWords.wholeWidth(points, SIX);
		}));
	}

	@Test
	void topBarAndCardTextEnd() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(54, 1500, CASE, (f, i) -> {
			String s = line(f);
			List<TopBarWords.Piece> pieces = TopBarWords.split(s);
			assertEquals(noSpaces(s.trim()), noSpaces(String.join("", pieces.stream().map(TopBarWords.Piece::text).toList())));
			int[] ranks = TopBarWords.visualRanks(pieces);
			boolean[] seen = new boolean[ranks.length];
			for (int r : ranks) {
				assertTrue(r >= 0 && r < ranks.length && !seen[r], "ranks " + java.util.Arrays.toString(ranks));
				seen[r] = true;
			}
			LineLayout layout = LineLayout.build(s, 1f, 4f, (piece, rtl) -> (float) SIX.applyAsDouble(piece));
			for (int k = 0; k < layout.size(); k++) {
				assertTrue(Float.isFinite(layout.x[k]) && layout.x[k] >= 0 && layout.x[k] <= layout.extent + 1e-3f);
				layout.sung(k, f.random.nextFloat());
				TopBarWords.shown(layout.texts[k], layout.rtl);
			}
			TopBarWords.baseRtl(s);
			NowPlayingHud.dotted(s);
			NowPlayingHud.subText(s, f.nasty(10), f.random.nextBoolean() ? "" : "Demo");
			NowPlayingHud.isRtl(s);
			NowPlayingHud.visual(s, Style.EMPTY);
			NowPlayingHud.wordSeq(s, f.random.nextBoolean(), Style.EMPTY);
			LyricsFont.joined(s);
			LyricsFont.stripMarks(s);
			LyricsFont.ltrNumber(s);
			LyricsFont.ltr(s);
			LyricsFont.width(SPLITTER, Style.EMPTY, s);
			LyricsFont.drawnWidth(SPLITTER, Style.EMPTY, s);
			LyricsRenderer.shownTitle(s);
		}));
	}

	@Test
	void everyParsedLineTimesItsLettersInRange() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(55, 300, CASE, (f, i) -> {
			StringBuilder lrc = new StringBuilder();
			for (int k = 0; k < 20; k++) {
				lrc.append(String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]", f.random.nextInt(100), f.random.nextInt(60), f.random.nextInt(100))).append(f.nasty(30)).append('\n');
			}
			Lyrics l = LrcParser.parseSynced(lrc.toString());
			for (int k = 0; k < l.lines().size(); k++) {
				String text = l.get(k).text();
				int total = text.codePointCount(0, text.length());
				for (long t : new long[] {-1, 0, l.get(k).timeMs(), l.sungEnd(k), l.lineEnd(k), 6_000_000}) {
					float sung = LyricsRenderer.sungChars(l, k, t, total);
					assertTrue(Float.isFinite(sung) && sung >= 0 && sung <= 2 * total, "sung " + sung + " of " + total);
				}
			}
		}));
	}
}
