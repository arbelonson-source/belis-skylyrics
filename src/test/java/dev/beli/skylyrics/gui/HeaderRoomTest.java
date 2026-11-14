package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HeaderRoomTest {
	@Test
	void headerTextStopsBeforeTheButton() {
		assertEquals(100 - 6 - 20, Ui.headerRoom(20, 100));
	}

	@Test
	void noRoomMeansNoText() {
		assertEquals(0, Ui.headerRoom(90, 95));
	}
}
