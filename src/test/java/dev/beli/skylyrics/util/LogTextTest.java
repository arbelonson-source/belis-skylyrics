package dev.beli.skylyrics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.AccessDeniedException;
import org.junit.jupiter.api.Test;

class LogTextTest {
	@Test
	void linuxHomeBecomesTilde() {
		assertEquals("~/.config/skylyrics.json", LogText.redact("/home/alice/.config/skylyrics.json", "/home/alice"));
	}

	@Test
	void windowsHomeBecomesTilde() {
		assertEquals("~\\AppData\\skylyrics.json", LogText.redact("C:\\Users\\Alice\\AppData\\skylyrics.json", "C:\\Users\\Alice"));
	}

	@Test
	void windowsHomeWithForwardSlashesInTheText() {
		assertEquals("~/AppData/x.json", LogText.redact("C:/Users/Alice/AppData/x.json", "C:\\Users\\Alice"));
	}

	@Test
	void macHomeBecomesTilde() {
		assertEquals("~/Library/skylyrics.json", LogText.redact("/Users/alice/Library/skylyrics.json", "/Users/alice"));
	}

	@Test
	void pathOutsideHomeStaysAsIs() {
		assertEquals("/opt/game/skylyrics.json", LogText.redact("/opt/game/skylyrics.json", "/home/alice"));
	}

	@Test
	void anotherNameStartingTheSameIsNotCut() {
		assertEquals("/home/alicia/x", LogText.redact("/home/alicia/x", "/home/alice"));
		assertEquals("/home/bobby/x", LogText.redact("/home/bobby/x", "/home/bob"));
		assertEquals("~/x", LogText.redact("/home/bob/x", "/home/bob"));
	}

	@Test
	void homeAloneAndInTheMiddleOfText() {
		assertEquals("~", LogText.redact("/home/alice", "/home/alice"));
		assertEquals("Couldn't read ~/a.json: denied", LogText.redact("Couldn't read /home/alice/a.json: denied", "/home/alice"));
	}

	@Test
	void homeWithTrailingSeparatorAndEveryOccurrence() {
		assertEquals("a ~/x and ~/y", LogText.redact("a /home/alice/x and /home/alice/y", "/home/alice/"));
	}

	@Test
	void missingOrRootHomeChangesNothing() {
		assertEquals("/x/y", LogText.redact("/x/y", null));
		assertEquals("/x/y", LogText.redact("/x/y", ""));
		assertEquals("/x/y", LogText.redact("/x/y", "/"));
	}

	@Test
	void exceptionKeepsTypeAndFileNameButNotTheHome() {
		String home = System.getProperty("user.home");
		String text = LogText.of(new AccessDeniedException(home + "/.config/skylyrics.json"));
		assertTrue(text.startsWith("java.nio.file.AccessDeniedException: ~"), text);
		assertTrue(text.endsWith("skylyrics.json"), text);
		assertFalse(text.contains(home), text);
	}

	@Test
	void gateLogsOnceUntilReset() {
		OnceGate gate = new OnceGate();
		assertTrue(gate.shouldLog());
		assertFalse(gate.shouldLog());
		assertFalse(gate.shouldLog());
		gate.reset();
		assertTrue(gate.shouldLog());
		assertFalse(gate.shouldLog());
	}

	@Test
	void theLogKeepsTheTechnicalWordsBehindAPlainSentence() {
		String home = System.getProperty("user.home");
		Exception e = new java.io.IOException("Can't reach the internet or Spotify",
				new java.net.SocketException("Connection reset", new java.io.IOException(home + "/x")));
		assertEquals("java.io.IOException: Can't reach the internet or Spotify, caused by java.net.SocketException: Connection reset,"
				+ " caused by java.io.IOException: ~/x", LogText.chain(e));
		assertEquals("java.io.IOException", LogText.chain(new java.io.IOException()));
	}
}
