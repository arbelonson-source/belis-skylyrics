package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.beli.skylyrics.Fuzz;

/**
 * A cover is whatever bytes the player's art address or a cover server hands over, or what lies in the cover folder:
 * random bytes, a picture cut off or with flipped bits, a header that claims a giant or empty picture, or a format
 * (WebP, SVG, a web page) that isn't one we read. Decoding must end in a picture of sane size or none, quickly,
 * and never in an Error that would take memory the game needs.
 */
class CoverFuzzTest {
	private static final Duration BOUND = Duration.ofSeconds(60);

	private static byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(image, format, out)) {
			throw new IOException("no writer for " + format);
		}
		return out.toByteArray();
	}

	private static BufferedImage picture(Random r, int w, int h, int type) {
		BufferedImage image = new BufferedImage(w, h, type);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				image.setRGB(x, y, r.nextInt() | (r.nextInt(4) == 0 ? 0 : 0xFF000000));
			}
		}
		return image;
	}

	/** Real pictures in every format the JDK writes, to damage. */
	private static List<byte[]> templates() throws IOException {
		Random r = new Random(7);
		List<byte[]> out = new ArrayList<>();
		out.add(encode(picture(r, 64, 48, BufferedImage.TYPE_INT_ARGB), "png"));
		out.add(encode(picture(r, 300, 300, BufferedImage.TYPE_INT_RGB), "jpg"));
		out.add(encode(picture(r, 40, 40, BufferedImage.TYPE_INT_RGB), "gif"));
		out.add(encode(picture(r, 33, 17, BufferedImage.TYPE_INT_RGB), "bmp"));
		out.add(encode(picture(r, 20, 30, BufferedImage.TYPE_INT_RGB), "tiff"));
		out.add(encode(picture(r, 1, 1, BufferedImage.TYPE_INT_RGB), "png"));
		out.add(encode(picture(r, 600, 2, BufferedImage.TYPE_INT_RGB), "jpg"));
		return out;
	}

	/** A PNG whose header claims {@code w} x {@code h} (with a right checksum) followed by a little picture data, or none. */
	static byte[] pngClaiming(int w, int h, boolean withData) {
		ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(w).putInt(h).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
		chunk(out, "IHDR", ihdr.array());
		if (withData) {
			chunk(out, "IDAT", new byte[] {0x78, (byte) 0x9c, 0x63, 0x60, 0x18, 0x05, (byte) 0xa3, 0x60});
		}
		return out.toByteArray();
	}

	private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
		out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
		byte[] t = type.getBytes(StandardCharsets.US_ASCII);
		out.writeBytes(t);
		out.writeBytes(data);
		CRC32 crc = new CRC32();
		crc.update(t);
		crc.update(data);
		out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
	}

	/** Pictures that are no picture we read, or one that lies about its size. */
	private static List<byte[]> oddOnes() {
		List<byte[]> out = new ArrayList<>();
		out.add(new byte[0]);
		out.add("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100000\" height=\"100000\"><rect width=\"9\" height=\"9\"/></svg>".getBytes(StandardCharsets.UTF_8));
		out.add("<!doctype html><title>Login</title><p>Sign in to the Wi-Fi".getBytes(StandardCharsets.UTF_8));
		out.add(new byte[] {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ', 0x18, 0, 0, 0, 0x30, 1, 0, (byte) 0x9d, 1, 0x2a, 1, 0, 1, 0});
		out.add(new byte[] {'G', 'I', 'F', '8', '9', 'a', (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0, 0, 0});
		out.add(pngClaiming(100_000, 100_000, true));
		out.add(pngClaiming(100_000, 100_000, false));
		out.add(pngClaiming(20_000, 20_000, true));
		out.add(pngClaiming(20_000, 20_000, false));
		out.add(pngClaiming(0, 0, true));
		out.add(pngClaiming(1, 4096, true));
		out.add(pngClaiming(-1, -1, true));
		return out;
	}

	private static void checkDecoded(BufferedImage image) {
		if (image == null) {
			return;
		}
		assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
		assertTrue((long) image.getWidth() * image.getHeight() <= 4096L * 4096, "decoded " + image.getWidth() + "x" + image.getHeight());
		AlbumArt.pickColor(image);
		try {
			CoverCache.encodeSmall(image);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static byte[] input(Fuzz f, List<byte[]> templates, List<byte[]> odd) {
		byte[] base = templates.get(f.random.nextInt(templates.size()));
		return switch (f.random.nextInt(6)) {
			case 0 -> f.bytes(4000);
			case 1 -> java.util.Arrays.copyOf(base, f.random.nextInt(base.length + 1)); // cut off
			case 2 -> odd.get(f.random.nextInt(odd.size()));
			default -> f.mutate(base);
		};
	}

	@Test
	void anyCoverBytesDecodeToASanePictureOrNone() throws Exception {
		List<byte[]> templates = templates();
		List<byte[]> odd = oddOnes();
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(30, 600, (f, i) -> {
			byte[] bytes = i < odd.size() ? odd.get(i) : input(f, templates, odd);
			BufferedImage image;
			try {
				image = AlbumArt.decode(bytes);
			} catch (IOException wontDecode) {
				return; // counted against the cover, like a broken file
			}
			checkDecoded(image);
		}));
	}

	@Test
	void anyFileInTheCoverFolderReadsAsASanePictureOrNone(@TempDir Path dir) throws Exception {
		List<byte[]> templates = templates();
		List<byte[]> odd = oddOnes();
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(31, 400, (f, i) -> {
			byte[] bytes = i < odd.size() ? odd.get(i) : input(f, templates, odd);
			Path file = dir.resolve(CoverCache.fileName("https://example.com/" + i));
			Files.write(file, bytes);
			checkDecoded(CoverCache.readCached(file));
		}));
	}

	@Test
	void anySizeAndAddressIsHandled() {
		assertTimeoutPreemptively(BOUND, () -> Fuzz.cases(32, 3000, (f, i) -> {
			int w = f.random.nextInt(3) == 0 ? f.random.nextInt() : f.random.nextInt(9000), h = f.random.nextInt(3) == 0 ? f.random.nextInt() : f.random.nextInt(9000);
			if (CoverCache.sizeOk(w, h)) {
				int step = CoverCache.subsampling(w, h, CoverCache.SIZE);
				assertTrue(step >= 1, w + "x" + h + " step " + step);
			}
			String url = f.random.nextBoolean() ? f.mutate("file:///home/someone/Music/A%20B/cover.jpg") : "file:" + f.nasty(30);
			AlbumArt.fileUrlPath(url);
			AlbumArt.fileUrlString(url);
			CoverCache.fileName(f.nasty(30));
			int size = 1 + f.random.nextInt(256);
			float c = AlbumArt.cornerAlpha(f.random.nextInt(300) - 50, f.random.nextInt(300) - 50, size, f.random.nextFloat() * size / 2);
			assertTrue(c >= 0 && c <= 1);
		}));
	}
}
