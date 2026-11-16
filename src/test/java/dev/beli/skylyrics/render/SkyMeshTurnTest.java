package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Turning each grid point once must give the corners the per-corner maths gave. */
class SkyMeshTurnTest {
	private static final class Recorder extends SkyCanvas {
		final List<float[]> out = new ArrayList<>();
		final List<Integer> argb = new ArrayList<>();
		private final float[] at = new float[12];
		private final int[] col = new int[4];
		private int n;
		int quads;

		@Override
		protected void put(float x, float y, float z, int c) {
			at[n * 3] = x;
			at[n * 3 + 1] = y;
			at[n * 3 + 2] = z;
			col[n] = c;
			if (++n == 4) {
				out.add(at.clone());
				for (int i = 0; i < 4; i++) {
					argb.add(col[i]);
				}
				n = 0;
			}
		}
	}

	/** The corners as the old code made them, in the order it made them, before the canvas winds them. */
	private static void oldCorners(SkyMesh m, float cos, float sin, float r, float level, List<float[]> out, List<Integer> argb) {
		for (int i = 0; i + 1 < m.rows; i++) {
			for (int j = 0; j + 1 < m.cols; j++) {
				int[] ks = {i * m.cols + j, i * m.cols + j + 1, (i + 1) * m.cols + j + 1, (i + 1) * m.cols + j};
				float[] q = new float[12];
				for (int c = 0; c < 4; c++) {
					int k = ks[c];
					q[c * 3] = (m.x[k] * cos - m.z[k] * sin) * r;
					q[c * 3 + 1] = m.y[k] * r;
					q[c * 3 + 2] = (m.z[k] * cos + m.x[k] * sin) * r;
					argb.add(SkyColors.argb(m.rgb[k], level));
				}
				out.add(q);
			}
		}
	}

	@Test
	void turnedCornersMatchThePerCornerMaths() {
		float[] rows = {-3, 0, 5, 20, 45, 90};
		SkyMesh m = SkyMesh.dome(rows, 36, (x, y, z) -> 0x102030 + (int) (x * 40 + 40) * 65536 + (int) (z * 40 + 40));
		float yaw = (float) Math.toRadians(37), cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
		Recorder rec = new Recorder();
		m.draw(rec, cos, sin, 90f, 0.63f);
		List<float[]> old = new ArrayList<>();
		List<Integer> oldArgb = new ArrayList<>();
		oldCorners(m, cos, sin, 90f, 0.63f, old, oldArgb);
		assertEquals(old.size(), rec.out.size());
		for (int q = 0; q < old.size(); q++) {
			// The canvas may wind a quad either way round: compare the four corners as a set
			for (int c = 0; c < 4; c++) {
				boolean found = false;
				for (int d = 0; d < 4; d++) {
					boolean same = true;
					for (int a = 0; a < 3; a++) {
						same &= Math.abs(old.get(q)[c * 3 + a] - rec.out.get(q)[d * 3 + a]) < 1e-5f;
					}
					found |= same && oldArgb.get(q * 4 + c).equals(rec.argb.get(q * 4 + d));
				}
				assertTrue(found, "corner " + c + " of quad " + q + " differs");
			}
		}
	}
}
