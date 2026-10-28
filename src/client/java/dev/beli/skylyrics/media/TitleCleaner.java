package dev.beli.skylyrics.media;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

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

	private TitleCleaner() {
	}

	public static Track clean(Track track) {
		String title = track.title();
		String artist = ARTIST_NOISE.matcher(track.artist()).replaceAll("").trim();

		if (BROWSERS.contains(track.player().toLowerCase(Locale.ROOT))) {
			title = NOISE.matcher(title).replaceAll("").trim();
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
		return new Track(title, artist, track.album(), track.lengthMs(), track.artUrl(), track.player());
	}

	/** A stricter version used only for looking lyrics up: "Song - Remastered 2011" -> "Song". */
	public static String forSearch(String title) {
		String t = NOISE.matcher(title).replaceAll("");
		t = t.replaceAll("(?i)\\s*[-\u2013]\\s*(\\d{4}\\s*)?(remaster(ed)?|mono|stereo|single|radio edit|live|version|edit|from .*)\\b.*$", "");
		t = t.replaceAll("(?i)\\s*[(\\[](feat\\.?|ft\\.?|with|prod\\.?)\\s[^)\\]]*[)\\]]", "");
		return t.trim();
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
