package dev.beli.skylyrics.render;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Stand-ins for the game's images in unit tests. A real NativeImage keeps its pixels in native memory, which loads
 * LWJGL's native library into the test JVM (unit tests may not: see build.gradle.kts). The cover tests only pass images
 * around and close them, so an empty one does.
 */
final class BlankImages {
	private BlankImages() {
	}

	/**
	 * A NativeImage made without running its constructor: it owns no pixels, so closing it has nothing to free and never
	 * reaches native code (NativeImage.close() only frees a pointer that isn't 0). Each call gives a different image.
	 */
	static NativeImage blank() {
		try {
			Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
			java.lang.reflect.Field field = unsafeType.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			return (NativeImage) unsafeType.getMethod("allocateInstance", Class.class).invoke(field.get(null), NativeImage.class);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Couldn't make a blank NativeImage", e);
		}
	}
}
