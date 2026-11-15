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
			int first = b[0] & 0xFF;
			// 0.0.0.0/8 ("this network") and 100.64.0.0/10 (carrier-grade NAT, also used by Tailscale)
			return first == 0 || first == 100 && (b[1] & 0xC0) == 0x40;
		}
		if (a instanceof Inet6Address) {
			if ((b[0] & 0xFE) == 0xFC) {
				return true; // fc00::/7 unique local
			}
			// ::a.b.c.d (old IPv4-compatible) and 64:ff9b::a.b.c.d (NAT64) carry an IPv4 address in the last four bytes
			boolean compat = true;
			for (int i = 0; i < 12; i++) {
				compat &= b[i] == 0;
			}
			boolean nat64 = (b[0] & 0xFF) == 0x00 && (b[1] & 0xFF) == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B;
			for (int i = 4; i < 12; i++) {
				nat64 &= b[i] == 0;
			}
			if (compat || nat64) {
				try {
					return isLocal(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
				} catch (UnknownHostException e) {
					return true;
				}
			}
		}
		return false;
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

	/** GET with up to {@link #MAX_HOPS} redirects followed by hand; the client must not follow redirects itself. */
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

	/** The whole stream as text; a reply over {@code max} bytes is an IOException, like a dropped connection. */
	public static String readText(InputStream in, int max) throws IOException {
		byte[] bytes = in.readNBytes(max + 1);
		if (bytes.length > max) {
			throw new IOException("The reply is bigger than " + max + " bytes");
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}
}
