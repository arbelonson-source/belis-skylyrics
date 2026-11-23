package dev.beli.skylyrics.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.DoubleSupplier;
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
	static final int SEARCH_W = 130;
	private static final int SHELF_COVER = 84;
	/** Space under a shelf row for the floor line. */
	private static final int SHELF_FLOOR = 12;
	private static final int TOGGLE_W = 18;
	/** The section tabs start this far in from the panel's left edge; each is its name plus this padding, this far from the next. */
	static final int TABS_LEFT = 10, TAB_PAD = 14, TAB_GAP = 4;
	private static final long SEARCH_WAIT_NS = 350_000_000L;
	private static final float STAGGER = 0.03f;
	private static final float POP = 0.35f;
	private static final float TOAST_TIME = 3;
	/** A page that failed in the middle of a list is tried again after this many seconds. */
	private static final float RETRY_AFTER = 4;
	/** After this many failed loads in a row the list stops trying by itself (a click, or Enter on the line under the songs, still tries again). */
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
	/** When "‹ Playlists" last closed a playlist; its double-click's second click must not land on the section tabs in its place. */
	private long closedAtNs = Long.MIN_VALUE / 2;
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
	/** The wheel scrolled the shelf since the last key: the cover under the mouse is the chosen one even while the mouse stays put. */
	private boolean wheelPick;
	/** Brings the card the keys picked back into view when the window is resized. */
	private final PickInView picks = new PickInView();
	/** A recent search was just picked, so the Narrator says how many results it found; any later key or click ends that. */
	private boolean sayResults;
	/** The section just changed, so the Narrator names it once; any later key or click ends that. */
	private boolean sayNewSection;
	/** Which header stop the keyboard is on (see {@link #stops}), or -1 when it is on the songs or the search box. */
	private int stop = -1;
	/**
	 * The keyboard is on the line under the songs that says why the next page failed (Enter tries again, as a click does),
	 * reached with the arrow past the last card; {@link #selected} is still the card it came from.
	 */
	private boolean footer;
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
	private final ScrollSteps carouselSteps = new ScrollSteps();
	private String tip = "";

	/** An error in the library closes it back to where it was opened from instead of ending the game (see {@link ScreenFault}). */
	private final ScreenFault fault = new ScreenFault("the song library", this::leaveAfterError);

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
		try {
			return onKeyUp(event);
		} catch (RuntimeException | LinkageError e) {
			return fault.failed("a key release", e);
		}
	}

	private boolean onKeyUp(KeyEvent event) {
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
		try {
			openLibrary();
		} catch (RuntimeException | LinkageError e) {
			fault.failed("opening", e);
		}
	}

	private void openLibrary() {
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
			// The chosen card and header button were places in the old list and header: the new list starts at its top
			selected = -1;
			stop = -1;
			footer = false;
		}
		if (library instanceof HeardLibrary && section == Section.PLAYLISTS) {
			section = Section.LIKED;
		}
	}

	/** A list being loaded page by page as you scroll. */
	static final class Shelf {
		final IntFunction<CompletableFuture<? extends Page<?>>> loader;
		/** Liked songs and Recently heard shift while you scroll, so a song can come round again on a later page. */
		final boolean noRepeats;
		/** The screen's clock, in seconds: when the covers of a page that came pop in, and when a failed one is tried again. */
		private final DoubleSupplier clock;
		final List<Object> items = new ArrayList<>();
		final List<Float> born = new ArrayList<>();
		final List<Float> lift = new ArrayList<>();
		@Nullable CompletableFuture<? extends Page<?>> pending;
		int next;
		/** How many entries the list has altogether, as the last page said. */
		int total;
		boolean loaded;
		String error = "";
		float retryAt;
		/** Failed loads in a row. */
		int failures;
		/** Entries Spotify sent that got no card: local files, podcast episodes, gaps. */
		int skipped;
		float scroll;
		float scrollTarget;

		Shelf(IntFunction<CompletableFuture<? extends Page<?>>> loader, DoubleSupplier clock) {
			this(loader, false, clock);
		}

		Shelf(IntFunction<CompletableFuture<? extends Page<?>>> loader, boolean noRepeats, DoubleSupplier clock) {
			this.loader = loader;
			this.noRepeats = noRepeats;
			this.clock = clock;
		}

		void more() {
			if (pending == null && next >= 0) {
				error = "";
				pending = loader.apply(next);
				// A page that is there at once (the made-up songs, Recently heard) goes in at once: the list is never said
				// to be loading, even by the Narrator's first words as the library opens, before anything is drawn
				poll();
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
			float clock = (float) this.clock.getAsDouble();
			try {
				Page<?> page = pending.join();
				List<?> fresh = noRepeats ? SpotifyLibrary.withoutRepeats(items, page.items()) : page.items();
				for (int i = 0; i < fresh.size(); i++) {
					items.add(fresh.get(i));
					born.add(LibraryGrid.revealAt(clock, i, STAGGER));
					lift.add(0f);
				}
				next = page.next();
				total = page.total();
				skipped += Math.max(0, page.raw() - page.items().size());
				failures = 0;
			} catch (CompletionException e) {
				error = message(e.getCause());
				retryAt = clock + RETRY_AFTER;
				failures++;
			}
			pending = null;
			loaded = true;
		}

		/** How many cards the list has with the pages still to come, not just the ones loaded so far. */
		int size() {
			return LibraryGrid.listSize(items.size(), next, total);
		}

		/**
		 * An empty list shows Loading, not its empty text, while a page is on its way: the first one, one tried again after
		 * a failure, or the next one after pages with nothing to show (a playlist starting with local files). As the
		 * Narrator says it ({@link Narration#shelfState}).
		 */
		boolean waiting() {
			return error.isEmpty() && (!loaded || pending != null);
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
			}, s == Section.LIKED, () -> clock);
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

	/** The panel's width on a screen this wide. */
	static int panelW(int screenW) {
		return Math.min(460, screenW - 16);
	}

	/** The width the songs get in a panel this wide. */
	static int viewWidth(int pw) {
		return pw - 24;
	}

	/** The panel's height on a screen this high. */
	static int panelH(int screenH) {
		return Math.min(300, screenH - 16);
	}

	/** A grid card's height with its two lines of text, for songs this wide. */
	static int cellHeight(int viewW) {
		return LibraryGrid.cellWidth(viewW) + TEXT_H;
	}

	/** The height the songs get on a screen this high, under a sign-in strip this tall (0 for none). */
	static int viewHeight(int screenH, int stripH) {
		return panelH(screenH) - HEADER_H - 12 - (stripH > 0 ? stripH + 3 : 0);
	}

	private void layout() {
		pw = panelW(width);
		ph = panelH(height);
		px = (width - pw) / 2;
		py = (height - ph) / 2 + Math.round((1 - openAnim) * 8);
		viewX = px + 12;
		viewW = viewWidth(pw);
		viewY = py + HEADER_H + 6 + bannerH();
		viewH = viewHeight(height, heardMode() ? bannerStripH() : 0);
		cols = LibraryGrid.columns(viewW);
		cellW = LibraryGrid.cellWidth(viewW);
		cellH = cellHeight(viewW);
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

	/** The sign-in strip's button, for a label this wide. */
	static int bannerButtonW(int labelW) {
		return labelW + 14;
	}

	/** Room for the sign-in strip's words on a screen this wide, beside its button. */
	static int bannerRoom(int screenW, int buttonW) {
		return panelW(screenW) - 20 - buttonW - 24;
	}

	/** The strip's layout, measured again only when its key changes: drawing, clicks and the focus outline all read this one. */
	private Banner banner() {
		BannerKey key = new BannerKey(mod.spotify().account(), width, font, net.minecraft.locale.Language.getInstance());
		Banner b = banner;
		if (b != null && b.key().equals(key)) {
			return b;
		}
		int bw = bannerButtonW(font.width(Tr.t("Open Music tab")));
		int room = bannerRoom(width, bw);
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
		return LibraryGrid.contentHeight(shelf.items.size(), cols, cellH, GRID_PAD, shelf.pending != null, !shelf.error.isEmpty())
				+ 10 * (gridErrorLines(shelf) - 1) + (footer ? FOOTER_RING : 0);
	}

	/** Room added under the grid's last line while the keyboard is on it, so the ring around it is not cut off at the bottom. */
	private static final int FOOTER_RING = 5;

	/** Where the first of {@code lines} lines under the grid sits, for a grid drawn from {@code top}. */
	private int footerY(Shelf shelf, int top, int lines) {
		return top + contentHeight(shelf) - (footer ? FOOTER_RING : 0) - 12 - 10 * (lines - 1);
	}

	/** The line under the songs shows: "Loading…" for the next page, or why it failed. Not in the carousel, which has no such line. */
	private boolean footerShown(Shelf shelf) {
		return view() != SkyLyricsConfig.LibraryView.CAROUSEL && !shelf.items.isEmpty() && (shelf.pending != null || !shelf.error.isEmpty());
	}

	/** The line under the songs says why the next page failed (the automatic tries may be over: only a click or Enter tries again). */
	private boolean footerFailed(Shelf shelf) {
		return footerShown(shelf) && shelf.pending == null;
	}

	/** The arrow that goes past the last card onto the line under the songs: Down in the grid, Right on the shelf. */
	private static boolean towardFooter(KeyEvent event, SkyLyricsConfig.LibraryView mode) {
		return switch (mode) {
			case GRID -> event.isDown();
			case SHELF -> event.isRight();
			case CAROUSEL -> false;
		};
	}

	/** Where that arrow takes the keyboard from the chosen card. */
	private int pastCard(Shelf shelf) {
		return view() == SkyLyricsConfig.LibraryView.SHELF ? LibraryViews.shelfMove(selected, 1, 0, shelfRows, shelf.items.size())
				: LibraryGrid.move(selected, cols, shelf.items.size());
	}

	/** The line under the songs as the Narrator says it with the keyboard on it. */
	private String footerWords(Shelf shelf) {
		return Narration.footer(shelf.pending != null, Tr.t("Loading"), shelf.error, Tr.t("Try again"), Tr.t("button"));
	}

	/** How many lines why the next page failed takes under the grid (at least 1, the Loading line's room the grid always gets). */
	private int gridErrorLines(Shelf shelf) {
		return shelf.pending != null || shelf.error.isEmpty() ? 1 : errorLines(shelf.error).size();
	}

	/** Lines of {@code text} centred in the panel, 10 apart from {@code y}, laid out in the direction of the whole text. */
	private void drawLines(GuiGraphics g, List<String> lines, String text, int y) {
		boolean rtl = Ui.paragraphRtl(text);
		for (int i = 0; i < lines.size(); i++) {
			g.drawCenteredString(font, Ui.visual(lines.get(i), rtl), px + pw / 2, y + 10 * i, Ui.MUTED);
		}
	}

	/** Room above and below the grid, so a hovered card's glow isn't cut off at the edge. */
	private static final int GRID_PAD = 3;

	private float maxScroll(Shelf shelf) {
		if (view() == SkyLyricsConfig.LibraryView.SHELF) {
			return LibraryViews.shelfMaxScroll(shelf.items.size(), shelfRows, shelfStride, LibraryGrid.GAP, shelfTailW(shelf), viewW);
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
		try {
			if (!fault.hasFailed()) { // the game may still finish the frame (or the narration) it was on
				drawBackdrop(g, mouseX, mouseY, partialTick);
			}
		} catch (RuntimeException | LinkageError e) {
			ScreenFault.unclip();
			fault.failed("drawing", e);
		}
	}

	private void drawBackdrop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
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
		try {
			if (!fault.hasFailed()) { // the game may still finish the frame (or the narration) it was on
				drawScreen(g, mouseX, mouseY, partialTick);
			}
		} catch (RuntimeException | LinkageError e) {
			ScreenFault.unclip();
			fault.failed("drawing", e);
		}
	}

	private void drawScreen(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
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
		boolean followPick = picks.follow(width, height);

		Frame f = new Frame(g, font, mouseX, mouseY, dt, true);
		Ui.rect(g, px - 1, py - 1, pw + 2, ph + 2, 9, 0x50000000);
		Ui.rect(g, px, py, pw, ph, 8, Ui.PANEL);
		renderHeader(g, f);
		Shelf shelf = shelf();
		if (shelf == null) {
			renderSignIn(g, f);
		} else {
			shelf.poll();
			if (footer && !footerShown(shelf)) {
				// The page tried again came: the keyboard goes on where the arrow that reached the line goes now, onto the new cards
				footer = false;
				selected = pastCard(shelf);
				reveal(shelf);
				afterKeyboardAction();
			}
			// On the line under the songs, the Narrator also hears its Loading and the error after another failed try
			String status = footer ? footerWords(shelf) : statusWords(shelf);
			if (!status.equals(lastStatus)) {
				lastStatus = status;
				afterKeyboardAction(); // loading finished, nothing found, how many results: said when it changes
			}
			if (followPick && stop < 0) {
				// The window changed size under the keyboard's pick (fewer columns move a card down, a shorter panel shows
				// less): scroll it back into view, as the next key would have
				if (footer) {
					shelf.scrollTarget = Float.MAX_VALUE; // the line under the songs sits at the very end
				} else {
					reveal(shelf);
				}
			}
			shelf.scrollTarget = Mth.clamp(shelf.scrollTarget, 0, maxScroll(shelf));
			shelf.scroll = Ui.approach(shelf.scroll, shelf.scrollTarget, dt, 18);
			if (view() == SkyLyricsConfig.LibraryView.SHELF && Ui.inside(mouseX, mouseY, viewX, viewY, viewW, viewH)) {
				// The wheel slides the shelf under a mouse that stays put: the cover now under it is chosen, as if it had moved there
				int hit = shelfHitAt(shelf, mouseX, mouseY);
				int was = selected;
				selected = LibraryKeys.afterShelfFrame(selected, hit, wheelPick);
				if (selected != was) {
					footer = false;
					picks.scrolledAway();
				}
			}
			boolean nearEnd = switch (view()) {
				case SHELF -> shelf.scrollTarget + shelfStride * 2 > maxScroll(shelf);
				case CAROUSEL -> selected >= shelf.items.size() - 6;
				default -> LibraryGrid.nearEnd(shelf.scrollTarget, viewH, contentHeight(shelf), cellH, selected, shelf.items.size(), cols);
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
			int x = px + TABS_LEFT;
			for (Section s : sections()) {
				String name = sectionName(s);
				int w = font.width(name) + TAB_PAD;
				boolean hover = Ui.inside(f.mx(), f.my(), x, y, w, 14);
				if (s == section) {
					Ui.rect(g, x, y, w, 14, 7, Ui.ACCENT_SOFT);
				} else if (hover) {
					Ui.rect(g, x, y, w, 14, 7, 0x14FFFFFF);
				}
				g.drawString(font, Ui.visual(name), x + TAB_PAD / 2, y + 3, s == section ? 0xFFFFFFFF : hover ? Ui.TEXT : Ui.MUTED, false);
				x += w + TAB_GAP;
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
		footer = false;
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
		return px + viewButtonsLeft(pw) + i * (TOGGLE_W + 2);
	}

	/** Where the first of the three view buttons starts, from the left edge of a panel this wide. */
	static int viewButtonsLeft(int pw) {
		return pw - 10 - 3 * TOGGLE_W - 2 * 2;
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
			if (shelf.waiting()) {
				searching(g, searchView ? Tr.t("Searching") : Tr.t("Loading"));
				return;
			}
			if (searchView && query.isBlank() && !recentSearches().isEmpty()) {
				renderRecent(g, f);
				return;
			}
			if (shelf.error.isEmpty()) {
				String empty = emptyText();
				if (opened != null) {
					// Why an open playlist shows nothing can take two lines in a small window, like why it didn't load
					List<String> lines = errorLines(empty);
					drawLines(g, lines, empty, errorTop(viewY + viewH / 2, lines.size()));
				} else {
					g.drawCenteredString(font, Ui.visual(Ui.clip(font, empty, viewW - 20)), px + pw / 2, viewY + viewH / 2 - 10, Ui.MUTED);
				}
				return;
			}
			// Why it failed, on as many lines as it needs, above the button that tries again
			List<String> lines = errorLines(shelf.error);
			drawLines(g, lines, shelf.error, errorTop(viewY + viewH / 2, lines.size()));
			int rw = Controls.buttonW(font.width(Tr.t("Try again")), 80);
			int bx = px + pw / 2 - rw / 2, by = viewY + viewH / 2 + 4;
			Controls.drawButton(f, bx, by, rw, 16, Tr.t("Try again"), Ui.inside(f.mx(), f.my(), bx, by, rw, 16), true, true);
			Ui.outline(f.g(), bx - 2, by - 2, rw + 4, 20, 6, Ui.TEXT);
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
		if (ClipBox.open(g, viewX - 4, viewY, viewX + viewW + 4, viewY + viewH)) {
			try {
				int top = viewY + GRID_PAD - Math.round(shelf.scroll);
				// The card the keyboard is on; none while it is on the line under the songs
				int chosen = footer ? -1 : selected;
				for (int i = 0; i < shelf.items.size(); i++) {
					int x = viewX + (i % cols) * (cellW + LibraryGrid.GAP);
					int y = top + (i / cols) * (cellH + LibraryGrid.GAP);
					if (y + cellH < viewY || y > viewY + viewH) {
						continue;
					}
					boolean hover = inView && Ui.inside(f.mx(), f.my(), x, y, cellW, cellH);
					float lift = Ui.approach(shelf.lift.get(i), hover || i == chosen ? 1 : 0, f.dt(), 14);
					shelf.lift.set(i, lift);
					float t = Mth.clamp((clock - shelf.born.get(i)) / POP, 0, 1);
					if (t <= 0) {
						continue;
					}
					Object entry = shelf.items.get(i);
					boolean current = entry instanceof LibraryItem item && LibraryGrid.isPlaying(item, now);
					renderCard(g, entry, x, y, LibraryGrid.popIn(t), t, lift, i == chosen, current, moving);
				}
				if (shelf.pending != null) {
					int y = footerY(shelf, top, 1);
					g.drawCenteredString(font, Ui.visual(Tr.t("Loading") + ".".repeat(1 + (int) (clock * 3) % 3)), px + pw / 2, y, Ui.DIM);
				} else if (!shelf.error.isEmpty()) {
					// In the Loading line's place, which the scroll reaches; the grid ends lower for each line more it takes
					List<String> lines = errorLines(shelf.error);
					drawLines(g, lines, shelf.error, footerY(shelf, top, lines.size()));
				}
				if (footer && footerShown(shelf)) {
					// The keyboard is on that line: a ring round it, like a button's
					List<String> lines = shelf.pending != null ? List.of(Tr.t("Loading") + "...") : errorLines(shelf.error);
					int w = 0;
					for (String line : lines) {
						w = Math.max(w, font.width(line));
					}
					int y = footerY(shelf, top, lines.size());
					Ui.outline(g, px + pw / 2 - w / 2 - 5, y - 4, w + 10, 10 * lines.size() + 6, 6, Ui.TEXT);
				}
			} finally {
				g.disableScissor();
			}
		}

		float max = maxScroll(shelf);
		if (max > 0) {
			int barH = Math.max(16, viewH * viewH / (viewH + Math.round(max)));
			int barY = viewY + Math.round((viewH - barH) * (shelf.scroll / max));
			Ui.rect(g, px + pw - 7, barY, 2, barH, 1, Ui.TRACK);
		}
	}

	/** The lines after the shelf's last column as drawn: "Loading" with {@code dots}, or why the next page failed on as many lines as it needs, or none. */
	private List<String> shelfTail(Shelf shelf, int dots) {
		String line = LibraryViews.tailText(shelf.pending != null, shelf.error, Tr.t("Loading") + ".".repeat(dots));
		if (line.isEmpty()) {
			return List.of();
		}
		return shelf.pending != null ? List.of(Ui.clip(font, line, viewW - 20)) : errorLines(line);
	}

	/** Width of those lines (with all three dots, so the room doesn't change as they move), or 0 when there are none. */
	private int shelfTailW(Shelf shelf) {
		int w = 0;
		for (String line : shelfTail(shelf, 3)) {
			w = Math.max(w, font.width(line));
		}
		return w;
	}

	/** Where the first of {@code lines} lines after the shelf's last column sits: together they are centred on the first row's covers. */
	private static int shelfTailY(int top, int lines) {
		return top + SHELF_COVER / 2 - 5 * (lines - 1);
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
		// The cover the keyboard is on; none while it is on the line after the last column
		int chosen = footer ? -1 : selected;
		if (ClipBox.open(g, viewX - 4, viewY, viewX + viewW + 4, viewY + viewH)) {
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
					float lift = Ui.approach(shelf.lift.get(i), hover || i == chosen ? 1 : 0, f.dt(), 14);
					shelf.lift.set(i, lift);
					float t = Mth.clamp((clock - shelf.born.get(i)) / POP, 0, 1);
					if (t <= 0) {
						continue;
					}
					Object entry = shelf.items.get(i);
					boolean current = entry instanceof LibraryItem item && LibraryGrid.isPlaying(item, now);
					renderCard(g, entry, x, y - Math.round(lift * 3), SHELF_COVER, 1, false, LibraryGrid.popIn(t), t, lift, i == chosen,
							current, moving);
				}
				// "Loading…", or why the next page failed (a click or Enter on it tries again), after the last column where the scroll reaches
				List<String> tail = shelfTail(shelf, 1 + (int) (clock * 3) % 3);
				boolean rtl = Ui.paragraphRtl(String.join(" ", tail));
				for (int i = 0; i < tail.size(); i++) {
					g.drawString(font, Ui.visual(tail.get(i), rtl), LibraryViews.tailX(viewX, left, width), shelfTailY(top, tail.size()) + 10 * i,
							shelf.pending != null ? Ui.DIM : Ui.MUTED, false);
				}
				if (footer && !tail.isEmpty()) {
					// The keyboard is on those lines: a ring round them, like a button's (the scissor leaves room for it at the right)
					Ui.outline(g, LibraryViews.tailX(viewX, left, width) - 4, shelfTailY(top, tail.size()) - 4, shelfTailW(shelf) + 8,
							10 * tail.size() + 6, 6, Ui.TEXT);
				}
			} finally {
				g.disableScissor();
			}
		}
		if (chosen >= 0 && chosen < shelf.items.size()) {
			String[] text = labels(shelf.items.get(chosen));
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
		if (ClipBox.open(g, viewX - 4, viewY, viewX + viewW + 4, viewY + viewH)) {
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
			return playlistEmptyText(opened);
		}
		return switch (section) {
			case SEARCH -> query.isBlank() ? Tr.t("Type to find a song") : Tr.t("Nothing found for “%s”", query.strip());
			case LIKED -> heardMode() ? Tr.t("Songs you play will show up here") : Tr.t("No liked songs yet");
			case PLAYLISTS -> Tr.t("No playlists yet");
		};
	}

	/**
	 * What an open playlist with no song cards says. One made only of local files or podcast episodes isn't empty (its
	 * card counts them, and Spotify's app shows them): the library just has no card for those.
	 */
	static String playlistEmptyText(Shelf playlist) {
		return playlist.skipped > 0 ? Tr.t("Local files and podcasts don't show here") : Tr.t("This playlist is empty");
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
		return new String[] {list.name(), songWords(list)};
	}

	/** How many songs a playlist has, in words: "13 songs". */
	private static String songWords(LibraryPlaylist list) {
		return list.songs() == 1 ? Tr.t("1 song") : Tr.t("%s songs", list.songs());
	}

	/** A playlist's song count under its card in the grid: {@code words} ("13 songs") when that fits {@code room}, else the number after a note ("♪ 13"). */
	static String songCount(String words, int songs, int room, java.util.function.ToIntFunction<String> width) {
		// The note fits any count in any language, where "13 canciones" ended in "…" on a small card
		return width.applyAsInt(words) <= room ? words : "♪ " + songs;
	}

	/** The letter drawn on a card without a cover, by title: worked out once, not every frame. */
	private final java.util.Map<String, String> initials = new java.util.LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<String, String> eldest) {
			return size() > 512;
		}
	};

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
			under = songCount(songWords(list), list.songs(), size, font::width);
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
				String initial = initials.computeIfAbsent(title, t -> dev.beli.skylyrics.util.TextCut.initial(t).toUpperCase(Locale.ROOT));
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

	/** Room for the toast's words in a panel this wide. */
	static int toastRoom(int pw) {
		return pw - 60;
	}

	/** Room for why the songs didn't load in a view this wide. */
	static int errorRoom(int viewW) {
		return viewW - 20;
	}

	/** Why the songs didn't load can take this many lines (in German, French, Russian and Ukrainian, Spotify blocking the app takes three in the smallest window). */
	static final int ERROR_LINES = 3;

	/** Why the songs didn't load, in lines that fit {@code room}: as many as it needs, up to {@link #ERROR_LINES}. */
	static List<String> errorLines(net.minecraft.client.StringSplitter splitter, String text, int room) {
		return wrapped(splitter, text, room, ERROR_LINES);
	}

	/** Where the first of {@code lines} lines of why the songs didn't load sits: they end just above the Try again button under the view's {@code middle}. */
	static int errorTop(int middle, int lines) {
		return middle - 10 * lines;
	}

	/**
	 * The toast's words in lines that fit {@code room}: one line when it fits, else two. Only a message too long even for
	 * two keeps the end of its second line cut with "…".
	 */
	static List<String> toastLines(net.minecraft.client.StringSplitter splitter, String text, int room) {
		return wrapped(splitter, text, room, 2);
	}

	/** Words in lines that fit {@code room}, as many as they need up to {@code most}; only past that the last line's end is cut with "…". */
	static List<String> wrapped(net.minecraft.client.StringSplitter splitter, String text, int room, int most) {
		List<String> lines = splitter.splitLines(text, room, net.minecraft.network.chat.Style.EMPTY).stream()
				.map(line -> line.getString().strip()).toList();
		if (lines.isEmpty()) {
			return List.of(text);
		}
		if (lines.size() <= most) {
			return lines;
		}
		List<String> out = new ArrayList<>(lines.subList(0, most - 1));
		out.add(Ui.clipBy(String.join(" ", lines.subList(most - 1, lines.size())), room, t -> (int) Math.ceil(splitter.stringWidth(t))));
		return List.copyOf(out);
	}

	/** A text's lines as last worked out, and for which text, room and font: worked out again only when one changes. */
	private static final class Wrap {
		private final int most;
		private List<String> lines = List.of();
		private String text = "";
		private int room = -1;
		private @Nullable Object font;

		Wrap(int most) {
			this.most = most;
		}

		List<String> lines(net.minecraft.client.gui.Font font, String text, int room) {
			if (!text.equals(this.text) || room != this.room || font != this.font) {
				lines = wrapped(font.getSplitter(), text, room, most);
				this.text = text;
				this.room = room;
				this.font = font;
			}
			return lines;
		}
	}

	private final Wrap toastWrap = new Wrap(2);
	private final Wrap errorWrap = new Wrap(ERROR_LINES);

	private List<String> toastLines() {
		return toastWrap.lines(font, toast, toastRoom(pw));
	}

	/** Why the songs didn't load, in lines that fit the view. */
	private List<String> errorLines(String error) {
		return errorWrap.lines(font, error, errorRoom(viewW));
	}

	private void renderToast(GuiGraphics g) {
		float shown = toastShown();
		if (toast.isEmpty() || toastAge >= shown) {
			return;
		}
		float a = Mth.clamp(Math.min(toastAge * 6, (shown - toastAge) * 3), 0, 1);
		List<String> lines = toastLines();
		int textW = 0;
		for (String line : lines) {
			textW = Math.max(textW, font.width(line));
		}
		// One line sits where it always did (16 high, its bottom 8 above the panel's); a second line grows it upward
		int h = 6 + 10 * lines.size();
		int w = textW + 20, x = px + (pw - w) / 2, y = py + ph - 8 - h + Math.round((1 - a) * 6);
		Ui.rect(g, x, y, w, h, 8, Ui.alpha(Ui.CONTROL_HOVER, a));
		boolean rtl = Ui.paragraphRtl(toast);
		for (int i = 0; i < lines.size(); i++) {
			g.drawCenteredString(font, Ui.visual(lines.get(i), rtl), px + pw / 2, y + 4 + 10 * i, Ui.alpha(Ui.TEXT, a));
		}
	}

	private void say(String text) {
		toast = text;
		toastAge = 0;
		message.shown();
		afterKeyboardAction(); // the Narrator reads it while it shows
	}

	private final MessageNarration message = new MessageNarration();

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
			Shelf songs = new Shelf(offset -> lib.playlist(list, offset), () -> clock);
			songs.more();
			opened = songs;
			openedAtNs = System.nanoTime();
			openedName = list.name();
			openedFrom = index;
			openedList = list;
			selected = -1;
			footer = false;
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
		footer = false;
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
		footer = false;
		sayNewSection = true;
		if (s == Section.SEARCH) {
			typing = true;
		}
	}

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		try {
			return onClick(event, doubleClick);
		} catch (RuntimeException | LinkageError e) {
			return fault.failed("a click", e);
		}
	}

	private boolean onClick(MouseButtonEvent event, boolean doubleClick) {
		keyboardLast = false;
		picks.scrolledAway();
		sayNewSection = false;
		sayResults = false;
		stop = -1;
		footer = false;
		if (!rescue.pressed(event.button())) {
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
				closedAtNs = System.nanoTime();
			}
		} else if (my >= y && my < y + 14) {
			Section[] all = sections();
			int[] widths = new int[all.length];
			for (int i = 0; i < all.length; i++) {
				widths[i] = font.width(sectionName(all[i])) + TAB_PAD;
			}
			int hit = LibraryKeys.sectionAt(mx, px + TABS_LEFT, widths, TAB_GAP, System.nanoTime() - closedAtNs);
			if (hit >= 0) {
				showSection(all[hit]);
				return true;
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
			int gridEnd = viewY + GRID_PAD - Math.round(shelf.scroll) + contentHeight(shelf);
			if (!shelf.items.isEmpty() && !shelf.error.isEmpty() && view() == SkyLyricsConfig.LibraryView.GRID
					&& my <= gridEnd && my >= gridEnd - LibraryGrid.FOOTER - 10 * (gridErrorLines(shelf) - 1)) {
				// The error under the last card is a button too, all its lines (Enter on it with the keyboard does the same)
				shelf.more();
				return true;
			}
			if (view() == SkyLyricsConfig.LibraryView.SHELF) {
				int tailLines = shelfTail(shelf, 3).size();
				if (!shelf.items.isEmpty() && !shelf.error.isEmpty() && LibraryViews.tailHit(mx, my, viewX, Math.round(shelf.scroll),
						LibraryViews.shelfWidth(shelf.items.size(), shelfRows, shelfStride, LibraryGrid.GAP), shelfTailW(shelf),
						shelfTailY(shelfTop(), tailLines), tailLines)) {
					// The error line after the last column is a button too, like the grid's
					shelf.more();
					return true;
				}
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
		try {
			onMouseMove(mx, my);
		} catch (RuntimeException | LinkageError e) {
			fault.failed("the mouse moving", e);
		}
	}

	private void onMouseMove(double mx, double my) {
		Shelf shelf = shelf();
		if (library != null && shelf != null && view() == SkyLyricsConfig.LibraryView.SHELF
				&& Ui.inside(mx, my, viewX, viewY, viewW, viewH)) {
			int hit = shelfHitAt(shelf, mx, my);
			if (hit >= 0) {
				// The mouse chose a cover: the keyboard is back on the covers too
				footer = false;
				picks.scrolledAway();
			}
			selected = LibraryKeys.afterHover(selected, hit);
		}
		super.mouseMoved(mx, my);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		try {
			return onScroll(mx, my, scrollX, scrollY);
		} catch (RuntimeException | LinkageError e) {
			return fault.failed("the mouse wheel", e);
		}
	}

	private boolean onScroll(double mx, double my, double scrollX, double scrollY) {
		picks.scrolledAway();
		Shelf shelf = shelf();
		// The shelf and the carousel run sideways: a sideways swipe moves them too
		double along = ScrollSteps.along(scrollX, scrollY, ScrollSteps.RIGHT_IS_POSITIVE);
		if (shelf != null && view() == SkyLyricsConfig.LibraryView.CAROUSEL) {
			int step = carouselSteps.step(along);
			if (!shelf.items.isEmpty() && step != 0) {
				selected = Mth.clamp(Math.max(0, selected) + step, 0, shelf.items.size() - 1);
			}
			return true;
		}
		if (shelf != null) {
			wheelPick = view() == SkyLyricsConfig.LibraryView.SHELF;
			double amount = view() == SkyLyricsConfig.LibraryView.SHELF ? along : scrollY;
			shelf.scrollTarget = Mth.clamp(shelf.scrollTarget - (float) amount * 30, 0, maxScroll(shelf));
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean keyPressed(KeyEvent event) {
		try {
			return onKey(event);
		} catch (RuntimeException | LinkageError e) {
			return fault.failed("a key", e);
		}
	}

	private boolean onKey(KeyEvent event) {
		keyboardLast = true;
		wheelPick = false;
		picks.keyPicked();
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
				stop = LibraryKeys.stopAfterClose(stop);
				sayNewSection = true;
			} else {
				onClose();
			}
			return true;
		}
		KeyMapping key = mod.libraryKey();
		if (key != null && key.matches(event)) {
			// A letter here starts a search, and a key held since it opened us only repeats
			if (ScreenKeys.libraryKeyCloses(typing, library != null, event.key(), ScreenKeys.realCtrl(event) || event.hasControlDownWithQuirk(), event.hasAltDown(), closeKeyHeld)) {
				mod.closedWithKey(key);
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
			int copy = ScreenKeys.copyOrCut(letter, event.hasControlDownWithQuirk(), event.hasShiftDown(), event.hasAltDown());
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
		if (footer && !footerShown(shelf)) {
			footer = false;
		}
		if (footer && arrow) {
			// On the line under the songs: the arrow that came here stays, the others go back to the card it came from
			if (!towardFooter(event, mode)) {
				footer = false;
				reveal(shelf);
			}
			return true;
		}
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
			int from = selected;
			if (mode == SkyLyricsConfig.LibraryView.SHELF) {
				int dCol = event.isLeft() ? -1 : event.isRight() ? 1 : 0;
				int dRow = event.isUp() ? -1 : event.isDown() ? 1 : 0;
				selected = LibraryViews.shelfMove(selected, dCol, dRow, shelfRows, shelf.items.size());
			} else {
				selected = LibraryGrid.move(selected, step, shelf.items.size());
			}
			if (LibraryKeys.ontoFooter(towardFooter(event, mode), from >= 0 && selected == from, footerFailed(shelf))) {
				// Past the last card onto the line saying why the next page failed, where Enter tries again; it sits at the very end
				footer = true;
				shelf.scrollTarget = Float.MAX_VALUE;
				return true;
			}
			reveal(shelf);
			return true;
		}
		if (LibraryKeys.triesAgain(footer, shelf.items.isEmpty() && !shelf.error.isEmpty())
				&& (event.isConfirmation() || !typing && event.key() == InputConstants.KEY_SPACE)) {
			// Tries the page again, as a click does (nothing while it is already being tried again); never plays the card above
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
		shelf.scrollTarget = LibraryGrid.reveal(shelf.scrollTarget, selected, cols, cellH, viewH);
	}

	//? if >=1.21.9 {
	@Override
	//?}
	public boolean charTyped(CharacterEvent event) {
		try {
			return onChar(event);
		} catch (RuntimeException | LinkageError e) {
			return fault.failed("typing", e);
		}
	}

	private boolean onChar(CharacterEvent event) {
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
		footer = false;
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
			int n = shelf.size();
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
		if (footer && footerShown(shelf)) {
			return footerWords(shelf);
		}
		if (selected >= 0 && selected < shelf.items.size()) {
			Object entry = shelf.items.get(selected);
			String[] text = labels(entry);
			boolean current = entry instanceof LibraryItem item && LibraryGrid.isPlaying(item, mod.tracker().track());
			String action = entry instanceof LibraryPlaylist list && list.openable() ? Tr.t("Press Enter to open") : Tr.t("button");
			String tile = Narration.tile(Narration.card(name, shelf.error, text[0], Narration.underPlaying(text[1], current, Tr.t("Now playing")),
					Tr.t("%s of %s", selected + 1, shelf.size())), action);
			return sayResults ? Narration.join(statusWords(shelf), tile) : tile;
		}
		return Narration.join(Narration.card(name, shelf.error, "", "", ""), listWords(shelf));
	}

	/** What a list says with the keyboard in it on no card: what it is doing, or once it has cards how many ("40 songs"). */
	private String listWords(Shelf shelf) {
		int n = shelf.size();
		String one, many;
		if (opened == null && section == Section.SEARCH) {
			one = Tr.t("1 result");
			many = Tr.t("%s results", n);
		} else if (opened == null && section == Section.PLAYLISTS) {
			one = Tr.t("1 playlist");
			many = Tr.t("%s playlists", n);
		} else {
			one = Tr.t("1 song");
			many = Tr.t("%s songs", n);
		}
		return Narration.listState(statusWords(shelf), n, one, many);
	}

	/** The message in the toast while it is still showing, for the Narrator. */
	private String toastWords() {
		return toastAge < toastShown() ? toast : "";
	}

	/** How long the toast stays: its time scaled by Minecraft's Notification Display Time. */
	private static float toastShown() {
		return ToastTime.shownFor(TOAST_TIME, ToastTime.multiplier());
	}

	@Override
	protected void updateNarrationState(NarrationElementOutput output) {
		try {
			if (!fault.hasFailed()) { // the game may still finish the frame (or the narration) it was on
				narrateScreen(output);
			}
		} catch (RuntimeException | LinkageError e) {
			fault.failed("its narration", e);
		}
	}

	private void narrateScreen(NarrationElementOutput output) {
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
		message.add(output, toastWords());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void leaveAfterError() {
		ScreenFault.leave(this, parent);
	}

	@Override
	public void onClose() {
		try {
			closed = true;
			//? if >=26.2 {
			/*minecraft.gui.setScreen(parent);
			*///?} else {
			minecraft.setScreen(parent);
			//?}
		} catch (RuntimeException | LinkageError e) {
			fault.failed("closing", e);
		}
	}

	@Override
	public void removed() {
		try {
			lastSection = section;
			// Page loads still waiting for their turn are not wanted any more
			shelves.values().forEach(Shelf::cancel);
			if (opened != null) {
				opened.cancel();
			}
		} catch (RuntimeException | LinkageError e) {
			fault.failedClosing(e);
		}
	}
}
