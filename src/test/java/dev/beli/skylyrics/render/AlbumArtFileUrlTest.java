package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AlbumArtFileUrlTest {
	private static Path p(String s) {
		return Path.of(s);
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
}
