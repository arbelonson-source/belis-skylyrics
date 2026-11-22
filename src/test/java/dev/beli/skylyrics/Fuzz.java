package dev.beli.skylyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Seeded random and damaged input for the fuzz tests: text with control characters, lone surrogates, emoji sequences,
 * combining marks and right-to-left letters; JSON with wrong types, missing fields, nulls, huge numbers and deep nesting;
 * and damaged bytes. The same seed always gives the same input, so a failure names its case and can be run again.
 */
public final class Fuzz {
	public final Random random;

	public Fuzz(long seed) {
		random = new Random(seed);
	}

	/** Pieces that have broken text code before, or that text code often gets wrong. */
	static final String[] PIECES = {
			"", " ", "  ", "\t", "\n", "\r\n", "\r", "\u0000", "\u0007", "\u001b[31m", "\u007f", "\u0085", " ", " ",
			" ", " ", "　", "﻿", "​", "‌", "‍", "⁠", "‮", "‪", "⁦", "⁩",
			"‎", "‏", "؜", "️", "︎", "󠄀", "\uD83D", "\uDE00", "\uDC00\uD800", "\uD800\uD800",
			"😀", "👨‍👩‍👧‍👦", "🏳️‍🌈",
			"🇯🇵", "👍🏽", "❤️", "‍‍‍", "\uD83D‍",
			"é", "à́̂̃̄̅̆̇̈̉", "́́́", "⃝",
			"שלום", "مرحبا", "مَرْحَبًا",
			"ภาษาไทย", "我爱你", "日本語", "한국어",
			"ﷺ", "்", "क्ष", "각", "ㅤ", "⠀", "�", "￿", "",
			"§", "§a", "§k", "§😀", "[00:01.00]", "[99:99.99]", "[00:00]", "<00:01.00>",
			"[offset:+5000]", "[offset:-99999999999999999999]", "[-00:01.00]", "［００：０１．００］",
			"[00:01.00][00:02.00]", "[ar:x]", "[", "]", "[:]", "[00:", "(Instrumental)", "♪", "[Chorus]", "[Verse 1: x]",
			"%s", "%n", "{}", "{0}", "\\", "\"", "'", "<", ">", "&amp;", "${x}", " - ", " / ", "feat.", "(Live)", "(", ")",
			"（", "【", "Remastered 2011", "|", "–", "—", "·", ".", "...", "…", "0", "-1", "NaN",
	};

	/** A random code point from anywhere: plain letters most of the time, then any script, symbols, unassigned and private ones. */
	public int codePoint() {
		return switch (random.nextInt(10)) {
			case 0, 1, 2 -> 'a' + random.nextInt(26);
			case 3 -> ' ';
			case 4 -> random.nextInt(0x80);
			case 5 -> random.nextInt(0x3000);
			case 6 -> 0x3000 + random.nextInt(0xD800 - 0x3000);
			case 7 -> 0xE000 + random.nextInt(0x10000 - 0xE000);
			case 8 -> 0x10000 + random.nextInt(0x20000);
			default -> random.nextInt(Character.MAX_CODE_POINT + 1);
		};
	}

	/** Text of up to {@code max} pieces: odd pieces, random code points and now and then a lone surrogate. */
	public String text(int max) {
		StringBuilder b = new StringBuilder();
		int n = random.nextInt(max + 1);
		for (int i = 0; i < n; i++) {
			switch (random.nextInt(4)) {
				case 0 -> b.append(PIECES[random.nextInt(PIECES.length)]);
				case 1 -> b.append((char) random.nextInt(0x10000)); // any UTF-16 unit, lone surrogates too
				default -> b.appendCodePoint(codePoint());
			}
		}
		return b.toString();
	}

	/** Text from {@link #text}, or now and then something extreme: empty, only spaces, one very long word, many marks. */
	public String nasty(int max) {
		return switch (random.nextInt(12)) {
			case 0 -> "";
			case 1 -> " ".repeat(1 + random.nextInt(50));
			case 2 -> "x".repeat(1 + random.nextInt(10_000));
			case 3 -> "e" + "́".repeat(1 + random.nextInt(2000));
			case 4 -> "👨‍".repeat(1 + random.nextInt(500));
			case 5 -> "ש".repeat(1 + random.nextInt(2000)) + " a ".repeat(random.nextInt(20));
			case 6 -> "我".repeat(1 + random.nextInt(3000));
			case 7 -> "​".repeat(1 + random.nextInt(2000));
			case 8 -> "\uD83D".repeat(1 + random.nextInt(1000));
			default -> text(max);
		};
	}

	/** {@code base} with a few random edits: chars dropped, doubled, swapped for odd pieces, or the end cut off. */
	public String mutate(String base) {
		StringBuilder b = new StringBuilder(base);
		int edits = 1 + random.nextInt(8);
		for (int e = 0; e < edits && b.length() > 0; e++) {
			int at = random.nextInt(b.length());
			switch (random.nextInt(6)) {
				case 0 -> b.deleteCharAt(at);
				case 1 -> b.insert(at, b.charAt(at));
				case 2 -> b.insert(at, PIECES[random.nextInt(PIECES.length)]);
				case 3 -> b.setCharAt(at, (char) random.nextInt(0x10000));
				case 4 -> b.setLength(at);
				default -> b.insert(at, text(4));
			}
		}
		return b.toString();
	}

	/** {@code base} with some bits flipped, bytes dropped, inserted or the end cut off. */
	public byte[] mutate(byte[] base) {
		List<Byte> b = new ArrayList<>(base.length);
		for (byte x : base) {
			b.add(x);
		}
		int edits = 1 + random.nextInt(10);
		for (int e = 0; e < edits && !b.isEmpty(); e++) {
			int at = random.nextInt(b.size());
			switch (random.nextInt(5)) {
				case 0 -> b.set(at, (byte) (b.get(at) ^ (1 << random.nextInt(8))));
				case 1 -> b.remove(at);
				case 2 -> b.add(at, (byte) random.nextInt(256));
				case 3 -> b.set(at, (byte) (random.nextBoolean() ? 0xFF : 0));
				default -> {
					if (random.nextInt(3) == 0) {
						b.subList(at, b.size()).clear();
					}
				}
			}
		}
		byte[] out = new byte[b.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = b.get(i);
		}
		return out;
	}

	public byte[] bytes(int max) {
		byte[] out = new byte[random.nextInt(max + 1)];
		random.nextBytes(out);
		return out;
	}

	// JSON

	/** Raw JSON text that stands in for a value: numbers no type holds, deep nesting, a long string. */
	static final String[] RAW = {
			"1e999999999", "-1e999999999", "1e-999999999", "9".repeat(400), "-" + "9".repeat(400), "1e308", "-1e308",
			"1.7976931348623157E309", "NaN", "Infinity", "-Infinity", "9223372036854775807", "-9223372036854775808",
			"9223372036854775808", "18446744073709551616", "1e20", "-0", "0.5", "-0.5", "2147483648", "-2147483649", "1e10",
			"4.9e-324", "0e0", "[".repeat(300) + "]".repeat(300), "[".repeat(20_000) + "]".repeat(20_000),
			"{\"a\":".repeat(300) + "1" + "}".repeat(300), "{\"a\":".repeat(20_000) + "1" + "}".repeat(20_000),
	};

	/** A value of any kind, sometimes an odd one. */
	public JsonElement value(int depth) {
		int pick = random.nextInt(depth <= 0 ? 9 : 12);
		return switch (pick) {
			case 0 -> JsonNull.INSTANCE;
			case 1 -> new JsonPrimitive(random.nextBoolean());
			case 2 -> new JsonPrimitive(random.nextInt(2000) - 1000);
			case 3 -> new JsonPrimitive(random.nextLong());
			case 4 -> new JsonPrimitive(random.nextDouble() * Math.pow(10, random.nextInt(40) - 10));
			case 5, 6 -> new JsonPrimitive(text(12));
			case 7 -> new JsonPrimitive(PIECES[random.nextInt(PIECES.length)]);
			case 8 -> raw(RAW[random.nextInt(RAW.length)]);
			case 9 -> {
				JsonArray a = new JsonArray();
				int n = random.nextInt(5);
				for (int i = 0; i < n; i++) {
					a.add(value(depth - 1));
				}
				yield a;
			}
			default -> {
				JsonObject o = new JsonObject();
				int n = random.nextInt(5);
				for (int i = 0; i < n; i++) {
					o.add(random.nextBoolean() ? text(3) : "name", value(depth - 1));
				}
				yield o;
			}
		};
	}

	private final List<String> raws = new ArrayList<>();

	/** A placeholder that {@link #write} turns into {@code json} as it is (Gson can't hold some of these as values). */
	public JsonElement raw(String json) {
		raws.add(json);
		return new JsonPrimitive("@@RAW" + (raws.size() - 1) + "@@");
	}

	/** The element as JSON text, with every {@link #raw} placeholder replaced by its text. */
	public String write(JsonElement e) {
		String text = e.toString();
		for (int i = raws.size() - 1; i >= 0; i--) {
			text = text.replace("\"@@RAW" + i + "@@\"", raws.get(i));
		}
		raws.clear();
		return text;
	}

	/**
	 * A copy of {@code template} with damage: values swapped for odd ones or values of another type, fields dropped,
	 * lists emptied or filled with junk. About {@code rate} of the values are touched.
	 */
	public JsonElement damage(JsonElement template, double rate) {
		if (random.nextDouble() < rate) {
			return random.nextInt(4) == 0 && template.isJsonPrimitive() && template.getAsJsonPrimitive().isString()
					? new JsonPrimitive(nasty(40)) : value(2);
		}
		if (template.isJsonObject()) {
			JsonObject out = new JsonObject();
			for (Map.Entry<String, JsonElement> e : template.getAsJsonObject().entrySet()) {
				if (random.nextDouble() < rate / 2) {
					continue; // dropped
				}
				out.add(e.getKey(), damage(e.getValue(), rate));
			}
			if (random.nextDouble() < rate / 2) {
				out.add(text(3), value(2));
			}
			return out;
		}
		if (template.isJsonArray()) {
			JsonArray out = new JsonArray();
			for (JsonElement e : template.getAsJsonArray()) {
				if (random.nextDouble() < rate / 2) {
					continue;
				}
				out.add(damage(e, rate));
				if (random.nextDouble() < rate / 3) {
					out.add(damage(e, rate)); // repeated
				}
			}
			if (random.nextDouble() < rate / 2) {
				out.add(value(2));
			}
			return out;
		}
		return template.deepCopy();
	}

	/** JSON text for a damaged copy of {@code template}, and now and then text that isn't JSON at all, or only part of it. */
	public String damagedText(String template, double rate) {
		JsonElement parsed = JsonParser.parseString(template);
		String text = write(damage(parsed, rate));
		return switch (random.nextInt(14)) {
			case 0 -> text.substring(0, random.nextInt(text.length() + 1)); // cut off
			case 1 -> mutate(text);
			case 2 -> "﻿" + text;
			case 3 -> text + text;
			case 4 -> nasty(40);
			case 5 -> "null";
			case 6 -> "[" + text + "]";
			default -> text;
		};
	}

	/** Runs {@code check} for case 0 to {@code cases - 1}, each with its own seeded fuzz; a failure says which case. */
	public static void cases(long seed, int cases, Case check) {
		for (int i = 0; i < cases; i++) {
			Fuzz fuzz = new Fuzz(seed * 1_000_003L + i);
			try {
				check.run(fuzz, i);
			} catch (Throwable e) {
				throw new AssertionError("fuzz case " + i + " of seed " + seed + ": " + e, e);
			}
		}
	}

	/** As {@link #cases(long, int, Case)}, each case within {@code limit}: a case that doesn't end says which it is. */
	public static void cases(long seed, int cases, java.time.Duration limit, Case check) {
		for (int i = 0; i < cases; i++) {
			int index = i;
			Fuzz fuzz = new Fuzz(seed * 1_000_003L + i);
			org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(limit, () -> {
				try {
					check.run(fuzz, index);
				} catch (Throwable e) {
					throw new AssertionError("fuzz case " + index + " of seed " + seed + ": " + e, e);
				}
			}, () -> "fuzz case " + index + " of seed " + seed + " took over " + limit);
		}
	}

	public interface Case {
		void run(Fuzz fuzz, int index) throws Exception;
	}

	/** For a failure message: the input with every char that isn't plain printable ASCII written as \\uXXXX, cut short. */
	public static String show(String s) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length() && b.length() < 300; i++) {
			char c = s.charAt(i);
			if (c >= 0x20 && c < 0x7f) {
				b.append(c);
			} else {
				b.append(String.format("\\u%04x", (int) c));
			}
		}
		return s.length() > 300 ? b + "... (" + s.length() + " chars)" : b.toString();
	}
}
