package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.beli.skylyrics.config.SkyLyricsConfig.SkyEffect;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * On the synthwave and moonlit skies the constellations go out with the painted sky, through vanilla's text background
 * pipeline, whose shader throws away every pixel fainter than a tenth. Their lines were sent at 0.4 of the sky's
 * strength, so they were thrown away whenever the sky showed under a quarter (dusk and dawn, a low Strength, rain on
 * 1.21.1 to 1.21.10) while the sky and the patterns' stars still showed, and the constellations' own fade in and out
 * ended in a jump. Drives the real SkyEffects.drawPainted against a recording vertex consumer.
 */
class PaintedConstellationsTest {
	/** rendertype_text_background.fsh (and 26.x's text.fsh): {@code if (color.a < 0.1) discard;} */
	private static final float DISCARD = 0.1f;
	private static final float FAR = 100;
	/** Constellations sit at 0.87 of the far radius, nearer than every layer of either painted sky (0.91 and out). */
	private static final float NEAREST_SKY_LAYER = 0.9f;

	record Corner(float x, float y, float z, int argb) {
		float alpha() {
			return (argb >>> 24) / 255f;
		}

		float distance() {
			return (float) Math.sqrt(x * x + y * y + z * z);
		}
	}

	private static List<Corner> drawPainted(SkyEffect effect, float baseYaw, float amount, float patternShow) throws Exception {
		SkyEffects e = new SkyEffects(null, null, null, null);
		set(e, "effect", effect);
		set(e, "starPatterns", true);
		set(e, "patternShow", patternShow);
		set(e, "amount", amount);
		set(e, "far", FAR);
		set(e, "baseYaw", baseYaw);
		set(e, "pixel", 0.002f);
		set(e, "clock", 12.0);
		// The view this frame was "already worked out": the fresh one, which counts the whole sky as on screen
		set(e, "viewFrame", ScreenLayer.frames());
		List<Corner> out = new ArrayList<>();
		Method m = SkyEffects.class.getDeclaredMethod("drawPainted", PoseStack.Pose.class, VertexConsumer.class);
		m.setAccessible(true);
		m.invoke(e, new PoseStack().last(), recorder(out));
		return out;
	}

	private static void set(Object o, String name, Object v) throws Exception {
		Field f = SkyEffects.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, v);
	}

	/** Keeps every corner the painted sky's vertex consumer gets: the position, then the colour set on it. */
	private static VertexConsumer recorder(List<Corner> out) {
		VertexConsumer[] self = new VertexConsumer[1];
		float[] at = new float[3];
		self[0] = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class<?>[] {VertexConsumer.class},
				(proxy, m, args) -> {
					if (m.getName().equals("addVertex") && args.length == 4) {
						at[0] = (Float) args[1];
						at[1] = (Float) args[2];
						at[2] = (Float) args[3];
					} else if (m.getName().equals("setColor") && args.length == 1 && args[0] instanceof Integer argb) {
						out.add(new Corner(at[0], at[1], at[2], argb));
					}
					return m.getReturnType() == VertexConsumer.class ? self[0] : null;
				});
		return self[0];
	}

	private static List<Corner> constellations(List<Corner> all) {
		List<Corner> near = new ArrayList<>();
		for (Corner c : all) {
			if (c.distance() < FAR * NEAREST_SKY_LAYER) {
				near.add(c);
			}
		}
		return near;
	}

	/** How many corners the constellations send when every pattern is in view: four per line, then four per star. */
	private static int constellationCorners() {
		int n = 0;
		for (Constellations.Pattern p : Constellations.PATTERNS) {
			n += 4 * (p.lines().length / 2 + p.stars().length / 2);
		}
		return n;
	}

	/** The painted sky's own colour in the direction of {@code c}, turned back to the lyrics facing south. */
	private static int behind(SkyEffect effect, float baseYaw, Corner c) {
		float r = c.distance(), x = c.x() / r, y = c.y() / r, z = c.z() / r;
		float cos = (float) Math.cos(Math.toRadians(baseYaw)), sin = (float) Math.sin(Math.toRadians(baseYaw));
		float sx = x * cos + z * sin, sz = z * cos - x * sin;
		return effect == SkyEffect.MOONLIGHT ? MoonlitSky.background(sx, y, sz) : SynthwaveSky.background(sx, y, sz);
	}

	/** What the pixel ends up as over a fully shown painted sky of colour {@code sky}: {@code c} blended on by its alpha. */
	private static int over(int sky, Corner c) {
		return SkyColors.mix(sky, c.argb() & 0xFFFFFF, c.alpha());
	}

	private static int farthest(int a, int b) {
		return Math.max(Math.abs((a >> 16 & 255) - (b >> 16 & 255)),
				Math.max(Math.abs((a >> 8 & 255) - (b >> 8 & 255)), Math.abs((a & 255) - (b & 255))));
	}

	@Test
	void theLinesShowWheneverThePaintedSkyDoes() throws Exception {
		for (SkyEffect effect : new SkyEffect[] {SkyEffect.SYNTHWAVE, SkyEffect.MOONLIGHT}) {
			// A fifth of the sky: early dusk at the default Strength, or a whole night at Strength 0.14
			List<Corner> all = drawPainted(effect, 0, 0.2f, 1);
			float skyAlpha = 0;
			for (Corner c : all) {
				if (c.distance() >= FAR * NEAREST_SKY_LAYER) {
					skyAlpha = Math.max(skyAlpha, c.alpha());
				}
			}
			assertTrue(skyAlpha >= DISCARD, effect + ": the painted sky itself must show at a fifth (" + skyAlpha + ")");
			List<Corner> stars = constellations(all);
			assertTrue(stars.size() == constellationCorners(), effect + ": every pattern drawn, " + stars.size());
			for (Corner c : stars) {
				assertTrue(c.alpha() >= DISCARD, effect + ": a constellation corner sent at alpha " + c.alpha()
						+ " is thrown away while the sky behind it shows at " + skyAlpha);
			}
		}
	}

	@Test
	void constellationsFadeIntoThePaintedSkyInsteadOfJumping() throws Exception {
		for (SkyEffect effect : new SkyEffect[] {SkyEffect.SYNTHWAVE, SkyEffect.MOONLIGHT}) {
			List<Corner> full = constellations(drawPainted(effect, 0, 1, 1));
			for (float show : new float[] {0.1f, 0.2f, 0.3f, 0.45f, 0.6f, 0.8f}) {
				List<Corner> part = constellations(drawPainted(effect, 0, 1, show));
				assertTrue(part.size() == full.size(), effect + " at " + show + ": " + part.size());
				float share = SkyEffects.smooth(show);
				for (int i = 0; i < part.size(); i++) {
					Corner c = part.get(i);
					assertTrue(c.alpha() >= DISCARD, effect + " fading in at " + show + ": a corner at alpha " + c.alpha()
							+ " is thrown away, so the constellations jump in later instead of fading");
					int sky = behind(effect, 0, c);
					int now = farthest(over(sky, c), sky), whole = farthest(over(sky, full.get(i)), sky);
					assertTrue(now <= share * whole + 3, effect + " fading in at " + show + ": " + now + " of " + whole
							+ " away from the sky already (should be about " + share + " of it)");
				}
			}
		}
	}

	@Test
	void overAFullPaintedSkyTheConstellationsLookAsBefore() throws Exception {
		for (SkyEffect effect : new SkyEffect[] {SkyEffect.SYNTHWAVE, SkyEffect.MOONLIGHT}) {
			for (float baseYaw : new float[] {0, 90, -135}) {
				List<Corner> stars = constellations(drawPainted(effect, baseYaw, 1, 1));
				assertTrue(stars.size() == constellationCorners(), effect + " " + baseYaw + ": " + stars.size());
				int k = 0;
				for (Constellations.Pattern p : Constellations.PATTERNS) {
					for (int line = 0; line < p.lines().length / 2; line++) {
						for (int j = 0; j < 4; j++, k++) {
							Corner c = stars.get(k);
							int sky = behind(effect, baseYaw, c);
							// Lines: 0xCDB9F0 over the sky at 0.4
							int want = SkyColors.mix(sky, 0xCDB9F0, 0.4f);
							assertTrue(farthest(over(sky, c), want) <= 4, effect + " " + baseYaw + ": a line corner ends up "
									+ Integer.toHexString(over(sky, c)) + ", not " + Integer.toHexString(want));
						}
					}
					for (int star = 0; star < p.stars().length / 2; star++) {
						for (int j = 0; j < 4; j++, k++) {
							Corner c = stars.get(k);
							int sky = behind(effect, baseYaw, c);
							// Stars: 0xFAE1FF over the sky at 0.95, twinkling down to 0.7 of that
							int dim = SkyColors.mix(sky, 0xFAE1FF, 0.95f * 0.7f), bright = SkyColors.mix(sky, 0xFAE1FF, 0.95f);
							int got = over(sky, c);
							int span = farthest(dim, bright);
							assertTrue(farthest(got, dim) <= span + 4 && farthest(got, bright) <= span + 4, effect + " " + baseYaw
									+ ": a star corner ends up " + Integer.toHexString(got) + ", outside "
									+ Integer.toHexString(dim) + " to " + Integer.toHexString(bright));
						}
					}
				}
			}
		}
	}
}
