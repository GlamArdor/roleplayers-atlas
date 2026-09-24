package glam.ardor.roleplayers_atlas;

import glam.ardor.roleplayers_atlas.reloader.BiomeTileProviders;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.terrain.ChunkSummary;
import folk.sisby.surveyor.terrain.LayerSummary;
import folk.sisby.surveyor.terrain.WorldTerrain;
import folk.sisby.surveyor.util.RegistryPalette;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Reference2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBiomeTags;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Hottest class in the mod. Might get ugly.
 */
public class TerrainTiling {
	public static final int EMPTY_PRIORITY = 16;
	public static final int RAVINE_PRIORITY = 12;
	public static final int LAVA_PRIORITY = 6;
	public static final int WATER_PRIORITY = 4;
	public static final int ICE_PRIORITY = 3;
	public static final int BEACH_PRIORITY = 3;

	public static final List<Identifier> CUSTOM_TILES = List.of(
		FeatureTiles.BEDROCK_ROOF,
		FeatureTiles.EMPTY,
		FeatureTiles.END_VOID,
		FeatureTiles.WATER,
		FeatureTiles.ICE,
		FeatureTiles.TILE_RAVINE,
		FeatureTiles.SWAMP_WATER,
		FeatureTiles.TILE_LAVA,
		FeatureTiles.TILE_LAVA_SHORE
	);

	public static final int NETHER_SCAN_HEIGHT = 50;
	public static final Map<Biome, Integer> priorityCache = new Reference2IntArrayMap<>();
	public static final Map<Biome, Boolean> swampCache = new Reference2BooleanArrayMap<>();
	private static final int DEFAULT_SEA_LEVEL = 63;

	/**
	 * The height everything else is measured against. The server sends its own
	 * with the join packet, so a world whose water sits somewhere other than 63
	 * no longer reads as one endless plateau.
	 */
	private static int seaLevel() {
		net.minecraft.client.world.ClientWorld world = net.minecraft.client.MinecraftClient.getInstance().world;
		return world == null ? DEFAULT_SEA_LEVEL : world.getSeaLevel();
	}

	public static int priorityForBiome(Registry<Biome> biomeRegistry, Biome biome) {
		return priorityCache.computeIfAbsent(biome, b -> {
			RegistryEntry<Biome> biomeEntry = biomeRegistry.getEntry(biome);
			if (biomeEntry.isIn(BiomeTags.IS_BEACH)) {
				return BEACH_PRIORITY;
			} else if (biomeEntry.isIn(BiomeTags.IS_NETHER)) {
				return 2;
			} else {
				return 1;
			}
		});
	}

	public static boolean isSwamp(Registry<Biome> biomeRegistry, Biome biome) {
		return swampCache.computeIfAbsent(biome, b -> biomeRegistry.getEntry(b).isIn(ConventionalBiomeTags.SWAMP));
	}

	/**
	 * Settles which of a chunk's heights it is drawn at, given how many of its
	 * columns fell into each tier.
	 * <p>
	 * Taking the commonest outright drew every tier boundary as a clean
	 * staircase, because a chunk one column past the line flipped whole. Drawing
	 * lots instead, weighted by the columns themselves, lets a chunk that is
	 * mostly valley still come out as valley while its neighbours along the edge
	 * scatter – so the change reads as ground rising rather than as a step.
	 * <p>
	 * The draw is fixed by the chunk's position, so a tile keeps the tier it was
	 * given: redrawn, reloaded, or seen by another player of the same map.
	 */
	private static int settleElevation(int[][] possibleTiles, int biomeIndex, ChunkPos pos) {
		int tiers = TileElevation.values().length;
		int total = 0;
		for (int i = 0; i < tiers; i++) total += possibleTiles[i][biomeIndex];
		if (total <= 0) return -1;
		int roll = Math.floorMod(scatter(pos), total);
		for (int i = 0; i < tiers; i++) {
			roll -= possibleTiles[i][biomeIndex];
			if (roll < 0) return i;
		}
		return tiers - 1;
	}

	/** A number that is the chunk's own and looks like nothing in particular. */
	private static int scatter(ChunkPos pos) {
		long h = pos.x * 0x9E3779B97F4A7C15L ^ pos.z * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (int) h;
	}

	public static TileChoice frequencyToTexture(int[][] possibleTiles, Registry<Biome> biomeRegistry, IndexedIterable<Biome> biomePalette, ChunkPos pos) {
		int elevationOrdinal = -1;
		int biomeIndex = -1;
		int bestFrequency = 0;
		for (int i = 0; i < possibleTiles.length; i++) {
			for (int j = 0; j < possibleTiles[i].length; j++) {
				if (possibleTiles[i][j] > bestFrequency) {
					elevationOrdinal = i;
					biomeIndex = j;
					bestFrequency = possibleTiles[i][j];
				}
			}
		}
		if (bestFrequency == 0) return null;
		// Which land it is stays a matter of majority – only how high it reads is
		// softened. The last row holds water, ice and the like, which have no
		// height to speak of and are left alone.
		if (elevationOrdinal < TileElevation.values().length) {
			int settled = settleElevation(possibleTiles, biomeIndex, pos);
			if (settled >= 0) elevationOrdinal = settled;
		}
		int customTileIndex = biomeIndex - possibleTiles[0].length + CUSTOM_TILES.size();
		Identifier providerId = customTileIndex >= 0 ? CUSTOM_TILES.get(customTileIndex) : biomeRegistry.getId(biomePalette.get(biomeIndex));
		if (providerId == null) {
			throw new RuntimeException(customTileIndex >= 0 ? "Custom tile index %s was out of bounds for size %s!".formatted(customTileIndex, CUSTOM_TILES.size()) : "Biome ID was null at index %s and instance %S!".formatted(biomeIndex, biomePalette.get(biomeIndex)));
		}
		return new TileChoice(BiomeTileProviders.getInstance().getTileProvider(providerId), elevationOrdinal == TileElevation.values().length ? null : TileElevation.values()[elevationOrdinal], customTileIndex >= 0 ? null : providerId);
	}

	public static TileChoice terrainToTile(WorldSummary summary, ChunkPos pos) {
		int defaultTile = CUSTOM_TILES.indexOf(summary.dimension() == World.END ? FeatureTiles.END_VOID : FeatureTiles.EMPTY);
		boolean checkRavines = summary.dimension() == World.OVERWORLD;

		int topY = 999;

		WorldTerrain terrain = summary.terrain();
		if (terrain == null) return null;
		ChunkSummary chunk = terrain.get(pos);
		if (chunk == null) return null; // Skip events fired for chunks we don't have yet (e.g. new shares)
		@Nullable LayerSummary.Raw lithograph = chunk.toSingleLayer(null, null, topY);
		RegistryPalette<Biome>.ValueView biomePalette = terrain.getBiomePalette(pos);
		RegistryPalette<Block>.ValueView blockPalette = terrain.getBlockPalette(pos);
		Registry<Biome> biomeRegistry = biomePalette.registry(); // 1.21: ensures server registry is used in singleplayer
		if (lithograph == null) return new TileChoice(BiomeTileProviders.getInstance().getTileProvider(CUSTOM_TILES.get(defaultTile)), null, null);

		int elevationSize = TileElevation.values().length;
		int elevationCount = elevationSize + 1;
		int biomeCount = biomePalette.size();
		int baseTileCount = biomeCount + CUSTOM_TILES.size();
		int[][] possibleTiles = new int[elevationCount][baseTileCount];
		int seaLevel = seaLevel();

		for (int i = 0; i < lithograph.depths().length; i++) {
			if (!lithograph.exists().get(i)) {
				possibleTiles[elevationSize][defaultTile] += EMPTY_PRIORITY;
				continue;
			}
			int height = topY - lithograph.depths()[i] + lithograph.waterDepths()[i];
			Block block = blockPalette.get(lithograph.blocks()[i]);
			Biome biome = biomePalette.get(lithograph.biomes()[i]);

			if (checkRavines && height - seaLevel < -7) {
				possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(FeatureTiles.TILE_RAVINE)] += RAVINE_PRIORITY;
			} else if (lithograph.waterDepths()[i] > 0) {
				possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(isSwamp(biomeRegistry, biome) ? FeatureTiles.SWAMP_WATER : FeatureTiles.WATER)] += WATER_PRIORITY;
			} else if (block == Blocks.ICE) {
				possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(FeatureTiles.ICE)] += ICE_PRIORITY;
			} else if (block == Blocks.LAVA) {
				possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(FeatureTiles.TILE_LAVA)] += LAVA_PRIORITY;
			}
			possibleTiles[TileElevation.fromBlocksAboveSea(height - seaLevel).ordinal()][lithograph.biomes()[i]] += priorityForBiome(biomeRegistry, biome);
		}

		return frequencyToTexture(possibleTiles, biomeRegistry, biomePalette, pos);
	}

	public static TileChoice terrainToTileNether(WorldSummary summary, ChunkPos pos) {
		int defaultTile = CUSTOM_TILES.indexOf(FeatureTiles.BEDROCK_ROOF);

		int topY = 999;
		int logicalTopY = 126;

		WorldTerrain terrain = summary.terrain();
		if (terrain == null) return null;
		ChunkSummary chunk = terrain.get(pos);
		if (chunk == null) return null; // Skip events fired for chunks we don't have yet (e.g. new shares)
		@Nullable LayerSummary.Raw lowLithograph = chunk.toSingleLayer(null, NETHER_SCAN_HEIGHT, topY);
		@Nullable LayerSummary.Raw fullLithograph = chunk.toSingleLayer(null, logicalTopY, topY);
		RegistryPalette<Biome>.ValueView biomePalette = terrain.getBiomePalette(pos);
		RegistryPalette<Block>.ValueView blockPalette = terrain.getBlockPalette(pos);
		Registry<Biome> biomeRegistry = biomePalette.registry(); // 1.21: ensures server registry is used in singleplayer

		int elevationSize = TileElevation.values().length;
		int elevationCount = elevationSize + 1;
		int biomeCount = biomePalette.size();
		int baseTileCount = biomeCount + CUSTOM_TILES.size();
		int[][] possibleTiles = new int[elevationCount][baseTileCount];

		if (fullLithograph == null) {
			return new TileChoice(BiomeTileProviders.getInstance().getTileProvider(CUSTOM_TILES.get(defaultTile)), null, null);
		}

		int SEA_DEPTH = topY - 31;

		if (lowLithograph == null) {
			for (int i = 0; i < fullLithograph.depths().length; i++) {
				if (!fullLithograph.exists().get(i)) {
					possibleTiles[elevationSize][defaultTile] += EMPTY_PRIORITY;
				} else {
					Biome biome = biomePalette.get(fullLithograph.biomes()[i]);
					possibleTiles[elevationSize][fullLithograph.biomes()[i]] += priorityForBiome(biomeRegistry, biome);
				}
			}
		} else {
			for (int i = 0; i < lowLithograph.depths().length; i++) {
				if (!lowLithograph.exists().get(i) || lowLithograph.depths()[i] > SEA_DEPTH) {
					Biome biome = biomePalette.get(fullLithograph.biomes()[i]);
					possibleTiles[elevationSize][fullLithograph.biomes()[i]] += priorityForBiome(biomeRegistry, biome);
				} else {
					Block block = blockPalette.get(lowLithograph.blocks()[i]);
					if (block == Blocks.LAVA) { // Lava Sea
						possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(FeatureTiles.TILE_LAVA)] += LAVA_PRIORITY;
					} else { // Low Floor
						possibleTiles[elevationSize][biomeCount + CUSTOM_TILES.indexOf(FeatureTiles.TILE_LAVA_SHORE)] += BEACH_PRIORITY;
					}
				}
			}
		}

		return frequencyToTexture(possibleTiles, biomeRegistry, biomePalette, pos);
	}
}
