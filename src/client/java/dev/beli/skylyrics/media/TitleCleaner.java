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
	/** "Remastered 2011", "Live at …", "Mono": the part after the dash is not a song name. */
	private static final Pattern VERSION_TAG = Pattern.compile(
			"(?i)(\\d{4}\\s+)?(digital\\s+)?remaster(ed)?(\\s+(\\d{4}|version))?|(\\d{4}\\s+)?(mono|stereo)(\\s+(version|mix))?"
					+ "|live|live (at|from|in) .+|radio edit|(single|album|live|acoustic|demo) version|demo|acoustic|instrumental");

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

	public static Track clean(Track track) {
		String title = track.title();
		String artist = ARTIST_NOISE.matcher(capped(track.artist())).replaceAll("").trim();

		if (BROWSERS.contains(track.player().toLowerCase(Locale.ROOT))) {
			title = NOISE.matcher(capped(title)).replaceAll("").trim();
			String[] parts = DASH.split(title, 2);
			// "Artist - Song" video titles; the channel name is usually not the real artist.
			// "Topic" channels already give the real artist and song.
			if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()
					&& !TOPIC.matcher(track.artist()).matches() && !VERSION_TAG.matcher(parts[1].trim()).matches()) {
				artist = parts[0].trim();
				title = parts[1].trim();
			}
		}
		if (title.equals(track.title()) && artist.equals(track.artist())) {
			return track;
		}
		return new Track(title, artist, track.album(), track.lengthMs(), track.artUrl(), track.player(), track.uri());
	}

	/** A stricter version used only for looking lyrics up: "Song - Remastered 2011" -> "Song". */
	public static String forSearch(String title) {
		String t = NOISE.matcher(capped(title)).replaceAll("");
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

	/** Lowercase letters and digits only, so "Don't Stop" matches "Dont stop". */
	public static String key(String s) {
		return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
	}

	/** What identifies the song: no version tags or credits, no case, accents or punctuation; "&" means "and". */
	static String songKey(String title) {
		String t = VERSION_BRACKET.matcher(forSearch(title)).replaceAll("").replace("&", " and ");
		t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		String k = key(t);
		return k.isEmpty() ? title.strip().toLowerCase(Locale.ROOT) : k; // "!!!" must not equal "???"
	}

	/** Any bracketed part of a title. */
	private static final Pattern ANY_BRACKET = Pattern.compile(
			"\\s*[(\\[\uFF08\uFF3B\u3010\u3014]([^)\\]\uFF09\uFF3D\u3011\u3015]*)[)\\]\uFF09\uFF3D\u3011\u3015]");
	/** Brackets that make it another work, so they are never ignored. */
	private static final Pattern OTHER_WORK = Pattern.compile("(?i)\\b(remix|part|pt|reprise|interlude|intro|outro|skit|cover)\\b");

	/** Brackets naming a track without the singing (or only the singing): another recording, with other lyrics or none. */
	private static final Pattern NO_VOCALS = Pattern.compile("(?i)\\b(instrumental|karaoke|backing track|a ?cap+ella|off[ -]vocal)\\b");

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

	/** First artist only: "A, B & C" -> "A". */
	public static String mainArtist(String artist) {
		String[] parts = artist.split("\\s*(,|&|\\bfeat\\.?|\\bft\\.?|\\bx\\b)\\s*", 2);
		return parts[0].trim();
	}
}
