package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * A flowing line's pieces are placed one after another along a row: two neighbours of one row never share yaw, however
 * high or low in the sky the row sits (a card there spans more yaw), at several sizes, distances and pitches.
 */
class WorldFlowOverlapTest {
	private static final String LINE = "This is a very long line of lyrics that keeps going across the whole sky so it has to wrap onto several rows";

	/** One lit piece of the flowing line: where its edges are in yaw and its middle in pitch, in degrees. */
	private record Piece(float left, float right, float pitch, float halfH, String text) {
	}

	@SuppressWarnings("unchecked")
	private static List<Piece> write(long seed, float pitch, float scale, float away) throws Exception {
		WorldWords.measureHook = t -> t.length() * 4.0;
		WorldWords.buriedHook = pos -> false;
		try {
			ScreenLayer.capture(new Matrix4f(), new Matrix4f().perspective((float) Math.toRadians(70), 1.7f, 0.05f, 100f));
			WorldWords words = new WorldWords();
			words.seed(seed);
			words.tune(30, 6, away, 1, 1, true, true, false, scale);
			words.players(false, false);
			Camera camera = new Camera();
			for (int k = 1; k <= 120; k++) {
				words.update(0, LINE, (k - 1) * 1.5f, false, 0f, pitch, camera, null, null, net.minecraft.network.chat.Style.EMPTY, k * 100_000_000L);
			}
			Field cardsField = WorldWords.class.getDeclaredField("cards");
			cardsField.setAccessible(true);
			Field sentenceField = WorldWords.class.getDeclaredField("sentence");
			sentenceField.setAccessible(true);
			Object sentence = sentenceField.get(words);
			List<Piece> out = new ArrayList<>();
			for (Object card : (List<Object>) cardsField.get(words)) {
				Class<?> c = card.getClass();
				Field of = c.getDeclaredField("of"), pos = c.getDeclaredField("pos"), at = c.getDeclaredField("at"), size = c.getDeclaredField("size");
				Field disp = c.getDeclaredField("display"), nod = c.getDeclaredField("lean");
				for (Field f : new Field[] {of, pos, at, size, disp, nod}) {
					f.setAccessible(true);
				}
				if (of.get(card) != sentence || sentence == null) {
					continue;
				}
				Vec3 p = (Vec3) pos.get(card);
				float[] a = (float[]) at.get(card);
				float half = a[a.length - 1] * (float) size.getFloat(card) * scale / 2;
				double far = p.length(), flat = Math.sqrt(p.x * p.x + p.z * p.z);
				float yaw = (float) Math.toDegrees(Math.atan2(-p.x, p.z));
				float width = WorldWords.halfAngle(half, flat);
				float pit = (float) -Math.toDegrees(Math.asin(p.y / far));
				out.add(new Piece(yaw - width, yaw + width, pit, WorldWords.halfAngle(5 * size.getFloat(card) * scale, far), new String(((LyricsRenderer.Display) disp.get(card)).cps(), 0, ((LyricsRenderer.Display) disp.get(card)).cps().length)));
			}
			return out;
		} finally {
			WorldWords.measureHook = null;
			WorldWords.buriedHook = null;
			var captured = ScreenLayer.class.getDeclaredField("captured");
			captured.setAccessible(true);
			captured.setBoolean(null, false);
		}
	}

	@Test
	void neighboursOfOneRowAreNeverDrawnOverEachOther() throws Exception {
		int checked = 0;
		StringBuilder bad = new StringBuilder();
		for (long seed = 1; seed <= 60; seed++) {
			for (float[] cfg : new float[][] {{1f, 1f, 0f}, {1.5f, 1f, 0f}, {1f, 1.5f, 0f}, {1f, 0.6f, 0f}, {1f, 1f, -30f}, {1f, 1f, 30f}, {2f, 1f, 0f}}) {
				float scale = cfg[0];
				List<Piece> pieces = write(seed, cfg[2], scale, cfg[1]);
				for (Piece a : pieces) {
					for (Piece b : pieces) {
						// Same row: their middles are within a row's height; b starts right of a's middle
						if (a != b && Math.abs(a.pitch - b.pitch) < Math.min(a.halfH, b.halfH) && a.left < b.left) {
							checked++;
							if (a.right > b.left + 0.05f) {
								bad.append("seed ").append(seed).append(" cfg ").append(java.util.Arrays.toString(cfg)).append(": ").append(a).append(" over ").append(b).append('\n');
							}
						}
					}
				}
			}
		}
		assertTrue(checked > 100, "pairs checked " + checked);
		assertTrue(bad.length() == 0, "overlapping pieces:\n" + bad);
	}
}
