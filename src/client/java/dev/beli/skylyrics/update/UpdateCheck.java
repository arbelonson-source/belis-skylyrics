package dev.beli.skylyrics.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The pure parts of the updater: reading a GitHub release, comparing versions, picking the right jar. */
public final class UpdateCheck {
	private UpdateCheck() {
	}

	/** One downloadable file of a release; sha256 is lower-case hex, or null when GitHub gave no digest. */
	public record Asset(String name, String url, String sha256) {
	}

	public record Release(String version, List<Asset> assets) {
	}

	/** The latest-release answer from the GitHub API, or null when it is not a usable release. */
	public static Release parse(String json) {
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			String tag = string(root, "tag_name");
			if (tag == null || root.has("draft") && root.get("draft").getAsBoolean()
					|| root.has("prerelease") && root.get("prerelease").getAsBoolean()) {
				return null;
			}
			List<Asset> assets = new ArrayList<>();
			if (root.has("assets") && root.get("assets").isJsonArray()) {
				for (JsonElement e : root.getAsJsonArray("assets")) {
					JsonObject a = e.getAsJsonObject();
					String name = string(a, "name");
					String url = string(a, "browser_download_url");
					if (name == null || url == null) {
						continue;
					}
					String digest = string(a, "digest");
					String sha = digest != null && digest.toLowerCase(Locale.ROOT).startsWith("sha256:")
							? digest.substring(7).toLowerCase(Locale.ROOT) : null;
					assets.add(new Asset(name, url, sha));
				}
			}
			return new Release(tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag, assets);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	/** Negative when a is older than b. Dots are compared as numbers, and 1.0.0-beta is older than 1.0.0. */
	public static int compare(String a, String b) {
		String[] pa = core(a).split("-", 2);
		String[] pb = core(b).split("-", 2);
		String[] na = pa[0].split("\\.");
		String[] nb = pb[0].split("\\.");
		for (int i = 0; i < Math.max(na.length, nb.length); i++) {
			int c = Long.compare(number(na, i), number(nb, i));
			if (c != 0) {
				return c;
			}
		}
		if (pa.length != pb.length) {
			return pa.length == 1 ? 1 : -1;
		}
		return pa.length == 1 ? 0 : pa[1].compareTo(pb[1]);
	}

	/** The version without a leading v or the +build part. */
	private static String core(String v) {
		String s = v.trim();
		if (s.startsWith("v") || s.startsWith("V")) {
			s = s.substring(1);
		}
		int plus = s.indexOf('+');
		return plus >= 0 ? s.substring(0, plus) : s;
	}

	private static long number(String[] parts, int i) {
		if (i >= parts.length) {
			return 0;
		}
		String digits = parts[i].replaceAll("\\D.*$", "");
		try {
			return digits.isEmpty() ? 0 : Long.parseLong(digits);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** The Minecraft key of a running mod version like 0.7.0+26.1, or null when it has none (a dev build). */
	public static String mcKey(String modVersion) {
		int plus = modVersion.indexOf('+');
		return plus >= 0 && plus < modVersion.length() - 1 ? modVersion.substring(plus + 1) : null;
	}

	/** The release's jar for this Minecraft key, or null. */
	public static Asset pick(Release release, String mcKey) {
		for (Asset a : release.assets()) {
			if (a.name().equals("belis-skylyrics-" + release.version() + "+" + mcKey + ".jar")) {
				return a;
			}
		}
		return null;
	}
}
