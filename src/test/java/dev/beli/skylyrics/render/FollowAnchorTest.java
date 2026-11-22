package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.config.SkyLyricsConfig.Direction;
import dev.beli.skylyrics.config.SkyLyricsConfig.Layout;
import dev.beli.skylyrics.config.SkyLyricsConfig.Placement;
import dev.beli.skylyrics.lyrics.Lyrics;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import org.junit.jupiter.api.Test;

class FollowAnchorTest {
	@Test
	void withoutLazyFollowTheAnchorAlwaysChasesAtOnce() {
		assertTrue(FollowAnchor.following(false, false, 0));
		assertTrue(FollowAnchor.following(false, false, 10));
		assertEquals(Float.POSITIVE_INFINITY, FollowAnchor.speed(false));
	}

	@Test
	void lazyFollowStaysPutForSmallTurnsAndCatchesUpAfterBigOnes() {
		assertFalse(FollowAnchor.following(false, true, 20));
		assertTrue(FollowAnchor.following(false, true, 60));
		assertTrue(FollowAnchor.following(false, true, -60));
		// wraps around the 180 line
		assertTrue(FollowAnchor.following(false, true, 350 - 10 + 90));
		// keeps chasing until nearly facing it, then rests
		assertTrue(FollowAnchor.following(true, true, 20));
		assertFalse(FollowAnchor.following(true, true, 2));
		assertEquals(FollowAnchor.LAZY_SPEED, FollowAnchor.speed(true));
	}

	@Test
	void theWorldLayoutIgnoresTheHiddenPlacementSoSparklesStayWithItsWords() {
		assertTrue(FollowAnchor.floating(Placement.FLOATING, Layout.ARC, false));
		assertTrue(FollowAnchor.floating(Placement.FLOATING, Layout.LINES, false));
		assertTrue(FollowAnchor.floating(Placement.FLOATING, Layout.LINES, true));
		assertFalse(FollowAnchor.floating(Placement.SKY, Layout.LINES, false));
		// Placement is hidden for World words: one left on Floating from another layout must not pull the sparkles
		// round with your view while the words stay in the north
		assertFalse(FollowAnchor.floating(Placement.FLOATING, Layout.WORLD, false));
		assertFalse(FollowAnchor.floating(Placement.SKY, Layout.WORLD, false));
		// An instrumental's note line is drawn like Lines in World too, so there Placement still counts
		assertTrue(FollowAnchor.floating(Placement.FLOATING, Layout.WORLD, true));
		assertFalse(FollowAnchor.floating(Placement.SKY, Layout.WORLD, true));
	}

	@Test
	void aBadAnchorStartsOver() {
		assertTrue(FollowAnchor.needsInit(Float.NaN, 0));
		assertTrue(FollowAnchor.needsInit(0, Float.NaN));
		assertTrue(FollowAnchor.needsInit(Float.POSITIVE_INFINITY, 0));
		assertFalse(FollowAnchor.needsInit(10, -30));
	}

	@Test
	void joiningAWorldMakesBothAnchorsStartFresh() throws Exception {
		SkyEffects sky = new SkyEffects(null, null, null, null);
		set(sky, "anchorYaw", 90f);
		assertFalse(sky.anchorNeedsInit());
		sky.onJoinWorld();
		assertTrue(sky.anchorNeedsInit());
		LyricsRenderer r = new LyricsRenderer(null, null, null);
		set(r, "anchorYaw", 90f);
		assertFalse(r.anchorNeedsInit());
		r.onJoinWorld();
		assertTrue(r.anchorNeedsInit());
	}

	/**
	 * An instrumental's note line is drawn like Lines in the World layout too, and goes by Lazy follow there (left on
	 * from another layout): the sparkles round it must wait and catch up the same way, not snap to the view and leave
	 * it behind as soon as you look a little to the side.
	 */
	@Test
	void anInstrumentalsSparklesStayWithItsNoteLineWhenYouLookAroundInTheWorldLayout() throws Exception {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.layout = Layout.WORLD;
		cfg.placement = Placement.SKY;
		cfg.direction = Direction.FOLLOW;
		cfg.lazyFollow = true;
		cfg.viewLock = false;
		LyricsRenderer lyrics = new LyricsRenderer(null, null, null);
		set(lyrics, "shown", Lyrics.INSTRUMENTAL);
		SkyEffects sky = new SkyEffects(lyrics, null, null, null);
		ViewLock.release();
		for (float yaw : new float[] {0, 10, 20, 30, 20, 60, 90, 90, 90}) {
			Camera camera = looking(yaw);
			step(lyrics, cfg, camera);
			step(sky, cfg, camera);
			assertEquals((float) get(lyrics, "anchorYaw"), (float) get(sky, "anchorYaw"), 1e-3f, "looking at " + yaw);
		}
	}

	/**
	 * While Elytra mode carries the World words (gliding, riding, flying), new words come where you look even with a
	 * World direction or a locked view, and the ones up turn with you: the sparkles go round there too, gliding over
	 * rather than jumping when the travel starts, and back to the words' side of the sky when it stops.
	 */
	@Test
	void whileTheWorldWordsTravelWithYouTheSparklesComeAlong() throws Exception {
		SkyLyricsConfig cfg = new SkyLyricsConfig();
		cfg.layout = Layout.WORLD;
		cfg.direction = Direction.NORTH;
		cfg.viewLock = false;
		ViewLock.release();
		LyricsRenderer lyrics = new LyricsRenderer(null, null, null);
		Object words = get(lyrics, "worldWords");
		SkyEffects sky = new SkyEffects(lyrics, null, null, null);
		// Standing, facing west: the sparkles keep to the north with the words
		Camera west = looking(90);
		for (int frame = 0; frame < 100; frame++) {
			step(sky, cfg, west);
		}
		assertEquals(180, Math.abs((float) get(sky, "anchorYaw")), 0.5f, "standing: round the words in the north");
		// Gliding west: the words come where you look, and the sparkles go over to them, not all in one frame
		set(words, "gliding", true);
		step(sky, cfg, west);
		float first = (float) get(sky, "anchorYaw");
		assertTrue(Math.abs(Math.abs(first) - 180) < 45, "no jump as the travel starts: " + first);
		for (int frame = 0; frame < 100; frame++) {
			step(sky, cfg, west);
		}
		assertEquals(90, (float) get(sky, "anchorYaw"), 0.5f, "gliding: round the words in front of you");
		// A View lock doesn't hold them back either, as new words don't keep to it while you travel: pinned looking
		// east, then steering south
		cfg.viewLock = true;
		step(sky, cfg, looking(-90));
		for (int frame = 0; frame < 100; frame++) {
			step(sky, cfg, looking(0));
		}
		assertEquals(0, (float) get(sky, "anchorYaw"), 0.5f, "gliding with a lock: still round the words in front of you");
		// Landed: back to the words' side of the sky, the pinned view in the east
		set(words, "gliding", false);
		for (int frame = 0; frame < 100; frame++) {
			step(sky, cfg, looking(0));
		}
		assertEquals(-90, (float) get(sky, "anchorYaw"), 0.5f, "landed: back at the pinned view");
		cfg.viewLock = false;
		ViewLock.release();
	}

	private static Camera looking(float yaw) {
		return new Camera() {
			{
				setRotation(yaw, 0);
			}
		};
	}

	private static void step(Object anchors, SkyLyricsConfig cfg, Camera camera) throws Exception {
		Method update = anchors.getClass().getDeclaredMethod("updateAnchor", SkyLyricsConfig.class, Camera.class, float.class);
		update.setAccessible(true);
		update.invoke(anchors, cfg, camera, 0.05f);
	}

	private static Object get(Object o, String name) throws Exception {
		java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(o);
	}

	private static void set(Object o, String name, Object v) throws Exception {
		java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, v);
	}
}
