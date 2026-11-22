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
}
