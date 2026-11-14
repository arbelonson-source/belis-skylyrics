package dev.beli.skylyrics.lyrics;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.media.TitleCleaner;
import dev.beli.skylyrics.media.Track;

/**
 * Backup lyrics from <a href="https://music.163.com">NetEase Cloud Music</a>, for when LRCLIB
 * is down or has nothing. No account or API key needed. Blocking; call from a background thread.
 * Its search always returns something, even for made-up names, so a song only counts when the
 * title matches exactly and the artist or the length matches too.
 */
public final class NeteaseClient implements LyricsSource {
	private static final String BASE = "https://music.163.com/api";
	/** Durations closer than this count as the same recording. */
	private static final double DURATION_SLACK_S = 4;
	/** With a different artist name (another alphabet, say), the length must be this close. */
	private static final double STRICT_SLACK_S = 1;
	/** A line shaped like "[00:00.00] label : name". Only counts as a credit when the label is a known one. */
	private static final Pattern LABELLED = Pattern.compile("^(?:\\[[^\\]]*])*\\s*([^:：\\[\\]]{1,20}?)\\s*[:：].*$");
	/** A line with nothing but time stamps. */
	private static final Pattern EMPTY = Pattern.compile("^(?:\\[[^\\]]*])*$");
	/** Credit labels seen in NetEase lyrics, lower case. One-letter duet tags like "男" are not here on purpose. */
	private static final Set<String> CREDIT_LABELS = Set.of(
			"作词", "作曲", "编曲", "词", "曲", "编", "制作", "制作人", "监制", "策划", "统筹", "出品", "发行",
			"演唱", "主唱", "主人声", "和声", "和音", "和声编写", "合声", "配唱", "配唱制作人", "人声编辑",
			"吉他", "贝斯", "鼓", "键盘", "弦乐", "钢琴", "打击乐", "录音", "录音师", "录音工程", "录音助理",
			"录音工程师", "混音", "混音师", "混音助理", "混音工程师", "母带", "母带处理", "母带工程师", "音频工程师",
			"歌词翻译", "翻译", "歌词", "文案", "封面", "企划", "制作公司", "录音室", "混音室", "母带室",
			"op", "sp", "lyrics", "lyricist", "music", "composer", "arranger", "arrangement", "producer", "producers",
			"vocals", "chorus", "backing vocals", "guitar", "guitars", "bass", "drums", "keyboards", "piano",
			"percussion", "strings", "mixing", "mastering", "recording", "engineering", "programming", "translation",
			"作詞", "作曲家", "編曲");
	/**
	 * Credit words inside labels that aren't in the list, like "人声录音" or "音乐总监". Only used in the blocks
	 * before the first lyric and after the last one; spoken lines like "他说" have none of them.
	 */
	private static final Pattern CREDIT_WORD = Pattern.compile("作|编|制|录|混|母带|监|策划|统筹|出品|发行|吉他|贝斯|鼓|键盘|弦|和声|和音|人声|版权|翻译|企划|总监|工程");
	/** A label of Chinese characters only, as NetEase credits are. */
	private static final Pattern HAN_LABEL = Pattern.compile("\\p{IsHan}{2,8}");
	/** Timed lyrics have at least one "[mm:ss" stamp. */
	private static final Pattern TIMED = Pattern.compile("(?m)^\\[\\d{1,3}:\\d{1,2}");
	/** NetEase's placeholder text for instrumentals: "pure music, please enjoy". */
	private static final String PURE_MUSIC = "纯音乐";

	private final HttpClient http;
	private final String userAgent;
	private final String base;

	public NeteaseClient(String modVersion) {
		this(modVersion, BASE);
	}

	NeteaseClient(String modVersion, String base) {
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(6))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		this.userAgent = "BelisSkyLyrics/" + modVersion + " (https://github.com/arbelonson-source/belis-skylyrics)";
		this.base = base;
	}

	@Override
	public LrclibClient.Result find(Track track) throws IOException, InterruptedException {
		String title = TitleCleaner.forSearch(track.title());
		if (title.isBlank()) {
			return LrclibClient.Result.NOT_FOUND;
		}
		String artist = TitleCleaner.mainArtist(track.artist());
		long id = bestMatch(title + " " + artist, track);
		if (id < 0 && !artist.isBlank()) {
			id = bestMatch(title, track);
		}
		return id < 0 ? LrclibClient.Result.NOT_FOUND : lyrics(id);
	}

	private long bestMatch(String query, Track track) throws IOException, InterruptedException {
		JsonObject answer = get("/search/get?type=1&limit=20&s=" + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8));
		JsonObject result = object(answer, "result");
		if (result == null || !(result.get("songs") instanceof JsonArray songs)) {
			return -1;
		}
		String wantTitle = key(TitleCleaner.forSearch(track.title()));
		String wantArtist = key(TitleCleaner.mainArtist(track.artist()));
		double seconds = track.lengthMs() / 1000.0;
		long best = -1;
		double bestDiff = Double.MAX_VALUE;
		for (JsonElement el : songs) {
			if (!(el instanceof JsonObject song) || !song.has("id") || !key(TitleCleaner.forSearch(string(song, "name"))).equals(wantTitle)) {
				continue;
			}
			double diff = seconds > 0 && song.has("duration") ? Math.abs(song.get("duration").getAsLong() / 1000.0 - seconds) : -1;
			boolean sameArtist = sameArtist(song, wantArtist);
			boolean ok = diff < 0 ? sameArtist : sameArtist ? diff <= DURATION_SLACK_S : diff <= STRICT_SLACK_S;
			double rank = diff < 0 ? 0 : diff + (sameArtist ? 0 : DURATION_SLACK_S);
			if (ok && rank < bestDiff) {
				best = song.get("id").getAsLong();
				bestDiff = rank;
			}
		}
		return best;
	}

	private static boolean sameArtist(JsonObject song, String wantArtist) {
		if (wantArtist.isEmpty() || !(song.get("artists") instanceof JsonArray artists)) {
			return false;
		}
		for (JsonElement a : artists) {
			if (a instanceof JsonObject o) {
				String name = key(string(o, "name"));
				if (!name.isEmpty() && (name.contains(wantArtist) || wantArtist.contains(name))) {
					return true;
				}
			}
		}
		return false;
	}

	private LrclibClient.Result lyrics(long id) throws IOException, InterruptedException {
		JsonObject answer = get("/song/lyric?lv=1&tv=-1&id=" + id);
		JsonObject lrc = object(answer, "lrc");
		String text = lrc == null ? "" : clean(string(lrc, "lyric"));
		if (bool(answer, "pureMusic") || text.contains(PURE_MUSIC)) {
			return new LrclibClient.Result(null, null, true);
		}
		if (text.isBlank()) {
			return LrclibClient.Result.NOT_FOUND;
		}
		return TIMED.matcher(text).find()
				? new LrclibClient.Result(text, null, false)
				: new LrclibClient.Result(null, text, false);
	}

	/**
	 * Drops the credit lines and NetEase's JSON metadata lines, keeping the lyrics. Credits only
	 * sit in the block before the first lyric and the block after the last one, so a sung
	 * line like "他说：我爱你" in the middle of a song is never touched.
	 */
	static String clean(String lyric) {
		String[] lines = lyric.split("\\R");
		boolean[] drop = new boolean[lines.length];
		for (int i = 0; i < lines.length; i++) {
			drop[i] = lines[i].strip().startsWith("{");
		}
		for (int i = 0; i < lines.length && neutralOrCredit(lines[i]); i++) {
			drop[i] |= isCredit(lines[i]);
		}
		for (int i = lines.length - 1; i >= 0 && neutralOrCredit(lines[i]); i--) {
			drop[i] |= isCredit(lines[i]);
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (!drop[i]) {
				out.append(lines[i]).append('\n');
			}
		}
		return out.toString().strip();
	}

	/** Lines that do not end the credit block: blank, time stamps only, metadata, credits. */
	private static boolean neutralOrCredit(String line) {
		String t = line.strip();
		return t.isEmpty() || t.startsWith("{") || EMPTY.matcher(t).matches() || isCredit(t);
	}

	private static boolean isCredit(String line) {
		Matcher m = LABELLED.matcher(line.strip());
		if (!m.matches()) {
			return false;
		}
		String label = m.group(1).strip();
		return CREDIT_LABELS.contains(label.toLowerCase(Locale.ROOT))
				|| HAN_LABEL.matcher(label).matches() && CREDIT_WORD.matcher(label).find();
	}

	/** Lowercase letters and digits only, so "Don't Stop" matches "Dont stop". */
	static String key(String s) {
		return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
	}

	private static String string(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	private static boolean bool(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
	}

	private static @Nullable JsonObject object(JsonObject o, String key) {
		return o.get(key) instanceof JsonObject child ? child : null;
	}

	private JsonObject get(String pathAndQuery) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(base + pathAndQuery))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", userAgent)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() / 100 != 2) {
			throw new IOException("NetEase answered " + response.statusCode());
		}
		JsonElement json = JsonParser.parseString(response.body());
		if (!(json instanceof JsonObject o) || (o.has("code") && o.get("code").getAsInt() != 200)) {
			throw new IOException("NetEase refused the request: " + response.body().substring(0, Math.min(120, response.body().length())));
		}
		return o;
	}
}
