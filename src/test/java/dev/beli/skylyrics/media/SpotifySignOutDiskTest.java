package dev.beli.skylyrics.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sign out is a click on the settings screen, so it runs on the game's render thread: removing the sign-in file must not
 * happen there. On a slow or network drive, or a game folder OneDrive keeps, that removal can take a while, and the game
 * would stand still for it. The fake disk here holds every write and removal until it is let go.
 */
class SpotifySignOutDiskTest {
	private static final String ID = "0123456789abcdef0123456789abcdef";

	@TempDir
	Path dir;

	private SpotifyWebSource signedIn(Path login) {
		SpotifyWebSource source = new SpotifyWebSource(() -> ID, login);
		JsonObject tokens = new JsonObject();
		tokens.addProperty("access_token", "a");
		tokens.addProperty("refresh_token", "r");
		tokens.addProperty("expires_in", 3600);
		source.acceptAndSave(ID, tokens);
		return source;
	}

	/** Runs the click on a thread of its own, the way the render thread would, and says whether it came back within 10 s. */
	private static boolean returnsSoon(Runnable click) throws InterruptedException {
		CountDownLatch done = new CountDownLatch(1);
		Thread render = new Thread(() -> {
			click.run();
			done.countDown();
		}, "stand-in render thread");
		render.setDaemon(true);
		render.start();
		return done.await(10, TimeUnit.SECONDS);
	}

	/** Waits (at most 10 s) until the file is gone. */
	private static boolean goneSoon(Path file) throws InterruptedException {
		long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (Files.exists(file)) {
			if (System.nanoTime() - end > 0) {
				return false;
			}
			Thread.sleep(10);
		}
		return true;
	}

	@Test
	void signingOutDoesNotWaitForTheDisk() throws Exception {
		Path login = dir.resolve("spotify-login.json");
		SpotifyWebSource source = signedIn(login);
		assertTrue(Files.exists(login));
		CountDownLatch disk = new CountDownLatch(1);
		source.beforeSave = () -> {
			try {
				disk.await(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
		try {
			assertTrue(returnsSoon(source::signOut), "Sign out waited for the sign-in file to be removed");
			assertEquals(SpotifyWebSource.Account.SIGNED_OUT, source.account(), "signed out at once, on screen");
		} finally {
			disk.countDown();
		}
		assertTrue(goneSoon(login), "the sign-in file is removed once the disk lets it");
		assertEquals(SpotifyWebSource.Account.SIGNED_OUT, new SpotifyWebSource(() -> ID, login).account(),
				"the next game starts signed out");
	}

	@Test
	void quittingRightAfterSigningOutWaitsForTheFileToGo() {
		Path login = dir.resolve("spotify-login.json");
		SpotifyWebSource source = signedIn(login);
		source.signOut();
		assertTrue(source.awaitSaved(30_000), "the game's quit waits for the sign-out's removal");
		assertFalse(Files.exists(login), "the next game starts signed out");
	}

	@Test
	void quittingWhileANewSignInIsStillBeingSavedWaitsForTheSignOutBehindIt() throws Exception {
		// Spotify renews the sign-in (or the browser finishes one) and its thread is still saving it on a slow drive when the
		// player clicks Sign out and quits. The sign-out's thread finds that save running and leaves the removal to it, as
		// one writer does every change in order; the quit has to wait for that writer too, or the game closes with the
		// renewed sign-in on the disk and the next game starts signed in
		Path login = dir.resolve("spotify-login.json");
		SpotifyWebSource source = signedIn(login);
		CountDownLatch disk = new CountDownLatch(1);
		CountDownLatch saving = new CountDownLatch(1);
		source.beforeSave = () -> {
			saving.countDown();
			try {
				disk.await(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
		JsonObject renewed = new JsonObject();
		renewed.addProperty("access_token", "a2");
		renewed.addProperty("refresh_token", "r2");
		renewed.addProperty("expires_in", 3600);
		Thread reader = new Thread(() -> source.acceptAndSave(ID, renewed), "stand-in music reader thread");
		reader.setDaemon(true);
		reader.start();
		try {
			assertTrue(saving.await(10, TimeUnit.SECONDS), "the renewed sign-in is on its way to the disk");
			source.signOut();
			assertFalse(source.awaitSaved(300), "the quit said the sign-out was on the disk while the save before it still ran");
		} finally {
			disk.countDown();
		}
		assertTrue(source.awaitSaved(30_000), "the quit waits for the save on its way and the removal after it");
		assertFalse(Files.exists(login), "the next game starts signed out");
		reader.join(10_000);
	}

	@Test
	void aDiskThatNeverAnswersDoesNotKeepTheGameOpen() throws Exception {
		Path login = dir.resolve("spotify-login.json");
		SpotifyWebSource source = signedIn(login);
		CountDownLatch disk = new CountDownLatch(1);
		source.beforeSave = () -> {
			try {
				disk.await(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};
		try {
			source.signOut();
			assertFalse(source.awaitSaved(200), "the wait at quit has its limit");
		} finally {
			disk.countDown();
		}
		assertTrue(source.awaitSaved(30_000));
		assertFalse(Files.exists(login));
	}
}
