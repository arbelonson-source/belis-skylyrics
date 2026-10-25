package dev.beli.skylyrics.lyrics;

/** One line of lyrics. {@code timeMs} is when the line starts; blank text means an instrumental break. */
public record LyricLine(long timeMs, String text) {
	public boolean isBreak() {
		return text.isBlank();
	}
}
