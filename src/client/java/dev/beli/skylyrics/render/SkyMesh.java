package dev.beli.skylyrics.render;

import dev.beli.skylyrics.render.SkyCanvas.SkyFrame;

/**
 * Points on the sky in rows and columns, with their colours; directions are for lyrics facing south.
 * Painted skies build these once and only turn them to the lyrics' direction each frame.
 */
final class SkyMesh {
	/** The finished, opaque sky colour in a direction (a unit vector, lyrics facing south). */
	interface Paint {
		int at(float x, float y, float z);
	}

	final int rows, cols;
	final float[] x, y, z;
	final int[] rgb;
	/** For domes: each column's yaw (degrees, lyrics facing south) and each row's height; null for other shapes. */
	private float[] columnYaw, rowHeight;
	/** For round patches: the unit direction of the middle and the angular radius (radians) that holds every point. */
	/** Scratch for one draw (the render thread only): which columns are in view. */
	private boolean[] columnShown;
	private float capX, capY, capZ, capRadius = -1;

	SkyMesh(int rows, int cols) {
		this.rows = rows;
		this.cols = cols;
		x = new float[rows * cols];
		y = new float[rows * cols];
		z = new float[rows * cols];
		rgb = new int[rows * cols];
	}

	void set(int i, int j, float px, float py, float pz, int color) {
		int k = i * cols + j;
		x[k] = px;
		y[k] = py;
		z[k] = pz;
		rgb[k] = color;
	}

	/** Works out every point's colour again, for skies that change slowly. */
	void repaint(Paint paint) {
		for (int k = 0; k < rgb.length; k++) {
			rgb[k] = paint.at(x[k], y[k], z[k]);
		}
	}

	/** Turned by the yaw (given as cosine and sine) and pushed out to {@code r}. */
	void draw(SkyCanvas c, float cos, float sin, float r, float level) {
		draw(c, cos, sin, r, level, SkyView.ALL);
	}

	/**
	 * Like {@link #draw(SkyCanvas, float, float, float, float)} but leaves out whatever the camera can't see: columns
	 * and rows of a dome, or a whole round patch. What is drawn has exactly the same corners in the same order.
	 */
	void draw(SkyCanvas c, float cos, float sin, float r, float level, SkyView view) {
		if (capRadius >= 0 && !view.capVisibleTurned(capX, capY, capZ, cos, sin, capRadius)) {
			return;
		}
		boolean[] column = null;
		if (columnYaw != null) {
			float turn = (float) Math.toDegrees(Math.atan2(sin, cos));
			column = columnShown;
			for (int j = 0; j + 1 < cols; j++) {
				column[j] = view.yawVisible(columnYaw[j] + turn, columnYaw[j + 1] + turn);
			}
		}
		for (int i = 0; i + 1 < rows; i++) {
			if (rowHeight != null && !view.heightVisible(Math.min(rowHeight[i], rowHeight[i + 1]), Math.max(rowHeight[i], rowHeight[i + 1]))) {
				continue;
			}
			for (int j = 0; j + 1 < cols; j++) {
				if (column != null && !column[j]) {
					continue;
				}
				corner(c, i * cols + j, cos, sin, r, level);
				corner(c, i * cols + j + 1, cos, sin, r, level);
				corner(c, (i + 1) * cols + j + 1, cos, sin, r, level);
				corner(c, (i + 1) * cols + j, cos, sin, r, level);
			}
		}
	}

	private void corner(SkyCanvas c, int k, float cos, float sin, float r, float level) {
		c.vertex((x[k] * cos - z[k] * sin) * r, y[k] * r, (z[k] * cos + x[k] * sin) * r, SkyColors.argb(rgb[k], level));
	}

	/** Marks this as a dome (rows of height in degrees, {@code columns} equal steps of yaw round) so the draw can skip what's out of view. */
	SkyMesh asDome(float[] elevations, int columns) {
		rowHeight = elevations.clone();
		columnYaw = new float[columns + 1];
		columnShown = new boolean[columns];
		for (int j = 0; j <= columns; j++) {
			columnYaw[j] = 360f * j / columns;
		}
		return this;
	}

	/** A full dome: one row per elevation (degrees, bottom to top), {@code columns} steps round. */
	static SkyMesh dome(float[] elevations, int columns, Paint paint) {
		SkyMesh mesh = new SkyMesh(elevations.length, columns + 1).asDome(elevations, columns);
		for (int i = 0; i < elevations.length; i++) {
			double e = Math.toRadians(elevations[i]);
			for (int j = 0; j <= columns; j++) {
				double yaw = 2 * Math.PI * j / columns;
				float px = (float) (-Math.cos(e) * Math.sin(yaw)), py = (float) Math.sin(e), pz = (float) (Math.cos(e) * Math.cos(yaw));
				mesh.set(i, j, px, py, pz, paint.at(px, py, pz));
			}
		}
		return mesh;
	}

	/** Rings (tangents of their angular radius) round the centre of {@code f}. */
	static SkyMesh polar(SkyFrame f, float[] rings, int segments, Paint paint) {
		SkyMesh mesh = new SkyMesh(rings.length, segments + 1);
		for (int i = 0; i < rings.length; i++) {
			for (int j = 0; j <= segments; j++) {
				double a = 2 * Math.PI * j / segments;
				float ta = rings[i] * (float) Math.cos(a), tb = rings[i] * (float) Math.sin(a);
				float px = f.dx + ta * f.ux + tb * f.vx, py = f.dy + ta * f.uy + tb * f.vy, pz = f.dz + ta * f.uz + tb * f.vz;
				float l = (float) Math.sqrt(px * px + py * py + pz * pz);
				mesh.set(i, j, px / l, py / l, pz / l, paint.at(px / l, py / l, pz / l));
			}
		}
		mesh.capAround(f);
		return mesh;
	}

	/** Remembers the round patch round the middle of {@code f} that holds every point, so it can be skipped whole. */
	private void capAround(SkyFrame f) {
		capX = f.dx;
		capY = f.dy;
		capZ = f.dz;
		float least = 1;
		for (int k = 0; k < x.length; k++) {
			least = Math.min(least, capX * x[k] + capY * y[k] + capZ * z[k]);
		}
		// A hair wider than the farthest point, to be safe against rounding
		capRadius = (float) Math.acos(Math.max(-1, least)) + 0.005f;
	}
}
