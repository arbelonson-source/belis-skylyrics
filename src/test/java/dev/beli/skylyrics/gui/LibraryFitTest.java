package dev.beli.skylyrics.gui;

import static dev.beli.skylyrics.gui.TranslationFitTest.languages;
import static dev.beli.skylyrics.gui.TranslationFitTest.text;
import static dev.beli.skylyrics.gui.TranslationFitTest.width;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The song library's texts in every language, measured with the game's own letter widths (see {@link TranslationFitTest})
 * in the smallest window the game gives (320 wide) and at 426 wide (a 1280x720 window at GUI scale 3).
 */
class LibraryFitTest {
	private static final int SMALLEST = 320;
	private static final int SMALL = 426;

	@BeforeAll
	static void readTheGameFont() throws IOException {
		TranslationFitTest.readTheGameFont();
	}

	/** A text of a language by its English, with {@code %s} filled in, as the game shows it. */
	private static String shown(Map<String, Map<String, String>> all, String lang, String english, Object... args) {
		String key = all.get("en_us.json").entrySet().stream().filter(e -> e.getValue().equals(english)).map(Map.Entry::getKey)
				.findFirst().orElseThrow(() -> new AssertionError("no such text: " + english));
		return String.format(Locale.ROOT, text(all, lang, key), args);
	}

	@Test
	void aPlaylistCardShowsItsWholeSongCount() throws IOException {
		Map<String, Map<String, String>> all = languages();
		List<String> cut = new ArrayList<>();
		int measured = 0;
		for (int screenW : new int[] {SMALLEST, SMALL}) {
			int cell = LibraryGrid.cellWidth(LibraryScreen.viewWidth(LibraryScreen.panelW(screenW)));
			for (String lang : all.keySet()) {
				// The made-up playlists have 7 to 14 songs; Spotify ones can have thousands
				for (int songs : new int[] {1, 2, 7, 13, 100, 1000, 10000}) {
					String words = songs == 1 ? shown(all, lang, "1 song") : shown(all, lang, "%s songs", songs);
					if (width(words) < 0) {
						continue;
					}
					measured++;
					String card = LibraryScreen.songCount(words, songs, cell, TranslationFitTest::width);
					// The whole words (Hebrew says "one song" in letters), or something short with the number in it
					if (width(card) > cell || !card.equals(words) && !card.contains(String.valueOf(songs))) {
						cut.add(screenW + " wide, " + lang + " \"" + words + "\" " + width(words) + " for " + cell);
					}
				}
			}
		}
		assertTrue(measured >= 200, "only " + measured + " counts measured");
		assertTrue(cut.isEmpty(), cut.size() + " playlist cards cut their song count short with \"…\", like: " + cut.subList(0, Math.min(10, cut.size())));
	}

	@Test
	void whyTheSongsDidntLoadShowsEveryWordInsideTheView() throws IOException {
		Map<String, Map<String, String>> all = languages();
		net.minecraft.client.StringSplitter splitter = new net.minecraft.client.StringSplitter((cp, style) -> Math.max(0, width(Character.toString(cp))));
		// The lowest the songs get: a screen 240 high (the least any GUI scale gives) with the two-line sign-in strip above them
		int middle = LibraryScreen.viewHeight(240, LibraryViews.BANNER_TWO) / 2;
		List<String> cut = new ArrayList<>();
		int measured = 0;
		for (int screenW : new int[] {SMALLEST, SMALL}) {
			int room = LibraryScreen.errorRoom(LibraryScreen.viewWidth(LibraryScreen.panelW(screenW)));
			for (String lang : all.keySet()) {
				// What SpotifyLibrary.errorText says, itself or passed on from Spotify's answer or the sign-in
				List<String> errors = List.of(shown(all, lang, "Sign in to Spotify on the Music tab to see your songs here"),
						shown(all, lang, "Can't reach the internet or Spotify"), shown(all, lang, "Something went wrong, try again"),
						shown(all, lang, "Spotify didn't answer, try again"),
						shown(all, lang, "Spotify blocked this app. On the Spotify dashboard, add your account under User Management"),
						shown(all, lang, "Spotify needs Premium on the account that created your Spotify app"),
						shown(all, lang, "Sign out and sign in again on the Music tab to use the song library"),
						shown(all, lang, "Spotify signed you out, sign in again"),
						shown(all, lang, "Spotify no longer accepts your Client ID, check it and sign in again"),
						shown(all, lang, "Spotify is having problems right now"),
						shown(all, lang, "Spotify is getting too many requests, wait a moment"));
				for (String error : errors) {
					if (width(error) < 0) {
						continue;
					}
					measured++;
					List<String> lines = LibraryScreen.errorLines(splitter, error, room);
					String said = String.join("", lines).replaceAll("\\s", "");
					boolean fits = lines.stream().allMatch(l -> width(l) <= room);
					// Its lines stand above the Try again button under the middle of the view; the first must not be above the view
					boolean inView = LibraryScreen.errorTop(middle, lines.size()) >= 0;
					if (!said.equals(error.replaceAll("\\s", "")) || !fits || !inView) {
						cut.add(screenW + " wide, " + lang + " \"" + String.join(" / ", lines) + "\" (" + width(error) + " for " + room + ")");
					}
				}
			}
		}
		assertTrue(measured >= 200, "only " + measured + " errors measured");
		assertTrue(cut.isEmpty(), cut.size() + " library errors are cut short, like: " + cut.subList(0, Math.min(8, cut.size())));
	}

	@Test
	void theSignInStripAboveTheSongsHeardLatelyShowsEveryWord() throws IOException {
		Map<String, Map<String, String>> all = languages();
		net.minecraft.client.StringSplitter splitter = new net.minecraft.client.StringSplitter((cp, style) -> Math.max(0, width(Character.toString(cp))));
		List<String> cut = new ArrayList<>();
		int measured = 0;
		for (int screenW : new int[] {SMALLEST, SMALL}) {
			for (String lang : all.keySet()) {
				int button = width(shown(all, lang, "Open Music tab"));
				if (button < 0) {
					continue;
				}
				int room = LibraryScreen.bannerRoom(screenW, LibraryScreen.bannerButtonW(button));
				// Not signed in, and signed in before the song library needed more of Spotify
				for (String text : List.of(shown(all, lang, "Sign in to Spotify on the Music tab to see your songs here"),
						shown(all, lang, "Sign in to Spotify again so SkyLyrics can see your songs"))) {
					if (width(text) < 0) {
						continue;
					}
					measured++;
					List<net.minecraft.network.chat.FormattedText> lines = splitter.splitLines(text, room, net.minecraft.network.chat.Style.EMPTY);
					// The strip is two lines high at most (LibraryViews.BANNER_TWO): a third line's words would end the second in "…"
					if (lines.size() > 2) {
						cut.add(screenW + " wide, " + lang + " \"" + text + "\" needs " + lines.size() + " lines of " + room);
					}
				}
			}
		}
		assertTrue(measured >= 30, "only " + measured + " strips measured");
		assertTrue(cut.isEmpty(), "the sign-in strip ends its second line in \"…\": " + cut);
	}
}
