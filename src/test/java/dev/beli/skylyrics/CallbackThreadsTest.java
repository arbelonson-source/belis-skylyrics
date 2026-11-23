package dev.beli.skylyrics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every Fabric event the mod listens to, with the thread Fabric calls it on (read in Fabric API's and Minecraft's code for
 * each Minecraft version the mod builds, 1.21.1 to 26.3). The game draws its frames on the game thread, so a handler
 * that Fabric calls on another thread must hand its work to the game thread ({@code client.execute}), or it changes what
 * a frame may be drawing at that moment. The sources are read with every version's code in them (the other versions'
 * lines are comments), so an event only one version uses counts too.
 */
class CallbackThreadsTest {
	private static final Path SOURCES = Path.of("src/client/java");

	/** Where an event is called from: {@code game} is true for the game (render) thread. */
	private record Fired(boolean game, String where) {
	}

	private static final Map<String, Fired> EVENTS = Map.ofEntries(
			Map.entry("ClientTickEvents.END_CLIENT_TICK", new Fired(true, "the end of Minecraft.tick")),
			Map.entry("ClientLifecycleEvents.CLIENT_STOPPING",
					new Fired(true, "Minecraft.destroy (exitWorldAndClose on 26.3), after the game loop ends")),
			Map.entry("ClientPlayConnectionEvents.JOIN", new Fired(true,
					"the end of ClientPacketListener.handleLogin, which only gets past its first line on the game thread")),
			// Fabric's ConnectionMixin calls it from Connection.channelInactive, on Netty's thread, when the connection drops,
			// the server kicks you or you leave; from Connection.handleDisconnection on the game thread only when that comes first
			Map.entry("ClientPlayConnectionEvents.DISCONNECT", new Fired(false, "Connection.channelInactive (Netty's thread)")),
			Map.entry("ScreenEvents.AFTER_INIT", new Fired(true, "Screen.init, from Minecraft.setScreen")),
			Map.entry("ScreenEvents.afterRender", new Fired(true, "Screen drawing (before 26.1)")),
			Map.entry("ScreenEvents.afterExtract", new Fired(true, "Screen drawing (26.1 and later)")),
			Map.entry("WorldRenderEvents.BEFORE_ENTITIES", new Fired(true, "LevelRenderer (1.21.9 to 1.21.11)")),
			Map.entry("WorldRenderEvents.AFTER_ENTITIES", new Fired(true, "LevelRenderer (before 1.21.9)")),
			Map.entry("WorldRenderEvents.END_MAIN", new Fired(true, "LevelRenderer (1.21.9 to 1.21.11)")),
			Map.entry("LevelRenderEvents.COLLECT_SUBMITS", new Fired(true, "LevelRenderer (26.1 and later)")),
			Map.entry("LevelRenderEvents.END_MAIN", new Fired(true, "LevelRenderer (26.1 and later)")),
			Map.entry("LivingEntityFeatureRendererRegistrationCallback.EVENT",
					new Fired(true, "EntityRenderers, when a resource reload applies (before 26.1)")),
			Map.entry("LivingEntityRenderLayerRegistrationCallback.EVENT",
					new Fired(true, "EntityRenderers, when a resource reload applies (26.1 and later)")),
			Map.entry("HudLayerRegistrationCallback.EVENT", new Fired(true, "the Gui's construction (1.21.2 to 1.21.5)")),
			Map.entry("HudRenderCallback.EVENT", new Fired(true, "Gui.render (1.21.1)")),
			Map.entry("HudElementRegistry.addFirst", new Fired(true, "Gui.render (1.21.6 and later)")),
			Map.entry("HudElementRegistry.attachElementAfter", new Fired(true, "Gui.render (1.21.6 and later)")));

	/** An event holder's member being registered to ("ScreenEvents.afterRender(screen).register(" too), and the HUD registry's calls. */
	private static final Pattern REGISTER = Pattern.compile(
			"\\b([A-Z]\\w*(?:Events|Callback))\\.(\\w+)(?:\\([^()]*\\))?\\.register\\(|\\b(HudElementRegistry)\\.(\\w+)\\(");

	private record Registration(String event, String file, String handler) {
	}

	private static List<Registration> registrations() throws IOException {
		List<Registration> out = new ArrayList<>();
		List<Path> files;
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			files = walk.filter(f -> f.toString().endsWith(".java")).sorted().toList();
		}
		for (Path file : files) {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			Matcher m = REGISTER.matcher(text);
			while (m.find()) {
				String event = m.group(1) != null ? m.group(1) + "." + m.group(2) : m.group(3) + "." + m.group(4);
				out.add(new Registration(event, SOURCES.relativize(file).toString(), argument(text, m.end())));
			}
		}
		return out;
	}

	/** The registration's argument: from just after its opening bracket to the bracket that closes it. */
	private static String argument(String text, int from) {
		int depth = 1;
		for (int i = from; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')' && --depth == 0) {
				return text.substring(from, i);
			}
		}
		return text.substring(from);
	}

	@Test
	void theModStillListensToItsEvents() throws IOException {
		List<String> seen = registrations().stream().map(Registration::event).toList();
		// The scan must find the registrations at all, or the checks below pass on nothing
		for (String event : List.of("ClientTickEvents.END_CLIENT_TICK", "ClientPlayConnectionEvents.JOIN", "ClientPlayConnectionEvents.DISCONNECT",
				"ClientLifecycleEvents.CLIENT_STOPPING", "ScreenEvents.AFTER_INIT")) {
			assertTrue(seen.contains(event), event + " not found in " + seen);
		}
	}

	@Test
	void everyEventTheModListensToHasItsThreadWrittenDown() throws IOException {
		for (Registration r : registrations()) {
			assertTrue(EVENTS.containsKey(r.event()), r.file() + " listens to " + r.event()
					+ ": find which thread Fabric calls it on and add it to EVENTS (a handler off the game thread must hand off)");
		}
	}

	/** {@code (handler, client) -> client.execute(...)}: the handler's only work is to give the rest to the game thread. */
	private static final Pattern HANDS_OFF = Pattern.compile("^\\s*\\(\\s*\\w+\\s*,\\s*(\\w+)\\s*\\)\\s*->\\s*\\1\\.execute\\(");

	@Test
	void aHandlerCalledOffTheGameThreadHandsItsWorkToTheGameThread() throws IOException {
		boolean any = false;
		for (Registration r : registrations()) {
			Fired fired = EVENTS.get(r.event());
			if (fired == null || fired.game()) {
				continue;
			}
			any = true;
			// The old leave handler cleared the World words' list of players (and the stars and the underground check) on
			// Netty's thread while a frame could be walking it: an error, and those lyrics paused for 5 seconds
			assertTrue(HANDS_OFF.matcher(r.handler()).find(), r.file() + ": " + r.event() + " is called from " + fired.where()
					+ ", so its handler must be (handler, client) -> client.execute(...), not: " + r.handler().strip());
		}
		assertTrue(any, "no handler off the game thread was checked");
		assertFalse(EVENTS.get("ClientPlayConnectionEvents.DISCONNECT").game());
	}
}
