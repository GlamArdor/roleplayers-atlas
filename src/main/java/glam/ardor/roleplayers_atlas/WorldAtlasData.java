package glam.ardor.roleplayers_atlas;

import com.google.common.collect.Multimap;
import glam.ardor.roleplayers_atlas.gui.AtlasScreen;
import glam.ardor.roleplayers_atlas.reloader.BiomeTileProviders;
import glam.ardor.roleplayers_atlas.reloader.MarkerTextures;
import glam.ardor.roleplayers_atlas.reloader.StructureTileProviders;
import glam.ardor.roleplayers_atlas.reloader.TileTextures;
import glam.ardor.roleplayers_atlas.util.Rect;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentMap;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

public class WorldAtlasData {
	public static final Map<RegistryKey<World>, WorldAtlasData> WORLDS = new HashMap<>();

	public static WorldAtlasData getOrCreate(RegistryKey<World> dimension) {
		return WORLDS.computeIfAbsent(dimension, k -> {
			WorldAtlasData data = new WorldAtlasData();
			data.dimension = k;
			data.refreshCities();
			return data;
		});
	}

	/**
	 * The same, but told which world's data it is about to be handed.
	 * <p>
	 * A dimension key is not a world. Sent from a lobby to the main server, a
	 * player never disconnects — the proxy simply swaps what is behind
	 * {@code minecraft:overworld} — so the map went on showing the lobby's land
	 * and the lobby's marks, and those marks could not be deleted because they
	 * no longer existed in the world they were being deleted from.
	 * <p>
	 * Binding to the summary itself catches that: a different one means a
	 * different world, whatever the dimension is called.
	 */
	public static WorldAtlasData getOrCreate(WorldSummary summary) {
		WorldAtlasData data = getOrCreate(summary.dimension());
		if (data.boundSummary != summary) {
			if (data.boundSummary != null) data.reset();
			data.boundSummary = summary;
		}
		return data;
	}

	/** The world this data belongs to, to tell it from another wearing the same name. */
	private WorldSummary boundSummary = null;

	/** Which dimension this data is for — needed to look up what was drawn on it. */
	private RegistryKey<World> dimension = null;

	/**
	 * Towns drawn by hand, resolved to textures once rather than looked up per
	 * tile. Held here rather than read from {@link CityPaint} on every draw
	 * because {@link #getTile} runs for every visible chunk of every frame.
	 */
	protected final Map<ChunkPos, TileTexture> cityTiles = new HashMap<>();

	/** Picks the hand-drawn town back up after it changed on disk or in the window. */
	public void refreshCities() {
		cityTiles.clear();
		if (dimension != null) {
			CityPaint.allAt(dimension).forEach((pos, id) -> {
				TileTexture texture = TileTextures.getInstance().getTextures().get(id);
				if (texture != null) cityTiles.put(pos, texture);
			});
		}
		invalidateTileBatches();
	}

	public static void refreshAllCities() {
		WORLDS.values().forEach(WorldAtlasData::refreshCities);
	}

	/** Which biome is drawn at this spot, or null where it is water or unexplored. */
	public @Nullable Identifier biomeAt(ChunkPos pos) {
		return chunkBiomes.get(pos);
	}

	/** Whether anything has been drawn here — painting empty page would show nothing. */
	public boolean hasTile(ChunkPos pos) {
		return biomeTiles.containsKey(pos);
	}

	/** Set while a redraw is working through, so it can be heard rather than guessed at. */
	private boolean redrawing = false;

	/** Forgets everything drawn, so the new world starts from a blank page. */
	private void reset() {
		biomeTiles.clear();
		chunkBiomes.clear();
		structureTiles.clear();
		landmarkMarkers.clear();
		structureMarkers.clear();
		terrainDeque.clear();
		terrainDequeHash.clear();
		debugBiomePredicates.clear();
		debugStructurePredicates.clear();
		debugBiomes.clear();
		debugStructures.clear();
		tileScope.set(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
		isFinished = false;
		refreshCities();
		// The world changed under us — every remembered step refers to the old one.
		AtlasUndo.clear();
		invalidateTileBatches();
		if (MinecraftClient.getInstance().currentScreen instanceof AtlasScreen as) as.updateBookmarkerList();
	}

	public static boolean isEmpty(RegistryKey<World> dimension) {
		return !WORLDS.containsKey(dimension) || WORLDS.get(dimension).isEmpty();
	}

	protected final Map<ChunkPos, TileTexture> biomeTiles = new HashMap<>();
	/** The biome each drawn chunk came from, so a click can name it. */
	protected final Map<ChunkPos, Identifier> chunkBiomes = new HashMap<>();
	protected final Map<ChunkPos, TileTexture> structureTiles = new HashMap<>();
	protected final Map<UUID, Map<Identifier, Pair<Landmark, MarkerTexture>>> landmarkMarkers = new ConcurrentHashMap<>();
	protected final Map<Landmark, MarkerTexture> structureMarkers = new ConcurrentHashMap<>();

	protected final Rect tileScope = new Rect(0, 0, 0, 0);
	protected final Set<ChunkPos> terrainDequeHash = new HashSet<>();
	protected final Deque<ChunkPos> terrainDeque = new ConcurrentLinkedDeque<>();
	protected boolean isFinished = false;

	// Debug Display Info
	protected final Map<ChunkPos, String> debugBiomePredicates = new HashMap<>();
	protected final Map<ChunkPos, String> debugStructurePredicates = new HashMap<>();
	protected final Map<ChunkPos, TerrainTileProvider> debugBiomes = new HashMap<>();
	protected final Map<ChunkPos, StructureTileProvider> debugStructures = new HashMap<>();

	// Subtile batches are expensive to assemble at close zoom, so they're cached
	// per visible scope and invalidated whenever any tile changes.
	private record TileBatchKey(int startX, int startZ, int endX, int endZ, int step) {
	}

	private final java.util.LinkedHashMap<TileBatchKey, Map<TileTexture, java.util.List<glam.ardor.roleplayers_atlas.gui.tiles.SubTile>>> tileBatchCache = new java.util.LinkedHashMap<>(8, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<TileBatchKey, Map<TileTexture, java.util.List<glam.ardor.roleplayers_atlas.gui.tiles.SubTile>>> eldest) {
			return size() > 4;
		}
	};

	public Map<TileTexture, java.util.List<glam.ardor.roleplayers_atlas.gui.tiles.SubTile>> getTileBatches(int startChunkX, int startChunkZ, int endChunkX, int endChunkZ, int step) {
		synchronized (tileBatchCache) {
			return tileBatchCache.computeIfAbsent(new TileBatchKey(startChunkX, startChunkZ, endChunkX, endChunkZ, step), k -> {
				glam.ardor.roleplayers_atlas.gui.tiles.TileRenderIterator tiles = new glam.ardor.roleplayers_atlas.gui.tiles.TileRenderIterator(this);
				tiles.setScope(new Rect(k.startX(), k.startZ(), k.endX(), k.endZ()));
				tiles.setStep(k.step());
				Map<TileTexture, java.util.List<glam.ardor.roleplayers_atlas.gui.tiles.SubTile>> out = new it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap<>();
				for (glam.ardor.roleplayers_atlas.gui.tiles.SubTileQuartet quartet : tiles) {
					for (glam.ardor.roleplayers_atlas.gui.tiles.SubTile subtile : quartet) {
						if (subtile == null || subtile.texture == null) continue;
						out.computeIfAbsent(subtile.texture, t -> new java.util.ArrayList<>()).add(subtile.copy());
					}
				}
				return out;
			});
		}
	}

	/**
	 * Puts every chunk already drawn back in the queue to be worked out again.
	 * <p>
	 * The old tiles are left in place while it happens, so the map redraws
	 * rather than blinking empty. Used when a setting changes what a biome
	 * should look like — without it the change only showed on land explored
	 * afterwards, which reads as the setting doing nothing.
	 */
	public void retile() {
		for (ChunkPos pos : biomeTiles.keySet()) {
			if (terrainDequeHash.add(pos)) terrainDeque.add(pos);
		}
		isFinished = false;
		redrawing = true;
	}

	public static void retileAll() {
		WORLDS.values().forEach(WorldAtlasData::retile);
	}

	/**
	 * Rewrites every death marker's epitaph, so a change of reckoning shows on
	 * the graves already drawn instead of waiting for the next rejoin.
	 * <p>
	 * The names are rebuilt from the landmarks Surveyor holds rather than from
	 * the markers on the page, because a marker's name is already the epitaph
	 * and re-reading it would write the old date inside the new one.
	 */
	public static void redateGraves() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) return;
		RegistryKey<World> dimension = client.world.getRegistryKey();
		WorldSummary summary = SurveyorClient.tryGetSummary(dimension);
		WorldAtlasData data = WORLDS.get(dimension);
		if (summary == null || summary.landmarks() == null || data == null) return;
		data.onLandmarksAdded(summary, summary.landmarks().keySet(SurveyorClient.getExploration()));
	}

	public void invalidateTileBatches() {
		synchronized (tileBatchCache) {
			tileBatchCache.clear();
		}
	}


	private boolean isEmpty() {
		return tileScope.maxY == tileScope.minY && terrainDequeHash.isEmpty();
	}

	public void onTerrainUpdated(WorldSummary summary, Map<RegionPos, BitSet> chunks) {
		for (ChunkPos pos : RegionPos.regionsToChunks(chunks)) {
			if (!biomeTiles.containsKey(pos) && !terrainDequeHash.contains(pos)) {
				terrainDequeHash.add(pos);
				terrainDeque.add(pos);
			}
		}
	}

	public void onStructuresAdded(WorldSummary summary, Multimap<RegistryKey<Structure>, ChunkPos> starts) {
		starts.forEach((key, pos) -> StructureTileProviders.getInstance().resolve(structureTiles, debugStructures, debugStructurePredicates, structureMarkers, summary, key, pos, summary.structures().get(key, pos), summary.structures().getType(key), summary.structures().getTags(key)));
		invalidateTileBatches();
	}

	/**
	 * Works through the chunks waiting to be drawn.
	 * <p>
	 * Split out from the world tick because the atlas pauses the game: with the
	 * map open nothing else ticks, so a correction made while looking at it
	 * would sit in the queue until the screen was closed — which reads as the
	 * correction having done nothing. The screen calls this itself.
	 */
	public void drawQueued(WorldSummary summary, int limit) {
		if (!BiomeTileProviders.getInstance().hasFallbacks()) return;
		boolean tilesChanged = false;
		for (int i = 0; i < limit; i++) {
			ChunkPos pos = terrainDeque.pollFirst();
			terrainDequeHash.remove(pos);
			if (pos == null) break;
			TileChoice tile = summary.dimension() == World.NETHER ? TerrainTiling.terrainToTileNether(summary, pos) : TerrainTiling.terrainToTile(summary, pos);
			if (tile != null) {
				tileScope.extendTo(pos.x, pos.z);
				// A patch names this one chunk and outranks anything worked out
				// from its biome. The height tier still applies on top, so a
				// painted forest still climbs its hills.
				Identifier patch = BiomeOverrides.patch(summary.dimension(), pos);
				TerrainTileProvider provider = patch == null ? tile.provider() : BiomeTileProviders.getInstance().getTileProvider(patch);
				biomeTiles.put(pos, provider.getTexture(pos, tile.elevation()));
				debugBiomes.put(pos, provider);
				debugBiomePredicates.put(pos, tile.elevation() == null ? null : tile.elevation().getName());
				// Remembered so a click on the map can say what biome is there.
				if (tile.biomeId() == null) chunkBiomes.remove(pos); else chunkBiomes.put(pos, tile.biomeId());
				tilesChanged = true;
			}
		}
		if (tilesChanged) invalidateTileBatches();
		// A redraw the player asked for should be heard happening; the first fill
		// after joining a world should not, which is why only retile sets this.
		if (redrawing) {
			if (tilesChanged) glam.ardor.roleplayers_atlas.AtlasSounds.redrawing();
			if (terrainDeque.isEmpty()) redrawing = false;
		}
	}

	public void tick(WorldSummary summary) {
		if (!BiomeTileProviders.getInstance().hasFallbacks()) return;
		drawQueued(summary, RoleplayersAtlas.CONFIG.chunkTickLimit);
		if (!isFinished && terrainDeque.isEmpty()) {
			isFinished = true;
			RoleplayersAtlas.LOGGER.info("[Roleplayer's Atlas] Finished loading terrain for {} - {} tiles.", summary.dimension(), biomeTiles.size());
		}
		if (--verifyCooldown <= 0) {
			verifyCooldown = 20;
			verifyNearbyHearsay(summary);
			checkArrivals(summary);
		}
	}

	private int verifyCooldown = 20;

	/** How close you have to come to something you were told about to call it seen. */
	private static final double VERIFY_RANGE = 24.0;

	/** How close counts as having got there — near enough that the arrow has nothing left to say. */
	private static final double ARRIVE_RANGE = 16.0;

	/**
	 * Drops a guide arrow once its owner reaches what it was pointing at. A
	 * route counts as walked when either of its ends is reached: it's a path
	 * between two places, and which one you set out for is your business.
	 */
	private void checkArrivals(WorldSummary summary) {
		if (!RoleplayersAtlas.CONFIG.clearTrackingOnArrival || RoleplayersAtlas.trackedMarkers.isEmpty()) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !client.player.getEntityWorld().getRegistryKey().equals(summary.dimension())) return;
		double px = client.player.getX();
		double pz = client.player.getZ();
		ChunkPos playerChunk = new ChunkPos(client.player.getBlockPos());
		boolean any = false;
		for (Landmark landmark : getAllMarkers(1).keySet()) {
			String key = RoleplayersAtlas.trackKey(landmark);
			if (!RoleplayersAtlas.trackedMarkers.contains(key)) continue;
			if (!arrived(landmark, px, pz, playerChunk)) continue;
			RoleplayersAtlas.trackedMarkers.remove(key);
			any = true;
		}
		if (any) {
			TrackedMarkersStore.save();
			AtlasSounds.arrived();
			if (MinecraftClient.getInstance().currentScreen instanceof AtlasScreen as) as.updateBookmarkerList();
		}
	}

	private static boolean arrived(Landmark landmark, double px, double pz, ChunkPos playerChunk) {
		java.util.List<net.minecraft.util.math.BlockPos> route = landmark.get(AtlasComponents.ROUTE);
		if (route != null && !route.isEmpty()) {
			return within(route.getFirst(), px, pz) || within(route.getLast(), px, pz);
		}
		net.minecraft.util.math.BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
		if (pos != null) return within(pos, px, pz);
		if (landmark.contains(LandmarkComponentTypes.CHUNKS)) {
			return RegionPos.regionsToChunks(landmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new HashMap<>())).contains(playerChunk);
		}
		return false;
	}

	private static boolean within(net.minecraft.util.math.BlockPos pos, double px, double pz) {
		return Math.hypot(pos.getX() + 0.5 - px, pos.getZ() + 0.5 - pz) <= ARRIVE_RANGE;
	}

	/**
	 * Turns hearsay into knowledge. A mark copied from someone else's scroll
	 * stays faint until its owner has actually stood next to it — at which
	 * point it firms up and records the day it was checked. Territories count
	 * as reached once you're inside them; everything else, by distance.
	 */
	private void verifyNearbyHearsay(WorldSummary summary) {
		if (summary.landmarks() == null) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !client.player.getEntityWorld().getRegistryKey().equals(summary.dimension())) return;
		double px = client.player.getX();
		double pz = client.player.getZ();
		ChunkPos playerChunk = new ChunkPos(client.player.getBlockPos());
		for (Map.Entry<Landmark, MarkerTexture> entry : getEditableLandmarks().entrySet()) {
			Landmark landmark = entry.getKey();
			if (!AtlasTime.isUnverified(landmark)) continue;
			if (!reached(landmark, px, pz, playerChunk)) continue;
			long day = AtlasTime.gameDay();
			long seenAt = AtlasTime.realMillis();
			summary.landmarks().put(copyLandmarkWith(landmark, landmark.id(), m -> {
				m.set(AtlasComponents.CONFIRMED_DAY, day);
				m.set(AtlasComponents.CONFIRMED_REAL_TIME, seenAt);
			}));
			AtlasSounds.hearsayConfirmed();
		}
	}

	private static boolean reached(Landmark landmark, double px, double pz, ChunkPos playerChunk) {
		net.minecraft.util.math.BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
		if (pos != null && Math.hypot(pos.getX() + 0.5 - px, pos.getZ() + 0.5 - pz) <= VERIFY_RANGE) return true;
		java.util.List<net.minecraft.util.math.BlockPos> route = landmark.get(AtlasComponents.ROUTE);
		if (route != null) {
			for (net.minecraft.util.math.BlockPos point : route) {
				if (Math.hypot(point.getX() + 0.5 - px, point.getZ() + 0.5 - pz) <= VERIFY_RANGE) return true;
			}
		}
		if (landmark.contains(LandmarkComponentTypes.CHUNKS)) {
			return RegionPos.regionsToChunks(landmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new HashMap<>())).contains(playerChunk);
		}
		return false;
	}

	public Rect getScope() {
		return tileScope;
	}

	/** Chunks with resolved terrain tiles — the explored area. */
	public java.util.Set<ChunkPos> exploredChunks() {
		return biomeTiles.keySet();
	}

	public TileTexture getTile(int x, int z) {
		return getTile(new ChunkPos(x, z));
	}

	public TileTexture getTile(ChunkPos pos) {
		if (!biomeTiles.containsKey(pos)) return RoleplayersAtlas.CONFIG.emptyHandling == AtlasConfig.EmptyHandling.CLOUDS ? TileTextures.getInstance().getTextures().get(RoleplayersAtlas.id("clouds")) : null;
		// A town drawn by hand outranks anything the game found here: the player
		// built it, and no structure the generator placed knows about it.
		TileTexture drawn = cityTiles.get(pos);
		if (drawn != null) return drawn;
		return structureTiles.getOrDefault(pos, biomeTiles.get(pos));
	}

	public Identifier getProvider(ChunkPos pos) {
		if (structureTiles.containsKey(pos)) {
			return debugStructures.get(pos).id();
		} else {
			return debugBiomes.containsKey(pos) ? debugBiomes.get(pos).id() : null;
		}
	}

	public String getTilePredicate(ChunkPos pos) {
		if (structureTiles.containsKey(pos)) {
			return debugStructurePredicates.get(pos);
		} else {
			return debugBiomePredicates.get(pos);
		}
	}

	public void addLandmarkMarker(Landmark landmark, MarkerTexture texture) {
		landmarkMarkers.computeIfAbsent(landmark.owner(), t -> new ConcurrentHashMap<>()).put(landmark.id(), Pair.of(landmark, texture));
	}

	public static Landmark copyLandmarkWith(Landmark landmark, Identifier id, Consumer<LandmarkComponentMap> modifier) {
		LandmarkComponentMap copy = LandmarkComponentMap.builder().build();
		landmark.components().keySet().forEach(t -> copy.set(t, landmark.components().get(t)));
		modifier.accept(copy);
		return new Landmark(landmark.owner(), id, copy);
	}

	public void addLandmark(WorldSummary summary, Landmark landmark) {
		if (landmark == null) return;
		if (landmark.id().getPath().startsWith("grave")) {
			// Disabled deaths: new grave markers are not added at all.
			if (!RoleplayersAtlas.CONFIG.deathMarkers) return;
			// Death markers get their own auto-created layer on first death.
			if (MarkerLayers.get(MarkerLayers.DEATHS_ID) == null) {
				MarkerLayers.put(new MarkerLayers.MapLayer(MarkerLayers.DEATHS_ID, Text.translatable("gui.roleplayers_atlas.layer.deaths").getString(), 0xB03A3A));
			}
			Landmark grave = dateGrave(summary, landmark);
			AtlasConfig.GraveStyle style = RoleplayersAtlas.CONFIG.graveStyle;
			Text name = grave.get(LandmarkComponentTypes.NAME);
			if (name == null && style == AtlasConfig.GraveStyle.CAUSE) style = AtlasConfig.GraveStyle.DIED;
			String styleName = style.toString().toLowerCase();
			MutableText verb = switch (style) {
				case CAUSE -> name.copy().formatted(Formatting.GRAY).formatted(Formatting.RED);
				case GRAVE, ITEMS, DIED -> Text.translatable("gui.roleplayers_atlas.marker.death.%s.verb".formatted(styleName)).formatted(Formatting.RED);
				case EUPHEMISMS -> Text.translatable("gui.roleplayers_atlas.marker.death.%s.verb.%s".formatted(styleName, new Random(grave.getOrDefault(LandmarkComponentTypes.SEED, 0)).nextInt(11))).formatted(Formatting.RED);
			};
			// A grave dug before this update has no real moment recorded, so the
			// server's reckoning can't date it; it keeps the day number it has
			// always shown rather than being given an invented cycle.
			Long graveReal = grave.get(AtlasComponents.REAL_TIME);
			boolean reign = RoleplayersAtlas.CONFIG.reckoning == AtlasConfig.Reckoning.REIGN && graveReal != null && graveReal > 0;
			MutableText timeText = (reign
				? AtlasTime.inWorldDate(grave.get(AtlasComponents.DAY), graveReal).copy()
				: Text.literal(String.valueOf(grave.getOrDefault(AtlasComponents.DAY, 0L)))).formatted(Formatting.WHITE);
			String key = "gui.roleplayers_atlas.marker.death.%s%s".formatted(styleName, reign ? ".reign" : "");
			// With in-world dates turned off the epitaph is the verb on its own.
			MutableText text = RoleplayersAtlas.CONFIG.showMarkDate
				? Text.translatable(key, verb, timeText).formatted(Formatting.GRAY)
				: verb.copy().formatted(Formatting.GRAY);
			addLandmarkMarker(copyLandmarkWith(grave, grave.id(), m -> {
				m.set(LandmarkComponentTypes.COLOR, DyeColor.GRAY.getEntityColor());
				m.set(LandmarkComponentTypes.NAME, text);
			}), MarkerTextures.getInstance().fromLandmark(grave, style == AtlasConfig.GraveStyle.ITEMS ? "items" : null));
		} else {
			addLandmarkMarker(landmark, MarkerTextures.getInstance().fromLandmark(landmark));
		}
	}

	/**
	 * Graves are made by Surveyor, which dates them by the world's time of day —
	 * a clock a server can freeze or reset, so every death can end up reading
	 * "day 1". They get the atlas' own game-time day instead, written into the
	 * grave once so it stays put and travels with an exported scroll.
	 * <p>
	 * Graves that were already on the map when this ran keep the number they
	 * always showed: the moment they were dug isn't recorded anywhere, so it
	 * can't be recovered, and inventing today's date for them would be a lie.
	 * Only the field it is read from changes.
	 */
	private Landmark dateGrave(WorldSummary summary, Landmark landmark) {
		if (landmark.get(AtlasComponents.DAY) != null) return landmark;
		boolean fresh = net.minecraft.util.Util.getMeasuringTimeMs() - RoleplayersAtlas.worldJoinedMs > 5000L;
		long day = fresh ? AtlasTime.gameDay() : 1 + landmark.getOrDefault(LandmarkComponentTypes.TIME, 0L) / 24000L;
		Landmark dated = copyLandmarkWith(landmark, landmark.id(), m -> {
			m.set(AtlasComponents.DAY, day);
			if (fresh) m.set(AtlasComponents.REAL_TIME, AtlasTime.realMillis());
		});
		if (summary != null && summary.landmarks() != null) summary.landmarks().put(dated);
		return dated;
	}

	public void onLandmarksAdded(WorldSummary summary, Multimap<UUID, Identifier> landmarks) {
		landmarks.forEach((type, pos) -> this.addLandmark(summary, summary.landmarks().get(type, pos)));
		if (MinecraftClient.getInstance().currentScreen instanceof AtlasScreen as) as.updateBookmarkerList();
	}

	public void onLandmarksRemoved(WorldSummary summary, Multimap<UUID, Identifier> landmarks) {
		landmarks.forEach((type, pos) -> {
			if (landmarkMarkers.containsKey(type)) {
				landmarkMarkers.get(type).remove(pos);
				if (landmarkMarkers.get(type).isEmpty()) landmarkMarkers.remove(type);
			}
		});
		if (MinecraftClient.getInstance().currentScreen instanceof AtlasScreen as) as.updateBookmarkerList();
	}

	public boolean deleteLandmark(RegistryKey<World> dimension, Landmark landmark) {
		WorldSummary summary = SurveyorClient.tryGetSummary(dimension);
		if (summary == null || summary.landmarks() == null || landmark.owner().equals(WorldLandmarks.GLOBAL) || !SurveyorClient.canModify(landmark.owner())) return false;
		// Surveyor drops a removal for a mark it doesn't have without a word, so
		// a stale one would sit there being clicked at forever. Say so instead.
		if (!summary.landmarks().contains(landmark.owner(), landmark.id())) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] {} isn't in this world's data — dropping it from the map instead.", landmark.id());
			onLandmarksRemoved(summary, com.google.common.collect.ImmutableMultimap.of(landmark.owner(), landmark.id()));
			return true;
		}
		// An erased mark is the loss people feel worst, so it is the one most
		// worth being able to take back.
		swapLandmark(dimension, landmark, null, Text.translatable("gui.roleplayers_atlas.undo.marker",
			landmark.getOrDefault(LandmarkComponentTypes.NAME, Text.translatable("gui.roleplayers_atlas.unnamedMarker"))));
		return true;
	}

	/**
	 * Takes one mark off the map and puts another on, as one act. Both halves are
	 * optional: nothing out is an addition, nothing in is an erasure, and both is
	 * an edit. Its own inverse is filed as it goes, so taking it back and doing
	 * it again are the same machinery.
	 */
	public static void swapLandmark(RegistryKey<World> dimension, @Nullable Landmark remove, @Nullable Landmark put, Text description) {
		WorldSummary summary = SurveyorClient.tryGetSummary(dimension);
		if (summary == null || summary.landmarks() == null) return;
		if (remove != null) summary.landmarks().remove(remove.owner(), remove.id());
		if (put != null) summary.landmarks().put(put);
		AtlasUndo.push(description, () -> swapLandmark(dimension, put, remove, description));
		if (MinecraftClient.getInstance().currentScreen instanceof AtlasScreen as) as.updateBookmarkerList();
	}

	public Map<Landmark, MarkerTexture> getEditableLandmarks() {
		Map<Landmark, MarkerTexture> map = new HashMap<>();
		landmarkMarkers.forEach((type, landmarks) -> landmarks.forEach((pos, pair) -> { // Don't allow editing global landmarks via GUI.
			if (!pair.left().owner().equals(WorldLandmarks.GLOBAL) && SurveyorClient.canModify(pair.left().owner())) map.put(pair.left(), pair.right());
		}));
		return map;
	}

	public Map<Landmark, MarkerTexture> getAllMarkers(int tileChunks) {
		Map<Landmark, MarkerTexture> map = new HashMap<>();
		landmarkMarkers.forEach((type, landmarks) -> landmarks.forEach((pos, pair) -> map.put(pair.left(), pair.right())));
		structureMarkers.forEach((landmark, texture) -> {
			if (tileChunks >= texture.nearClip() && tileChunks <= texture.farClip()) map.put(landmark, texture);
		});
		// The respawn point isn't stored anywhere — it joins the map here, which
		// is enough for it to render, be hovered and carry a guide arrow.
		Landmark spawn = SpawnMarker.get(dimensionOf());
		if (spawn != null) map.put(spawn, SpawnMarker.texture());
		return map;
	}

	/** Which dimension this data belongs to, resolved from the registry it was filed under. */
	private RegistryKey<World> dimensionOf() {
		for (Map.Entry<RegistryKey<World>, WorldAtlasData> entry : WORLDS.entrySet()) {
			if (entry.getValue() == this) return entry.getKey();
		}
		return null;
	}

	public MarkerTexture getMarkerTexture(Landmark landmark) {
		if (SpawnMarker.is(landmark)) return SpawnMarker.texture();
		return landmarkMarkers.containsKey(landmark.owner()) && landmarkMarkers.get(landmark.owner()).containsKey(landmark.id()) ? landmarkMarkers.get(landmark.owner()).get(landmark.id()).right() : structureMarkers.get(landmark);
	}

	public boolean isLoading() {
		return terrainDequeHash.size() > 20;
	}
}
