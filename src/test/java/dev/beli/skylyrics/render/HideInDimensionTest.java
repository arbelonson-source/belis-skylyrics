package dev.beli.skylyrics.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Hide in the Nether and Hide in the End on a server. Paper and Spigot (and Multiverse on them) name every world they
 * make beyond the first three after itself, so a resource Nether reaches the game as "minecraft:resource_nether"; only
 * its dimension type says it is a Nether. The game's own three worlds keep their usual names.
 */
class HideInDimensionTest {
	/** Level's own names load the game's particle registry. */
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final HolderOwner<DimensionType> OWNER = new HolderOwner<>() {
	};

	private static ResourceKey<Level> world(String name) {
		return ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace(name));
	}

	private static Holder<DimensionType> type(ResourceKey<DimensionType> key) {
		return Holder.Reference.createStandAlone(OWNER, key);
	}

	@Test
	void aServersOwnNetherWorldIsTheNether() {
		assertTrue(Visibility.nether(world("resource_nether"), type(BuiltinDimensionTypes.NETHER)));
		assertTrue(Visibility.nether(world("survival_nether"), type(BuiltinDimensionTypes.NETHER)));
		assertFalse(Visibility.end(world("resource_nether"), type(BuiltinDimensionTypes.NETHER)));
	}

	@Test
	void aServersOwnEndWorldIsTheEnd() {
		assertTrue(Visibility.end(world("resource_the_end"), type(BuiltinDimensionTypes.END)));
		assertFalse(Visibility.nether(world("resource_the_end"), type(BuiltinDimensionTypes.END)));
	}

	@Test
	void theGamesOwnWorldsStayAsTheyWere() {
		assertTrue(Visibility.nether(Level.NETHER, type(BuiltinDimensionTypes.NETHER)));
		assertTrue(Visibility.end(Level.END, type(BuiltinDimensionTypes.END)));
		assertFalse(Visibility.nether(Level.OVERWORLD, type(BuiltinDimensionTypes.OVERWORLD)));
		assertFalse(Visibility.end(Level.OVERWORLD, type(BuiltinDimensionTypes.OVERWORLD)));
		// A data pack's Nether with a type of its own is still the Nether by name
		assertTrue(Visibility.nether(Level.NETHER, type(ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath("pack", "hot")))));
	}

	@Test
	void aServersOtherWorldsAreNeither() {
		// A hub, a resource world or a cave world: Overworld types, whatever they are called
		for (String name : new String[] {"hub", "resource", "lobby_nether_portal"}) {
			assertFalse(Visibility.nether(world(name), type(BuiltinDimensionTypes.OVERWORLD)), name);
			assertFalse(Visibility.end(world(name), type(BuiltinDimensionTypes.OVERWORLD)), name);
		}
		assertFalse(Visibility.nether(world("caves"), type(BuiltinDimensionTypes.OVERWORLD_CAVES)));
	}
}
