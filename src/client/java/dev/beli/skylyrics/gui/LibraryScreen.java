package dev.beli.skylyrics.gui;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.Tr;
import dev.beli.skylyrics.config.SkyLyricsConfig;
import dev.beli.skylyrics.gui.Controls.Frame;
import dev.beli.skylyrics.media.DemoLibrary;
import dev.beli.skylyrics.media.Library;
import dev.beli.skylyrics.media.LibraryItem;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.media.LibraryPlaylist;
import dev.beli.skylyrics.media.SpotifyLibrary.Page;
import dev.beli.skylyrics.media.SpotifyWebSource;
import dev.beli.skylyrics.render.CoverCache;

/** Browse your songs as a grid of covers and click one to play it. Drawn by hand like the settings window. */
public final class LibraryScreen extends Screen {
	private static final int HEADER_H = 52;
	private static final int TEXT_H = 22;
	private static final int SEARCH_W = 130;
	private static final int SHELF_COVER = 84;
	/** Space under a shelf row for the floor line. */
	private static final int SHELF_FLOOR = 12;
	private static final int TOGGLE_W = 18;
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
	/** Shelf view: rows that fit and the distance between columns. */
	private int shelfRows, shelfStride;
	/** Carousel view: where the glide has got to, in item places. */
	private float carPos;
	private String tip = "";

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
		shelfRows = LibraryViews.shelfRows(viewH - TEXT_H - 4, SHELF_COVER + SHELF_FLOOR);
		shelfStride = SHELF_COVER + LibraryGrid.GAP;
	}

	private SkyLyricsConfig.LibraryView view() {
		return SkyLyricsConfig.get().libraryView;
	}

	private int carSize() {
		return Math.max(40, Math.min(Math.min(viewH - TEXT_H - 16, viewW / 3), 130));
	}

	private int contentHeight(Shelf shelf) {
		int rows = (shelf.items.size() + cols - 1) / cols;
		return Math.max(0, rows * (cellH + LibraryGrid.GAP) - LibraryGrid.GAP) + (shelf.pending != null ? 16 : 0);
	}

	private float maxScroll(Shelf shelf) {
		if (view() == SkyLyricsConfig.LibraryView.SHELF) {
			return Math.max(0, LibraryViews.shelfWidth(shelf.items.size(), shelfRows, shelfStride, LibraryGrid.GAP)
					+ (shelf.pending != null ? 16 : 0) - viewW);
		}
		if (view() == SkyLyricsConfig.LibraryView.CAROUSEL) {
			return 0;
		}
		return Math.max(0, contentHeight(shelf) - viewH);
	}

	@Override
	//? if >=26.1 {
	/*public void extractBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	*///?} else {
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	//?}
		if (minecraft.level == null) {
			//? if >=26.1 {
			/*super.extractBackground(g, mouseX, mouseY, partialTick);
			*///?} else {
			super.renderBackground(g, mouseX, mouseY, partialTick);
			//?}
		}
	}

	@Override
	//? if >=26.1 {
	/*public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	*///?} else {
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	//?}
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
		mod.covers().upload();
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
			boolean nearEnd = switch (view()) {
				case SHELF -> shelf.scrollTarget + shelfStride * 2 > maxScroll(shelf);
				case CAROUSEL -> selected >= shelf.items.size() - 6;
				default -> shelf.scrollTarget + viewH > contentHeight(shelf) - cellH;
			};
			if (nearEnd && shelf.error.isEmpty()) {
				shelf.more();
			}
			if (view() == SkyLyricsConfig.LibraryView.CAROUSEL && selected < 0 && !shelf.items.isEmpty()) {
				selected = 0;
			}
			carPos = LibraryViews.ease(carPos, Mth.clamp(selected, 0, Math.max(0, shelf.items.size() - 1)), dt, 12);
			renderShelf(g, f, shelf);
		}
		renderToast(g);
		if (!tip.isEmpty()) {
			int w = font.width(tip) + 8;
			int tx = px + pw - 10 - w;
			Ui.rect(g, tx, py + HEADER_H + 2, w, 12, 4, 0xF0101216);
			g.drawString(font, Ui.visual(tip), tx + 4, py + HEADER_H + 5, Ui.TEXT, false);
			tip = "";
		}
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
			g.drawString(font, Ui.visual(Ui.clip(font, openedName, pw - w - 100)), px + 18 + w, y + 3, Ui.MUTED, false);
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
		if (library != null) {
			renderViewToggle(g, f, y);
		}
		g.fill(px + 10, py + HEADER_H - 1, px + pw - 10, py + HEADER_H, Ui.DIVIDER);
	}

	private int toggleX(int i) {
		return px + pw - 10 - (3 - i) * TOGGLE_W - (2 - i) * 2;
	}

	/** Three small buttons in the header to pick grid, shelf or carousel. */
	private void renderViewToggle(GuiGraphics g, Frame f, int y) {
		SkyLyricsConfig.LibraryView[] views = SkyLyricsConfig.LibraryView.values();
		for (int i = 0; i < views.length; i++) {
			int x = toggleX(i);
			boolean on = views[i] == view();
			boolean hover = Ui.inside(f.mx(), f.my(), x, y, TOGGLE_W, 14);
			Ui.rect(g, x, y, TOGGLE_W, 14, 5, on ? Ui.ACCENT_SOFT : hover ? Ui.CONTROL_HOVER : Ui.CONTROL);
			int c = on ? 0xFFFFFFFF : hover ? Ui.TEXT : Ui.MUTED;
			int cx = x + TOGGLE_W / 2, cy = y + 7;
			switch (views[i]) {
				case GRID -> {
					g.fill(cx - 4, cy - 4, cx - 1, cy - 1, c);
					g.fill(cx + 1, cy - 4, cx + 4, cy - 1, c);
					g.fill(cx - 4, cy + 1, cx - 1, cy + 4, c);
					g.fill(cx + 1, cy + 1, cx + 4, cy + 4, c);
				}
				case SHELF -> {
					g.fill(cx - 5, cy - 4, cx + 5, cy - 3, c);
					g.fill(cx - 5, cy + 3, cx + 5, cy + 4, c);
					g.fill(cx - 4, cy - 2, cx - 1, cy + 2, c);
					g.fill(cx + 1, cy - 2, cx + 4, cy + 2, c);
				}
				case CAROUSEL -> {
					g.fill(cx - 5, cy - 2, cx - 3, cy + 2, c);
					g.fill(cx - 2, cy - 4, cx + 2, cy + 4, c);
					g.fill(cx + 3, cy - 2, cx + 5, cy + 2, c);
				}
			}
			if (hover) {
				tip = switch (views[i]) {
					case GRID -> Tr.t("Grid");
					case SHELF -> Tr.t("Shelf");
					case CAROUSEL -> Tr.t("Carousel");
				};
			}
		}
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

		Track now = mod.tracker().track();
		boolean moving = mod.tracker().playing();
		boolean inView = Ui.inside(f.mx(), f.my(), viewX, viewY, viewW, viewH);
		if (view() == SkyLyricsConfig.LibraryView.SHELF) {
			renderRows(g, f, shelf, now, moving, inView);
			return;
		}
		if (view() == SkyLyricsConfig.LibraryView.CAROUSEL) {
			renderCarousel(g, shelf, now, moving);
			return;
		}
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
			Object entry = shelf.items.get(i);
			boolean current = entry instanceof LibraryItem item && LibraryGrid.isPlaying(item, now);
			renderCard(g, entry, x, y - Math.round(lift * 2), LibraryGrid.popIn(t), t, lift, i == selected, current, moving);
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

	private int shelfTop() {
		return viewY + Math.max(0, (viewH - TEXT_H - 4 - (SHELF_COVER + SHELF_FLOOR) * shelfRows) / 2);
	}

	/** Covers standing in rows on a shelf; the shelf scrolls sideways and the chosen cover shows its name. */
	private void renderRows(GuiGraphics g, Frame f, Shelf shelf, Track now, boolean moving, boolean inView) {
		int rowH = SHELF_COVER + SHELF_FLOOR;
		int top = shelfTop();
		int left = Math.round(shelf.scroll);
		int width = LibraryViews.shelfWidth(shelf.items.size(), shelfRows, shelfStride, LibraryGrid.GAP);
		g.enableScissor(viewX - 4, viewY, viewX + viewW + 4, viewY + viewH);
		for (int r = 0; r < shelfRows; r++) {
			int fy = top + r * rowH + SHELF_COVER + 4;
			int end = Math.min(viewX + viewW, viewX - left + width);
			if (end > viewX) {
				g.fill(viewX, fy, end, fy + 1, Ui.DIVIDER);
				g.fill(viewX + 2, fy + 1, end - 2, fy + 3, 0x18000000);
				g.fill(viewX + 6, fy + 3, end - 6, fy + 4, 0x0C000000);
			}
		}
		for (int i = 0; i < shelf.items.size(); i++) {
			int x = viewX + LibraryViews.shelfCol(i, shelfRows) * shelfStride - left;
			int y = top + LibraryViews.shelfRow(i, shelfRows) * rowH;
			if (x + SHELF_COVER < viewX - 4 || x > viewX + viewW + 4) {
				continue;
			}
			boolean hover = inView && Ui.inside(f.mx(), f.my(), x, y, SHELF_COVER, SHELF_COVER);
			float lift = Ui.approach(shelf.lift.get(i), hover || i == selected ? 1 : 0, f.dt(), 14);
			shelf.lift.set(i, lift);
			float t = Mth.clamp((clock - shelf.born.get(i)) / POP, 0, 1);
			if (t <= 0) {
				continue;
			}
			Object entry = shelf.items.get(i);
			boolean current = entry instanceof LibraryItem item && LibraryGrid.isPlaying(item, now);
			renderCard(g, entry, x, y - Math.round(lift * 3), SHELF_COVER, 1, false, LibraryGrid.popIn(t), t, lift, i == selected,
					current, moving);
		}
		if (shelf.pending != null) {
			g.drawString(font, Ui.visual(Tr.t("Loading") + ".".repeat(1 + (int) (clock * 3) % 3)), viewX - left + width + 8,
					top + SHELF_COVER / 2, Ui.DIM, false);
		}
		g.disableScissor();
		if (selected >= 0 && selected < shelf.items.size()) {
			String[] text = labels(shelf.items.get(selected));
			g.drawCenteredString(font, Ui.visual(Ui.clip(font, text[0], viewW - 20)), px + pw / 2, viewY + viewH - TEXT_H + 2, Ui.TEXT);
			g.drawCenteredString(font, Ui.visual(Ui.clip(font, text[1], viewW - 20)), px + pw / 2, viewY + viewH - TEXT_H + 12, Ui.DIM);
		}
		float max = maxScroll(shelf);
		if (max > 0) {
			int barW = Math.max(16, viewW * viewW / (viewW + Math.round(max)));
			int barX = viewX + Math.round((viewW - barW) * (shelf.scroll / max));
			Ui.rect(g, barX, viewY + viewH - 3, barW, 2, 1, Ui.TRACK);
		}
	}

	/** Items worth drawing around the glide position, the nearest to the middle last so they end up on top. */
	private List<Integer> carouselOrder(Shelf shelf) {
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < shelf.items.size(); i++) {
			if (LibraryViews.alpha(i - carPos) > 0) {
				order.add(i);
			}
		}
		order.sort((a, b) -> Float.compare(Math.abs(b - carPos), Math.abs(a - carPos)));
		return order;
	}

	private int carTop(int size) {
		return viewY + Math.max(2, (viewH - TEXT_H - size) / 2);
	}

	/** One big cover in the middle with dimmer, smaller, narrower neighbours on each side. */
	private void renderCarousel(GuiGraphics g, Shelf shelf, Track now, boolean moving) {
		int size = carSize();
		int y = carTop(size);
		g.enableScissor(viewX - 4, viewY, viewX + viewW + 4, viewY + viewH);
		for (int i : carouselOrder(shelf)) {
			float o = i - carPos;
			float t = Mth.clamp((clock - shelf.born.get(i)) / POP, 0, 1);
			if (t <= 0) {
				continue;
			}
			int x = Math.round(px + pw / 2f + LibraryViews.spread(o, size) - size / 2f);
			boolean middle = i == selected;
			Object entry = shelf.items.get(i);
			boolean current = entry instanceof LibraryItem item && LibraryGrid.isPlaying(item, now);
			renderCard(g, entry, x, y, size, LibraryViews.squash(o), false, LibraryViews.scale(o) * LibraryGrid.popIn(t),
					LibraryViews.alpha(o) * t, middle ? 1 : 0, middle, current, moving);
		}
		if (shelf.pending != null) {
			g.drawCenteredString(font, Ui.visual(Tr.t("Loading") + ".".repeat(1 + (int) (clock * 3) % 3)), px + pw / 2,
					viewY + viewH - 10, Ui.DIM);
		}
		g.disableScissor();
		if (selected >= 0 && selected < shelf.items.size()) {
			String[] text = labels(shelf.items.get(selected));
			int ty = y + size + 6;
			g.drawCenteredString(font, Ui.visual(Ui.clip(font, text[0], viewW - 20)), px + pw / 2, ty, Ui.TEXT);
			g.drawCenteredString(font, Ui.visual(Ui.clip(font, text[1], viewW - 20)), px + pw / 2, ty + 10, Ui.DIM);
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

	private void renderCard(GuiGraphics g, Object entry, int x, int y, float scale, float alpha, float lift, boolean chosen,
			boolean current, boolean moving) {
		renderCard(g, entry, x, y, cellW, 1, true, scale, alpha, lift, chosen, current, moving);
	}

	/** Title and second line of a card, as {title, under}. */
	private static String[] labels(Object entry) {
		if (entry instanceof LibraryItem item) {
			return new String[] {item.title(), item.artists()};
		}
		LibraryPlaylist list = (LibraryPlaylist) entry;
		return new String[] {list.name(), list.songs() == 1 ? Tr.t("1 song") : Tr.t("%s songs", list.songs())};
	}

	/** Draws one cover of the given size; xSquash below 1 narrows it to fake a turned cover. */
	private void renderCard(GuiGraphics g, Object entry, int x, int y, int size, float xSquash, boolean withLabels, float scale,
			float alpha, float lift, boolean chosen, boolean current, boolean moving) {
		String title;
		String under;
		String colorKey;
		String coverUrl;
		if (entry instanceof LibraryItem item) {
			title = item.title();
			under = item.artists();
			colorKey = item.album().isEmpty() ? item.title() : item.album();
			coverUrl = item.coverUrl();
		} else {
			LibraryPlaylist list = (LibraryPlaylist) entry;
			title = list.name();
			under = list.songs() == 1 ? Tr.t("1 song") : Tr.t("%s songs", list.songs());
			colorKey = list.name();
			coverUrl = list.coverUrl();
		}
		CoverCache.Cover cover = mod.covers().get(coverUrl);
		float shown = cover == null ? 0 : cover.shown(System.nanoTime());
		int rgb = Mth.hsvToRgb(LibraryGrid.hue(colorKey), 0.55f, 0.8f);
		int glow = cover == null ? rgb : cover.color();

		g.pose().pushMatrix();
		g.pose().translate(x + size / 2f, y + size / 2f);
		g.pose().scale(scale * xSquash, scale);
		g.pose().translate(-(x + size / 2f), -(y + size / 2f));
		if (lift > 0.01f) {
			Ui.rect(g, x - 2, y - 2, size + 4, size + 4, 7, Ui.alpha(0xFF000000 | glow, 0.35f * lift * alpha));
		}
		if (shown < 1) {
			// Coloured stand-in until the real cover arrives, then the cover fades in over it
			Ui.rect(g, x, y, size, size, 5, Ui.alpha(0xFF000000 | rgb, alpha));
			Ui.rect(g, x, y + size / 2, size, size / 2, 5, Ui.alpha(0x30000000, alpha));
			String initial = title.isEmpty() ? "♪" : title.substring(0, title.offsetByCodePoints(0, 1)).toUpperCase();
			g.pose().pushMatrix();
			g.pose().translate(x + size / 2f, y + size / 2f - 7);
			g.pose().scale(2, 2);
			g.drawCenteredString(font, initial, 0, 0, Ui.alpha(0xFFFFFFFF, 0.85f * alpha));
			g.pose().popMatrix();
		}
		if (cover != null) {
			int s = CoverCache.SIZE;
			g.blit(RenderPipelines.GUI_TEXTURED, cover.id(), x, y, 0, 0, size, size, s, s, s, s, ARGB.white(alpha * shown));
		}
		if (current) {
			// Little bars in the corner of the song playing now, still while it's paused
			int bw = 16, bh = 12, bx = x + size - bw - 3, by = y + size - bh - 3;
			Ui.rect(g, bx, by, bw, bh, 4, Ui.alpha(Ui.ACCENT, 0.95f * alpha));
			for (int b = 0; b < 3; b++) {
				int barH = Math.max(2, Math.round(7 * (moving ? LibraryGrid.bar(b, clock) : 0.35f)));
				Ui.rect(g, bx + 3 + b * 4, by + bh - 2 - barH, 2, barH, 0, Ui.alpha(0xFFFFFFFF, alpha));
			}
		}
		if (chosen) {
			Ui.outline(g, x - 1, y - 1, size + 2, size + 2, 6, Ui.alpha(Ui.ACCENT, alpha));
		}
		if (withLabels) {
			g.drawString(font, Ui.visual(Ui.clip(font, title, size)), x, y + size + 3, Ui.alpha(current ? Ui.ACCENT : Ui.TEXT, alpha), false);
			g.drawString(font, Ui.visual(Ui.clip(font, under, size)), x, y + size + 12, Ui.alpha(Ui.DIM, alpha), false);
		}
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
		for (int i = 0; i < 3; i++) {
			if (Ui.inside(mx, my, toggleX(i), y, TOGGLE_W, 14)) {
				SkyLyricsConfig cfg = SkyLyricsConfig.get();
				cfg.libraryView = SkyLyricsConfig.LibraryView.values()[i];
				cfg.save();
				typing = false;
				Shelf current = shelf();
				if (current != null) {
					current.scrollTarget = 0;
					current.scroll = 0;
					carPos = Math.max(0, selected);
					reveal(current);
				}
				return true;
			}
		}
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
			if (view() == SkyLyricsConfig.LibraryView.SHELF) {
				int rowH = SHELF_COVER + SHELF_FLOOR;
				int rtop = shelfTop();
				int left = Math.round(shelf.scroll);
				int c = (int) Math.floor((mx - viewX + left) / shelfStride);
				int r = (int) Math.floor((my - rtop) / rowH);
				int hit = LibraryViews.shelfIndex(c, r, shelfRows, shelf.items.size());
				if (hit >= 0 && Ui.inside(mx, my, viewX + c * shelfStride - left, rtop + r * rowH, SHELF_COVER, SHELF_COVER)) {
					selected = hit;
					activate(shelf, hit);
				}
				return true;
			}
			if (view() == SkyLyricsConfig.LibraryView.CAROUSEL) {
				int size = carSize();
				int cy = carTop(size);
				List<Integer> order = carouselOrder(shelf);
				for (int k = order.size() - 1; k >= 0; k--) {
					int i = order.get(k);
					float o = i - carPos;
					int w = Math.round(size * LibraryViews.scale(o) * LibraryViews.squash(o));
					int h = Math.round(size * LibraryViews.scale(o));
					int cx = Math.round(px + pw / 2f + LibraryViews.spread(o, size));
					if (Ui.inside(mx, my, cx - w / 2, cy + (size - h) / 2, w, h)) {
						if (i == selected) {
							activate(shelf, i);
						} else {
							selected = i;
						}
						break;
					}
				}
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
			//? if >=26.2 {
			/*minecraft.gui.setScreen(settings);
			*///?} else {
			minecraft.setScreen(settings);
			//?}
			return;
		}
		SkyLyricsScreen settings = new SkyLyricsScreen(this);
		settings.selectTab(2);
		//? if >=26.2 {
		/*minecraft.gui.setScreen(settings);
		*///?} else {
		minecraft.setScreen(settings);
		//?}
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		Shelf shelf = shelf();
		if (shelf != null && view() == SkyLyricsConfig.LibraryView.CAROUSEL) {
			if (!shelf.items.isEmpty() && scrollY != 0) {
				selected = Mth.clamp(Math.max(0, selected) + (scrollY > 0 ? -1 : 1), 0, shelf.items.size() - 1);
			}
			return true;
		}
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
			if (event.key() == InputConstants.KEY_BACKSPACE && !query.isEmpty()) {
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
		SkyLyricsConfig.LibraryView mode = view();
		int rowStep = mode == SkyLyricsConfig.LibraryView.GRID ? cols : 1;
		int step = event.isLeft() ? -1 : event.isRight() ? 1 : event.isUp() ? -rowStep : event.isDown() ? rowStep : 0;
		if (step != 0 && !(typing && (event.isLeft() || event.isRight()))) {
			typing = false;
			if (mode == SkyLyricsConfig.LibraryView.SHELF) {
				int dCol = event.isLeft() ? -1 : event.isRight() ? 1 : 0;
				int dRow = event.isUp() ? -1 : event.isDown() ? 1 : 0;
				selected = LibraryViews.shelfMove(selected, dCol, dRow, shelfRows, shelf.items.size());
			} else {
				selected = LibraryGrid.move(selected, step, shelf.items.size());
			}
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
		if (view() == SkyLyricsConfig.LibraryView.SHELF) {
			int x = LibraryViews.shelfCol(selected, shelfRows) * shelfStride;
			shelf.scrollTarget = Mth.clamp(LibraryViews.reveal(shelf.scrollTarget, x, SHELF_COVER, viewW), 0, maxScroll(shelf));
			return;
		}
		if (view() == SkyLyricsConfig.LibraryView.CAROUSEL) {
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
		//? if >=26.2 {
		/*minecraft.gui.setScreen(parent);
		*///?} else {
		minecraft.setScreen(parent);
		//?}
	}

	@Override
	public void removed() {
		lastSection = section;
	}
}
