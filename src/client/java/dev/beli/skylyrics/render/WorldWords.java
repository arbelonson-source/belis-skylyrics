package dev.beli.skylyrics.render;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.DoublePredicate;

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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import dev.beli.skylyrics.config.SkyLyricsConfig.WorldArrival;
import dev.beli.skylyrics.config.SkyLyricsConfig.WorldLeave;
import dev.beli.skylyrics.render.LyricsRenderer.Display;

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
	/** At most this many cards at once by default (the setting); past it the oldest is cut short. */
	static final int MAX_CARDS = 7;
	/** A card cut short to make room fades out over this many seconds. */
	private static final float CUT = 0.3f;
	/** After a jump in the song, at most this many of the skipped pieces are spawned (the latest ones). */
	private static final int CATCH_UP = 2;
	/** Once its words are over, a card fades out over this many seconds by default (the setting). */
	static final float FADE = 2.2f;
	/** ...and in the first of these seconds it pales towards a soft white, by this much, so it fades into the sky like a ghost
	 * of itself instead of turning a darker grey than the sky around it. */
	private static final float PALE_TIME = 0.24f, PALE = 0.55f;
	private static final int PALE_RGB = 0xF4F2FF;
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
	/** Front to back, in text units, so the layers don't flicker against each other. */
	private static final float SHADOW_Z = -0.5f, GLOW_Z = 0.1f, FACE_Z = 0.3f;
	/** From the top of a row of text to the middle of its capitals. */
	private static final float CAP_MIDDLE = 3.8f;
	/** Words with at most this many letters lean on the next word ("CAN YOU", "THE BEAT"). */
	static final int SHORT_WORD = 3;
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
	/** How much each piece of a flowing line turns differently from the rest (degrees), and how much of the usual nod and lean it keeps, so the line reads as one. */
	private static final float SENTENCE_WOBBLE = 1.5f, SENTENCE_NOD = 0.25f, SENTENCE_LEAN = 0.25f;
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

	/** One card: where it stands, what it shows, and how it's turned. */
	private static final class Card {
		final Display display;
		final float[] at;
		/** Where it stands; moves along with you while you glide, ride or fly (elytra mode). */
		Vec3 pos;
		final float nod, lean, size;
		/** The way the card faces, turned along with you while you travel. */
		float face;
		final long born;
		long goneAt = -1;
		/** The colour it had when it started fading, kept so the next line's colour never washes over it, or -1. */
		int goneRgb = -1;
		/** When it was cut short to make room for newer cards, or -1. */
		long cutAt = -1;
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

	private final List<Card> cards = new ArrayList<>();
	/** Reused for each card's turn while drawing, which only happens on the render thread. */
	private static final Quaternionf TURN = new Quaternionf();
	private final int[] recent = {-1, -1};
	private int line = Integer.MIN_VALUE;
	private int spawned;
	/** Pieces read left to right in rows (the Flowing line setting) instead of scattering. */
	private boolean flow;
	private boolean hold = true;
	private WorldArrival arrival = WorldArrival.POP;
	private WorldLeave leave = WorldLeave.SHRINK;
	private boolean nudge = true, avoidPlayers = true, knock = true, swung, clearMiddle = true;
	private long lastNudge;
	/** Where the camera was last frame, to carry the cards along while you glide. */
	private @Nullable Vec3 lastEye;
	private float lastYaw, lastPitch;
	private long lastGlide;
	/** Elytra mode is carrying the words this frame. */
	private boolean gliding;
	private @Nullable Sentence sentence;
	/** The line written before it, which the next line of a verse goes under. */
	private @Nullable Sentence last;
	private int maxCards = MAX_CARDS;
	/** The glow's own colour, or -1 to glow in the words' colour. */
	private int glowRgb = -1;
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
	private long rng = System.nanoTime();

	/** You died: every word up drops out of the sky, spinning as it fades. */
	void fall(long now) {
		for (Card c : cards) {
			if (c.fellAt < 0) {
				c.fellAt = now;
			}
		}
	}

	void clear() {
		cards.clear();
		newLyrics();
	}

	/** Starts over for new lyrics, keeping a song title that's up. */
	void newLyrics() {
		cards.removeIf(c -> !c.title);
		line = Integer.MIN_VALUE;
		spawned = 0;
		lineRaw = null;
		lineText = null;
		sentence = null;
		last = null;
		recent[0] = recent[1] = -1;
	}

	/**
	 * Splits a line into the pieces that appear together: one word, or a short word with the next one, never more
	 * than two words. Returns each piece's first and past-last code point in the line.
	 */
	static List<int[]> chunks(String text) {
		int[] cps = text.codePoints().toArray();
		List<int[]> words = new ArrayList<>();
		for (int i = 0; i < cps.length; ) {
			if (cps[i] == ' ') {
				i++;
				continue;
			}
			int start = i;
			while (i < cps.length && cps[i] != ' ') {
				i++;
			}
			words.add(new int[] {start, i});
		}
		List<int[]> out = new ArrayList<>();
		for (int w = 0; w < words.size(); w++) {
			int[] word = words.get(w);
			if (w + 1 < words.size() && letters(cps, word) <= SHORT_WORD) {
				out.add(new int[] {word[0], words.get(++w)[1]});
			} else {
				out.add(word);
			}
		}
		return out;
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
			case SHRINK -> 0;
		};
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

	/** How many blocks card {@code c} has moved up or down while leaving, {@code ahead} seconds from now. */
	private float leftUp(Card c, long now, float ahead) {
		return c.goneAt < 0 ? 0 : leaveUp(leave, (now - c.goneAt) / 1e9f + ahead, fade);
	}

	/** How visible a card cut short {@code seconds} ago still is. */
	static float cutAlpha(float seconds) {
		return 1 - Mth.clamp(seconds / CUT, 0, 1);
	}

	/**
	 * How big a card is, times the setting: little words stay small and long ones get big, so they sit together like
	 * a collage instead of a row of equal labels. {@code roll} is a random number from 0 to 1.
	 */
	static float sizeFor(int letters, float roll) {
		float t = Mth.clamp((letters - SHORT_LETTERS) / (float) LONG_LETTERS, 0, 1);
		return Mth.lerp(t, SIZE_SHORT, SIZE_LONG) * (1 + (roll * 2 - 1) * SIZE_JITTER);
	}

	/** Glows in {@code rgb}, or in the words' own colour when it's -1. */
	void glowColour(int rgb) {
		glowRgb = rgb;
	}

	/** How each new word shows up (the Arrival setting). */
	void arrival(WorldArrival arrival) {
		this.arrival = arrival == null ? WorldArrival.POP : arrival;
	}

	/** How each sung word goes (the Leaving setting). */
	void leave(WorldLeave leave) {
		this.leave = leave == null ? WorldLeave.SHRINK : leave;
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

	private static final List<Player> inSight = new ArrayList<>();
	private static long sightTick = Long.MIN_VALUE;
	private static @Nullable ClientLevel sightLevel;

	/**
	 * Where the other players near you are, as boxes of yaw, pitch and half sizes in degrees seen from {@code eye}. A
	 * player's box reaches up a little, since words float upwards and would otherwise rise into them.
	 */
	private static List<float[]> players(@Nullable ClientLevel level, Vec3 eye) {
		List<float[]> spots = new ArrayList<>();
		Player me = Minecraft.getInstance().player;
		if (level == null || me == null) {
			// Let go of the old world's players once you've left it
			inSight.clear();
			sightLevel = null;
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
			sentence = null;
			last = null;
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
	 */
	void update(int index, @Nullable String text, float sungChars, boolean over, float fixedYaw, Camera camera,
			@Nullable ClientLevel level, Font font, Style style, long now) {
		if (index != line || text != null && !text.equals(lineRaw)) {
			line = index;
			lineRaw = text;
			lineText = text == null || !capitals ? text : capitals(text);
			lineChunks = lineText == null ? List.of() : chunks(lineText);
			float[] whole = lineText == null ? null : widths(Display.of(lineText), font, style);
			lineWidth = whole == null || whole.length == 0 ? 0 : whole[whole.length - 1];
			lineRtl = lineText != null && rightToLeft(lineText);
			spawned = 0;
			last = sentence;
			sentence = null;
			if (hold) {
				letGo(now);
			}
		}
		int due = 0;
		while (due < lineChunks.size() && lineChunks.get(due)[0] <= sungChars) {
			due++;
		}
		if (due - spawned > CATCH_UP) {
			spawned = due - CATCH_UP;
		}
		for (; spawned < due; spawned++) {
			if (!hold) {
				letGo(now);
			}
			spawn(lineChunks.get(spawned), centre(fixedYaw, camera.yRot(), camera.xRot(), gliding), camera, level, font, style, now);
		}
		if (lineText == null || over) {
			letGo(now);
		}
		for (Card c : cards) {
			if (c.title && c.goneAt < 0 && now - c.born > TITLE_HOLD * 1e9f) {
				c.goneAt = now;
			}
		}
		Vec3 eye = camera.position();
		for (Iterator<Card> it = cards.iterator(); it.hasNext(); ) {
			Card c = it.next();
			if (c.goneAt >= 0 && now - c.goneAt > fade * 1e9f || c.cutAt >= 0 && now - c.cutAt > CUT * 1e9f
					|| c.pos.distanceTo(eye) > GONE_DISTANCE) {
				it.remove();
			}
		}
	}

	/**
	 * Elytra mode: while you glide, ride or fly, every card moves and turns along with you, so the words travel with
	 * you instead of being left behind, and steering doesn't swing them (or the rest of the line) out of sight.
	 * Call once a frame, before {@link #update}.
	 */
	void glide(Vec3 eye, float yaw, float pitch, boolean gliding, long now) {
		Vec3 moved = carry(lastEye, eye, gliding, (now - lastGlide) / 1e9f);
		lastGlide = now;
		this.gliding = gliding;
		float turn = Mth.wrapDegrees(yaw - lastYaw), tilt = pitch - lastPitch;
		lastEye = eye;
		lastYaw = yaw;
		lastPitch = pitch;
		if (moved == Vec3.ZERO) {
			return;
		}
		for (Card c : cards) {
			c.pos = eye.add(swing(c.pos.add(moved).subtract(eye), turn, tilt));
			c.face += turn;
		}
		if (sentence != null) {
			sentence.swing(turn, tilt);
		}
		if (last != null) {
			last.swing(turn, tilt);
		}
	}

	/** An offset from the eye turned with the view: {@code turn} degrees round and {@code tilt} degrees down. */
	static Vec3 swing(Vec3 offset, float turn, float tilt) {
		double far = offset.length();
		if (far < 1e-3 || turn == 0 && tilt == 0) {
			return offset;
		}
		float pitch = Mth.clamp(pitchOf(offset) + tilt, -SWING_PITCH, SWING_PITCH);
		return direction(yawOf(offset) + turn, pitch).scale(far);
	}

	/**
	 * How far the cards move with you this frame, {@code seconds} after the last: as far as you went while travelling,
	 * otherwise nowhere.
	 */
	static Vec3 carry(@Nullable Vec3 last, Vec3 eye, boolean gliding, float seconds) {
		float jump = Math.max(GLIDE_JUMP, GLIDE_SPEED * Mth.clamp(seconds, 0, 1));
		if (!gliding || last == null || last.distanceToSqr(eye) > jump * jump) {
			return Vec3.ZERO;
		}
		return eye.subtract(last);
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
	void announce(String title, String artist, float fixedYaw, Camera camera, Font font, Style style, long now) {
		cards.removeIf(c -> c.title);
		String text = titleText(title, capitals);
		if (text.isEmpty()) {
			return;
		}
		float[] centre = centre(fixedYaw, camera.yRot(), camera.xRot(), gliding);
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
		Card c = new Card(d, widths(d, font, style), camera.position().add(dir.scale(TITLE_DISTANCE * away)), yaw, 0, 0,
				SIZE * size * away, now);
		c.title = true;
		cards.add(c);
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
		float[] at = new float[d.cps().length + 1];
		for (int v = 1; v <= d.cps().length; v++) {
			at[v] = LyricsFont.width(font, style, new String(d.cps(), 0, v));
		}
		return at;
	}

	/**
	 * Yaw and pitch new words gather round: where you look, or with a fixed direction that way, a little above the
	 * horizon, so you can look about and find them where you left them. While Elytra mode carries the words, always
	 * where you look: the fixed spot would be off to the side or behind as soon as you fly another way.
	 */
	static float[] centre(float fixedYaw, float yaw, float pitch, boolean gliding) {
		return gliding || Float.isNaN(fixedYaw) ? new float[] {yaw, pitch} : new float[] {fixedYaw, 0};
	}

	private void spawn(int[] chunk, float[] centre, Camera camera, @Nullable ClientLevel level, Font font, Style style,
			long now) {
		String piece = new String(lineText.codePoints().toArray(), chunk[0], chunk[1] - chunk[0]);
		Display d = Display.of(piece);
		float[] at = widths(d, font, style);
		int letters = 0;
		for (int cp : d.cps()) {
			if (Character.isLetterOrDigit(cp)) {
				letters++;
			}
		}
		float size = SIZE * sizeFor(letters, next());
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
			if (to.length() > 1e-3 && (c.of == null || c.of != sentence) && c.cutAt < 0) {
				float[] print = footprint(to, riseAfter((now - c.born) / 1e9f + LOOK_AHEAD, rise) * c.lift + leftUp(c, now, LOOK_AHEAD), c.at[c.at.length - 1] * c.size * wordScale / 2,
						5 * c.size * wordScale);
				under.add(c);
				shown.add(print);
				if (c.goneAt < 0 || goneAlpha((now - c.goneAt) / 1e9f, fade) > 0.3f) {
					taken.add(print);
				}
				if (c.goneAt < 0) {
					lit.add(print);
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
		float[] spot = flow ? follow(centre, at, size, avoid, firm) : scatter(centre, at, size, avoid);
		Vec3 dir = direction(spot[0], spot[1]);
		// Come in front of anything solid on the way out (a tree, a hill, a wall), so the card is neither inside a block
		// nor hidden behind one
		float halfW = at[at.length - 1] * size * away * wordScale / 2, halfH = 5 * size * away * wordScale;
		float meant = spot[2] * away;
		// A card brought in is shrunk to match, so it covers just the patch of sky it was given and no more
		float distance = clearReach(CLOSEST, meant, 1,
				out -> buried(level, eye.add(dir.scale(out)), dir, halfW * (float) out / meant, halfH * (float) out / meant));
		float shrink = distance / meant;
		Vec3 pos = eye.add(dir.scale(distance));
		if (buried(level, pos, dir, halfW * shrink, halfH * shrink)) {
			// Nowhere clear on the way in (standing in a cave or by a hill): lift it out where it was meant to be
			Vec3 there = eye.add(dir.scale(meant));
			for (int up = 1; up <= LIFT_STEPS; up++) {
				if (!buried(level, there.add(0, up, 0), dir, halfW, halfH)) {
					pos = there.add(0, up, 0);
					shrink = 1;
					break;
				}
			}
		}
		// Older words this one still lands on (a crowded sky, or the last line still fading) go at once, rather than
		// show through it for the whole fade
		float[] mine = footprint(pos.subtract(eye), 0, halfW * shrink, halfH * shrink);
		for (int k = 0; k < shown.size(); k++) {
			float[] o = shown.get(k);
			Card c = under.get(k);
			// A word already fading goes on any touch; a lit one only when mostly covered
			float most = c.goneAt < 0 ? OVERLAP : 1;
			if (!c.title && Math.abs(Mth.wrapDegrees(mine[0] - o[0])) < (mine[2] + o[2]) * most
					&& Math.abs(mine[1] - o[1]) < (mine[3] + o[3]) * most) {
				c.cutAt = now;
			}
		}
		// The Tilt setting scales every angle, so at 0 a card faces you square on
		float turn = tilt * (flow && sentence != null ? sentence.turn + (next() * 2 - 1) * SENTENCE_WOBBLE
				: Mth.lerp(next(), TURN_MIN, TURN_MAX) * (next() < 0.5f ? -1 : 1));
		float nod = tilt * (next() * 2 - 1) * NOD * (flow ? SENTENCE_NOD : 1), lean = tilt * (next() * 2 - 1) * LEAN * (flow ? SENTENCE_LEAN : 1);
		if (flow && sentence != null) {
			// Every word leans along the line where it sits, so the line reads as one curved piece
			lean += spot[3];
		}
		Card card = new Card(d, at, pos, spot[0] + turn, nod, lean, size * away * shrink, now);
		card.rtl = flow && lineRtl;
		card.of = flow ? sentence : null;
		card.lift = shrink;
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
			// Up high a card is closer to the axis it turns around, so it takes up more yaw
			float halfW = halfAngle(at[at.length - 1] * size * wordScale / 2, distance * Mth.cos(pitch * DEG)) + PAD_X;
			float halfH = halfAngle(5 * size * wordScale, distance) + PAD_Y;
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
	private float[] follow(float[] centre, float[] at, float size, List<float[]> taken, List<float[]> firm) {
		float aside = clearMiddle ? SIDE_YAW + SENTENCE_SHIFT_X : 0;
		if (sentence != null && sentence.lost(centre[0], centre[1] - LIFT, aside)) {
			// You turned away mid-line: the rest of it starts again where you look now, clear of what's there
			sentence = null;
			last = null;
		}
		if (sentence == null) {
			boolean verse = last != null && last.rtl == lineRtl && !last.lost(centre[0], centre[1] - LIFT, aside);
			float distance = verse ? last.distance : Mth.lerp(next(), SENTENCE_NEAR, SENTENCE_FAR), bestCost = Float.MAX_VALUE;
			// The whole line's box: as wide as it reads, or a full row, and as many rows deep as it will need
			float wide = (float) Math.toDegrees(lineWidth * SIZE * wordScale * LINE_SPREAD / distance);
			// Rows grow with the words, so a big Size still writes a few words across before starting the next row
			float width = Math.min(Sentence.WIDTH * Mth.clamp(wordScale, 1, ROW_GROW), 2 * VIEW_HALF);
			int rows = Math.max(1, Mth.ceil(wide / (width * ROW_FILL)));
			float row = 2 * halfAngle(5 * SIZE * SIZE_LONG * wordScale, distance) + Sentence.ROW_GAP;
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
				float left = onScreen(centre[0] + side - width / 2 + (next() * 2 - 1) * SENTENCE_SHIFT_X, width, centre[0]);
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
				top = Mth.clamp(Math.max(top, belowTop(centre[1], width, slope, bend, row, drift)), -70, 40);
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
		float distance = sentence.distance * (1 + (next() * 2 - 1) * SENTENCE_DEPTH);
		float halfW = halfAngle(at[at.length - 1] * size * wordScale / 2, distance), halfH = halfAngle(5 * size * wordScale, distance);
		float[] at2 = placeClear(sentence, halfW, halfH, next() * SENTENCE_INDENT, (next() * 2 - 1) * SENTENCE_BOB, firm);
		return new float[] {at2[0], at2[1], distance, at2[2]};
	}

	/** How far either side of where you look, in degrees, a line's rows may reach and still be on screen. */
	static final float VIEW_HALF = 40f;

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

	/** Where a row starting at {@code left} must start instead so all of its {@code width} stays on screen. */
	static float onScreen(float left, float width, float centre) {
		return Mth.clamp(left, centre - VIEW_HALF, centre + VIEW_HALF - Math.min(width, 2 * VIEW_HALF));
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
	static float[] placeClear(Sentence line, float halfW, float halfH, float indent, float bob, List<float[]> taken) {
		float[] mark = line.mark();
		int rows = line.rows, best = 0;
		float least = Float.MAX_VALUE;
		for (int t = 0; t < CLEAR_TRIES; t++) {
			float[] at = line.place(halfW, halfH, indent, bob);
			if (line.rows - rows > 1) {
				break;
			}
			float crowd = crowding(at[0], at[1], halfW, halfH, taken);
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
			at = line.place(halfW, halfH, indent, bob);
		}
		return at;
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
		/** Pitch of the bottom of the rows above and of the lowest word on this row so far (before the slope). */
		float above = Float.NEGATIVE_INFINITY, low = Float.NEGATIVE_INFINITY;
		/**
		 * About how much of the line is still to be written, in degrees along the row. When the last row won't be full,
		 * it sits centred under the row above instead of hugging the left edge.
		 */
		float rest = Float.POSITIVE_INFINITY;
		/** Whether the next piece starts a new row, as the first piece of a line written under another one does. */
		boolean newRow;
		/** How many rows have been started after the first. */
		int rows;

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
			float yaw = cursor + GAP * halfH + halfW;
			boolean wrap = newRow || cursor > left && yaw + halfW > left + width;
			if (wrap) {
				newRow = false;
				rows++;
				// A short last row is centred under the full one above it
				float centre = (cursor - left - Math.max(rest, 2 * halfW)) / 2;
				yaw = left + Math.max(indent, centre) + halfW;
			}
			rest -= yaw + halfW - cursor;
			cursor = yaw + halfW;
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
			low = Math.max(low, middle + reach);
			return new float[] {yaw, middle - rise, lean};
		}

		/** Where writing has got to, for {@link #back}. */
		float[] mark() {
			return new float[] {top, cursor, above, low, rest, newRow ? 1 : 0, rows};
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
		}

		/**
		 * The next line of a verse: written on from a new row under this one, with the same slant and bow so the two
		 * never cross, and centred under this one's last row when it's shorter.
		 */
		Sentence under() {
			Sentence next = new Sentence(left, top, distance, turn, slope, rtl, bend);
			next.width = width;
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
			return Math.abs(off) > width / 2 + LOST_YAW + aside || Math.abs(pitch - top) > LOST_PITCH;
		}

		/** How far past a line's edge (yaw) or from its row (pitch), in degrees, you can look before it starts over. */
		static final float LOST_YAW = 22f, LOST_PITCH = 30f;

		/** Turns the rest of the line along with you (see {@link WorldWords#glide}). */
		void swing(float turn, float tilt) {
			left += turn;
			cursor += turn;
			float moved = Mth.clamp(top + tilt, -SWING_PITCH, SWING_PITCH) - top;
			top += moved;
			above += moved;
			low += moved;
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

	/**
	 * Finds words that sit on top of each other as seen from here and sends the newer one to the nearest clear side,
	 * gliding there over a moment rather than jumping.
	 */
	private void separate(Vec3 eye, long now) {
		float dt = lastNudge == 0 ? 0 : Mth.clamp((now - lastNudge) / 1e9f, 0, 0.1f);
		lastNudge = now;
		// Players come first and never move, so a word in front of someone glides off them, even as they walk about
		ClientLevel level = Minecraft.getInstance().level;
		List<float[]> people = avoidPlayers ? players(level, eye) : List.of();
		List<Card> live = new ArrayList<>(cards.size());
		List<float[]> seen = new ArrayList<>(cards.size());
		// Words still being sung go first, so a fading word glides out of a new one's way instead of shoving it up the sky
		List<Card> order = new ArrayList<>(cards.size());
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
					Vec3 side = new Vec3(-to.z, 0, to.x).normalize().scale(-flat * Math.tan(move[0] * DEG));
					Vec3 up = new Vec3(0, far * Math.tan(-move[1] * DEG), 0);
					Vec3 dir = to.normalize();
					float halfBlocks = c.at[c.at.length - 1] * c.size * wordScale / 2, tall = 5 * c.size * wordScale;
					if (buried(level, c.pos.add(c.slide).add(side).add(up), dir, halfBlocks, tall)) {
						// That way is into the ground or a wall: go over the top instead, or stay put
						up = new Vec3(0, Math.abs(up.y) + Math.abs(far * Math.tan(move[0] * DEG)), 0);
						side = Vec3.ZERO;
						if (buried(level, c.pos.add(c.slide).add(up), dir, halfBlocks, tall)) {
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
			live.add(c);
			seen.add(mine);
		}
		float ease = 1 - (float) Math.exp(-SLIDE_SPEED * dt);
		for (Card c : cards) {
			c.slid = c.slid.add(c.slide.subtract(c.slid).scale(ease));
		}
	}

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
	 * How far to move {@code b} (yaw, pitch, half width, half height, in degrees) so it no longer covers {@code a}: along
	 * whichever way is shorter, with a little room to spare, or nothing when they're already apart.
	 */
	static float[] push(float[] a, float[] b) {
		float dx = Mth.wrapDegrees(b[0] - a[0]), dy = b[1] - a[1];
		float w = a[2] + b[2] - Math.abs(dx), h = a[3] + b[3] - Math.abs(dy);
		if (w <= 0 || h <= 0) {
			return new float[] {0, 0};
		}
		// Words sit in rows, so going up or down past a short word is often the smaller step than going round it
		if (h <= w) {
			return new float[] {0, (dy < 0 ? -1 : 1) * (h + SLIDE_ROOM)};
		}
		return new float[] {(dx < 0 ? -1 : 1) * (w + SLIDE_ROOM), 0};
	}

	/** Spare degrees left between two words a slide has parted, and how quickly (per second) a word glides there. */
	static final float SLIDE_ROOM = 0.6f, SLIDE_SPEED = 6f;

	static float halfAngle(float half, double distance) {
		return (float) Math.toDegrees(Math.atan2(half, distance));
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
		Vec3 eye = camera.position();
		if (nudge || avoidPlayers) {
			separate(eye, now);
		}
		Vec3 look = direction(camera.yRot(), camera.xRot());
		if (swung) {
			swung = false;
			knockAt(eye, look, now);
		}
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
			float a = alpha * Mth.clamp(age / fadeIn, 0, 1) * (c.goneAt < 0 ? 1 : goneAlpha(gone, fade))
					* (c.cutAt < 0 ? 1 : cutAlpha((now - c.cutAt) / 1e9f));
			float fell = c.fellAt < 0 ? 0 : (now - c.fellAt) / 1e9f;
			if (c.fellAt >= 0) {
				a *= goneAlpha(fell, FALL_TIME);
			}
			if (a < 0.03f) {
				continue;
			}
			float x = (float) (c.pos.x + c.slid.x - eye.x), z = (float) (c.pos.z + c.slid.z - eye.z);
			float sway = swayAngle(age, c.born);
			float y = (float) (c.pos.y + c.slid.y - eye.y) + riseAfter(age, rise) * c.lift + leftUp(c, now, 0) + tilt * SWAY_BOB * Mth.sin(sway) - fallDrop(fell);
			float hit = c.hitAt < 0 ? -1 : (now - c.hitAt) / 1e9f, spin = 0, squash = 1, shove = knockOut(hit);
			if (shove > 0) {
				x += (float) c.knock.x * shove;
				y += (float) c.knock.y * shove;
				z += (float) c.knock.z * shove;
				spin = c.spin * knockWobble(hit);
				squash = 1 + KNOCK_SQUASH * knockWobble(hit * 1.6f);
			}
			int n = c.display.cps().length;
			// A flowing line's words don't pop: each one glides into its place in the sentence, whole, as it fades up
			float enter = arrival == WorldArrival.ZOOM ? zoomIn(age) : popping && !flow ? pop(age) : 1;
			float grow = c.size * scale * enter * (c.goneAt < 0 || leave != WorldLeave.SHRINK ? 1 : leaveSize(gone, fade)) * squash;
			Vector3f centre = new Vector3f(x, y, z);
			if (!showing(centre, c.at[n] * grow / 2, grow, look)) {
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
			int shown = flow || !popping ? n : Mth.ceil(n * Math.min(1, age / TYPE));
			int colour = c.goneAt < 0 ? mix(rgb, 0xFFFFFF, flash(age))
					: paled(keptColour(c, rgb), gone);
			if (shove > 0) {
				colour = mix(colour, 0xFFFFFF, (float) Math.exp(-KNOCK_FLASH * hit));
			}
			float blown = blownBack(leave, gone, fade, c.rtl);
			pose.pushPose();
			pose.translate(x, y, z);
			pose.mulPose(TURN.rotationY(Mth.PI - face * DEG).rotateX(c.nod * DEG)
					.rotateZ((c.lean + tilt * SWAY_LEAN * Mth.cos(sway) + ((c.born & 1) == 0 ? FALL_SPIN : -FALL_SPIN) * fell + spin
					- BLOW_TIP * blown / BLOW) * DEG));
			pose.scale(grow, -grow, grow);
			float x0 = -c.at[n] / 2f - (flow && popping ? (c.rtl ? -1 : 1) * slideIn(age) : 0) + blown,
					y0 = -CAP_MIDDLE - (arrival == WorldArrival.DROP ? dropIn(age) : 0);
			// A fading card loses its shadow sooner, so it melts into the sky instead of leaving a dark smudge
			float shade = a * shadowOpacity(day) * (c.goneAt < 0 ? 1 : goneAlpha(gone, fade));
			draw(pose, out, c, shown, style, SHADOW_RGB, shade, x0 + SHADOW_X, y0 + SHADOW_Y, SHADOW_Z, mode);
			float edge = a * edgeOpacity(day) * (c.goneAt < 0 ? 1 : goneAlpha(gone, fade));
			for (int k = 0; k < 4 && edge * 255 >= 4; k++) {
				draw(pose, out, c, shown, style, SHADOW_RGB, edge, x0 + (k < 2 ? -OUTLINE : OUTLINE), y0 + (k % 2 == 0 ? -OUTLINE : OUTLINE),
						SHADOW_Z, mode);
			}
			// The glow comes up just behind the letters, so a new word lights up as it lands
			float ring = a * Mth.clamp(age / fadeIn, 0, 1) * GLOW_ALPHA * glow * glowFor(c == sung || c.title, c.goneAt < 0 ? -1 : gone, i + 1 < cards.size() ? (now - cards.get(i + 1).born) / 1e9f : -1, light) / 2 * GLOW_COPIES / copies;
			int halo = mix(glowRgb < 0 ? colour : glowRgb, 0x000000, HALO_DAY * day);
			for (int k = 0; k < copies && ring * 255 >= 4; k++) {
				float turn = k * Mth.TWO_PI / copies + 0.5f;
				draw(pose, out, c, shown, style, halo, ring, x0 + Mth.cos(turn) * GLOW_RADIUS, y0 + Mth.sin(turn) * GLOW_RADIUS,
						GLOW_Z, mode);
			}
			draw(pose, out, c, shown, style, colour, a, x0, y0, FACE_Z, mode);
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
		if (ScreenLayer.ready()) {
			float halfH = HALF_HEIGHT * grow;
			return ScreenLayer.ballInView(centre, Mth.sqrt(halfW * halfW + halfH * halfH) + EDGE * grow);
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

	/** Draws the card's typed-in letters once, in one colour; each unbroken run where it sits in the whole word. */
	private static void draw(PoseStack pose, SubmitNodeCollector out, Card c, int shown, Style style, int rgb, float alpha,
			float x0, float y0, float z, Font.DisplayMode mode) {
		int alphaByte = Math.round(alpha * 255);
		if (alphaByte < 4) { // text this faint would be drawn fully opaque
			return;
		}
		Display d = c.display;
		int n = d.cps().length;
		Style coloured = style.withColor(rgb);
		pose.pushPose();
		pose.translate(0, 0, z);
		for (int from = 0; from < n; ) {
			if (d.logical()[from] >= shown) {
				from++;
				continue;
			}
			int to = from + 1;
			while (to < n && d.logical()[to] < shown) {
				to++;
			}
			int a0 = from, a1 = to;
			FormattedCharSequence seq = sink -> {
				for (int v = a0; v < a1; v++) {
					if (!sink.accept(v, coloured, d.cps()[v])) {
						return false;
					}
				}
				return true;
			};
			//? if >=26.1 {
			/*out.submitText(pose, x0 + c.at[from], y0, seq, false, mode, LightCoordsUtil.FULL_BRIGHT, alphaByte << 24 | 0xFFFFFF, 0, 0);
			*///?} else {
			out.submitText(pose, x0 + c.at[from], y0, seq, false, mode, LightTexture.FULL_BRIGHT, alphaByte << 24 | 0xFFFFFF, 0, 0);
			//?}
			from = to;
		}
		pose.popPose();
	}

	private static int mix(int from, int to, float t) {
		int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}
}
