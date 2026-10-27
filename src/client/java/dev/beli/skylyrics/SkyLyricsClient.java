package dev.beli.skylyrics;

import java.nio.file.Path;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.2 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
*///?} else {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
//?}
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
//? if >=26.2 {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
//?}
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.dev.DevTestHook;
import dev.beli.skylyrics.gui.LibraryScreen;
import dev.beli.skylyrics.gui.SkyLyricsScreen;
import dev.beli.skylyrics.lyrics.LrclibClient;
import dev.beli.skylyrics.lyrics.LyricsService;
import dev.beli.skylyrics.media.ChosenSource;
import dev.beli.skylyrics.media.Library;
import dev.beli.skylyrics.media.MacMediaSource;
import dev.beli.skylyrics.media.MediaSource;
import dev.beli.skylyrics.media.MediaTracker;
import dev.beli.skylyrics.media.MprisSource;
import dev.beli.skylyrics.media.SpotifyLibrary;
import dev.beli.skylyrics.media.SpotifyWebSource;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.media.WindowsMediaSource;
import dev.beli.skylyrics.render.AlbumArt;
import dev.beli.skylyrics.render.CoverCache;
import dev.beli.skylyrics.render.HatLayer;
import dev.beli.skylyrics.render.LyricsRenderer;
import dev.beli.skylyrics.render.NowPlayingHud;
import dev.beli.skylyrics.render.SkyEffects;

public final class SkyLyricsClient implements ClientModInitializer {
	public static final String MOD_ID = "skylyrics";
	public static final Logger LOG = LoggerFactory.getLogger("SkyLyrics");

	private static SkyLyricsClient instance;

	private MediaTracker tracker;
	private SpotifyWebSource spotify;
	private Library library;
	private LyricsService lyrics;
	private AlbumArt art;
	private CoverCache covers;
	private LyricsRenderer renderer;
	private SkyEffects sky;
	private NowPlayingHud hud;
	private DevTestHook testHook;

	private KeyMapping openSettings;
	private KeyMapping openLibrary;
	private KeyMapping toggleLyrics;
	private KeyMapping showNowPlaying;
	private KeyMapping playPause;
	private KeyMapping nextTrack;
	private KeyMapping previousTrack;
	private KeyMapping lyricsLate;
	private KeyMapping lyricsEarly;
	/** Whether the library key has been checked against the shader toggle yet. */
	private boolean keysChecked;

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

		spotify = new SpotifyWebSource(() -> SkyLyricsConfig.get().spotifyClientId, cache.resolve("spotify-login.json"));
		library = windows() || mac() ? new SpotifyLibrary(spotify) : new SpotifyLibrary(spotify, MprisSource::openInSpotify);
		MediaSource music = new ChosenSource(musicSource(cache), spotify, () -> SkyLyricsConfig.get().preferredPlayer);
		tracker = new MediaTracker(music, () -> SkyLyricsConfig.get().demoMode);
		lyrics = new LyricsService(new LrclibClient(version), cache);
		art = new AlbumArt();
		covers = new CoverCache(cache.resolve("covers"));
		renderer = new LyricsRenderer(tracker, lyrics, art);
		hud = new NowPlayingHud(tracker, lyrics, art);

		tracker.onTrackChange(track -> {
			lyrics.load(track);
			art.load(track);
			hud.flash();
		});
		tracker.start(500);

		registerKeys();
		sky = new SkyEffects(renderer, lyrics, art, tracker);
		//? if >=26.2 {
		/*LevelRenderEvents.COLLECT_SUBMITS.register(sky::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(renderer::renderEarly);
		LevelRenderEvents.END_MAIN.register(renderer::renderLate);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, entityRenderer, layers, context) -> {
		*///?} else {
		WorldRenderEvents.BEFORE_ENTITIES.register(sky::render);
		WorldRenderEvents.BEFORE_ENTITIES.register(renderer::renderEarly);
		WorldRenderEvents.END_MAIN.register(renderer::renderLate);
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, entityRenderer, layers, context) -> {
		//?}
			if (entityRenderer instanceof AvatarRenderer<?> avatar) {
				layers.register(new HatLayer(avatar, renderer));
			}
		});
		HudElementRegistry.addFirst(id("lyrics"), renderer::renderHud);
		// Under the action bar text and the chat, so both stay readable when the bar sits at the bottom
		HudElementRegistry.attachElementBefore(VanillaHudElements.OVERLAY_MESSAGE, id("now_playing"), hud::render);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen) {
				//? if >=26.2 {
				/*ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> renderTitle(s, g));
				*///?} else {
				ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, delta) -> renderTitle(s, g));
				//?}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		if (DevTestHook.enabled()) {
			testHook = new DevTestHook();
			ClientTickEvents.END_CLIENT_TICK.register(testHook::tick);
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
		LOG.info("Beli's SkyLyrics {} ready (placement {}, demo {})", version, cfg.placement, cfg.demoMode);
	}

	/** Lyrics on the main menu, in the gap under its lowest button (the copyright line at the very bottom is a button too). */
	private void renderTitle(Screen screen, GuiGraphics g) {
		int bottom = screen.height - TITLE_FOOTER;
		int top = 0;
		//? if >=26.2 {
		/*for (AbstractWidget widget : Screens.getWidgets(screen)) {
		*///?} else {
		for (AbstractWidget widget : Screens.getButtons(screen)) {
		//?}
			if (widget.visible && widget.getBottom() < bottom) {
				top = Math.max(top, widget.getBottom());
			}
		}
		renderer.renderMenu(g, top + 4, bottom);
	}

	/** Room left at the bottom of the title screen for the version and copyright lines. */
	private static final int TITLE_FOOTER = 12;

	/** True on Windows, where the song comes from Windows' media controls instead of MPRIS. */
	public static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	/** True on macOS, where the song comes from asking Spotify and Music directly. */
	public static boolean mac() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
	}

	private static MediaSource musicSource(Path cache) {
		if (windows()) {
			return new WindowsMediaSource(() -> SkyLyricsConfig.get().preferredPlayer, cache.resolve("windows"));
		}
		if (mac()) {
			return new MacMediaSource(() -> SkyLyricsConfig.get().preferredPlayer);
		}
		return new MprisSource(() -> SkyLyricsConfig.get().preferredPlayer);
	}

	private void registerKeys() {
		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		openSettings = key("open_settings", InputConstants.KEY_J, category);
		openLibrary = key("library", InputConstants.KEY_N, category);
		toggleLyrics = key("toggle", InputConstants.UNKNOWN.getValue(), category);
		showNowPlaying = key("now_playing", InputConstants.UNKNOWN.getValue(), category);
		playPause = key("play_pause", InputConstants.UNKNOWN.getValue(), category);
		nextTrack = key("next", InputConstants.UNKNOWN.getValue(), category);
		previousTrack = key("previous", InputConstants.UNKNOWN.getValue(), category);
		lyricsLate = key("lyrics_late", InputConstants.UNKNOWN.getValue(), category);
		lyricsEarly = key("lyrics_early", InputConstants.UNKNOWN.getValue(), category);
	}

	/**
	 * The library key was K until 0.6.2, which is also Iris's key for turning shaders on and off, so pressing it did both.
	 * If ours still sits on the same key as that toggle, move ours back to its default.
	 */
	private static void moveOffShaderToggle(Minecraft client, KeyMapping ours) {
		for (KeyMapping other : client.options.keyMappings) {
			if (other != ours && other.getName().equals("iris.keybind.toggleShaders") && other.same(ours)
					&& !ours.isDefault()) {
				ours.setKey(ours.getDefaultKey());
				KeyMapping.resetMapping();
				client.options.save();
			}
		}
	}

	private static KeyMapping key(String name, int code, KeyMapping.Category category) {
		//? if >=26.2 {
		/*return KeyMappingHelper.registerKeyMapping(
		*///?} else {
		return KeyBindingHelper.registerKeyBinding(
		//?}
				new KeyMapping("key.skylyrics." + name, code, category));
	}

	private void tick(Minecraft client) {
		art.upload();
		if (!keysChecked) {
			keysChecked = true;
			moveOffShaderToggle(client, openLibrary);
		}
		while (openSettings.consumeClick()) {
			//? if >=26.2 {
			/*if (client.gui.screen() == null) {
				client.gui.setScreen(new SkyLyricsScreen(null));
			*///?} else {
			if (client.screen == null) {
				client.setScreen(new SkyLyricsScreen(null));
			//?}
			}
		}
		while (openLibrary.consumeClick()) {
			//? if >=26.2 {
			/*if (client.gui.screen() == null) {
				client.gui.setScreen(new LibraryScreen(null));
			*///?} else {
			if (client.screen == null) {
				client.setScreen(new LibraryScreen(null));
			//?}
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
		while (lyricsLate.consumeClick()) {
			nudgeTiming(client, SYNC_STEP_MS);
		}
		while (lyricsEarly.consumeClick()) {
			nudgeTiming(client, -SYNC_STEP_MS);
		}
	}

	private static final int SYNC_STEP_MS = 100;

	/** Fixes the timing of just the song playing now, and says so above the hotbar. */
	private void nudgeTiming(Minecraft client, int ms) {
		Track track = tracker.track();
		if (track == Track.NONE) {
			//? if >=26.2 {
			/*client.gui.hud.setOverlayMessage(Component.translatable("skylyrics.timing.none"), false);
			*///?} else {
			client.gui.setOverlayMessage(Component.translatable("skylyrics.timing.none"), false);
			//?}
			return;
		}
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		cfg.setSongOffset(track, cfg.songOffset(track) + ms);
		cfg.save();
		//? if >=26.2 {
		/*client.gui.hud.setOverlayMessage(Component.translatable("skylyrics.timing.song", SkyLyricsScreen.seconds(cfg.songOffset(track))), false);
		*///?} else {
		client.gui.setOverlayMessage(Component.translatable("skylyrics.timing.song", SkyLyricsScreen.seconds(cfg.songOffset(track))), false);
		//?}
	}

	private void shutdown() {
		spotify.cancelLogin();
		tracker.shutdown();
		lyrics.shutdown();
		art.close();
		covers.close();
	}

	public MediaTracker tracker() {
		return tracker;
	}

	public SpotifyWebSource spotify() {
		return spotify;
	}

	public Library library() {
		return library;
	}

	public LyricsService lyrics() {
		return lyrics;
	}

	public CoverCache covers() {
		return covers;
	}

	public AlbumArt art() {
		return art;
	}

	/** The key that opens the settings, so the screen can close on it too. */
	public KeyMapping openSettingsKey() {
		return openSettings;
	}

	/** The key that opens the song library, so the screen can close on it too. */
	public KeyMapping libraryKey() {
		return openLibrary;
	}

	public LyricsRenderer renderer() {
		return renderer;
	}

	public NowPlayingHud hud() {
		return hud;
	}
}
