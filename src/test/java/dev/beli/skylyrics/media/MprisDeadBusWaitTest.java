package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * A session bus that is dead (its daemon crashed and left the socket, or exited and took it along) or a stale address:
 * the D-Bus library's default is to try such a bus again every 500 ms for 10 s, all on the media thread that also reads
 * the song and does the play buttons. Here nothing listens on any socket, and the real session bus is never asked: every
 * address comes from the made-up environment and no user id is given, so /run/user/&lt;uid&gt;/bus isn't tried.
 */
@EnabledOnOs(OS.LINUX)
class MprisDeadBusWaitTest {
	private static final String BUS_SENTENCE = MprisSource.busFailureMessage(System.getenv(), Files.exists(Path.of("/.flatpak-info")));
	/** Far more than one try takes (a few ms, the first one about 100 ms while the library loads), far less than 9.5 s. */
	private static final long BOUND_MS = 3000;
	private static final Predicate<String> EXISTS = p -> Files.exists(Path.of(p));

	private Path dir;

	@BeforeEach
	void makeDir() throws IOException {
		dir = Files.createTempDirectory(Path.of("/tmp"), "bus"); // short: a socket path has room for about 100 letters
	}

	@AfterEach
	void removeDir() throws IOException {
		try (var files = Files.list(dir)) {
			for (Path p : files.toList()) {
				Files.deleteIfExists(p);
			}
		}
		Files.deleteIfExists(dir);
	}

	/** A socket file with nobody behind it, as a crashed bus daemon leaves it. */
	private Path deadSocket(String name) throws IOException {
		Path socket = dir.resolve(name);
		try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
			server.bind(UnixDomainSocketAddress.of(socket));
		}
		return socket;
	}

	/** The connector the game uses, on {@code env} only; it must name an address, or the library would look up the real one. */
	private static MprisSource.Connector connector(Map<String, String> env) {
		assertNotNull(MprisSource.sessionBusAddress(env, EXISTS, ""), "the test environment names no bus: " + env);
		return () -> MprisSource.connectSession(env, EXISTS, "");
	}

	@Test
	void aDeadBusIsToldOnTheStatusLineAtOnce() throws Exception {
		// An old address from before a re-login, and the runtime-dir socket the reader tries after it: both dead
		Map<String, String> env = Map.of("DBUS_SESSION_BUS_ADDRESS", "unix:path=" + deadSocket("old") + ";unix:abstract=/gone",
				"XDG_RUNTIME_DIR", dir.toString());
		deadSocket("bus");
		MprisSource source = new MprisSource(() -> "", connector(env), c -> true, System::nanoTime);
		MediaTracker tracker = new MediaTracker(source, () -> false);
		long start = System.nanoTime();
		tracker.start(500);
		try {
			long limit = start + TimeUnit.SECONDS.toNanos(30);
			while (tracker.error().isEmpty() && System.nanoTime() - limit < 0) {
				Thread.sleep(10);
			}
			long tookMs = (System.nanoTime() - start) / 1_000_000;
			// Until the first read ends the Status says "Can't reach music players" without why, the 10 s pop-up's clock
			// hasn't started, and a play button or a switch to Spotify Web waits behind it
			assertTrue(tookMs < BOUND_MS, "the media thread waited " + tookMs + " ms for a dead bus");
			assertEquals(BUS_SENTENCE, tracker.error());
			assertFalse(tracker.networkDown(), "a dead session bus counted as no internet");
		} finally {
			tracker.shutdown();
		}
	}

	private static DBusConnection living() throws Exception {
		Class<?> unsafe = Class.forName("sun.misc.Unsafe");
		var f = unsafe.getDeclaredField("theUnsafe");
		f.setAccessible(true);
		return (DBusConnection) unsafe.getMethod("allocateInstance", Class.class).invoke(f.get(null), DBusConnection.class);
	}

	/** The reconnect after the bus the reader was connected to went away: given up within a moment, with the D-Bus sentence. */
	private static void goneBusIsGivenUpAtOnce(Map<String, String> env) throws Exception {
		DBusConnection living = living();
		AtomicBoolean up = new AtomicBoolean(true);
		MprisSource.Connector real = connector(env);
		MprisSource source = new MprisSource(() -> "", () -> up.get() ? living : real.connect(), c -> up.get(), System::nanoTime);
		assertSame(living, source.connection());
		up.set(false);
		long start = System.nanoTime();
		IOException failure = assertThrows(IOException.class, source::connection);
		long tookMs = (System.nanoTime() - start) / 1_000_000;
		assertTrue(tookMs < BOUND_MS, "the reconnect waited " + tookMs + " ms for a bus that went away");
		assertInstanceOf(MprisSource.BusFailure.class, failure);
		assertEquals(BUS_SENTENCE, MediaTracker.failureText(failure));
	}

	@Test
	void aBusThatCrashedIsGivenUpAtOnce() throws Exception {
		deadSocket("bus"); // the socket a crashed daemon leaves behind
		goneBusIsGivenUpAtOnce(Map.of("XDG_RUNTIME_DIR", dir.toString()));
	}

	@Test
	void aBusThatExitedIsGivenUpAtOnce() throws Exception {
		// A bus daemon that stops cleanly removes its socket; the address still names it (there is no runtime-dir socket)
		goneBusIsGivenUpAtOnce(Map.of("DBUS_SESSION_BUS_ADDRESS", "unix:path=" + dir.resolve("gone") + ";unix:abstract=/gone",
				"XDG_RUNTIME_DIR", dir.toString()));
	}

	/**
	 * Quitting while a connect is still trying (here the library's own 10 s of tries, as any connect that takes long):
	 * the quit doesn't wait for it, and its interrupt ends the tries, so the media thread is let go at once too.
	 */
	@Test
	void quittingDuringALongConnectDoesNotWaitForIt() throws Exception {
		Path socket = deadSocket("bus");
		CountDownLatch trying = new CountDownLatch(1);
		CountDownLatch gaveUp = new CountDownLatch(1);
		MprisSource source = new MprisSource(() -> "", () -> {
			trying.countDown();
			try {
				return DBusConnectionBuilder.forAddress("unix:path=" + socket).withShared(false).build();
			} finally {
				gaveUp.countDown();
			}
		}, c -> true, System::nanoTime);
		MediaTracker tracker = new MediaTracker(source, () -> false);
		tracker.start(500);
		assertTrue(trying.await(5, TimeUnit.SECONDS), "never connected");
		Thread.sleep(300); // between two of the library's tries
		long start = System.nanoTime();
		tracker.shutdown();
		long tookMs = (System.nanoTime() - start) / 1_000_000;
		assertTrue(tookMs < 1000, "quitting waited " + tookMs + " ms for the connect");
		assertTrue(gaveUp.await(2, TimeUnit.SECONDS), "the connect kept trying after the quit");
	}
}
