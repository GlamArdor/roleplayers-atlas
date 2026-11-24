package glam.ardor.roleplayers_atlas.reloader;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biomes;

/**
 * Works out what a biome should look like from its name, for biomes that carry
 * no tags at all – which datapack biomes on a plugin server routinely don't.
 * <p>
 * Nothing but lookup tables, so it can be reasoned about (and checked) without
 * a running game.
 */
public final class BiomeNames {
	private BiomeNames() {
	}

	/**
	 * Words that stand for a look, most telling first. A token is matched by
	 * containment, so "forestq" and "hillsqq" still land where they should.
	 * <p>
	 * Order decides when one word contains two of these: "farmwood" is drawn as
	 * woodland rather than farmland because trees are the thing you would see
	 * from above.
	 */
	private static final List<Map.Entry<String, Identifier>> NAME_HINTS = List.of(
		Map.entry("mangrove", Biomes.MANGROVE_SWAMP.identifier()),
		Map.entry("crimson", Biomes.CRIMSON_FOREST.identifier()),
		Map.entry("warped", Biomes.WARPED_FOREST.identifier()),
		Map.entry("basalt", Biomes.BASALT_DELTAS.identifier()),
		Map.entry("soul", Biomes.SOUL_SAND_VALLEY.identifier()),
		Map.entry("nether", Biomes.NETHER_WASTES.identifier()),
		Map.entry("hell", Biomes.NETHER_WASTES.identifier()),
		Map.entry("inferno", Biomes.NETHER_WASTES.identifier()),
		Map.entry("sculk", Biomes.DEEP_DARK.identifier()),
		Map.entry("mushroom", Biomes.MUSHROOM_FIELDS.identifier()),
		Map.entry("shroom", Biomes.MUSHROOM_FIELDS.identifier()),
		Map.entry("fungal", Biomes.MUSHROOM_FIELDS.identifier()),
		Map.entry("fungus", Biomes.MUSHROOM_FIELDS.identifier()),
		Map.entry("lush", Biomes.LUSH_CAVES.identifier()),
		Map.entry("cavern", Biomes.DRIPSTONE_CAVES.identifier()),
		Map.entry("cave", Biomes.DRIPSTONE_CAVES.identifier()),
		Map.entry("grotto", Biomes.DRIPSTONE_CAVES.identifier()),
		Map.entry("undergroun", Biomes.DRIPSTONE_CAVES.identifier()),
		Map.entry("cherry", Biomes.CHERRY_GROVE.identifier()),
		Map.entry("sakura", Biomes.CHERRY_GROVE.identifier()),
		Map.entry("blossom", Biomes.CHERRY_GROVE.identifier()),
		Map.entry("birch", Biomes.BIRCH_FOREST.identifier()),
		Map.entry("spooky", Biomes.DARK_FOREST.identifier()),
		Map.entry("haunted", Biomes.DARK_FOREST.identifier()),
		Map.entry("gloom", Biomes.DARK_FOREST.identifier()),
		Map.entry("flower", Biomes.FLOWER_FOREST.identifier()),
		Map.entry("floral", Biomes.FLOWER_FOREST.identifier()),
		Map.entry("taiga", Biomes.TAIGA.identifier()),
		Map.entry("conifer", Biomes.TAIGA.identifier()),
		Map.entry("boreal", Biomes.TAIGA.identifier()),
		Map.entry("spruce", Biomes.TAIGA.identifier()),
		Map.entry("pine", Biomes.TAIGA.identifier()),
		Map.entry("rainforest", Biomes.JUNGLE.identifier()),
		Map.entry("jungle", Biomes.JUNGLE.identifier()),
		Map.entry("tropic", Biomes.JUNGLE.identifier()),
		Map.entry("bamboo", Biomes.BAMBOO_JUNGLE.identifier()),
		Map.entry("swamp", Biomes.SWAMP.identifier()),
		Map.entry("wetland", Biomes.SWAMP.identifier()),
		Map.entry("marsh", Biomes.SWAMP.identifier()),
		Map.entry("mire", Biomes.SWAMP.identifier()),
		Map.entry("moor", Biomes.SWAMP.identifier()),
		Map.entry("bog", Biomes.SWAMP.identifier()),
		Map.entry("badland", Biomes.BADLANDS.identifier()),
		Map.entry("mesa", Biomes.BADLANDS.identifier()),
		Map.entry("canyon", Biomes.BADLANDS.identifier()),
		Map.entry("waste", Biomes.BADLANDS.identifier()),
		Map.entry("barren", Biomes.BADLANDS.identifier()),
		Map.entry("blight", Biomes.BADLANDS.identifier()),
		Map.entry("savanna", Biomes.SAVANNA.identifier()),
		Map.entry("steppe", Biomes.SAVANNA.identifier()),
		Map.entry("desert", Biomes.DESERT.identifier()),
		Map.entry("dune", Biomes.DESERT.identifier()),
		Map.entry("arid", Biomes.DESERT.identifier()),
		Map.entry("sand", Biomes.DESERT.identifier()),
		Map.entry("volcan", Biomes.BASALT_DELTAS.identifier()),
		Map.entry("magma", Biomes.BASALT_DELTAS.identifier()),
		Map.entry("lava", Biomes.BASALT_DELTAS.identifier()),
		Map.entry("ember", Biomes.BASALT_DELTAS.identifier()),
		Map.entry("cinder", Biomes.BASALT_DELTAS.identifier()),
		Map.entry("spike", Biomes.ICE_SPIKES.identifier()),
		Map.entry("glacier", Biomes.SNOWY_PLAINS.identifier()),
		Map.entry("tundra", Biomes.SNOWY_PLAINS.identifier()),
		Map.entry("frozen", Biomes.SNOWY_PLAINS.identifier()),
		Map.entry("frost", Biomes.SNOWY_PLAINS.identifier()),
		Map.entry("snow", Biomes.SNOWY_PLAINS.identifier()),
		Map.entry("summit", Biomes.JAGGED_PEAKS.identifier()),
		Map.entry("peak", Biomes.JAGGED_PEAKS.identifier()),
		Map.entry("mountain", Biomes.STONY_PEAKS.identifier()),
		Map.entry("highland", Biomes.STONY_PEAKS.identifier()),
		Map.entry("crag", Biomes.STONY_PEAKS.identifier()),
		Map.entry("cliff", Biomes.WINDSWEPT_GRAVELLY_HILLS.identifier()),
		Map.entry("bluff", Biomes.WINDSWEPT_GRAVELLY_HILLS.identifier()),
		Map.entry("slope", Biomes.STONY_PEAKS.identifier()),
		Map.entry("windswept", Biomes.WINDSWEPT_HILLS.identifier()),
		// Not the bare windswept stone: the map already draws relief from the
		// land's own height, so a name saying "hills" adds nothing and only
		// buries a wooded, gentle place under a wall of rock.
		Map.entry("hill", Biomes.PLAINS.identifier()),
		Map.entry("knoll", Biomes.PLAINS.identifier()),
		Map.entry("down", Biomes.PLAINS.identifier()),
		Map.entry("island", Biomes.BEACH.identifier()),
		Map.entry("isle", Biomes.BEACH.identifier()),
		Map.entry("atoll", Biomes.BEACH.identifier()),
		Map.entry("plateau", Biomes.MEADOW.identifier()),
		Map.entry("meadow", Biomes.MEADOW.identifier()),
		Map.entry("pasture", Biomes.MEADOW.identifier()),
		Map.entry("heath", Biomes.MEADOW.identifier()),
		Map.entry("beach", Biomes.BEACH.identifier()),
		Map.entry("shore", Biomes.BEACH.identifier()),
		Map.entry("coast", Biomes.BEACH.identifier()),
		Map.entry("strand", Biomes.BEACH.identifier()),
		Map.entry("ocean", Biomes.OCEAN.identifier()),
		Map.entry("abyss", Biomes.DEEP_OCEAN.identifier()),
		Map.entry("river", Biomes.RIVER.identifier()),
		Map.entry("creek", Biomes.RIVER.identifier()),
		Map.entry("stream", Biomes.RIVER.identifier()),
		Map.entry("brook", Biomes.RIVER.identifier()),
		Map.entry("lagoon", Biomes.RIVER.identifier()),
		Map.entry("lake", Biomes.RIVER.identifier()),
		Map.entry("pond", Biomes.RIVER.identifier()),
		Map.entry("sunflower", Biomes.SUNFLOWER_PLAINS.identifier()),
		Map.entry("forest", Biomes.FOREST.identifier()),
		Map.entry("woodland", Biomes.FOREST.identifier()),
		Map.entry("wood", Biomes.FOREST.identifier()),
		Map.entry("thicket", Biomes.FOREST.identifier()),
		Map.entry("copse", Biomes.FOREST.identifier()),
		Map.entry("grove", Biomes.GROVE.identifier()),
		Map.entry("glade", Biomes.FOREST.identifier()),
		Map.entry("timber", Biomes.FOREST.identifier()),
		Map.entry("orchard", Biomes.PLAINS.identifier()),
		Map.entry("garden", Biomes.PLAINS.identifier()),
		Map.entry("farm", Biomes.PLAINS.identifier()),
		Map.entry("wheat", Biomes.PLAINS.identifier()),
		Map.entry("barley", Biomes.PLAINS.identifier()),
		Map.entry("rye", Biomes.PLAINS.identifier()),
		Map.entry("prairie", Biomes.PLAINS.identifier()),
		Map.entry("grassland", Biomes.PLAINS.identifier()),
		Map.entry("midland", Biomes.PLAINS.identifier()),
		Map.entry("lowland", Biomes.PLAINS.identifier()),
		Map.entry("plain", Biomes.PLAINS.identifier()),
		Map.entry("field", Biomes.PLAINS.identifier()),
		Map.entry("vale", Biomes.PLAINS.identifier()),
		Map.entry("valley", Biomes.PLAINS.identifier()),
		Map.entry("grass", Biomes.PLAINS.identifier())
	);

	/** Words too short or too common to be matched anywhere but on their own. */
	private static final Map<String, Identifier> WHOLE_WORD_HINTS = Map.of(
		"end", Biomes.THE_END.identifier(),
		"void", Biomes.THE_VOID.identifier(),
		"fen", Biomes.SWAMP.identifier(),
		"ice", Biomes.SNOWY_PLAINS.identifier(),
		"sea", Biomes.OCEAN.identifier(),
		"ash", Biomes.BASALT_DELTAS.identifier(),
		"dead", Biomes.BADLANDS.identifier(),
		"oak", Biomes.FOREST.identifier(),
		"dark", Biomes.DARK_FOREST.identifier(),
		"deep", Biomes.DEEP_OCEAN.identifier()
	);

	/**
	 * What a word standing before the noun narrows it down to: "sakura_forest"
	 * is a cherry grove, while "blue_forest" stays a forest because nothing here
	 * answers to blue.
	 * <p>
	 * Applied over and over until nothing more matches, so several words can
	 * stack: "deep" turns an ocean into a deep one, and "cold" then turns that
	 * into a deep cold one.
	 */
	private static final Map<Identifier, Map<String, Identifier>> REFINEMENTS = Map.ofEntries(
		Map.entry(Biomes.FOREST.identifier(), Map.ofEntries(
			Map.entry("windswept", Biomes.WINDSWEPT_FOREST.identifier()),
			Map.entry("old", Biomes.OLD_GROWTH_BIRCH_FOREST.identifier()),
			Map.entry("ancient", Biomes.OLD_GROWTH_BIRCH_FOREST.identifier()),
			Map.entry("primeval", Biomes.OLD_GROWTH_BIRCH_FOREST.identifier()),
			Map.entry("primal", Biomes.OLD_GROWTH_BIRCH_FOREST.identifier()),
			Map.entry("elder", Biomes.OLD_GROWTH_BIRCH_FOREST.identifier()),
			Map.entry("birch", Biomes.BIRCH_FOREST.identifier()),
			Map.entry("dark", Biomes.DARK_FOREST.identifier()),
			Map.entry("spooky", Biomes.DARK_FOREST.identifier()),
			Map.entry("haunted", Biomes.DARK_FOREST.identifier()),
			Map.entry("flower", Biomes.FLOWER_FOREST.identifier()),
			Map.entry("floral", Biomes.FLOWER_FOREST.identifier()),
			Map.entry("cherry", Biomes.CHERRY_GROVE.identifier()),
			Map.entry("sakura", Biomes.CHERRY_GROVE.identifier()),
			Map.entry("blossom", Biomes.CHERRY_GROVE.identifier()),
			Map.entry("taiga", Biomes.TAIGA.identifier()),
			Map.entry("conifer", Biomes.TAIGA.identifier()),
			Map.entry("boreal", Biomes.TAIGA.identifier()),
			Map.entry("spruce", Biomes.TAIGA.identifier()),
			Map.entry("pine", Biomes.TAIGA.identifier()),
			Map.entry("jungle", Biomes.JUNGLE.identifier()),
			Map.entry("rainforest", Biomes.JUNGLE.identifier()),
			Map.entry("bamboo", Biomes.BAMBOO_JUNGLE.identifier()),
			Map.entry("crimson", Biomes.CRIMSON_FOREST.identifier()),
			Map.entry("warped", Biomes.WARPED_FOREST.identifier()),
			Map.entry("mangrove", Biomes.MANGROVE_SWAMP.identifier()),
			Map.entry("grove", Biomes.GROVE.identifier()))),
		Map.entry(Biomes.BIRCH_FOREST.identifier(), Map.of(
			"old", Biomes.OLD_GROWTH_BIRCH_FOREST.identifier(),
			"ancient", Biomes.OLD_GROWTH_BIRCH_FOREST.identifier(),
			"primeval", Biomes.OLD_GROWTH_BIRCH_FOREST.identifier())),
		Map.entry(Biomes.TAIGA.identifier(), Map.of(
			"old", Biomes.OLD_GROWTH_SPRUCE_TAIGA.identifier(),
			"ancient", Biomes.OLD_GROWTH_SPRUCE_TAIGA.identifier(),
			"primeval", Biomes.OLD_GROWTH_SPRUCE_TAIGA.identifier())),
		Map.entry(Biomes.OLD_GROWTH_SPRUCE_TAIGA.identifier(), Map.of(
			"pine", Biomes.OLD_GROWTH_PINE_TAIGA.identifier())),
		Map.entry(Biomes.JUNGLE.identifier(), Map.of(
			"sparse", Biomes.SPARSE_JUNGLE.identifier(),
			"thin", Biomes.SPARSE_JUNGLE.identifier(),
			"scattered", Biomes.SPARSE_JUNGLE.identifier(),
			"bamboo", Biomes.BAMBOO_JUNGLE.identifier())),
		Map.entry(Biomes.BADLANDS.identifier(), Map.of(
			"eroded", Biomes.ERODED_BADLANDS.identifier(),
			"weathered", Biomes.ERODED_BADLANDS.identifier(),
			"cracked", Biomes.ERODED_BADLANDS.identifier(),
			"wooded", Biomes.WOODED_BADLANDS.identifier(),
			"forested", Biomes.WOODED_BADLANDS.identifier(),
			"treed", Biomes.WOODED_BADLANDS.identifier())),
		Map.entry(Biomes.SAVANNA.identifier(), Map.of(
			"plateau", Biomes.SAVANNA_PLATEAU.identifier(),
			"windswept", Biomes.WINDSWEPT_SAVANNA.identifier())),
		Map.entry(Biomes.OCEAN.identifier(), Map.of(
			"warm", Biomes.WARM_OCEAN.identifier(),
			"tepid", Biomes.LUKEWARM_OCEAN.identifier(),
			"lukewarm", Biomes.LUKEWARM_OCEAN.identifier(),
			"mild", Biomes.LUKEWARM_OCEAN.identifier(),
			"cold", Biomes.COLD_OCEAN.identifier(),
			"chill", Biomes.COLD_OCEAN.identifier(),
			"deep", Biomes.DEEP_OCEAN.identifier(),
			"abyss", Biomes.DEEP_OCEAN.identifier())),
		Map.entry(Biomes.DEEP_OCEAN.identifier(), Map.of(
			"cold", Biomes.DEEP_COLD_OCEAN.identifier(),
			"chill", Biomes.DEEP_COLD_OCEAN.identifier(),
			"lukewarm", Biomes.DEEP_LUKEWARM_OCEAN.identifier(),
			"tepid", Biomes.DEEP_LUKEWARM_OCEAN.identifier(),
			"mild", Biomes.DEEP_LUKEWARM_OCEAN.identifier())),
		// An island is coastline unless the name says what grows on it – the
		// shore is the last thing you would draw a tropical island as.
		Map.entry(Biomes.BEACH.identifier(), Map.ofEntries(
			Map.entry("stony", Biomes.STONY_SHORE.identifier()),
			Map.entry("stone", Biomes.STONY_SHORE.identifier()),
			Map.entry("rocky", Biomes.STONY_SHORE.identifier()),
			Map.entry("pebble", Biomes.STONY_SHORE.identifier()),
			Map.entry("shingle", Biomes.STONY_SHORE.identifier()),
			Map.entry("tropic", Biomes.JUNGLE.identifier()),
			Map.entry("jungle", Biomes.JUNGLE.identifier()),
			Map.entry("rainforest", Biomes.JUNGLE.identifier()),
			Map.entry("bamboo", Biomes.BAMBOO_JUNGLE.identifier()),
			Map.entry("forest", Biomes.FOREST.identifier()),
			Map.entry("wood", Biomes.FOREST.identifier()),
			Map.entry("mushroom", Biomes.MUSHROOM_FIELDS.identifier()),
			Map.entry("volcan", Biomes.BASALT_DELTAS.identifier()))),
		Map.entry(Biomes.WINDSWEPT_HILLS.identifier(), Map.of(
			"gravel", Biomes.WINDSWEPT_GRAVELLY_HILLS.identifier(),
			"gravelly", Biomes.WINDSWEPT_GRAVELLY_HILLS.identifier(),
			"scree", Biomes.WINDSWEPT_GRAVELLY_HILLS.identifier())),
		Map.entry(Biomes.DARK_FOREST.identifier(), Map.of(
			"deep", Biomes.DEEP_DARK.identifier(),
			"sculk", Biomes.DEEP_DARK.identifier())),
		Map.entry(Biomes.MEADOW.identifier(), Map.of(
			"savanna", Biomes.SAVANNA_PLATEAU.identifier(),
			"steppe", Biomes.SAVANNA_PLATEAU.identifier())),
		Map.entry(Biomes.GROVE.identifier(), Map.of(
			"cherry", Biomes.CHERRY_GROVE.identifier(),
			"sakura", Biomes.CHERRY_GROVE.identifier(),
			"blossom", Biomes.CHERRY_GROVE.identifier())),
		Map.entry(Biomes.SWAMP.identifier(), Map.of(
			"mangrove", Biomes.MANGROVE_SWAMP.identifier())),
		Map.entry(Biomes.DRIPSTONE_CAVES.identifier(), Map.of(
			"lush", Biomes.LUSH_CAVES.identifier(),
			"verdant", Biomes.LUSH_CAVES.identifier(),
			"overgrown", Biomes.LUSH_CAVES.identifier(),
			"mossy", Biomes.LUSH_CAVES.identifier())),
		Map.entry(Biomes.JAGGED_PEAKS.identifier(), Map.of(
			"stony", Biomes.STONY_PEAKS.identifier(),
			"stone", Biomes.STONY_PEAKS.identifier(),
			"rocky", Biomes.STONY_PEAKS.identifier())),
		Map.entry(Biomes.PLAINS.identifier(), Map.ofEntries(
			Map.entry("sunflower", Biomes.SUNFLOWER_PLAINS.identifier()),
			Map.entry("meadow", Biomes.MEADOW.identifier()),
			Map.entry("pasture", Biomes.MEADOW.identifier()),
			Map.entry("heath", Biomes.MEADOW.identifier()),
			Map.entry("flower", Biomes.FLOWER_FOREST.identifier()),
			Map.entry("floral", Biomes.FLOWER_FOREST.identifier()),
			Map.entry("cherry", Biomes.CHERRY_GROVE.identifier()),
			Map.entry("sakura", Biomes.CHERRY_GROVE.identifier()),
			Map.entry("blossom", Biomes.CHERRY_GROVE.identifier()),
			Map.entry("savanna", Biomes.SAVANNA.identifier()),
			Map.entry("steppe", Biomes.SAVANNA.identifier()),
			Map.entry("mushroom", Biomes.MUSHROOM_FIELDS.identifier()),
			Map.entry("shroom", Biomes.MUSHROOM_FIELDS.identifier()),
			Map.entry("soul", Biomes.SOUL_SAND_VALLEY.identifier()),
			// "windswept" is the one word that does mean bare stone.
			Map.entry("windswept", Biomes.WINDSWEPT_HILLS.identifier()),
			// Hills and fields are shapes, not cover – whatever grows on them is
			// said by the word in front, and that is what should be drawn.
			Map.entry("forest", Biomes.FOREST.identifier()),
			Map.entry("wood", Biomes.FOREST.identifier()),
			Map.entry("thicket", Biomes.FOREST.identifier()),
			Map.entry("birch", Biomes.BIRCH_FOREST.identifier()),
			Map.entry("taiga", Biomes.TAIGA.identifier()),
			Map.entry("conifer", Biomes.TAIGA.identifier()),
			Map.entry("boreal", Biomes.TAIGA.identifier()),
			Map.entry("spruce", Biomes.TAIGA.identifier()),
			Map.entry("pine", Biomes.TAIGA.identifier()),
			Map.entry("jungle", Biomes.JUNGLE.identifier()),
			Map.entry("rainforest", Biomes.JUNGLE.identifier()),
			Map.entry("desert", Biomes.DESERT.identifier()),
			Map.entry("dune", Biomes.DESERT.identifier()),
			Map.entry("swamp", Biomes.SWAMP.identifier()),
			Map.entry("marsh", Biomes.SWAMP.identifier()),
			Map.entry("bog", Biomes.SWAMP.identifier()),
			Map.entry("mire", Biomes.SWAMP.identifier()),
			Map.entry("badland", Biomes.BADLANDS.identifier()),
			Map.entry("mesa", Biomes.BADLANDS.identifier())))
	);

	/**
	 * The other two dimensions read nothing like the overworld, so a name that
	 * says which one it belongs to overrules the shape it settled on: an "end
	 * highland" is not a mountain, it is the pale stone of the End.
	 */
	private static final Set<String> END_WORDS = Set.of("end", "ender", "outer");
	private static final Set<String> NETHER_WORDS = Set.of("nether", "hell", "hellish", "inferno", "infernal");

	private static final Map<Identifier, Identifier> END_FORMS = Map.ofEntries(
		Map.entry(Biomes.STONY_PEAKS.identifier(), Biomes.END_HIGHLANDS.identifier()),
		Map.entry(Biomes.JAGGED_PEAKS.identifier(), Biomes.END_HIGHLANDS.identifier()),
		Map.entry(Biomes.WINDSWEPT_HILLS.identifier(), Biomes.END_HIGHLANDS.identifier()),
		Map.entry(Biomes.PLAINS.identifier(), Biomes.END_MIDLANDS.identifier()),
		Map.entry(Biomes.MEADOW.identifier(), Biomes.END_MIDLANDS.identifier()),
		Map.entry(Biomes.FOREST.identifier(), Biomes.END_MIDLANDS.identifier()),
		Map.entry(Biomes.BADLANDS.identifier(), Biomes.END_BARRENS.identifier()),
		Map.entry(Biomes.DESERT.identifier(), Biomes.END_BARRENS.identifier()),
		Map.entry(Biomes.BEACH.identifier(), Biomes.SMALL_END_ISLANDS.identifier()),
		Map.entry(Biomes.OCEAN.identifier(), Biomes.SMALL_END_ISLANDS.identifier()),
		Map.entry(Biomes.THE_VOID.identifier(), Biomes.THE_VOID.identifier())
	);

	private static final Map<Identifier, Identifier> NETHER_FORMS = Map.of(
		Biomes.FOREST.identifier(), Biomes.WARPED_FOREST.identifier(),
		Biomes.TAIGA.identifier(), Biomes.WARPED_FOREST.identifier(),
		Biomes.JUNGLE.identifier(), Biomes.CRIMSON_FOREST.identifier(),
		Biomes.MUSHROOM_FIELDS.identifier(), Biomes.CRIMSON_FOREST.identifier(),
		Biomes.DESERT.identifier(), Biomes.SOUL_SAND_VALLEY.identifier(),
		Biomes.BEACH.identifier(), Biomes.SOUL_SAND_VALLEY.identifier(),
		Biomes.STONY_PEAKS.identifier(), Biomes.BASALT_DELTAS.identifier(),
		Biomes.WINDSWEPT_HILLS.identifier(), Biomes.BASALT_DELTAS.identifier(),
		Biomes.OCEAN.identifier(), Biomes.BASALT_DELTAS.identifier(),
		Biomes.RIVER.identifier(), Biomes.BASALT_DELTAS.identifier()
	);

	/** Snowbound counterparts, for when a name says both what a place is and that it is frozen. */
	private static final Map<Identifier, Identifier> SNOWY_FORMS = Map.of(
		Biomes.PLAINS.identifier(), Biomes.SNOWY_PLAINS.identifier(),
		Biomes.TAIGA.identifier(), Biomes.SNOWY_TAIGA.identifier(),
		Biomes.BEACH.identifier(), Biomes.SNOWY_BEACH.identifier(),
		Biomes.OCEAN.identifier(), Biomes.FROZEN_OCEAN.identifier(),
		Biomes.DEEP_OCEAN.identifier(), Biomes.DEEP_FROZEN_OCEAN.identifier(),
		Biomes.RIVER.identifier(), Biomes.FROZEN_RIVER.identifier(),
		Biomes.JAGGED_PEAKS.identifier(), Biomes.FROZEN_PEAKS.identifier(),
		Biomes.STONY_PEAKS.identifier(), Biomes.SNOWY_SLOPES.identifier(),
		Biomes.MEADOW.identifier(), Biomes.SNOWY_SLOPES.identifier(),
		Biomes.FOREST.identifier(), Biomes.SNOWY_TAIGA.identifier()
	);

	private static final Set<String> SNOWY_WORDS = Set.of("snow", "snowy", "frozen", "frost", "frosty", "icy", "ice", "glacial", "glacier", "tundra", "arctic", "polar", "winter");

	/**
	 * Guesses a look from the biome's name when it carries no tags at all.
	 * <p>
	 * Names are read back to front, because English puts the noun last: a
	 * "desert_swamp" is a swamp, not a desert. A word that means nothing here is
	 * simply skipped, so "goldenrod_forest_autumn" still finds its forest.
	 */
	public static @Nullable Identifier guess(Identifier biomeId) {
		String[] words = biomeId.getPath().toLowerCase(Locale.ROOT).split("[^a-z]+");
		Identifier found = null;
		int foundAt = -1;
		for (int i = words.length - 1; i >= 0 && found == null; i--) {
			found = match(words[i]);
			if (found != null) foundAt = i;
		}

		boolean end = false;
		boolean nether = false;
		for (String word : words) {
			if (END_WORDS.contains(word)) end = true;
			if (NETHER_WORDS.contains(word)) nether = true;
		}
		// "the_end" alone says where but not what; the dimension is the answer.
		if (found == null) return end ? Biomes.THE_END.identifier() : nether ? Biomes.NETHER_WASTES.identifier() : null;

		// Words standing before the noun narrow it down, and they stack – each
		// pass reconsiders the whole name against what it has become so far.
		for (int pass = 0; pass < 4; pass++) {
			Map<String, Identifier> refinements = REFINEMENTS.get(found);
			if (refinements == null) break;
			Identifier next = null;
			for (int i = 0; i < foundAt && next == null; i++) {
				next = lookup(refinements, words[i]);
			}
			if (next == null || next.equals(found)) break;
			found = next;
		}

		if (end) return END_FORMS.getOrDefault(found, Biomes.THE_END.identifier());
		if (nether) return NETHER_FORMS.getOrDefault(found, Biomes.NETHER_WASTES.identifier());

		for (String word : words) {
			if (SNOWY_WORDS.contains(word)) return SNOWY_FORMS.getOrDefault(found, found);
		}
		return found;
	}

	/** Refinements match a whole word, or a word grown from one – "wooded" from "wood". */
	private static @Nullable Identifier lookup(Map<String, Identifier> refinements, String word) {
		Identifier exact = refinements.get(word);
		if (exact != null) return exact;
		for (Map.Entry<String, Identifier> entry : refinements.entrySet()) {
			if (word.startsWith(entry.getKey())) return entry.getValue();
		}
		return null;
	}

	private static @Nullable Identifier match(String word) {
		if (word.isEmpty()) return null;
		Identifier whole = WHOLE_WORD_HINTS.get(word);
		if (whole != null) return whole;
		for (Map.Entry<String, Identifier> hint : NAME_HINTS) {
			if (word.contains(hint.getKey())) return hint.getValue();
		}
		return null;
	}

}
