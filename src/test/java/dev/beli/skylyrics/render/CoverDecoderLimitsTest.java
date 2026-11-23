package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * A cover's bytes come from whatever the music app hands over: a song file's own picture, a podcast's or a radio
 * station's image. Its size is checked before decoding, but a picture can cost far more than its size says. Only the
 * formats covers come in are decoded: Java's TIFF decoder makes room for whatever a strip's length field says.
 */
class CoverDecoderLimitsTest {
	private static long allocatedHere() {
		return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
	}

	/**
	 * A 16 x 16 grey TIFF of about 2 KB whose one strip says it holds {@code stripBytes} deflated bytes: Java's TIFF
	 * decoder makes room for all of them before it reads one.
	 */
	static byte[] tiffAsking(int stripBytes) {
		int[][] fields = {
				{256, 4, 16}, // width
				{257, 4, 16}, // height
				{258, 3, 8}, // bits per sample
				{259, 3, 8}, // deflate
				{262, 3, 1}, // black is zero
				{273, 4, 1024}, // where the strip starts
				{277, 3, 1}, // samples per pixel
				{278, 4, 16}, // rows per strip
				{279, 4, stripBytes}, // the strip's length
		};
		ByteBuffer b = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN);
		b.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
		b.putShort((short) fields.length);
		for (int[] f : fields) {
			b.putShort((short) f[0]).putShort((short) f[1]).putInt(1);
			if (f[1] == 3) {
				b.putShort((short) f[2]).putShort((short) 0);
			} else {
				b.putInt(f[2]);
			}
		}
		b.putInt(0);
		while (b.position() < 1024 + 64) {
			b.put((byte) 0x78);
		}
		return Arrays.copyOf(b.array(), b.position());
	}

	@Test
	void aTinyTiffThatAsksForHundredsOfMegabytesIsNeverDecoded() {
		byte[] tiff = tiffAsking(200_000_000);
		assertTrue(tiff.length < 4096);
		long before = allocatedHere();
		BufferedImage image = null;
		try {
			image = AlbumArt.decode(tiff);
		} catch (IOException e) {
			// the decoder gives up when the strip isn't there, but only after making room for it
		}
		long used = allocatedHere() - before;
		assertTrue(used < 32L << 20, "decoding a " + tiff.length + "-byte cover took " + used / 1_000_000 + " MB");
		assertNull(image);
		assertFalse(CoverCache.dimensionsOk(tiff));
	}

	@Test
	void aSavedCoverThatIsATiffIsNotDecodedEither() throws IOException {
		long before = allocatedHere();
		assertNull(CoverCache.decodeSmall(tiffAsking(200_000_000), CoverCache.SIZE));
		assertTrue(allocatedHere() - before < 32L << 20);
	}

	@Test
	void theFormatsCoversComeInStillDecode() throws IOException {
		BufferedImage src = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 200; y++) {
			for (int x = 0; x < 300; x++) {
				src.setRGB(x, y, (x * 255 / 300) << 16 | (y * 255 / 200) << 8 | 120);
			}
		}
		for (String format : new String[] {"png", "jpg", "gif", "bmp"}) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			assertTrue(ImageIO.write(src, format, out), format);
			byte[] bytes = out.toByteArray();
			assertTrue(CoverCache.dimensionsOk(bytes), format);
			BufferedImage decoded = CoverCache.decodeSmall(bytes, CoverCache.SIZE);
			assertNotNull(decoded, format);
			assertEquals(300, decoded.getWidth(), format);
			assertNotNull(AlbumArt.decode(bytes), format);
		}
	}
}
