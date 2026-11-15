package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TrPlaceholderTest {
	/** Tr.t with no arguments does not fill a "%s" in; vanilla then says "Format error: ...". Every "%s" text needs its values. */
	@Test
	void noTranslatedTextWithAPlaceholderIsAskedForWithoutValues() throws IOException {
		Pattern bare = Pattern.compile("\\bTr\\.t\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");
		List<String> bad = new ArrayList<>();
		try (Stream<Path> files = Files.walk(Path.of("src/client/java"))) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				Matcher m = bare.matcher(Files.readString(file, StandardCharsets.UTF_8));
				while (m.find()) {
					if (m.group(1).contains("%")) {
						bad.add(file.getFileName() + ": " + m.group(1));
					}
				}
			}
		}
		assertEquals(List.of(), bad);
	}
}
