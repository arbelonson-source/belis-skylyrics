package dev.beli.skylyrics.render;

/**
 * Where the camera is looking, for skipping sky shapes it can't see. The screen is widened by a generous margin
 * all round, so a shape is only dropped when it is clearly outside; nothing may pop in at the edge of the screen.
 * Looking close to straight up or down the view takes in the zenith or nadir, so no column is ever dropped then. Free of Minecraft classes so it can be unit tested.
 */
final class SkyView {
	/** Extra degrees added all round the screen's corners before anything is dropped (also absorbs head bob and lag). */
	static final float MARGIN = 12;

	/** Draws everything: used when no camera is known yet, and by callers that don't cull. */
	static final SkyView ALL = new SkyView();

	private static final float DEG = (float) (Math.PI / 180);

	private boolean known;
	/** Unit look direction. */
	private float fx, fy, fz;
	/** Look yaw and pitch (degrees, Minecraft yaw: 0 = south, 90 = west), and the half angle of the cone holding the whole view with margin. */
	private float yaw, pitch, cone;
	/** How far (degrees) the view, with margin, reaches up and down from the look direction. */
	private float heightReach;
	/** How far (degrees) in yaw the cone reaches either side of {@link #yaw}; 180 = all the way round. */
	private float yawReach;

	/** Sets the view from the look direction and the tangents of half the horizontal and vertical field of view. */
	SkyView set(float lookX, float lookY, float lookZ, float tanHalfH, float tanHalfV) {
		float l = (float) Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
		if (!(l > 1e-6f) || !(tanHalfH > 0) || !(tanHalfV > 0) || Float.isInfinite(tanHalfH + tanHalfV)) {
			return clear();
		}
		fx = lookX / l;
		fy = lookY / l;
		fz = lookZ / l;
		yaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
		pitch = (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, fy))));
		// Half the field of view each way, widened by the margin
		float halfH = (float) Math.toDegrees(Math.atan(tanHalfH)) + MARGIN, halfV = (float) Math.toDegrees(Math.atan(tanHalfV)) + MARGIN;
		yawReach = halfYawRange(pitch, halfH, halfV);
		cone = halfH >= 90 || halfV >= 90 ? 180
				: (float) Math.toDegrees(Math.atan(Math.hypot(Math.tan(halfH * DEG), Math.tan(halfV * DEG))));
		// A wide screen reaches lower (or higher) at its sides than at its middle, so heights use the whole cone
		heightReach = cone;
		known = true;
		return this;
	}

	/** Forgets the camera: everything counts as visible. */
	SkyView clear() {
		known = false;
		return this;
	}

	/**
	 * How far in yaw (degrees, each way) a view looking at {@code pitchDeg}, with half the field of view
	 * {@code halfH} across and {@code halfV} up and down (degrees, margin included), can reach. The farthest
	 * yaw is at a top or bottom corner of the screen. 180 when the view takes in the zenith or nadir (or is
	 * a half turn wide), where every yaw is visible.
	 */
	static float halfYawRange(float pitchDeg, float halfH, float halfV) {
		if (halfH >= 90 || Math.abs(pitchDeg) + halfV >= 90) {
			return 180;
		}
		double ahead = Math.cos(pitchDeg * DEG) - Math.tan(halfV * DEG) * Math.abs(Math.sin(pitchDeg * DEG));
		return ahead <= 0 ? 180 : (float) Math.toDegrees(Math.atan2(Math.tan(halfH * DEG), ahead));
	}

	/** Whether the yaw range {@code loDeg..hiDeg} (hi - lo under 180, either may be past +-180) can be on screen. */
	boolean yawVisible(float loDeg, float hiDeg) {
		return !known || yawReach >= 180 || yawRangeVisible(loDeg, hiDeg, yaw, yawReach);
	}

	/** The pure test behind {@link #yawVisible}: does the range come within {@code reach} degrees of {@code viewYaw}? */
	static boolean yawRangeVisible(float loDeg, float hiDeg, float viewYaw, float reach) {
		float mid = (loDeg + hiDeg) / 2, half = Math.abs(hiDeg - loDeg) / 2;
		float d = Math.abs(wrap(mid - viewYaw));
		return d - half <= reach;
	}

	/** Degrees folded into -180..180. */
	static float wrap(float deg) {
		float d = deg % 360;
		if (d > 180) {
			d -= 360;
		} else if (d < -180) {
			d += 360;
		}
		return d;
	}

	/** Whether heights between {@code loDeg} and {@code hiDeg} above the horizon can be on screen. */
	boolean heightVisible(float loDeg, float hiDeg) {
		return !known || hiDeg >= pitch - heightReach && loDeg <= pitch + heightReach;
	}

	/** Whether a round patch of sky (unit direction, angular radius in radians) can reach the screen. */
	boolean capVisible(float dx, float dy, float dz, float radius) {
		if (!known || cone >= 180) {
			return true;
		}
		float reach = cone * DEG + radius;
		if (reach >= Math.PI) {
			return true;
		}
		return dx * fx + dy * fy + dz * fz >= (float) Math.cos(reach);
	}

	/** Like {@link #capVisible} for a direction given for lyrics facing south, then turned by the yaw (cosine and sine). */
	boolean capVisibleTurned(float x, float y, float z, float cos, float sin, float radius) {
		return capVisible(x * cos - z * sin, y, z * cos + x * sin, radius);
	}
}
