package glam.ardor.roleplayers_atlas.reloader;

import net.minecraft.util.Identifier;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
		Map.entry("mangrove", BiomeKeys.MANGROVE_SWAMP.getValue()),
		Map.entry("crimson", BiomeKeys.CRIMSON_FOREST.getValue()),
		Map.entry("warped", BiomeKeys.WARPED_FOREST.getValue()),
		Map.entry("basalt", BiomeKeys.BASALT_DELTAS.getValue()),
		Map.entry("soul", BiomeKeys.SOUL_SAND_VALLEY.getValue()),
		Map.entry("nether", BiomeKeys.NETHER_WASTES.getValue()),
		Map.entry("hell", BiomeKeys.NETHER_WASTES.getValue()),
		Map.entry("inferno", BiomeKeys.NETHER_WASTES.getValue()),
		Map.entry("sculk", BiomeKeys.DEEP_DARK.getValue()),
		Map.entry("mushroom", BiomeKeys.MUSHROOM_FIELDS.getValue()),
		Map.entry("shroom", BiomeKeys.MUSHROOM_FIELDS.getValue()),
		Map.entry("fungal", BiomeKeys.MUSHROOM_FIELDS.getValue()),
		Map.entry("fungus", BiomeKeys.MUSHROOM_FIELDS.getValue()),
		Map.entry("lush", BiomeKeys.LUSH_CAVES.getValue()),
		Map.entry("cavern", BiomeKeys.DRIPSTONE_CAVES.getValue()),
		Map.entry("cave", BiomeKeys.DRIPSTONE_CAVES.getValue()),
		Map.entry("grotto", BiomeKeys.DRIPSTONE_CAVES.getValue()),
		Map.entry("undergroun", BiomeKeys.DRIPSTONE_CAVES.getValue()),
		Map.entry("cherry", BiomeKeys.CHERRY_GROVE.getValue()),
		Map.entry("sakura", BiomeKeys.CHERRY_GROVE.getValue()),
		Map.entry("blossom", BiomeKeys.CHERRY_GROVE.getValue()),
		Map.entry("birch", BiomeKeys.BIRCH_FOREST.getValue()),
		Map.entry("spooky", BiomeKeys.DARK_FOREST.getValue()),
		Map.entry("haunted", BiomeKeys.DARK_FOREST.getValue()),
		Map.entry("gloom", BiomeKeys.DARK_FOREST.getValue()),
		Map.entry("flower", BiomeKeys.FLOWER_FOREST.getValue()),
		Map.entry("floral", BiomeKeys.FLOWER_FOREST.getValue()),
		Map.entry("taiga", BiomeKeys.TAIGA.getValue()),
		Map.entry("conifer", BiomeKeys.TAIGA.getValue()),
		Map.entry("boreal", BiomeKeys.TAIGA.getValue()),
		Map.entry("spruce", BiomeKeys.TAIGA.getValue()),
		Map.entry("pine", BiomeKeys.TAIGA.getValue()),
		Map.entry("rainforest", BiomeKeys.JUNGLE.getValue()),
		Map.entry("jungle", BiomeKeys.JUNGLE.getValue()),
		Map.entry("tropic", BiomeKeys.JUNGLE.getValue()),
		Map.entry("bamboo", BiomeKeys.BAMBOO_JUNGLE.getValue()),
		Map.entry("swamp", BiomeKeys.SWAMP.getValue()),
		Map.entry("wetland", BiomeKeys.SWAMP.getValue()),
		Map.entry("marsh", BiomeKeys.SWAMP.getValue()),
		Map.entry("mire", BiomeKeys.SWAMP.getValue()),
		Map.entry("moor", BiomeKeys.SWAMP.getValue()),
		Map.entry("bog", BiomeKeys.SWAMP.getValue()),
		Map.entry("badland", BiomeKeys.BADLANDS.getValue()),
		Map.entry("mesa", BiomeKeys.BADLANDS.getValue()),
		Map.entry("canyon", BiomeKeys.BADLANDS.getValue()),
		Map.entry("waste", BiomeKeys.BADLANDS.getValue()),
		Map.entry("barren", BiomeKeys.BADLANDS.getValue()),
		Map.entry("blight", BiomeKeys.BADLANDS.getValue()),
		Map.entry("savanna", BiomeKeys.SAVANNA.getValue()),
		Map.entry("steppe", BiomeKeys.SAVANNA.getValue()),
		Map.entry("desert", BiomeKeys.DESERT.getValue()),
		Map.entry("dune", BiomeKeys.DESERT.getValue()),
		Map.entry("arid", BiomeKeys.DESERT.getValue()),
		Map.entry("sand", BiomeKeys.DESERT.getValue()),
		Map.entry("volcan", BiomeKeys.BASALT_DELTAS.getValue()),
		Map.entry("magma", BiomeKeys.BASALT_DELTAS.getValue()),
		Map.entry("lava", BiomeKeys.BASALT_DELTAS.getValue()),
		Map.entry("ember", BiomeKeys.BASALT_DELTAS.getValue()),
		Map.entry("cinder", BiomeKeys.BASALT_DELTAS.getValue()),
		Map.entry("spike", BiomeKeys.ICE_SPIKES.getValue()),
		Map.entry("glacier", BiomeKeys.SNOWY_PLAINS.getValue()),
		Map.entry("tundra", BiomeKeys.SNOWY_PLAINS.getValue()),
		Map.entry("frozen", BiomeKeys.SNOWY_PLAINS.getValue()),
		Map.entry("frost", BiomeKeys.SNOWY_PLAINS.getValue()),
		Map.entry("snow", BiomeKeys.SNOWY_PLAINS.getValue()),
		Map.entry("summit", BiomeKeys.JAGGED_PEAKS.getValue()),
		Map.entry("peak", BiomeKeys.JAGGED_PEAKS.getValue()),
		Map.entry("mountain", BiomeKeys.STONY_PEAKS.getValue()),
		Map.entry("highland", BiomeKeys.STONY_PEAKS.getValue()),
		Map.entry("crag", BiomeKeys.STONY_PEAKS.getValue()),
		Map.entry("cliff", BiomeKeys.WINDSWEPT_GRAVELLY_HILLS.getValue()),
		Map.entry("bluff", BiomeKeys.WINDSWEPT_GRAVELLY_HILLS.getValue()),
		Map.entry("slope", BiomeKeys.STONY_PEAKS.getValue()),
		Map.entry("windswept", BiomeKeys.WINDSWEPT_HILLS.getValue()),
		// Not the bare windswept stone: the map already draws relief from the
		// land's own height, so a name saying "hills" adds nothing and only
		// buries a wooded, gentle place under a wall of rock.
		Map.entry("hill", BiomeKeys.PLAINS.getValue()),
		Map.entry("knoll", BiomeKeys.PLAINS.getValue()),
		Map.entry("down", BiomeKeys.PLAINS.getValue()),
		Map.entry("island", BiomeKeys.BEACH.getValue()),
		Map.entry("isle", BiomeKeys.BEACH.getValue()),
		Map.entry("atoll", BiomeKeys.BEACH.getValue()),
		Map.entry("plateau", BiomeKeys.MEADOW.getValue()),
		Map.entry("meadow", BiomeKeys.MEADOW.getValue()),
		Map.entry("pasture", BiomeKeys.MEADOW.getValue()),
		Map.entry("heath", BiomeKeys.MEADOW.getValue()),
		Map.entry("beach", BiomeKeys.BEACH.getValue()),
		Map.entry("shore", BiomeKeys.BEACH.getValue()),
		Map.entry("coast", BiomeKeys.BEACH.getValue()),
		Map.entry("strand", BiomeKeys.BEACH.getValue()),
		Map.entry("ocean", BiomeKeys.OCEAN.getValue()),
		Map.entry("abyss", BiomeKeys.DEEP_OCEAN.getValue()),
		Map.entry("river", BiomeKeys.RIVER.getValue()),
		Map.entry("creek", BiomeKeys.RIVER.getValue()),
		Map.entry("stream", BiomeKeys.RIVER.getValue()),
		Map.entry("brook", BiomeKeys.RIVER.getValue()),
		Map.entry("lagoon", BiomeKeys.RIVER.getValue()),
		Map.entry("lake", BiomeKeys.RIVER.getValue()),
		Map.entry("pond", BiomeKeys.RIVER.getValue()),
		Map.entry("sunflower", BiomeKeys.SUNFLOWER_PLAINS.getValue()),
		Map.entry("forest", BiomeKeys.FOREST.getValue()),
		Map.entry("woodland", BiomeKeys.FOREST.getValue()),
		Map.entry("wood", BiomeKeys.FOREST.getValue()),
		Map.entry("thicket", BiomeKeys.FOREST.getValue()),
		Map.entry("copse", BiomeKeys.FOREST.getValue()),
		Map.entry("grove", BiomeKeys.GROVE.getValue()),
		Map.entry("glade", BiomeKeys.FOREST.getValue()),
		Map.entry("timber", BiomeKeys.FOREST.getValue()),
		Map.entry("orchard", BiomeKeys.PLAINS.getValue()),
		Map.entry("garden", BiomeKeys.PLAINS.getValue()),
		Map.entry("farm", BiomeKeys.PLAINS.getValue()),
		Map.entry("wheat", BiomeKeys.PLAINS.getValue()),
		Map.entry("barley", BiomeKeys.PLAINS.getValue()),
		Map.entry("rye", BiomeKeys.PLAINS.getValue()),
		Map.entry("prairie", BiomeKeys.PLAINS.getValue()),
		Map.entry("grassland", BiomeKeys.PLAINS.getValue()),
		Map.entry("midland", BiomeKeys.PLAINS.getValue()),
		Map.entry("lowland", BiomeKeys.PLAINS.getValue()),
		Map.entry("plain", BiomeKeys.PLAINS.getValue()),
		Map.entry("field", BiomeKeys.PLAINS.getValue()),
		Map.entry("vale", BiomeKeys.PLAINS.getValue()),
		Map.entry("valley", BiomeKeys.PLAINS.getValue()),
		Map.entry("grass", BiomeKeys.PLAINS.getValue())
	);

	/** Words too short or too common to be matched anywhere but on their own. */
	private static final Map<String, Identifier> WHOLE_WORD_HINTS = Map.of(
		"end", BiomeKeys.THE_END.getValue(),
		"void", BiomeKeys.THE_VOID.getValue(),
		"fen", BiomeKeys.SWAMP.getValue(),
		"ice", BiomeKeys.SNOWY_PLAINS.getValue(),
		"sea", BiomeKeys.OCEAN.getValue(),
		"ash", BiomeKeys.BASALT_DELTAS.getValue(),
		"dead", BiomeKeys.BADLANDS.getValue(),
		"oak", BiomeKeys.FOREST.getValue(),
		"dark", BiomeKeys.DARK_FOREST.getValue(),
		"deep", BiomeKeys.DEEP_OCEAN.getValue()
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
		Map.entry(BiomeKeys.FOREST.getValue(), Map.ofEntries(
			Map.entry("windswept", BiomeKeys.WINDSWEPT_FOREST.getValue()),
			Map.entry("old", BiomeKeys.OLD_GROWTH_BIRCH_FOREST.getValue()),
			Map.entry("ancient", BiomeKeys.OLD_GROWTH_BIRCH_FOREST.getValue()),
			Map.entry("primeval", BiomeKeys.OLD_GROWTH_BIRCH_FOREST.getValue()),
			Map.entry("primal", BiomeKeys.OLD_GROWTH_BIRCH_FOREST.getValue()),
			Map.entry("elder", BiomeKeys.OLD_GROWTH_BIRCH_FOREST.getValue()),
			Map.entry("birch", BiomeKeys.BIRCH_FOREST.getValue()),
			Map.entry("dark", BiomeKeys.DARK_FOREST.getValue()),
			Map.entry("spooky", BiomeKeys.DARK_FOREST.getValue()),
			Map.entry("haunted", BiomeKeys.DARK_FOREST.getValue()),
			Map.entry("flower", BiomeKeys.FLOWER_FOREST.getValue()),
			Map.entry("floral", BiomeKeys.FLOWER_FOREST.getValue()),
			Map.entry("cherry", BiomeKeys.CHERRY_GROVE.getValue()),
			Map.entry("sakura", BiomeKeys.CHERRY_GROVE.getValue()),
			Map.entry("blossom", BiomeKeys.CHERRY_GROVE.getValue()),
			Map.entry("taiga", BiomeKeys.TAIGA.getValue()),
			Map.entry("conifer", BiomeKeys.TAIGA.getValue()),
			Map.entry("boreal", BiomeKeys.TAIGA.getValue()),
			Map.entry("spruce", BiomeKeys.TAIGA.getValue()),
			Map.entry("pine", BiomeKeys.TAIGA.getValue()),
			Map.entry("jungle", BiomeKeys.JUNGLE.getValue()),
			Map.entry("rainforest", BiomeKeys.JUNGLE.getValue()),
			Map.entry("bamboo", BiomeKeys.BAMBOO_JUNGLE.getValue()),
			Map.entry("crimson", BiomeKeys.CRIMSON_FOREST.getValue()),
			Map.entry("warped", BiomeKeys.WARPED_FOREST.getValue()),
			Map.entry("mangrove", BiomeKeys.MANGROVE_SWAMP.getValue()),
			Map.entry("grove", BiomeKeys.GROVE.getValue()))),
		Map.entry(BiomeKeys.BIRCH_FOREST.getValue(), Map.of(
			"old", BiomeKeys.OLD_GROWTH_BIRCH_FOREST.getValue(),
			"ancient", BiomeKeys.OLD_GROWTH_BIRCH_FOREST.getValue(),
			"primeval", BiomeKeys.OLD_GROWTH_BIRCH_FOREST.getValue())),
		Map.entry(BiomeKeys.TAIGA.getValue(), Map.of(
			"old", BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA.getValue(),
			"ancient", BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA.getValue(),
			"primeval", BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA.getValue())),
		Map.entry(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA.getValue(), Map.of(
			"pine", BiomeKeys.OLD_GROWTH_PINE_TAIGA.getValue())),
		Map.entry(BiomeKeys.JUNGLE.getValue(), Map.of(
			"sparse", BiomeKeys.SPARSE_JUNGLE.getValue(),
			"thin", BiomeKeys.SPARSE_JUNGLE.getValue(),
			"scattered", BiomeKeys.SPARSE_JUNGLE.getValue(),
			"bamboo", BiomeKeys.BAMBOO_JUNGLE.getValue())),
		Map.entry(BiomeKeys.BADLANDS.getValue(), Map.of(
			"eroded", BiomeKeys.ERODED_BADLANDS.getValue(),
			"weathered", BiomeKeys.ERODED_BADLANDS.getValue(),
			"cracked", BiomeKeys.ERODED_BADLANDS.getValue(),
			"wooded", BiomeKeys.WOODED_BADLANDS.getValue(),
			"forested", BiomeKeys.WOODED_BADLANDS.getValue(),
			"treed", BiomeKeys.WOODED_BADLANDS.getValue())),
		Map.entry(BiomeKeys.SAVANNA.getValue(), Map.of(
			"plateau", BiomeKeys.SAVANNA_PLATEAU.getValue(),
			"windswept", BiomeKeys.WINDSWEPT_SAVANNA.getValue())),
		Map.entry(BiomeKeys.OCEAN.getValue(), Map.of(
			"warm", BiomeKeys.WARM_OCEAN.getValue(),
			"tepid", BiomeKeys.LUKEWARM_OCEAN.getValue(),
			"lukewarm", BiomeKeys.LUKEWARM_OCEAN.getValue(),
			"mild", BiomeKeys.LUKEWARM_OCEAN.getValue(),
			"cold", BiomeKeys.COLD_OCEAN.getValue(),
			"chill", BiomeKeys.COLD_OCEAN.getValue(),
			"deep", BiomeKeys.DEEP_OCEAN.getValue(),
			"abyss", BiomeKeys.DEEP_OCEAN.getValue())),
		Map.entry(BiomeKeys.DEEP_OCEAN.getValue(), Map.of(
			"cold", BiomeKeys.DEEP_COLD_OCEAN.getValue(),
			"chill", BiomeKeys.DEEP_COLD_OCEAN.getValue(),
			"lukewarm", BiomeKeys.DEEP_LUKEWARM_OCEAN.getValue(),
			"tepid", BiomeKeys.DEEP_LUKEWARM_OCEAN.getValue(),
			"mild", BiomeKeys.DEEP_LUKEWARM_OCEAN.getValue())),
		// An island is coastline unless the name says what grows on it – the
		// shore is the last thing you would draw a tropical island as.
		Map.entry(BiomeKeys.BEACH.getValue(), Map.ofEntries(
			Map.entry("stony", BiomeKeys.STONY_SHORE.getValue()),
			Map.entry("stone", BiomeKeys.STONY_SHORE.getValue()),
			Map.entry("rocky", BiomeKeys.STONY_SHORE.getValue()),
			Map.entry("pebble", BiomeKeys.STONY_SHORE.getValue()),
			Map.entry("shingle", BiomeKeys.STONY_SHORE.getValue()),
			Map.entry("tropic", BiomeKeys.JUNGLE.getValue()),
			Map.entry("jungle", BiomeKeys.JUNGLE.getValue()),
			Map.entry("rainforest", BiomeKeys.JUNGLE.getValue()),
			Map.entry("bamboo", BiomeKeys.BAMBOO_JUNGLE.getValue()),
			Map.entry("forest", BiomeKeys.FOREST.getValue()),
			Map.entry("wood", BiomeKeys.FOREST.getValue()),
			Map.entry("mushroom", BiomeKeys.MUSHROOM_FIELDS.getValue()),
			Map.entry("volcan", BiomeKeys.BASALT_DELTAS.getValue()))),
		Map.entry(BiomeKeys.WINDSWEPT_HILLS.getValue(), Map.of(
			"gravel", BiomeKeys.WINDSWEPT_GRAVELLY_HILLS.getValue(),
			"gravelly", BiomeKeys.WINDSWEPT_GRAVELLY_HILLS.getValue(),
			"scree", BiomeKeys.WINDSWEPT_GRAVELLY_HILLS.getValue())),
		Map.entry(BiomeKeys.DARK_FOREST.getValue(), Map.of(
			"deep", BiomeKeys.DEEP_DARK.getValue(),
			"sculk", BiomeKeys.DEEP_DARK.getValue())),
		Map.entry(BiomeKeys.MEADOW.getValue(), Map.of(
			"savanna", BiomeKeys.SAVANNA_PLATEAU.getValue(),
			"steppe", BiomeKeys.SAVANNA_PLATEAU.getValue())),
		Map.entry(BiomeKeys.GROVE.getValue(), Map.of(
			"cherry", BiomeKeys.CHERRY_GROVE.getValue(),
			"sakura", BiomeKeys.CHERRY_GROVE.getValue(),
			"blossom", BiomeKeys.CHERRY_GROVE.getValue())),
		Map.entry(BiomeKeys.SWAMP.getValue(), Map.of(
			"mangrove", BiomeKeys.MANGROVE_SWAMP.getValue())),
		Map.entry(BiomeKeys.DRIPSTONE_CAVES.getValue(), Map.of(
			"lush", BiomeKeys.LUSH_CAVES.getValue(),
			"verdant", BiomeKeys.LUSH_CAVES.getValue(),
			"overgrown", BiomeKeys.LUSH_CAVES.getValue(),
			"mossy", BiomeKeys.LUSH_CAVES.getValue())),
		Map.entry(BiomeKeys.JAGGED_PEAKS.getValue(), Map.of(
			"stony", BiomeKeys.STONY_PEAKS.getValue(),
			"stone", BiomeKeys.STONY_PEAKS.getValue(),
			"rocky", BiomeKeys.STONY_PEAKS.getValue())),
		Map.entry(BiomeKeys.PLAINS.getValue(), Map.ofEntries(
			Map.entry("sunflower", BiomeKeys.SUNFLOWER_PLAINS.getValue()),
			Map.entry("meadow", BiomeKeys.MEADOW.getValue()),
			Map.entry("pasture", BiomeKeys.MEADOW.getValue()),
			Map.entry("heath", BiomeKeys.MEADOW.getValue()),
			Map.entry("flower", BiomeKeys.FLOWER_FOREST.getValue()),
			Map.entry("floral", BiomeKeys.FLOWER_FOREST.getValue()),
			Map.entry("cherry", BiomeKeys.CHERRY_GROVE.getValue()),
			Map.entry("sakura", BiomeKeys.CHERRY_GROVE.getValue()),
			Map.entry("blossom", BiomeKeys.CHERRY_GROVE.getValue()),
			Map.entry("savanna", BiomeKeys.SAVANNA.getValue()),
			Map.entry("steppe", BiomeKeys.SAVANNA.getValue()),
			Map.entry("mushroom", BiomeKeys.MUSHROOM_FIELDS.getValue()),
			Map.entry("shroom", BiomeKeys.MUSHROOM_FIELDS.getValue()),
			Map.entry("soul", BiomeKeys.SOUL_SAND_VALLEY.getValue()),
			// "windswept" is the one word that does mean bare stone.
			Map.entry("windswept", BiomeKeys.WINDSWEPT_HILLS.getValue()),
			// Hills and fields are shapes, not cover – whatever grows on them is
			// said by the word in front, and that is what should be drawn.
			Map.entry("forest", BiomeKeys.FOREST.getValue()),
			Map.entry("wood", BiomeKeys.FOREST.getValue()),
			Map.entry("thicket", BiomeKeys.FOREST.getValue()),
			Map.entry("birch", BiomeKeys.BIRCH_FOREST.getValue()),
			Map.entry("taiga", BiomeKeys.TAIGA.getValue()),
			Map.entry("conifer", BiomeKeys.TAIGA.getValue()),
			Map.entry("boreal", BiomeKeys.TAIGA.getValue()),
			Map.entry("spruce", BiomeKeys.TAIGA.getValue()),
			Map.entry("pine", BiomeKeys.TAIGA.getValue()),
			Map.entry("jungle", BiomeKeys.JUNGLE.getValue()),
			Map.entry("rainforest", BiomeKeys.JUNGLE.getValue()),
			Map.entry("desert", BiomeKeys.DESERT.getValue()),
			Map.entry("dune", BiomeKeys.DESERT.getValue()),
			Map.entry("swamp", BiomeKeys.SWAMP.getValue()),
			Map.entry("marsh", BiomeKeys.SWAMP.getValue()),
			Map.entry("bog", BiomeKeys.SWAMP.getValue()),
			Map.entry("mire", BiomeKeys.SWAMP.getValue()),
			Map.entry("badland", BiomeKeys.BADLANDS.getValue()),
			Map.entry("mesa", BiomeKeys.BADLANDS.getValue())))
	);

	/**
	 * The other two dimensions read nothing like the overworld, so a name that
	 * says which one it belongs to overrules the shape it settled on: an "end
	 * highland" is not a mountain, it is the pale stone of the End.
	 */
	private static final Set<String> END_WORDS = Set.of("end", "ender", "outer");
	private static final Set<String> NETHER_WORDS = Set.of("nether", "hell", "hellish", "inferno", "infernal");

	private static final Map<Identifier, Identifier> END_FORMS = Map.ofEntries(
		Map.entry(BiomeKeys.STONY_PEAKS.getValue(), BiomeKeys.END_HIGHLANDS.getValue()),
		Map.entry(BiomeKeys.JAGGED_PEAKS.getValue(), BiomeKeys.END_HIGHLANDS.getValue()),
		Map.entry(BiomeKeys.WINDSWEPT_HILLS.getValue(), BiomeKeys.END_HIGHLANDS.getValue()),
		Map.entry(BiomeKeys.PLAINS.getValue(), BiomeKeys.END_MIDLANDS.getValue()),
		Map.entry(BiomeKeys.MEADOW.getValue(), BiomeKeys.END_MIDLANDS.getValue()),
		Map.entry(BiomeKeys.FOREST.getValue(), BiomeKeys.END_MIDLANDS.getValue()),
		Map.entry(BiomeKeys.BADLANDS.getValue(), BiomeKeys.END_BARRENS.getValue()),
		Map.entry(BiomeKeys.DESERT.getValue(), BiomeKeys.END_BARRENS.getValue()),
		Map.entry(BiomeKeys.BEACH.getValue(), BiomeKeys.SMALL_END_ISLANDS.getValue()),
		Map.entry(BiomeKeys.OCEAN.getValue(), BiomeKeys.SMALL_END_ISLANDS.getValue()),
		Map.entry(BiomeKeys.THE_VOID.getValue(), BiomeKeys.THE_VOID.getValue())
	);

	private static final Map<Identifier, Identifier> NETHER_FORMS = Map.of(
		BiomeKeys.FOREST.getValue(), BiomeKeys.WARPED_FOREST.getValue(),
		BiomeKeys.TAIGA.getValue(), BiomeKeys.WARPED_FOREST.getValue(),
		BiomeKeys.JUNGLE.getValue(), BiomeKeys.CRIMSON_FOREST.getValue(),
		BiomeKeys.MUSHROOM_FIELDS.getValue(), BiomeKeys.CRIMSON_FOREST.getValue(),
		BiomeKeys.DESERT.getValue(), BiomeKeys.SOUL_SAND_VALLEY.getValue(),
		BiomeKeys.BEACH.getValue(), BiomeKeys.SOUL_SAND_VALLEY.getValue(),
		BiomeKeys.STONY_PEAKS.getValue(), BiomeKeys.BASALT_DELTAS.getValue(),
		BiomeKeys.WINDSWEPT_HILLS.getValue(), BiomeKeys.BASALT_DELTAS.getValue(),
		BiomeKeys.OCEAN.getValue(), BiomeKeys.BASALT_DELTAS.getValue(),
		BiomeKeys.RIVER.getValue(), BiomeKeys.BASALT_DELTAS.getValue()
	);

	/** Snowbound counterparts, for when a name says both what a place is and that it is frozen. */
	private static final Map<Identifier, Identifier> SNOWY_FORMS = Map.of(
		BiomeKeys.PLAINS.getValue(), BiomeKeys.SNOWY_PLAINS.getValue(),
		BiomeKeys.TAIGA.getValue(), BiomeKeys.SNOWY_TAIGA.getValue(),
		BiomeKeys.BEACH.getValue(), BiomeKeys.SNOWY_BEACH.getValue(),
		BiomeKeys.OCEAN.getValue(), BiomeKeys.FROZEN_OCEAN.getValue(),
		BiomeKeys.DEEP_OCEAN.getValue(), BiomeKeys.DEEP_FROZEN_OCEAN.getValue(),
		BiomeKeys.RIVER.getValue(), BiomeKeys.FROZEN_RIVER.getValue(),
		BiomeKeys.JAGGED_PEAKS.getValue(), BiomeKeys.FROZEN_PEAKS.getValue(),
		BiomeKeys.STONY_PEAKS.getValue(), BiomeKeys.SNOWY_SLOPES.getValue(),
		BiomeKeys.MEADOW.getValue(), BiomeKeys.SNOWY_SLOPES.getValue(),
		BiomeKeys.FOREST.getValue(), BiomeKeys.SNOWY_TAIGA.getValue()
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
		if (found == null) return end ? BiomeKeys.THE_END.getValue() : nether ? BiomeKeys.NETHER_WASTES.getValue() : null;

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

		if (end) return END_FORMS.getOrDefault(found, BiomeKeys.THE_END.getValue());
		if (nether) return NETHER_FORMS.getOrDefault(found, BiomeKeys.NETHER_WASTES.getValue());

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
