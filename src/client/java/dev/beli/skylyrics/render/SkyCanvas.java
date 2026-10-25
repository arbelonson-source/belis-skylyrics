package dev.beli.skylyrics.render;

/**
 * Takes the sky shapes as quads around the camera (which sits at the origin) and hands each one on
 * wound so its front faces the camera. Render types may cull back faces or not; one correctly wound
 * copy draws exactly once either way, so translucent layers don't double up.
 * Free of Minecraft classes, so the shapes can be checked in plain unit tests.
 */
abstract class SkyCanvas {
	private final float[] qx = new float[4], qy = new float[4], qz = new float[4];
	private final int[] qc = new int[4];
	private int corner;
	/** Sine of the depth below the horizon where shapes have faded out; 0 = no fading. */
	private float horizonFade;

	/** Receives one corner of a quad that already faces the camera. */
	protected abstract void put(float x, float y, float z, int argb);

	/**
	 * Fades whatever is drawn next out below the horizon, over {@code degrees} (0 turns it off). Low
	 * shapes then don't paint over distant land seen from up high.
	 */
	final void fadeBelowHorizon(float degrees) {
		horizonFade = (float) Math.sin(Math.toRadians(degrees));
	}

	/** Adds one corner; every fourth call completes a quad. Corners go round the quad in either direction. */
	final void vertex(float x, float y, float z, int argb) {
		if (horizonFade > 0 && y < 0) {
			float below = -y / (float) Math.sqrt(x * x + y * y + z * z);
			float keep = 1 - SkyColors.smooth(below / horizonFade);
			argb = Math.round((argb >>> 24) * keep) << 24 | argb & 0xFFFFFF;
		}
		qx[corner] = x;
		qy[corner] = y;
		qz[corner] = z;
		qc[corner] = argb;
		if (++corner < 4) {
			return;
		}
		corner = 0;
		if (facesOrigin(qx, qy, qz)) {
			for (int i = 0; i < 4; i++) {
				put(qx[i], qy[i], qz[i], qc[i]);
			}
		} else {
			for (int i = 3; i >= 0; i--) {
				put(qx[i], qy[i], qz[i], qc[i]);
			}
		}
	}

	/**
	 * True when the corners run counter-clockwise as seen from the origin, which is what the GPU calls
	 * the front. Uses the diagonals, so a quad with two equal corners (a fan's tip) still works.
	 */
	static boolean facesOrigin(float[] x, float[] y, float[] z) {
		float ax = x[2] - x[0], ay = y[2] - y[0], az = z[2] - z[0];
		float bx = x[3] - x[1], by = y[3] - y[1], bz = z[3] - z[1];
		float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
		float cx = x[0] + x[1] + x[2] + x[3], cy = y[0] + y[1] + y[2] + y[3], cz = z[0] + z[1] + z[2] + z[3];
		return nx * cx + ny * cy + nz * cz < 0;
	}

	/** A point on the sphere of the given radius, {@code yaw} in Minecraft degrees (0 = south, 90 = west). */
	final void sky(float yawDeg, float elevDeg, float radius, int argb) {
		float yaw = (float) Math.toRadians(yawDeg), elev = (float) Math.toRadians(elevDeg);
		float h = radius * (float) Math.cos(elev);
		vertex(-h * (float) Math.sin(yaw), radius * (float) Math.sin(elev), h * (float) Math.cos(yaw), argb);
	}

	/**
	 * A point near the centre of {@code frame}: {@code a} to the right and {@code b} up, both as tangents
	 * of the angle from the centre, pushed out onto the sphere.
	 */
	final void at(SkyFrame frame, float a, float b, float radius, int argb) {
		float x = frame.dx + a * frame.ux + b * frame.vx;
		float y = frame.dy + a * frame.uy + b * frame.vy;
		float z = frame.dz + a * frame.uz + b * frame.vz;
		float k = radius / (float) Math.sqrt(x * x + y * y + z * z);
		vertex(x * k, y * k, z * k, argb);
	}

	/** The middle of a shape in the sky with its right and up directions, all unit length. */
	static final class SkyFrame {
		float dx, dy, dz, ux, uy, uz, vx, vy, vz;

		/** Centres on a compass direction ({@code yaw} in Minecraft degrees) and height, upright. */
		SkyFrame aim(float yawDeg, float elevDeg) {
			float yaw = (float) Math.toRadians(yawDeg), elev = (float) Math.toRadians(elevDeg);
			float c = (float) Math.cos(elev);
			return aim(-c * (float) Math.sin(yaw), (float) Math.sin(elev), c * (float) Math.cos(yaw));
		}

		/** Centres on a direction, with "up" towards the zenith (the direction must not be straight up). */
		SkyFrame aim(float x, float y, float z) {
			float l = (float) Math.sqrt(x * x + y * y + z * z);
			dx = x / l;
			dy = y / l;
			dz = z / l;
			// right = d x up, up' = right x d
			float h = (float) Math.sqrt(dx * dx + dz * dz);
			ux = -dz / h;
			uy = 0;
			uz = dx / h;
			vx = -dx * dy / h;
			vy = h;
			vz = -dz * dy / h;
			return this;
		}
	}
}
