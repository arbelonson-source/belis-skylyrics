package dev.beli.skylyrics.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses LRC text ("[01:23.45]words") into {@link Lyrics}. */
public final class LrcParser {
	private static final Pattern TIME = Pattern.compile("\\[\\s*(?:(\\d{1,2}):(?=\\d{1,3}:\\d{1,2}\\.))?(\\d{1,3}):(\\d{1,2})(?:[.:](\\d+))?\\s*]");
	private static final Pattern OFFSET = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
	// Word-level timing from "enhanced" LRC, e.g. <00:12.34>
	private static final Pattern WORD_TIME = Pattern.compile("<(?:\\d{1,2}:)?\\d{1,3}:\\d{1,2}(?:[.:]\\d+)?>");
	// Also old Mac files that end lines with a bare \r
	private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\r|\\n|\\u2028|\\u2029");
	// A whole line like "[Chorus]" or "[Verse 2: Someone]" names a part of the song, it isn't sung
	private static final Pattern SECTION = Pattern.compile("\\[[^\\[\\]]*]");
	// Whole line that is only an instrumental marker: "(Instrumental)", "[music]", "Instrumental". A bare "Music" stays a
	// lyric, since a song can sing just that word; only the bracketed form is a marker
	private static final Pattern MARKER = Pattern.compile(
			"[(\\[（【]\\s*(?:instrumental(?: break| interlude)?|music|musique|musica)\\s*[)\\]）】]|instrumental(?: break| interlude)?",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern FULL_WIDTH = Pattern.compile("［[\\d：．.:]+］");
	private static final char BOM = 0xFEFF;
	// A timed line of only music notes, like "♪" or "♫ ♫", marks a break in the singing
	private static final String NOTE_CHARS = "[\\s♩♪♫♬\\x{1F3B5}\\x{1F3B6}]";
	private static final Pattern NOTES = Pattern.compile(NOTE_CHARS + "*");
	// "(Intro)", "[Outro]", "(Guitar Solo)": a whole bracketed line naming a part without words
	private static final Pattern PART_MARKER = Pattern.compile(
			"[(\\[（【]\\s*(?:intro|outro|interlude|solo|[\\p{L}]+ solo|instrumental(?: break| interlude)?)\\s*[)\\]）】]", Pattern.CASE_INSENSITIVE);
	// A part tag in front of sung words: "[Chorus] words", "[Verse 1: Someone] words"
	private static final Pattern LEADING_TAG = Pattern.compile(
			"^\\[\\s*(?:pre-?chorus|post-?chorus|chorus|verse|bridge|hook|outro|intro|refrain|interlude|breakdown)(?:\\s*\\d+)?(?:\\s*:[^\\[\\]]*]|\\s*])\\s*(?=\\S)",
			Pattern.CASE_INSENSITIVE);
	// Lyrics copied from websites often use thin, wide or no-break spaces between words, which the floating words
	// would not split on; any run of them becomes one plain space
	private static final Pattern SPACES = Pattern.compile("[\\s\\p{Zs}]+");
	// Zero-width spaces and stray byte order marks are invisible but would sit inside words
	private static final Pattern INVISIBLE = Pattern.compile("[\\u200B\\u2060\\uFEFF]");

	// Control characters would show as boxes (tab becomes a space with the other spaces)
	private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\t]]");
	private static final Pattern LEADING = Pattern.compile("^[\\s\\p{Zs}\\u200B\\u2060\\uFEFF]+");

	private LrcParser() {
	}

	private static boolean isNote(int cp) {
		return cp == '\u2669' || cp == '\u266A' || cp == '\u266B' || cp == '\u266C' || cp == 0x1F3B5 || cp == 0x1F3B6;
	}

	/** "♪ instrumental ♪": the marker word with notes on at least one side and nothing else (plain code: no backtracking on hostile text). */
	static boolean notesAroundWord(String text) {
		int start = 0;
		int end = text.length();
		boolean note = false;
		while (start < end) {
			int cp = text.codePointAt(start);
			if (!Character.isWhitespace(cp) && !isNote(cp)) {
				break;
			}
			note |= isNote(cp);
			start += Character.charCount(cp);
		}
		while (end > start) {
			int cp = text.codePointBefore(end);
			if (!Character.isWhitespace(cp) && !isNote(cp)) {
				break;
			}
			note |= isNote(cp);
			end -= Character.charCount(cp);
		}
		String word = text.substring(start, end);
		return note && (word.equalsIgnoreCase("instrumental") || word.equalsIgnoreCase("music"));
	}

	public static Lyrics parseSynced(String lrc) {
		if (lrc == null || lrc.isBlank()) {
			return Lyrics.NONE;
		}
		long offset = 0;
		List<LyricLine> lines = new ArrayList<>();
		for (String raw : lines(lrc)) {
			raw = FULL_WIDTH.matcher(LEADING.matcher(raw).replaceFirst("")).replaceAll(r -> r.group().replace('［', '[').replace('］', ']').replace('：', ':').replace('．', '.'));
			// Only a line that starts with the tag sets it; a broken huge number is ignored instead of losing the lyrics
			Matcher off = OFFSET.matcher(raw);
			if (off.lookingAt()) {
				try {
					offset = Long.parseLong(off.group(1));
				} catch (NumberFormatException ignored) {
				}
				continue;
			}
			Matcher m = TIME.matcher(raw);
			List<Long> times = new ArrayList<>();
			int end = 0;
			// Some files put spaces between the stamps of a repeated line: "[00:05] [00:30] text"
			while (m.find() && raw.substring(end, m.start()).isBlank()) {
				times.add(toMillis(m.group(1), m.group(2), m.group(3), m.group(4)));
				end = m.end();
			}
			if (times.isEmpty()) {
				continue;
			}
			String text = tidy(TIME.matcher(WORD_TIME.matcher(raw.substring(end)).replaceAll("")).replaceAll(" "));
			text = LEADING_TAG.matcher(text).replaceFirst("");
			if (NOTES.matcher(text).matches() || notesAroundWord(text) || PART_MARKER.matcher(text).matches() || SECTION.matcher(text).matches() || MARKER.matcher(text).matches()) {
				text = "";
			}
			for (long t : times) {
				lines.add(new LyricLine(t, text));
			}
		}
		if (lines.isEmpty()) {
			return Lyrics.NONE;
		}
		// A positive [offset:] means lyrics should show earlier
		long shift = -offset;
		List<LyricLine> shifted = new ArrayList<>(lines.size());
		for (LyricLine line : lines) {
			shifted.add(new LyricLine(Math.max(0, line.timeMs() + shift), line.text()));
		}
		shifted.sort(Comparator.comparingLong(LyricLine::timeMs));
		// Stamps with nothing but notes or blanks (some sites do this for songs without words) is an instrumental
		if (shifted.stream().allMatch(LyricLine::isBreak)) {
			return Lyrics.INSTRUMENTAL;
		}
		return new Lyrics(collapseBreaks(joinSameStamp(shifted)), true, false);
	}

	/** Spreads unsynced lyrics evenly across the song so they still move along with it. */
	public static Lyrics parsePlain(String text, long durationMs) {
		if (text == null || text.isBlank()) {
			return Lyrics.NONE;
		}
		List<String> rows = new ArrayList<>();
		for (String raw : lines(text)) {
			String row = tidy(raw);
			if (!SECTION.matcher(row).matches()) {
				rows.add(NOTES.matcher(row).matches() ? "" : row);
			}
		}
		// Blank lines before the first words or after the last would only be an empty pause
		while (!rows.isEmpty() && rows.getFirst().isEmpty()) {
			rows.removeFirst();
		}
		while (!rows.isEmpty() && rows.getLast().isEmpty()) {
			rows.removeLast();
		}
		if (rows.isEmpty()) {
			return Lyrics.NONE;
		}
		long length = durationMs > 0 ? durationMs : rows.size() * 3500L;
		long intro = Math.min(8000, length / 12);
		long step = Math.max(1200, (length - intro) / rows.size());
		List<LyricLine> lines = new ArrayList<>(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			lines.add(new LyricLine(intro + i * step, rows.get(i)));
		}
		return new Lyrics(collapseBreaks(lines), false, false);
	}

	private static String[] lines(String text) {
		// A byte order mark in front would hide the first timestamp
		if (text.charAt(0) == BOM) {
			text = text.substring(1);
		}
		return LINE_BREAK.split(text);
	}

	static String tidy(String text) {
		return SPACES.matcher(CONTROL.matcher(INVISIBLE.matcher(text).replaceAll("")).replaceAll("")).replaceAll(" ").strip();
	}

	/** At most this many different lines share one stamp as one line: an original, its romanization and a translation. */
	static final int MAX_JOINED = 3;
	/** How far apart lines are put when a file stamps many lines alike and nothing comes after them. */
	static final long SPREAD_MS = 3500;

	/**
	 * Lines that share a stamp (an original and its translation, or a repeated stamp) are shown together as one line,
	 * "first / second": the line being sung is found by time alone, so the first of two equal stamps would never show.
	 * A repeat of the same words stays single, and a break stamped with words yields to the words. More than
	 * {@link #MAX_JOINED} different lines on one stamp is a file whose stamps mean nothing (every line at 00:00, say):
	 * those are spread out up to the next stamp instead of becoming one huge line.
	 */
	private static List<LyricLine> joinSameStamp(List<LyricLine> lines) {
		List<LyricLine> out = new ArrayList<>(lines.size());
		for (int i = 0; i < lines.size(); ) {
			long t = lines.get(i).timeMs();
			int j = i;
			List<String> texts = new ArrayList<>();
			for (; j < lines.size() && lines.get(j).timeMs() == t; j++) {
				LyricLine line = lines.get(j);
				if (!line.isBreak() && !texts.contains(line.text())) {
					texts.add(line.text());
				}
			}
			if (texts.isEmpty()) {
				out.add(lines.get(i));
			} else if (texts.size() <= MAX_JOINED) {
				out.add(new LyricLine(t, String.join(" / ", texts)));
			} else {
				long next = j < lines.size() ? lines.get(j).timeMs() : t + texts.size() * SPREAD_MS;
				long step = Math.max(1, (next - t) / texts.size());
				for (int k = 0; k < texts.size(); k++) {
					out.add(new LyricLine(t + k * step, texts.get(k)));
				}
			}
			i = j;
		}
		return out;
	}

	private static List<LyricLine> collapseBreaks(List<LyricLine> lines) {
		List<LyricLine> out = new ArrayList<>(lines.size());
		for (LyricLine line : lines) {
			// Several empty lines in a row are one break
			if (line.isBreak() && !out.isEmpty() && out.getLast().isBreak()) {
				continue;
			}
			out.add(line);
		}
		return out;
	}

	private static long toMillis(String hour, String min, String sec, String frac) {
		long ms = (hour == null ? 0 : Long.parseLong(hour) * 3_600_000L) + Long.parseLong(min) * 60_000L + Long.parseLong(sec) * 1000L;
		if (frac != null) {
			// "5" = 500ms, "45" = 450ms, "456" = 456ms
			String padded = (frac + "00").substring(0, 3);
			ms += Long.parseLong(padded);
		}
		return ms;
	}
}
