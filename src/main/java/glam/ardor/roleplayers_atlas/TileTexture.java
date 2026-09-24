package glam.ardor.roleplayers_atlas;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record TileTexture(Identifier id, boolean innerBorder, Set<TileTexture> tilesTo, Set<TileTexture> tilesToHorizontal, Set<TileTexture> tilesToVertical) {
	public static TileTexture empty(Identifier id, boolean innerBorder) {
		return new TileTexture(Identifier.of(id.getNamespace(), "textures/atlas/tile/%s.png".formatted(id.getPath())), innerBorder, new ReferenceOpenHashSet<>(), new ReferenceOpenHashSet<>(), new ReferenceOpenHashSet<>());
	}

	private static final TileTexture MISSING = empty(RoleplayersAtlas.id("missing"), false);
	private static final TileTexture TEST = empty(RoleplayersAtlas.id("test"), false);

	/**
	 * What a chunk is drawn with when nothing at all is known about its biome.
	 * <p>
	 * Read fresh every time rather than settled once at class load – otherwise
	 * changing the setting did nothing until the game was restarted, which is
	 * not what "unrecognised biomes" looks like it promises.
	 */
	public static TileTexture fallback() {
		return RoleplayersAtlas.CONFIG.fallbackFailHandling == AtlasConfig.FallbackHandling.TEST ? TEST : MISSING;
	}

	public String displayId() {
		Identifier trimmed = id.withPath(p -> p.substring("textures/atlas/tile/".length(), id.getPath().length() - 4));
		return id.getNamespace().equals(RoleplayersAtlas.ID) ? trimmed.getPath() : trimmed.toString();
	}

	public boolean tiles(TileTexture other) {
		return this == other || (innerBorder ^ (tilesTo.contains(other) || tilesToHorizontal.contains(other) || tilesToVertical.contains(other)));
	}

	public boolean tilesHorizontally(TileTexture other) {
		return this == other || (innerBorder ^ (tilesTo.contains(other) || tilesToHorizontal.contains(other)));
	}

	public boolean tilesVertically(TileTexture other) {
		return this == other || (innerBorder ^ (tilesTo.contains(other) || tilesToVertical.contains(other)));
	}

	public record Builder(Identifier id, boolean innerBorder, Set<Identifier> tilesTo, Set<Identifier> tilesToHorizontal, Set<Identifier> tilesToVertical) {
		public void build(Map<Identifier, TileTexture> emptyTextures) {
			if (!tilesTo.isEmpty()) emptyTextures.get(id).tilesTo.addAll(tilesTo.stream().map(emptyTextures::get).collect(Collectors.toSet()));
			if (!tilesToHorizontal.isEmpty()) emptyTextures.get(id).tilesToHorizontal.addAll(tilesToHorizontal.stream().map(emptyTextures::get).collect(Collectors.toSet()));
			if (!tilesToVertical.isEmpty()) emptyTextures.get(id).tilesToVertical.addAll(tilesToVertical.stream().map(emptyTextures::get).collect(Collectors.toSet()));
		}
	}
}
