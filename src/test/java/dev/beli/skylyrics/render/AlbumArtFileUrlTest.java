package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AlbumArtFileUrlTest {
	/** What a Unix-style address gives: on Windows "/home/a" has no drive, so it isn't an absolute path and is refused (null). */
	private static Path p(String s) {
		return java.io.File.separatorChar == '\\' ? null : Path.of(s);
	}

	@Test
	void commonForms() {
		assertEquals(p("/home/a/cover.jpg"), AlbumArt.fileUrlPath("file:///home/a/cover.jpg"));
		assertEquals(p("/home/a/cover.jpg"), AlbumArt.fileUrlPath("file://localhost/home/a/cover.jpg"));
		assertEquals(p("/home/a/cover.jpg"), AlbumArt.fileUrlPath("file:/home/a/cover.jpg"));
		assertEquals(p("/home/a/cover.jpg"), AlbumArt.fileUrlPath("FILE:///home/a/cover.jpg"));
	}

	@Test
	void spacesAndPercent() {
		assertEquals(p("/home/a b/my cover.jpg"), AlbumArt.fileUrlPath("file:///home/a b/my cover.jpg"));
		assertEquals(p("/home/a b/my cover.jpg"), AlbumArt.fileUrlPath("file:///home/a%20b/my%20cover.jpg"));
		assertEquals(p("/home/50%/x.jpg"), AlbumArt.fileUrlPath("file:///home/50%/x.jpg"));
		assertEquals(p("/home/a#b?c.jpg"), AlbumArt.fileUrlPath("file:///home/a%23b%3Fc.jpg"));
	}

	@Test
	void unicode() {
		assertEquals(p("/m/caf\u00e9/\u05e9\u05d9\u05e8.jpg"), AlbumArt.fileUrlPath("file:///m/caf\u00e9/\u05e9\u05d9\u05e8.jpg"));
		assertEquals(p("/m/caf\u00e9.jpg"), AlbumArt.fileUrlPath("file:///m/caf%C3%A9.jpg"));
		assertEquals(p("/m/\uD83C\uDFB5.jpg"), AlbumArt.fileUrlPath("file:///m/\uD83C\uDFB5.jpg"));
	}

	@Test
	void notLocalFiles() {
		assertNull(AlbumArt.fileUrlPath("file://otherhost/a.jpg"));
		assertNull(AlbumArt.fileUrlPath("file:relative.jpg"));
		assertNull(AlbumArt.fileUrlPath("file:///a%00b.jpg"));
		assertNull(AlbumArt.fileUrlPath("http://x/a.jpg"));
		assertNull(AlbumArt.fileUrlPath(null));
		assertNull(AlbumArt.fileUrlPath("file://host"));
	}

	@Test
	void decodedTextForWindowsAndLooseForms() {
		assertEquals("C:/Users/a b/cover.img", AlbumArt.fileUrlString("file:///C:/Users/a%20b/cover.img"));
		assertEquals("C:/x.img", AlbumArt.fileUrlString("file:/C:/x.img"));
		assertEquals("/home/u/My Music/c.jpg", AlbumArt.fileUrlString("file:///home/u/My Music/c.jpg"));
		assertEquals("/home/u/c.jpg", AlbumArt.fileUrlString("file://localhost/home/u/c.jpg"));
		assertNull(AlbumArt.fileUrlString("file://server/share/c.jpg"));
	}

	@Test
	void aDriveWhereTheHostGoesIsAPathNotAComputer() {
		assertEquals("C:/Users/a b/c.jpg", AlbumArt.fileUrlString("file://C:/Users/a%20b/c.jpg"));
		assertEquals("d:/x.jpg", AlbumArt.fileUrlString("file://d:/x.jpg"));
		assertEquals("C:/Users/a b/c.jpg", AlbumArt.fileUrlString("file://C:/Users/a b/c.jpg"));
		if (java.io.File.separatorChar == '\\') {
			assertEquals(Path.of("C:/Users/a b/c.jpg"), AlbumArt.fileUrlPath("file://C:/Users/a%20b/c.jpg"));
		} else {
			assertNull(AlbumArt.fileUrlPath("file://C:/Users/a%20b/c.jpg"), "no drives here, and never read as a host");
		}
		for (String url : new String[] {"file://CC:/x.jpg", "file://C:\\x.jpg", "file://evil/C:/x.jpg"}) {
			assertNull(AlbumArt.fileUrlString(url), url);
		}
	}

	@Test
	void aRealFileAddressRoundTripsOnWindows(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
		org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows"), "Windows only");
		Path file = java.nio.file.Files.writeString(dir.resolve("my cover-123.img"), "x");
		Path back = AlbumArt.fileUrlPath(file.toUri().toString());
		org.junit.jupiter.api.Assertions.assertNotNull(back);
		org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.isRegularFile(back));
		assertEquals(Path.of("C:/Users/a b/cover.img"), AlbumArt.fileUrlPath("file:///C:/Users/a%20b/cover.img"));
		assertEquals(Path.of("C:/x.img"), AlbumArt.fileUrlPath("file:/C:/x.img"));
	}

	@Test
	void networkSharesAreNeverLocalFiles() {
		for (String url : new String[] {"file://evil/share/x.jpg", "file:////evil/share/x.jpg", "file://evil.example.com/c.jpg",
				"file:///%5C%5Cevil/share/x.jpg", "file://evil.invalid/share/x.jpg", "file:///\\\\evil/share/x.jpg"}) {
			assertNull(AlbumArt.fileUrlPath(url), url);
			assertNull(AlbumArt.fileUrlString(url), url);
		}
		assertEquals("C:/Users/a b/cover.img", AlbumArt.fileUrlString("file:///C:/Users/a%20b/cover.img"));
		assertEquals(p("/home/u/c.jpg"), AlbumArt.fileUrlPath("file://localhost/home/u/c.jpg"));
	}

	@Test
	void noNetworkShareOnWindows() {
		org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows"), "Windows only");
		assertNull(AlbumArt.fileUrlPath("file://evil.invalid/share/x.jpg"));
		assertNull(AlbumArt.fileUrlPath("file:////evil.invalid/share/x.jpg"));
	}
}
