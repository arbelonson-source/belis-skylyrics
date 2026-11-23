package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.junit.jupiter.api.Test;

/**
 * A progressive JPEG comes in scans, each one a refinement of the whole picture, and encoders write about ten. A scan
 * that changes nothing takes a few dozen bytes, and the decoder goes over the whole picture again for each one: a
 * cover of a few hundred kilobytes kept a cover thread busy for minutes, one of the 8 MB allowed for hours.
 */
class CoverJpegScansTest {
	private static long cpuHereMs() {
		return ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() / 1_000_000;
	}

	/**
	 * A plain grey progressive JPEG with one of its scans repeated {@code copies} times. Each copy is a few dozen bytes,
	 * and the decoder goes over the whole picture again for each one.
	 */
	static byte[] jpegWithRepeatedScans(int side, int copies) throws IOException {
		return jpegWithRepeatedScans(new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB), copies);
	}

	static byte[] jpegWithRepeatedScans(BufferedImage img, int copies) throws IOException {
		ByteArrayOutputStream plain = new ByteArrayOutputStream();
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
		try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(plain)) {
			writer.setOutput(out);
			writer.write(null, new IIOImage(img, null, null), param);
		} finally {
			writer.dispose();
		}
		byte[] jpg = plain.toByteArray();
		List<Integer> scans = new ArrayList<>();
		for (int i = 0; i + 1 < jpg.length; i++) {
			if ((jpg[i] & 0xFF) == 0xFF && (jpg[i + 1] & 0xFF) == 0xDA) {
				scans.add(i);
			}
		}
		assertTrue(scans.size() >= 2, "not progressive");
		int start = scans.get(scans.size() - 2), end = scans.get(scans.size() - 1);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpg, 0, end);
		for (int i = 0; i < copies; i++) {
			out.write(jpg, start, end - start);
		}
		out.write(jpg, end, jpg.length - end);
		return out.toByteArray();
	}

	@Test
	void aJpegOfThousandsOfScansDecodesInAMomentNotForMinutes() throws IOException {
		byte[] jpg = jpegWithRepeatedScans(1024, 4000);
		assertTrue(jpg.length < 200_000, "a " + jpg.length + "-byte file");
		assertTrue(CoverCache.dimensionsOk(jpg));
		long before = cpuHereMs();
		BufferedImage image = AlbumArt.decode(jpg);
		long took = cpuHereMs() - before;
		// About ten seconds of work on a fast PC before; a fraction of a second now
		assertTrue(took < 3000, "decoding a " + jpg.length / 1000 + " KB cover took " + took + " ms");
		assertNotNull(image);
	}

	@Test
	void aProgressiveJpegWithItsUsualScansStillComesOutWhole() throws IOException {
		BufferedImage src = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 200; y++) {
			for (int x = 0; x < 300; x++) {
				src.setRGB(x, y, (x * 255 / 300) << 16 | (y * 255 / 200) << 8 | 120);
			}
		}
		// Every scan applied: the same pixels as a decode with no limit
		BufferedImage progressive = CoverCache.decodeSmall(jpegWithRepeatedScans(src, 0), CoverCache.SIZE);
		BufferedImage whole = ImageIO.read(new java.io.ByteArrayInputStream(jpegWithRepeatedScans(src, 0)));
		for (int[] at : new int[][] {{10, 10}, {150, 100}, {290, 190}}) {
			int a = progressive.getRGB(at[0], at[1]), b = whole.getRGB(at[0], at[1]);
			assertEquals(a, b, "pixel " + at[0] + "," + at[1]);
		}
	}
}
