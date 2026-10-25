package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class WorldWordsTest {
	private static List<String> pieces(String line) {
		int[] cps = line.codePoints().toArray();
		List<String> out = new ArrayList<>();
		for (int[] c : WorldWords.chunks(line)) {
			out.add(new String(cps, c[0], c[1] - c[0]));
		}
		return out;
	}

	@Test
	void shortWordsLeanOnTheNextOne() {
		assertEquals(List.of("BABY,", "I'M HIGH", "OCTANE"), pieces("BABY, I'M HIGH OCTANE"));
		assertEquals(List.of("THAT,", "YOU WANNA", "GET OUT"), pieces("THAT, YOU WANNA GET OUT"));
		assertEquals(List.of("THAT", "YOU NEED", "IT RIGHT", "NOW"), pieces("THAT YOU NEED IT RIGHT NOW"));
	}

	@Test
	void neverMoreThanTwoWords() {
		assertEquals(List.of("I AM", "SO ON", "MY WAY"), pieces("I am so on my way").stream()
				.map(s -> s.toUpperCase(java.util.Locale.ROOT)).toList());
		assertEquals(List.of("ALONE"), pieces("  alone ").stream().map(s -> s.toUpperCase(java.util.Locale.ROOT)).toList());
		assertTrue(WorldWords.chunks("   ").isEmpty());
	}

	@Test
	void chunksPointAtTheirLetters() {
		List<int[]> c = WorldWords.chunks("GO HOME NOW");
		assertEquals(0, c.get(0)[0], "starts with the first letter");
		assertEquals(7, c.get(0)[1]);
		assertEquals(8, c.get(1)[0]);
	}

	@Test
	void olderCardsFade() {
		assertEquals(1, WorldWords.ageAlpha(0));
		assertEquals(0.7f, WorldWords.ageAlpha(1), 1e-6);
		assertEquals(0.5f, WorldWords.ageAlpha(2), 1e-6);
		assertEquals(0, WorldWords.ageAlpha(3), 1e-6);
		assertEquals(0, WorldWords.ageAlpha(5), 1e-6);
		float last = 2;
		for (float age = 0; age <= 3; age += 0.1f) {
			float a = WorldWords.ageAlpha(age);
			assertTrue(a <= last, "fades steadily");
			last = a;
		}
	}
}
