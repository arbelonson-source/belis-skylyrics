package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DriftRepaintTest {
	@Test
	void repaintPlanWaitsForTheStepThenPaintsEveryChunk() {
		DriftRepaint plan = new DriftRepaint(0.05f, 4);
		assertEquals(DriftRepaint.NONE, plan.advance(0.03f, true));
		assertEquals(0, plan.advance(0.06f, true));
		assertEquals(0.06f, plan.target());
		assertEquals(1, plan.advance(0.07f, true));
		assertEquals(2, plan.advance(0.08f, true));
		assertEquals(3, plan.advance(0.09f, true));
		assertEquals(0.06f, plan.target(), "a repaint in progress keeps its drift");
		assertEquals(DriftRepaint.NONE, plan.advance(0.10f, true));
		assertEquals(0, plan.advance(0.12f, true));
		assertEquals(0.12f, plan.target());
	}

	@Test
	void repaintPlanSleepsOutOfViewAndRepaintsAllOnReturn() {
		DriftRepaint plan = new DriftRepaint(0.05f, 4);
		assertEquals(0, plan.advance(0.06f, true));
		assertEquals(DriftRepaint.NONE, plan.advance(0.5f, false));
		assertEquals(DriftRepaint.NONE, plan.advance(90f, false));
		assertEquals(DriftRepaint.ALL, plan.advance(90.01f, true));
		assertEquals(90.01f, plan.target());
		assertEquals(DriftRepaint.NONE, plan.advance(90.02f, true));
	}

	@Test
	void aTurnRoundTheSkyIsNotAJump() {
		DriftRepaint plan = new DriftRepaint(0.05f, 1);
		assertEquals(0, plan.advance(359.9f, true));
		assertEquals(DriftRepaint.NONE, plan.advance(359.93f, true));
		assertEquals(0, plan.advance(359.99f, true));
		assertEquals(DriftRepaint.NONE, plan.advance(0.02f, true));
		assertEquals(0, plan.advance(0.05f, true));
	}

	@Test
	void aBigJumpIsRepaintedWhole() {
		DriftRepaint plan = new DriftRepaint(0.05f, 4);
		assertEquals(DriftRepaint.ALL, plan.advance(16f, true));
		assertEquals(DriftRepaint.NONE, plan.advance(16.01f, true));
		assertEquals(DriftRepaint.ALL, plan.advance(2f, true));
	}
}
