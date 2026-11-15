package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorMessageTest {
	@Test
	void errorShowsTheMessageWithoutTheClassName() {
		Exception e = new java.io.IOException("Allow Minecraft in System Settings");
		String shown = MediaTracker.errorText(e.getMessage(), "fallback");
		assertEquals("Allow Minecraft in System Settings", shown);
		assertFalse(shown.contains("IOException"));
	}

	@Test
	void emptyMessageFallsBack() {
		assertEquals("fallback", MediaTracker.errorText(null, "fallback"));
		assertEquals("fallback", MediaTracker.errorText("", "fallback"));
		assertEquals("fallback", MediaTracker.errorText("   ", "fallback"));
	}

	@Test
	void flatpakIsFoundFromTheEnvironmentOrTheInfoFile() {
		assertTrue(MprisSource.inFlatpak(Map.of("FLATPAK_ID", "org.prismlauncher.PrismLauncher"), false));
		assertTrue(MprisSource.inFlatpak(Map.of(), true));
		assertFalse(MprisSource.inFlatpak(Map.of(), false));
		assertFalse(MprisSource.inFlatpak(Map.of("FLATPAK_ID", ""), false));
	}

	@Test
	void flatpakAppIdIsTheRealOneWhenKnown() {
		assertEquals("org.prismlauncher.PrismLauncher", MprisSource.flatpakAppId(Map.of("FLATPAK_ID", "org.prismlauncher.PrismLauncher")));
		assertEquals("<app id>", MprisSource.flatpakAppId(Map.of()));
	}

	@Test
	void busFailureMessageNamesTheFix() {
		String flatpak = MprisSource.busFailureMessage(Map.of("FLATPAK_ID", "com.x.Y"), false);
		assertTrue(flatpak.contains("flatpak override --user --talk-name=org.mpris.MediaPlayer2.* com.x.Y"), flatpak);
		String plain = MprisSource.busFailureMessage(Map.of(), false);
		assertTrue(plain.contains("no D-Bus session"), plain);
	}
}
