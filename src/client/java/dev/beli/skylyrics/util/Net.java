package dev.beli.skylyrics.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

/**
 * Small safety helpers for the game's web requests: where a redirect may lead, and how long and how big a reply may be.
 * Addresses a player's music app hands over directly are used as they are (a self-hosted music server on the home network
 * is fine); only a redirect from the outside must not lead into the local network.
 */
public final class Net {
	/** Most redirects followed by hand. */
	public static final int MAX_HOPS = 5;

	private Net() {
	}

	/** True for an address that is this computer or someone's home or office network, not the open internet. */
	public static boolean isLocal(InetAddress a) {
		if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) {
			return true;
		}
		byte[] b = a.getAddress();
		if (a instanceof Inet4Address) {
			return localV4(b[0] & 0xFF, b[1] & 0xFF, b[2] & 0xFF);
		}
		if (a instanceof Inet6Address) {
			if ((b[0] & 0xFE) == 0xFC) {
				return true; // fc00::/7 unique local
			}
			// ::a.b.c.d (old IPv4-compatible), 64:ff9b::/96 and 64:ff9b:1::/48 (NAT64) carry an IPv4 address in the last four bytes
			boolean compat = true;
			for (int i = 0; i < 12; i++) {
				compat &= b[i] == 0;
			}
			boolean nat64 = (b[0] & 0xFF) == 0x00 && (b[1] & 0xFF) == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B;
			boolean nat64Local = nat64 && b[4] == 0 && b[5] == 1;
			for (int i = 4; i < 12; i++) {
				nat64 &= b[i] == 0;
			}
			if (nat64Local) {
				return true;
			}
			if (compat || nat64) {
				return v4Local(b[12], b[13], b[14], b[15]);
			}
			if ((b[0] & 0xFF) == 0x20 && (b[1] & 0xFF) == 0x02) {
				return v4Local(b[2], b[3], b[4], b[5]); // 6to4: the IPv4 address sits in bytes 2-5
			}
			if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0 && b[3] == 0) {
				// Teredo: server IPv4 in bytes 4-7, client IPv4 in bytes 12-15 with every bit flipped
				return v4Local(b[4], b[5], b[6], b[7]) || v4Local((byte) ~b[12], (byte) ~b[13], (byte) ~b[14], (byte) ~b[15]);
			}
		}
		return false;
	}

	private static boolean v4Local(byte a, byte b, byte c, byte d) {
		return localV4(a & 0xFF, b & 0xFF, c & 0xFF) || isLocalAddr4(a, b, c, d);
	}

	/** The Java checks (loopback, site-local, link-local, any-local, multicast) for four IPv4 bytes. */
	private static boolean isLocalAddr4(byte a, byte b, byte c, byte d) {
		try {
			InetAddress x = InetAddress.getByAddress(new byte[] {a, b, c, d});
			return x.isAnyLocalAddress() || x.isLoopbackAddress() || x.isLinkLocalAddress() || x.isSiteLocalAddress() || x.isMulticastAddress();
		} catch (UnknownHostException e) {
			return true;
		}
	}

	/** The ranges of an IPv4 address that are not the open internet, beyond what java.net tells (first three bytes are enough). */
	private static boolean localV4(int a, int b, int c) {
		return a == 0 // this network
				|| a == 100 && (b & 0xC0) == 0x40 // 100.64.0.0/10 carrier-grade NAT, also Tailscale
				|| a == 192 && b == 0 && (c == 0 || c == 2) // 192.0.0.0/24 and documentation 192.0.2.0/24
				|| a == 198 && (b == 18 || b == 19) // 198.18.0.0/15 benchmarking
				|| a == 198 && b == 51 && c == 100 // documentation
				|| a == 203 && b == 0 && c == 113 // documentation
				|| a >= 240; // 240.0.0.0/4 reserved, includes 255.255.255.255
	}

	/** True when the host resolves and every address it has is local; a host that does not resolve counts as not local. */
	public static boolean isLocalHost(String host) {
		return resolvesTo(host, true);
	}

	private static boolean resolvesTo(String host, boolean local) {
		if (host == null || host.isEmpty()) {
			return false;
		}
		try {
			InetAddress[] all = InetAddress.getAllByName(host);
			for (InetAddress a : all) {
				if (isLocal(a) != local) {
					return false;
				}
			}
			return all.length > 0;
		} catch (UnknownHostException | SecurityException e) {
			return false;
		}
	}

	/**
	 * Whether a redirect from {@code from} to {@code to} may be followed: still http or https, never from https down to http,
	 * and not into the local network unless the first address already was there. An unresolvable target is refused.
	 */
	public static boolean hopAllowed(boolean originLocal, URI from, URI to) {
		String scheme = to.getScheme();
		boolean https = "https".equalsIgnoreCase(scheme);
		if (!https && !"http".equalsIgnoreCase(scheme)) {
			return false;
		}
		if (!https && "https".equalsIgnoreCase(from.getScheme())) {
			return false;
		}
		String host = to.getHost();
		if (host == null) {
			return false;
		}
		if (originLocal) {
			return true;
		}
		try {
			for (InetAddress a : InetAddress.getAllByName(host)) {
				if (isLocal(a)) {
					return false;
				}
			}
			return true;
		} catch (UnknownHostException | SecurityException e) {
			return false;
		}
	}

	/**
	 * GET with up to {@link #MAX_HOPS} redirects followed by hand; the client must not follow redirects itself.
	 * Known limit: DNS rebinding between the hop check and the connection isn't prevented, because java.net.http can't pin the address without breaking TLS host checks.
	 */
	public static HttpResponse<InputStream> getFollowing(HttpClient http, URI start, Duration timeout) throws IOException, InterruptedException {
		boolean originLocal = start.getHost() != null && isLocalHost(start.getHost());
		return getFollowing(http, start, timeout, (from, to) -> hopAllowed(originLocal, from, to));
	}

	static HttpResponse<InputStream> getFollowing(HttpClient http, URI start, Duration timeout, BiPredicate<URI, URI> allowed)
			throws IOException, InterruptedException {
		if (http.followRedirects() != HttpClient.Redirect.NEVER) {
			throw new IllegalArgumentException("the client must not follow redirects by itself");
		}
		URI uri = start;
		for (int hop = 0; hop <= MAX_HOPS; hop++) {
			HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(uri).timeout(timeout).GET().build(),
					HttpResponse.BodyHandlers.ofInputStream());
			int status = response.statusCode();
			if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) {
				return response;
			}
			response.body().close();
			String location = response.headers().firstValue("Location").orElse(null);
			URI next;
			try {
				next = location == null ? null : uri.resolve(location);
			} catch (IllegalArgumentException e) {
				next = null;
			}
			if (next == null || !allowed.test(uri, next)) {
				throw new IOException("Refused a redirect to " + (next == null ? "nowhere" : next.getHost()));
			}
			uri = next;
		}
		throw new IOException("Too many redirects");
	}

	/** Closes the stream after this long, so a server that stalls mid-body can't hold a worker for ever. Returns the same stream. */
	public static InputStream closeAfter(InputStream in, Duration limit) {
		CompletableFuture.delayedExecutor(limit.toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
			try {
				in.close();
			} catch (IOException ignored) {
				// already closed
			}
		});
		return in;
	}

	/**
	 * The handler's body, but one that hasn't all come in {@code limit} after the headers fails the request (an
	 * {@link java.net.http.HttpTimeoutException}) and drops the connection. A request's own time limit ends when its headers
	 * are in, so for a body read whole (ofString) a connection that goes quiet half way (Wi-Fi dropped, a stalled proxy)
	 * would hold the caller until the server gave up, or for ever.
	 */
	public static <T> HttpResponse.BodyHandler<T> bodyWithin(HttpResponse.BodyHandler<T> handler, Duration limit) {
		return info -> new Within<>(handler.apply(info), limit);
	}

	private static final class Within<T> implements HttpResponse.BodySubscriber<T> {
		private final HttpResponse.BodySubscriber<T> inner;
		private final CompletableFuture<T> body = new CompletableFuture<>();
		private volatile java.util.concurrent.Flow.@org.jspecify.annotations.Nullable Subscription subscription;

		Within(HttpResponse.BodySubscriber<T> inner, Duration limit) {
			this.inner = inner;
			inner.getBody().whenComplete((value, failure) -> {
				if (failure != null) {
					body.completeExceptionally(failure);
				} else {
					body.complete(value);
				}
			});
			CompletableFuture.delayedExecutor(limit.toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
				if (body.completeExceptionally(new java.net.http.HttpTimeoutException("The answer didn't come in within " + limit.toMillis() + " ms"))) {
					java.util.concurrent.Flow.Subscription s = subscription;
					if (s != null) {
						s.cancel(); // closes the connection
					}
				}
			});
		}

		@Override
		public java.util.concurrent.CompletionStage<T> getBody() {
			return body;
		}

		@Override
		public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
			subscription = s;
			inner.onSubscribe(s);
			if (body.isCompletedExceptionally()) {
				s.cancel(); // the time ran out before the body even started
			}
		}

		@Override
		public void onNext(java.util.List<java.nio.ByteBuffer> item) {
			inner.onNext(item);
		}

		@Override
		public void onError(Throwable failure) {
			inner.onError(failure);
		}

		@Override
		public void onComplete() {
			inner.onComplete();
		}
	}

	/** The whole stream as text; a reply over {@code max} bytes is an IOException, like a dropped connection. */
	public static String readText(InputStream in, int max) throws IOException {
		byte[] bytes = in.readNBytes(max + 1);
		if (bytes.length > max) {
			throw new IOException("The reply is bigger than " + max + " bytes");
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}
}
