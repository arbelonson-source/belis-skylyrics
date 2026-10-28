package dev.beli.skylyrics.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses LRC text ("[01:23.45]words") into {@link Lyrics}. */
public final class LrcParser {
	private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
	private static final Pattern OFFSET = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
	// Word-level timing from "enhanced" LRC, e.g. <00:12.34>
	private static final Pattern WORD_TIME = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");
	// Also old Mac files that end lines with a bare \r
	private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\r|\\n");
	// A whole line like "[Chorus]" or "[Verse 2: Someone]" names a part of the song, it isn't sung
	private static final Pattern SECTION = Pattern.compile("\\[[^\\[\\]]*]");
	private static final char BOM = 0xFEFF;
	// A timed line of only music notes, like "♪" or "♫ ♫", marks a break in the singing
	private static final Pattern NOTES = Pattern.compile("[\\s♩♪♫♬]*");

	private LrcParser() {
	}

	public static Lyrics parseSynced(String lrc) {
		if (lrc == null || lrc.isBlank()) {
			return Lyrics.NONE;
		}
		long offset = 0;
		List<LyricLine> lines = new ArrayList<>();
		for (String raw : lines(lrc)) {
			raw = raw.stripLeading();
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
			while (m.find() && m.start() == end) {
				times.add(toMillis(m.group(1), m.group(2), m.group(3)));
				end = m.end();
			}
			if (times.isEmpty()) {
				continue;
			}
			String text = WORD_TIME.matcher(raw.substring(end)).replaceAll("").strip();
			if (NOTES.matcher(text).matches() || SECTION.matcher(text).matches()) {
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
		return new Lyrics(collapseBreaks(shifted), true, false);
	}

	/** Spreads unsynced lyrics evenly across the song so they still move along with it. */
	public static Lyrics parsePlain(String text, long durationMs) {
		if (text == null || text.isBlank()) {
			return Lyrics.NONE;
		}
		List<String> rows = new ArrayList<>();
		for (String raw : lines(text)) {
			String row = raw.strip();
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

	private static long toMillis(String min, String sec, String frac) {
		long ms = Long.parseLong(min) * 60_000L + Long.parseLong(sec) * 1000L;
		if (frac != null) {
			// "5" = 500ms, "45" = 450ms, "456" = 456ms
			String padded = (frac + "00").substring(0, 3);
			ms += Long.parseLong(padded);
		}
		return ms;
	}
}
