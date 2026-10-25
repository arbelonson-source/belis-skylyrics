package dev.beli.skylyrics;

import java.nio.file.Path;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.dev.DevTestHook;
import dev.beli.skylyrics.gui.SkyLyricsScreen;
import dev.beli.skylyrics.lyrics.LrclibClient;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.MprisSource;
import dev.beli.skylyrics.render.AlbumArt;
import dev.beli.skylyrics.render.LyricsRenderer;
import dev.beli.skylyrics.render.NowPlayingHud;

public final class SkyLyricsClient implements ClientModInitializer {
	public static final String MOD_ID = "skylyrics";
	public static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");

	private static SkyLyricsClient instance;

	private MediaTracker tracker;
	private LyricsService lyrics;
	private AlbumArt art;
	private LyricsRenderer renderer;
	private NowPlayingHud hud;
	private DevTestHook testHook;

	private KeyMapping openSettings;
	private KeyMapping toggleLyrics;
	private KeyMapping showNowPlaying;
	private KeyMapping playPause;
	private KeyMapping nextTrack;
	private KeyMapping previousTrack;

	public static SkyLyricsClient get() {
		return instance;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		Path cache = FabricLoader.getInstance().getGameDir().resolve(".cache").resolve(MOD_ID);

		tracker = new MediaTracker(new MprisSource(() -> SkyLyricsConfig.get().preferredPlayer),
				() -> SkyLyricsConfig.get().demoMode);
		lyrics = new LyricsService(new LrclibClient(version), cache);
		art = new AlbumArt();
		renderer = new LyricsRenderer(tracker, lyrics, art);
		hud = new NowPlayingHud(tracker, lyrics, art);

		tracker.onTrackChange(track -> {
			lyrics.load(track);
			art.load(track);
			hud.flash();
		});
		tracker.start(500);

		registerKeys();
		WorldRenderEvents.BEFORE_ENTITIES.register(renderer::render);
		HudElementRegistry.addLast(id("now_playing"), hud::render);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		if (DevTestHook.enabled()) {
			testHook = new DevTestHook();
			ClientTickEvents.END_CLIENT_TICK.register(testHook::tick);
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
		LOG.info("Beli's SkyLyrics {} ready (placement {}, demo {})", version, cfg.placement, cfg.demoMode);
	}

	private void registerKeys() {
		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		openSettings = key("open_settings", GLFW.GLFW_KEY_J, category);
		toggleLyrics = key("toggle", InputConstants.UNKNOWN.getValue(), category);
		showNowPlaying = key("now_playing", InputConstants.UNKNOWN.getValue(), category);
		playPause = key("play_pause", InputConstants.UNKNOWN.getValue(), category);
		nextTrack = key("next", InputConstants.UNKNOWN.getValue(), category);
		previousTrack = key("previous", InputConstants.UNKNOWN.getValue(), category);
	}

	private static KeyMapping key(String name, int code, KeyMapping.Category category) {
		return KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.skylyrics." + name, InputConstants.Type.KEYSYM, code, category));
	}

	private void tick(Minecraft client) {
		art.upload();
		while (openSettings.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new SkyLyricsScreen(null));
			}
		}
		while (toggleLyrics.consumeClick()) {
			SkyLyricsConfig cfg = SkyLyricsConfig.get();
			cfg.enabled = !cfg.enabled;
			cfg.save();
		}
		while (showNowPlaying.consumeClick()) {
			hud.toggleForced();
		}
		while (playPause.consumeClick()) {
			tracker.playPause();
		}
		while (nextTrack.consumeClick()) {
			tracker.next();
		}
		while (previousTrack.consumeClick()) {
			tracker.previous();
		}
	}

	private void shutdown() {
		tracker.shutdown();
		lyrics.shutdown();
		art.close();
	}

	public MediaTracker tracker() {
		return tracker;
	}

	public LyricsService lyrics() {
		return lyrics;
	}

	public AlbumArt art() {
		return art;
	}

	/** The key that opens the settings, so the screen can close on it too. */
	public KeyMapping openSettingsKey() {
		return openSettings;
	}

	public NowPlayingHud hud() {
		return hud;
	}
}
