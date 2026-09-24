package glam.ardor.roleplayers_atlas;

import glam.ardor.roleplayers_atlas.gui.AtlasScreen;
import glam.ardor.roleplayers_atlas.gui.core.ScreenState;
import glam.ardor.roleplayers_atlas.reloader.BiomeTileProviders;
import glam.ardor.roleplayers_atlas.reloader.MarkerTextures;
import glam.ardor.roleplayers_atlas.reloader.StructureTileProviders;
import glam.ardor.roleplayers_atlas.reloader.TileTextures;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.client.SurveyorClientEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RoleplayersAtlas implements ClientModInitializer {
	public static final String ID = "roleplayers_atlas";

	/** The zone radius a named mark uses until one is set on the mark itself. */
	public static final int DEFAULT_ZONE_RADIUS = 32;
	public static final String NAME = "Roleplayer's Atlas";

	/** Landmarks tracked with a guide arrow this session, keyed by owner+id. */
	public static final java.util.Set<String> trackedMarkers = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public static String trackKey(folk.sisby.surveyor.landmark.Landmark landmark) {
		return landmark.owner() + "/" + landmark.id();
	}

	/** Layer ids currently hidden by the filter tabs (session). A landmark without a LAYER component is "personal". */
	public static final java.util.Set<String> hiddenLayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** When this client joined, used to tell landmarks that just happened from ones that were already there. */
	public static long worldJoinedMs = 0;

	public static String layerOf(folk.sisby.surveyor.landmark.Landmark landmark) {
		String layer = landmark.get(AtlasComponents.LAYER);
		if (layer != null) return layer;
		// Death markers implicitly live in the auto-created deaths layer.
		if (landmark.id().getPath().startsWith("grave")) return MarkerLayers.DEATHS_ID;
		return MarkerLayers.DEFAULT_ID;
	}

	/** Whether the landmark's layer is currently shown. Global (structure) landmarks always are. */
	public static boolean layerVisible(folk.sisby.surveyor.landmark.Landmark landmark) {
		if (landmark.id().getPath().startsWith("grave") && !CONFIG.deathMarkers) return false;
		if (landmark.owner() == null || landmark.owner().equals(folk.sisby.surveyor.landmark.WorldLandmarks.GLOBAL)) return true;
		return !hiddenLayers.contains(layerOf(landmark));
	}

	public static final Logger LOGGER = LogManager.getLogger(NAME);

	public static final AtlasConfig CONFIG = AtlasConfig.createToml(FabricLoader.getInstance().getConfigDir(), "", "roleplayers-atlas", AtlasConfig.class);
	public static final ScreenState<AtlasScreen> lastState = new ScreenState<>();

	public static Identifier id(String path) {
		return path.contains(":") ? Identifier.tryParse(path) : Identifier.of(ID, path);
	}

	/**
	 * The book the player appears to be holding while the atlas is out. It is
	 * never in anyone's inventory – the hold mode conjures it for the duration
	 * and the renderer swaps it in, so the atlas needs no item to exist.
	 */
	public static ItemStack virtualAtlasStack() {
		ItemStack stack = Items.BOOK.getDefaultStack().copy();
		stack.set(DataComponentTypes.ITEM_MODEL, id("atlas"));
		stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("gui.roleplayers_atlas.atlas"));
		return stack;
	}

	public static AtlasScreen openAtlasScreen() {
		if (MinecraftClient.getInstance().currentScreen == null) {
			AtlasScreen screen = new AtlasScreen();
			screen.init();
			screen.prepareToOpen();
			screen.tick();
			MinecraftClient.getInstance().setScreen(screen);
			return screen;
		}
		return null;
	}

	public static Map<UUID, PlayerSummary> getOrderedFriends() {
		Map<UUID, PlayerSummary> friends = SurveyorClient.getFriends();
		PlayerSummary playerSummary = friends.remove(SurveyorClient.getClientUuid());
		Map<UUID, PlayerSummary> orderedFriends = new LinkedHashMap<>(friends);
		if (playerSummary != null) orderedFriends.put(SurveyorClient.getClientUuid(), playerSummary);
		return orderedFriends;
	}

	@Override
	public void onInitializeClient() {
		AtlasComponents.init();
		AtlasKeybindings.init();
		ZoneTitles.init();
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(TileTextures.getInstance());
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(StructureTileProviders.getInstance());
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(BiomeTileProviders.getInstance());
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(MarkerTextures.getInstance());

		// getOrCreate(summary), not the dimension: a proxy can put a different
		// world behind the same dimension without the client ever disconnecting.
		SurveyorClientEvents.Register.terrainUpdated(id("world_data"), (s, k) -> WorldAtlasData.getOrCreate(s).onTerrainUpdated(s, k));
		SurveyorClientEvents.Register.structuresAdded(id("world_data"), (s, k) -> WorldAtlasData.getOrCreate(s).onStructuresAdded(s, k));
		SurveyorClientEvents.Register.landmarksAdded(id("world_data"), (s, k) -> WorldAtlasData.getOrCreate(s).onLandmarksAdded(s, k));
		SurveyorClientEvents.Register.landmarksRemoved(id("world_data"), (s, k) -> WorldAtlasData.getOrCreate(s).onLandmarksRemoved(s, k));
		ClientTickEvents.END_WORLD_TICK.register((w -> SurveyorClient.getSummaries(MinecraftClient.getInstance().getNetworkHandler()).values().forEach(s -> WorldAtlasData.getOrCreate(s).tick(s))));
		CommonLifecycleEvents.TAGS_LOADED.register(((manager, client) -> BiomeTileProviders.getInstance().registerFallbacks(manager.getOrThrow(RegistryKeys.BIOME))));
		ClientPlayConnectionEvents.DISCONNECT.register(((handler, client) -> BiomeTileProviders.getInstance().clearFallbacks()));
		ClientPlayConnectionEvents.DISCONNECT.register(((handler, client) -> WorldAtlasData.WORLDS.clear()));
		// Landmarks already on the map arrive in a burst right after joining;
		// anything later is a death that just happened and can be dated.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> worldJoinedMs = net.minecraft.util.Util.getMeasuringTimeMs());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> TrackedMarkersStore.load(client));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> MarkerLayers.load(client));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SpawnMarker.load(client));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (CONFIG.hideDeathsByDefault) hiddenLayers.add(MarkerLayers.DEATHS_ID);
		});
		ClientTickEvents.END_CLIENT_TICK.register(SpawnMarker::tick);
		// Corrections are per world, and a proxy can change the world underfoot.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world != null) {
				String world = glam.ardor.roleplayers_atlas.util.WorldKey.current(client);
				BiomeOverrides.bind(world);
				CityPaint.bind(world);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BiomeOverrides.clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CityPaint.clear());
		// A step that says "put this landmark back" means nothing in another world.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AtlasUndo.clear());
		ClientTickEvents.END_CLIENT_TICK.register(MapAutosave::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpawnMarker.clear());
		ClientTickEvents.END_CLIENT_TICK.register(ParchmentExport::tickCapture);
		ClientTickEvents.END_CLIENT_TICK.register(AtlasScreen::tickFullExport);
		// The Cloth settings screen shows its edits as they are made; the ticking
		// is what reads its widgets back into the config while it is open.
		ClientTickEvents.END_CLIENT_TICK.register(glam.ardor.roleplayers_atlas.gui.LivePreview::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TrackedMarkersStore.save());


		WorldSummary.enableTerrain();
		WorldSummary.enableStructures();
		WorldSummary.enableLandmarks();

		FabricLoader.getInstance().getModContainer(ID).ifPresent(c -> ResourceManagerHelper.registerBuiltinResourcePack(id("shader_patch"), c, Text.of("Shader Patch"), ResourcePackActivationType.NORMAL));
	}
}
