package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyNightFixesTest {
	@Test
	void theStarCacheMissesWhenOnlyTheTimeOfDayChanged() {
		Object level = new Object(), camera = new Object();
		StarMemo memo = new StarMemo();
		memo.store(level, camera, 100L, 18000L, 0.5f, 1, 2, 3, 0f);
		assertTrue(memo.hit(level, camera, 100L, 18000L, 0.5f, 1, 2, 3));
		// /tick freeze, then /time set day: same game tick, same camera, new day time
		assertFalse(memo.hit(level, camera, 100L, 1000L, 0.5f, 1, 2, 3));
	}

	/** The probe the game hands the star brightness through keeps its answer until the next client tick. */
	private static final class Probe {
		double world = 0.5, kept = Double.NaN;

		double read() {
			if (Double.isNaN(kept)) {
				kept = world;
			}
			return kept;
		}

		void tick() {
			kept = Double.NaN;
		}
	}

	@Test
	void aTimeSetThatLandsBetweenClientTicksIsNotPinnedToTheOldSky() {
		Object level = new Object(), camera = new Object();
		StarMemo memo = new StarMemo();
		Probe probe = new Probe();
		// frozen game: day (0) then night (0.5) time, same tick, same camera; frames come faster than client ticks
		probe.world = 0;
		probe.tick();
		assertEquals(0f, memo.get(level, camera, 100L, 6000L, 1f, 1, 2, 3, 0, probe::read));
		// /time set 18000 is handled between two client ticks: the day time moves, the probe still holds day
		probe.world = 0.5;
		memo.get(level, camera, 100L, 18000L, 1f, 1, 2, 3, 1000, probe::read);
		probe.tick();
		assertEquals(0.5f, memo.get(level, camera, 100L, 18000L, 1f, 1, 2, 3, 1060, probe::read),
				"the night must show once the probe has ticked, not stay on the old answer");
	}

	@Test
	void anAnswerFromAfterTheSettleTimeIsKept() {
		Object level = new Object(), camera = new Object();
		StarMemo memo = new StarMemo();
		int[] reads = {0};
		java.util.function.DoubleSupplier read = () -> ++reads[0];
		memo.get(level, camera, 100L, 6000L, 1f, 1, 2, 3, 0, read);
		memo.get(level, camera, 100L, 18000L, 1f, 1, 2, 3, 1000, read);
		long late = 1000 + StarMemo.SETTLE_MS;
		memo.get(level, camera, 100L, 18000L, 1f, 1, 2, 3, late, read);
		int before = reads[0];
		assertEquals(before, (int) memo.get(level, camera, 100L, 18000L, 1f, 1, 2, 3, late + 5, read));
		assertEquals(before, reads[0], "a settled answer is reused, not read again");
	}

	@Test
	void hidingTheSkyResetsSparklesAndMeteors() throws Exception {
		SkyEffects sky = new SkyEffects(null, null, null, null);
		set(sky, "sparkleShow", 1f);
		set(sky, "meteorsAlive", 2);
		float[] age = (float[]) get(sky, "meteorAge"), life = (float[]) get(sky, "meteorLife");
		age[0] = 0.2f;
		life[0] = 1f;
		call(sky, "hideSky");
		assertEquals(0f, (float) get(sky, "sparkleShow"));
		assertEquals(0, (int) get(sky, "meteorsAlive"));
		assertTrue(age[0] >= life[0], "a shooting star must not resume mid-flight");
	}

	@Test
	void aFramesPointCounterStartsAtZero() throws Exception {
		SkyEffects sky = new SkyEffects(null, null, null, null);
		set(sky, "corner", 3);
		call(sky, "beginFrame");
		assertEquals(0, (int) get(sky, "corner"));
	}

	@Test
	void aTargetLeftMidQuadStartsTheNextFrameClean() throws Exception {
		Class<?> type = Class.forName("dev.beli.skylyrics.render.SkyEffects$Target");
		Constructor<?> make = type.getDeclaredConstructor(boolean.class);
		make.setAccessible(true);
		Object target = make.newInstance(false);
		set(target, "corner", 3);
		set(SkyCanvas.class, target, "corner", 2);
		Method begin = type.getDeclaredMethod("begin", com.mojang.blaze3d.vertex.PoseStack.Pose.class, com.mojang.blaze3d.vertex.VertexConsumer.class);
		begin.setAccessible(true);
		begin.invoke(target, null, null);
		assertEquals(0, (int) get(target, "corner"));
		assertEquals(0, (int) get(SkyCanvas.class, target, "corner"));
	}

	private static final class Recorder extends SkyCanvas {
		final List<float[]> corners = new ArrayList<>();

		@Override
		protected void put(float x, float y, float z, int argb) {
			corners.add(new float[] {x, y, z});
		}
	}

	@Test
	void aCanvasLeftMidQuadStartsTheNextFrameClean() {
		Recorder r = new Recorder();
		r.vertex(9, 9, -5, 0);
		r.vertex(9, 9, -5, 0);
		r.reset();
		float[][] square = {{-1, -1, -5}, {1, -1, -5}, {1, 1, -5}, {-1, 1, -5}};
		for (float[] p : square) {
			r.vertex(p[0], p[1], p[2], 0);
		}
		assertEquals(4, r.corners.size());
		assertEquals(-1f, r.corners.get(0)[0]);
	}

	private static void call(Object o, String name) throws Exception {
		Method m = o.getClass().getDeclaredMethod(name);
		m.setAccessible(true);
		m.invoke(o);
	}

	private static void set(Object o, String name, Object v) throws Exception {
		set(o.getClass(), o, name, v);
	}

	private static void set(Class<?> c, Object o, String name, Object v) throws Exception {
		Field f = c.getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, v);
	}

	private static Object get(Object o, String name) throws Exception {
		return get(o.getClass(), o, name);
	}

	private static Object get(Class<?> c, Object o, String name) throws Exception {
		Field f = c.getDeclaredField(name);
		f.setAccessible(true);
		return f.get(o);
	}
}
