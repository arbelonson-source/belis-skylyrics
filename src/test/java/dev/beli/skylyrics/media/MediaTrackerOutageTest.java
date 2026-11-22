package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class MediaTrackerOutageTest {
	/** Like the Spotify poller: fails on an attempt, then answers with its last snapshot while backing off. */
	private static final class BackingOff implements MediaSource {
		volatile Throwable failure;
		volatile boolean asking;
		volatile CountDownLatch barrier = new CountDownLatch(1);

		@Override
		public Snapshot poll() throws Exception {
			if (asking && failure != null) {
				throw (Exception) failure;
			}
			return Snapshot.NOTHING;
		}

		@Override
		public Throwable failing() {
			return failure;
		}

		@Override
		public void playPause() {
			barrier.countDown();
		}

		@Override
		public void next() {
		}

		@Override
		public void previous() {
		}

		@Override
		public String displayName() {
			return "Fake";
		}
	}

	/** Runs the polls queued so far to the end (a control queued after them runs after them). */
	private static void settle(MediaTracker tracker, BackingOff source) throws InterruptedException {
		source.barrier = new CountDownLatch(1);
		tracker.playPause();
		assertTrue(source.barrier.await(30, TimeUnit.SECONDS));
	}

	@Test
	void anOutageLogsOnceAndStaysFailingBetweenRetries() throws Exception {
		BackingOff source = new BackingOff();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		try {
			source.failure = new UnknownHostException();
			source.asking = true;
			tracker.refresh(); // the attempt that throws
			settle(tracker, source);
			source.asking = false;
			for (int i = 0; i < 5; i++) {
				tracker.refresh(); // backing off: answers without asking
			}
			settle(tracker, source);
			source.asking = true;
			tracker.refresh(); // the next attempt fails too
			settle(tracker, source);
			source.asking = false;
			tracker.refresh();
			settle(tracker, source);
			assertEquals(1, tracker.outageWarnings);
			assertFalse(tracker.connected());
			assertFalse(tracker.error().isEmpty());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void statusNeverFlickersToConnectedWhileBackingOff() throws Exception {
		BackingOff source = new BackingOff();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		try {
			source.failure = new IOException("boom");
			source.asking = true;
			tracker.refresh();
			settle(tracker, source);
			source.asking = false;
			tracker.refresh();
			settle(tracker, source);
			assertFalse(tracker.connected());
			assertEquals("boom", tracker.error());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void recoveryLogsOnceAndClearsTheError() throws Exception {
		BackingOff source = new BackingOff();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		try {
			source.failure = new IOException("boom");
			source.asking = true;
			tracker.refresh();
			settle(tracker, source);
			source.asking = false;
			tracker.refresh();
			settle(tracker, source);
			source.failure = null; // the next attempt works
			tracker.refresh();
			tracker.refresh();
			settle(tracker, source);
			assertEquals(1, tracker.outageWarnings);
			assertEquals(1, tracker.recoveries);
			assertTrue(tracker.connected());
			assertEquals("", tracker.error());
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void aNetworkFailureBlamesTheInternetNotThePlayer() {
		assertEquals("Can't reach the internet or Spotify", MediaTracker.failureText(new UnknownHostException()));
		assertEquals("Can't reach the internet or Spotify", MediaTracker.failureText(new java.net.ConnectException()));
		assertEquals("Can't reach the internet or Spotify", MediaTracker.failureText(new IOException()));
		assertEquals("Can't reach the internet or Spotify", MediaTracker.failureText(new IOException("wrapped", new UnknownHostException("api.spotify.com"))));
		assertEquals("Spotify answered with error 500", MediaTracker.failureText(new IOException("Spotify answered with error 500")));
		assertEquals("Allow Minecraft", MediaTracker.failureText(new IOException("Allow Minecraft")));
	}

	@Test
	void aReaderThatTripsOverItselfShowsAPlainSentenceNotJavasWords(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		String plain = "Couldn't read your music player";
		// Linux: the session bus doesn't answer, and the D-Bus library says so in its own English
		MprisSource mpris = new MprisSource(() -> "", new MprisSource.Reader() {
			@Override
			public java.util.List<String> names() {
				throw new org.freedesktop.dbus.errors.NoReply("No reply within specified time");
			}

			@Override
			public MprisSource.Reading read(String busName) {
				return null;
			}
		});
		Exception noReply = org.junit.jupiter.api.Assertions.assertThrows(Exception.class, mpris::poll);
		assertEquals(plain, MediaTracker.failureText(noReply));
		// Spotify Web: a sign-in answer that can't be read ends in Java's words about a number
		java.nio.file.Path login = dir.resolve("login.json");
		java.nio.file.Files.writeString(login, "{\"clientId\":\"0123456789abcdef0123456789abcdef\",\"refreshToken\":\"r\",\"scopes\":\"\"}");
		SpotifyWebSource spotify = new SpotifyWebSource(() -> "0123456789abcdef0123456789abcdef", login);
		spotify.useHttp(new OneAnswer("{\"access_token\":\"fresh\",\"expires_in\":\"soon\"}"));
		Exception odd = org.junit.jupiter.api.Assertions.assertThrows(Exception.class, spotify::poll);
		assertEquals(plain, MediaTracker.failureText(odd));
		// A bug in a reader: Java's words about it
		assertEquals(plain, MediaTracker.failureText(new NullPointerException("Cannot invoke \"String.length()\" because \"title\" is null")));
		assertEquals(plain, MediaTracker.failureText(new IllegalStateException("Expected BEGIN_OBJECT but was STRING at line 1 column 1")));
	}

	/** A network that answers every request with 200 and this body. */
	private static final class OneAnswer extends java.net.http.HttpClient {
		private final String body;

		OneAnswer(String body) {
			this.body = body;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> java.net.http.HttpResponse<T> send(java.net.http.HttpRequest request, java.net.http.HttpResponse.BodyHandler<T> handler) {
			return (java.net.http.HttpResponse<T>) new java.net.http.HttpResponse<String>() {
				public int statusCode() { return 200; }
				public java.net.http.HttpRequest request() { return request; }
				public java.util.Optional<java.net.http.HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
				public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
				public String body() { return body; }
				public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
				public java.net.URI uri() { return request.uri(); }
				public Version version() { return Version.HTTP_1_1; }
			};
		}

		@Override public java.util.Optional<java.net.CookieHandler> cookieHandler() { return java.util.Optional.empty(); }
		@Override public java.util.Optional<java.time.Duration> connectTimeout() { return java.util.Optional.empty(); }
		@Override public Redirect followRedirects() { return Redirect.NEVER; }
		@Override public java.util.Optional<java.net.ProxySelector> proxy() { return java.util.Optional.empty(); }
		@Override public javax.net.ssl.SSLContext sslContext() { throw new UnsupportedOperationException(); }
		@Override public javax.net.ssl.SSLParameters sslParameters() { return new javax.net.ssl.SSLParameters(); }
		@Override public java.util.Optional<java.net.Authenticator> authenticator() { return java.util.Optional.empty(); }
		@Override public Version version() { return Version.HTTP_1_1; }
		@Override public java.util.Optional<java.util.concurrent.Executor> executor() { return java.util.Optional.empty(); }

		@Override
		public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(java.net.http.HttpRequest request,
				java.net.http.HttpResponse.BodyHandler<T> handler) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(java.net.http.HttpRequest request,
				java.net.http.HttpResponse.BodyHandler<T> handler, java.net.http.HttpResponse.PushPromiseHandler<T> push) {
			throw new UnsupportedOperationException();
		}
	}

	@Test
	void aSourceThatIsNotSetUpYetNeverStartsTheErrorToast() throws Exception {
		BackingOff source = new BackingOff();
		MediaTracker tracker = new MediaTracker(source, () -> false);
		try {
			source.failure = SpotifyWebSource.notSetUp("");
			source.asking = true;
			tracker.refresh();
			settle(tracker, source);
			assertFalse(tracker.connected());
			assertEquals("Paste your Client ID first", tracker.error());
			java.lang.reflect.Field f = MediaTracker.class.getDeclaredField("failingSince");
			f.setAccessible(true);
			assertEquals(Long.MIN_VALUE, f.getLong(tracker), "a setup step is not a read failure");
			assertFalse(tracker.takeReadFailure());
			// a real failure still counts
			source.failure = new IOException("boom");
			tracker.refresh();
			settle(tracker, source);
			assertTrue(f.getLong(tracker) != Long.MIN_VALUE);
		} finally {
			tracker.shutdown();
		}
	}

	@Test
	void theEmptyBarHintFollowsThePlatformAndTheReader() {
		assertEquals("Play a song in Spotify, a browser or any player", MediaTracker.idleHint(true, false, false));
		assertEquals("Play a song in Spotify or the Music app", MediaTracker.idleHint(true, true, false));
		// Spotify Web follows only the Spotify account (on any device): another player or a browser video never shows
		assertEquals("Play a song in Spotify on any device", MediaTracker.idleHint(true, false, true));
		assertEquals("Play a song in Spotify on any device", MediaTracker.idleHint(true, true, true));
		assertEquals("", MediaTracker.idleHint(false, false, true));
		assertEquals("", MediaTracker.idleHint(false, false, false));
		assertEquals("", MediaTracker.idleHint(false, true, false));
	}
}
