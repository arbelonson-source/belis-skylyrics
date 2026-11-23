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
	void aRealReleaseAfterAReplayedPressStillReleasesOnce() {
		ClickRescue rescue = new ClickRescue();
		Counter screen = new Counter();
		rescue.poll(5, 5, false, screen);
		for (int i = 0; i < 10; i++) {
			rescue.poll(5, 5, true, screen);
		}
		assertEquals(1, screen.clicks);
		assertFalse(rescue.pressed()); // the late real press is dropped
		assertFalse(rescue.released()); // the screen ignores the real release...
		rescue.poll(5, 5, false, screen);
		assertEquals(1, screen.releases, "...and gets the replay's release instead, once");
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

	@Test
	void aMacCtrlClickIsNotReplayedAsALeftClick() {
		// On a Mac the game turns Ctrl+click into a right click: the screen gets a right press while the left button is
		// down. That is a click the game delivered, not one a mod pack swallowed, so it must not come back as a left click
		ClickRescue rescue = new ClickRescue();
		Counter screen = new Counter();
		rescue.poll(5, 5, false, screen);
		int right = ClickRescue.left() + 1;
		assertTrue(rescue.pressed(right), "the screen still gets its right press");
		for (int i = 0; i < 10; i++) {
			rescue.poll(5, 5, true, screen);
		}
		rescue.poll(5, 5, false, screen);
		assertEquals(0, screen.clicks, "Ctrl+click flipped a switch or played a song");
		assertEquals(0, screen.releases);
	}

	@Test
	void aRightClickBeforeASwallowedLeftClickStillLetsItBeReplayed() {
		ClickRescue rescue = new ClickRescue();
		Counter screen = new Counter();
		rescue.poll(5, 5, false, screen);
		assertTrue(rescue.pressed(ClickRescue.left() + 1));
		rescue.poll(5, 5, false, screen);
		// then a left click that never reaches the screen
		for (int i = 0; i < 10; i++) {
			rescue.poll(5, 5, true, screen);
		}
		rescue.poll(5, 5, false, screen);
		assertEquals(1, screen.clicks);
		assertEquals(1, screen.releases);
	}

	@Test
	void aLatePressIsStillDroppedByButton() {
		ClickRescue rescue = new ClickRescue();
		Counter screen = new Counter();
		rescue.poll(5, 5, false, screen);
		for (int i = 0; i < 10; i++) {
			rescue.poll(5, 5, true, screen);
		}
		assertFalse(rescue.pressed(ClickRescue.left()), "the replayed click doesn't count twice");
	}

	@Test
	void onlyTheLeftReleaseEndsADrag() {
		assertTrue(ClickRescue.endsDrag(ClickRescue.left(), ClickRescue.left()));
		assertFalse(ClickRescue.endsDrag(ClickRescue.left() + 1, ClickRescue.left()));
		assertFalse(ClickRescue.endsDrag(ClickRescue.left() + 2, ClickRescue.left()));
	}
}
