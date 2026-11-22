package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * The Minecraft versions this version's jar tells Fabric it runs on, read from the fabric.mod.json its build made. Fabric
 * gives snapshots and pre-releases a version just below their release (25w31a is 1.21.9-alpha.25.31.a, 26.2-snapshot-1 is
 * 26.2-alpha.1), so a range that ends with "<1.21.9" also takes the 1.21.9 snapshots, where the jar can only crash.
 */
class ModManifestTest {
	/** The releases around every jar, each tried with the snapshot, pre-release and release candidate names Fabric gives them. */
	private static final String[] RELEASES = {"1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8",
			"1.21.9", "1.21.10", "1.21.11", "1.22", "26.1", "26.1.1", "26.1.2", "26.2", "26.2.1", "26.3", "26.3.1", "26.4", "27.1"};
	private static final String[] EARLY = {"-alpha.1", "-alpha.25.31.a", "-beta.1", "-pre.1", "-rc.1"};

	/** This mod's own fabric.mod.json on the test classpath (Fabric API's modules bring theirs too). */
	static JsonObject manifest() throws IOException {
		Enumeration<URL> all = ModManifestTest.class.getClassLoader().getResources("fabric.mod.json");
		while (all.hasMoreElements()) {
			try (InputStream in = all.nextElement().openStream()) {
				JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
				if (json.has("id") && SkyLyricsClient.MOD_ID.equals(json.get("id").getAsString())) {
					return json;
				}
			}
		}
		return null;
	}

	/** The jar's Minecraft requirement: one predicate, or a list of which any one is enough. */
	private static List<VersionPredicate> minecraft(JsonObject manifest) throws Exception {
		JsonElement value = manifest.getAsJsonObject("depends").get("minecraft");
		List<VersionPredicate> out = new ArrayList<>();
		if (value.isJsonArray()) {
			for (JsonElement e : value.getAsJsonArray()) {
				out.add(VersionPredicate.parse(e.getAsString()));
			}
		} else {
			out.add(VersionPredicate.parse(value.getAsString()));
		}
		return out;
	}

	private static boolean claims(List<VersionPredicate> predicates, String version) throws Exception {
		Version v = Version.parse(version);
		for (VersionPredicate p : predicates) {
			if (p.test(v)) {
				return true;
			}
		}
		return false;
	}

	@Test
	void theBuildFilledInTheMinecraftVersions() throws Exception {
		JsonObject manifest = manifest();
		assertNotNull(manifest, "no fabric.mod.json for " + SkyLyricsClient.MOD_ID + " on the test classpath");
		String mc = manifest.getAsJsonObject("depends").get("minecraft").toString();
		assertTrue(!mc.contains("${"), "the build did not fill in the Minecraft versions: " + mc);
	}

	@Test
	void theJarRunsOnTheVersionInItsName() throws Exception {
		JsonObject manifest = manifest();
		String version = manifest.get("version").getAsString(); // like 0.8.0+1.21.8 or 0.8.0+26.1
		String named = version.substring(version.indexOf('+') + 1);
		assertTrue(claims(minecraft(manifest), named), "the jar named +" + named + " does not claim Minecraft " + named);
	}

	@Test
	void noJarClaimsTheSnapshotsOfAVersionItDoesNotRunOn() throws Exception {
		JsonObject manifest = manifest();
		List<VersionPredicate> mc = minecraft(manifest);
		List<String> wrong = new ArrayList<>();
		for (String release : RELEASES) {
			if (claims(mc, release)) {
				continue;
			}
			for (String early : EARLY) {
				if (claims(mc, release + early)) {
					wrong.add(release + early);
				}
			}
		}
		assertTrue(wrong.isEmpty(), "Minecraft " + manifest.getAsJsonObject("depends").get("minecraft")
				+ " also takes snapshots of versions it doesn't run on: " + wrong);
	}
}
