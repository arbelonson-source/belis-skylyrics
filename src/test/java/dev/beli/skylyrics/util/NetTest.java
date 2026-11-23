package dev.beli.skylyrics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class NetTest {
	private static boolean local(String ip) throws Exception {
		return Net.isLocal(InetAddress.getByName(ip));
	}

	@Test
	void localAddressesAreRecognised() throws Exception {
		for (String ip : new String[] {"127.0.0.1", "127.1.2.3", "0.0.0.0", "0.1.2.3", "169.254.1.1", "10.0.0.1", "172.16.5.5", "172.31.255.1",
				"192.168.1.20", "100.64.0.1", "100.127.255.254", "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "::ffff:127.0.0.1",
				"::ffff:192.168.0.9", "::127.0.0.1", "64:ff9b::7f00:1", "64:ff9b:1::7f00:1", "64:ff9b:1::808:808", "2002:7f00:1::", "2002:c0a8:101::1", "2002:0a00:1::5",
				"2001:0:4136:e378:8000:63bf:80ff:fffe", "2001:0:7f00:1:0:0:0:1", "192.0.0.5", "198.18.0.1", "198.19.255.1", "240.0.0.1", "255.255.255.255",
				"192.0.2.7", "198.51.100.7", "203.0.113.7"}) {
			assertTrue(local(ip), ip);
		}
	}

	@Test
	void publicAddressesAreNot() throws Exception {
		for (String ip : new String[] {"8.8.8.8", "1.1.1.1", "100.63.255.255", "100.128.0.1", "172.32.0.1", "11.0.0.1", "2606:4700::1111",
				"::ffff:8.8.8.8", "64:ff9b::808:808", "2002:0808:0808::1", "2001:0:4136:e378:8000:63bf:f7f7:f7f7", "198.17.0.1", "198.20.0.1", "223.255.255.1"}) {
			assertFalse(local(ip), ip);
		}
	}

	@Test
	void aRedirectIntoTheLocalNetworkIsRefusedFromTheOutside() {
		URI from = URI.create("https://8.8.8.8/cover.jpg");
		assertFalse(Net.hopAllowed(false, from, URI.create("https://127.0.0.1:8080/x")));
		assertFalse(Net.hopAllowed(false, from, URI.create("https://192.168.1.5/x")));
		assertFalse(Net.hopAllowed(false, from, URI.create("https://[::ffff:10.0.0.1]/x")));
		assertFalse(Net.hopAllowed(false, from, URI.create("https://[fd00::1]/x")));
		assertFalse(Net.hopAllowed(false, from, URI.create("https://100.64.1.1/x")));
		assertTrue(Net.hopAllowed(false, from, URI.create("https://1.1.1.1/x")));
	}

	@Test
	void aLocalStartMayStayLocal() {
		URI from = URI.create("http://192.168.1.5:4533/cover");
		assertTrue(Net.hopAllowed(true, from, URI.create("http://192.168.1.6/cover")));
		assertTrue(Net.hopAllowed(true, from, URI.create("http://127.0.0.1/cover")));
	}

	@Test
	void hopsKeepTheSchemeAndNeverDropToPlainHttp() {
		URI secure = URI.create("https://8.8.8.8/a");
		assertFalse(Net.hopAllowed(false, secure, URI.create("http://1.1.1.1/a")));
		assertFalse(Net.hopAllowed(true, secure, URI.create("http://1.1.1.1/a")));
		assertFalse(Net.hopAllowed(false, secure, URI.create("ftp://1.1.1.1/a")));
		assertFalse(Net.hopAllowed(true, secure, URI.create("file:///etc/passwd")));
		assertTrue(Net.hopAllowed(false, URI.create("http://8.8.8.8/a"), URI.create("https://1.1.1.1/a")));
		assertTrue(Net.hopAllowed(false, URI.create("http://8.8.8.8/a"), URI.create("http://1.1.1.1/a")));
	}

	@Test
	void aHostThatDoesNotResolveIsRefused() {
		assertFalse(Net.hopAllowed(false, URI.create("https://8.8.8.8/a"), URI.create("https://no-such-host.invalid/x")));
		assertFalse(Net.isLocalHost("no-such-host.invalid"));
	}

	private HttpServer server(AtomicInteger hits, String location) throws IOException {
		HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		s.createContext("/", ex -> {
			hits.incrementAndGet();
			if (ex.getRequestURI().getPath().equals("/end")) {
				ex.sendResponseHeaders(200, -1);
			} else {
				ex.getResponseHeaders().add("Location", location);
				ex.sendResponseHeaders(302, -1);
			}
			ex.close();
		});
		s.start();
		return s;
	}

	private static HttpClient client() {
		return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
	}

	@Test
	void theHopLoopAsksTheDecisionBeforeEveryHop() throws Exception {
		AtomicInteger hits = new AtomicInteger();
		HttpServer s = server(hits, "/end");
		try {
			URI start = URI.create("http://127.0.0.1:" + s.getAddress().getPort() + "/start");
			assertThrows(IOException.class, () -> Net.getFollowing(client(), start, Duration.ofSeconds(5), (from, to) -> false));
			assertEquals(1, hits.get(), "the refused hop must not be fetched");
			hits.set(0);
			try (InputStream in = Net.getFollowing(client(), start, Duration.ofSeconds(5), (from, to) -> true).body()) {
				assertEquals(2, hits.get());
			}
		} finally {
			s.stop(0);
		}
	}

	@Test
	void theHopLoopStopsAfterFiveHops() throws Exception {
		AtomicInteger hits = new AtomicInteger();
		HttpServer s = server(hits, "/again");
		try {
			URI start = URI.create("http://127.0.0.1:" + s.getAddress().getPort() + "/start");
			assertThrows(IOException.class, () -> Net.getFollowing(client(), start, Duration.ofSeconds(5), (from, to) -> true));
			assertEquals(Net.MAX_HOPS + 1, hits.get());
		} finally {
			s.stop(0);
		}
	}

	@Test
	void theHopLoopRefusesAClientThatFollowsByItself() {
		HttpClient following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
		assertThrows(IllegalArgumentException.class, () -> Net.getFollowing(following, URI.create("http://127.0.0.1:1/"), Duration.ofSeconds(1)));
	}

	@Test
	void aStalledBodyIsClosedAfterTheLimit() throws Exception {
		CountDownLatch closed = new CountDownLatch(1);
		InputStream stalled = new InputStream() {
			@Override
			public int read() {
				return -1;
			}

			@Override
			public void close() {
				closed.countDown();
			}
		};
		assertTrue(Net.closeAfter(stalled, Duration.ofMillis(50)) == stalled);
		assertTrue(closed.await(30, java.util.concurrent.TimeUnit.SECONDS), "the stream was never closed");
	}

	/** A body that stops half way fails the request soon after the limit; one that comes in whole arrives as it is. */
	@Test
	void aBodyThatStopsHalfWayFailsTheRequestAfterTheLimit() throws Exception {
		CountDownLatch end = new CountDownLatch(1);
		HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		s.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		s.createContext("/stall", ex -> {
			ex.sendResponseHeaders(200, 1000);
			ex.getResponseBody().write("{\"half".getBytes());
			ex.getResponseBody().flush();
			try {
				end.await(60, java.util.concurrent.TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			ex.close();
		});
		s.createContext("/whole", ex -> {
			byte[] bytes = "{\"whole\":true}".getBytes();
			ex.sendResponseHeaders(200, bytes.length);
			try (java.io.OutputStream out = ex.getResponseBody()) {
				out.write(bytes);
			}
		});
		s.start();
		try {
			String base = "http://127.0.0.1:" + s.getAddress().getPort();
			HttpClient http = client();
			HttpResponse.BodyHandler<String> within = Net.bodyWithin(HttpResponse.BodyHandlers.ofString(), Duration.ofMillis(300));
			assertEquals("{\"whole\":true}", http.send(java.net.http.HttpRequest.newBuilder(URI.create(base + "/whole")).build(), within).body());
			long start = System.nanoTime();
			assertThrows(java.net.http.HttpTimeoutException.class,
					() -> http.send(java.net.http.HttpRequest.newBuilder(URI.create(base + "/stall")).build(), within));
			assertTrue(System.nanoTime() - start < 10_000_000_000L, "the request waited for the server to give up");
		} finally {
			end.countDown();
			s.stop(0);
		}
	}

	@Test
	void textOverTheCapIsAnError() throws Exception {
		assertEquals("abc", Net.readText(new java.io.ByteArrayInputStream("abc".getBytes()), 3));
		assertThrows(IOException.class, () -> Net.readText(new java.io.ByteArrayInputStream("abcd".getBytes()), 3));
	}
}
