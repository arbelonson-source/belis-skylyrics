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
	/**
	 * The whole Moonlight sky as {@link MoonlitSky#draw} drew it, but for its clouds' colours, which are taken from
	 * {@code sky} after it drew this frame (MoonlitCloudRepaintTest holds those to the old repaint).
	 */
	static void moonlit(MoonlitSky sky, SkyCanvas c, float radius, float baseYaw, float level, double time, float pixel, SkyView view) {
		float cos = (float) Math.cos(baseYaw * MoonlitSky.DEG), sin = (float) Math.sin(baseYaw * MoonlitSky.DEG);
		c.fadeBelowHorizon(MoonlitSky.HORIZON_FADE);
		mesh(MoonlitSky.DOME_MESH, c, cos, sin, radius * MoonlitSky.DOME, level, view);
		mesh(MoonlitSky.GLOW_MESH, c, cos, sin, radius * MoonlitSky.GLOW_SKY, level, view);
		mesh(sky.clouds, c, cos, sin, radius * MoonlitSky.CLOUDS, level, view);
		SkyCanvas.SkyFrame frame = new SkyCanvas.SkyFrame();
		float r = radius * MoonlitSky.STARS;
		for (int i = 0; i < MoonlitSky.STAR_COUNT; i++) {
			if (MoonlitSky.STAR_ALPHA[i] == 0
					|| !view.capVisibleTurned(MoonlitSky.STAR_X[i], MoonlitSky.STAR_Y[i], MoonlitSky.STAR_Z[i], cos, sin, MoonlitSky.STAR_REACH)) {
				continue;
			}
			float twinkle = 0.7f + 0.3f * (float) Math.sin(SkyClock.phase(time, 0.6 + 0.11 * (i % 9), i * 1.7f));
			int col = SkyColors.argb(SkyColors.mix(MoonlitSky.STAR_SKY[i], MoonlitSky.STAR_RGB, MoonlitSky.STAR_ALPHA[i] * twinkle), level);
			float s = MoonlitSky.STAR_SIZE[i] * pixel;
			frame.aim(baseYaw + MoonlitSky.STAR_YAW[i], MoonlitSky.STAR_ELEV[i]);
			c.at(frame, -s, -s, r, col);
			c.at(frame, s, -s, r, col);
			c.at(frame, s, s, r, col);
			c.at(frame, -s, s, r, col);
		}
		mesh(MoonlitSky.DISC_MESH, c, cos, sin, radius * MoonlitSky.DISC, level, view);
		c.fadeBelowHorizon(0);
	}

	/** The whole Synthwave sky as {@link SynthwaveSky#draw} drew it. */
	static void synthwave(SkyCanvas c, float radius, float baseYaw, float level, double time, float pixel, SkyView view) {
		float cos = (float) Math.cos(baseYaw * SynthwaveSky.DEG), sin = (float) Math.sin(baseYaw * SynthwaveSky.DEG);
		c.fadeBelowHorizon(SynthwaveSky.HORIZON_FADE);
		mesh(SynthwaveSky.DOME_MESH, c, cos, sin, radius * SynthwaveSky.DOME, level, view);
		mesh(SynthwaveSky.SWIRL_MESH, c, cos, sin, radius * SynthwaveSky.SWIRL, level, view);
		mesh(SynthwaveSky.SUN_MESH, c, cos, sin, radius * SynthwaveSky.SUN_SKY, level, view);
		SkyCanvas.SkyFrame frame = new SkyCanvas.SkyFrame();
		float r = radius * SynthwaveSky.STARS;
		for (int i = 0; i < SynthwaveSky.STAR_COUNT; i++) {
			if (SynthwaveSky.STAR_ALPHA[i] == 0
					|| !view.capVisibleTurned(SynthwaveSky.STAR_X[i], SynthwaveSky.STAR_Y[i], SynthwaveSky.STAR_Z[i], cos, sin, SynthwaveSky.STAR_REACH)) {
				continue;
			}
			float twinkle = 0.75f + 0.25f * (float) Math.sin(SkyClock.phase(time, 0.7 + 0.13 * (i % 7), i * 2.3f));
			int col = SkyColors.argb(SkyColors.mix(SynthwaveSky.STAR_SKY[i], SynthwaveSky.STAR_RGB, SynthwaveSky.STAR_ALPHA[i] * twinkle), level);
			float s = SynthwaveSky.STAR_SIZE[i] * pixel;
			frame.aim(baseYaw + SynthwaveSky.STAR_YAW[i], SynthwaveSky.STAR_ELEV[i]);
			c.at(frame, -s, -s, r, col);
			c.at(frame, s, -s, r, col);
			c.at(frame, s, s, r, col);
			c.at(frame, -s, s, r, col);
		}
		frame.aim(baseYaw + SynthwaveSky.SUN_YAW, SynthwaveSky.SUN_ELEV);
		if (view.capVisible(frame.dx, frame.dy, frame.dz, SynthwaveSky.SUN_REACH)) {
			float tan = SynthwaveSky.TAN_SUN, d = radius * SynthwaveSky.DISC;
			for (int i = 0; i < SynthwaveSky.FACE_FILL.length; i++) {
				if (!SynthwaveSky.FACE_FILL[i]) {
					continue;
				}
				float b0 = 1 - 2 * SynthwaveSky.FACE_T[i], b1 = 1 - 2 * SynthwaveSky.FACE_T[i + 1];
				float w0 = (float) Math.sqrt(Math.max(0, 1 - b0 * b0)), w1 = (float) Math.sqrt(Math.max(0, 1 - b1 * b1));
				int c0 = SkyColors.argb(SynthwaveSky.FACE_ROW_RGB[i], level), c1 = SkyColors.argb(SynthwaveSky.FACE_ROW_RGB[i + 1], level);
				c.at(frame, -w0 * tan, b0 * tan, d, c0);
				c.at(frame, w0 * tan, b0 * tan, d, c0);
				c.at(frame, w1 * tan, b1 * tan, d, c1);
				c.at(frame, -w1 * tan, b1 * tan, d, c1);
			}
		}
		c.fadeBelowHorizon(0);
	}
	/** The eclipse's milky way and corona as {@link EclipseSky#draw} drew them. */
	static void eclipse(SkyCanvas c, float radius, float baseYaw, float level, double time, SkyView view) {
		final float deg = EclipseSky.DEG;
		c.fadeBelowHorizon(EclipseSky.HORIZON_FADE);
		SkyCanvas.SkyFrame frame = new SkyCanvas.SkyFrame();
		// The milky way
		float r = radius * EclipseSky.BAND;
		frame.aim(baseYaw + EclipseSky.BAND_YAW, EclipseSky.BAND_ELEV);
		float cs = (float) Math.cos(EclipseSky.BAND_SLOPE * deg), sn = (float) Math.sin(EclipseSky.BAND_SLOPE * deg);
		float[] a = {frame.dx, frame.dy, frame.dz};
		float[] b = {cs * frame.ux + sn * frame.vx, cs * frame.uy + sn * frame.vy, cs * frame.uz + sn * frame.vz};
		float[] pole = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
		int segments = EclipseSky.BAND_SEGMENTS;
		boolean[] shown = new boolean[segments];
		boolean any = false;
		for (int j = 0; j < segments; j++) {
			float ca = EclipseSky.ALONG_COS[j] + EclipseSky.ALONG_COS[j + 1], sa = EclipseSky.ALONG_SIN[j] + EclipseSky.ALONG_SIN[j + 1];
			float l = (float) Math.sqrt(ca * ca + sa * sa);
			ca /= l;
			sa /= l;
			shown[j] = view.capVisible(ca * a[0] + sa * b[0], ca * a[1] + sa * b[1], ca * a[2] + sa * b[2], EclipseSky.BAND_REACH);
			any |= shown[j];
		}
		for (int i = 0; any && i < EclipseSky.BAND_ROWS; i++) {
			for (int j = 0; j < segments; j++) {
				if (!shown[j]) {
					continue;
				}
				int g0 = i * (segments + 1) + j, g1 = g0 + segments + 1;
				int[][] at = {{j, i, g0}, {j + 1, i, g0 + 1}, {j + 1, i + 1, g1 + 1}, {j, i + 1, g1}};
				for (int[] p : at) {
					float ca = EclipseSky.ALONG_COS[p[0]], sa = EclipseSky.ALONG_SIN[p[0]];
					float cp = EclipseSky.ACROSS_COS[p[1]] * r, sp = EclipseSky.ACROSS_SIN[p[1]] * r;
					c.vertex((ca * a[0] + sa * b[0]) * cp + pole[0] * sp, (ca * a[1] + sa * b[1]) * cp + pole[1] * sp,
							(ca * a[2] + sa * b[2]) * cp + pole[2] * sp, SkyColors.argb(EclipseSky.BAND_RGB, EclipseSky.BAND_GRID[p[2]] * level));
				}
			}
		}
		// The corona
		r = radius * EclipseSky.CORONA;
		frame.aim(baseYaw + EclipseSky.SUN_YAW, EclipseSky.SUN_ELEV);
		if (view.capVisible(frame.dx, frame.dy, frame.dz, EclipseSky.CORONA_REACH)) {
			float tanR = (float) Math.tan(EclipseSky.SUN_RADIUS * deg);
			float alpha = level * (0.94f + 0.06f * (float) Math.sin(SkyClock.phase(time, 0.8, 0)));
			int n = EclipseSky.SEGMENTS;
			boolean[] wedge = new boolean[n];
			for (int j = 0; j < n; j++) {
				float f = EclipseSky.WEDGE_FWD[j], rt = EclipseSky.WEDGE_RIGHT[j], u = EclipseSky.WEDGE_UP[j];
				wedge[j] = view.capVisible(f * frame.dx + rt * frame.ux + u * frame.vx, f * frame.dy + rt * frame.uy + u * frame.vy,
						f * frame.dz + rt * frame.uz + u * frame.vz, EclipseSky.WEDGE_RADIUS[j]);
			}
			float[] rings = EclipseSky.RINGS;
			float[] cos = EclipseSky.RING_COS, sin = EclipseSky.RING_SIN;
			int[] grid = EclipseSky.CORONA_GRID;
			for (int i = 0; i + 1 < rings.length; i++) {
				float a0 = rings[i] * tanR, a1 = rings[i + 1] * tanR;
				int row0 = i * (n + 1), row1 = row0 + n + 1;
				for (int j = 0; j < n; j++) {
					if (!wedge[j]) {
						continue;
					}
					c.at(frame, a0 * cos[j], a0 * sin[j], r, SkyColors.argb(grid[row0 + j], alpha));
					c.at(frame, a0 * cos[j + 1], a0 * sin[j + 1], r, SkyColors.argb(grid[row0 + j + 1], alpha));
					c.at(frame, a1 * cos[j + 1], a1 * sin[j + 1], r, SkyColors.argb(grid[row1 + j + 1], alpha));
					c.at(frame, a1 * cos[j], a1 * sin[j], r, SkyColors.argb(grid[row1 + j], alpha));
				}
			}
		}
		c.fadeBelowHorizon(0);
	}

	/** The eclipse's black disc as {@link EclipseSky#drawDisc} drew it. */
	static void eclipseDisc(SkyCanvas c, float radius, float baseYaw, float level, SkyView view) {
		SkyCanvas.SkyFrame frame = new SkyCanvas.SkyFrame().aim(baseYaw + EclipseSky.SUN_YAW, EclipseSky.SUN_ELEV);
		if (!view.capVisible(frame.dx, frame.dy, frame.dz, EclipseSky.DISC_REACH)) {
			return;
		}
		float tanR = (float) Math.tan(EclipseSky.SUN_RADIUS * EclipseSky.DEG);
		int black = SkyColors.argb(EclipseSky.DISC_RGB, level);
		float r = radius * EclipseSky.CORONA * EclipseSky.DISC_LAYER;
		for (int j = 0; j < EclipseSky.SEGMENTS; j += EclipseSky.DISC_STEP) {
			int k = Math.min(EclipseSky.SEGMENTS, j + EclipseSky.DISC_STEP);
			c.at(frame, 0, 0, r, black);
			c.at(frame, 0, 0, r, black);
			c.at(frame, tanR * EclipseSky.RING_COS[k], tanR * EclipseSky.RING_SIN[k], r, black);
			c.at(frame, tanR * EclipseSky.RING_COS[j], tanR * EclipseSky.RING_SIN[j], r, black);
		}
	}
}
