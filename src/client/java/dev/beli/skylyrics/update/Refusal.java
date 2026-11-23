package dev.beli.skylyrics.update;

import dev.beli.skylyrics.util.LogText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.beli.skylyrics.util.SafeFile;

/**
 * What the updater remembers about a release it refused (its jar would not load on this game), so the same jar is not
 * downloaded again on every start while nothing relevant changed. A tiny json file in the mod's cache folder (never the
 * mods folder, which Fabric scans). A file that is missing, unreadable or malformed simply means "no memory". It keeps
 * the jar's constraints, not the words shown for them, so a later start tells the player in the language the game has then.
 * A file from an earlier version has only those words: it counts as no memory, so that release is checked once more.
 */
record Refusal(String asset, String sha256, Map<String, String> running, String name, List<String> constraints) {
	private static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");
	/** The ids whose running versions decide whether the answer could be different now. */
	private static final String[] IDS = {"minecraft", "fabric-api", "fabricloader", "java"};

	/** The refusal of this asset under these running versions. */
	static Refusal of(UpdateCheck.Asset asset, UnaryOperator<String> running, Needs.Unmet unmet) {
		return new Refusal(asset.name(), asset.sha256(), versions(running), unmet.name(), unmet.constraints());
	}

	static Map<String, String> versions(UnaryOperator<String> running) {
		Map<String, String> out = new LinkedHashMap<>();
		for (String id : IDS) {
			String v = running.apply(id);
			out.put(id, v == null ? "" : v);
		}
		return out;
	}

	/** True when this is the same file (name and checksum) refused on the same running versions. */
	boolean matches(UpdateCheck.Asset other, UnaryOperator<String> now) {
		return sha256 != null && !sha256.isEmpty() && other.sha256() != null && asset.equals(other.name())
				&& sha256.equalsIgnoreCase(other.sha256()) && running.equals(versions(now));
	}

	Needs.Unmet unmet() {
		return new Needs.Unmet(name, constraints);
	}

	/** The remembered refusal, or null when there is none or the file can't be understood. */
	static Refusal read(Path file) {
		if (file == null) {
			return null;
		}
		if (file.toAbsolutePath().getParent() != null) {
			dev.beli.skylyrics.util.Leftovers.cleanTemps(file.toAbsolutePath().getParent(), file.getFileName().toString(), System.currentTimeMillis());
		}
		try {
			if (!Files.isRegularFile(file) || Files.size(file) > 16 * 1024) {
				return null;
			}
			JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject r = o.getAsJsonObject("running");
			Map<String, String> running = new LinkedHashMap<>();
			for (String id : IDS) {
				running.put(id, r.get(id).getAsString());
			}
			List<String> constraints = new ArrayList<>();
			for (JsonElement c : o.getAsJsonArray("constraints")) {
				constraints.add(c.getAsJsonPrimitive().getAsString());
			}
			Refusal out = new Refusal(o.get("asset").getAsString(), o.get("sha256").getAsString(), running,
					o.get("name").getAsString(), constraints);
			return out.name.isEmpty() ? null : out;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** Writes the file all at once; never throws (a failure only means the next start downloads again). */
	void write(Path file) {
		if (file == null) {
			return;
		}
		Path tmp = null;
		try {
			JsonObject r = new JsonObject();
			running.forEach(r::addProperty);
			JsonObject o = new JsonObject();
			o.addProperty("asset", asset);
			o.addProperty("sha256", sha256);
			o.add("running", r);
			o.addProperty("name", name);
			JsonArray c = new JsonArray();
			constraints.forEach(c::add);
			o.add("constraints", c);
			o.addProperty("needs", unmet().needs()); // what earlier versions read
			Files.createDirectories(file.getParent());
			tmp = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
			Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
			SafeFile.replace(tmp, file);
		} catch (IOException | RuntimeException e) {
			LOG.info("Could not remember the refused update: {}", LogText.of(e));
			try {
				if (tmp != null) {
					Files.deleteIfExists(tmp);
				}
			} catch (IOException ignored) {
				// a leftover temp file in the cache folder is harmless
			}
		}
	}

	static void forget(Path file) {
		if (file == null) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException | RuntimeException e) {
			LOG.info("Could not forget the refused update: {}", LogText.of(e));
		}
	}
}
