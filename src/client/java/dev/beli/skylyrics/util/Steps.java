package dev.beli.skylyrics.util;

import java.util.function.BiConsumer;

/** Runs the steps of one event one by one, so a step that throws does not stop the ones after it. */
public final class Steps {
	private Steps() {
	}

	/** Runs the step; a RuntimeException goes to onFailure with the step's name and the next step still runs. True when it did not throw. */
	public static boolean run(String name, Runnable step, BiConsumer<String, RuntimeException> onFailure) {
		try {
			step.run();
			return true;
		} catch (RuntimeException e) {
			onFailure.accept(name, e);
			return false;
		}
	}
}
