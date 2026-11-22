package dev.beli.skylyrics.render;

/**
 * The skies' shapes drawn the way they were before their corners were kept from frame to frame: every corner worked out
 * again and sent through {@link SkyCanvas#vertex}, which works out each quad afresh. The pictures to compare with.
 */
final class OldSkyDraw {
	private OldSkyDraw() {}

	/** A mesh as {@link SkyMesh#draw} drew it: each corner turned and pushed out again, with the mesh's own culling. */
	static void mesh(SkyMesh m, SkyCanvas c, float cos, float sin, float r, float level, SkyView view) {
		if (!m.capShown(cos, sin, view)) {
			return;
		}
		boolean[] column = m.columnsInView(cos, sin, view);
		int alpha = SkyColors.argb(0, level);
		for (int i = 0; i + 1 < m.rows; i++) {
			if (!m.rowShown(i, view)) {
				continue;
			}
			for (int j = 0; j + 1 < m.cols; j++) {
				if (column != null && !column[j]) {
					continue;
				}
				int k = i * m.cols + j;
				for (int p : new int[] {k, k + 1, k + m.cols + 1, k + m.cols}) {
					c.vertex((m.x[p] * cos - m.z[p] * sin) * r, m.y[p] * r, (m.z[p] * cos + m.x[p] * sin) * r, alpha | m.rgb[p] & 0xFFFFFF);
				}
			}
		}
	}
}
