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
	/** For polar meshes: a round patch (unit direction, angular radius in radians) holding each segment, so one can be skipped alone. */
	private float[] wedgeX, wedgeY, wedgeZ, wedgeRadius;
	/** Scratch for one draw: each corner after turning, and which rows have been turned yet. */
	private final float[] outX, outY, outZ;
	private final int[] outArgb;
	private final boolean[] turned;
	private float capX, capY, capZ, capRadius = -1;

	SkyMesh(int rows, int cols) {
		this.rows = rows;
		this.cols = cols;
		x = new float[rows * cols];
		y = new float[rows * cols];
		z = new float[rows * cols];
		rgb = new int[rows * cols];
		outX = new float[rows * cols];
		outY = new float[rows * cols];
		outZ = new float[rows * cols];
		outArgb = new int[rows * cols];
		turned = new boolean[rows];
	}

	void set(int i, int j, float px, float py, float pz, int color) {
		int k = i * cols + j;
		x[k] = px;
		y[k] = py;
		z[k] = pz;
		rgb[k] = color;
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
		boolean[] column = columnsInView(cos, sin, view);
		// What every corner shares is worked out once; each corner is then turned once, however many quads use it
		int alpha = SkyColors.argb(0, level);
		java.util.Arrays.fill(turned, false);
		for (int i = 0; i + 1 < rows; i++) {
			if (rowHeight != null && !view.heightVisible(Math.min(rowHeight[i], rowHeight[i + 1]), Math.max(rowHeight[i], rowHeight[i + 1]))) {
				continue;
			}
			turnRow(i, column, cos, sin, r, alpha);
			turnRow(i + 1, column, cos, sin, r, alpha);
			for (int j = 0; j + 1 < cols; j++) {
				if (column != null && !column[j]) {
					continue;
				}
				int k = i * cols + j;
				emit(c, k);
				emit(c, k + 1);
				emit(c, k + cols + 1);
				emit(c, k + cols);
			}
		}
	}

	/** Which columns (domes) or segments (polar meshes) can be on screen; null when every one is drawn. */
	private boolean[] columnsInView(float cos, float sin, SkyView view) {
		if (columnYaw != null) {
			float turn = (float) Math.toDegrees(Math.atan2(sin, cos));
			for (int j = 0; j + 1 < cols; j++) {
				columnShown[j] = view.yawVisible(columnYaw[j] + turn, columnYaw[j + 1] + turn);
			}
			return columnShown;
		}
		if (wedgeRadius != null) {
			for (int j = 0; j + 1 < cols; j++) {
				columnShown[j] = view.capVisibleTurned(wedgeX[j], wedgeY[j], wedgeZ[j], cos, sin, wedgeRadius[j]);
			}
			return columnShown;
		}
		return null;
	}

	/** Whether {@link #draw} would hand on any quad for this view (the same tests, nothing drawn). */
	boolean anyVisible(float cos, float sin, SkyView view) {
		if (capRadius >= 0 && !view.capVisibleTurned(capX, capY, capZ, cos, sin, capRadius)) {
			return false;
		}
		boolean[] column = columnsInView(cos, sin, view);
		boolean anyColumn = column == null;
		for (int j = 0; column != null && j + 1 < cols; j++) {
			anyColumn |= column[j];
		}
		if (!anyColumn) {
			return false;
		}
		for (int i = 0; i + 1 < rows; i++) {
			if (rowHeight == null || view.heightVisible(Math.min(rowHeight[i], rowHeight[i + 1]), Math.max(rowHeight[i], rowHeight[i + 1]))) {
				return true;
			}
		}
		return false;
	}

	/** Turns and pushes out the corners of one row that a drawn quad uses, once per draw. */
	private void turnRow(int i, boolean[] column, float cos, float sin, float r, int alpha) {
		if (turned[i]) {
			return;
		}
		turned[i] = true;
		for (int j = 0; j < cols; j++) {
			if (column != null && !(j + 1 < cols && column[j]) && !(j > 0 && column[j - 1])) {
				continue;
			}
			int k = i * cols + j;
			outX[k] = (x[k] * cos - z[k] * sin) * r;
			outY[k] = y[k] * r;
			outZ[k] = (z[k] * cos + x[k] * sin) * r;
			outArgb[k] = alpha | rgb[k] & 0xFFFFFF;
		}
	}

	private void emit(SkyCanvas c, int k) {
		c.vertex(outX[k], outY[k], outZ[k], outArgb[k]);
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
		mesh.wedges();
		return mesh;
	}

	/** Remembers, for each segment of a polar mesh, a round patch that holds all of its corners (and so every quad between them). */
	private void wedges() {
		int n = cols - 1;
		wedgeX = new float[n];
		wedgeY = new float[n];
		wedgeZ = new float[n];
		wedgeRadius = new float[n];
		columnShown = new boolean[n];
		for (int j = 0; j < n; j++) {
			float sx = 0, sy = 0, sz = 0;
			for (int i = 0; i < rows; i++) {
				for (int k = i * cols + j; k <= i * cols + j + 1; k++) {
					sx += x[k];
					sy += y[k];
					sz += z[k];
				}
			}
			float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
			float least = 1;
			if (l > 1e-3f) {
				sx /= l;
				sy /= l;
				sz /= l;
				for (int i = 0; i < rows; i++) {
					for (int k = i * cols + j; k <= i * cols + j + 1; k++) {
						least = Math.min(least, sx * x[k] + sy * y[k] + sz * z[k]);
					}
				}
			}
			wedgeX[j] = sx;
			wedgeY[j] = sy;
			wedgeZ[j] = sz;
			// A flat quad between corners stays inside a patch smaller than a half sphere; a wider one is never skipped
			float radius = l > 1e-3f ? (float) Math.acos(Math.max(-1, least)) + 0.005f : (float) Math.PI;
			wedgeRadius[j] = radius < 1.5f ? radius : (float) Math.PI;
		}
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
