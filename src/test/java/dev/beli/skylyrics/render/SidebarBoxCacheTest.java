package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SidebarBoxCacheTest {
	private static final Object FONT = new Object();
	private static final long T0 = 5_000_000_000L;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static Objective objective(Scoreboard sb, String title) {
		return sb.addObjective("o" + sb.getObjectiveNames().size(), ObjectiveCriteria.DUMMY, Component.literal(title),
				ObjectiveCriteria.RenderType.INTEGER, true, null);
	}

	private static void score(Scoreboard sb, Objective o, String owner, int value) {
		sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(owner), o).set(value);
	}

	private static float[] box(NowPlayingHud.SidebarCache cache, Scoreboard sb, Objective o, long now) {
		float[] hit = cache.lookup(sb, o, FONT, 400, 240, now);
		return hit != null ? hit : cache.store(NowPlayingHud.measureSidebar(sb, o, t -> t.getString().length() * 6, 400, 240), now);
	}

	private static Scoreboard board(Objective[] out, int entries) {
		Scoreboard sb = new Scoreboard();
		Objective o = objective(sb, "Title");
		for (int i = 0; i < entries; i++) {
			score(sb, o, "player" + i, i * 3);
		}
		out[0] = o;
		return sb;
	}

	@Test
	void anUnchangedScoreboardKeepsTheSameBox() {
		Objective[] o = new Objective[1];
		Scoreboard sb = board(o, 12);
		NowPlayingHud.SidebarCache cache = new NowPlayingHud.SidebarCache();
		float[] first = box(cache, sb, o[0], T0);
		assertNotNull(first);
		assertSame(first, box(cache, sb, o[0], T0 + 16_000_000L));
		assertEquals(1, cache.measures);
	}

	@Test
	void aChangedScoreANewEntryOrARenamedObjectiveGiveANewBox() {
		Objective[] o = new Objective[1];
		Scoreboard sb = board(o, 5);
		NowPlayingHud.SidebarCache cache = new NowPlayingHud.SidebarCache();
		float[] first = box(cache, sb, o[0], T0);
		score(sb, o[0], "player1", 123456); // a wider number
		float[] second = box(cache, sb, o[0], T0);
		assertEquals(2, cache.measures);
		assertTrue(second[2] - second[0] > first[2] - first[0]);
		score(sb, o[0], "someone with a long name", 1);
		float[] third = box(cache, sb, o[0], T0);
		assertEquals(3, cache.measures);
		assertTrue(third[3] - third[1] > second[3] - second[1]);
		o[0].setDisplayName(Component.literal("A much much longer title for the board"));
		float[] fourth = box(cache, sb, o[0], T0);
		assertEquals(4, cache.measures);
		assertTrue(fourth[2] - fourth[0] > third[2] - third[0]);
		PlayerTeam team = sb.addPlayerTeam("t");
		sb.addPlayerToTeam("player0", team);
		box(cache, sb, o[0], T0);
		team.setPlayerPrefix(Component.literal("[Admin of everything] "));
		box(cache, sb, o[0], T0);
		assertEquals(6, cache.measures);
	}

	@Test
	void theGuiSizeAndAgeAlsoMeasureAgain() {
		Objective[] o = new Objective[1];
		Scoreboard sb = board(o, 4);
		NowPlayingHud.SidebarCache cache = new NowPlayingHud.SidebarCache();
		box(cache, sb, o[0], T0);
		assertEquals(null, cache.lookup(sb, o[0], FONT, 401, 240, T0));
		cache.store(NowPlayingHud.measureSidebar(sb, o[0], t -> 5, 401, 240), T0);
		assertEquals(null, cache.lookup(sb, o[0], FONT, 400, 240, T0));
		cache.store(NowPlayingHud.measureSidebar(sb, o[0], t -> 5, 400, 240), T0);
		assertEquals(null, cache.lookup(sb, o[0], FONT, 400, 240, T0 + 2_000_000_000L)); // a font reload changes widths
	}

	@Test
	void aHitAllocatesFarLessThanMeasuringEveryFrame() {
		Objective[] o = new Objective[1];
		Scoreboard sb = board(o, 15);
		NowPlayingHud.SidebarCache cache = new NowPlayingHud.SidebarCache();
		java.util.function.ToIntFunction<FormattedText> width = t -> t.getString().length() * 6;
		box(cache, sb, o[0], T0);
		ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
		long id = Thread.currentThread().getId();
		for (int i = 0; i < 20_000; i++) {
			assertNotNull(cache.lookup(sb, o[0], FONT, 400, 240, T0));
			NowPlayingHud.measureSidebar(sb, o[0], width, 400, 240);
		}
		long a = mx.getThreadAllocatedBytes(id);
		for (int i = 0; i < 10_000; i++) {
			NowPlayingHud.measureSidebar(sb, o[0], width, 400, 240);
		}
		long measuring = mx.getThreadAllocatedBytes(id) - a;
		a = mx.getThreadAllocatedBytes(id);
		for (int i = 0; i < 10_000; i++) {
			cache.lookup(sb, o[0], FONT, 400, 240, T0);
		}
		long hits = mx.getThreadAllocatedBytes(id) - a;
		System.out.println("sidebar bytes per 10k: measuring every frame " + measuring + ", cache hit " + hits);
		assertTrue(hits * 3 < measuring, "a hit allocated " + hits + " bytes per 10000, measuring " + measuring);
	}
}
