package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Leaving a world, as the game does it: the connection closes, Fabric says so on Netty's thread, and the game thread then
 * runs Minecraft.disconnect, which first empties its queue of tasks. The lyrics must still let go of the old world, on the
 * game thread, once; and a world joined before the next tick must keep its fresh state.
 */
class WorldLeaveTest {
	private final List<String> done = new ArrayList<>();
	/** The game thread's task queue, which Minecraft.disconnect empties. */
	private final Deque<Runnable> gameTasks = new ArrayDeque<>();
	private final WorldLeave leaving = new WorldLeave(() -> done.add("leave on " + Thread.currentThread().getName()));

	/** Fabric's DISCONNECT, on Netty's thread. */
	private void closedOnNetty() throws InterruptedException {
		Thread netty = new Thread(leaving::left, "Netty Client IO");
		netty.start();
		netty.join();
	}

	/** One game tick: the queued tasks, then the mod's tick. */
	private void tick() {
		for (Runnable task; (task = gameTasks.poll()) != null; ) {
			task.run();
		}
		leaving.apply();
	}

	@Test
	void theOldWorldIsLetGoOnTheGameThreadAfterTheGameEmptiesItsTasks() throws InterruptedException {
		closedOnNetty();
		gameTasks.clear(); // Minecraft.disconnect, first thing
		assertEquals(List.of(), done, "nothing let go on Netty's thread, while a frame may be drawing the old world");
		tick();
		assertEquals(List.of("leave on " + Thread.currentThread().getName()), done);
		tick();
		tick();
		assertEquals(1, done.size(), "let go once: " + done);
	}

	@Test
	void aJoinBeforeTheNextTickLetsGoOfTheOldWorldFirst() throws InterruptedException {
		closedOnNetty();
		gameTasks.clear();
		leaving.joined(() -> done.add("join"));
		tick();
		assertEquals(List.of("leave on " + Thread.currentThread().getName(), "join"), done,
				"the leave must not come after the join and clear the new world's state");
	}

	@Test
	void twoReportsOfOneLeaveLetGoOnce() throws InterruptedException {
		closedOnNetty();
		leaving.left(); // the game thread's own handling of the same close, in case it came first
		tick();
		tick();
		assertEquals(1, done.size(), done.toString());
	}

	@Test
	void ticksAndJoinsWithoutALeaveLetGoOfNothing() {
		tick();
		leaving.joined(() -> done.add("join"));
		tick();
		assertEquals(List.of("join"), done);
	}
}
