package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * What the mod writes above the hotbar, in chat or in a pop-up is read out by the Narrator too. Minecraft hands its own
 * messages there to the Narrator itself (setOverlayMessage and addMessage don't), so a key that turned the lyrics off
 * or moved their timing, or the line about an update, said nothing to a player who can't see them.
 */
class NarratedMessagesTest {
	/** Every way the mod shows a message, in each version's spelling. */
	private static final Pattern SHOWN = Pattern.compile("setOverlayMessage\\(|getChat\\(\\)\\.addMessage\\(|addClientSystemMessage\\(|SystemToast\\.addOrUpdate\\(");
	/** How far below it, in lines, the Narrator must be given the words (past the other versions' spellings). */
	private static final int WITHIN = 6;

	@Test
	void everyMessageTheModShowsIsHandedToTheNarrator() throws IOException {
		List<String> silent = new ArrayList<>();
		int shown = 0;
		try (Stream<Path> files = Files.walk(Path.of("src/client/java"))) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
				for (int i = 0; i < lines.size(); i++) {
					String line = lines.get(i).strip();
					if (line.startsWith("//") || line.startsWith("* ") || line.equals("*") || !SHOWN.matcher(line).find()) {
						continue;
					}
					shown++;
					boolean said = false;
					for (int k = i; k < Math.min(lines.size(), i + 1 + WITHIN) && !said; k++) {
						said = lines.get(k).contains("getNarrator().say") || lines.get(k).contains("narrate(client, ");
					}
					if (!said) {
						silent.add(file.getFileName() + ":" + (i + 1) + "  " + line);
					}
				}
			}
		}
		assertTrue(shown > 0, "found none: the search no longer matches how messages are shown");
		assertTrue(silent.isEmpty(), "shown but not read out:\n" + String.join("\n", silent));
	}
}
