package glam.ardor.roleplayers_atlas.reloader;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import glam.ardor.roleplayers_atlas.BiomeOverrides;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.AtlasConfig;
import glam.ardor.roleplayers_atlas.TerrainTileProvider;
import glam.ardor.roleplayers_atlas.TileElevation;
import glam.ardor.roleplayers_atlas.TileTexture;
import glam.ardor.roleplayers_atlas.util.ForgeTags;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBiomeTags;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import glam.ardor.roleplayers_atlas.util.LegacyJsonDataLoader;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BiomeTileProviders extends LegacyJsonDataLoader implements IdentifiableResourceReloadListener {
	public static final BiomeTileProviders INSTANCE = new BiomeTileProviders();
	public static final Identifier ID = RoleplayersAtlas.id("tile_provider/biome");

	public static BiomeTileProviders getInstance() {
		return INSTANCE;
	}

	protected final Map<Identifier, TerrainTileProvider> tileProviders = new HashMap<>();
	protected final Map<Identifier, Identifier> biomeFallbacks = new HashMap<>();
	protected boolean hasFallbacks = false;

	public BiomeTileProviders() {
		super("atlas/biome");
	}

	/** Where a biome's look came from, so the player can see what is guesswork. */
	public enum Source {
		EXACT,
		TAGS,
		NAME,
		NONE,
		MANUAL,
		SHARED
	}

	protected final Map<Identifier, Source> sources = new HashMap<>();

	/** Every biome this world has, and how each one ended up looking as it does. */
	public Map<Identifier, Source> sources() {
		Map<Identifier, Source> out = new HashMap<>(sources);
		BiomeOverrides.importedBiomes().forEach(id -> out.put(id, Source.SHARED));
		BiomeOverrides.all().keySet().forEach(id -> out.put(id, Source.MANUAL));
		return out;
	}

	/** What a biome is drawn as right now, correction and all. */
	public Identifier drawnAs(Identifier biomeId) {
		Identifier manual = BiomeOverrides.get(biomeId);
		if (manual != null && tileProviders.containsKey(manual)) return manual;
		if (tileProviders.containsKey(biomeId)) return biomeId;
		Identifier guessed = biomeFallbacks.get(biomeId);
		return guessed != null ? guessed : null;
	}

	/** The looks a correction can choose from – every biome the atlas has a picture for. */
	public java.util.Set<Identifier> availableLooks() {
		return java.util.Collections.unmodifiableSet(tileProviders.keySet());
	}

	public TerrainTileProvider getTileProvider(Identifier providerId) {
		// The player's own word comes before anything the atlas worked out,
		// including an exact match – a server can put anything in any biome.
		Identifier manual = BiomeOverrides.get(providerId);
		if (manual != null) {
			TerrainTileProvider corrected = tileProviders.get(manual);
			if (corrected != null) return corrected;
		}
		TerrainTileProvider exact = tileProviders.get(providerId);
		if (exact != null) return exact;
		TerrainTileProvider guessed = tileProviders.get(biomeFallbacks.get(providerId));
		if (guessed != null) return guessed;
		if (RoleplayersAtlas.CONFIG.fallbackFailHandling == AtlasConfig.FallbackHandling.PLAINS && !providerId.equals(Biomes.PLAINS.identifier())) {
			return getTileProvider(Biomes.PLAINS.identifier());
		}
		return TerrainTileProvider.fallback();
	}

	/**
	 * Register fallbacks for any biomes present in the client world that don't have explicit sets.
	 * Doing this on world join catches data-biomes that might not be registered in other worlds.
	 */
	public void registerFallbacks(Registry<Biome> biomeRegistry) {
		for (Biome biome : biomeRegistry) {
			Identifier biomeId = biomeRegistry.getKey(biome);
			if (tileProviders.containsKey(biomeId)) {
				sources.put(biomeId, Source.EXACT);
				continue;
			}
			Identifier fallbackBiome = getFallbackBiome(biomeRegistry.wrapAsHolder(biome));
			if (fallbackBiome != null && tileProviders.containsKey(fallbackBiome)) {
				biomeFallbacks.put(biomeId, fallbackBiome);
				sources.put(biomeId, Source.TAGS);
				RoleplayersAtlas.LOGGER.info("[Roleplayer's Atlas] Set fallback biome for {} to {}. You can set a more fitting texture using a resource pack!", biomeId, fallbackBiome);
			} else if (fallbackBiome != null) {
				sources.put(biomeId, Source.NONE);
				RoleplayersAtlas.LOGGER.error("[Roleplayer's Atlas] Fallback biome for {} is {}, which has no defined tile provider.", biomeId, fallbackBiome);
			} else {
				// Tags said nothing. Datapack biomes on a plugin server routinely
				// carry none at all, so read the name before giving up.
				Identifier byName = BiomeNames.guess(biomeId);
				if (byName != null && tileProviders.containsKey(byName)) {
					biomeFallbacks.put(biomeId, byName);
					sources.put(biomeId, Source.NAME);
					RoleplayersAtlas.LOGGER.info("[Roleplayer's Atlas] {} has no biome tags; drawing it as {} going by its name.", biomeId, byName);
					continue;
				}
				sources.put(biomeId, Source.NONE);
				RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] No fallback could be found for {} – no tags, and nothing recognisable in the name.", biomeId);
				if (RoleplayersAtlas.CONFIG.fallbackFailHandling == AtlasConfig.FallbackHandling.CRASH) throw new IllegalStateException("Roleplayer's Atlas fallback biome registration failed! Fix the missing biome or change fallbackFailHandling in roleplayers-atlas.toml");
			}
		}
		hasFallbacks = true;
	}

	public void clearFallbacks() {
		hasFallbacks = false;
		biomeFallbacks.clear();
		sources.clear();
	}

	public boolean hasFallbacks() {
		return hasFallbacks;
	}

	public static Identifier getFallbackBiome(Holder<Biome> biome) {
		if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_VOID) || biome.is(ConventionalBiomeTags.VOID) || biome.is(ForgeTags.Biomes.IS_VOID)) {
			return Biomes.THE_VOID.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_END) || biome.is(BiomeTags.IS_END) || biome.is(ConventionalBiomeTags.IN_THE_END) || biome.is(ConventionalBiomeTags.END_ISLANDS)) {
			if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_VEGETATION_SPARSE) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_VEGETATION_DENSE) || biome.is(ConventionalBiomeTags.VEGETATION_DENSE) || biome.is(ConventionalBiomeTags.VEGETATION_SPARSE) || biome.is(ForgeTags.Biomes.IS_LUSH)) return Biomes.END_HIGHLANDS.identifier();
			return Biomes.END_BARRENS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_NETHER_FOREST) || biome.is(ConventionalBiomeTags.NETHER_FORESTS)) {
			return Biomes.WARPED_FOREST.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_NETHER) || biome.is(BiomeTags.IS_NETHER) || biome.is(ConventionalBiomeTags.IN_NETHER)) {
			return Biomes.SOUL_SAND_VALLEY.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_SWAMP) || biome.is(ConventionalBiomeTags.SWAMP) || biome.is(ForgeTags.Biomes.IS_SWAMP)) {
			return Biomes.SWAMP.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_OCEAN) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(ConventionalBiomeTags.DEEP_OCEAN) || biome.is(ConventionalBiomeTags.OCEAN) || biome.is(ConventionalBiomeTags.SHALLOW_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(ConventionalBiomeTags.RIVER) || biome.is(ConventionalBiomeTags.AQUATIC) || biome.is(ConventionalBiomeTags.AQUATIC_ICY) || biome.is(ForgeTags.Biomes.IS_WATER)) {
			if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_AQUATIC_ICY) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_ICY) || biome.is(ConventionalBiomeTags.ICY) || biome.is(ConventionalBiomeTags.AQUATIC_ICY)) return Biomes.FROZEN_RIVER.identifier();
			return Biomes.RIVER.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_STONY_SHORES) || biome.is(ConventionalBiomeTags.STONY_SHORES)) {
			return Biomes.STONY_SHORE.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_BEACH) || biome.is(ConventionalBiomeTags.BEACH)) {
			return Biomes.BEACH.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_JUNGLE_TREE) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_JUNGLE) || biome.is(ConventionalBiomeTags.JUNGLE) || biome.is(ConventionalBiomeTags.TREE_JUNGLE)) {
			return Biomes.JUNGLE.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_FLOWER_FOREST) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_FLORAL) || biome.is(ConventionalBiomeTags.FLOWER_FORESTS) || biome.is(ConventionalBiomeTags.FLORAL)) {
			return Biomes.FLOWER_FOREST.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_SAVANNA_TREE) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_SAVANNA) || biome.is(BiomeTags.IS_SAVANNA) || biome.is(ConventionalBiomeTags.SAVANNA) || biome.is(ConventionalBiomeTags.TREE_SAVANNA)) {
			return Biomes.SAVANNA.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_BADLANDS) || biome.is((ConventionalBiomeTags.BADLANDS)) || biome.is((ConventionalBiomeTags.MESA))) {
			return Biomes.BADLANDS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_CONIFEROUS_TREE) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_TAIGA) || biome.is(ConventionalBiomeTags.TREE_CONIFEROUS) || biome.is(ForgeTags.Biomes.IS_CONIFEROUS) || biome.is(BiomeTags.IS_TAIGA) || biome.is(ConventionalBiomeTags.TAIGA)) {
			if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_SNOWY) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_ICY) || biome.is(ConventionalBiomeTags.ICY) || biome.is(ConventionalBiomeTags.SNOWY)) return Biomes.SNOWY_TAIGA.identifier();
			return Biomes.TAIGA.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_OLD_GROWTH)) {
			return Biomes.BIRCH_FOREST.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_BIRCH_FOREST) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_DECIDUOUS_TREE) || biome.is(ConventionalBiomeTags.BIRCH_FOREST) || biome.is(ConventionalBiomeTags.TREE_DECIDUOUS)) {
			return Biomes.BIRCH_FOREST.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_FOREST) || biome.is(ConventionalBiomeTags.FOREST)) {
			return Biomes.FOREST.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_SNOWY_PLAINS) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_PLAINS) || biome.is(ConventionalBiomeTags.PLAINS) || biome.is(ConventionalBiomeTags.SNOWY_PLAINS) || biome.is(ForgeTags.Biomes.IS_PLAINS) || biome.is(ConventionalBiomeTags.SNOWY) || biome.is(ForgeTags.Biomes.IS_SNOWY)) {
			if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_ICY) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_SNOWY_PLAINS) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_SNOWY) || biome.is(ConventionalBiomeTags.SNOWY_PLAINS) || biome.is(ConventionalBiomeTags.ICY) || biome.is(ConventionalBiomeTags.SNOWY)) return Biomes.SNOWY_PLAINS.identifier();
			return Biomes.PLAINS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_WASTELAND) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_DEAD) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_DESERT) || biome.is(ConventionalBiomeTags.DESERT) || biome.is(ConventionalBiomeTags.WASTELAND) || biome.is(ConventionalBiomeTags.DEAD) || biome.is(ForgeTags.Biomes.IS_SANDY) || biome.is(ForgeTags.Biomes.IS_DESERT) || biome.is(ForgeTags.Biomes.IS_DEAD) || biome.is(ForgeTags.Biomes.IS_WASTELAND)) {
			return Biomes.DESERT.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_ICY) || biome.is(ConventionalBiomeTags.ICY)) {
			return Biomes.FROZEN_OCEAN.identifier();
		} else if (biome.is(ForgeTags.Biomes.IS_PLATEAU)) {
			return Biomes.MEADOW.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_WINDSWEPT) || biome.is(ConventionalBiomeTags.EXTREME_HILLS) || biome.is(ConventionalBiomeTags.WINDSWEPT)) {
			return Biomes.WINDSWEPT_HILLS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_MOUNTAIN_PEAK) || biome.is(ConventionalBiomeTags.MOUNTAIN_PEAK) || biome.is(ForgeTags.Biomes.IS_PEAK)) {
			return Biomes.JAGGED_PEAKS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_MOUNTAIN_SLOPE) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(ConventionalBiomeTags.MOUNTAIN) || biome.is(ConventionalBiomeTags.MOUNTAIN_SLOPE) || biome.is(ForgeTags.Biomes.IS_SLOPE) || biome.is(ForgeTags.Biomes.IS_MOUNTAIN)) {
			return Biomes.STONY_PEAKS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_MUSHROOM) || biome.is(ConventionalBiomeTags.MUSHROOM) || biome.is(ForgeTags.Biomes.IS_MUSHROOM)) {
			return Biomes.MUSHROOM_FIELDS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_HILL) || biome.is(BiomeTags.IS_HILL)) {
			return Biomes.WINDSWEPT_GRAVELLY_HILLS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_UNDERGROUND) || biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_CAVE) || biome.is(ConventionalBiomeTags.CAVES) || biome.is(ConventionalBiomeTags.UNDERGROUND) || biome.is(ForgeTags.Biomes.IS_UNDERGROUND) || biome.is(ForgeTags.Biomes.IS_CAVE)) {
			return Biomes.DRIPSTONE_CAVES.identifier();
		} else if (biome.is(ForgeTags.Biomes.IS_SPOOKY)) {
			return Biomes.DARK_FOREST.identifier();
		} else if (biome.is(ForgeTags.Biomes.IS_MAGICAL)) {
			return Biomes.MUSHROOM_FIELDS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_VEGETATION_DENSE) || biome.is(ConventionalBiomeTags.VEGETATION_DENSE) || biome.is(ForgeTags.Biomes.IS_DENSE)) {
			return Biomes.FOREST.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_VEGETATION_SPARSE) || biome.is(ConventionalBiomeTags.VEGETATION_SPARSE) || biome.is(ForgeTags.Biomes.IS_SPARSE)) {
			return Biomes.PLAINS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_HOT) || biome.is(ConventionalBiomeTags.CLIMATE_HOT) || biome.is(ForgeTags.Biomes.IS_HOT)) {
			return Biomes.DESERT.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_COLD) || biome.is(ConventionalBiomeTags.CLIMATE_COLD) || biome.is(ForgeTags.Biomes.IS_COLD)) {
			return Biomes.SNOWY_PLAINS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_TEMPERATE) || biome.is(ConventionalBiomeTags.CLIMATE_TEMPERATE)) {
			return Biomes.PLAINS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_DRY) || biome.is(ConventionalBiomeTags.CLIMATE_DRY) || biome.is(ForgeTags.Biomes.IS_DRY)) {
			return Biomes.BADLANDS.identifier();
		} else if (biome.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags.IS_WET) || biome.is(ConventionalBiomeTags.CLIMATE_WET) || biome.is(ForgeTags.Biomes.IS_WET)) {
			return Biomes.SWAMP.identifier();
		}
		return null;
	}

	public static TileTexture getTexture(Map<Identifier, TileTexture> textures, Identifier id) {
		if (textures.containsKey(id)) {
			return textures.get(id);
		} else {
			throw new IllegalStateException("texture %s is not present!".formatted(id));
		}
	}

	public static @Nullable List<TileTexture> resolveTextureJson(Map<Identifier, TileTexture> textures, JsonElement textureJson) {
		if (textureJson instanceof JsonPrimitive texturePrimitive && texturePrimitive.isString()) {
			return List.of(getTexture(textures, Identifier.tryParse(texturePrimitive.getAsString())));
		} else if (textureJson instanceof JsonArray textureArray) {
			return textureArray.asList().stream().map(je -> getTexture(textures, Identifier.tryParse(je.getAsString()))).toList();
		} else if (textureJson instanceof JsonObject textureObject && textureObject.keySet().stream().allMatch(k -> textureObject.get(k) instanceof JsonPrimitive jp && jp.isNumber())) {
			Multiset<TileTexture> outList = HashMultiset.create();
			textureObject.entrySet().forEach(e -> outList.add(getTexture(textures, Identifier.tryParse(e.getKey())), e.getValue().getAsInt()));
			return outList.stream().toList();
		}
		return null;
	}

	@Override
	protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
		RoleplayersAtlas.LOGGER.info("[Roleplayer's Atlas] Reloading Biome Tile Providers...");
		Map<Identifier, TileTexture> textures = TileTextures.getInstance().getTextures();
		Set<TileTexture> unusedTextures = new HashSet<>(textures.values().stream().filter(t -> t.id().getPath().startsWith("biome")).toList());
		Map<Identifier, Identifier> providerParents = new HashMap<>();
		for (Map.Entry<Identifier, JsonElement> fileEntry : prepared.entrySet()) {
			Identifier fileId = fileEntry.getKey();
			try {
				JsonObject fileJson = fileEntry.getValue().getAsJsonObject();
				if (fileJson.has("parent")) {
					Identifier parentId = Identifier.tryParse(fileJson.getAsJsonPrimitive("parent").getAsString());
					providerParents.put(fileId, parentId);
					continue;
				}
				JsonElement textureJson = fileJson.get("textures");
				List<TileTexture> defaultTextures = resolveTextureJson(textures, textureJson);
				if (defaultTextures != null) {
					defaultTextures.forEach(unusedTextures::remove);
					tileProviders.put(fileId, new TerrainTileProvider(fileId, defaultTextures));
				} else {
					JsonObject textureObject = textureJson.getAsJsonObject();
					Map<TileElevation, List<TileTexture>> textureElevations = new HashMap<>();
					Set<TileElevation> skippedElevations = new HashSet<>();
					List<TileTexture> elevationTextures = null;
					for (TileElevation elevation : TileElevation.values()) {
						if (textureObject.has(elevation.getName())) {
							elevationTextures = resolveTextureJson(textures, textureObject.get(elevation.getName()));
							if (elevationTextures == null) throw new IllegalStateException("Malformed object %s in textures object!".formatted(elevation.getName()));
							elevationTextures.forEach(unusedTextures::remove);
							textureElevations.put(elevation, elevationTextures);
							for (TileElevation skipped : skippedElevations) {
								textureElevations.put(skipped, elevationTextures);
							}
							skippedElevations.clear();
						} else {
							skippedElevations.add(elevation);
						}
					}
					if (textureElevations.isEmpty()) {
						throw new IllegalStateException("No elevation keys were found in the textures object!");
					}
					for (TileElevation elevation : skippedElevations) {
						textureElevations.put(elevation, elevationTextures);
					}
					tileProviders.put(fileId, new TerrainTileProvider(fileId, textureElevations));
				}
			} catch (Exception e) {
				RoleplayersAtlas.LOGGER.error("[Roleplayer's Atlas] Error reading biome tile provider {}!", fileId, e);
			}
		}
		providerParents.forEach((id, parentId) -> {
			if (tileProviders.containsKey(parentId)) {
				tileProviders.put(id, tileProviders.get(parentId));
			} else {
				RoleplayersAtlas.LOGGER.error("[Roleplayer's Atlas] Error reading biome tile provider {}!", id, new IllegalStateException("Parent id %s doesn't exist".formatted(parentId)));
			}
		});

		for (TileTexture texture : unusedTextures) {
			if (texture.displayId().startsWith("test") || texture.displayId().startsWith("base")) continue;
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Tile texture {} isn't referenced by any biome tile provider!", texture.displayId());
		}
	}

	@Override
	public Identifier getFabricId() {
		return ID;
	}

	@Override
	public Collection<Identifier> getFabricDependencies() {
		return List.of(TileTextures.ID);
	}
}
