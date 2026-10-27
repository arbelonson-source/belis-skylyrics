package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.update.UpdateCheck;
import dev.beli.skylyrics.update.UpdateCheck.Release;
import org.junit.jupiter.api.Test;

class UpdateCheckTest {
	private static final String RELEASE = """
			{"tag_name": "v0.7.1", "draft": false, "prerelease": false, "assets": [
			  {"name": "belis-skylyrics-0.7.1+26.1.jar", "browser_download_url": "https://x/a.jar", "digest": "sha256:ABCDEF"},
			  {"name": "belis-skylyrics-0.7.1+1.21.11.jar", "browser_download_url": "https://x/b.jar"},
			  {"name": "belis-skylyrics-0.7.1+1.21.11-sources.jar", "browser_download_url": "https://x/c.jar"}
			]}""";

	@Test
	void newerVersionsCompareAsNumbers() {
		assertTrue(UpdateCheck.compare("0.10.0", "0.9.9") > 0);
		assertTrue(UpdateCheck.compare("0.6.4", "0.7.0") < 0);
		assertTrue(UpdateCheck.compare("1.0", "1.0.0") == 0);
	}

	@Test
	void buildSuffixAndPrefixAreIgnored() {
		assertEquals(0, UpdateCheck.compare("v0.7.0", "0.7.0+26.1"));
		assertTrue(UpdateCheck.compare("0.7.1", "0.7.0+26.1") > 0);
	}

	@Test
	void preReleaseIsOlderThanRelease() {
		assertTrue(UpdateCheck.compare("0.7.0-beta.1", "0.7.0") < 0);
		assertTrue(UpdateCheck.compare("0.7.0", "0.7.0-beta.1") > 0);
	}

	@Test
	void mcKeyIsThePartAfterThePlus() {
		assertEquals("26.1", UpdateCheck.mcKey("0.7.0+26.1"));
		assertEquals("1.21.11", UpdateCheck.mcKey("0.7.0+1.21.11"));
		assertNull(UpdateCheck.mcKey("dev"));
		assertNull(UpdateCheck.mcKey("0.7.0+"));
	}

	@Test
	void parsesTagAssetsAndDigest() {
		Release r = UpdateCheck.parse(RELEASE);
		assertNotNull(r);
		assertEquals("0.7.1", r.version());
		assertEquals(3, r.assets().size());
		assertEquals("abcdef", r.assets().get(0).sha256());
		assertNull(r.assets().get(1).sha256());
	}

	@Test
	void picksTheJarForTheRunningMinecraft() {
		Release r = UpdateCheck.parse(RELEASE);
		assertEquals("https://x/b.jar", UpdateCheck.pick(r, "1.21.11").url());
		assertEquals("https://x/a.jar", UpdateCheck.pick(r, "26.1").url());
		assertNull(UpdateCheck.pick(r, "26.2"));
	}

	@Test
	void ignoresDraftsPrereleasesAndGarbage() {
		assertNull(UpdateCheck.parse("{\"tag_name\": \"v1\", \"prerelease\": true}"));
		assertNull(UpdateCheck.parse("{\"message\": \"Not Found\"}"));
		assertNull(UpdateCheck.parse("not json"));
		assertNull(UpdateCheck.parse("[]"));
	}
}
