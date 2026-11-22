package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

/** Collects the corners a sky hands on, as bits, so that two pictures can be compared with nothing allowed to differ at all. */
final class SkyRecorder extends SkyCanvas {
	final List<int[]> corners = new ArrayList<>();

	@Override
	protected void put(float x, float y, float z, int argb) {
		corners.add(new int[] {Float.floatToIntBits(x), Float.floatToIntBits(y), Float.floatToIntBits(z), argb});
	}

	/** Fails unless {@code now} handed on exactly the corners {@code before} did, in the same order. */
	static void assertSame(SkyRecorder before, SkyRecorder now, String what) {
		assertEquals(before.corners.size(), now.corners.size(), what + ": corners handed on");
		for (int k = 0; k < before.corners.size(); k++) {
			assertArrayEquals(before.corners.get(k), now.corners.get(k), what + ": corner " + k);
		}
	}

	static SkyView looking(float yawDeg, float pitchDeg) {
		float tanV = (float) Math.tan(Math.toRadians(35)), tanH = tanV * 16 / 9;
		double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
		return new SkyView().set((float) (-Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (Math.cos(y) * Math.cos(p)), tanH, tanV);
	}
}
