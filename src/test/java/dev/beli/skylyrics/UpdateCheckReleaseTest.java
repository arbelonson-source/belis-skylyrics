package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import dev.beli.skylyrics.update.UpdateCheck;

/** Which GitHub answers count as a release, and how its checksum text is read. */
class UpdateCheckReleaseTest {
	@Test
	void aDraftReleaseIsIgnored() {
		assertNull(UpdateCheck.parse("{\"tag_name\": \"v9.9.9\", \"draft\": true, \"prerelease\": false, \"assets\": []}"));
		assertNull(UpdateCheck.parse("{\"tag_name\": \"v9.9.9\", \"draft\": true}"));
		assertNotNull(UpdateCheck.parse("{\"tag_name\": \"v9.9.9\", \"draft\": false}"));
	}

	@Test
	void anUppercaseDigestStillMatchesWhenComparedInLowerCase() {
		String json = "{\"tag_name\": \"v1.0.0\", \"assets\": [{\"name\": \"a.jar\", \"browser_download_url\": \"https://x/a.jar\","
				+ " \"digest\": \"SHA256:ABCDEF0123456789\"}, {\"name\": \"b.jar\", \"browser_download_url\": \"https://x/b.jar\","
				+ " \"digest\": \"sha256:AbCdEf\"}]}";
		UpdateCheck.Release r = UpdateCheck.parse(json);
		assertNotNull(r);
		assertEquals("abcdef0123456789", r.assets().get(0).sha256());
		assertEquals("abcdef", r.assets().get(1).sha256());
	}

	@Test
	void aDigestOfAnotherKindIsNotTakenAsASha256() {
		String json = "{\"tag_name\": \"v1.0.0\", \"assets\": [{\"name\": \"a.jar\", \"browser_download_url\": \"https://x/a.jar\","
				+ " \"digest\": \"md5:abcdef\"}]}";
		assertNull(UpdateCheck.parse(json).assets().get(0).sha256());
	}

	@Test
	void anUppercaseVInTheTagIsDropped() {
		assertEquals("1.2.3", UpdateCheck.parse("{\"tag_name\": \"V1.2.3\"}").version());
	}
}
