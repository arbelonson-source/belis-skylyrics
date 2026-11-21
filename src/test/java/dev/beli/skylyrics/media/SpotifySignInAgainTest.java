package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Signing in again right after the browser came back (the player said no on Spotify's page, or wants another
 * account). The side of a connection that closes it first keeps the port for a few minutes (TIME_WAIT), and on
 * Windows nothing can listen on that port meanwhile, so the sign-in must leave closing first to the browser.
 */
class SpotifySignInAgainTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";
	private static final byte[] LOOPBACK = {127, 0, 0, 1};

	private static int freePort() throws IOException {
		try (ServerSocket probe = new ServerSocket(0, 4, InetAddress.getByAddress(LOOPBACK))) {
			return probe.getLocalPort();
		}
	}

	private static String stateOf(String url) {
		return url.replaceAll(".*[?&]state=([^&]*).*", "$1");
	}

	/** Asks the way a browser does: reads the answer as far as its Content-Length says, then hangs up. */
	static String browserGet(int port, String path) throws IOException {
		try (Socket s = new Socket(InetAddress.getByAddress(LOOPBACK), port)) {
			s.setSoTimeout(30_000);
			s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			s.getOutputStream().flush();
			InputStream in = s.getInputStream();
			ByteArrayOutputStream head = new ByteArrayOutputStream();
			for (int c; !head.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n") && (c = in.read()) >= 0; ) {
				head.write(c);
			}
			String headers = head.toString(StandardCharsets.US_ASCII);
			Matcher length = Pattern.compile("(?i)content-length: *(\\d+)").matcher(headers);
			byte[] body = in.readNBytes(length.find() ? Integer.parseInt(length.group(1)) : 0);
			return headers + new String(body, StandardCharsets.UTF_8);
		}
	}

	private static void awaitEnd(SpotifyWebSource source) throws InterruptedException {
		long limit = System.nanoTime() + 30_000_000_000L;
		while (source.account() == SpotifyWebSource.Account.WAITING && System.nanoTime() < limit) {
			Thread.sleep(20);
		}
		assertNotEquals(SpotifyWebSource.Account.WAITING, source.account(), "the first sign-in ended");
	}

	@Test
	void signingInAgainRightAfterTheBrowserCameBackCanListenAgain(@TempDir Path dir) throws Exception {
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		source.loginPort = freePort();
		try {
			String state = stateOf(source.startLogin());
			// The player said no on Spotify's page: the browser shows our page and hangs up
			assertTrue(browserGet(source.loginPort, "/callback?state=" + state + "&error=access_denied").contains("Sign-in was cancelled"));
			awaitEnd(source);
			// ...and they try again at once
			source.startLogin();
			assertEquals(SpotifyWebSource.Account.WAITING, source.account());
		} finally {
			source.cancelLogin();
		}
	}

	@Test
	void aSpareConnectionTheBrowserNeverUsedDoesNotHoldThePortEither(@TempDir Path dir) throws Exception {
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, dir.resolve("login.json"));
		source.loginPort = freePort();
		try {
			source.startLogin();
			// Browsers open a spare connection and may never send anything on it; the sign-in drops it after a while
			try (Socket spare = new Socket(InetAddress.getByAddress(LOOPBACK), source.loginPort)) {
				spare.setSoTimeout(30_000);
				try {
					assertEquals(-1, spare.getInputStream().read(), "the idle connection should be dropped");
				} catch (SocketException reset) {
					// dropped with a reset, which is the same thing
				}
			}
			source.cancelLogin();
			awaitEnd(source);
			source.startLogin();
			assertEquals(SpotifyWebSource.Account.WAITING, source.account());
		} finally {
			source.cancelLogin();
		}
	}
}
