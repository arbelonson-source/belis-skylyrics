package dev.beli.skylyrics.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.input.MouseButtonEvent;
import org.junit.jupiter.api.Test;

class ClickRescueTest {
	private static final class Counter implements ClickRescue.Target {
		int clicks;
		int releases;

		@Override
		public boolean click(MouseButtonEvent event) {
			clicks++;
			return true;
		}

		@Override
		public boolean drag(MouseButtonEvent event, double dx, double dy) {
			return true;
		}

		@Override
		public boolean release(MouseButtonEvent event) {
			releases++;
			return true;
		}
	}

	@Test
	void aDeliveredClickIsLeftAlone() {
		ClickRescue rescue = new ClickRescue();
		Counter screen = new Counter();
		rescue.poll(5, 5, false, screen);
		assertTrue(rescue.pressed());
		for (int i = 0; i < 10; i++) {
			rescue.poll(5, 5, true, screen);
		}
		assertTrue(rescue.released());
		rescue.poll(5, 5, false, screen);
		assertEquals(0, screen.clicks);
		assertEquals(0, screen.releases);
	}

	@Test
	void aSwallowedClickIsReplayedOnce() {
		ClickRescue rescue = new ClickRescue();
		Counter screen = new Counter();
		rescue.poll(5, 5, false, screen);
		for (int i = 0; i < 10; i++) {
			rescue.poll(5, 5, true, screen);
		}
		assertEquals(1, screen.clicks);
		// The real press turning up late is dropped so the click doesn't count twice
		assertFalse(rescue.pressed());
		rescue.poll(5, 5, false, screen);
		assertEquals(1, screen.releases);
	}

	@Test
	void theClickThatOpenedTheScreenDoesNotCount() {
		ClickRescue rescue = new ClickRescue();
		Counter screen = new Counter();
		for (int i = 0; i < 10; i++) {
			rescue.poll(5, 5, true, screen);
		}
		rescue.poll(5, 5, false, screen);
		assertEquals(0, screen.clicks);
	}
}
