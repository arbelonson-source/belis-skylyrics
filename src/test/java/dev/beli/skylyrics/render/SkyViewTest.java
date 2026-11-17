package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyViewTest {
	private static final float TAN_V = (float) Math.tan(Math.toRadians(35)), TAN_H = TAN_V * 16 / 9;

	/** A camera at a Minecraft yaw and pitch (degrees) with a 70 degree vertical field of view at 16:9. */
	private static SkyView looking(float yawDeg, float pitchDeg) {
		double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
		return new SkyView().set((float) (-Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (Math.cos(y) * Math.cos(p)), TAN_H, TAN_V);
	}

	@Test
	void aColumnStraightAheadIsKept() {
		assertTrue(looking(0, 0).yawVisible(-5, 5));
		assertTrue(looking(90, 10).yawVisible(85, 95));
	}

	@Test
	void aColumnBehindIsDropped() {
		assertFalse(looking(0, 0).yawVisible(175, 185));
		assertFalse(looking(0, 0).yawVisible(170, 180));
		assertFalse(looking(-90, 0).yawVisible(80, 100));
	}

	@Test
	void aColumnJustPastTheEdgeIsKeptByTheMargin() {
		// Half the width at 16:9 and 70 degrees is about 51 degrees (the corners reach further still)
		float halfWidth = (float) Math.toDegrees(Math.atan(TAN_H));
		assertTrue(looking(0, 0).yawVisible(halfWidth + 2, halfWidth + 3));
		assertTrue(looking(0, 0).yawVisible(-halfWidth - 3, -halfWidth - 2));
		// ...but a long way past it goes
		assertFalse(looking(0, 0).yawVisible(halfWidth + SkyView.MARGIN + 40, halfWidth + SkyView.MARGIN + 50));
	}

	@Test
	void lookingStraightUpOrDownKeepsEveryColumn() {
		for (float pitch : new float[] {90, -90, 89.9f, 80, -80}) {
			SkyView v = looking(33, pitch);
			for (float yaw = -180; yaw < 180; yaw += 10) {
				assertTrue(v.yawVisible(yaw, yaw + 10), "pitch " + pitch + " yaw " + yaw);
			}
		}
	}

	@Test
	void wrapsRoundAtPlusAndMinus180() {
		SkyView south = looking(180, 0);
		assertTrue(south.yawVisible(170, 180));
		assertTrue(south.yawVisible(180, 190));
		assertTrue(south.yawVisible(-180, -170));
		assertTrue(south.yawVisible(530, 540));
		assertFalse(south.yawVisible(-10, 0));
		SkyView other = looking(-175, 0);
		assertTrue(other.yawVisible(175, 185));
		assertEquals(-170, SkyView.wrap(190), 1e-4f);
		assertEquals(170, SkyView.wrap(-190), 1e-4f);
		assertEquals(10, SkyView.wrap(730), 1e-4f);
	}

	@Test
	void anUnknownCameraSeesEverything() {
		assertTrue(SkyView.ALL.yawVisible(170, 190));
		assertTrue(SkyView.ALL.heightVisible(-5, 0));
		assertTrue(SkyView.ALL.capVisible(0, 0, -1, 0.01f));
		assertTrue(new SkyView().set(0, 0, 1, TAN_H, TAN_V).clear().capVisible(0, 0, -1, 0.01f));
		assertTrue(new SkyView().set(0, 0, 0, TAN_H, TAN_V).capVisible(0, 0, -1, 0.01f));
	}

	@Test
	void heightsOutsideTheViewAreDropped() {
		SkyView up = looking(0, 80);
		assertFalse(up.heightVisible(-3, 0));
		assertTrue(up.heightVisible(60, 78));
		SkyView level = looking(0, 0);
		assertTrue(level.heightVisible(-3, 0));
		assertTrue(level.heightVisible(40, 52));
		// 35 degrees up to the screen's top edge, plus the margin (the corners reach further than the edge)
		assertFalse(level.heightVisible(80, 90));
	}

	@Test
	void roundPatchesFollowTheLook() {
		SkyView v = looking(0, 0);
		assertTrue(v.capVisible(0, 0, 1, 0.05f));
		assertFalse(v.capVisible(0, 0, -1, 0.05f));
		// Just off the corner of the screen but a big patch: kept
		double a = Math.toRadians(70);
		assertTrue(v.capVisible((float) -Math.sin(a), 0, (float) Math.cos(a), 0.3f));
		assertFalse(v.capVisible(0, 1, 0, 0.05f));
		// Turned by the yaw the lyrics face (90 degrees: south becomes west)
		assertTrue(looking(90, 0).capVisibleTurned(0, 0, 1, 0, 1, 0.05f));
	}

	@Test
	void aWideColumnCountsByItsNearEdgeNotItsMiddle() {
		float reach = SkyView.halfYawRange(0, (float) Math.toDegrees(Math.atan(TAN_H)) + SkyView.MARGIN, (float) Math.toDegrees(Math.atan(TAN_V)) + SkyView.MARGIN);
		SkyView v = looking(0, 0);
		// 30 degrees wide, its middle well past the reach but its near edge inside it
		assertTrue(v.yawVisible(reach - 3, reach + 27), "middle at " + (reach + 12));
		assertFalse(v.yawVisible(reach + 3, reach + 33));
		assertTrue(SkyView.yawRangeVisible(60, 90, 0, 63));
		assertFalse(SkyView.yawRangeVisible(70, 100, 0, 63));
	}

	@Test
	void aRoundPatchReachesTheCornerOfTheWidenedScreenAndNoFurther() {
		// The corner of the margin-widened screen is farther from the look direction than its sides
		double halfH = Math.toDegrees(Math.atan(TAN_H)) + SkyView.MARGIN, halfV = Math.toDegrees(Math.atan(TAN_V)) + SkyView.MARGIN;
		double cone = Math.toDegrees(Math.atan(Math.hypot(Math.tan(Math.toRadians(halfH)), Math.tan(Math.toRadians(halfV)))));
		assertTrue(cone > halfH + 1, "the corner is farther than the side: " + cone + " vs " + halfH);
		SkyView v = looking(0, 0);
		double inside = Math.toRadians(cone - 0.5), outside = Math.toRadians(cone + 0.5);
		assertTrue(v.capVisible((float) Math.sin(inside), 0, (float) Math.cos(inside), 0));
		assertFalse(v.capVisible((float) Math.sin(outside), 0, (float) Math.cos(outside), 0));
	}

	@Test
	void yawReachGrowsAsYouLookUp() {
		assertEquals(180, SkyView.halfYawRange(85, 30, 30));
		assertEquals(180, SkyView.halfYawRange(-60, 30, 30));
		assertEquals(30, SkyView.halfYawRange(0, 30, 30), 1e-3f);
		assertTrue(SkyView.halfYawRange(40, 30, 30) > SkyView.halfYawRange(20, 30, 30));
		assertTrue(SkyView.halfYawRange(40, 30, 30) < 180);
		// The farthest yaw is at the top corner of the screen: forward + tanH right + tanV up, for a camera tilted up 40 degrees
		double p = Math.toRadians(40), th = Math.tan(Math.toRadians(30)), tv = Math.tan(Math.toRadians(30));
		double ahead = Math.cos(p) - tv * Math.sin(p);
		assertEquals(Math.toDegrees(Math.atan2(th, ahead)), SkyView.halfYawRange(40, 30, 30), 1e-3f);
		assertEquals(SkyView.halfYawRange(40, 30, 30), SkyView.halfYawRange(-40, 30, 30), 1e-3f, "down is the same as up");
	}
}
