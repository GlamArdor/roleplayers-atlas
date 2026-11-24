package glam.ardor.roleplayers_atlas;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

public record TerrainTileProvider(Identifier id, Map<TileElevation, List<TileTexture>> textures) {
	private static final Map<TileTexture, TerrainTileProvider> FALLBACKS = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Drawn where the biome couldn't be recognised at all. Built per texture and
	 * kept, so switching the setting swaps the tile without allocating one for
	 * every chunk that lands on it.
	 */
	public static TerrainTileProvider fallback() {
		return FALLBACKS.computeIfAbsent(TileTexture.fallback(), t -> new TerrainTileProvider(RoleplayersAtlas.id("default"), List.of(t)));
	}

	public TerrainTileProvider(Identifier id, List<TileTexture> textures) {
		this(id, Arrays.stream(TileElevation.values()).collect(Collectors.toMap(e -> e, e -> textures)));
	}

	public TileTexture getTexture(ChunkPos pos, @Nullable TileElevation elevation) {
		int variation = (int) (Mth.getSeed(pos.x(), pos.z(), pos.x() * pos.z()) & 0x7FFFFFFF);
		TileElevation usedElevation = elevation == null ? TileElevation.VALLEY : elevation;
		return textures.get(usedElevation).get(variation % textures.get(usedElevation).size());
	}
}
