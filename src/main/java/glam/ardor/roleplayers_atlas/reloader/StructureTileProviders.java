package glam.ardor.roleplayers_atlas.reloader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.MarkerTexture;
import glam.ardor.roleplayers_atlas.StructureTileProvider;
import glam.ardor.roleplayers_atlas.TileTexture;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.structure.JigsawPieceSummary;
import folk.sisby.surveyor.structure.StructurePieceSummary;
import folk.sisby.surveyor.structure.StructureStartSummary;
import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import glam.ardor.roleplayers_atlas.util.LegacyJsonDataLoader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static glam.ardor.roleplayers_atlas.reloader.BiomeTileProviders.resolveTextureJson;

public class StructureTileProviders extends LegacyJsonDataLoader implements IdentifiableResourceReloadListener {
	public static final StructureTileProviders INSTANCE = new StructureTileProviders();

	public static final Identifier ID = RoleplayersAtlas.id("structures");

	public static StructureTileProviders getInstance() {
		return INSTANCE;
	}

	protected final Map<Identifier, StructureTileProvider> startTiles = new HashMap<>();
	protected final Map<Identifier, StructureTileProvider> typeTiles = new HashMap<>();
	protected final Map<Identifier, StructureTileProvider> tagTiles = new HashMap<>();
	protected final Map<Identifier, StructureTileProvider> pieceTypeTiles = new HashMap<>();
	protected final Map<Identifier, StructureTileProvider> pieceJigsawSingleTiles = new HashMap<>();
	protected final Map<Identifier, StructureTileProvider> pieceJigsawFeatureTiles = new HashMap<>();
	protected final Map<Identifier, MarkerTexture> startMarkers = new HashMap<>();
	protected final Map<Identifier, MarkerTexture> typeMarkers = new HashMap<>();
	protected final Map<Identifier, MarkerTexture> tagMarkers = new HashMap<>();
	protected final Map<Identifier, MarkerTexture> pieceTypeMarkers = new HashMap<>();
	protected final Map<Identifier, MarkerTexture> pieceJigsawSingleMarkers = new HashMap<>();
	protected final Map<Identifier, MarkerTexture> pieceJigsawFeatureMarkers = new HashMap<>();

	public enum ProviderType {
		START("start"),
		TAG("tag"),
		TYPE("type"),
		PIECE_TYPE("piece/type"),
		JIGSAW_SINGLE("piece/jigsaw/single"),
		JIGSAW_FEATURE("piece/jigsaw/feature");

		public final String key;

		ProviderType(String key) {
			this.key = key;
		}

		public String prefix() {
			return key + "/";
		}

		public String translation(Identifier id) {
			return "structure.%s.%s".formatted(key.replace('/', '.'), id.toString().replace(':', '.'));
		}
	}

	protected final Map<ProviderType, Pair<Map<Identifier, StructureTileProvider>, Map<Identifier, MarkerTexture>>> PROVIDER_MAPS = Map.of(
		ProviderType.START, Pair.of(startTiles, startMarkers),
		ProviderType.TYPE, Pair.of(typeTiles, typeMarkers),
		ProviderType.TAG, Pair.of(tagTiles, tagMarkers),
		ProviderType.PIECE_TYPE, Pair.of(pieceTypeTiles, pieceTypeMarkers),
		ProviderType.JIGSAW_SINGLE, Pair.of(pieceJigsawSingleTiles, pieceJigsawSingleMarkers),
		ProviderType.JIGSAW_FEATURE, Pair.of(pieceJigsawFeatureTiles, pieceJigsawFeatureMarkers)
	);

	public StructureTileProviders() {
		super("atlas/structure");
	}

	public void resolve(Map<ChunkPos, TileTexture> outTiles, Map<ChunkPos, StructureTileProvider> structureProviders, Map<ChunkPos, String> tilePredicates, StructurePieceSummary piece, WorldSummary summary) {
		if (piece instanceof JigsawPieceSummary jigsawPiece) {
			if (pieceJigsawSingleTiles.containsKey(jigsawPiece.getId())) {
				StructureTileProvider provider = (jigsawPiece.getElementType() == StructurePoolElementType.FEATURE ? pieceJigsawFeatureTiles : pieceJigsawSingleTiles).get(jigsawPiece.getId());
				provider.getTextures(summary, jigsawPiece.getBoundingBox(), jigsawPiece.getJunctions(), tilePredicates).forEach((pos, texture) -> {
					if (structureProviders.containsKey(pos) && structureProviders.get(pos).priority() < provider.priority()) return;
					outTiles.put(pos, texture);
					structureProviders.put(pos, provider);
				});
			}
		} else {
			Identifier pieceTypeId = BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.getType());
			if (pieceTypeTiles.containsKey(pieceTypeId)) {
				StructureTileProvider provider = pieceTypeTiles.get(pieceTypeId);
				provider.getTextures(summary, piece.getBoundingBox(), tilePredicates).forEach((pos, texture) -> {
					if (structureProviders.containsKey(pos) && structureProviders.get(pos).priority() < provider.priority()) return;
					outTiles.put(pos, texture);
					structureProviders.put(pos, provider);
				});
			}
		}
	}

	public void resolve(Map<ChunkPos, TileTexture> outTiles, Map<ChunkPos, StructureTileProvider> structureProviders, Map<ChunkPos, String> debugPredicates, Map<Landmark, MarkerTexture> outMarkers, WorldSummary summary, ResourceKey<Structure> key, ChunkPos pos, StructureStartSummary start, ResourceKey<StructureType<?>> type, Collection<TagKey<Structure>> tags) {
		if (startMarkers.containsKey(key.identifier())) {
			MarkerTexture texture = startMarkers.get(key.identifier());
			outMarkers.put(Landmark.create(WorldLandmarks.GLOBAL, key.identifier().withPath(p -> "start/" + p + "/" + pos.x() + "/" + pos.z()), b -> b
				.add(LandmarkComponentTypes.POS, pos.getMiddleBlockPosition(0))
				.add(LandmarkComponentTypes.NAME, Component.translatable(ProviderType.START.translation(key.identifier())))
			), texture);
		} else if (type != null && typeMarkers.containsKey(type.identifier())) {
			MarkerTexture texture = typeMarkers.get(type.identifier());
			outMarkers.put(Landmark.create(WorldLandmarks.GLOBAL, key.identifier().withPath(p -> "start/" + p + "/" + pos.x() + "/" + pos.z()), b -> b
				.add(LandmarkComponentTypes.POS, pos.getMiddleBlockPosition(0))
				.add(LandmarkComponentTypes.NAME, Component.translatable(ProviderType.TYPE.translation(type.identifier())))
			), texture);
		} else {
			tagMarkers.entrySet().stream().filter(entry -> tags.contains(TagKey.create(Registries.STRUCTURE, entry.getKey()))).findFirst().ifPresent(entry ->
				outMarkers.put(Landmark.create(WorldLandmarks.GLOBAL, key.identifier().withPath(p -> "start/" + p + "/" + pos.x() + "/" + pos.z()), b -> b
					.add(LandmarkComponentTypes.POS, pos.getMiddleBlockPosition(0))
					.add(LandmarkComponentTypes.NAME, Component.translatable(ProviderType.TAG.translation(entry.getKey())))
				), entry.getValue()));
		}

		if (startTiles.containsKey(key.identifier())) {
			StructureTileProvider provider = startTiles.get(key.identifier());
			provider.getTextures(summary, start.getBoundingBox(), debugPredicates).forEach((pos2, texture) -> {
				if (structureProviders.containsKey(pos) && structureProviders.get(pos).priority() < provider.priority()) return;
				outTiles.put(pos2, texture);
				structureProviders.put(pos2, provider);
			});
		} else if (type != null && typeTiles.containsKey(type.identifier())) {
			StructureTileProvider provider = typeTiles.get(key.identifier());
			provider.getTextures(summary, start.getBoundingBox(), debugPredicates).forEach((pos2, texture) -> {
				if (structureProviders.containsKey(pos) && structureProviders.get(pos).priority() < provider.priority()) return;
				outTiles.put(pos2, texture);
				structureProviders.put(pos2, provider);
			});
		} else {
			tags.stream().filter(t -> tagTiles.containsKey(t.location())).findFirst().ifPresent(tag -> {
				StructureTileProvider provider = tagTiles.get(tag.location());
				provider.getTextures(summary, start.getBoundingBox(), debugPredicates).forEach((pos2, texture) -> {
					if (structureProviders.containsKey(pos) && structureProviders.get(pos).priority() < provider.priority()) return;
					outTiles.put(pos2, texture);
					structureProviders.put(pos2, provider);
				});
			});
		}

		start.getChildren().forEach(p -> resolve(outTiles, structureProviders, debugPredicates, p, summary));
	}

	@Override
	protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
		RoleplayersAtlas.LOGGER.info("[Roleplayer's Atlas] Reloading Structure Tile / Marker Providers...");
		Map<Identifier, TileTexture> textures = TileTextures.getInstance().getTextures();
		Set<TileTexture> unusedTextures = new HashSet<>(textures.values().stream().filter(t -> t.id().getPath().startsWith("structure")).toList());

		PROVIDER_MAPS.values().forEach(p -> p.left().clear());
		PROVIDER_MAPS.values().forEach(p -> p.right().clear());
		for (Map.Entry<Identifier, JsonElement> fileEntry : prepared.entrySet()) {
			Identifier fileId = fileEntry.getKey();
			PROVIDER_MAPS.forEach((providerType, pair) -> {
				Map<Identifier, StructureTileProvider> providerMap = pair.left();
				Map<Identifier, MarkerTexture> markerMap = pair.right();
				if (fileId.getPath().startsWith(providerType.prefix())) {
					Identifier id = Identifier.fromNamespaceAndPath(fileId.getNamespace(), fileId.getPath().substring(providerType.prefix().length()));
					try {
						JsonObject fileJson = fileEntry.getValue().getAsJsonObject();
						if (fileJson.has("textures")) {
							JsonElement textureJson = fileJson.get("textures");
							int priority = fileJson.has("priority") ? fileJson.get("priority").getAsInt() : 999;
							List<TileTexture> defaultTextures = resolveTextureJson(textures, textureJson);
							if (defaultTextures != null) {
								StructureTileProvider provider = new StructureTileProvider(id, priority, defaultTextures);
								providerMap.put(provider.id(), provider);
								unusedTextures.removeAll(provider.allTextures());
							} else {
								JsonObject textureObject = textureJson.getAsJsonObject();
								Map<StructureTileProvider.ChunkMatcher, List<TileTexture>> matchers = new HashMap<>();
								for (String matcherKey : textureObject.keySet()) {
									Identifier matcherId = RoleplayersAtlas.id(matcherKey);
									StructureTileProvider.ChunkMatcher matcher = StructureTileProvider.getChunkMatcher(matcherId);
									if (matcher == null) throw new IllegalStateException("Matcher %s does not exist!".formatted(matcherId.toString()));
									List<TileTexture> matcherTextures = resolveTextureJson(textures, textureObject.get(matcherKey));
									if (matcherTextures == null) throw new IllegalStateException("Malformed object %s in textures object!".formatted(matcherId.toString()));
									matcherTextures.forEach(unusedTextures::remove);
									matchers.put(matcher, matcherTextures);
								}
								if (matchers.isEmpty()) {
									throw new IllegalStateException("No matcher keys were found in the textures object!");
								}
								StructureTileProvider provider = new StructureTileProvider(id, priority, matchers);
								providerMap.put(provider.id(), provider);
								unusedTextures.removeAll(provider.allTextures());
							}
						}
						if (fileJson.has("markers")) {
							JsonElement markerJson = fileJson.get("markers");
							Identifier markerTextureId = Identifier.tryParse(markerJson.getAsString());
							MarkerTexture texture = MarkerTextures.getInstance().asMap().get(markerTextureId);
							if (texture == null) throw new IllegalStateException("Marker texture %s does not exist!".formatted(markerTextureId));
							RoleplayersAtlas.CONFIG.structureMarkers.putIfAbsent(fileId.toString(), true);
							if (RoleplayersAtlas.CONFIG.structureMarkers.get(fileId.toString())) {
								markerMap.put(id, texture);
							}
						}
					} catch (Exception e) {
						RoleplayersAtlas.LOGGER.error("[Roleplayer's Atlas] Error reading structure tile provider {}!", fileId, e);
					}
				}
			});
		}

		for (TileTexture texture : unusedTextures) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Tile texture {} isn't referenced by any structure tile provider!", texture.displayId());
		}
	}

	@Override
	public Identifier getFabricId() {
		return ID;
	}

	@Override
	public Collection<Identifier> getFabricDependencies() {
		return List.of(TileTextures.ID, MarkerTextures.ID);
	}
}
