package glam.ardor.roleplayers_atlas.util;

import folk.sisby.surveyor.util.RegionPos;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.ChunkPos;

/** Helpers for chunk-area (territory) landmarks. */
public final class TerritoryUtil {
	private TerritoryUtil() {
	}

	public static Map<RegionPos, BitSet> chunksToRegions(Collection<ChunkPos> chunks) {
		Map<RegionPos, BitSet> regions = new HashMap<>();
		for (ChunkPos chunk : chunks) {
			RegionPos region = new RegionPos(RegionPos.chunkToRegion(chunk.x), RegionPos.chunkToRegion(chunk.z));
			regions.computeIfAbsent(region, r -> new BitSet(RegionPos.CHUNK_AREA)).set(RegionPos.chunkToBit(chunk));
		}
		return regions;
	}

	/** O(1) membership test straight on the region bitsets. */
	public static boolean contains(Map<RegionPos, BitSet> regions, ChunkPos chunk) {
		if (regions == null) return false;
		BitSet bits = regions.get(new RegionPos(RegionPos.chunkToRegion(chunk.x), RegionPos.chunkToRegion(chunk.z)));
		return bits != null && bits.get(RegionPos.chunkToBit(chunk));
	}

	public static ColumnPos centroid(Map<RegionPos, BitSet> regions) {
		long sumX = 0, sumZ = 0;
		int count = 0;
		for (ChunkPos chunk : RegionPos.regionsToChunks(regions)) {
			sumX += chunk.getMinBlockX() + 8;
			sumZ += chunk.getMinBlockZ() + 8;
			count++;
		}
		if (count == 0) return new ColumnPos(0, 0);
		return new ColumnPos((int) (sumX / count), (int) (sumZ / count));
	}
}
