package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
//? if <26.3 {
import static org.junit.jupiter.api.Assertions.assertTrue;
//?}

import java.lang.reflect.Field;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Vanilla draws its clouds after the painted skies (Synthwave, Moonlight), each cloud hidden wherever something nearer
 * has left its depth. The painted sky went out through the text background type, which leaves the depth of everything it
 * draws, at just short of the render-distance fog: every cloud farther away than that was hidden, so at render distance
 * 12 (standing at y 64) the clouds ended in a hard circle about 55 degrees up, and at 6 there were none (the clouds are
 * 128 blocks up, the sky 78 blocks away). A painted sky that leaves no depth hides no cloud vanilla draws, as on 26.3.
 */
class PaintedSkyCloudsTest {
	/** 1.21.1's render types reach the item and block registries as they load. */
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void thePaintedSkyLeavesNoDepthForTheCloudsToHideBehind() throws Exception {
		assertFalse(leavesDepth(PaintedSkyType.get(false)), "the painted sky marks its depth, hiding every cloud past it");
	}

	//? if <26.3 {
	@Test
	void theTextBackgroundTypeKeptWithShadersLeavesItsDepth() throws Exception {
		// The type Iris has a program for: what the painted sky went out through before, and still does with a shader pack
		assertTrue(leavesDepth(RenderTypes.textBackground()));
		assertTrue(PaintedSkyType.get(true) == RenderTypes.textBackground());
	}
	//?}

	/** Whether drawing with {@code type} marks the depth buffer. */
	private static boolean leavesDepth(RenderType type) throws Exception {
		//? if >=26.1 {
		/*var state = type.pipeline().getDepthStencilState();
		return state != null && state.writeDepth();
		*///?} else if >=1.21.10 {
		return type.pipeline().isWriteDepth();
		//?} else if >=1.21.5 {
		/*return ((com.mojang.blaze3d.pipeline.RenderPipeline) field(type, "renderPipeline")).isWriteDepth();
		*///?} else {
		/*return (Boolean) field(field(field(type, "state"), "writeMaskState"), "writeDepth");
		*///?}
	}

	/** A private field's value, looked up through the object's classes. */
	private static Object field(Object o, String name) throws Exception {
		for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(o);
			} catch (NoSuchFieldException e) {
				// look further up
			}
		}
		throw new NoSuchFieldException(name);
	}
}
