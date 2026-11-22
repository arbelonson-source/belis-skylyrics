package dev.beli.skylyrics.render;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.DoublePredicate;
import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=26.1 {
/*import net.minecraft.util.LightCoordsUtil;
*///?} else {
import net.minecraft.client.renderer.LightTexture;
//?}
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.config.SkyLyricsConfig.WorldArrival;
import dev.beli.skylyrics.config.SkyLyricsConfig.WorldLeave;
import dev.beli.skylyrics.render.LyricsRenderer.Display;
import dev.beli.skylyrics.SkyLyricsClient;
import dev.beli.skylyrics.dev.DevTestHook;

/**
 * The "words in the world" layout: every word or two of a line pops up as it's sung, on its own card floating a
 * little above where you were looking, scattered so the cards don't cover each other. A card stays where it
 * appeared (drifting slowly upwards) while you move around it; once the line is over (or, without "Keep the whole
 * line", once the next words come), it greys and fades away over a couple of seconds. These are drawn in the world pass on purpose: each card has its own place and turn,
 * which the flat screen layer can't show.
 */
final class WorldWords {
	private static final float DEG = Mth.DEG_TO_RAD;
	/** Cards appear this far from your eyes, in blocks. */
	private static final float NEAR = 6.5f, FAR = 15f;
	/** Never closer than this, even when blocks are in the way. */
	private static final float CLOSEST = 3f;
	/** How many blocks a card may be lifted to get its whole face out of the ground or a wall. */
	private static final int LIFT_STEPS = 8;
	/** The middle of the scatter sits this many degrees above the crosshair. */
	private static final float LIFT = 12f;
	/** Half the width and half the height of the scatter around that middle, in degrees. */
	private static final float SPREAD_X = 19f, SPREAD_Y = 11f;
	/** Spots in the scatter, and how many of the last used ones a new card avoids. */
	static final int SLOTS = 12;
	private static final int RECENT = 2;
	/** How far a card may land from its spot, in degrees, so the pattern doesn't look like a grid. */
	private static final float WOBBLE_X = 3.5f, WOBBLE_Y = 3f;
	/** Spots tried for each new card; the one overlapping the other cards least wins. */
	private static final int TRIES = 24;
	/**
	 * A flowing line's next word moves on along the line this many times at most to find a spot clear of older words,
	 * which it keeps this many degrees from.
	 */
	private static final int CLEAR_TRIES = 6;
	/** Players closer than this are kept clear of words; how much room to leave around them, in blocks. */
	static final float KNOCK_TIME = 2.2f, KNOCK_PEAK = 0.3f, KNOCK_DISTANCE = 0.12f, KNOCK_SPIN = 50, KNOCK_DAMP = 3, KNOCK_RATE = 18,
			KNOCK_SQUASH = 0.25f, KNOCK_FLASH = 6, KNOCK_MARGIN = 1;
	private static final double PLAYER_RANGE = 64;
	private static final float PLAYER_MARGIN = 0.6f;
	private static final float CLEAR_PAD = 0.4f;
	/** Older words are kept clear of where they'll have drifted up to this many seconds from now, not just where they are. */
	private static final float LOOK_AHEAD = 1.5f;
	/** How much further out than the spots the last tries may go, when a crowd (a whole line kept up) fills them. */
	private static final float REACH = 0.6f;
	/** Room kept around each card when judging overlap, in degrees. */
	private static final float PAD_X = 2f, PAD_Y = 1.7f;
	/** Cards turn sideways by between these many degrees, and nod and lean a little, so they don't look pasted on. */
	private static final float TURN_MIN = 10f, TURN_MAX = 30f, NOD = 7f, LEAN = 4f;
	/** Blocks per text unit: capital letters end up a bit under half a block tall, about two degrees across the scatter. */
	private static final float SIZE = 0.06f;
	/**
	 * Card size, times the setting, for short pieces and for long ones; sizes in between follow the letter count. Kept
	 * close together, about 1.4 to 1, so a line reads as one even staircase and little words stay easy to read.
	 */
	private static final float SIZE_SHORT = 0.85f, SIZE_LONG = 1.2f;
	/** Pieces with up to this many letters are the smallest, with this many more the biggest. */
	private static final int SHORT_LETTERS = 3, LONG_LETTERS = 6;
	/** Each card is randomly up to this much smaller or bigger than its letters say, so sizes don't look graded. */
	private static final float SIZE_JITTER = 0.1f;
	/** The one big word of each line (the One big word setting) is this many times its usual size. */
	static final float BIG = 1.45f;
	/** At most this many cards at once by default (the setting); past it the oldest is cut short. */
	static final int MAX_CARDS = 7;
	/** A card cut short to make room fades out over this many seconds. */
	private static final float CUT = 0.3f;
	/** How long a word goes on being cut by its own line starting over: quick, since the new words are written over it. */
	private static final float CUT_OWN = 0.1f;
	/** After a jump in the song, at most this many of the skipped pieces are spawned (the latest ones). */
	static final int CATCH_UP = 2;
	/** Once its words are over, a card fades out over this many seconds by default (the setting). */
	static final float FADE = 1.2f;
	/** ...and in the first of these seconds it pales towards a soft white, by this much, so it fades into the sky like a ghost
	 * of itself instead of turning a darker grey than the sky around it. */
	private static final float PALE_TIME = 0.24f, PALE = 0.55f;
	private static final int PALE_RGB = 0xF4F2FF;
	/** Once the next word lands, a sung word eases down to this much of its size over this many seconds, so the word
	 * being sung always stands out from the ones just before it. */
	private static final float SETTLED = 0.82f, SETTLE_TIME = 0.35f;
	/** How opaque it gets as it settles, so the word being sung is the brightest one in the line. */
	private static final float SETTLED_ALPHA = 0.7f;
	/** A new card fades in over this many seconds, and its letters type in over this many. */
	private static final float FADE_IN = 0.14f, TYPE = 0.23f;
	/** A new card starts this much bigger and settles back over this many seconds. */
	static final float POP = 0.22f, POP_TIME = 0.24f;
	/** In a flowing line a new word instead slides this many text units in from the left as it fades up, over this many seconds. */
	static final float SLIDE = 4f, SLIDE_TIME = 0.22f;
	/** A new word lands this much of the way to white and settles into the lyrics colour over this many seconds. */
	static final float FLASH = 0.55f, FLASH_TIME = 0.7f;
	/** The Drop arrival: a word starts this many text units above its place and falls in over this many seconds. */
	static final float DROP = 7f, DROP_TIME = 0.42f;
	/** The Zoom arrival: a word starts at this much of its size and grows in over this many seconds. */
	static final float ZOOM_FROM = 0.15f, ZOOM_TIME = 0.34f;
	/** The Fade arrival: a word fades in over this many seconds. */
	static final float SOFT_FADE = 0.5f;
	/** A typed word's next letter starts this long after the one before, and takes {@code TYPE_FADE} to fade in. */
	private static final float TYPE_STEP = 0.05f, TYPE_FADE = 0.12f;
	/** How far an arrival overshoots before it settles: the usual "back" easing. */
	private static final float BACK = 1.70158f;
	/** Cards drift up this many blocks a second, up to this high. */
	private static final float RISE = 0.25f, RISE_MAX = 1f;
	/** How much smaller a let-go card has got by the time it has faded out. */
	private static final float LEAVE = 0.15f;
	/** The Float and Sink leavings: how many blocks a word has moved up or down by the time it has faded out. */
	static final float LEAVE_RISE = 1.2f;
	/** The Blow leaving: how many text units a word has blown aside by the time it has faded out, and how far it tips (degrees). */
	static final float BLOW = 48f, BLOW_TIP = 14f;
	/** When you die the words drop away: how long they take to fade, how hard they fall (blocks per second, squared) and spin (degrees a second). */
	private static final float FALL_TIME = 1.6f, FALL_PULL = 3f, FALL_SPIN = 40f;
	/** While up, a card rocks this many degrees either way and bobs this many blocks, once every {@code SWAY_TIME} seconds. */
	private static final float SWAY_LEAN = 1.6f, SWAY_BOB = 0.05f, SWAY_TIME = 5.5f;
	/** Before the first frame's view is known, cards further from where you look than this (cosine of the angle) aren't drawn. */
	private static final float CULL = 0.3f;
	/** A card's half height, and how far its glow and shadow reach past the letters, in text units (for the on-screen check). */
	private static final float HALF_HEIGHT = 6f, EDGE = 3f;
	/** Cards this far away are dropped, in blocks. */
	private static final float GONE_DISTANCE = 48f;
	/**
	 * A jump further than this in one frame is a teleport, not flying, so the words stay behind (blocks); after a slow
	 * frame or a lag spike, up to {@link #GLIDE_SPEED} blocks a second still counts as flying.
	 */
	private static final float GLIDE_JUMP = 16f;
	/** Faster than any dive or firework boost, in blocks a second. */
	private static final float GLIDE_SPEED = 100f;
	/**
	 * Moving faster than this, in blocks a second, for {@link #FAST_FRAMES} frames in a row counts as flying even when
	 * the game doesn't say so (a very fast fall, knockback): well above sprinting, riding or sailing.
	 */
	private static final float FAST_SPEED = 30f;
	private static final int FAST_FRAMES = 3;
	/** How far up or down, in degrees, cards turned along with your view may go, so they never flip over your head. */
	private static final float SWING_PITCH = 80f;
	/** Drop shadow: offset down-right in text units, colour, and opacity by night and by day (a bright sky needs it darker). */
	private static final float SHADOW_X = 0.7f, SHADOW_Y = 0.9f, SHADOW_NIGHT = 0.55f, SHADOW_DAY = 0.85f;
	private static final int SHADOW_RGB = 0x05040C;
	/** By day a thin dark edge goes all round the letters, this far out (text units) and this strong at full day, so they read against white clouds. */
	private static final float OUTLINE = 0.5f, OUTLINE_DAY = 0.7f;
	/** Soft glow: copies of the word around it, how far out (text units) and how strong at full glow. */
	private static final int GLOW_COPIES = 3, GLOW_LIGHT = 2;
	private static final float GLOW_RADIUS = 1.1f, GLOW_ALPHA = 0.4f;
	/** Glow on the words being sung and on the rest of the line still up, and how long (seconds) a let-go card's lasts. */
	private static final float GLOW_SUNG = 1.3f, GLOW_LIT = 0.8f;
	static final float GLOW_OUT = 0.6f;
	/** By day the glow goes this far toward black: a dark halo shows against a bright sky, a light one doesn't. */
	private static final float HALO_DAY = 0.75f;
	/** How much richer in colour the glow is than the letters it rings, like a neon tube: pale core, deep halo. */
	static final float NEON = 1.7f;
	/** Front to back, in text units, so the layers don't flicker against each other. */
	private static final float SHADOW_Z = -0.5f, GLOW_Z = 0.1f, FACE_Z = 0.3f;
	/** From the top of a row of text to the middle of its capitals. */
	private static final float CAP_MIDDLE = 3.8f;
	/** Words with at most this many letters lean on the next word ("CAN YOU", "THE BEAT"). */
	static final int SHORT_WORD = 3;
	/** Chinese and Japanese have no spaces, so a run of their characters is cut into pieces of about this many. */
	static final int CJK_PIECE = 3;
	/** The scatter's spots, as degrees right of and above its middle. */
	private static final float[][] SPOTS = slots();
	/** How far a flowing line floats, and how far it may start off the middle of the view (degrees). */
	/** A line's words, with their mixed sizes and the gaps between, take about this much more room than its plain text; rows fill to about this much. */
	private static final float LINE_SPREAD = 1.25f, ROW_FILL = 0.85f;
	/** A flowing line's rows get wider with the Size setting, up to this many times {@link Sentence#WIDTH}. */
	private static final float ROW_GROW = 1.8f;
	private static final float SENTENCE_NEAR = 8f, SENTENCE_FAR = 12f, SENTENCE_SHIFT_X = 6f, SENTENCE_SHIFT_Y = 5f;
	/** How far a flowing line turns towards you (degrees) and how much its pieces vary in distance (a fraction). */
	private static final float SENTENCE_TURN = 15f, SENTENCE_DEPTH = 0.03f;
	/** A flowing line's new rows start up to this far in, its pieces sit up to this far off the row (degrees). */
	private static final float SENTENCE_INDENT = 2.5f, SENTENCE_BOB = 0.7f;
	/** Each piece of a flowing line steps the other way from the last, by at least this share of {@link #SENTENCE_BOB}. */
	static final float SENTENCE_STEP = 0.45f;
	/** How much each piece of a flowing line turns differently from the rest (degrees), and how much of the usual nod and lean it keeps.
	 * The nod stays small so the line reads as one; each piece keeps a little more than the usual lean, so the words tip a few degrees
	 * each their own way instead of sitting stiffly level. */
	static final float SENTENCE_WOBBLE = 1.5f, SENTENCE_NOD = 0.25f, SENTENCE_LEAN = 1.25f;
	/** How far (in letter pixels) the end of a tipped word in a flowing line may rise or drop, so long words tip less and stay off the next row. */
	static final float TIP_DROP = 1.2f;
	/** A flowing line rises or falls by between these many degrees, words and all, like a line written across a page. */
	private static final float SENTENCE_SLOPE_MIN = 1.5f, SENTENCE_SLOPE_MAX = 5f;
	/** A flowing line's rows also bow by between these many degrees in the middle, mostly up like an arch, sometimes down. */
	private static final float SENTENCE_BEND_MIN = 0.8f, SENTENCE_BEND_MAX = 2.2f, SENTENCE_ARCH = 0.7f;
	/** A new song's title: how much bigger than a word, how many seconds it stays, degrees above the words and blocks out. */
	static final float TITLE_SIZE = 1.7f, TITLE_HOLD = 4f, TITLE_LIFT = 9f, TITLE_DISTANCE = 13f;
	/** The artist under a title: how much smaller, and how many degrees lower. */
	static final float ARTIST_SIZE = 0.55f, ARTIST_DROP = 4.2f;
	/** Longest title shown, in characters; longer ones are cut with an ellipsis. */
	static final int TITLE_MAX = 32;
	/** The widest a title may look, in degrees of view: a bit more than a word, as it is the one big card. */
	static final float TITLE_MAX_DEGREES = 45;

	/** One card: where it stands, what it shows, and how it's turned. */
	private static final class Card {
		final Display display;
		final float[] at;
		/** Where it stands; moves along with you while you glide, ride or fly (elytra mode). */
		Vec3 pos;
		/** When separation found no free way for it (boxed in on both sides), and where it and the eye were then; -1 if not. */
		long blockedAt = -1;
		Vec3 blockedPos = Vec3.ZERO, blockedEye = Vec3.ZERO;
		final float nod, lean, size;
		/** The way the card faces, turned along with you while you travel. */
		float face;
		/** How many degrees of tilt were clipped at {@link #SWING_PITCH}, given back when you tilt the other way. */
		float clip;
		final long born;
		long goneAt = -1;
		/** The colour it had when it started fading, kept so the next line's colour never washes over it, or -1. */
		int goneRgb = -1;
		/** When it was cut short to make room for newer cards, or -1. */
		long cutAt = -1;
		/** How long the cut takes to fade it out, in seconds. */
		float cutFor = CUT;
		/** A new song's title: it keeps its own time, and stays while the lyrics change over. */
		boolean title;
		/** When it started dropping away because you died, or -1. */
		long fellAt = -1;
		/** The flowing line it's part of, or null. */
		@Nullable Sentence of;
		/** How far it rises, as a share of a card at its planned distance: one brought in close rises less, to look the same. */
		float lift = 1;
		/** Part of a right-to-left line (Hebrew, Arabic), so it slides in from the right. */
		boolean rtl;
		/** Where it's been nudged to, out of another word's way, and how far along it has glided there so far. */
		Vec3 slide = Vec3.ZERO, slid = Vec3.ZERO;
		/** When you last hit it, which way it was knocked, and how far and which way it spins. */
		long hitAt = -1;
		Vec3 knock = Vec3.ZERO;
		float spin;
		/** How far along its line it comes, from 0 at the first word to 1 at the last (for the Two-tone colour). */
		float along;
		/** Its letters' colour and glow as last drawn, kept while they stay the same, so a steady frame builds no style. */
		private @Nullable Style faceBase, faceStyle, haloBase, haloStyle;
		private int faceRgb, haloRgb;
		/** Its whole word in the style of each kind of copy ({@link #SHADOW}, {@link #HALO}, {@link #FACE}), as last drawn. */
		private final @Nullable Run[] runs = new Run[3];

		Style faceStyle(Style base, int rgb) {
			if (faceStyle == null || base != faceBase || rgb != faceRgb) {
				faceBase = base;
				faceRgb = rgb;
				faceStyle = base.withColor(rgb);
			}
			return faceStyle;
		}

		Style haloStyle(Style base, int rgb) {
			if (haloStyle == null || base != haloBase || rgb != haloRgb) {
				haloBase = base;
				haloRgb = rgb;
				haloStyle = base.withColor(rgb);
			}
			return haloStyle;
		}

		/** Letters {@code from} to {@code to} in {@code style}: the kept run when that is the whole word, else a new one. */
		Run run(int kind, int from, int to, Style style) {
			if (from != 0 || to != display.cps().length) {
				return new Run(display, from, to, style); // dissolving or typing in: the word is split up
			}
			Run kept = runs[kind];
			if (kept == null || kept.style() != style) {
				runs[kind] = kept = new Run(display, from, to, style);
			}
			return kept;
		}

		Card(Display display, float[] at, Vec3 pos, float face, float nod, float lean, float size, long born) {
			this.display = display;
			this.at = at;
			this.pos = pos;
			this.face = face;
			this.nod = nod;
			this.lean = lean;
			this.size = size;
			this.born = born;
		}
	}

	/** The kinds of copy a card is drawn as, each with its own style: the shadow and dark outline, the glow, the letters. */
	private static final int SHADOW = 0, HALO = 1, FACE = 2;

	/**
	 * Letters {@code from} to {@code to} of a card in one style, as the game draws them. The game keeps what it is given
	 * until the frame is drawn, so these never change; a card keeps its whole word in each of its styles ({@link Card#run}).
	 */
	private record Run(Display d, int from, int to, Style style) implements FormattedCharSequence {
		@Override
		public boolean accept(FormattedCharSink sink) {
			for (int v = from; v < to; v++) {
				if (!sink.accept(v, style, d.cps()[v])) {
					return false;
				}
			}
			return true;
		}
	}

	/** The shadow's style for the words' style, kept while that stays the same. */
	private @Nullable Style shadowBase, shadowStyle;

	private final List<Card> cards = new ArrayList<>();
	/** Reused for each card's turn while drawing, which only happens on the render thread. */
	private static final Quaternionf TURN = new Quaternionf();
	/** Reused for each card's middle while drawing; the checks that get it only read it. */
	private static final Vector3f CENTRE = new Vector3f();
	private final int[] recent = {-1, -1};
	private int line = Integer.MIN_VALUE;
	private int spawned;
	/** Pieces read left to right in rows (the Flowing line setting) instead of scattering. */
	private boolean flow;
	private boolean hold = true;
	private WorldArrival arrival = WorldArrival.POP;
	private WorldLeave leave = WorldLeave.SHRINK;
	private boolean soft;
	private boolean big = true;
	/** Which piece of the line being sung is its big word, or -1 for none. */
	private int bigPiece = -1;
	private boolean nudge = true, avoidPlayers = true, knock = true, swung, clearMiddle = true;
	private long lastNudge;
	/** Where the camera was last frame, to carry the cards along while you glide. */
	private @Nullable Vec3 lastEye;
	private float lastYaw, lastPitch;
	private long lastGlide;
	/** How many frames in a row the camera moved faster than {@link #FAST_SPEED}. */
	private int fastRun;
	/** Elytra mode is carrying the words this frame. */
	private boolean gliding;
	/** The Elytra mode setting: whether the words may travel with you at all. */
	private boolean travel = true;
	/** The lowest pitch a new row may start on to still be on screen, worked out as each word is placed. */
	private float seenLow = Sentence.ROW_LIMIT;
	/** Half the height of the screen in degrees, as of the last card placed. */
	private float seenHalf = 35f;
	/** How far either side of where you look a word may reach on this screen, in degrees ({@link #sideView}), as of the last card placed. */
	private float seenSide = Float.POSITIVE_INFINITY;
	private @Nullable Sentence sentence;
	/** The line written before it, which the next line of a verse goes under. */
	private @Nullable Sentence last;
	/** The sentences this lyric line has already started over from (it filled the sky, or you turned away), whose words may still show. */
	private final List<Sentence> earlier = new ArrayList<>();
	private int maxCards = MAX_CARDS;
	/** The glow's own colour, or -1 to glow in the words' colour. */
	private int glowRgb = -1;
	/** The colour each line fades into from first word to last, or -1 for one colour (the Two-tone setting). */
	private int toneRgb = -1;
	private float fade = FADE;
	/** How much further out than usual new cards float, and as much bigger, so they look the same size (the Distance setting). */
	private float away = 1;
	private float tilt = 1;
	/** How much a flowing line slopes and bends, times the usual (the Line curve setting). */
	private float curve = 1;
	/** How far cards drift up, times the usual (the Float up setting). */
	private float rise = 1;
	/** The line as given, to notice when it changes, and in capitals as shown. */
	private @Nullable String lineRaw, lineText;
	/** Whether new lines are written in capitals; a change shows from the next line on. */
	private boolean capitals;
	private List<int[]> lineChunks = List.of();
	/** How wide the whole line is, in font pixels, to tell how many rows it will take. */
	private float lineWidth;
	/** The Size setting: placing a card has to know how big it will be drawn. */
	private float wordScale = 1;
	/** Whether the line reads right to left, so a flowing line is written from the right. */
	private boolean lineRtl;
	/**
	 * Per piece of the line: {@link #RUN_NONE}, or where it is in a stretch that reads against the line's own direction
	 * (an English phrase in a Hebrew line, say), see {@link #runs}.
	 */
	private int[] lineRuns = new int[0];
	/** Per piece of the line: half its width in text units times its nominal size, to tell how wide a reversed stretch is. */
	private float[] lineHalf = new float[0];
	/** Each piece's letters and measured widths, kept from {@link #nominalHalf} for {@link #spawn}; null where not measured. */
	private Display[] lineDisplay = new Display[0];
	private float[][] lineAt = new float[0][];
	/** The piece being placed (an index into {@link #lineChunks}). */
	private int placing = -1;
	private long rng = System.nanoTime();

	/** For tests: fixes the random numbers, so a layout comes out the same every run. */
	void seed(long seed) {
		rng = seed;
	}

	/** You died: every word up drops out of the sky, spinning as it fades. */
	void fall(long now) {
		for (Card c : cards) {
			if (c.fellAt < 0) {
				c.fellAt = now;
			}
		}
	}

	/** Whether this is the dev game (the test hook is on); false in plain tests and in normal play. Read once. */
	private static final boolean HOOK = hookOn();

	private static boolean hookOn() {
		try {
			return DevTestHook.enabled();
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/**
	 * Whether a flowing line's piece with no clear place on the way in starts the line over instead of being lifted
	 * out (which would land it on the line's own earlier rows): only a line already under way that wrote a whole
	 * clear row before (it has been placed in {@code s} already) and began on clear ground. In a tunnel or by a wall
	 * nearly every piece is buried, and starting over each time would leave one word blinking at a time.
	 */
	static boolean startsOverInsteadOfLift(boolean flow, @Nullable Sentence s, boolean nowhereClear) {
		return flow && nowhereClear && s != null && s.pieces > 1 && s.rows >= 2 && !s.startedBuried;
	}

	/** Says why a flowing line starts over, in the dev game only (never in normal play). */
	private static void startsOver(Supplier<String> why) {
		if (HOOK) {
			SkyLyricsClient.LOG.info("Test hook: world line starts over ({})", why.get());
		}
	}

	/** For tests: the sentences this lyric line has started over from. */
	List<Sentence> earlierSentences() {
		return earlier;
	}

	void clear() {
		cards.clear();
		swung = false;
		newLyrics();
	}

	/**
	 * The song jumped back (a seek back, a restart, a loop): every word up is from a line that hasn't been sung yet at
	 * the new spot, so they all leave the usual way, and the line now being sung places its words again as it goes.
	 */
	void rewind(long now) {
		letGo(now);
		spawned = 0;
		startsOver(() -> "other: rewind (the song jumped back)");
		sentence = null;
		last = null;
		earlier.clear();
		recent[0] = recent[1] = -1;
	}

	/** Starts over for new lyrics, keeping a song title that's up. */
	void newLyrics() {
		startsOver(() -> "other: newLyrics (new lyrics, a style or font change, a world change)");
		cards.removeIf(c -> !c.title);
		line = Integer.MIN_VALUE;
		spawned = 0;
		lineRaw = null;
		lineText = null;
		sentence = null;
		last = null;
		earlier.clear();
		recent[0] = recent[1] = -1;
	}

	/** A piece's place in a stretch of the line that reads against its direction: none, first, in the middle, last. */
	static final int RUN_NONE = 0, RUN_FIRST = 1, RUN_MIDDLE = 2, RUN_LAST = 3;

	/** The line's pieces as {@link TopBarWords} sees them: a piece cut out of a Chinese or Japanese run joins the one before. */
	static List<TopBarWords.Piece> pieces(String text, List<int[]> chunks) {
		int[] cps = text.codePoints().toArray();
		List<TopBarWords.Piece> out = new ArrayList<>(chunks.size());
		for (int k = 0; k < chunks.size(); k++) {
			int[] c = chunks.get(k);
			out.add(new TopBarWords.Piece(new String(cps, c[0], c[1] - c[0]), k == 0 || chunks.get(k - 1)[1] != c[0]));
		}
		return out;
	}

	/**
	 * Which pieces of a line are written against the line's direction when it is laid out the way it is read: in a Hebrew
	 * line an English phrase of two or more pieces still reads left to right, so its first piece goes at the phrase's far
	 * end. Each stretch is marked {@link #RUN_FIRST}, {@link #RUN_MIDDLE}… {@link #RUN_LAST}; every other piece is {@link #RUN_NONE}.
	 * A line of one direction has none.
	 */
	static int[] runs(List<TopBarWords.Piece> pieces, boolean rtl) {
		int n = pieces.size();
		int[] out = new int[n];
		if (n < 2) {
			return out;
		}
		int[] rank = TopBarWords.visualRanks(pieces);
		// Counted from where the line starts being written, so a reversed stretch always runs down by one
		for (int i = 0; i < n; i++) {
			rank[i] = rtl ? n - 1 - rank[i] : rank[i];
		}
		for (int i = 0; i + 1 < n; ) {
			int j = i;
			while (j + 1 < n && rank[j + 1] == rank[j] - 1) {
				j++;
			}
			if (j > i) {
				out[i] = RUN_FIRST;
				for (int k = i + 1; k < j; k++) {
					out[k] = RUN_MIDDLE;
				}
				out[j] = RUN_LAST;
				i = j + 1;
			} else {
				i++;
			}
		}
		return out;
	}

	/** Half the width, in text units times size, a piece will have: what a reversed stretch is measured with. */
	private float nominalHalf(String text, List<int[]> chunks, int k, Font font, Style style) {
		int[] c = chunks.get(k);
		Display d = Display.of(new String(text.codePoints().toArray(), c[0], c[1] - c[0]), lineRtl);
		float[] at = widths(d, font, style);
		// spawn needs the same widths for this piece: keep them
		lineDisplay[k] = d;
		lineAt[k] = at;
		int letters = 0;
		for (int cp : d.cps()) {
			if (Character.isLetterOrDigit(cp)) {
				letters++;
			}
		}
		float size = SIZE * sizeFor(letters, 0.5f) * (big && bigPiece == k ? BIG : 1);
		return at[at.length - 1] * size / 2;
	}

	/**
	 * Splits a line into the pieces that appear together: one word, or a short word with the next one, never more
	 * than two words. Chinese and Japanese, written without spaces, come in pieces of a few characters instead of the
	 * whole line at once. Returns each piece's first and past-last code point in the line.
	 */
	static List<int[]> chunks(String text) {
		int[] cps = text.codePoints().toArray();
		// Each word, and whether it's a piece of a Chinese or Japanese run, which never leans on the next
		List<int[]> words = new ArrayList<>();
		for (int i = 0; i < cps.length; ) {
			if (cps[i] == ' ') {
				i++;
				continue;
			}
			int start = i;
			boolean cjk = false;
			while (i < cps.length && cps[i] != ' ') {
				cjk |= cjk(cps[i]);
				i++;
			}
			if (cjk) {
				for (int[] piece : cjkPieces(cps, start, i)) {
					words.add(new int[] {piece[0], piece[1], 1});
				}
			} else {
				words.add(new int[] {start, i, 0});
			}
		}
		List<int[]> out = new ArrayList<>();
		for (int w = 0; w < words.size(); w++) {
			int[] word = words.get(w);
			if (w + 1 < words.size() && word[2] == 0 && words.get(w + 1)[2] == 0 && letters(cps, word) <= SHORT_WORD) {
				out.add(new int[] {word[0], words.get(++w)[1]});
			} else {
				out.add(new int[] {word[0], word[1]});
			}
		}
		return out;
	}

	static boolean cjk(int cp) {
		Character.UnicodeScript script = Character.UnicodeScript.of(cp);
		return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
				|| script == Character.UnicodeScript.KATAKANA;
	}

	/**
	 * Cuts a run with Chinese or Japanese in it into even pieces of about {@link #CJK_PIECE} characters. Punctuation
	 * and the long-vowel and repeat marks stay with the character before them, an opening bracket with the one after,
	 * and a Latin word inside the run stays whole.
	 */
	private static List<int[]> cjkPieces(int[] cps, int start, int end) {
		List<int[]> units = cjkUnits(cps, start, end);
		int count = (units.size() + CJK_PIECE - 1) / CJK_PIECE;
		List<int[]> pieces = new ArrayList<>(count);
		for (int k = 0; k < count; k++) {
			int from = k * units.size() / count, to = (k + 1) * units.size() / count;
			pieces.add(new int[] {units.get(from)[0], units.get(to - 1)[1]});
		}
		return pieces;
	}

	/** The single characters of a Chinese or Japanese run, each with the marks and brackets that stay with it. */
	static List<int[]> cjkUnits(int[] cps, int start, int end) {
		List<int[]> units = new ArrayList<>();
		int open = -1;
		boolean lastCjk = false;
		for (int i = start; i < end; i++) {
			int cp = cps[i];
			if (Character.getType(cp) == Character.START_PUNCTUATION) {
				open = open < 0 ? i : open;
				continue;
			}
			boolean mark = !Character.isLetterOrDigit(cp) || cp == 0x30FC || cp == 0x3005 || cp >= 0x309D && cp <= 0x309E
					|| cp >= 0x30FD && cp <= 0x30FE;
			if (open < 0 && !units.isEmpty() && (mark || !lastCjk && !cjk(cp))) {
				units.get(units.size() - 1)[1] = i + 1;
			} else if (mark && units.isEmpty()) {
				open = open < 0 ? i : open;
				continue;
			} else {
				units.add(new int[] {open < 0 ? i : open, i + 1});
				open = -1;
			}
			if (!mark) {
				lastCjk = cjk(cp);
			}
		}
		if (open >= 0) {
			if (units.isEmpty()) {
				units.add(new int[] {open, end});
			} else {
				units.get(units.size() - 1)[1] = end;
			}
		}
		return units;
	}

	private static int letters(int[] cps, int[] word) {
		int n = 0;
		for (int i = word[0]; i < word[1]; i++) {
			if (Character.isLetterOrDigit(cps[i])) {
				n++;
			}
		}
		return n;
	}

	/**
	 * How visible a card still is {@code seconds} after its words were over: it eases out, half gone halfway through,
	 * then slips away softly, rather than hanging on as a grey ghost and blinking out at the end.
	 */
	static float goneAlpha(float seconds) {
		return goneAlpha(seconds, FADE);
	}

	static float goneAlpha(float seconds, float fade) {
		float t = Mth.clamp(seconds / fade, 0, 1);
		return 1 - t * t * (3 - 2 * t);
	}

	/** A dissolving card's letters start going within this part of its fade, and each takes this part to go. */
	private static final float MELT_SPREAD = 0.55f, MELT_TIME = 0.25f;

	/** How big a let-go card still is, {@code seconds} after it was let go: it shrinks a little as it fades, as if drifting off. */
	static float leaveSize(float seconds, float fade) {
		return 1 - LEAVE * Mth.clamp(seconds / fade, 0, 1);
	}

	/** How many blocks a let-go card has moved up (below 0: down), {@code seconds} after it was let go. */
	static float leaveUp(WorldLeave leave, float seconds, float fade) {
		float t = Mth.clamp(seconds / fade, 0, 1);
		return switch (leave) {
			case FLOAT -> LEAVE_RISE * t;
			case SINK -> -LEAVE_RISE * t;
			// A blown word lifts a little too, the way a gust carries it
			case BLOW -> LEAVE_RISE / 4 * t * t;
			case SHRINK, DISSOLVE -> 0;
		};
	}

	/**
	 * How much of letter {@code letter} of a dissolving card is left, {@code t} of the way through its fade (0 to 1): each
	 * letter starts going at its own moment, picked from {@code seed} so it's the same every frame, and is gone by 0.8.
	 */
	static float letterLeft(long seed, int letter, float t) {
		long h = seed * 0x9E3779B97F4A7C15L + (letter + 1) * 0xBF58476D1CE4E5B9L;
		h = (h ^ h >>> 31) * 0x94D049BB133111EBL;
		h ^= h >>> 29;
		float start = (h >>> 40) / (float) (1 << 24) * MELT_SPREAD;
		float u = Mth.clamp((t - start) / MELT_TIME, 0, 1);
		return 1 - u * u * (3 - 2 * u);
	}

	/** How far letter {@code letter} (reading order) of a word typed in {@code seconds} ago has faded in, 0-1. */
	static float typedIn(int letter, float seconds) {
		float t = Mth.clamp((seconds - letter * TYPE_STEP) / TYPE_FADE, 0, 1);
		return t * t * (3 - 2 * t);
	}

	/** How far a let-go card has blown aside, in text units: slowly at first, then faster, like a leaf caught by the wind. */
	static float blowAside(WorldLeave leave, float seconds, float fade) {
		float t = Mth.clamp(seconds / fade, 0, 1);
		return leave == WorldLeave.BLOW ? BLOW * t * t : 0;
	}

	/**
	 * Which way a blown card drifts, in text units: back toward where its line began (left in English, right in Hebrew),
	 * so it never sweeps across the words still being sung.
	 */
	static float blownBack(WorldLeave leave, float seconds, float fade, boolean rtl) {
		return (rtl ? 1 : -1) * blowAside(leave, seconds, fade);
	}

	/** {@link #leaveUp(WorldLeave, float, float)} with Distortion Effects {@code still} (0 to 1): 0 leaves the card where it was. */
	static float leaveUp(WorldLeave leave, float seconds, float fade, float still) {
		return leaveUp(leave, seconds, fade) * still;
	}

	/** {@link #blownBack(WorldLeave, float, float, boolean)} with Distortion Effects {@code still}. */
	static float blownBack(WorldLeave leave, float seconds, float fade, boolean rtl, float still) {
		return blownBack(leave, seconds, fade, rtl) * still;
	}

	/** {@link #leaveSize(float, float)} with Distortion Effects {@code still}. */
	static float leaveSize(float seconds, float fade, float still) {
		return Calm.around(leaveSize(seconds, fade), 1, still);
	}

	/** {@link #fallDrop(float)} with Distortion Effects {@code still}. */
	static float fallDrop(float seconds, float still) {
		return fallDrop(seconds) * still;
	}

	/** {@link #slideIn(float)} with Distortion Effects {@code still}. */
	static float slideIn(float seconds, float still) {
		return slideIn(seconds) * still;
	}

	/** How much of the way to its target a gliding word covers in {@code dt} seconds; with Distortion Effects at 0 it doesn't glide. */
	static float glideEase(float dt, float still) {
		return Calm.around(1 - (float) Math.exp(-SLIDE_SPEED * dt), 1, still);
	}

	/** How many blocks card {@code c} has moved up or down while leaving, {@code ahead} seconds from now. */
	private float leftUp(Card c, long now, float ahead) {
		return c.goneAt < 0 ? 0 : leaveUp(leave, (now - c.goneAt) / 1e9f + ahead, fade, Calm.motion());
	}

	/** How visible a card cut short {@code seconds} ago still is. */
	static float cutAlpha(float seconds) {
		return cutAlpha(seconds, CUT);
	}

	/** The same for a cut that takes {@code length} seconds. */
	static float cutAlpha(float seconds, float length) {
		return 1 - Mth.clamp(seconds / length, 0, 1);
	}

	/**
	 * How big a card is, times the setting: little words stay small and long ones get big, so they sit together like
	 * a collage instead of a row of equal labels. {@code roll} is a random number from 0 to 1.
	 */
	static float sizeFor(int letters, float roll) {
		float t = Mth.clamp((letters - SHORT_LETTERS) / (float) LONG_LETTERS, 0, 1);
		return Mth.lerp(t, SIZE_SHORT, SIZE_LONG) * (1 + (roll * 2 - 1) * SIZE_JITTER);
	}

	/** The widest a card may look, in degrees of view, and the longest piece that is drawn big. */
	static final float MAX_CARD_DEGREES = 30;
	static final int BIG_MAX_LETTERS = 14;

	/**
	 * A card's size, scaled down if it is {@code width} blocks wide at this {@code size} and so would look wider than
	 * {@code maxDegrees} of view from {@code distance} blocks away; smaller words keep their size.
	 */
	static float fitSize(float size, float width, float distance, float maxDegrees) {
		float most = 2 * distance * (float) Math.tan(Math.toRadians(maxDegrees / 2));
		return width <= most || width <= 0 ? size : size * most / width;
	}

	/**
	 * The size a card of the piece with letter positions {@code at} really gets at {@code distance}: {@code size} shrunk
	 * to fit by {@link #fitSize}. A card is laid out with this, so a long word reserves the room it takes, not more.
	 */
	static float fitted(float size, float[] at, float wordScale, float distance) {
		return fitSize(size, at[at.length - 1] * size * wordScale, distance, MAX_CARD_DEGREES);
	}

	/**
	 * Which piece of a line is drawn big, so each line has one word that stands out like a sung highlight: any piece
	 * but the first with a few letters, picked by {@code roll} (0 to 1). Lines of fewer than three pieces have none.
	 */
	static int bigPiece(String text, List<int[]> chunks, float roll) {
		if (chunks.size() < 3) {
			return -1;
		}
		int[] cps = text.codePoints().toArray();
		List<Integer> fits = new ArrayList<>();
		for (int i = 1; i < chunks.size(); i++) {
			int letters = 0;
			for (int c = chunks.get(i)[0]; c < chunks.get(i)[1]; c++) {
				if (Character.isLetterOrDigit(cps[c])) {
					letters++;
				}
			}
			if (letters >= SHORT_LETTERS && letters <= BIG_MAX_LETTERS) {
				fits.add(i);
			}
		}
		return fits.isEmpty() ? -1 : fits.get(Math.min((int) (roll * fits.size()), fits.size() - 1));
	}

	/** Glows in {@code rgb}, or in the words' own colour when it's -1. */
	void glowColour(int rgb) {
		glowRgb = rgb;
	}

	/** Each line fades from the words' colour into {@code rgb} from first word to last, or keeps one colour when it's -1. */
	void tone(int rgb) {
		toneRgb = rgb;
	}

	/** The colour of a word {@code along} its line (0 first, 1 last): {@code rgb}, blended toward {@code tone} unless that's -1. */
	static int toned(int rgb, int tone, float along) {
		return tone < 0 ? rgb : mix(rgb, tone, Mth.clamp(along, 0, 1));
	}

	/** How each new word shows up (the Arrival setting). */
	void arrival(WorldArrival arrival) {
		this.arrival = arrival == null ? WorldArrival.POP : arrival;
	}

	/** How each sung word goes (the Leaving setting). */
	void leave(WorldLeave leave) {
		this.leave = leave == null ? WorldLeave.SHRINK : leave;
	}

	/** Whether letters get only their glow, with no drop shadow or dark edge (the Soft edges setting). */
	void soft(boolean soft) {
		this.soft = soft;
	}

	/** Whether one word in each line is drawn bigger than the rest (the One big word setting). */
	void big(boolean on) {
		this.big = on;
	}

	/** Whether words that end up overlapping glide apart (the Slide apart setting). */
	void nudge(boolean on) {
		this.nudge = on;
	}

	/** How much a flowing line slopes and bends (the Line curve setting); 0 keeps it straight. */
	void curve(float amount) {
		this.curve = Mth.clamp(amount, 0, 2);
	}

	/** Whether new words keep out of the middle of your view (the Keep the middle clear setting). */
	void clearMiddle(boolean on) {
		this.clearMiddle = on;
	}

	/** Whether words keep out of the way of other players, for PvP, and whether hitting a word knocks it. */
	void players(boolean avoid, boolean knock) {
		this.avoidPlayers = avoid;
		this.knock = knock;
	}

	/** You swung: the next frame knocks the word under the crosshair, if there is one. */
	void swing() {
		swung = knock;
	}

	/** The words aren't being drawn (hidden), so a swing doesn't wait to knock a word when they show again. */
	void dropSwing() {
		swung = false;
	}

	private static final List<Player> inSight = new ArrayList<>();
	private static long sightTick = Long.MIN_VALUE;
	private static @Nullable ClientLevel sightLevel;

	/** Lets go of the last world's players, so its whole level can be freed once you leave it. */
	static void forgetPlayers() {
		inSight.clear();
		sightLevel = null;
	}

	/**
	 * Where the other players near you are, as boxes of yaw, pitch and half sizes in degrees seen from {@code eye}. A
	 * player's box reaches up a little, since words float upwards and would otherwise rise into them.
	 */
	private static List<float[]> players(@Nullable ClientLevel level, Vec3 eye) {
		List<float[]> spots = new ArrayList<>();
		Player me = Minecraft.getInstance().player;
		if (level == null || me == null) {
			// Let go of the old world's players once you've left it
			forgetPlayers();
			return spots;
		}
		// Only players you can see: a word dodging someone invisible or behind a wall would give them away in PvP.
		// Looking is checked once a tick, as it means a ray through the blocks for each player.
		if (level.getGameTime() != sightTick || level != sightLevel) {
			sightTick = level.getGameTime();
			sightLevel = level;
			inSight.clear();
			for (Player p : level.players()) {
				if (p != me && !p.isSpectator() && !p.isInvisibleTo(me) && p.distanceToSqr(me) < PLAYER_RANGE * PLAYER_RANGE
						&& me.hasLineOfSight(p)) {
					inSight.add(p);
				}
			}
		}
		for (Player p : inSight) {
			if (p.isRemoved()) {
				continue;
			}
			Vec3 to = p.position().add(0, p.getBbHeight() / 2, 0).subtract(eye);
			double far = to.length();
			if (far > 1 && far < PLAYER_RANGE) {
				spots.add(new float[] {yawOf(to), pitchOf(to), halfAngle(p.getBbWidth() / 2 + PLAYER_MARGIN, far),
						halfAngle(p.getBbHeight() / 2 + PLAYER_MARGIN, far)});
			}
		}
		return spots;
	}

	/**
	 * How many cards float at once, how many seconds sung ones take to fade, how far out new ones float, how much they
	 * turn and lean, how far they drift up and whether a line flows left to right (the World words settings).
	 */
	void tune(int maxCards, float fade, float away, float tilt, float rise, boolean flow, boolean hold, boolean capitals, float scale) {
		this.wordScale = Mth.clamp(scale, 0.25f, 4);
		this.away = Mth.clamp(away, 0.5f, 2);
		this.tilt = Mth.clamp(tilt, 0, 2);
		this.rise = Mth.clamp(rise, 0, 2);
		this.hold = hold;
		this.capitals = capitals;
		this.maxCards = Math.max(1, maxCards);
		this.fade = Math.max(0.1f, fade);
		if (flow != this.flow) {
			startsOver(() -> "other: the Flow setting changed");
			sentence = null;
			last = null;
			earlier.clear();
		}
		this.flow = flow;
	}

	/**
	 * The line in capitals, one letter for one letter. {@code toUpperCase} turns "ß" into "SS", which would put every
	 * later word out of step with how far the line has been sung, since that is counted in the line as written.
	 */
	static String capitals(String text) {
		return new String(text.codePoints().map(Character::toUpperCase).toArray(), 0, text.codePointCount(0, text.length()));
	}

	/** Whether a line reads right to left (Hebrew, Arabic), by its first strong letter. */
	static boolean rightToLeft(String text) {
		return Bidi.requiresBidi(text.toCharArray(), 0, text.length())
				&& !new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight();
	}

	/** How far towards white a word still is, {@code seconds} after it appeared: bright as it lands, then its own colour. */
	static float flash(float seconds) {
		float t = Mth.clamp(seconds / FLASH_TIME, 0, 1);
		return FLASH * (1 - t) * (1 - t);
	}

	/** How far short of its place a flowing word still is, {@code seconds} after it appeared: it glides in and settles. */
	static float slideIn(float seconds) {
		float t = Mth.clamp(seconds / SLIDE_TIME, 0, 1);
		return SLIDE * (1 - t) * (1 - t) * (1 - t);
	}

	/** Eases from 0 to 1, going a little past 1 near the end and settling back, like something landing. */
	static float back(float t) {
		float u = Mth.clamp(t, 0, 1) - 1;
		return 1 + (BACK + 1) * u * u * u + BACK * u * u;
	}

	/** How far above its place a dropping word still is, {@code seconds} after it appeared; it dips just below as it lands. */
	static float dropIn(float seconds) {
		return DROP * (1 - back(seconds / DROP_TIME));
	}

	/** How big a zooming word is, {@code seconds} after it appeared, from small to just past full size and back. */
	static float zoomIn(float seconds) {
		return ZOOM_FROM + (1 - ZOOM_FROM) * back(seconds / ZOOM_TIME);
	}

	/** How much bigger than normal a card is {@code seconds} after it appeared: a quick pop that settles. */
	static float pop(float seconds) {
		float t = Mth.clamp(seconds / POP_TIME, 0, 1);
		float settle = 1 - (1 - t) * (1 - t) * (1 - t);
		return 1 + POP * (1 - settle);
	}

	/**
	 * The scatter: spots spread evenly over an oval, each one a golden angle around from the last, so neighbours in
	 * the list land on opposite sides. Degrees right of and above the middle.
	 */
	static float[][] slots() {
		float golden = Mth.PI * (3 - Mth.sqrt(5));
		float[][] s = new float[SLOTS][];
		for (int i = 0; i < SLOTS; i++) {
			float r = Mth.sqrt((i + 0.5f) / SLOTS);
			s[i] = new float[] {Mth.cos(i * golden) * r * SPREAD_X, Mth.sin(i * golden) * r * SPREAD_Y};
		}
		return s;
	}

	/**
	 * Spawns the cards that have started being sung, and lets the sung ones go.
	 *
	 * @param index     the line being sung, or -1 / a break when nothing is
	 * @param text      that line's text (null in a break)
	 * @param sungChars how many code points of the line have been sung so far
	 * @param over      whether the line has been over for a moment, so its last words can go too
	 * @param fixedYaw  the compass direction new words appear around, or NaN for wherever you look
	 * @param fixedPitch the pitch they gather round with a fixed yaw (a locked view), or NaN for the horizon
	 */
	/** The style the lyrics' widths were measured with. */
	private @Nullable Style measured;

	/** Whether the style changed since the words were measured (a font setting, a resource reload): then they're measured again. */
	boolean restyled(Style style) {
		boolean changed = measured != null && !measured.equals(style);
		measured = style;
		if (changed) {
			newLyrics();
		}
		return changed;
	}

	/** The font reload count ({@link LyricsFont#reloadCount()}) the current line was measured at. */
	private long measuredAt = Long.MIN_VALUE;

	/** Measures the current line's chunks: their half widths, and the letter positions {@link #spawn} reuses. */
	private void measureLine(Font font, Style style) {
		lineHalf = new float[lineChunks.size()];
		lineDisplay = new Display[lineChunks.size()];
		lineAt = new float[lineChunks.size()][];
		for (int k = 0; k < lineHalf.length; k++) {
			if (lineRuns[k] != RUN_NONE) {
				lineHalf[k] = nominalHalf(lineText, lineChunks, k, font, style);
			}
		}
	}

	void update(int index, @Nullable String text, float sungChars, boolean over, float fixedYaw, float fixedPitch, Camera camera,
			@Nullable ClientLevel level, Font font, Style style, long now) {
		boolean fresh = false;
		restyled(style);
		long epoch = LyricsFont.reloadCount();
		if (index != line || text != null && !text.equals(lineRaw)) {
			fresh = true;
			line = index;
			lineRaw = text;
			lineText = text == null || !capitals ? text : capitals(text);
			lineChunks = lineText == null ? List.of() : chunks(lineText);
			bigPiece = lineText == null ? -1 : bigPiece(lineText, lineChunks, next());
			lineWidth = Float.NaN;
			lineRtl = lineText != null && rightToLeft(lineText);
			lineRuns = lineText == null ? new int[0] : runs(pieces(lineText, lineChunks), lineRtl);
			measureLine(font, style);
			measuredAt = epoch;
			spawned = 0;
			startsOver(() -> "other: a new lyric line");
			last = sentence;
			sentence = null;
			earlier.clear();
			if (hold) {
				letGo(now);
			}
		} else if (epoch != measuredAt) {
			// The fonts were reloaded (F3+T, a font pack, another language) during this line: its words still to come
			// are measured again, so they don't keep the old widths
			lineWidth = Float.NaN;
			measureLine(font, style);
			measuredAt = epoch;
		}
		int due = 0;
		while (due < lineChunks.size() && lineChunks.get(due)[0] <= sungChars) {
			due++;
		}
		spawned = skipTo(spawned, due, fresh, over);
		for (; spawned < due; spawned++) {
			if (!hold) {
				letGo(now);
			}
			spawn(lineChunks.get(spawned), centre(fixedYaw, fixedPitch, camera.yRot(), camera.xRot(), gliding), camera, level, font, style, now);
		}
		if (lineText == null || over) {
			letGo(now);
		}
		expire(camera.position(), now);
	}

	/** Starts the fade of a title that has had its time and of a card out of reach, and drops the ones that have faded. */
	void expire(Vec3 eye, long now) {
		for (Card c : cards) {
			if (c.title && c.goneAt < 0 && now - c.born > TITLE_HOLD * 1e9f
					|| c.goneAt < 0 && c.pos.distanceTo(eye) > GONE_DISTANCE) {
				c.goneAt = now;
			}
		}
		for (Iterator<Card> it = cards.iterator(); it.hasNext(); ) {
			Card c = it.next();
			if (c.goneAt >= 0 && now - c.goneAt > fade * 1e9f || c.cutAt >= 0 && now - c.cutAt > c.cutFor * 1e9f) {
				it.remove();
			}
		}
	}

	/**
	 * Which piece to spawn from: at most {@link #CATCH_UP} behind the {@code due} ones, and none at all for a line that
	 * is already over as it (re)starts (after a respawn, a world change or a layout switch), which would only flash.
	 */
	static int skipTo(int spawned, int due, boolean fresh, boolean over) {
		if (fresh && over) {
			return Math.max(spawned, due);
		}
		return due - spawned > CATCH_UP ? due - CATCH_UP : spawned;
	}

	/** Whether Elytra mode carried the words with you last frame (see {@link #glide}). */
	boolean carrying() {
		return gliding;
	}

	/** The Elytra mode setting: off, the words stay where they were put however you move. */
	void travel(boolean on) {
		travel = on;
	}

	/**
	 * Elytra mode: while you glide, ride or fly, every card moves and turns along with you, so the words travel with
	 * you instead of being left behind, and steering doesn't swing them (or the rest of the line) out of sight.
	 * {@code flying} is whether the game says you glide, ride or fly; with the setting off ({@link #travel}) nothing,
	 * not even a very fast move, carries the words. Call once a frame, before {@link #update}.
	 */
	void glide(Vec3 eye, float yaw, float pitch, boolean flying, long now) {
		float seconds = (now - lastGlide) / 1e9f;
		fastRun = fastRun(fastRun, lastEye, eye, seconds);
		boolean gliding = travel && travelling(flying, fastRun);
		Vec3 moved = carry(lastEye, eye, gliding, seconds);
		lastGlide = now;
		this.gliding = gliding;
		float turn = Mth.wrapDegrees(yaw - lastYaw), tilt = pitch - lastPitch;
		lastEye = eye;
		lastYaw = yaw;
		lastPitch = pitch;
		if (moved == Vec3.ZERO) {
			// Not carried this frame: the cards stay in the world while you move, so held-back tilt no longer fits them
			for (Card c : cards) {
				c.clip = clipKept(c.clip, moved);
			}
			if (sentence != null) {
				sentence.clip = clipKept(sentence.clip, moved);
			}
			if (last != null) {
				last.clip = clipKept(last.clip, moved);
			}
			return;
		}
		for (Card c : cards) {
			Vec3 offset = c.pos.add(moved).subtract(eye);
			c.pos = eye.add(swing(offset, turn, tilt, c.clip));
			c.clip = clipAfter(offset, c.clip, tilt);
			c.face += turn;
			// Where it slid out of a neighbour's way, and a knock, turn round with it: left in the world's directions they
			// would put it off its spot, back onto the word it moved away from
			c.slide = turned(c.slide, turn);
			c.slid = turned(c.slid, turn);
			c.knock = turned(c.knock, turn);
		}
		if (sentence != null) {
			sentence.swing(turn, tilt);
		}
		if (last != null) {
			last.swing(turn, tilt);
		}
	}

	/** A sideways offset (a slide, a knock) turned {@code turn} degrees round, the way {@link #swing} turns a card. */
	static Vec3 turned(Vec3 offset, float turn) {
		return turn == 0 || offset == Vec3.ZERO ? offset : offset.yRot(-turn * DEG);
	}

	/**
	 * The held-back tilt that still applies after a frame that carried the cards by {@code moved}: a frame that didn't
	 * carry them ({@link Vec3#ZERO}) lets you move away from where the tilt was cut, so it is dropped, and a later
	 * turn can't make a card jump by it.
	 */
	static float clipKept(float clip, Vec3 moved) {
		return moved == Vec3.ZERO ? 0 : clip;
	}

	/** An offset from the eye turned with the view: {@code turn} degrees round and {@code tilt} degrees down. */
	static Vec3 swing(Vec3 offset, float turn, float tilt) {
		return swing(offset, turn, tilt, 0);
	}

	/**
	 * The same, where {@code clip} degrees of earlier tilt were cut off at {@link #SWING_PITCH}: they count again, so
	 * tilting back gives the word its old height instead of leaving it higher or lower.
	 */
	static Vec3 swing(Vec3 offset, float turn, float tilt, float clip) {
		double far = offset.length();
		if (far < 1e-3 || turn == 0 && tilt == 0) {
			return offset;
		}
		return direction(yawOf(offset) + turn, shownPitch(pitchOf(offset), clip, tilt)).scale(far);
	}

	/** The pitch drawn after tilting by {@code tilt} from {@code pitch} with {@code clip} degrees held back: never past {@link #SWING_PITCH}. */
	static float shownPitch(float pitch, float clip, float tilt) {
		return Mth.clamp(pitch + clip + tilt, -SWING_PITCH, SWING_PITCH);
	}

	/** The degrees of tilt held back after {@link #swing(Vec3, float, float, float)}. */
	static float clipAfter(Vec3 offset, float clip, float tilt) {
		if (offset.length() < 1e-3) {
			return clip;
		}
		float pitch = pitchOf(offset);
		return pitch + clip + tilt - shownPitch(pitch, clip, tilt);
	}

	/** Whether the cards travel with you: while gliding or riding, or once you have been moving fast for {@link #FAST_FRAMES} frames. */
	static boolean travelling(boolean gliding, int fastRun) {
		return gliding || fastRun >= FAST_FRAMES;
	}

	/**
	 * The run of frames in a row that the camera moved faster than {@link #FAST_SPEED}: one more if this frame did, none
	 * if it was slower, or a teleport-sized jump (so a /tp never starts a run).
	 */
	static int fastRun(int run, @Nullable Vec3 last, Vec3 eye, float seconds) {
		if (last == null || seconds <= 0) {
			return 0;
		}
		float jump = Math.max(GLIDE_JUMP, GLIDE_SPEED * Mth.clamp(seconds, 0, 1));
		double d2 = last.distanceToSqr(eye);
		double fast = (double) FAST_SPEED * seconds;
		if (!Double.isFinite(d2)) {
			return 0; // a NaN position is no speed: it counts like a jump, not as a fast frame
		}
		return d2 > jump * jump || d2 <= fast * fast ? 0 : run + 1;
	}

	/**
	 * How far the cards move with you this frame, {@code seconds} after the last: as far as you went while travelling,
	 * otherwise nowhere.
	 */
	static Vec3 carry(@Nullable Vec3 last, Vec3 eye, boolean gliding, float seconds) {
		float jump = Math.max(GLIDE_JUMP, GLIDE_SPEED * Mth.clamp(seconds, 0, 1));
		double d2 = last == null ? 0 : last.distanceToSqr(eye);
		if (!gliding || last == null || !Double.isFinite(d2) || d2 > jump * jump) {
			return Vec3.ZERO; // also for a NaN position: nothing to carry
		}
		return eye.subtract(last);
	}

	/**
	 * How big a word is next to when it was being sung: full size until the next word comes, then smoothly a little
	 * smaller.
	 *
	 * @param since seconds since the next word came or the word was let go, or a negative number while it's sung
	 */
	static float settled(float since) {
		return settle(since, SETTLED);
	}

	/** How opaque a sung word is {@code since} seconds after the next word came; see {@link #settled}. */
	static float settledAlpha(float since) {
		return settle(since, SETTLED_ALPHA);
	}

	private static float settle(float since, float to) {
		if (since <= 0) {
			return 1;
		}
		float t = Mth.clamp(since / SETTLE_TIME, 0, 1);
		return 1 - (1 - to) * t * t * (3 - 2 * t);
	}

	/** The colour of a word sung {@code gone} seconds ago that was {@code rgb}: the same hue, paler. */
	static int paled(int rgb, float gone) {
		return mix(rgb, PALE_RGB, PALE * Mth.clamp(gone / PALE_TIME, 0, 1));
	}

	/** The colour a fading card keeps: whatever the words were when it started fading. */
	private static int keptColour(Card c, int rgb) {
		if (c.goneRgb < 0) {
			c.goneRgb = rgb;
		}
		return c.goneRgb;
	}

	/** Every card shown so far starts fading. */
	private void letGo(long now) {
		for (Card c : cards) {
			if (c.goneAt < 0 && !c.title) {
				c.goneAt = now;
			}
		}
	}

	/**
	 * Shows a new song's title as a big card above where the words gather, facing you, for a few seconds. It stays
	 * while the lyrics change over, and new words keep clear of it.
	 */
	void announce(String title, String artist, float fixedYaw, float fixedPitch, Camera camera, Font font, Style style, long now) {
		cards.removeIf(c -> c.title);
		String text = titleText(title, capitals);
		if (text.isEmpty()) {
			return;
		}
		float[] centre = centre(fixedYaw, fixedPitch, camera.yRot(), camera.xRot(), gliding);
		float pitch = Mth.clamp(centre[1] - LIFT - TITLE_LIFT, -75, 60);
		addTitle(text, centre[0], pitch, TITLE_SIZE, camera, font, style, now);
		// The artist, smaller, just under it
		String by = titleText(artist, capitals);
		if (!by.isEmpty()) {
			addTitle(by, centre[0], pitch + ARTIST_DROP, TITLE_SIZE * ARTIST_SIZE, camera, font, style, now);
		}
	}

	private void addTitle(String text, float yaw, float pitch, float size, Camera camera, Font font, Style style, long now) {
		Display d = Display.of(text);
		Vec3 dir = direction(yaw, pitch);
		float[] at = widths(d, font, style);
		float distance = TITLE_DISTANCE * away;
		Card c = new Card(d, at, camera.position().add(dir.scale(distance)), yaw, 0, 0,
				fitTitle(SIZE * size * away, at[at.length - 1], wordScale, distance), now);
		c.title = true;
		cards.add(c);
	}

	/** A title card's size, scaled down if its text of {@code textWidth} would look wider than {@link #TITLE_MAX_DEGREES}. */
	static float fitTitle(float cardSize, float textWidth, float wordScale, float distance) {
		return fitSize(cardSize, textWidth * cardSize * wordScale, distance, TITLE_MAX_DEGREES);
	}

	/** A title as shown: trimmed, in capitals if the words are, and cut short with an ellipsis if it's long. */
	static String titleText(String title, boolean capitals) {
		String text = title.strip().replaceAll("\\s+", " ");
		if (text.codePointCount(0, text.length()) > TITLE_MAX) {
			text = text.substring(0, text.offsetByCodePoints(0, TITLE_MAX - 1)).stripTrailing() + "\u2026";
		}
		return capitals ? capitals(text) : text;
	}

	/** How far along each letter of a card starts, in text units, and its whole width last. */
	private static float[] widths(Display d, Font font, Style style) {
		return prefixWidths(d.cps(), measurer(font, style));
	}

	/** Tests only: measures text in place of the font (null: the font does). */
	static java.util.function.@Nullable ToDoubleFunction<String> measureHook;

	/** For tests, which have no level: says whether a card at this spot is inside something solid. */
	static java.util.function.@Nullable Predicate<Vec3> buriedHook;

	private static java.util.function.ToDoubleFunction<String> measurer(Font font, Style style) {
		java.util.function.ToDoubleFunction<String> hook = measureHook;
		return hook != null ? hook : text -> LyricsFont.width(font, style, text);
	}

	/** The last entry of {@link #prefixWidths}, from one measure of the whole text on a short line. */
	static float wholeWidth(int[] cps, java.util.function.ToDoubleFunction<String> measure) {
		if (cps.length == 0) {
			return 0;
		}
		if (cps.length <= WHOLE_PREFIXES) {
			return (float) measure.applyAsDouble(new String(cps, 0, cps.length));
		}
		float at = 0;
		for (int v = 0; v < cps.length; v++) {
			at = at + (float) measure.applyAsDouble(new String(cps, v, 1));
		}
		return at;
	}

	/** The widths behind {@link #widths}, with the measuring of a piece of text passed in. */
	static float[] prefixWidths(int[] cps, java.util.function.ToDoubleFunction<String> measure) {
		float[] at = new float[cps.length + 1];
		for (int v = 1; v <= cps.length; v++) {
			// Each letter has its own advance, so a prefix is the sum of its letters; only a formatting code ('§')
			// changes the next letter's look, so short lines measure every prefix whole; a long line (lyrics are
			// untrusted text) always adds up, which may be slightly off on an absurdly long formatted one.
			at[v] = cps.length <= WHOLE_PREFIXES
					? (float) measure.applyAsDouble(new String(cps, 0, v))
					: at[v - 1] + (float) measure.applyAsDouble(new String(cps, v - 1, 1));
		}
		return at;
	}

	/** Lines up to this many letters measure every prefix whole, exactly as the game would; longer ones add up letters. */
	private static final int WHOLE_PREFIXES = 400;

	/**
	 * Yaw and pitch new words gather round: where you look, or with a fixed direction that way, a little above the
	 * horizon, so you can look about and find them where you left them. While Elytra mode carries the words, always
	 * where you look: the fixed spot would be off to the side or behind as soon as you fly another way.
	 */
	static float[] centre(float fixedYaw, float yaw, float pitch, boolean gliding) {
		return centre(fixedYaw, Float.NaN, yaw, pitch, gliding);
	}

	/** As above; a locked view also fixes the pitch, otherwise (NaN) it is the horizon. */
	static float[] centre(float fixedYaw, float fixedPitch, float yaw, float pitch, boolean gliding) {
		return gliding || Float.isNaN(fixedYaw) ? new float[] {yaw, pitch}
				: new float[] {fixedYaw, Float.isNaN(fixedPitch) ? 0 : fixedPitch};
	}

	private void spawn(int[] chunk, float[] centre, Camera camera, @Nullable ClientLevel level, Font font, Style style,
			long now) {
		String piece = new String(lineText.codePoints().toArray(), chunk[0], chunk[1] - chunk[0]);
		int slot = lineChunks.indexOf(chunk);
		Display d;
		float[] at;
		if (slot >= 0 && slot < lineAt.length && lineAt[slot] != null) {
			d = lineDisplay[slot];
			at = lineAt[slot];
		} else {
			d = Display.of(piece, lineRtl);
			at = widths(d, font, style);
		}
		if (flow && Float.isNaN(lineWidth)) {
			lineWidth = lineText == null ? 0 : wholeWidth(Display.of(lineText).cps(), measurer(font, style));
		}
		int letters = 0;
		for (int cp : d.cps()) {
			if (Character.isLetterOrDigit(cp)) {
				letters++;
			}
		}
		float roll = next();
		if (flow && slot >= 0 && slot < lineRuns.length && lineRuns[slot] != RUN_NONE) {
			// A piece of a stretch against the line is the size its room was measured for (see nominalHalf), so the
			// stretch's words fill that room and its last one doesn't stick out of it
			roll = 0.5f;
		}
		float size = SIZE * sizeFor(letters, roll) * (big && bigPiece >= 0 && slot == bigPiece ? BIG : 1);
		Vec3 eye = camera.position();
		// Where the other cards are, as seen from here: yaw, pitch and half width/height, in degrees
		List<float[]> taken = new ArrayList<>(cards.size());
		// Every older word still showing, faint ones too, in case the new one can't help landing on it
		List<Card> under = new ArrayList<>(cards.size());
		List<float[]> shown = new ArrayList<>(cards.size());
		// Words still lit: a flowing line's words step round only these, while fading ones make way for it
		List<float[]> lit = new ArrayList<>(cards.size());
		for (Card c : cards) {
			Vec3 to = c.pos.subtract(eye);
			// The words of the line being written are spaced by the line itself
			if (to.length() > 1e-3 && c.cutAt < 0) {
				float[] print = footprint(to, riseAfter((now - c.born) / 1e9f + LOOK_AHEAD, rise) * c.lift + leftUp(c, now, LOOK_AHEAD), c.at[c.at.length - 1] * c.size * wordScale / 2,
						5 * c.size * wordScale);
				under.add(c);
				shown.add(print);
				// The words of the line being written are spaced by the line itself; they are kept here only so a
				// restart of that line (see cutUnder) can clear them
				if (c.of == null || c.of != sentence) {
					if (c.goneAt < 0 || goneAlpha((now - c.goneAt) / 1e9f, fade) > 0.3f) {
						taken.add(print);
					}
					if (c.goneAt < 0) {
						lit.add(print);
					}
				}
			}
		}
		// Other players count as taken too, so in a fight the words don't cover the person you're aiming at
		List<float[]> avoid = new ArrayList<>(taken), firm = lit;
		if (avoidPlayers) {
			List<float[]> others = players(level, eye);
			avoid.addAll(others);
			firm.addAll(others);
		}
		if (clearMiddle) {
			// The middle of your view is for the game: new words go round it, never across it
			float[] middle = {centre[0], centre[1] + MIDDLE_DROP, MIDDLE_W, MIDDLE_H};
			avoid.add(middle);
			firm.add(middle);
		}
		placing = lineChunks.indexOf(chunk);
		Sentence before = sentence;
		float base = size;
		float[] spot;
		float halfW, halfH, shrink;
		Vec3 pos;
		seenHalf = halfView(ScreenLayer.tanHalfV(), sliderFov());
		// The line is laid out round centre, so the bottom of what it may use is judged from there, as rowFits does
		seenLow = lowestSeen(centre[1], seenHalf);
		seenSide = sideView(ScreenLayer.tanHalfH());
		for (boolean again = false;; again = true) {
			spot = flow ? follow(centre, at, base, avoid, firm, now) : scatter(centre, at, base, avoid);
			Vec3 dir = direction(spot[0], spot[1]);
			// Come in front of anything solid on the way out (a tree, a hill, a wall), so the card is neither inside a block
			// nor hidden behind one
			float meant = spot[2] * away;
			// A very long word would stretch right across the view and wipe out the other cards: it is scaled to fit
			size = fitSize(base, at[at.length - 1] * base * away * wordScale, meant, MAX_CARD_DEGREES);
			float w = at[at.length - 1] * size * away * wordScale / 2, h = 5 * size * away * wordScale;
			// A card brought in is shrunk to match, so it covers just the patch of sky it was given and no more
			float distance = clearReach(CLOSEST, meant, 1,
					out -> buried(level, eye.add(dir.scale(out)), dir, w * (float) out / meant, h * (float) out / meant));
			shrink = distance / meant;
			pos = eye.add(dir.scale(distance));
			halfW = w;
			halfH = h;
			if (buried(level, pos, dir, w * shrink, h * shrink)) {
				if (sentence != null && (sentence != before || sentence.pieces <= 1)) {
					// The line began with no clear place (a tunnel, a wall): it keeps lifting, it never starts over
					sentence.startedBuried = true;
				}
				if (!again && startsOverInsteadOfLift(flow, sentence, true)) {
					// Nowhere clear on the way in for a line already under way: lifting the card would put it on the
					// line's own earlier rows, so the line starts over at the top and this piece begins it
					startsOver(() -> "buried: pieces " + sentence.pieces + " top " + sentence.top + " rows " + sentence.rows);
					Sentence cut = sentence;
					clearLine(cards, cut, earlier, now);
					if (!earlier.contains(cut)) {
						earlier.add(cut);
					}
					sentence = null;
					last = null;
					continue;
				}
				// Nowhere clear on the way in (standing in a cave or by a hill): lift it out where it was meant to be
				Vec3 there = eye.add(dir.scale(meant));
				for (int up = 1; up <= LIFT_STEPS; up++) {
					if (!buried(level, there.add(0, up, 0), dir, w, h)) {
						pos = there.add(0, up, 0);
						shrink = 1;
						break;
					}
				}
			}
			break;
		}
		// Older words this one still lands on (a crowded sky, or the last line still fading) go at once, rather than
		// show through it for the whole fade
		float[] mine = footprint(pos.subtract(eye), 0, halfW * shrink, halfH * shrink);
		if (before != null && sentence != before && !earlier.contains(before)) {
			earlier.add(before);
		}
		cutUnder(under, shown, mine, sentence, earlier, now);
		// The Tilt setting scales every angle, so at 0 a card faces you square on
		float turn = tilt * (flow && sentence != null ? sentence.turn + (next() * 2 - 1) * SENTENCE_WOBBLE
				: Mth.lerp(next(), TURN_MIN, TURN_MAX) * (next() < 0.5f ? -1 : 1));
		float nod = tilt * (next() * 2 - 1) * NOD * (flow ? SENTENCE_NOD : 1), lean = tilt * (next() * 2 - 1) * (flow ? tipFor(at[at.length - 1] / 2) : LEAN);
		if (flow && sentence != null) {
			// Every word leans along the line where it sits, so the line reads as one curved piece
			lean += spot[3];
		}
		Card card = new Card(d, at, pos, spot[0] + turn, nod, lean, size * away * shrink, now);
		card.rtl = flow && lineRtl;
		card.of = flow ? sentence : null;
		card.lift = shrink;
		int nth = lineChunks.indexOf(chunk);
		card.along = nth < 0 || lineChunks.size() < 2 ? 0 : nth / (float) (lineChunks.size() - 1);
		cards.add(card);
		int kept = 0;
		for (int k = cards.size() - 1; k >= 0; k--) {
			Card c = cards.get(k);
			// A flowing line kept whole stays whole, however long, and goes all together when the next one starts
			if (c.cutAt < 0 && !c.title && !(flow && hold) && ++kept > maxCards) {
				// A lit card over the limit fades like a sung one; one already fading goes quickly
				if (c.goneAt < 0) {
					c.goneAt = now;
				} else {
					c.cutAt = now;
				}
			}
		}
	}

	/**
	 * A flowing line starts over: every word still up of this line (the sentence being left and the ones it already
	 * started over from) fades out at once, quickly, so nothing of it is left to be written over. Other lines' words and
	 * title cards are left alone.
	 */
	static void clearLine(List<Card> cards, @Nullable Sentence leaving, List<Sentence> earlier, long now) {
		for (Card c : cards) {
			if (c.title || c.of == null || c.cutAt >= 0 || c.of != leaving && !earlier.contains(c.of)) {
				continue;
			}
			c.cutAt = now;
			c.cutFor = CUT_OWN;
		}
	}

	/**
	 * Cuts the older words a new card at {@code mine} lands on. A word already fading goes on any touch, a lit one only
	 * when mostly covered. The line being written is left alone ({@code current}); but the words of the sentences this
	 * lyric line has already started over from ({@code earlier}: it filled the sky, or you turned away) go on any
	 * touch, and quickly, for the rest of the line: they are written over, so the rest of the line lands on cleared sky.
	 */
	private static void cutUnder(List<Card> under, List<float[]> shown, float[] mine, @Nullable Sentence current, List<Sentence> earlier, long now) {
		for (int k = 0; k < shown.size(); k++) {
			float[] o = shown.get(k);
			Card c = under.get(k);
			if (c.title || c.of != null && c.of == current) {
				continue;
			}
			boolean own = c.of != null && earlier.contains(c.of);
			float most = c.goneAt < 0 && !own ? OVERLAP : 1;
			if (Math.abs(Mth.wrapDegrees(mine[0] - o[0])) < (mine[2] + o[2]) * most && Math.abs(mine[1] - o[1]) < (mine[3] + o[3]) * most) {
				c.cutAt = now;
				if (own) {
					c.cutFor = CUT_OWN;
				}
			}
		}
	}

	/**
	 * Yaw, pitch and distance for a card in the scatter: the free spot, of a few tried, that overlaps least. Later
	 * tries reach a little further out, so a crowded sky spreads wider instead of piling words up.
	 */
	private float[] scatter(float[] centre, float[] at, float size, List<float[]> taken) {
		float bestYaw = 0, bestPitch = 0, bestDistance = NEAR, bestCost = Float.MAX_VALUE;
		int bestSlot = -1;
		for (int t = 0; t < TRIES && bestCost > 0; t++) {
			int slot = (int) (next() * SLOTS);
			if (slot == recent[0] || slot == recent[1]) {
				continue;
			}
			float out = 1 + REACH * t / (TRIES - 1);
			float yaw = centre[0] - SPOTS[slot][0] * out + (next() * 2 - 1) * WOBBLE_X;
			float pitch = Mth.clamp(centre[1] - LIFT - SPOTS[slot][1] * out + (next() * 2 - 1) * WOBBLE_Y, -75, 45);
			float distance = Mth.lerp(next(), NEAR, FAR);
			// A long word is shrunk to fit once placed, so it takes room for the size it ends up with
			float fit = fitted(size, at, wordScale, distance);
			// Up high a card is closer to the axis it turns around, so it takes up more yaw
			float halfW = halfAngle(at[at.length - 1] * fit * wordScale / 2, distance * Mth.cos(pitch * DEG)) + PAD_X;
			float halfH = halfAngle(5 * fit * wordScale, distance) + PAD_Y;
			// A spot out at the side of a narrow screen (a tall or square window) is brought in, so the word lands on it
			yaw = inSide(yaw, centre[0], halfW - PAD_X, seenSide);
			float cost = 0;
			for (float[] o : taken) {
				float w = halfW + o[2] - Math.abs(Mth.wrapDegrees(yaw - o[0])), h = halfH + o[3] - Math.abs(pitch - o[1]);
				if (w > 0 && h > 0) {
					cost += w * h;
				}
			}
			if (cost < bestCost) {
				bestCost = cost;
				bestSlot = slot;
				bestYaw = yaw;
				bestPitch = pitch;
				bestDistance = distance;
			}
		}
		if (bestSlot < 0) { // every try hit a recent spot (unlikely); take the middle
			bestYaw = centre[0];
			bestPitch = centre[1] - LIFT;
		}
		recent[1] = recent[0];
		recent[0] = bestSlot;
		return new float[] {bestYaw, bestPitch, bestDistance};
	}

	/**
	 * Yaw, pitch and distance for the next piece of a flowing line. A line's first piece picks where the line goes
	 * (a little above and left of the middle, where it overlaps the fading words least); the rest follow it,
	 * stepping round only what's in {@code firm} (lit words, players, the kept-clear middle) so a fading word never
	 * pushes one onto a row of its own.
	 */
	private float[] follow(float[] centre, float[] at, float size, List<float[]> taken, List<float[]> firm, long now) {
		return follow(centre, at, size, taken, firm, now, false);
	}

	private float[] follow(float[] centre, float[] at, float size, List<float[]> taken, List<float[]> firm, long now, boolean again) {
		float aside = clearMiddle ? SIDE_YAW + SENTENCE_SHIFT_X : 0;
		if (sentence != null && (sentence.lost(centre[0], centre[1] - LIFT, aside) || sentence.full(seenLow))) {
			startsOver(() -> sentence.full(seenLow) ? "full: top " + sentence.top + " rows " + sentence.rows : "lost: view " + centre[0] + "/" + centre[1] + " line " + sentence.left + "+" + sentence.width + " home " + sentence.home);
			// You turned away mid-line: the rest of it starts again where you look now, clear of what's there
			clearLine(cards, sentence, earlier, now);
			sentence = null;
			last = null;
		}
		if (sentence == null) {
			boolean verse = last != null && last.rtl == lineRtl && !last.lost(centre[0], centre[1] - LIFT, aside);
			float distance = verse ? last.distance : Mth.lerp(next(), SENTENCE_NEAR, SENTENCE_FAR), bestCost = Float.MAX_VALUE;
			// The whole line's box: as wide as it reads, or a full row, and as many rows deep as it will need
			float wide = (float) Math.toDegrees(lineWidth * SIZE * wordScale * LINE_SPREAD / distance);
			// Rows grow with the words, so a big Size still writes a few words across before starting the next row
			float room = rowSide(seenSide);
			float width = Math.min(Sentence.WIDTH * Mth.clamp(wordScale, 1, ROW_GROW), 2 * room);
			int rows = Math.max(1, Mth.ceil(wide / (width * ROW_FILL)));
			float row = 2 * halfAngle(5 * SIZE * SIZE_LONG * (big && bigPiece >= 0 ? BIG : 1) * wordScale, distance) + Sentence.ROW_GAP;
			// Under a line that sits over the kept-clear middle, the next one must still end above it
			float reach = centre[1] - LIFT + VERSE_REACH;
			if (verse && clearMiddle && Math.abs(Mth.wrapDegrees(last.left + last.width / 2 - centre[0])) < last.width / 2 + MIDDLE_W) {
				reach = Math.min(reach, centre[1] + MIDDLE_DROP - MIDDLE_H - PAD_Y);
			}
			if (verse && last.low + rows * row <= reach) {
				// The next line of a verse goes just under the last one, while that still keeps it above the middle
				bestCost = 0;
				sentence = last.under();
			}
			float halfW = Math.min(wide, width) / 2, halfH = rows * row / 2 + PAD_Y, below = (rows - 2) * row / 2;
			for (int t = 0; t < TRIES && bestCost > 0; t++) {
				// With the middle kept clear, a line goes just over it, or off to the left or the right of where you look
				float roll = next(), side = !clearMiddle || roll < OVER_MIDDLE ? 0 : roll < (1 + OVER_MIDDLE) / 2 ? -SIDE_YAW : SIDE_YAW;
				float left = onScreen(centre[0] + side - width / 2 + (next() * 2 - 1) * SENTENCE_SHIFT_X, width, centre[0], room);
				float top = centre[1] - LIFT + (next() * 2 - 1) * SENTENCE_SHIFT_Y;
				if (clearMiddle && side == 0) {
					// All its rows end above the kept-clear part
					top = Math.min(top, centre[1] + MIDDLE_DROP - MIDDLE_H - PAD_Y - rows * row);
				}
				float turn = Mth.lerp(next(), 0, SENTENCE_TURN) * (next() < 0.5f ? -1 : 1);
				float slope = tilt * curve * Mth.lerp(next(), SENTENCE_SLOPE_MIN, SENTENCE_SLOPE_MAX) * (next() < 0.5f ? -1 : 1);
				float bend = tilt * curve * Mth.lerp(next(), SENTENCE_BEND_MIN, SENTENCE_BEND_MAX) * (next() < SENTENCE_ARCH ? 1 : -1);
				// Its highest word, however the line climbs or bows, stays well under the top of the screen
				float drift = (float) Math.toDegrees(Math.atan(RISE_MAX * rise / distance));
				top = Mth.clamp(Math.max(top, belowTop(centre[1], width, slope, bend, row, drift)), Sentence.START_UP, Sentence.startDown(centre[1]));
				float cost = 0;
				for (float[] o : taken) {
					float w = halfW + o[2] - Math.abs(Mth.wrapDegrees(left + halfW - o[0])), h = halfH + o[3] - Math.abs(top + below - o[1]);
					if (w > 0 && h > 0) {
						cost += w * h;
					}
				}
				if (cost < bestCost) {
					bestCost = cost;
					sentence = new Sentence(left, top, distance, turn, slope, lineRtl, bend);
				}
			}
			sentence.width = width;
			sentence.rest = wide;
		}
		int run = placing >= 0 && placing < lineRuns.length ? lineRuns[placing] : RUN_NONE;
		float depth = (next() * 2 - 1) * SENTENCE_DEPTH;
		// A piece of a stretch against the line stands as far out as its room was measured at
		float distance = sentence.distance * (1 + (run == RUN_NONE ? depth : 0));
		size = fitted(size, at, wordScale, distance);
		float halfW = halfAngle(at[at.length - 1] * size * wordScale / 2, distance), halfH = halfAngle(5 * size * wordScale, distance);
		openRun(sentence, lineRuns, lineHalf, placing, wordScale, halfH);
		float[] mark = sentence.mark();
		int rowsBefore = sentence.rows;
		float[] at2 = placeClear(sentence, halfW, halfH, next() * SENTENCE_INDENT, step(sentence.pieces, next()), firm);
		if (!again && sentence.rows > rowsBefore && !rowFitsToItsEnd(sentence, at2[1] + halfH, centre, seenHalf)) {
			// This piece started a row whose words would run off the bottom of the screen: the line starts over at the top
			// instead, and this piece begins it
			startsOver(() -> "below: top " + sentence.top + " rows " + sentence.rows);
			sentence.back(mark);
			clearLine(cards, sentence, earlier, now);
			sentence = null;
			last = null;
			return follow(centre, at, size, taken, firm, now, true);
		}
		closeRun(sentence, run);
		return new float[] {at2[0], at2[1], distance, at2[2]};
	}

	/** Before piece {@code placing} is written: along the line, or as the first of a stretch against it, which takes room for all its pieces. */
	static void openRun(Sentence sentence, int[] runs, float[] half, int placing, float wordScale, float halfH) {
		int run = placing >= 0 && placing < runs.length ? runs[placing] : RUN_NONE;
		if (run == RUN_NONE) {
			sentence.forward();
		} else if (run == RUN_FIRST || sentence.run <= 0) {
			// The stretch is written from its far end back: it takes room for all its pieces as one, and each sits in it.
			// A line that started over in the middle of a stretch has none open: its first piece opens one for what is left.
			// One wider than a row is written a row's worth at a time, as wrapped text is, each part in its own order
			float[] halves = new float[runs.length - placing];
			int count = 0;
			float span = -Sentence.GAP * halfH;
			// Measured where it comes out widest, on this row or the next one down, and leaving room for a new row's indent
			float below = (Float.isInfinite(sentence.low) ? sentence.top : sentence.low) + Sentence.ROW_GAP + 2 * halfH + SENTENCE_BOB;
			// and wherever along the row its slope and bow take it
			float right = sentence.left + sentence.width;
			float pitch = Math.max(Math.abs(sentence.widest(sentence.left, right, sentence.top, halfH, sentence.top)),
					Math.abs(sentence.widest(sentence.left, right, below, halfH, below)));
			for (int k = placing; k < runs.length; k++) {
				float h = halfAngle(half[k] * wordScale, sentence.distance);
				span += 2 * Sentence.yawHalf(h, pitch) + Sentence.GAP * halfH;
				if (count > 0 && span > sentence.width - SENTENCE_INDENT) {
					break;
				}
				halves[count++] = h;
				if (runs[k] == RUN_LAST) {
					break;
				}
			}
			sentence.reverse(java.util.Arrays.copyOf(halves, count), (count - 1) * Sentence.GAP * halfH);
		}
	}

	/** After a piece of kind {@code run} is written: the end of a stretch, or of the part of it a row holds, goes back to writing along the line. */
	static void closeRun(Sentence sentence, int run) {
		if (run == RUN_LAST || run != RUN_NONE && --sentence.runLeft <= 0) {
			sentence.forward();
		}
	}

	/** How far either side of where you look, in degrees, a line's rows may reach and still be on screen (a 16:9 one shows 51 at FOV 70). */
	static final float VIEW_HALF = 40f;

	/** Degrees a word keeps from the left and right edges of the screen. */
	static final float SIDE_MARGIN = 2f;
	/** A flowing line's rows reach at least this far either side, however narrow the screen, so a row still holds a word or two. */
	static final float MIN_SIDE = 8f;

	/**
	 * How far either side of where you look, in degrees, a word may reach and still be on screen: half the screen's width,
	 * from the projection the world was drawn with ({@code tanHalfH}, see {@link ScreenLayer#tanHalfH}), less
	 * {@link #SIDE_MARGIN}. A tall or square window (or a low FOV) shows far less than a 16:9 screen. Before the first
	 * frame nothing is known: no limit then but the layout's own.
	 */
	static float sideView(float tanHalfH) {
		if (!(tanHalfH > 0) || Float.isInfinite(tanHalfH)) {
			return Float.POSITIVE_INFINITY;
		}
		return (float) Math.toDegrees(Math.atan(tanHalfH)) - SIDE_MARGIN;
	}

	/** How far either side a flowing line's rows may reach on a screen that shows {@code side} ({@link #sideView}): {@link #VIEW_HALF}, or less on a narrow one. */
	static float rowSide(float side) {
		return Math.max(MIN_SIDE, Math.min(VIEW_HALF, side));
	}

	/**
	 * A scattered word's yaw, brought in towards where you look ({@code centre}) as far as it takes for all of it, {@code halfW}
	 * either way, to stay within {@code side} ({@link #sideView}). One wider than the screen goes in the middle.
	 */
	static float inSide(float yaw, float centre, float halfW, float side) {
		float off = Mth.wrapDegrees(yaw - centre), most = side - halfW;
		if (Math.abs(off) <= most) {
			return yaw;
		}
		return centre + (most <= 0 ? 0 : Math.copySign(most, off));
	}

	/** How far above where you look, in degrees, a line's highest word may reach: under the top of the screen, with room to float up. */
	static final float VIEW_UP = 24f;

	/**
	 * The highest a line may start (smallest pitch) so that its top row, rising {@code slope} across {@code width} and
	 * bowing up by {@code bend} and floating up {@code drift} degrees as its words fade, keeps them under {@link #VIEW_UP}.
	 */
	static float belowTop(float pitch, float width, float slope, float bend, float row, float drift) {
		float up = Math.max(0, width * (float) Math.tan(slope * Mth.DEG_TO_RAD)) + Math.max(0, bend);
		// A line's top is the foot of its first row, so its words stand a whole row above it
		return pitch - VIEW_UP + up + row + drift;
	}

	/** Degrees a row's start keeps above the bottom of the screen, so the row itself isn't cut off by it. */
	static final float ROW_MARGIN = 2f;

	/** The FOV slider, or the default 70 where there is no game (tests). */
	private static double sliderFov() {
		Minecraft mc = Minecraft.getInstance();
		return mc == null ? 70 : mc.options.fov().get();
	}

	/** Half the screen's height in degrees: from the projection the world was drawn with (zoom, Speed and so on), else the FOV slider. */
	static float halfView(float tanHalfV, double sliderFovDeg) {
		double tan = tanHalfV > 0 && !Float.isInfinite(tanHalfV) ? tanHalfV : Math.tan(sliderFovDeg * DEG / 2);
		return (float) Math.toDegrees(Math.atan(tan));
	}

	/** The lowest pitch a row may start on while you look at {@code pitch}: the bottom of the screen less a margin, never past {@link Sentence#rowLimit}. */
	static float lowestSeen(float pitch, float halfView) {
		return Math.min(Sentence.rowLimit(pitch), pitch + halfView - ROW_MARGIN);
	}

	/** Share of the screen's half height, from the bottom, that the hotbar and a word's hang keep a row clear of. */
	static final float HOTBAR_SHARE = 0.12f;

	/**
	 * Whether a row whose words reach down to pitch {@code reach}, {@code yawOff} degrees to the side of where you look
	 * at {@code view}, still shows on a screen {@code halfView} degrees tall either way. A point at pitch p and yaw offset y
	 * sits at tan(p) / cos(y) up or down the screen, so a row's ends sit lower than its pitch says; the bottom
	 * {@link #HOTBAR_SHARE} is kept clear.
	 */
	static boolean rowFits(float reach, float yawOff, float view, float halfView) {
		float down = reach - view;
		if (down <= 0) {
			return true;
		}
		float cos = (float) Math.cos(Mth.clamp(Math.abs(yawOff), 0, 89) * DEG);
		return down < 89 && Math.tan(down * DEG) / cos <= Math.tan(halfView * DEG) * (1 - HOTBAR_SHARE);
	}

	/**
	 * Whether a row that has just started, whose first word's foot is at pitch {@code foot}, still shows at its far end. The
	 * row's widest yaw offset from the view centre, the most it drops across its width (a downward slope, a downward bow)
	 * and the most a word bobs down from the row are all counted, so the row is judged by where it can end up lowest, not where it starts.
	 */
	static boolean rowFitsToItsEnd(Sentence line, float foot, float[] centre, float halfView) {
		float wide = Math.max(Math.abs(Mth.wrapDegrees(line.left - centre[0])), Math.abs(Mth.wrapDegrees(line.left + line.width - centre[0])));
		float drop = line.width * Math.max(0, -(float) Math.tan(line.slope * DEG)) + Math.max(0, -line.bend) + SENTENCE_BOB;
		return rowFits(foot + drop, wide, centre[1], halfView);
	}

	/** Where a row starting at {@code left} must start instead so all of its {@code width} stays on a 16:9 screen. */
	static float onScreen(float left, float width, float centre) {
		return onScreen(left, width, centre, VIEW_HALF);
	}

	/** The same on a screen whose rows may reach {@code side} degrees either way ({@link #rowSide}). */
	static float onScreen(float left, float width, float centre, float side) {
		return Mth.clamp(left, centre - side, centre + side - Math.min(width, 2 * side));
	}

	/** How far below where a line would start, in degrees, the next lines of a verse may still go under it. */
	private static final float VERSE_REACH = 9f;
	/** With the middle kept clear: how far to the side a line's middle goes, in degrees. */
	private static final float SIDE_YAW = 30f;
	/** With the middle kept clear: how often a line tries the sky just over the middle first, as the reference does. */
	private static final float OVER_MIDDLE = 0.5f;
	/** The part of your view kept clear, as half width and half height in degrees round a spot a little below the crosshair. */
	private static final float MIDDLE_W = 15f, MIDDLE_H = 11f, MIDDLE_DROP = 2f;
	/** How much of a word shows right over the crosshair, and from what share of the way out it starts coming back. */
	private static final float MIDDLE_ALPHA = 0.12f, MIDDLE_SOFT = 0.35f;

	/** How far apart, in blocks, the spots on a card's face are that get checked for blocks. */
	private static final float BURY_STEP = 0.5f;

	/** How much two cards must cover each other, as a share of their summed half sizes, before the older one goes. */
	private static final float OVERLAP = 0.6f;

	/**
	 * How far out a card can go before it meets something: the last clear distance on the way from {@code from} to
	 * {@code to}, checked every {@code step}, or {@code from} when the way is blocked straight away.
	 */
	static float clearReach(float from, float to, float step, DoublePredicate blocked) {
		float d = from;
		while (d < to) {
			float next = Math.min(d + step, to);
			if (blocked.test(next)) {
				return d;
			}
			d = next;
		}
		return Math.max(from, to);
	}

	/**
	 * Whether any of a card's face (its middle, corners and edge middles, half sizes in blocks) is inside something
	 * solid. Grass, flowers and water don't count; they're see-through enough.
	 */
	static boolean buried(@Nullable ClientLevel level, Vec3 pos, Vec3 dir, float halfW, float halfH) {
		java.util.function.Predicate<Vec3> hook = buriedHook;
		if (hook != null) {
			return hook.test(pos);
		}
		if (level == null) {
			return false;
		}
		double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		Vec3 right = flat < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(-dir.z / flat, 0, dir.x / flat);
		// A tilted word dips a corner, so keep a little room under and over it
		halfH += BURY_STEP / 2;
		// Check every half block across the face, so a step or a fence post between the corners still counts
		int across = Math.max(1, Mth.ceil(halfW / BURY_STEP)), down = Math.max(1, Mth.ceil(halfH / BURY_STEP));
		for (int i = -across; i <= across; i++) {
			for (int j = -down; j <= down; j++) {
				BlockPos at = BlockPos.containing(pos.add(right.scale(i * halfW / across)).add(0, j * halfH / down, 0));
				if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Places a line's next piece where nothing in {@code taken} is: while older words are in the way it carries on
	 * along its row, or into the next one, past them. It never goes further, so a word can't drop far below its line;
	 * when there's no room it takes the spot it tried that's least in the way, and the older word there makes way.
	 */
	/**
	 * How far a flowing line's next piece sits off its row: up and down in turn, like a voice stepping between notes,
	 * by a random amount that never quite flattens out. Bigger is lower.
	 */
	static float step(int piece, float roll) {
		return (piece % 2 == 0 ? -1 : 1) * Mth.lerp(roll, SENTENCE_STEP, 1) * SENTENCE_BOB;
	}

	static float[] placeClear(Sentence line, float halfW, float halfH, float indent, float bob, List<float[]> taken) {
		if (line.run > 0 && !Float.isNaN(line.runAt)) {
			// The rest of a stretch against the line sit in the room its first piece found for all of it: moved on alone,
			// each try would take the next place back, out past the edge the line's rows start from and across the middle
			return line.place(halfW, halfH, indent, bob);
		}
		float[] mark = line.mark();
		int rows = line.rows, best = 0;
		float least = Float.MAX_VALUE;
		for (int t = 0; t < CLEAR_TRIES; t++) {
			float[] at = tryPlace(line, halfW, halfH, indent, bob);
			if (line.rows - rows > 1) {
				break;
			}
			// A stretch's first piece needs its whole room clear, as high and as low as its words step off the row
			float crowd = line.run > 0 ? crowding(line.roomMiddle(), (line.runHigh + line.runLow) / 2, line.run / 2, halfH + (line.runLow - line.runHigh) / 2, taken)
					: crowding(at[0], at[1], halfW, halfH, taken);
			if (crowd == 0) {
				return at;
			}
			if (crowd < least) {
				least = crowd;
				best = t;
			}
		}
		// Writing on from the mark up to the least crowded try lands on it again, since placing is steady
		line.back(mark);
		float[] at = null;
		for (int t = 0; t <= best; t++) {
			at = tryPlace(line, halfW, halfH, indent, bob);
		}
		return at;
	}

	/**
	 * One try at placing the next piece. A stretch's first piece tries again as its first, taking room for all of it
	 * further along the row: the whole stretch moves on as one piece does, its words still in their own order.
	 */
	private static float[] tryPlace(Sentence line, float halfW, float halfH, float indent, float bob) {
		if (line.run > 0) {
			line.runAt = Float.NaN;
		}
		return line.place(halfW, halfH, indent, bob);
	}

	/** Whether a card of this half size, in degrees, has room here away from the given footprints. */
	static boolean clear(float yaw, float pitch, float halfW, float halfH, List<float[]> taken) {
		return crowding(yaw, pitch, halfW, halfH, taken) == 0;
	}

	/** How much of the given footprints, in square degrees, a card of this half size here would cover (with a margin). */
	static float crowding(float yaw, float pitch, float halfW, float halfH, List<float[]> taken) {
		float crowd = 0;
		for (float[] o : taken) {
			float padW = o[2] + CLEAR_PAD, padH = o[3] + CLEAR_PAD;
			// How far the two overlap across and down: no more than the smaller of them is wide or tall
			float w = Math.min(halfW + padW - Math.abs(Mth.wrapDegrees(yaw - o[0])), 2 * Math.min(halfW, padW));
			float h = Math.min(halfH + padH - Math.abs(pitch - o[1]), 2 * Math.min(halfH, padH));
			if (w > 0 && h > 0) {
				crowd += w * h;
			}
		}
		return crowd;
	}

	/**
	 * Where a flowing line's pieces go, in degrees: each one just right of the last, a little up or down, and on to
	 * a new row below once the row is full, like handwriting across the sky.
	 */
	static final class Sentence {
		/** How wide a row may get before the next piece starts a new one, in degrees. */
		static final float WIDTH = 34f;
		/** This line's row width, in degrees: {@link #WIDTH} at the normal size, wider for bigger words so a row still holds a few. */
		float width = WIDTH;
		/** Space between pieces, in their own half heights (about a letter, like a written word space), and between rows, in degrees. */
		static final float GAP = 0.7f, ROW_GAP = 0.4f;
		/** The pitch (degrees) past which a piece's yaw span stops growing with it, see {@link #yawHalf}. */
		static final float YAW_PITCH = 50f;
		/** Extra room, in degrees, between the lines of a verse than between the rows of one line; never less than {@link #CLEAR_PAD}. */
		static final float VERSE_GAP = 0.6f;
		/**
		 * How much of its row's climb a word leans with, and the most it ever leans (degrees). Mostly upright words
		 * step up or down along a sloped or bowed row like a staircase, which reads calmer than words tipped along it.
		 */
		static final float LINE_LEAN = 0.3f, CURVE_LEAN = 4f;
		final float distance, turn;
		/** How far the line rises to the right, in degrees (negative falls). */
		final float slope;
		/** How far each row bows up in its middle, in degrees (negative sags), so the words follow a gentle curve. */
		final float bend;
		/** Written from the right, for Hebrew and Arabic: each row starts at its right end. */
		final boolean rtl;
		/**
		 * The row's left edge, the pitch of the line the row being written sits on (bigger is lower) and how far along
		 * from where rows start it's written so far.
		 */
		float left, top, cursor;
		/** The pitch the line's first row started on, which the view is judged against however far its rows have gone down. */
		float home;
		/** Pitch of the bottom of the rows above and of the lowest word on this row so far (before the slope). */
		float above = Float.NEGATIVE_INFINITY, low = Float.NEGATIVE_INFINITY;
		/**
		 * About how much of the line is still to be written, in degrees along the row. When the last row won't be full,
		 * it sits centred under the row above instead of hugging the left edge.
		 */
		float rest = Float.POSITIVE_INFINITY;
		/**
		 * The width of a stretch of pieces that reads against the line (0 when none is being written), and where the next of
		 * its pieces ends, counted along the row (NaN before the stretch's first piece has found its room).
		 */
		float run, runAt = Float.NaN;
		/** The stretch's pieces' half widths as seen square on, the room between them, and the pitch its room was measured at. */
		float[] runHalves = new float[0];
		float runGap, runPitch;
		/** The highest and lowest pitch the open stretch's words may sit at, stepping off its row: what its room is checked over. */
		float runHigh, runLow;
		/** How many of the open stretch's pieces are still to be written. */
		int runLeft;
		/** Whether the next piece starts a new row, as the first piece of a line written under another one does. */
		boolean newRow;
		/** How many rows have been started after the first. */
		int rows;
		/** How many pieces have been written. */
		int pieces;
		/** Whether the line's first piece had no clear place on the way in (a tunnel, a wall); such a line never starts over for it. */
		boolean startedBuried;

		Sentence(float left, float top, float distance, float turn) {
			this(left, top, distance, turn, 0, false);
		}

		Sentence(float left, float top, float distance, float turn, float slope) {
			this(left, top, distance, turn, slope, false);
		}

		Sentence(float left, float top, float distance, float turn, float slope, boolean rtl) {
			this(left, top, distance, turn, slope, rtl, 0);
		}

		Sentence(float left, float top, float distance, float turn, float slope, boolean rtl, float bend) {
			this.bend = bend;
			this.rtl = rtl;
			this.slope = slope;
			this.left = left;
			this.top = top;
			this.home = top;
			this.cursor = left;
			this.distance = distance;
			this.turn = turn;
		}

		/**
		 * Yaw and pitch of the next piece's middle, given its half size, and how far it leans (degrees) to follow the line
		 * there. Pieces sit on the row's line, so a small word lines up with the big one before it; {@code indent} and
		 * {@code bob} add a little chance.
		 */
		float[] place(float halfW, float halfH, float indent, float bob) {
			// A stretch against the line's direction takes room for all of it when its first piece comes, then each piece
			// sits in it from the far end back
			boolean first = run > 0 && Float.isNaN(runAt);
			// A row high or low in the sky takes more yaw for the same width than one at the horizon, so a piece's width
			// along the row is its yaw span there, or it'd be drawn over the piece beside it
			float steepNow = (float) Math.tan(slope * DEG), alongNow = Mth.clamp((cursor - left) / width, 0, 1);
			boolean inRun = run > 0 && !first;
			float pitchNow = inRun ? runPitch : top + bob - (cursor - left) * steepNow - bend * 4 * alongNow * (1 - alongNow);
			float wy = yawHalf(halfW, pitchNow);
			if (rtl && !inRun) {
				// It is drawn mirrored, so the row's slope and bow are those at its mirrored middle, not at the cursor
				for (int k = 0; k < 3; k++) {
					float x = 2 * left + width - (cursor + GAP * halfH + wy);
					float a = Mth.clamp((x - left) / width, 0, 1);
					pitchNow = top + bob - (x - left) * steepNow - bend * 4 * a * (1 - a);
					wy = yawHalf(halfW, pitchNow);
				}
			}
			if (first) {
				// Its pieces take the yaw they span where they are drawn, as every other piece of the row does: the row's
				// slope and bow lift or drop them across the room, so the room is measured where it comes out widest
				runPitch = pitchNow;
				for (int k = 0; k < 2; k++) {
					runPitch = widest(cursor + GAP * halfH, cursor + GAP * halfH + runWidth(runPitch), top + bob, halfH, runPitch);
				}
				run = runWidth(runPitch);
			}
			float whole = first ? run / 2 : wy;
			float yaw = cursor + GAP * halfH + whole;
			boolean wrap = !inRun && (newRow || cursor > left && yaw + whole > left + width);
			if (wrap) {
				// It starts the next row, which sits about a row lower: its width is the yaw span there
				float pitchWrap = low + ROW_GAP + 2 * halfH + bob;
				wy = yawHalf(halfW, pitchWrap);
				if (first) {
					// Where on the new row its room ends up depends on its width: measured over all of the row
					runPitch = widest(left, left + width, pitchWrap, halfH, pitchWrap);
					run = runWidth(runPitch);
				}
				whole = first ? run / 2 : wy;
				newRow = false;
				rows++;
				// A short last row is centred under the full one above it. The indent never takes a piece past the row's far
				// end, and one wider than the whole row (a big word low in the sky) sits on its middle, so it sticks out a
				// little at both ends instead of all of it at the far one
				float centre = (cursor - left - Math.max(rest, 2 * whole)) / 2, room = width - 2 * whole;
				yaw = left + (room < 0 ? room / 2 : Math.min(Math.max(indent, centre), room)) + whole;
			}
			if (run <= 0 || first) {
				rest -= yaw + whole - cursor;
				cursor = yaw + whole;
			}
			if (run > 0) {
				if (first) {
					runAt = cursor;
				}
				yaw = runAt - wy;
				// The room between its pieces is what the stretch's room was measured with
				runAt = yaw - wy - (runHalves.length > 1 ? runGap / (runHalves.length - 1) : GAP * halfH);
			}
			if (rtl) {
				// The same handwriting mirrored: from the row's right end leftwards
				yaw = 2 * left + width - yaw;
			}
			// Bigger pitch is lower, so a rising line's pitch shrinks the further right it gets; the bow is highest mid-row
			float along = Mth.clamp((yaw - left) / width, 0, 1), steep = (float) Math.tan(slope * DEG);
			float rise = (yaw - left) * steep + bend * 4 * along * (1 - along);
			float climb = steep + bend * 4 * (1 - 2 * along) / width;
			float lean = Mth.clamp(LINE_LEAN * (float) Math.toDegrees(Math.atan(climb)), -CURVE_LEAN, CURVE_LEAN);
			// Where the line climbs steeper than a word may lean, or bows under it, the word's ends stray off the line:
			// it needs that much more room above and below
			float reach = halfH + halfW * Math.abs(climb - (float) Math.tan(lean * DEG)) + 4 * Math.abs(bend) * halfW * halfW / (width * width);
			if (wrap) {
				// The new row starts below the lowest word of the last one, however its words bobbed
				above = low;
				top = low + ROW_GAP + halfH + reach;
			}
			// A word never reaches up into the row above, however big it is or however it bobs
			float middle = Math.max(top - halfH + bob, above + ROW_GAP + reach);
			if (first) {
				// The stretch's later words step up and down off the row as every piece does, the most a step goes either way
				runHigh = Math.max(top - halfH - SENTENCE_BOB, above + ROW_GAP + reach) - rise;
				runLow = Math.max(top - halfH + SENTENCE_BOB, above + ROW_GAP + reach) - rise;
			}
			low = Math.max(low, middle + reach);
			pieces++;
			return new float[] {yaw, middle - rise, lean};
		}

		/**
		 * Half the yaw a piece of this half width (degrees, as seen square on) spans at {@code pitch}: a card high or low in
		 * the sky covers more of the horizon's circle, by 1 / cos(pitch). Counted up to {@link #YAW_PITCH} only: nearer the
		 * poles a row can't be as wide as it's drawn and still fit on the screen.
		 */
		static float yawHalf(float halfW, float pitch) {
			float p = Math.min(Math.abs(pitch), YAW_PITCH) * DEG;
			return (float) Math.toDegrees(Math.atan(Math.tan(halfW * DEG) / Math.cos(p)));
		}

		/** Where writing has got to, for {@link #back}. */
		float[] mark() {
			return new float[] {top, cursor, above, low, rest, newRow ? 1 : 0, rows, pieces, run, runAt};
		}

		/** The next pieces, {@code span} wide together, read against the line's direction: written from the far end back. */
		void reverse(float[] halves, float gap) {
			runHalves = halves;
			runGap = gap;
			runLeft = halves.length;
			run = runWidth(0);
			runAt = Float.NaN;
		}

		/** The middle, as drawn (yaw, degrees), of the room the stretch being written took along its row. */
		float roomMiddle() {
			float middle = cursor - run / 2;
			return rtl ? 2 * left + width - middle : middle;
		}

		/**
		 * Of {@code pitch} and the pitches pieces are drawn at from {@code from} to {@code to} along this row (as written,
		 * before a right-to-left row is mirrored), the one furthest from the horizon, where a piece spans the most yaw.
		 * {@code foot} is the row's line before its slope and bow; a piece's middle sits {@code halfH} above it.
		 */
		float widest(float from, float to, float foot, float halfH, float pitch) {
			float steep = (float) Math.tan(slope * DEG);
			for (int k = 0; k <= 8; k++) {
				float x = Mth.lerp(k / 8f, from, to), yaw = rtl ? 2 * left + width - x : x;
				float along = Mth.clamp((yaw - left) / width, 0, 1), rise = (yaw - left) * steep + bend * 4 * along * (1 - along);
				float low = foot - rise, high = foot - halfH - rise;
				if (Math.abs(low) > Math.abs(pitch)) {
					pitch = low;
				}
				if (Math.abs(high) > Math.abs(pitch)) {
					pitch = high;
				}
			}
			return pitch;
		}

		/** How wide the stretch's pieces are together along a row at {@code pitch}, with the room between them. */
		private float runWidth(float pitch) {
			float span = runGap;
			for (float h : runHalves) {
				span += 2 * yawHalf(h, pitch);
			}
			return span;
		}

		/** Back to writing along the line's direction. */
		void forward() {
			run = 0;
			runAt = Float.NaN;
		}

		/** Goes back to where writing was at a {@link #mark}, as if the pieces since were never placed. */
		void back(float[] mark) {
			top = mark[0];
			cursor = mark[1];
			above = mark[2];
			low = mark[3];
			rest = mark[4];
			newRow = mark[5] != 0;
			rows = (int) mark[6];
			pieces = (int) mark[7];
			run = mark[8];
			runAt = mark[9];
		}

		/**
		 * The next line of a verse: written on from a new row under this one, with the same slant and bow so the two
		 * never cross, and centred under this one's last row when it's shorter.
		 */
		Sentence under() {
			Sentence next = new Sentence(left, top, distance, turn, slope, rtl, bend);
			next.width = width;
			next.home = home;
			next.cursor = cursor;
			next.above = above;
			next.low = low + VERSE_GAP;
			next.newRow = true;
			return next;
		}

		/**
		 * Whether the view (yaw, and pitch where a line would start) has moved so far from this line that its next word
		 * would be out of sight. Standing still or looking around a little keeps writing on the same line.
		 */
		boolean lost(float yaw, float pitch) {
			return lost(yaw, pitch, 0);
		}

		/** The same, for a line placed up to {@code aside} degrees to the side of where you looked. */
		boolean lost(float yaw, float pitch, float aside) {
			float off = Mth.wrapDegrees(yaw - (left + width / 2));
			return Math.abs(off) > width / 2 + LOST_YAW + aside || Math.abs(Mth.clamp(pitch, START_UP, startDown(pitch + LIFT)) - home) > LOST_PITCH;
		}

		/** The highest and lowest pitch a line starts on, in degrees (a view past them is judged as if at them). */
		static final float START_UP = -70f, START_DOWN = 40f;
		/** The lowest pitch a row may start on; a line that would go further down starts over at the top instead. */
		static final float ROW_LIMIT = 50f;
		/** Looking down, a line starts this far above where you look, and no lower than {@link #START_DOWN_MAX}. */
		static final float DOWN_LEAD = 15f, START_DOWN_MAX = 65f;
		/** Looking down, its rows may go this far above the bottom of where you look, and never past {@link #ROW_CAP}: near 90 they bunch up round the point under you. */
		static final float LIMIT_LEAD = 5f, ROW_CAP = 80f;

		/** The lowest pitch a line starts on while you look at {@code view}: {@link #START_DOWN}, or a little above where you look when that is further down. */
		static float startDown(float view) {
			return Mth.clamp(view - DOWN_LEAD, START_DOWN, START_DOWN_MAX);
		}

		/** The lowest pitch a row may start on while you look at {@code view}: {@link #ROW_LIMIT}, or a little above the bottom of where you look when that is further down. */
		static float rowLimit(float view) {
			return Mth.clamp(view - LIMIT_LEAD, ROW_LIMIT, ROW_CAP);
		}

		/** Whether the rows have gone as far down as they may, so the next piece starts the line over higher up. */
		boolean full() {
			return full(ROW_LIMIT);
		}

		/**
		 * The same, also when the next row would start below {@code low}, the bottom of what the screen shows. The first
		 * row never counts: a line that can't fit even one row stays where it is.
		 */
		boolean full(float low) {
			return top > Math.max(ROW_LIMIT, low) || rows > 0 && top > low;
		}

		/** How far past a line's edge (yaw) or from its row (pitch), in degrees, you can look before it starts over. */
		static final float LOST_YAW = 22f, LOST_PITCH = 30f;

		/** Degrees of tilt cut off at {@link #SWING_PITCH}, given back when you tilt the other way. */
		float clip;

		/** Turns the rest of the line along with you (see {@link WorldWords#glide}). */
		void swing(float turn, float tilt) {
			left += turn;
			cursor += turn;
			// A phrase against the line that is half written keeps the rest of its room beside its first word (NaN, no
			// room taken yet, stays NaN)
			runAt += turn;
			float shown = shownPitch(top, clip, tilt);
			float moved = shown - top;
			clip = top + clip + tilt - shown;
			top += moved;
			runPitch += moved;
			above += moved;
			low += moved;
			home += moved;
		}
	}

	/**
	 * How much of a word shows where it sits in your view: barely at all over the middle, where you need to see the
	 * game, and fully once it's off to the side. {@code to} is the word's centre from the eye, the halves in blocks.
	 */
	static float middleFade(Vector3f to, float halfW, float halfH, float yaw, float pitch) {
		Vec3 v = new Vec3(to.x, to.y, to.z);
		double far = v.length(), flat = Math.sqrt(v.x * v.x + v.z * v.z);
		if (far < 1e-3) {
			return 1;
		}
		// How far the word's nearest edge is from the crosshair, as a share of the middle's size
		float x = (Math.abs(Mth.wrapDegrees(yawOf(v) - yaw)) - halfAngle(halfW, flat)) / MIDDLE_W;
		float y = (Math.abs(pitchOf(v) - pitch - MIDDLE_DROP) - halfAngle(halfH, far)) / MIDDLE_H;
		float out = Mth.clamp((Math.max(x, y) - MIDDLE_SOFT) / (1 - MIDDLE_SOFT), 0, 1);
		return Mth.lerp(out * out * (3 - 2 * out), MIDDLE_ALPHA, 1);
	}

	static Vec3 direction(float yaw, float pitch) {
		return new Vec3(-Mth.sin(yaw * DEG) * Mth.cos(pitch * DEG), -Mth.sin(pitch * DEG), Mth.cos(yaw * DEG) * Mth.cos(pitch * DEG));
	}

	private static float yawOf(Vec3 v) {
		return (float) Math.toDegrees(Math.atan2(-v.x, v.z));
	}

	private static float pitchOf(Vec3 v) {
		return (float) -Math.toDegrees(Math.asin(Mth.clamp(v.y / v.length(), -1, 1)));
	}

	/** The most a piece of a flowing line may tip either way (degrees), given half its width in letter pixels. */
	static float tipFor(float halfWidth) {
		float most = LEAN * SENTENCE_LEAN;
		return halfWidth <= TIP_DROP ? most : Math.min(most, (float) Math.toDegrees(Math.asin(TIP_DROP / halfWidth)));
	}

	/**
	 * The patch of sky a card takes up, as yaw, pitch and half width/height in degrees, for a card at {@code to} from
	 * the eye that has floated up by {@code risen}. Every card floats up at the same pace until it stops, so next to
	 * a new card an older one ends up back where it started; the patch covers both ends of that, or the new card
	 * would be placed where the older one is right now and the two would cross.
	 * The width is in yaw, which a card high up in the sky spans more of than one on the horizon.
	 */
	static float[] footprint(Vec3 to, float risen, float halfWidth, float halfHeight) {
		Vec3 now = to.add(0, risen, 0);
		float from = pitchOf(to), at = pitchOf(now);
		double far = to.length();
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		return new float[] {yawOf(to), (from + at) / 2, halfAngle(halfWidth, flat), halfAngle(halfHeight, far) + Math.abs(from - at) / 2};
	}

	/** Scratch lists for {@link #separate}: filled and read inside one call, never kept (render thread only). */
	private final List<float[]> nudgeSeen = new ArrayList<>();
	private final List<Card> nudgeOrder = new ArrayList<>();

	/**
	 * Finds words that sit on top of each other as seen from here and sends the newer one to the nearest clear side,
	 * gliding there over a moment rather than jumping.
	 */
	private void separate(Vec3 eye, long now) {
		float dt = lastNudge == 0 ? 0 : Mth.clamp((now - lastNudge) / 1e9f, 0, 0.1f);
		lastNudge = now;
		// Players come first and never move, so a word in front of someone glides off them, even as they walk about
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc == null ? null : mc.level; // no game in the tests
		List<float[]> people = avoidPlayers ? players(level, eye) : List.of();
		List<float[]> seen = nudgeSeen;
		seen.clear();
		// Words still being sung go first, so a fading word glides out of a new one's way instead of shoving it up the sky
		List<Card> order = nudgeOrder;
		order.clear();
		for (Card c : cards) {
			if (c.goneAt < 0) {
				order.add(c);
			}
		}
		for (Card c : cards) {
			if (c.goneAt >= 0) {
				order.add(c);
			}
		}
		for (Card c : order) {
			// Fading words still take part until they're almost gone, so nothing glides onto them and they glide too; but
			// a word being cut is leaving to make room for a new one, so it mustn't shove that one off up the sky
			if (c.fellAt >= 0 || c.cutAt >= 0 || c.goneAt >= 0 && goneAlpha((now - c.goneAt) / 1e9f, fade) < 0.1f) {
				continue;
			}
			Vec3 to = c.pos.add(c.slide).subtract(eye);
			if (to.length() < 1e-3) {
				continue;
			}
			float halfW = c.display.cps().length == 0 ? 0 : c.at[c.at.length - 1] * c.size * wordScale / 2;
			float[] mine = footprint(to, riseAfter((now - c.born) / 1e9f, rise) * c.lift + leftUp(c, now, 0), halfW, 5 * c.size * wordScale);
			for (float[] o : join(people, nudge ? seen : List.of())) {
				float[] move = push(o, mine);
				if (move[0] != 0 || move[1] != 0) {
					double far = to.length(), flat = Math.sqrt(to.x * to.x + to.z * to.z);
					// Sideways is across the line of sight, up is straight up; both as far as the angles ask at that distance
					Vec3 side = sideStep(to, move[0]);
					Vec3 up = new Vec3(0, far * Math.tan(-move[1] * DEG), 0);
					Vec3 dir = to.normalize();
					Vec3 here = c.pos.add(c.slide);
					if (stillBlocked(c.blockedAt, c.blockedPos, c.blockedEye, now, here, eye)) {
						continue; // boxed in a moment ago and nothing moved: skip the block lookups
					}
					float halfBlocks = c.at[c.at.length - 1] * c.size * wordScale / 2, tall = 5 * c.size * wordScale;
					if (buried(level, c.pos.add(c.slide).add(side).add(up), dir, halfBlocks, tall)) {
						// That way is into the ground or a wall: go over the top instead, or stay put
						up = new Vec3(0, Math.abs(up.y) + Math.abs(far * Math.tan(move[0] * DEG)), 0);
						side = Vec3.ZERO;
						if (buried(level, c.pos.add(c.slide).add(up), dir, halfBlocks, tall)) {
							c.blockedAt = now;
							c.blockedPos = here;
							c.blockedEye = eye;
							continue;
						}
						move[0] = 0;
						move[1] = -halfAngle((float) up.y, far);
					}
					c.slide = c.slide.add(side).add(up);
					mine[0] += move[0];
					mine[1] += move[1];
				}
			}
			seen.add(mine);
		}
		float ease = glideEase(dt, Calm.motion());
		for (Card c : cards) {
			if (!c.slid.equals(c.slide)) { // there already (most never move): it would come out the same
				c.slid = c.slid.add(c.slide.subtract(c.slid).scale(ease));
			}
		}
	}

	/**
	 * Whether a word that found no free way earlier can still be taken as boxed in: it was recent, and neither the
	 * word nor the camera has moved more than a block since. Saves the 65 to 175 block lookups each try costs.
	 */
	static boolean stillBlocked(long blockedAt, Vec3 blockedPos, Vec3 blockedEye, long now, Vec3 pos, Vec3 eye) {
		return blockedAt >= 0 && now - blockedAt < BLOCKED_NS
				&& pos.distanceToSqr(blockedPos) < 1 && eye.distanceToSqr(blockedEye) < 1;
	}

	private static final long BLOCKED_NS = 500_000_000L;

	/** Knocks the nearest word under the crosshair away from you; it spins, squashes and springs back. */
	private void knockAt(Vec3 eye, Vec3 look, long now) {
		float yaw = yawOf(look), pitch = pitchOf(look);
		Card best = null;
		double bestFar = Double.MAX_VALUE;
		for (Card c : cards) {
			if (c.fellAt >= 0 || c.cutAt >= 0 || c.goneAt >= 0 && goneAlpha((now - c.goneAt) / 1e9f, fade) < 0.2f) {
				continue;
			}
			Vec3 to = c.pos.add(c.slid).add(0, riseAfter((now - c.born) / 1e9f, rise) * c.lift + leftUp(c, now, 0), 0).subtract(eye);
			float halfW = c.display.cps().length == 0 ? 0 : c.at[c.at.length - 1] * c.size * wordScale / 2;
			float[] box = footprint(to, 0, halfW + KNOCK_MARGIN, 5 * c.size * wordScale + KNOCK_MARGIN);
			if (Math.abs(Mth.wrapDegrees(yaw - box[0])) < box[2] && Math.abs(pitch - box[1]) < box[3] && to.length() < bestFar) {
				best = c;
				bestFar = to.length();
			}
		}
		if (best != null) {
			best.hitAt = now;
			// It flies up and off to one side, a bit further the further away it is, so the hit reads from where you stand
			Vec3 to = best.pos.add(best.slid).subtract(eye);
			Vec3 away = to.normalize(), side = new Vec3(-away.z, 0, away.x).normalize();
			best.knock = away.scale(0.3).add(side.scale((next() < 0.5f ? -1 : 1) * Mth.lerp(next(), 0.5f, 0.9f))).add(0, 0.7, 0)
					.scale(Math.max(2.5, to.length() * KNOCK_DISTANCE));
			best.spin = (next() < 0.5f ? -1 : 1) * Mth.lerp(next(), KNOCK_SPIN * 0.6f, KNOCK_SPIN);
		}
	}

	/** How far out a knocked word is, 0 to 1: it flies off fast, then floats back to where it was. */
	static float knockOut(float seconds) {
		if (seconds <= 0 || seconds >= KNOCK_TIME) {
			return 0;
		}
		float t = seconds / KNOCK_PEAK;
		return t * (float) Math.exp(1 - t);
	}

	/** A knocked word's wobble, -1 to 1, dying away. */
	static float knockWobble(float seconds) {
		if (seconds <= 0 || seconds >= KNOCK_TIME) {
			return 0;
		}
		return (float) (Math.exp(-KNOCK_DAMP * seconds) * Math.sin(KNOCK_RATE * seconds));
	}

	private static List<float[]> join(List<float[]> a, List<float[]> b) {
		if (a.isEmpty()) {
			return b;
		}
		List<float[]> both = new ArrayList<>(a);
		both.addAll(b);
		return both;
	}

	/**
	 * The sideways offset (across the line of sight, level) that turns a word at {@code to} by {@code yawDeg} of yaw, in
	 * the same direction yaw counts (yaw is atan2(-x, z), so a positive turn goes to -x when looking down +z).
	 */
	static Vec3 sideStep(Vec3 to, float yawDeg) {
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		if (flat < 1e-6) {
			return Vec3.ZERO;
		}
		return new Vec3(-to.z, 0, to.x).normalize().scale(flat * Math.tan(yawDeg * DEG));
	}

	/**
	 * How far to move {@code b} (yaw, pitch, half width, half height, in degrees) so it no longer covers {@code a}: along
	 * whichever way is shorter, with a little room to spare, or nothing when they're already apart.
	 */
	static float[] push(float[] a, float[] b) {
		float dx = Mth.wrapDegrees(b[0] - a[0]), dy = b[1] - a[1];
		float w = a[2] + b[2] - Math.abs(dx), h = a[3] + b[3] - Math.abs(dy);
		if (w <= 0 || h <= 0) {
			// Apart already: the answer for most pairs, so no array per pair. Callers read it and only write to a result
			// that moves something (see separate), so sharing the one zero is safe.
			return NO_PUSH;
		}
		// Words sit in rows, so going up or down past a short word is often the smaller step than going round it
		if (h <= w) {
			return new float[] {0, (dy < 0 ? -1 : 1) * (h + SLIDE_ROOM)};
		}
		return new float[] {(dx < 0 ? -1 : 1) * (w + SLIDE_ROOM), 0};
	}

	/** The shared answer when two words are apart. Read-only: never write into it. */
	private static final float[] NO_PUSH = {0, 0};

	/** Spare degrees left between two words a slide has parted, and how quickly (per second) a word glides there. */
	static final float SLIDE_ROOM = 0.6f, SLIDE_SPEED = 6f;

	static float halfAngle(float half, double distance) {
		return (float) Math.toDegrees(Math.atan2(half, distance));
	}

	/**
	 * How many logical letters of a card are showing: all of them, or while it types in, a share by its age. The count is
	 * of logical letters (the highest index plus one), not drawn glyphs: shaping drops marks and merges lam-alef, so
	 * there can be fewer glyphs than letters, and counting glyphs would leave the last letters never drawn.
	 */
	static int shownLetters(int[] logical, boolean instant, float age) {
		int letters = 0;
		for (int l : logical) {
			letters = Math.max(letters, l + 1);
		}
		return instant ? letters : Mth.ceil(letters * Math.min(1, age / TYPE));
	}

	/** A number in [0, 1); cards only need to look scattered, not be reproducible. */
	private float next() {
		rng = rng * 6364136223846793005L + 1442695040888963407L;
		return (rng >>> 40) / (float) (1 << 24);
	}

	/**
	 * Draws the cards around the camera.
	 *
	 * @param rgb   the lyrics colour
	 * @param alpha overall opacity
	 * @param glow  glow strength from the settings, 0 for none
	 * @param day   how much it's daytime, 0 to 1; by day the shadow and glow go darker to stand out from the sky
	 * @param scale the size setting
	 * @param light fewer glow copies, of the same total strength, to save frames
	 */
	void render(PoseStack pose, SubmitNodeCollector out, Camera camera, Font.DisplayMode mode, Style style, int rgb, float alpha,
			float glow, float day, float scale, boolean light, long now) {
		int copies = light ? GLOW_LIGHT : GLOW_COPIES;
		if (shadowStyle == null || style != shadowBase) {
			shadowBase = style;
			shadowStyle = style.withColor(SHADOW_RGB);
		}
		Style shadowStyle = this.shadowStyle;
		Vec3 eye = camera.position();
		if (nudge || avoidPlayers) {
			separate(eye, now);
		}
		Vec3 look = direction(camera.yRot(), camera.xRot());
		if (swung) {
			swung = false;
			knockAt(eye, look, now);
		}
		// Minecraft's Distortion Effects scale the motion and Hide Lightning Flashes takes the word flashes out
		float still = Calm.motion();
		boolean noFlash = Calm.hideFlashes();
		Card sung = null;
		for (Card c : cards) {
			if (c.goneAt < 0 && c.cutAt < 0 && !c.title) {
				sung = c;
			}
		}
		for (int i = 0; i < cards.size(); i++) {
			Card c = cards.get(i);
			float age = (now - c.born) / 1e9f;
			float gone = c.goneAt < 0 ? 0 : (now - c.goneAt) / 1e9f;
			boolean popping = arrival == WorldArrival.POP;
			float fadeIn = arrival == WorldArrival.FADE ? SOFT_FADE : FADE_IN;
			// A dissolving card stays bright: its letters go one by one instead
			boolean melting = c.goneAt >= 0 && leave == WorldLeave.DISSOLVE;
			float wane = c.goneAt < 0 || melting ? 1 : goneAlpha(gone, fade);
			float melt = melting ? Mth.clamp(gone / fade, 0, 1) : -1;
			float a = alpha * Mth.clamp(age / fadeIn, 0, 1) * wane
					* (c.cutAt < 0 ? 1 : cutAlpha((now - c.cutAt) / 1e9f, c.cutFor));
			float fell = c.fellAt < 0 ? 0 : (now - c.fellAt) / 1e9f;
			if (c.fellAt >= 0) {
				a *= goneAlpha(fell, FALL_TIME);
			}
			if (a < 0.03f) {
				continue;
			}
			float x = (float) (c.pos.x + c.slid.x - eye.x), z = (float) (c.pos.z + c.slid.z - eye.z);
			float sway = swayAngle(age, c.born);
			float rocking = tilt * still, flashing = Calm.flash(noFlash, 1);
			float y = (float) (c.pos.y + c.slid.y - eye.y) + riseAfter(age, rise) * c.lift + leftUp(c, now, 0) + rocking * SWAY_BOB * Mth.sin(sway) - fallDrop(fell, still);
			float hit = c.hitAt < 0 ? -1 : (now - c.hitAt) / 1e9f, spin = 0, squash = 1, shove = knockOut(hit) * still;
			if (shove > 0) {
				x += (float) c.knock.x * shove;
				y += (float) c.knock.y * shove;
				z += (float) c.knock.z * shove;
				spin = c.spin * knockWobble(hit) * still;
				squash = 1 + KNOCK_SQUASH * knockWobble(hit * 1.6f) * still;
			}
			int n = c.display.cps().length;
			// A flowing line's words don't pop: each one glides into its place in the sentence, whole, as it fades up
			float enter = Calm.around(arrival == WorldArrival.ZOOM ? zoomIn(age) : popping && !flow ? pop(age) : 1, 1, still);
			float replaced = i + 1 < cards.size() ? (now - cards.get(i + 1).born) / 1e9f : -1;
			float since = c == sung || c.title ? -1 : replaced >= 0 ? replaced : c.goneAt >= 0 ? gone : -1;
			float grow = c.size * scale * enter * settled(since)
					* (c.goneAt < 0 || leave != WorldLeave.SHRINK ? 1 : leaveSize(gone, fade, still)) * squash;
			a *= settledAlpha(since);
			Vector3f centre = CENTRE.set(x, y, z);
			float blown = blownBack(leave, gone, fade, c.rtl, still);
			if (!showing(centre, c.at[n] * grow / 2, grow, look, blown)) {
				continue;
			}
			if (clearMiddle) {
				// Whatever drifts over the crosshair as you look about goes see-through, so the game always shows
				a *= middleFade(centre, c.at[n] * grow / 2, 5 * grow, camera.yRot(), camera.xRot());
				if (a < 0.03f) {
					continue;
				}
			}
			if (c.goneAt < 0) {
				LyricsInView.check(centre, c.at[n] * grow / 2, 5 * grow);
			}
			// A card seen from behind turns around, so its words never read mirrored
			float face = seenFromBehind(x, z, c.face) ? c.face + 180 : c.face;
			int shown = shownLetters(c.display.logical(), flow || !popping, age);
			float typed = arrival == WorldArrival.TYPE ? age : -1;
			int own = c.title ? rgb : toned(rgb, toneRgb, c.along);
			int colour = c.goneAt < 0 ? mix(own, 0xFFFFFF, flash(age) * flashing)
					: paled(keptColour(c, own), gone);
			if (shove > 0) {
				colour = mix(colour, 0xFFFFFF, (float) Math.exp(-KNOCK_FLASH * hit) * flashing);
			}
			pose.pushPose();
			pose.translate(x, y, z);
			pose.mulPose(TURN.rotationY(Mth.PI - face * DEG).rotateX(c.nod * DEG)
					.rotateZ((c.lean + rocking * SWAY_LEAN * Mth.cos(sway) + ((c.born & 1) == 0 ? FALL_SPIN : -FALL_SPIN) * still * fell + spin
					- BLOW_TIP * blown / BLOW) * DEG));
			pose.scale(grow, -grow, grow);
			float x0 = -c.at[n] / 2f - (flow && popping ? (c.rtl ? -1 : 1) * slideIn(age, still) : 0) + blown,
					y0 = -CAP_MIDDLE - (arrival == WorldArrival.DROP ? dropIn(age) * still : 0);
			if (!soft) {
				// A fading card loses its shadow sooner, so it melts into the sky instead of leaving a dark smudge
				float shade = a * shadowOpacity(day) * wane;
				draw(pose, out, c, shown, melt, typed, SHADOW, shadowStyle, shade, x0 + SHADOW_X, y0 + SHADOW_Y, SHADOW_Z, mode);
				float edge = a * edgeOpacity(day) * wane;
				for (int k = 0, n4 = outlineCopies(light, edge); k < n4; k++) {
					draw(pose, out, c, shown, melt, typed, SHADOW, shadowStyle, edge, x0 + (k < 2 ? -OUTLINE : OUTLINE),
							y0 + (k % 2 == 0 ? -OUTLINE : OUTLINE), SHADOW_Z, mode);
				}
			}
			// The glow comes up just behind the letters, so a new word lights up as it lands
			float ring = a * Mth.clamp(age / fadeIn, 0, 1) * GLOW_ALPHA * glow * glowFor(c == sung || c.title, c.goneAt < 0 ? -1 : gone, replaced, light) / 2 * GLOW_COPIES / copies;
			int halo = mix(glowRgb < 0 ? neon(colour) : glowRgb, 0x000000, HALO_DAY * day);
			Style haloStyle = copies > 0 && ring * 255 >= 4 ? c.haloStyle(style, halo) : null;
			for (int k = 0; k < copies && ring * 255 >= 4; k++) {
				float turn = k * Mth.TWO_PI / copies + 0.5f;
				draw(pose, out, c, shown, melt, typed, HALO, haloStyle, ring, x0 + Mth.cos(turn) * GLOW_RADIUS, y0 + Mth.sin(turn) * GLOW_RADIUS,
						GLOW_Z, mode);
			}
			draw(pose, out, c, shown, melt, typed, FACE, c.faceStyle(style, colour), a, x0, y0, FACE_Z, mode);
			pose.popPose();
		}
	}

	/**
	 * Whether any of a card could be on screen this frame, checked against the view the world was drawn with: a ball
	 * round it, big enough for the card however it's turned plus its glow and shadow. So cards just off the edge cost
	 * nothing, and a wide field of view doesn't cut off ones still in sight. Before the first frame, roughly whether
	 * it's ahead.
	 *
	 * @param halfW half the card's width, in blocks
	 * @param grow  blocks per text unit
	 */
	static boolean showing(Vector3f centre, float halfW, float grow, Vec3 look) {
		return showing(centre, halfW, grow, look, 0);
	}

	/** The ball's radius round a card's centre, in blocks; {@code blown} is how far a Blow leave has shifted it, in text units. */
	static float cullRadius(float halfW, float grow, float blown) {
		float halfH = HALF_HEIGHT * grow;
		return Mth.sqrt(halfW * halfW + halfH * halfH) + EDGE * grow + Math.abs(blown) * grow;
	}

	/** {@link #showing(Vector3f, float, float, Vec3)} for a card the Blow leave has shifted {@code blown} text units sideways. */
	static boolean showing(Vector3f centre, float halfW, float grow, Vec3 look, float blown) {
		if (ScreenLayer.ready()) {
			return ScreenLayer.ballInView(centre, cullRadius(halfW, grow, blown));
		}
		float far = centre.length();
		return far < 1e-3f || (centre.x * look.x + centre.y * look.y + centre.z * look.z) / far >= CULL;
	}

	/**
	 * How strongly a card glows, before the Glow setting: most on the words being sung, a little less on the rest of
	 * the line still up. Once a card is let go its glow goes out quickly as it greys, so fading words, a whole line of
	 * them at a time, cost no glow to draw.
	 *
	 * @param gone seconds since the card was let go, or a negative number while it's still lit
	 */
	/** The dark outline copies round a word: the four shifted ones by day, none in light mode (the shadow stays). */
	static int outlineCopies(boolean light, float edge) {
		return !light && edge * 255 >= 4 ? 4 : 0;
	}

	static float glowFor(boolean sung, float gone) {
		if (gone < 0) {
			return sung ? GLOW_SUNG : GLOW_LIT;
		}
		return GLOW_LIT * (1 - Mth.clamp(gone / GLOW_OUT, 0, 1));
	}

	/**
	 * {@link #glowFor(boolean, float)} with the Effects setting: with light effects only the words being sung keep their
	 * glow, as the ring costs most of what a card takes to draw and the rest of the line still reads by its shadow. A
	 * word's glow goes out over {@link #GLOW_OUT} once the next one comes, the same way as when it's let go.
	 *
	 * @param replaced seconds since the next word came, or a negative number while this is the newest
	 */
	static float glowFor(boolean sung, float gone, float replaced, boolean light) {
		float g = glowFor(sung, gone);
		if (light && !sung && replaced >= 0) {
			g = Math.min(g, GLOW_LIT * (1 - Mth.clamp(replaced / GLOW_OUT, 0, 1)));
		}
		return g;
	}

	/**
	 * How far through its slow sway a card is, as an angle: each card starts at its own point (from when it came) so
	 * neighbours don't rock in step.
	 */
	static float swayAngle(float age, long born) {
		return age * (Mth.TWO_PI / SWAY_TIME) + (born >>> 16 & 1023) * (Mth.TWO_PI / 1024);
	}

	/** How many blocks a word has dropped {@code seconds} after you died. */
	static float fallDrop(float seconds) {
		return FALL_PULL * seconds * seconds / 2;
	}

	/** How many blocks a card has drifted up {@code age} seconds after it came, with the Float up setting {@code rise}. */
	static float riseAfter(float age, float rise) {
		return Math.min(age * RISE, RISE_MAX) * rise;
	}

	/** How dark the edge round the letters is: none at night, where the shadow and glow are enough, darkest at noon. */
	static float edgeOpacity(float day) {
		return OUTLINE_DAY * Mth.clamp((day - 0.2f) / 0.8f, 0, 1);
	}

	/** How dark the drop shadow is: stronger by day, when the sky behind the words is bright. */
	static float shadowOpacity(float day) {
		return Mth.lerp(Mth.clamp(day, 0, 1), SHADOW_NIGHT, SHADOW_DAY);
	}

	/**
	 * Whether a card turned to {@code face} degrees, standing {@code x, z} blocks from the eye, shows you its back.
	 * Its words read from the side its face points to: (sin face, 0, -cos face) after the turn used in render.
	 */
	static boolean seenFromBehind(float x, float z, float face) {
		return -x * Mth.sin(face * DEG) + z * Mth.cos(face * DEG) < 0;
	}

	/**
	 * Draws the first {@code shown} letters of card {@code c}; {@code melt} is how far it has dissolved and {@code typed} how long
	 * ago it began typing in (below 0: neither).
	 */
	private static void draw(PoseStack pose, SubmitNodeCollector out, Card c, int shown, float melt, float typed, int kind, Style coloured,
			float alpha, float x0, float y0, float z, Font.DisplayMode mode) {
		int alphaByte = Math.round(alpha * 255);
		if (alphaByte < 4) { // text this faint would be drawn fully opaque
			return;
		}
		Display d = c.display;
		int n = d.cps().length;
		pose.pushPose();
		pose.translate(0, 0, z);
		for (int from = 0; from < n; ) {
			int each = letterByte(c, from, melt, typed, alpha, alphaByte);
			if (d.logical()[from] >= shown || each < 4) {
				from++;
				continue;
			}
			// Letters just as faint go in one draw, so a dissolving card only splits up round the letters on their way out
			int to = from + 1;
			while (to < n && d.logical()[to] < shown && letterByte(c, to, melt, typed, alpha, alphaByte) == each) {
				to++;
			}
			FormattedCharSequence seq = c.run(kind, from, to, coloured);
			//? if >=26.1 {
			/*out.submitText(pose, x0 + c.at[from], y0, seq, false, mode, LightCoordsUtil.FULL_BRIGHT, each << 24 | 0xFFFFFF, 0, 0);
			*///?} else {
			out.submitText(pose, x0 + c.at[from], y0, seq, false, mode, LightTexture.FULL_BRIGHT, each << 24 | 0xFFFFFF, 0, 0);
			//?}
			from = to;
		}
		pose.popPose();
	}

	/**
	 * How opaque glyph {@code v} of card {@code c} is drawn, 0-255: {@code alphaByte}, less on a dissolving card as its letter goes
	 * and on a typing card until its letter is in.
	 */
	private static int letterByte(Card c, int v, float melt, float typed, float alpha, int alphaByte) {
		if (melt < 0 && typed < 0) {
			return alphaByte;
		}
		int letter = c.display.logical()[v];
		float left = melt < 0 ? 1 : letterLeft(c.born, letter, melt);
		float in = typed < 0 ? 1 : typedIn(letter, typed);
		return Math.round(alpha * left * in * 255);
	}

	/**
	 * The glow's colour for letters of {@code rgb}: the same hue and brightness, but richer, so pale letters sit in a
	 * deeper haze of their own colour the way a neon sign does. White and greys stay as they are.
	 */
	static int neon(int rgb) {
		int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
		int top = Math.max(r, Math.max(g, b));
		return richer(r, top) << 16 | richer(g, top) << 8 | richer(b, top);
	}

	private static int richer(int channel, int top) {
		return Math.max(0, Math.round(top - (top - channel) * NEON));
	}

	private static int mix(int from, int to, float t) {
		int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}
}
