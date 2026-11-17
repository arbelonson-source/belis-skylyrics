package dev.beli.skylyrics.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.IntFunction;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
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
import dev.beli.skylyrics.media.HeardLibrary;
import dev.beli.skylyrics.media.Library;
import dev.beli.skylyrics.media.LibraryItem;
import dev.beli.skylyrics.media.Track;
import dev.beli.skylyrics.media.LibraryPlaylist;
import dev.beli.skylyrics.media.SpotifyLibrary;
import dev.beli.skylyrics.media.SpotifyLibrary.Page;
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
	/** A page that failed in the middle of a list is tried again after this many seconds. */
	private static final float RETRY_AFTER = 4;
	/** After this many failed loads in a row the list stops trying by itself (a click still tries again). */
	private static final int AUTO_RETRIES = 3;
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
	/** When a click last opened a playlist; its double-click's second click must not land on the songs that just appeared. */
	private long openedAtNs = Long.MIN_VALUE / 2;
	/** Set when a pressed Space already did something; the typed space that follows it must not start a search. */
	private boolean swallowSpace;

	private int selected = -1;
	/** Which not-signed-in button the keyboard is on: -1 none, 0 Open Music tab, 1 Try made-up songs. */
	private int signInFocus = -1;
	/** The playlist's place in its list while it is open, so closing it puts the keyboard back there; -1 if none. */
	private int openedFrom = -1;
	private @Nullable LibraryPlaylist openedList;
	/** The last input was a key, not a click. */
	private boolean keyboardLast;
	/** A recent search was just picked, so the Narrator says how many results it found; any later key or click ends that. */
	private boolean sayResults;
	/** The section just changed, so the Narrator names it once; any later key or click ends that. */
	private boolean sayNewSection;
	/** Which header stop the keyboard is on (see {@link #stops}), or -1 when it is on the songs or the search box. */
	private int stop = -1;
	/** What the empty list or the search results said last, so the Narrator is told when that changes. */
	private String lastStatus = "";
	private boolean closed;
	private @Nullable CompletableFuture<String> playing;
	private String playingTitle = "";
	private String toast = "";
	private float toastAge = TOAST_TIME;

	private float clock;
	private long lastFrame;
	private final ClickRescue rescue = new ClickRescue();
	private final ClickRescue.Target rescueTarget = new ClickRescue.Target() {
		@Override
		public boolean click(MouseButtonEvent event) {
			return mouseClicked(event, false);
		}

		@Override
		public boolean drag(MouseButtonEvent event, double dx, double dy) {
			return false;
		}

		@Override
		public boolean release(MouseButtonEvent event) {
			return false;
		}
	};
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
		super(Component.literal(Tr.t("Song library")));
		this.parent = parent;
		KeyMapping open = mod.libraryKey();
		closeKeyHeld = open != null && open.isDown();
	}

	/** The library key was still down when this opened (it opened from that key): its repeats must not close it again. */
	private boolean closeKeyHeld;

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean keyReleased(KeyEvent event) {
		KeyMapping open = mod.libraryKey();
		if (open != null && open.matches(event)) {
			closeKeyHeld = false;
		}
		return false;
	}

	//? if <1.21.9 {
	/*@Override
	public boolean keyReleased(int key, int scancode, int modifiers) {
		return keyReleased(new KeyEvent(key, scancode, modifiers));
	}

	*///?}

	/**
	 * Whether "Try made-up songs" still holds when the library opens again: until Spotify can be browsed. The Demo song
	 * setting is read on its own each time, so turning it off brings the real library back.
	 */
	static boolean stillTrying(boolean tried, boolean canBrowse) {
		return tried && !canBrowse;
	}

	@Override
	protected void init() {
		// Runs again after coming back from the settings, so signing in there takes effect right away
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		demo = stillTrying(demo, mod.spotify().canBrowse());
		boolean wantDemo = demo || cfg.demoMode;
		// Without Spotify the library shows the songs heard lately, unless that is switched off
		Library next = wantDemo ? new DemoLibrary() : mod.spotify().canBrowse() ? mod.library()
				: cfg.recentSongs ? mod.heard() : null;
		boolean same = next instanceof DemoLibrary ? library instanceof DemoLibrary : next == library;
		if (!same && (next != null || library != null)) {
			library = next;
			shelves.values().forEach(Shelf::cancel);
			shelves.clear();
			closeOpened();
		}
		if (library instanceof HeardLibrary && section == Section.PLAYLISTS) {
			section = Section.LIKED;
		}
	}

	/** A list being loaded page by page as you scroll. */
	private final class Shelf {
		final IntFunction<CompletableFuture<? extends Page<?>>> loader;
		/** Liked songs and Recently heard shift while you scroll, so a song can come round again on a later page. */
		final boolean noRepeats;
		final List<Object> items = new ArrayList<>();
		final List<Float> born = new ArrayList<>();
		final List<Float> lift = new ArrayList<>();
		@Nullable CompletableFuture<? extends Page<?>> pending;
		int next;
		boolean loaded;
		String error = "";
		float retryAt;
		/** Failed loads in a row. */
		int failures;
		float scroll;
		float scrollTarget;

		Shelf(IntFunction<CompletableFuture<? extends Page<?>>> loader) {
			this(loader, false);
		}

		Shelf(IntFunction<CompletableFuture<? extends Page<?>>> loader, boolean noRepeats) {
			this.loader = loader;
			this.noRepeats = noRepeats;
		}

		void more() {
			if (pending == null && next >= 0) {
				error = "";
				pending = loader.apply(next);
			}
		}

		/** The list is being thrown away: a load that hasn't started yet never will. */
		void cancel() {
			if (pending != null) {
				pending.cancel(true);
				pending = null;
			}
		}

		void poll() {
			if (pending == null || !pending.isDone()) {
				return;
			}
			try {
				Page<?> page = pending.join();
				List<?> fresh = noRepeats ? SpotifyLibrary.withoutRepeats(items, page.items()) : page.items();
				for (int i = 0; i < fresh.size(); i++) {
					items.add(fresh.get(i));
					born.add(LibraryGrid.revealAt(clock, i, STAGGER));
					lift.add(0f);
				}
				next = page.next();
				failures = 0;
			} catch (CompletionException e) {
				error = message(e.getCause());
				retryAt = clock + RETRY_AFTER;
				failures++;
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
			}, s == Section.LIKED);
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
		return SpotifyLibrary.errorText(error);
	}

	private void layout() {
		pw = Math.min(460, width - 16);
		ph = Math.min(300, height - 16);
		px = (width - pw) / 2;
		py = (height - ph) / 2 + Math.round((1 - openAnim) * 8);
		viewX = px + 12;
		viewW = pw - 24;
		viewY = py + HEADER_H + 6 + bannerH();
		viewH = ph - HEADER_H - 12 - bannerH();
		cols = LibraryGrid.columns(viewW);
		cellW = LibraryGrid.cellWidth(viewW);
		cellH = cellW + TEXT_H;
		shelfRows = LibraryViews.shelfRows(viewH - TEXT_H - 4, SHELF_COVER + SHELF_FLOOR);
		shelfStride = SHELF_COVER + LibraryGrid.GAP;
	}

	private boolean heardMode() {
		return library instanceof HeardLibrary;
	}

	/** Room for the "sign in to Spotify" strip above the recently heard songs. */
	private int bannerH() {
		return heardMode() ? bannerStripH() + 3 : 0;
	}

	/** Height of the sign-in strip: 16 for one line of text, 26 when the text has to wrap onto two. */
	private int bannerStripH() {
		return banner().height();
	}

	private int bannerRoom() {
		return banner().room();
	}

	private int bannerButtonW() {
		return banner().buttonW();
	}

	/** What the strip's size and text depend on: a language switch or resource reload swaps the Language, which also changes the widths. */
	private record BannerKey(Object account, int screenW, Object font, Object language) {}

	/** The strip as measured: its height, button width, text room and the one or two lines to draw. */
	private record Banner(BannerKey key, int height, int buttonW, int room, net.minecraft.util.FormattedCharSequence[] lines) {}

	private @Nullable Banner banner;

	/** The strip's layout, measured again only when its key changes: drawing, clicks and the focus outline all read this one. */
	private Banner banner() {
		BannerKey key = new BannerKey(mod.spotify().account(), width, font, net.minecraft.locale.Language.getInstance());
		Banner b = banner;
		if (b != null && b.key().equals(key)) {
			return b;
		}
		int bw = font.width(Tr.t("Open Music tab")) + 14;
		int room = Math.min(460, width - 16) - 20 - bw - 24;
		String text = signInText();
		int sh = LibraryViews.bannerStripH(font.width(text), room);
		net.minecraft.util.FormattedCharSequence[] lines;
		if (sh == LibraryViews.BANNER_ONE) {
			lines = new net.minecraft.util.FormattedCharSequence[] {Ui.visual(Ui.clip(font, text, room))};
		} else {
			List<net.minecraft.network.chat.FormattedText> split = font.getSplitter().splitLines(text, room, net.minecraft.network.chat.Style.EMPTY);
			boolean rtl = Ui.paragraphRtl(text);
			lines = new net.minecraft.util.FormattedCharSequence[Math.min(2, split.size())];
			for (int i = 0; i < lines.length; i++) {
				String line = split.get(i).getString();
				// a third line cannot fit: the second keeps its end clipped rather than losing the words silently
				if (i == 1 && split.size() > 2) {
					line = Ui.clip(font, line + " " + split.get(2).getString(), room);
				}
				lines[i] = Ui.visual(line, rtl);
			}
		}
		banner = b = new Banner(key, sh, bw, room, lines);
		return b;
	}

	private Section[] sections() {
		return heardMode() ? new Section[] {Section.SEARCH, Section.LIKED} : Section.values();
	}

	private SkyLyricsConfig.LibraryView view() {
		return SkyLyricsConfig.get().libraryView;
	}

	private int carSize() {
		return Math.max(40, Math.min(Math.min(viewH - TEXT_H - 16, viewW / 3), 130));
	}

	private int contentHeight(Shelf shelf) {
		int rows = (shelf.items.size() + cols - 1) / cols;
		return Math.max(0, rows * (cellH + LibraryGrid.GAP) - LibraryGrid.GAP) + 2 * GRID_PAD + (shelf.pending != null ? 16 : 0);
	}

	/** Room above and below the grid, so a hovered card's glow isn't cut off at the edge. */
	private static final int GRID_PAD = 3;

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
		//? if <1.21.6 {
		/*// up to 1.21.5 the background is drawn from render() itself (which this screen replaces); 1.21.6+ draws it before calling render()
		renderBackground(g, mouseX, mouseY, partialTick);
		*///?}
		rescue.poll(mouseX, mouseY, rescueTarget);
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		clock += dt;
		openAnim = Ui.approach(openAnim, 1, dt, 12);
		toastAge += dt;
		if (searchDirty && now - typedAt >= SEARCH_WAIT_NS) {
			searchDirty = false;
			Shelf old = shelves.remove(Section.SEARCH);
			if (old != null) {
				old.cancel();
			}
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
			String status = statusWords(shelf);
			if (!status.equals(lastStatus)) {
				lastStatus = status;
				afterKeyboardAction(); // loading finished, nothing found, how many results: said when it changes
			}
			shelf.scrollTarget = Mth.clamp(shelf.scrollTarget, 0, maxScroll(shelf));
			shelf.scroll = Ui.approach(shelf.scroll, shelf.scrollTarget, dt, 18);
			boolean nearEnd = switch (view()) {
				case SHELF -> shelf.scrollTarget + shelfStride * 2 > maxScroll(shelf);
				case CAROUSEL -> selected >= shelf.items.size() - 6;
				default -> shelf.scrollTarget + viewH > contentHeight(shelf) - cellH;
			};
			if (nearEnd && (shelf.error.isEmpty() || !shelf.items.isEmpty() && clock >= shelf.retryAt && shelf.failures < AUTO_RETRIES)) {
				shelf.more();
			}
			// Not while a search is being typed: the old results are still up, and Enter must not play one of them
			if (view() == SkyLyricsConfig.LibraryView.CAROUSEL && selected < 0 && !shelf.items.isEmpty() && !searchDirty) {
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
		int textRoom = Ui.headerRoom(px + 31, library != null ? searchX() : px + pw - 22);
		g.drawString(font, Ui.visual(Ui.clip(font, Tr.t("Song library"), textRoom)), px + 31, py + 8, Ui.TEXT, false);
		String subtitle = library == null ? Tr.t("Your Spotify songs")
				: library instanceof DemoLibrary ? Tr.t("Made-up songs, for trying it out")
				: heardMode() ? Tr.t("Songs you've played lately") : Tr.t("From your Spotify");
		g.drawString(font, Ui.visual(Ui.clip(font, subtitle, textRoom)), px + 31, py + 18, Ui.DIM, false);

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
			for (Section s : sections()) {
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
		if (heardMode()) {
			renderBanner(g, f);
		}
	}

	/** A slim strip telling the user their own Spotify songs are one sign-in away. */
	private void renderBanner(GuiGraphics g, Frame f) {
		Banner strip = banner();
		int y = py + HEADER_H + 3, bw = strip.buttonW(), sh = strip.height();
		Ui.rect(g, px + 10, y, pw - 20, sh, 5, Ui.CARD);
		for (int i = 0; i < strip.lines().length; i++) {
			g.drawString(font, strip.lines()[i], px + 16, y + (sh == LibraryViews.BANNER_ONE ? 4 : 3 + i * 10), Ui.MUTED, false);
		}
		int bx = px + pw - 10 - bw - 2, by = bannerButtonY(y, sh);
		Controls.drawButton(f, bx, by, bw, 14, Tr.t("Open Music tab"), Ui.inside(f.mx(), f.my(), bx, by, bw, 14), true, true);
		if (onStop(STOP_BANNER, 0)) {
			Ui.outline(g, bx - 2, by - 2, bw + 4, 18, 7, Ui.TEXT);
		}
	}

	private static int bannerButtonY(int stripY, int stripH) {
		return stripY + (stripH - 14) / 2;
	}

	private static final int STOP_BANNER = 0, STOP_VIEW = 1, STOP_CHIP = 2;

	/** What the keyboard can reach above the songs, in order: {kind, which}. */
	private List<int[]> stops() {
		List<int[]> out = new ArrayList<>();
		if (library == null) {
			return out;
		}
		if (heardMode()) {
			out.add(new int[] {STOP_BANNER, 0});
		}
		for (int i = 0; i < SkyLyricsConfig.LibraryView.values().length; i++) {
			out.add(new int[] {STOP_VIEW, i});
		}
		if (chipsShown()) {
			for (int i = 0; i < recentSearches().size(); i++) {
				out.add(new int[] {STOP_CHIP, i});
			}
		}
		return out;
	}

	/** The recent searches are on show: an empty search box in the Search section. */
	private boolean chipsShown() {
		return opened == null && section == Section.SEARCH && query.isBlank() && !recentSearches().isEmpty();
	}

	private boolean onStop(int kind, int which) {
		if (stop < 0) {
			return false;
		}
		List<int[]> all = stops();
		return stop < all.size() && all.get(stop)[0] == kind && all.get(stop)[1] == which;
	}

	/** Presses the header stop the keyboard is on, like clicking it. */
	private void pressStop(int[] at) {
		switch (at[0]) {
			case STOP_BANNER -> openMusicTab();
			case STOP_VIEW -> chooseView(at[1]);
			default -> {
				edit(recentSearches().get(at[1]));
				typedAt = 0;
				stop = -1;
				sayResults = true;
			}
		}
	}

	private void chooseView(int index) {
		SkyLyricsConfig cfg = SkyLyricsConfig.get();
		cfg.libraryView = SkyLyricsConfig.LibraryView.values()[index];
		cfg.save();
		typing = false;
		Shelf current = shelf();
		if (current != null) {
			current.scrollTarget = 0;
			current.scroll = 0;
			carPos = Math.max(0, selected);
			reveal(current);
		}
	}

	/** What the Narrator says for a header stop. */
	private String stopNarration(int[] at) {
		return switch (at[0]) {
			// the strip's own words first, so a screen reader says why the button is there
			case STOP_BANNER -> Narration.join(signInText(), Narration.button(Tr.t("Open Music tab"), Tr.t("button")));
			case STOP_VIEW -> {
				SkyLyricsConfig.LibraryView v = SkyLyricsConfig.LibraryView.values()[at[1]];
				String name = switch (v) {
					case GRID -> Tr.t("Grid");
					case SHELF -> Tr.t("Shelf");
					case CAROUSEL -> Tr.t("Carousel");
				};
				yield Narration.join(name, Tr.t("button"), v == view() ? Tr.t("selected") : "");
			}
			default -> Narration.join(Tr.t("Recent searches"), recentSearches().get(at[1]), Tr.t("button"));
		};
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
			if (onStop(STOP_VIEW, i)) {
				Ui.outline(g, x - 2, y - 2, TOGGLE_W + 4, 18, 7, Ui.TEXT);
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
		return dev.beli.skylyrics.util.TextCut.suffix(text, t -> font.width(t) <= maxW);
	}

	private int searchX() {
		return px + pw - 30 - SEARCH_W;
	}

	private String sectionName(Section s) {
		return switch (s) {
			case SEARCH -> Tr.t("Search");
			case LIKED -> heardMode() ? Tr.t("Recently heard") : Tr.t("Liked songs");
			case PLAYLISTS -> Tr.t("Playlists");
		};
	}

	private void renderShelf(GuiGraphics g, Frame f, Shelf shelf) {
		boolean searchView = opened == null && section == Section.SEARCH;
		if (searchView && searchDirty && !query.isBlank()) {
			// Still typing: the old results would be for a different search, so show it looking instead
			searching(g, Tr.t("Searching"));
			return;
		}
		if (shelf.items.isEmpty()) {
			if (shelf.error.isEmpty() && !shelf.loaded) {
				searching(g, searchView ? Tr.t("Searching") : Tr.t("Loading"));
				return;
			}
			if (searchView && query.isBlank() && !recentSearches().isEmpty()) {
				renderRecent(g, f);
				return;
			}
			String text = shelf.error.isEmpty() ? emptyText() : shelf.error;
			g.drawCenteredString(font, Ui.visual(Ui.clip(font, text, viewW - 20)), px + pw / 2, viewY + viewH / 2 - 10, Ui.MUTED);
			if (!shelf.error.isEmpty()) {
				int rw = Controls.buttonW(font.width(Tr.t("Try again")), 80);
				int bx = px + pw / 2 - rw / 2, by = viewY + viewH / 2 + 4;
				Controls.drawButton(f, bx, by, rw, 16, Tr.t("Try again"), Ui.inside(f.mx(), f.my(), bx, by, rw, 16), true, true);
				Ui.outline(f.g(), bx - 2, by - 2, rw + 4, 20, 6, Ui.TEXT);
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
		try {
			int top = viewY + GRID_PAD - Math.round(shelf.scroll);
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
				renderCard(g, entry, x, y, LibraryGrid.popIn(t), t, lift, i == selected, current, moving);
			}
			if (shelf.pending != null) {
				int y = top + contentHeight(shelf) - 12;
				g.drawCenteredString(font, Ui.visual(Tr.t("Loading") + ".".repeat(1 + (int) (clock * 3) % 3)), px + pw / 2, y, Ui.DIM);
			} else if (!shelf.error.isEmpty()) {
				g.drawCenteredString(font, Ui.visual(shelf.error), px + pw / 2, top + contentHeight(shelf) + 4, Ui.MUTED);
			}
		} finally {
			g.disableScissor();
		}

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
		try {
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
		} finally {
			g.disableScissor();
		}
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
		try {
			boolean first = true;
			for (int i : carouselOrder(shelf)) {
				float o = i - carPos;
				float t = Mth.clamp((clock - shelf.born.get(i)) / POP, 0, 1);
				if (t <= 0) {
					continue;
				}
				// Each card gets its own layer, or the letters of cards further back end up on top of nearer ones
				if (!first) {
					g.nextStratum();
				}
				first = false;
				int x = Math.round(px + pw / 2f + LibraryViews.spread(o, size) - size / 2f);
				float squash = LibraryViews.squash(o), scale = LibraryViews.scale(o) * LibraryGrid.popIn(t);
				// A solid panel-coloured backing so a dimmed card fades into the panel, not into the cards behind it
				g.pose().pushMatrix();
				try {
					g.pose().translate(x + size / 2f, y + size / 2f);
					g.pose().scale(scale * squash, scale);
					g.pose().translate(-(x + size / 2f), -(y + size / 2f));
					Ui.rect(g, x, y, size, size, 5, Ui.alpha(Ui.PANEL, t));
				} finally {
					g.pose().popMatrix();
				}
				boolean middle = i == selected;
				Object entry = shelf.items.get(i);
				boolean current = entry instanceof LibraryItem item && LibraryGrid.isPlaying(item, now);
				renderCard(g, entry, x, y, size, squash, false, scale, LibraryViews.alpha(o) * t, middle ? 1 : 0, middle, current, moving);
			}
			if (shelf.pending != null) {
				g.drawCenteredString(font, Ui.visual(Tr.t("Loading") + ".".repeat(1 + (int) (clock * 3) % 3)), px + pw / 2,
						viewY + viewH - 10, Ui.DIM);
			}
		} finally {
			g.disableScissor();
		}
		if (selected >= 0 && selected < shelf.items.size()) {
			String[] text = labels(shelf.items.get(selected));
			int ty = y + size + 6;
			g.drawCenteredString(font, Ui.visual(Ui.clip(font, text[0], viewW - 20)), px + pw / 2, ty, Ui.TEXT);
			g.drawCenteredString(font, Ui.visual(Ui.clip(font, text[1], viewW - 20)), px + pw / 2, ty + 10, Ui.DIM);
		}
	}

	/** A small equaliser of bouncing bars with a word under it, while songs are on their way. */
	private void searching(GuiGraphics g, String word) {
		int cx = px + pw / 2, base = viewY + viewH / 2 + 2;
		for (int i = 0; i < 5; i++) {
			float wave = (Mth.sin((float) (clock * 7 - i * 0.9)) + 1) / 2;
			int h = 4 + Math.round(wave * 14);
			int color = Ui.lerp(wave, Ui.ACCENT_SOFT | 0xFF000000 & 0xFF6D58A8, Ui.ACCENT);
			Ui.rect(g, cx - 22 + i * 9, base - h, 6, h, 2, color);
		}
		String dots = ".".repeat(1 + (int) (clock * 3) % 3);
		g.drawCenteredString(font, Ui.visual(word + dots), cx, base + 8, Ui.MUTED);
	}

	private static List<String> recentSearches() {
		return SkyLyricsConfig.get().recentSearches;
	}

	/** Remembers a search that led to a song being picked, newest first. */
	static List<String> remember(List<String> recent, String search) {
		List<String> out = new ArrayList<>();
		out.add(search);
		for (String old : recent) {
			if (out.size() >= RECENT && !old.equalsIgnoreCase(search)) {
				break;
			}
			if (!old.equalsIgnoreCase(search)) {
				out.add(old);
			}
		}
		return out;
	}

	static final int RECENT = SkyLyricsConfig.MAX_RECENT_SEARCHES;

	/** Where each recent search chip sits: x, y, width. */
	private List<int[]> recentChips() {
		List<int[]> out = new ArrayList<>();
		int x = viewX + 20, y = viewY + 34, right = viewX + viewW - 20;
		for (String q : recentSearches()) {
			int w = Math.min(font.width(Ui.clip(font, q, 160)) + 16, right - viewX - 20);
			if (x + w > right && x > viewX + 20) {
				x = viewX + 20;
				y += 22;
			}
			out.add(new int[] {x, y, w});
			x += w + 6;
		}
		return out;
	}

	private void renderRecent(GuiGraphics g, Frame f) {
		g.drawString(font, Ui.visual(Tr.t("Recent searches")), viewX + 20, viewY + 16, Ui.MUTED, false);
		List<String> recent = recentSearches();
		List<int[]> chips = recentChips();
		for (int i = 0; i < chips.size(); i++) {
			int[] c = chips.get(i);
			boolean hover = Ui.inside(f.mx(), f.my(), c[0], c[1], c[2], 16);
			Ui.rect(g, c[0], c[1], c[2], 16, 8, hover ? Ui.CONTROL_HOVER : Ui.CONTROL);
			g.drawCenteredString(font, Ui.visual(Ui.clip(font, recent.get(i), c[2] - 12)), c[0] + c[2] / 2, c[1] + 4, Ui.TEXT);
			if (onStop(STOP_CHIP, i)) {
				Ui.outline(g, c[0] - 2, c[1] - 2, c[2] + 4, 20, 10, Ui.TEXT);
			}
		}
	}

	private String emptyText() {
		if (opened != null) {
			return Tr.t("This playlist is empty");
		}
		return switch (section) {
			case SEARCH -> query.isBlank() ? Tr.t("Type to find a song") : Tr.t("Nothing found for “%s”", query.strip());
			case LIKED -> heardMode() ? Tr.t("Songs you play will show up here") : Tr.t("No liked songs yet");
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
		try {
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
				String initial = dev.beli.skylyrics.util.TextCut.first(title).toUpperCase(Locale.ROOT);
				if (initial.isEmpty()) {
					initial = "♪";
				}
				g.pose().pushMatrix();
				try {
					g.pose().translate(x + size / 2f, y + size / 2f - 7);
					g.pose().scale(2, 2);
					g.drawCenteredString(font, initial, 0, 0, Ui.alpha(0xFFFFFFFF, 0.85f * alpha));
				} finally {
					g.pose().popMatrix();
				}
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
		} finally {
			g.pose().popMatrix();
		}
	}

	private String signInText() {
		return switch (mod.spotify().account()) {
			case SIGNED_IN -> Tr.t("Sign in to Spotify again so SkyLyrics can see your songs");
			default -> Tr.t("Sign in to Spotify on the Music tab to see your songs here");
		};
	}

	/** The sign-in card's buttons (see {@link Controls#signInLayout}) for a card at x, y and width w. */
	private int[] signInButtons(int x, int y, int w) {
		int w0 = Controls.buttonW(font.width(Tr.t("Open Music tab")), 100);
		int w1 = Controls.buttonW(font.width(Tr.t("Try made-up songs")), 100);
		return Controls.signInLayout(x + w / 2, y + 50, w0, w1, w);
	}

	private void renderSignIn(GuiGraphics g, Frame f) {
		String text = signInText();
		int w = Math.min(300, pw - 40), x = px + (pw - w) / 2, y = viewY + viewH / 2 - 44;
		int[] b = signInButtons(x, y, w);
		Ui.rect(g, x, y, w, b[6], 8, Ui.CARD);
		List<net.minecraft.network.chat.FormattedText> lines = font.getSplitter().splitLines(text, w - 24, net.minecraft.network.chat.Style.EMPTY);
		boolean rtl = Ui.paragraphRtl(text);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			g.drawCenteredString(font, Ui.visual(lines.get(i).getString(), rtl), x + w / 2, y + 14 + i * 11, Ui.TEXT);
		}
		Controls.drawButton(f, b[0], b[1], b[2], 18, Tr.t("Open Music tab"), Ui.inside(f.mx(), f.my(), b[0], b[1], b[2], 18), true, true);
		Controls.drawButton(f, b[3], b[4], b[5], 18, Tr.t("Try made-up songs"), Ui.inside(f.mx(), f.my(), b[3], b[4], b[5], 18), false, true);
		if (signInFocus >= 0) {
			int k = signInFocus * 3;
			Ui.outline(g, b[k] - 2, b[k + 1] - 2, b[k + 2] + 4, 22, 6, Ui.TEXT);
		}
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
		afterKeyboardAction(); // the Narrator reads it while it shows
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
		if (library == null || index < 0 || index >= shelf.items.size() || staleResults()) {
			return;
		}
		if (clock <= shelf.born.get(index)) {
			// Not on screen yet, so not clickable either
			return;
		}
		selected = index;
		Object entry = shelf.items.get(index);
		if (entry instanceof LibraryPlaylist list) {
			if (!list.openable()) {
				// Spotify won't list the songs of someone else's playlist, but it will still play it
				if (playing == null) {
					playingTitle = list.name();
					say(Tr.t("Spotify keeps this playlist's songs hidden, so it plays from the top"));
					playing = library.playPlaylist(list);
				}
				return;
			}
			Library lib = library;
			Shelf songs = new Shelf(offset -> lib.playlist(list, offset));
			songs.more();
			opened = songs;
			openedAtNs = System.nanoTime();
			openedName = list.name();
			openedFrom = index;
			openedList = list;
			selected = -1;
			sayNewSection = true;
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
		if (opened == null && section == Section.SEARCH && !query.isBlank()) {
			SkyLyricsConfig cfg = SkyLyricsConfig.get();
			cfg.recentSearches = remember(cfg.recentSearches, query.strip());
			cfg.save();
		}
		playingTitle = item.title();
		say(Tr.t("Starting %s…", item.title()));
		toastAge = 0;
		playing = library.play(item, after);
	}

	/** After a playlist closes: scroll back to where it was, and put the keyboard on it only if the keyboard was used last. */
	private void restorePlace() {
		Shelf back = shelf();
		int from = openedFrom;
		if (back != null && openedList != null && !(from >= 0 && from < back.items.size() && back.items.get(from) == openedList)) {
			// The list changed while the playlist was open: find it again
			int found = back.items.indexOf(openedList);
			from = found >= 0 ? found : from;
		}
		openedList = null;
		int at = LibraryKeys.afterClose(from, back == null ? 0 : back.items.size());
		openedFrom = -1;
		selected = -1;
		if (back != null && at >= 0) {
			selected = at;
			reveal(back);
			selected = keyboardLast || view() == SkyLyricsConfig.LibraryView.CAROUSEL ? at : -1;
		}
	}

	private void closeOpened() {
		if (opened != null) {
			opened.cancel();
			opened = null;
		}
	}

	/** The search box has changed and the shelf still shows the answer to an older search. */
	private boolean staleResults() {
		return LibraryKeys.staleResults(opened == null && section == Section.SEARCH, searchDirty);
	}

	private void showSection(Section s) {
		closeOpened();
		section = s;
		selected = -1;
		openedFrom = -1;
		stop = -1;
		sayNewSection = true;
		if (s == Section.SEARCH) {
			typing = true;
		}
	}

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		keyboardLast = false;
		sayNewSection = false;
		sayResults = false;
		stop = -1;
		if (event.button() == ClickRescue.left() && !rescue.pressed()) {
			return true;
		}
		double mx = event.x();
		double my = event.y();
		if (event.button() != ClickRescue.left()) {
			return super.mouseClicked(event, doubleClick);
		}
		if (Ui.inside(mx, my, px + pw - 22, py + 8, 14, 14)) {
			onClose();
			return true;
		}
		if (library == null) {
			int w = Math.min(300, pw - 40), x = px + (pw - w) / 2;
			int[] b = signInButtons(x, viewY + viewH / 2 - 44, w);
			if (Ui.inside(mx, my, b[0], b[1], b[2], 18)) {
				pressSignIn(0);
			} else if (Ui.inside(mx, my, b[3], b[4], b[5], 18)) {
				pressSignIn(1);
			}
			return true;
		}
		if (heardMode() && Ui.inside(mx, my, px + pw - 12 - bannerButtonW(), bannerButtonY(py + HEADER_H + 3, bannerStripH()), bannerButtonW(), 14)) {
			openMusicTab();
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
				chooseView(i);
				return true;
			}
		}
		if (opened != null) {
			if (Ui.inside(mx, my, px + 10, y, font.width("‹ " + Tr.t("Playlists")) + 14, 14)) {
				closeOpened();
				restorePlace();
				sayNewSection = true;
			}
		} else {
			int x = px + 10;
			for (Section s : sections()) {
				int w = font.width(sectionName(s)) + 14;
				if (Ui.inside(mx, my, x, y, w, 14)) {
					showSection(s);
					return true;
				}
				x += w + 4;
			}
		}
		if (opened == null && section == Section.SEARCH && query.isBlank()) {
			List<int[]> chips = recentChips();
			for (int i = 0; i < chips.size(); i++) {
				int[] c = chips.get(i);
				if (Ui.inside(mx, my, c[0], c[1], c[2], 16)) {
					edit(recentSearches().get(i));
					typedAt = 0;
					return true;
				}
			}
		}
		Shelf shelf = shelf();
		if (shelf != null && Ui.inside(mx, my, viewX, viewY, viewW, viewH)) {
			if (staleResults() || LibraryKeys.tooSoonAfterOpen(System.nanoTime() - openedAtNs)) {
				// Old results under a search being typed, or the second click of a double-click on a playlist
				return true;
			}
			if (shelf.items.isEmpty() && !shelf.error.isEmpty()) {
				shelf.more();
				return true;
			}
			if (!shelf.items.isEmpty() && !shelf.error.isEmpty() && view() == SkyLyricsConfig.LibraryView.GRID
					&& Math.abs(my - (viewY + GRID_PAD - Math.round(shelf.scroll) + contentHeight(shelf) + 8)) <= 8) {
				// The error line under the last card is a button too
				shelf.more();
				return true;
			}
			if (view() == SkyLyricsConfig.LibraryView.SHELF) {
				int hit = shelfHitAt(shelf, mx, my);
				if (hit >= 0) {
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
			int top = viewY + GRID_PAD - Math.round(shelf.scroll);
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

	/** The buttons of the not-signed-in state: 0 opens the Music tab, 1 tries the made-up songs. */
	private void pressSignIn(int button) {
		if (button == 0) {
			openMusicTab();
		} else {
			demo = true;
			init();
		}
	}

	private void openMusicTab() {
		if (parent instanceof SkyLyricsScreen settings) {
			settings.showSpotify();
			//? if >=26.2 {
			/*minecraft.gui.setScreen(settings);
			*///?} else {
			minecraft.setScreen(settings);
			//?}
			return;
		}
		SkyLyricsScreen settings = new SkyLyricsScreen(this);
		settings.showSpotify();
		//? if >=26.2 {
		/*minecraft.gui.setScreen(settings);
		*///?} else {
		minecraft.setScreen(settings);
		//?}
	}

	//? if <1.21.9 {
	/*@Override
	public boolean mouseClicked(double x, double y, int button) {
		return mouseClicked(new MouseButtonEvent(x, y, button), false);
	}

	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		return keyPressed(new KeyEvent(key, scancode, modifiers));
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		return charTyped(new CharacterEvent(c));
	}

	*///?}
	private int shelfHitAt(Shelf shelf, double mx, double my) {
		return LibraryViews.shelfHit(mx, my, viewX, shelfTop(), Math.round(shelf.scroll), shelfStride,
				SHELF_COVER + SHELF_FLOOR, SHELF_COVER, shelfRows, shelf.items.size());
	}

	/** Hovering a cover on the shelf chooses it, so its name shows before you click. */
	@Override
	public void mouseMoved(double mx, double my) {
		Shelf shelf = shelf();
		if (library != null && shelf != null && view() == SkyLyricsConfig.LibraryView.SHELF
				&& Ui.inside(mx, my, viewX, viewY, viewW, viewH)) {
			selected = LibraryKeys.afterHover(selected, shelfHitAt(shelf, mx, my));
		}
		super.mouseMoved(mx, my);
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

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean keyPressed(KeyEvent event) {
		keyboardLast = true;
		sayNewSection = false;
		sayResults = false;
		boolean used = handleKey(event);
		if (used && !closed) {
			afterKeyboardAction();
		}
		return used;
	}

	private boolean handleKey(KeyEvent event) {
		swallowSpace = false;
		if (event.isEscape()) {
			if (typing) {
				typing = false;
				sayNewSection = true;
			} else if (opened != null) {
				closeOpened();
				restorePlace();
				sayNewSection = true;
			} else {
				onClose();
			}
			return true;
		}
		KeyMapping key = mod.libraryKey();
		if (key != null && key.matches(event)) {
			// A letter here starts a search, and a key held since it opened us only repeats
			if (ScreenKeys.libraryKeyCloses(typing, library != null, event.key(), ScreenKeys.ctrlOrAlt(event.modifiers()), closeKeyHeld)) {
				onClose();
				return true;
			}
			if (closeKeyHeld) {
				return true;
			}
		}
		if (library == null) {
			int dir = event.isCycleFocus() ? (event.hasShiftDown() ? -1 : 1)
					: event.isRight() || event.isDown() ? 1 : event.isLeft() || event.isUp() ? -1 : 0;
			if (dir != 0) {
				signInFocus = LibraryKeys.nextButton(signInFocus, 2, dir);
				return true;
			}
			if (event.isConfirmation() || event.key() == InputConstants.KEY_SPACE) {
				pressSignIn(Math.max(0, signInFocus));
				return true;
			}
			return super.keyPressed(event);
		}
		if (typing) {
			if (event.key() == InputConstants.KEY_BACKSPACE && !query.isEmpty()) {
				edit(event.hasControlDownWithQuirk() ? "" : query.substring(0, query.offsetByCodePoints(query.length(), -1)));
				return true;
			}
			//? if >=26.3 {
			/*int letter = event.shortcutKey();
			*///?} else {
			int letter = event.key();
			//?}
			int copy = ScreenKeys.copyOrCut(letter, event.hasControlDownWithQuirk());
			if (copy >= 0) {
				if (!query.isEmpty()) {
					minecraft.keyboardHandler.setClipboard(query);
					if (copy == 1) {
						edit("");
					}
				}
				return true;
			}
		}
		if (event.isPaste()) {
			// Pasting anywhere starts a search, like typing does
			if (!typing) {
				showSection(Section.SEARCH);
			}
			edit(ScreenKeys.libraryPasted(query, minecraft.keyboardHandler.getClipboard()));
			return true;
		}
		if (event.isCycleFocus()) {
			Section[] all = sections();
			showSection(all[Math.floorMod(section.ordinal() + (event.hasShiftDown() ? -1 : 1), all.length)]);
			typing = section == Section.SEARCH;
			return true;
		}
		Shelf shelf = shelf();
		if (shelf == null) {
			return super.keyPressed(event);
		}
		SkyLyricsConfig.LibraryView mode = view();
		List<int[]> stops = stops();
		boolean arrow = event.isLeft() || event.isRight() || event.isUp() || event.isDown();
		if (stop >= stops.size()) {
			stop = -1;
		}
		boolean hasSongs = !shelf.items.isEmpty();
		if (stop >= 0) {
			if (event.isConfirmation() || event.key() == InputConstants.KEY_SPACE) {
				swallowSpace = event.key() == InputConstants.KEY_SPACE;
				pressStop(stops.get(stop));
				return true;
			}
			if (arrow) {
				// Down goes back to the songs; the other arrows walk along the stops, and past the last one go there too
				stop = event.isDown() && hasSongs ? -1
						: LibraryKeys.stopMove(stop, stops.size(), event.isLeft() || event.isUp() ? -1 : 1);
				if (stop < 0 && hasSongs && !staleResults()) {
					selected = Math.max(0, selected);
					reveal(shelf);
				}
				return true;
			}
		} else if (arrow && !stops.isEmpty() && !(typing && (event.isLeft() || event.isRight()))
				&& (event.isUp() ? LibraryKeys.atTop(mode, selected, cols, shelfRows) : !hasSongs)) {
			int firstChip = -1;
			for (int i = 0; i < stops.size() && firstChip < 0; i++) {
				if (stops.get(i)[0] == STOP_CHIP) {
					firstChip = i;
				}
			}
			int viewStop = (heardMode() ? 1 : 0) + mode.ordinal();
			typing = false;
			stop = LibraryKeys.enterStop(hasSongs, viewStop, firstChip);
			return true;
		}
		int rowStep = mode == SkyLyricsConfig.LibraryView.GRID ? cols : 1;
		int step = event.isLeft() ? -1 : event.isRight() ? 1 : event.isUp() ? -rowStep : event.isDown() ? rowStep : 0;
		if (step != 0 && !(typing && (event.isLeft() || event.isRight()))) {
			if (staleResults()) {
				return true;
			}
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
		if (shelf.items.isEmpty() && !shelf.error.isEmpty() && (event.isConfirmation() || !typing && event.key() == InputConstants.KEY_SPACE)) {
			shelf.more();
			swallowSpace = event.key() == InputConstants.KEY_SPACE;
			return true;
		}
		if ((event.isConfirmation() || event.key() == InputConstants.KEY_SPACE && LibraryKeys.spacePlays(typing, selected, shelf.items.size()))
				&& selected >= 0) {
			swallowSpace = event.key() == InputConstants.KEY_SPACE;
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

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean charTyped(CharacterEvent event) {
		sayNewSection = false;
		sayResults = false;
		boolean used = handleChar(event);
		if (used) {
			afterKeyboardAction();
		}
		return used;
	}

	private boolean handleChar(CharacterEvent event) {
		if (library == null || !event.isAllowedChatCharacter()) {
			return false;
		}
		if (!ScreenKeys.acceptsTypedChar(closeKeyHeld)) {
			return true;
		}
		Shelf now = shelf();
		if (event.codepoint() == ' ' && (swallowSpace || now != null && LibraryKeys.spacePlays(typing, selected, now.items.size()))) {
			// Space already played or opened the chosen card; don't also start a search with a space in it
			swallowSpace = false;
			return true;
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
		query = dev.beli.skylyrics.util.TextCut.limit(text, 80);
		selected = -1;
		typedAt = System.nanoTime();
		searchDirty = true;
	}

	/** What an empty list or the search is doing, in words: loading, nothing found, or how many results. */
	private String statusWords(Shelf shelf) {
		boolean searchView = opened == null && section == Section.SEARCH;
		boolean busy = shelf.pending != null || searchView && searchDirty && !query.isBlank();
		String loading = searchView ? Tr.t("Searching") : Tr.t("Loading");
		if (Narration.countsResults(typing, sayResults) && searchView) {
			if (!shelf.error.isEmpty()) {
				return shelf.error;
			}
			if (query.isBlank()) {
				return emptyText();
			}
			int n = shelf.items.size();
			return Narration.resultCount(true, busy || !shelf.loaded, n, loading, emptyText(), Tr.t("1 result"), Tr.t("%s results", n));
		}
		return Narration.shelfState(shelf.items.size(), shelf.loaded, busy, shelf.error, loading, emptyText());
	}

	/** What the Narrator says for what has the keyboard now: a header button, the search box, or the chosen card. */
	private String focusNarration() {
		if (library == null) {
			return Narration.signIn(signInText(), Tr.t("Open Music tab"), Tr.t("Try made-up songs"), signInFocus, Tr.t("button"));
		}
		List<int[]> all = stops();
		if (stop >= 0 && stop < all.size()) {
			return stopNarration(all.get(stop));
		}
		Shelf shelf = shelf();
		if (typing) {
			String box = Narration.searchBox(Tr.t("Search"), Tr.t("text box"), query);
			return shelf == null ? box : Narration.join(box, statusWords(shelf));
		}
		if (shelf == null) {
			return "";
		}
		Section[] places = sections();
		int placeAt = java.util.Arrays.asList(places).indexOf(section);
		String where = places.length > 1 && placeAt >= 0 ? Tr.t("%s of %s", placeAt + 1, places.length) : "";
		String name = Narration.section(sayNewSection, opened != null, openedName, Narration.join(sectionName(section), where));
		if (shelf.items.isEmpty() && !shelf.error.isEmpty()) {
			return Narration.join(Narration.card(name, shelf.error, "", "", ""), Tr.t("Try again"), Tr.t("button"));
		}
		if (selected >= 0 && selected < shelf.items.size()) {
			Object entry = shelf.items.get(selected);
			String[] text = labels(entry);
			boolean current = entry instanceof LibraryItem item && LibraryGrid.isPlaying(item, mod.tracker().track());
			String action = entry instanceof LibraryPlaylist list && list.openable() ? Tr.t("Press Enter to open") : Tr.t("button");
			String tile = Narration.tile(Narration.card(name, shelf.error, text[0], Narration.underPlaying(text[1], current, Tr.t("Now playing")),
					Tr.t("%s of %s", selected + 1, shelf.items.size())), action);
			return sayResults ? Narration.join(statusWords(shelf), tile) : tile;
		}
		return Narration.join(Narration.card(name, shelf.error, "", "", ""), statusWords(shelf));
	}

	/** The message in the toast while it is still showing, for the Narrator. */
	private String toastWords() {
		return toastAge < TOAST_TIME ? toast : "";
	}

	@Override
	protected void updateNarrationState(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getNarrationMessage());
		if (library != null) {
			// Vanilla's own hint talks about Tab and arrows moving over widgets, which this screen doesn't have
			output.add(NarratedElementType.USAGE, Component.literal(Tr.t(
					"Tab changes the section, arrow keys move, Enter plays or opens, Up from the top row reaches the view buttons and recent searches, Esc goes back, and typing searches")));
		}
		String now = focusNarration();
		if (!now.isEmpty()) {
			output.nest().add(NarratedElementType.TITLE, Component.literal(now));
		}
		String message = toastWords();
		if (!message.isEmpty()) {
			output.nest().nest().add(NarratedElementType.TITLE, Component.literal(message));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		closed = true;
		//? if >=26.2 {
		/*minecraft.gui.setScreen(parent);
		*///?} else {
		minecraft.setScreen(parent);
		//?}
	}

	@Override
	public void removed() {
		lastSection = section;
		// Page loads still waiting for their turn are not wanted any more
		shelves.values().forEach(Shelf::cancel);
		if (opened != null) {
			opened.cancel();
		}
	}
}
