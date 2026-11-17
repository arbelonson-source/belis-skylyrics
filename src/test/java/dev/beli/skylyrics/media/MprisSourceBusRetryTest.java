package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.junit.jupiter.api.Test;

class MprisSourceBusRetryTest {
	private static final long POLL_NS = 500_000_000L;

	private final AtomicLong clock = new AtomicLong(1_000_000_000_000L);
	private final AtomicInteger builds = new AtomicInteger();
	private volatile boolean busUp;
	private DBusConnection fake;

	private MprisSource source() throws Exception {
		Class<?> unsafe = Class.forName("sun.misc.Unsafe");
		var f = unsafe.getDeclaredField("theUnsafe");
		f.setAccessible(true);
		fake = (DBusConnection) unsafe.getMethod("allocateInstance", Class.class).invoke(f.get(null), DBusConnection.class);
		return new MprisSource(() -> "", () -> {
			builds.incrementAndGet();
			if (!busUp) {
				throw new IllegalStateException("no bus");
			}
			return fake;
		}, c -> true, clock::get);
	}

	private boolean tryOnce(MprisSource s) {
		try {
			s.connection();
			return true;
		} catch (Exception e) {
			return false;
		} finally {
			clock.addAndGet(POLL_NS);
		}
	}

	@Test
	void aMissingBusIsNotRetriedEveryPoll() throws Exception {
		MprisSource s = source();
		for (int i = 0; i < 20; i++) {
			tryOnce(s);
		}
		assertTrue(builds.get() <= 3, "built " + builds.get() + " times in 20 polls");
	}

	@Test
	void theSameReasonIsGivenWhileWaiting() throws Exception {
		MprisSource s = source();
		IOException first = assertThrows(IOException.class, s::connection);
		IOException second = assertThrows(IOException.class, s::connection);
		assertSame(first, second);
		assertEquals(1, builds.get());
	}

	@Test
	void aBusThatStartsLaterIsPickedUpWithin30Seconds() throws Exception {
		MprisSource s = source();
		for (int i = 0; i < 20; i++) {
			tryOnce(s);
		}
		busUp = true;
		boolean up = false;
		for (int i = 0; i < 60 && !up; i++) { // 60 polls = 30 s
			up = tryOnce(s);
		}
		assertTrue(up, "bus never picked up");
	}

	@Test
	void aWorkingBusNeverWaits() throws Exception {
		busUp = true;
		MprisSource s = source();
		for (int i = 0; i < 20; i++) {
			assertTrue(tryOnce(s));
		}
		assertEquals(1, builds.get());
	}

	@Test
	void aLostBusIsReconnectedAtOnceAndBackOffRestarts() throws Exception {
		busUp = true;
		MprisSource s = source();
		assertTrue(tryOnce(s));
		assertEquals(5_000_000_000L, MprisSource.retryDelayNs(1));
		assertEquals(10_000_000_000L, MprisSource.retryDelayNs(2));
		assertEquals(20_000_000_000L, MprisSource.retryDelayNs(3));
		assertEquals(30_000_000_000L, MprisSource.retryDelayNs(4));
		assertEquals(30_000_000_000L, MprisSource.retryDelayNs(500));
	}
}
