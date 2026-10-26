package dev.beli.skylyrics.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.IntFunction;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.gui.Controls.Frame;
import dev.beli.skylyrics.media.DemoLibrary;
import dev.beli.skylyrics.media.Library;
import dev.beli.skylyrics.media.LibraryItem;
import dev.beli.skylyrics.media.LibraryPlaylist;
import dev.beli.skylyrics.media.SpotifyLibrary.Page;
import dev.beli.skylyrics.media.SpotifyWebSource;

/** Browse your songs as a grid of covers and click one to play it. Drawn by hand like the settings window. */
public final class LibraryScreen extends Screen {
	private static final int HEADER_H = 52;
	private static final int TEXT_H = 22;
	private static final int SEARCH_W = 130;
	private static final long SEARCH_WAIT_NS = 350_000_000L;
	private static final float STAGGER = 0.03f;
	private static final float POP = 0.35f;
	private static final float TOAST_TIME = 3;
	/** How many of the following songs are queued after the one clicked. */
	private static final int QUEUE = 50;

	private enum Section { SEARCH, LIKED, PLAYLISTS }

	private static Section lastSection = Section.LIKED;

	private final @Nullable Screen parent;
	private final SkyLyricsClient mod = SkyLyricsClient.get();
	private @Nullable Library library;
	private boolean demo;
	private Section section = lastSection;
	private final Map<Section, Shelf> shelves = new EnumMap<>(Section.class);
	private @Nullable Shelf opened;
	private String openedName = "";

	private String query = "";
	private boolean typing;
	private long typedAt;
	private boolean searchDirty;

	private int selected = -1;
	private @Nullable CompletableFuture<String> playing;
	private String playingTitle = "";
	private String toast = "";
	private float toastAge = TOAST_TIME;

	private float clock;
	private long lastFrame;
	private float openAnim;
	private int px, py, pw, ph;
	private int viewX, viewY, viewW, viewH;
	private int cols, cellW, cellH;

	public LibraryScreen(@Nullable Screen parent) {
		super(Component.literal("SkyLyrics library"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		// Runs again after coming back from the settings, so signing in there takes effect right away
		boolean wantDemo = demo || SkyLyricsConfig.get().demoMode;
		Library next = wantDemo ? new DemoLibrary() : mod.spotify().canBrowse() ? mod.library() : null;
		if (next != null && (library == null || wantDemo != (library instanceof DemoLibrary))) {
			library = next;
			demo = wantDemo;
			shelves.clear();
			opened = null;
		}
	}

	/** A list being loaded page by page as you scroll. */
	private final class Shelf {
		final IntFunction<CompletableFuture<? extends Page<?>>> loader;
		final List<Object> items = new ArrayList<>();
		final List<Float> born = new ArrayList<>();
		final List<Float> lift = new ArrayList<>();
		@Nullable CompletableFuture<? extends Page<?>> pending;
		int next;
		boolean loaded;
		String error = "";
		float scroll;
		float scrollTarget;

		Shelf(IntFunction<CompletableFuture<? extends Page<?>>> loader) {
			this.loader = loader;
		}

		void more() {
			if (pending == null && next >= 0) {
				error = "";
				pending = loader.apply(next);
			}
		}

		void poll() {
			if (pending == null || !pending.isDone()) {
				return;
			}
			try {
				Page<?> page = pending.join();
				float start = Math.max(clock, born.isEmpty() ? 0 : born.getLast());
				for (int i = 0; i < page.items().size(); i++) {
					items.add(page.items().get(i));
					born.add(start + i * STAGGER);
					lift.add(0f);
				}
				next = page.next();
			} catch (CompletionException e) {
				error = message(e.getCause());
			}
			pending = null;
			loaded = true;
		}
	}

	private @Nullable Shelf shelf() {
		if (library == null) {
			return null;
		}
		if (opened != null) {
			return opened;
		}
		Library lib = library;
		return shelves.computeIfAbsent(section, s -> {
			String q = query.strip();
			Shelf shelf = new Shelf(switch (s) {
				case SEARCH -> offset -> lib.search(q, offset);
				case LIKED -> lib::liked;
				case PLAYLISTS -> lib::playlists;
			});
			if (s != Section.SEARCH || !q.isEmpty()) {
				shelf.more();
			} else {
				shelf.next = -1;
				shelf.loaded = true;
			}
			return shelf;
		});
	}

	private static String message(@Nullable Throwable error) {
		return error instanceof IOException && error.getMessage() != null ? error.getMessage()
				: Tr.t("Something went wrong, try again");
	}

	private void layout() {
		pw = Math.min(460, width - 16);
		ph = Math.min(300, height - 16);
		px = (width - pw) / 2;
		py = (height - ph) / 2 + Math.round((1 - openAnim) * 8);
		viewX = px + 12;
		viewW = pw - 24;
		viewY = py + HEADER_H + 6;
		viewH = ph - HEADER_H - 12;
		cols = LibraryGrid.columns(viewW);
		cellW = LibraryGrid.cellWidth(viewW);
		cellH = cellW + TEXT_H;
	}

	private int contentHeight(Shelf shelf) {
		int rows = (shelf.items.size() + cols - 1) / cols;
		return Math.max(0, rows * (cellH + LibraryGrid.GAP) - LibraryGrid.GAP) + (shelf.pending != null ? 16 : 0);
	}

	private float maxScroll(Shelf shelf) {
		return Math.max(0, contentHeight(shelf) - viewH);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			super.renderBackground(g, mouseX, mouseY, partialTick);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		clock += dt;
		openAnim = Ui.approach(openAnim, 1, dt, 12);
		toastAge += dt;
		if (searchDirty && now - typedAt >= SEARCH_WAIT_NS) {
			searchDirty = false;
			shelves.remove(Section.SEARCH);
			selected = -1;
		}
		pollPlaying();
		layout();

		Frame f = new Frame(g, font, mouseX, mouseY, dt, true);
		Ui.rect(g, px - 1, py - 1, pw + 2, ph + 2, 9, 0x50000000);
		Ui.rect(g, px, py, pw, ph, 8, Ui.PANEL);
		renderHeader(g, f);
		Shelf shelf = shelf();
		if (shelf == null) {
			renderSignIn(g, f);
		} else {
			shelf.poll();
			shelf.scrollTarget = Mth.clamp(shelf.scrollTarget, 0, maxScroll(shelf));
			shelf.scroll = Ui.approach(shelf.scroll, shelf.scrollTarget, dt, 18);
			if (shelf.scrollTarget + viewH > contentHeight(shelf) - cellH && shelf.error.isEmpty()) {
				shelf.more();
			}
			renderShelf(g, f, shelf);
		}
		renderToast(g);
	}

	private void renderHeader(GuiGraphics g, Frame f) {
		Ui.rect(g, px + 10, py + 9, 16, 16, 4, Ui.ACCENT);
		g.drawCenteredString(font, "♪", px + 18, py + 13, 0xFFFFFFFF);
		g.drawString(font, Ui.visual(Tr.t("Song library")), px + 31, py + 8, Ui.TEXT, false);
		String subtitle = library == null ? Tr.t("Your Spotify songs")
				: demo ? Tr.t("Made-up songs, for trying it out") : Tr.t("From your Spotify");
		g.drawString(font, Ui.visual(subtitle), px + 31, py + 18, Ui.DIM, false);

		int cx = px + pw - 22;
		boolean closeHover = Ui.inside(f.mx(), f.my(), cx, py + 8, 14, 14);
		if (closeHover) {
			Ui.rect(g, cx, py + 8, 14, 14, 4, Ui.CONTROL_HOVER);
		}
		g.drawCenteredString(font, "×", cx + 7, py + 11, closeHover ? 0xFFFFFFFF : Ui.MUTED);

		if (library != null) {
			int sx = searchX();
			boolean hover = Ui.inside(f.mx(), f.my(), sx, py + 8, SEARCH_W, 15);
			Ui.rect(g, sx, py + 8, SEARCH_W, 15, 4, typing || hover ? Ui.CONTROL_HOVER : Ui.CONTROL);
			if (typing) {
				Ui.outline(g, sx, py + 8, SEARCH_W, 15, 4, Ui.ACCENT);
			}
			String shown = query.isEmpty() && !typing ? Tr.t("Search songs") : query;
			String clipped = shown.length() > 0 && font.width(shown) > SEARCH_W - 20
					? "…" + tail(shown, SEARCH_W - 26) : shown;
			boolean caret = typing && (clock % 1f) < 0.55f;
			g.drawString(font, "⌕", sx + 5, py + 12, Ui.DIM, false);
			g.drawString(font, Ui.visual(clipped + (caret ? "_" : "")), sx + 15, py + 12,
					query.isEmpty() && !typing ? Ui.DIM : Ui.TEXT, false);
		}

		int y = py + 31;
		if (opened != null) {
			String back = "‹ " + Tr.t("Playlists");
			int w = font.width(back) + 14;
			boolean hover = Ui.inside(f.mx(), f.my(), px + 10, y, w, 14);
			Ui.rect(g, px + 10, y, w, 14, 7, hover ? Ui.CONTROL_HOVER : Ui.CONTROL);
			g.drawString(font, Ui.visual(back), px + 17, y + 3, Ui.TEXT, false);
			g.drawString(font, Ui.visual(Ui.clip(font, openedName, pw - w - 40)), px + 18 + w, y + 3, Ui.MUTED, false);
		} else if (library != null) {
			int x = px + 10;
			for (Section s : Section.values()) {
				String name = sectionName(s);
				int w = font.width(name) + 14;
				boolean hover = Ui.inside(f.mx(), f.my(), x, y, w, 14);
				if (s == section) {
					Ui.rect(g, x, y, w, 14, 7, Ui.ACCENT_SOFT);
				} else if (hover) {
					Ui.rect(g, x, y, w, 14, 7, 0x14FFFFFF);
				}
				g.drawString(font, Ui.visual(name), x + 7, y + 3, s == section ? 0xFFFFFFFF : hover ? Ui.TEXT : Ui.MUTED, false);
				x += w + 4;
			}
		}
		g.fill(px + 10, py + HEADER_H - 1, px + pw - 10, py + HEADER_H, Ui.DIVIDER);
	}

	private String tail(String text, int maxW) {
		int i = 0;
		while (i < text.length() && font.width(text.substring(i)) > maxW) {
			i++;
		}
		return text.substring(i);
	}

	private int searchX() {
		return px + pw - 30 - SEARCH_W;
	}

	private static String sectionName(Section s) {
		return switch (s) {
			case SEARCH -> Tr.t("Search");
			case LIKED -> Tr.t("Liked songs");
			case PLAYLISTS -> Tr.t("Playlists");
		};
	}

	private void renderShelf(GuiGraphics g, Frame f, Shelf shelf) {
		if (shelf.items.isEmpty()) {
			String text;
			if (!shelf.error.isEmpty()) {
				text = shelf.error;
			} else if (!shelf.loaded) {
				text = Tr.t("Loading") + ".".repeat(1 + (int) (clock * 3) % 3);
			} else {
				text = emptyText();
			}
			g.drawCenteredString(font, Ui.visual(text), px + pw / 2, viewY + viewH / 2 - 10, Ui.MUTED);
			if (!shelf.error.isEmpty()) {
				int bx = px + pw / 2 - 40, by = viewY + viewH / 2 + 4;
				Controls.drawButton(f, bx, by, 80, 16, Tr.t("Try again"), Ui.inside(f.mx(), f.my(), bx, by, 80, 16), true, true);
			}
			return;
		}

		boolean inView = Ui.inside(f.mx(), f.my(), viewX, viewY, viewW, viewH);
		g.enableScissor(viewX - 4, viewY, viewX + viewW + 4, viewY + viewH);
		int top = viewY - Math.round(shelf.scroll);
		for (int i = 0; i < shelf.items.size(); i++) {
			int x = viewX + (i % cols) * (cellW + LibraryGrid.GAP);
			int y = top + (i / cols) * (cellH + LibraryGrid.GAP);
			if (y + cellH < viewY || y > viewY + viewH) {
				continue;
			}
			boolean hover = inView && Ui.inside(f.mx(), f.my(), x, y, cellW, cellH);
			float lift = Ui.approach(shelf.lift.get(i), hover || i == selected ? 1 : 0, f.dt(), 14);
			shelf.lift.set(i, lift);
			float t = Mth.clamp((clock - shelf.born.get(i)) / POP, 0, 1);
			if (t <= 0) {
				continue;
			}
			renderCard(g, shelf.items.get(i), x, y - Math.round(lift * 2), LibraryGrid.popIn(t), t, lift, i == selected);
		}
		if (shelf.pending != null) {
			int y = top + contentHeight(shelf) - 12;
			g.drawCenteredString(font, Ui.visual(Tr.t("Loading") + ".".repeat(1 + (int) (clock * 3) % 3)), px + pw / 2, y, Ui.DIM);
		} else if (!shelf.error.isEmpty()) {
			g.drawCenteredString(font, Ui.visual(shelf.error), px + pw / 2, top + contentHeight(shelf) + 4, Ui.MUTED);
		}
		g.disableScissor();

		float max = maxScroll(shelf);
		if (max > 0) {
			int barH = Math.max(16, viewH * viewH / (viewH + Math.round(max)));
			int barY = viewY + Math.round((viewH - barH) * (shelf.scroll / max));
			Ui.rect(g, px + pw - 7, barY, 2, barH, 1, Ui.TRACK);
		}
	}

	private String emptyText() {
		if (opened != null) {
			return Tr.t("This playlist is empty");
		}
		return switch (section) {
			case SEARCH -> query.isBlank() ? Tr.t("Type to find a song") : Tr.t("Nothing found for “%s”", query.strip());
			case LIKED -> Tr.t("No liked songs yet");
			case PLAYLISTS -> Tr.t("No playlists yet");
		};
	}

	private void renderCard(GuiGraphics g, Object entry, int x, int y, float scale, float alpha, float lift, boolean chosen) {
		String title;
		String under;
		String colorKey;
		if (entry instanceof LibraryItem item) {
			title = item.title();
			under = item.artists();
			colorKey = item.album().isEmpty() ? item.title() : item.album();
		} else {
			LibraryPlaylist list = (LibraryPlaylist) entry;
			title = list.name();
			under = Tr.t("%s songs", list.songs());
			colorKey = list.name();
		}
		int rgb = Mth.hsvToRgb(LibraryGrid.hue(colorKey), 0.55f, 0.8f);

		g.pose().pushMatrix();
		g.pose().translate(x + cellW / 2f, y + cellW / 2f);
		g.pose().scale(scale, scale);
		g.pose().translate(-(x + cellW / 2f), -(y + cellW / 2f));
		if (lift > 0.01f) {
			Ui.rect(g, x - 2, y - 2, cellW + 4, cellW + 4, 7, Ui.alpha(0xFF000000 | rgb, 0.35f * lift * alpha));
		}
		Ui.rect(g, x, y, cellW, cellW, 5, Ui.alpha(0xFF000000 | rgb, alpha));
		Ui.rect(g, x, y + cellW / 2, cellW, cellW / 2, 5, Ui.alpha(0x30000000, alpha));
		String initial = title.isEmpty() ? "♪" : title.substring(0, title.offsetByCodePoints(0, 1)).toUpperCase();
		g.pose().pushMatrix();
		g.pose().translate(x + cellW / 2f, y + cellW / 2f - 7);
		g.pose().scale(2, 2);
		g.drawCenteredString(font, initial, 0, 0, Ui.alpha(0xFFFFFFFF, 0.85f * alpha));
		g.pose().popMatrix();
		if (chosen) {
			Ui.outline(g, x - 1, y - 1, cellW + 2, cellW + 2, 6, Ui.alpha(Ui.ACCENT, alpha));
		}
		g.drawString(font, Ui.visual(Ui.clip(font, title, cellW)), x, y + cellW + 3, Ui.alpha(Ui.TEXT, alpha), false);
		g.drawString(font, Ui.visual(Ui.clip(font, under, cellW)), x, y + cellW + 12, Ui.alpha(Ui.DIM, alpha), false);
		g.pose().popMatrix();
	}

	private void renderSignIn(GuiGraphics g, Frame f) {
		SpotifyWebSource spotify = mod.spotify();
		String text = switch (spotify.account()) {
			case NO_CLIENT_ID -> Tr.t("Connect Spotify on the Music tab to see your songs here");
			case SIGNED_IN -> Tr.t("Sign in to Spotify again so SkyLyrics can see your songs");
			default -> Tr.t("Sign in to Spotify on the Music tab to see your songs here");
		};
		int w = Math.min(300, pw - 40), x = px + (pw - w) / 2, y = viewY + viewH / 2 - 44;
		Ui.rect(g, x, y, w, 80, 8, Ui.CARD);
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text), w - 24);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			g.drawCenteredString(font, lines.get(i), x + w / 2, y + 14 + i * 11, Ui.TEXT);
		}
		int by = y + 50;
		Controls.drawButton(f, x + w / 2 - 104, by, 100, 18, Tr.t("Open Music tab"), Ui.inside(f.mx(), f.my(), x + w / 2 - 104, by, 100, 18), true, true);
		Controls.drawButton(f, x + w / 2 + 4, by, 100, 18, Tr.t("Try made-up songs"), Ui.inside(f.mx(), f.my(), x + w / 2 + 4, by, 100, 18), false, true);
	}

	private void renderToast(GuiGraphics g) {
		if (toast.isEmpty() || toastAge >= TOAST_TIME) {
			return;
		}
		float a = Mth.clamp(Math.min(toastAge * 6, (TOAST_TIME - toastAge) * 3), 0, 1);
		String text = Ui.clip(font, toast, pw - 60);
		int w = font.width(text) + 20, x = px + (pw - w) / 2, y = py + ph - 24 + Math.round((1 - a) * 6);
		Ui.rect(g, x, y, w, 16, 8, Ui.alpha(Ui.CONTROL_HOVER, a));
		g.drawCenteredString(font, Ui.visual(text), px + pw / 2, y + 4, Ui.alpha(Ui.TEXT, a));
	}

	private void say(String text) {
		toast = text;
		toastAge = 0;
	}

	private void pollPlaying() {
		if (playing == null || !playing.isDone()) {
			return;
		}
		String error;
		try {
			error = playing.join();
		} catch (CompletionException e) {
			error = message(e.getCause());
		}
		playing = null;
		if (error.isEmpty()) {
			say(Tr.t("Playing %s", playingTitle));
			mod.tracker().refresh();
		} else {
			say(error);
		}
	}

	private void activate(Shelf shelf, int index) {
		if (library == null || index < 0 || index >= shelf.items.size()) {
			return;
		}
		selected = index;
		Object entry = shelf.items.get(index);
		if (entry instanceof LibraryPlaylist list) {
			if (!list.openable()) {
				say(Tr.t("Spotify doesn't let apps open this playlist"));
				return;
			}
			Library lib = library;
			Shelf songs = new Shelf(offset -> lib.playlist(list, offset));
			songs.more();
			opened = songs;
			openedName = list.name();
			selected = -1;
			return;
		}
		if (playing != null) {
			return;
		}
		LibraryItem item = (LibraryItem) entry;
		List<LibraryItem> after = new ArrayList<>();
		for (int i = index + 1; i < shelf.items.size() && after.size() < QUEUE; i++) {
			if (shelf.items.get(i) instanceof LibraryItem next) {
				after.add(next);
			}
		}
		playingTitle = item.title();
		say(Tr.t("Starting %s…", item.title()));
		toastAge = 0;
		playing = library.play(item, after);
	}

	private void showSection(Section s) {
		opened = null;
		section = s;
		selected = -1;
		if (s == Section.SEARCH) {
			typing = true;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		if (Ui.inside(mx, my, px + pw - 22, py + 8, 14, 14)) {
			onClose();
			return true;
		}
		if (library == null) {
			int w = Math.min(300, pw - 40), x = px + (pw - w) / 2, by = viewY + viewH / 2 - 44 + 50;
			if (Ui.inside(mx, my, x + w / 2 - 104, by, 100, 18)) {
				openMusicTab();
			} else if (Ui.inside(mx, my, x + w / 2 + 4, by, 100, 18)) {
				demo = true;
				init();
			}
			return true;
		}
		typing = Ui.inside(mx, my, searchX(), py + 8, SEARCH_W, 15);
		if (typing) {
			if (section != Section.SEARCH || opened != null) {
				showSection(Section.SEARCH);
			}
			return true;
		}
		int y = py + 31;
		if (opened != null) {
			if (Ui.inside(mx, my, px + 10, y, font.width("‹ " + Tr.t("Playlists")) + 14, 14)) {
				opened = null;
				selected = -1;
			}
		} else {
			int x = px + 10;
			for (Section s : Section.values()) {
				int w = font.width(sectionName(s)) + 14;
				if (Ui.inside(mx, my, x, y, w, 14)) {
					showSection(s);
					return true;
				}
				x += w + 4;
			}
		}
		Shelf shelf = shelf();
		if (shelf != null && Ui.inside(mx, my, viewX, viewY, viewW, viewH)) {
			if (shelf.items.isEmpty() && !shelf.error.isEmpty()) {
				shelf.more();
				return true;
			}
			int top = viewY - Math.round(shelf.scroll);
			int col = (int) ((mx - viewX) / (cellW + LibraryGrid.GAP));
			int row = (int) Math.floor((my - top) / (cellH + LibraryGrid.GAP));
			int index = row * cols + col;
			if (col < cols && row >= 0 && Ui.inside(mx, my, viewX + col * (cellW + LibraryGrid.GAP), top + row * (cellH + LibraryGrid.GAP), cellW, cellH)) {
				activate(shelf, index);
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void openMusicTab() {
		if (parent instanceof SkyLyricsScreen settings) {
			settings.selectTab(2);
			minecraft.setScreen(settings);
			return;
		}
		SkyLyricsScreen settings = new SkyLyricsScreen(this);
		settings.selectTab(2);
		minecraft.setScreen(settings);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		Shelf shelf = shelf();
		if (shelf != null) {
			shelf.scrollTarget = Mth.clamp(shelf.scrollTarget - (float) scrollY * 30, 0, maxScroll(shelf));
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			if (typing) {
				typing = false;
			} else if (opened != null) {
				opened = null;
				selected = -1;
			} else {
				onClose();
			}
			return true;
		}
		KeyMapping key = mod.libraryKey();
		if (!typing && key != null && key.matches(event)) {
			onClose();
			return true;
		}
		if (library == null) {
			return super.keyPressed(event);
		}
		if (typing) {
			if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
				edit(event.hasControlDown() ? "" : query.substring(0, query.offsetByCodePoints(query.length(), -1)));
				return true;
			}
			if (event.isPaste()) {
				edit(query + minecraft.keyboardHandler.getClipboard().replaceAll("\\s+", " "));
				return true;
			}
		}
		if (event.isCycleFocus()) {
			Section[] all = Section.values();
			showSection(all[Math.floorMod(section.ordinal() + (event.hasShiftDown() ? -1 : 1), all.length)]);
			typing = section == Section.SEARCH;
			return true;
		}
		Shelf shelf = shelf();
		if (shelf == null) {
			return super.keyPressed(event);
		}
		int step = event.isLeft() ? -1 : event.isRight() ? 1 : event.isUp() ? -cols : event.isDown() ? cols : 0;
		if (step != 0 && !(typing && (event.isLeft() || event.isRight()))) {
			typing = false;
			selected = LibraryGrid.move(selected, step, shelf.items.size());
			reveal(shelf);
			return true;
		}
		if (event.isConfirmation() && selected >= 0) {
			activate(shelf, selected);
			return true;
		}
		return super.keyPressed(event);
	}

	private void reveal(Shelf shelf) {
		if (selected < 0) {
			return;
		}
		int y = (selected / cols) * (cellH + LibraryGrid.GAP);
		if (y < shelf.scrollTarget) {
			shelf.scrollTarget = y;
		} else if (y + cellH > shelf.scrollTarget + viewH) {
			shelf.scrollTarget = y + cellH - viewH;
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (library == null || !event.isAllowedChatCharacter()) {
			return false;
		}
		if (!typing) {
			// Typing anywhere starts a search
			showSection(Section.SEARCH);
		}
		if (query.length() < 80) {
			edit(query + event.codepointAsString());
		}
		return true;
	}

	private void edit(String text) {
		query = text.length() > 80 ? text.substring(0, 80) : text;
		typedAt = System.nanoTime();
		searchDirty = true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		lastSection = section;
	}
}
