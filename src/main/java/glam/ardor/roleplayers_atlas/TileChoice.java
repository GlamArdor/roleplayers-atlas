package glam.ardor.roleplayers_atlas;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * What a chunk came out as: the picture to draw, how high it reads, and the
 * biome that won the vote.
 * <p>
 * The biome is kept so the player can point at a piece of map and be told what
 * it is – null where the tile came from water, ice or another feature rather
 * than from the land itself, which is nothing to correct.
 */
public record TileChoice(TerrainTileProvider provider, @Nullable TileElevation elevation, @Nullable Identifier biomeId) {
}
