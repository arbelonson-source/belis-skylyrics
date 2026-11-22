package dev.beli.skylyrics.media;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/** Tidies up song info, mostly for browser players where the "title" is a YouTube video title. */
public final class TitleCleaner {
	private static final Set<String> BROWSERS = Set.of(
			"firefox", "chromium", "chrome", "google-chrome", "brave", "vivaldi", "opera", "edge", "msedge", "zen", "librewolf", "plasma-browser-integration");

	/** "(Official Video)", "[Lyrics]", "(4K Remaster)" … */
	private static final Pattern NOISE = Pattern.compile(
			"\\s*[(\\[][^)\\]]*\\b(official|video|lyrics?|audio|visuali[sz]er|hd|hq|4k|mv|m/v|clip|explicit)\\b[^)\\]]*[)\\]]",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern ARTIST_NOISE = Pattern.compile("(?i)(\\s*-\\s*topic|vevo|\\s+official)$");
	private static final Pattern DASH = Pattern.compile("\\s+[-\u2013\u2014|]\\s+");

	private static final Pattern TOPIC = Pattern.compile("(?i).*\\s-\\s*topic$");
	/**
	 * "Remastered 2011", "Live at …", "Mono", 'From "Film"', "Recorded at …", "Bonus Track": the part after the dash is not a
	 * song name. "From" counts only with a quote or a film or show word after it, so a song named "From The Start" is still one.
	 * The film word has to come before any bracket: in "From Here On (from Film Soundtrack)" it names the song's film, and
	 * "From Here On" is the song.
	 */
	private static final Pattern VERSION_TAG = Pattern.compile(
			"(?i)(\\d{4}\\s+)?(digital\\s+)?remaster(ed)?(\\s+(\\d{4}|version))?|(\\d{4}\\s+)?(mono|stereo)(\\s+(version|mix))?"
					+ "|live|live (at|from|in) .+|radio edit|(single|album|live|acoustic|demo) version|demo|acoustic|instrumental"
					+ "|from\\s*[\"\u201C\u201D'\u2018\u2019\u00AB(\\[].+|from\\s[^()\\[\\]]*?\\b(soundtrack|motion picture|film|movie|series|musical|ost)\\b.*"
					+ "|recorded\\s+(live\\s+)?(at|in|from|for|on)\\s.+|bonus track");

	/** A version word ending the part after the dash ("Levels - Skrillex Remix"): that part is not a song name, so the dash is not "Artist - Song". */
	private static final Pattern VERSION_END = Pattern.compile(
			"(?i).*\\b(remix|mix|edit|version|reverb|sped up|slowed(\\s+down)?|remaster(ed)?(\\s+\\d{4})?)$");

	private static final String NOISE_WORD = "(?:official|music|video|audio|lyrics?|visuali[sz]er|hd|4k|mv|clip)";
	/** Noise words that are never a song name on their own ("Madonna - Music", "India.Arie - Video" are songs). */
	private static final String NOISE_ALONE = "(?:official|lyrics?|visuali[sz]er|hd|4k|mv)";
	/**
	 * " | Official Music Video" or " - Lyrics" at the end: only when every word of it is a noise word, and a single
	 * word only when it can't be a song name.
	 */
	private static final Pattern NOISE_TAIL = Pattern.compile(
			"(?i)\\s+[|\\-\u2013\u2014]\\s+(?:" + NOISE_WORD + "(?:\\s+" + NOISE_WORD + ")+|" + NOISE_ALONE + ")$");

	/** "(feat. X)", "[ft. X]", "(with X)", "(prod. X)", also in fullwidth brackets and 【】. */
	private static final Pattern GUEST_BRACKETS = Pattern.compile(
			"\\s*[(\\[\uFF08\uFF3B\u3010\u3014]\\s*(feat\\.?|ft\\.?|featuring|with|prod\\.?)[\\s\uFF1A:][^)\\]\uFF09\uFF3D\u3011\u3015]*[)\\]\uFF09\uFF3D\u3011\u3015]",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern GUEST_TAIL = Pattern.compile(
			"(?<=\\S)\\s+(feat\\.|ft\\.?|featuring)\\s+\\S.*$", Pattern.CASE_INSENSITIVE);

	private TitleCleaner() {
	}

	/**
	 * The longest title or artist the patterns look at. Titles come from music players and lyrics sites, and some
	 * patterns here backtrack a lot on a long text with an unclosed bracket; real titles are far shorter.
	 */
	public static final int MAX_TEXT = 200;

	/** {@code s} cut to {@link #MAX_TEXT}, never in the middle of a surrogate pair. */
	public static String capped(String s) {
		if (s.length() <= MAX_TEXT) {
			return s;
		}
		int end = Character.isHighSurrogate(s.charAt(MAX_TEXT - 1)) ? MAX_TEXT - 1 : MAX_TEXT;
		return s.substring(0, end);
	}

	/**
	 * The artist without a channel's "- Topic", "VEVO" or " Official" at its end. The pattern reads only the last
	 * {@link #MAX_TEXT} chars, but a longer credit (a charity single's dozen names) is kept whole: it is what the player
	 * says, and a song's saved timing is keyed by it.
	 */
	private static String withoutArtistNoise(String artist) {
		int head = Math.max(0, artist.length() - MAX_TEXT);
		return (artist.substring(0, head) + ARTIST_NOISE.matcher(artist.substring(head)).replaceAll("")).trim();
	}

	public static Track clean(Track track) {
		String title = track.title();
		String artist = withoutArtistNoise(track.artist());

		if (BROWSERS.contains(track.player().toLowerCase(Locale.ROOT))) {
			title = NOISE.matcher(capped(title)).replaceAll("").trim();
			title = NOISE_TAIL.matcher(title).replaceFirst("").trim();
			String[] parts = DASH.split(title, 2);
			// "Artist - Song" video titles; the channel name is usually not the real artist.
			// "Topic" channels already give the real artist and song.
			if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()
					&& !TOPIC.matcher(track.artist()).matches() && !VERSION_TAG.matcher(parts[1].trim()).matches() && !versionEnd(parts[1].trim())) {
				artist = parts[0].trim();
				title = parts[1].trim();
			}
		}
		if (title.equals(track.title()) && artist.equals(track.artist())) {
			return track;
		}
		return new Track(title, artist, track.album(), track.lengthMs(), track.artUrl(), track.player(), track.uri());
	}

	/**
	 * "Skrillex Remix" after the dash names a version, not a song; "Smells Like Teen Spirit - Remastered 2011" (a second dash)
	 * is still "Song - version", so the first dash is "Artist - Song" as usual.
	 */
	private static boolean versionEnd(String afterDash) {
		return !DASH.matcher(afterDash).find() && VERSION_END.matcher(afterDash).matches();
	}

	/** A stricter version used only for looking lyrics up: "Song - Remastered 2011" -> "Song". */
	public static String forSearch(String title) {
		String t = NOISE.matcher(capped(title)).replaceAll("");
		t = NOISE_TAIL.matcher(t.trim()).replaceFirst("");
		t = t.replaceAll("(?i)\\s+[-\u2013\u2014]\\s+(\\d{4}\\s*)?(remaster(ed)?|mono|stereo|single|radio edit|live|version|edit|from .*)\\b.*$", "");
		String bracketed = GUEST_BRACKETS.matcher(t).replaceAll("");
		// A title that is only a credit ("(feat. X)") is all there is to search for.
		t = bracketed.isBlank() ? t : bracketed;
		// "Song feat. X" at the end; needs a song name before it, so "Feel It" and "Lift Me Up" stay whole, and the dot
		// after "feat", so a title like "A Feat Of Strength" does too.
		t = GUEST_TAIL.matcher(t.trim()).replaceFirst("");
		return t.trim();
	}

	/** "(Remastered)", "[Live]", "(Radio Edit)", "(from X)" …: a version of the same song. "remix" and "part N" are not. */
	private static final Pattern VERSION_BRACKET = Pattern.compile(
			"\\s*[(\\[\uFF08\uFF3B\u3010\u3014][^)\\]\uFF09\uFF3D\u3011\u3015]*\\b(remaster(ed)?|live|radio edit|edit|version|mix|mono|stereo|acoustic|demo|from [^)\\]\uFF09\uFF3D\u3011\u3015]+|feat\\.?|ft\\.?|featuring|with|prod\\.?)\\b[^)\\]\uFF09\uFF3D\u3011\u3015]*[)\\]\uFF09\uFF3D\u3011\u3015]",
			Pattern.CASE_INSENSITIVE);

	/**
	 * The search title without version tags in brackets: "Song (2011 Remaster)" -> "Song". A title with a bracket that
	 * names another work or a track without vocals ("(Remix)", "(Part 2)", "(Instrumental Version)") is returned as
	 * {@link #forSearch} gives it.
	 */
	public static String withoutVersionBrackets(String title) {
		String t = forSearch(title);
		if (noVocals(title) || withoutBrackets(title) == null) {
			return t;
		}
		return VERSION_BRACKET.matcher(t).replaceAll("").trim();
	}

	/**
	 * Lowercase letters and digits only, so "Don't Stop" matches "Dont stop". Accents on Latin, Greek and
	 * Cyrillic letters fold away ("Beyonc\u00e9" = "Beyonce"); Arabic and Hebrew vowel marks fold too; marks in other scripts change the word
	 * (Hindi "\u0915\u093e" and "\u0915\u0940", Japanese "\u30d0" and "\u30cf") and stay. Variation selectors (an emoji's
	 * colour style, a Han variant) never change the word, and {@link Track} drops them from a player's title.
	 */
	public static String key(String s) {
		String t = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
		StringBuilder out = new StringBuilder(t.length());
		boolean foldable = false; // the last letter was Latin, Greek or Cyrillic
		for (int i = 0; i < t.length(); ) {
			int c = t.codePointAt(i);
			i += Character.charCount(c);
			if (c >= 0xFE00 && c <= 0xFE0F || c >= 0xE0100 && c <= 0xE01EF) {
				continue;
			}
			if (Character.getType(c) == Character.NON_SPACING_MARK || Character.getType(c) == Character.COMBINING_SPACING_MARK
					|| Character.getType(c) == Character.ENCLOSING_MARK) {
				if (!foldable) {
					out.appendCodePoint(c);
				}
			} else if (Character.isLetterOrDigit(c)) {
				Character.UnicodeScript script = Character.UnicodeScript.of(c);
				// Arabic and Hebrew vowel marks are optional in writing, so they fold away like accents
				foldable = script == Character.UnicodeScript.LATIN || script == Character.UnicodeScript.GREEK
						|| script == Character.UnicodeScript.CYRILLIC || script == Character.UnicodeScript.ARABIC
						|| script == Character.UnicodeScript.HEBREW;
				out.appendCodePoint(c);
			} else {
				foldable = false;
			}
		}
		return out.toString();
	}

	/** What identifies the song: no version tags or credits, no case, accents or punctuation; "&" means "and". */
	static String songKey(String title) {
		String t = VERSION_BRACKET.matcher(forSearch(title)).replaceAll("").replace("&", " and ");
		String k = key(t);
		return k.isEmpty() ? title.strip().toLowerCase(Locale.ROOT) : k; // "!!!" must not equal "???"
	}

	/** Any bracketed part of a title. */
	private static final Pattern ANY_BRACKET = Pattern.compile(
			"\\s*[(\\[\uFF08\uFF3B\u3010\u3014]([^)\\]\uFF09\uFF3D\u3011\u3015]*)[)\\]\uFF09\uFF3D\u3011\u3015]");
	/** Brackets that make it another work, so they are never ignored. */
	private static final Pattern OTHER_WORK = Pattern.compile("(?i)\\b(remix|part|pt|reprise|interlude|intro|outro|skit|cover)\\b");

	/**
	 * Brackets naming a track without the singing (or only the singing): another recording, with other lyrics or none. "Inst."
	 * is how K-pop and J-pop releases name the instrumental; NetEase uploads say 伴奏 (backing track) or 纯音乐 (pure music),
	 * Japanese ones カラオケ, オフボーカル or インスト. Those words have no word edges to look for, so they count anywhere in a bracket.
	 */
	private static final Pattern NO_VOCALS = Pattern.compile("(?i)\\b(instrumental|inst|karaoke|backing track|a ?cap+ella|off[ -]vocal)\\b"
			+ "|伴奏|纯音乐|カラオケ|オフボーカル|インスト");

	private static boolean noVocals(String title) {
		Matcher m = ANY_BRACKET.matcher(capped(title));
		while (m.find()) {
			if (NO_VOCALS.matcher(m.group(1)).find()) {
				return true;
			}
		}
		return false;
	}

	/** Whether a lyrics result titled {@code found} is the song {@code wanted}. A blank title is never a match. */
	public static boolean sameSong(String wanted, String found) {
		if (found.isBlank()) {
			return false;
		}
		if (noVocals(wanted) != noVocals(found)) {
			return false; // "Song (Instrumental)" is not "Song", whatever else matches
		}
		if (songKey(wanted).equals(songKey(found))) {
			return true;
		}
		// A bracket only one side has, often the title's translation or romanisation ("夜に駆ける (Yoru ni Kakeru)"),
		// doesn't make it another song; one that names another work ("(Remix)", "(Part 2)") does
		String w = withoutBrackets(wanted);
		String f = withoutBrackets(found);
		return w != null && f != null && !w.isBlank() && !f.isBlank() && songKey(w).equals(songKey(f));
	}

	/** The title without its bracketed parts, or null when one of them names another work. */
	static @Nullable String withoutBrackets(String title) {
		Matcher m = ANY_BRACKET.matcher(capped(title));
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			if (OTHER_WORK.matcher(m.group(1)).find()) {
				return null;
			}
			m.appendReplacement(out, "");
		}
		m.appendTail(out);
		return out.toString();
	}

	/** Longest artist credit a title card shows whole. */
	static final int SHOWN_ARTIST_MAX = 32;
	private static final Pattern CREDIT_SPLIT = Pattern.compile("(?i)\\s*,(?!\\s*the\\s)\\s*|\\s+(feat\\.?|ft\\.?)\\s+");

	/**
	 * The artist as a title card shows it. Players list several artists as "A, B, C", but band names
	 * hold commas and "&" too ("Simon & Garfunkel", "Tyler, The Creator"), so a short credit is shown
	 * whole and only a long list is cut to its first name.
	 */
	public static String shownArtist(String artist) {
		String a = artist.trim();
		return a.length() <= SHOWN_ARTIST_MAX ? a : CREDIT_SPLIT.split(a, 2)[0].trim();
	}

	private static final Pattern CREDIT_JOIN = Pattern.compile("\\s*(,|&|\\bfeat\\.?|\\bft\\.?|\\bx\\b)\\s*");

	/**
	 * First artist only: "A, B & C" -> "A". A name that starts with one of the joining signs ("&Nova") keeps it: cut there
	 * it would be empty, and a lookup by an empty artist takes any band's song of that title.
	 */
	public static String mainArtist(String artist) {
		String a = artist.trim();
		Matcher m = CREDIT_JOIN.matcher(a);
		while (m.find()) {
			if (m.start() > 0) {
				return a.substring(0, m.start()).trim();
			}
		}
		return a;
	}
}
