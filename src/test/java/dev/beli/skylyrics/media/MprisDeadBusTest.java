package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.ConnectException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * A session bus whose socket is still there but nothing listens on it (the bus daemon died or was restarted): the D-Bus
 * library says "Failed to connect to bus: Connection refused" with a java.net.ConnectException inside, which is not the
 * internet's fault, so the Music tab's Status keeps the D-Bus sentence.
 */
class MprisDeadBusTest {
	private static final String BUS_SENTENCE = MprisSource.busFailureMessage(System.getenv(), Files.exists(Path.of("/.flatpak-info")));

	private static Throwable pollFailure(MprisSource.Connector connector) {
		MprisSource source = new MprisSource(() -> "", connector, c -> true, System::nanoTime);
		return assertThrows(Exception.class, source::poll);
	}

	@Test
	void aRefusedBusConnectionIsNotCalledTheInternet() {
		// The chain dbus-java 5.2.2 throws for a dead socket (DBusConnectionBuilder.build on a socket nobody listens on)
		Throwable failure = pollFailure(() -> {
			throw new DBusException("Failed to connect to bus: Connection refused", new ConnectException("Connection refused"));
		});
		assertFalse(MediaTracker.networkFailure(failure), "a dead session bus counted as no internet: " + failure);
		assertEquals(BUS_SENTENCE, MediaTracker.failureText(failure));
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void theLibrarysOwnFailureForADeadSocketKeepsTheBusSentence() throws Exception {
		Path dir = Files.createTempDirectory(Path.of("/tmp"), "bus");
		Path socket = dir.resolve("bus");
		try {
			try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
				server.bind(UnixDomainSocketAddress.of(socket)); // closing leaves the socket file with nobody behind it
			}
			Throwable failure = pollFailure(() -> DBusConnectionBuilder.forAddress("unix:path=" + socket).withShared(false).build());
			assertFalse(MediaTracker.networkFailure(failure), "a dead session bus counted as no internet: " + failure);
			assertEquals(BUS_SENTENCE, MediaTracker.failureText(failure));
		} finally {
			Files.deleteIfExists(socket);
			Files.deleteIfExists(dir);
		}
	}
}
