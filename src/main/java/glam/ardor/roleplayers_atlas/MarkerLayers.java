package glam.ardor.roleplayers_atlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Player-defined marker layers: each has a name and a color, chosen by the
 * player, and acts as a filterable set of landmarks. Persisted per
 * world/server. The "personal" layer always exists and holds every landmark
 * without an explicit layer component.
 */
public final class MarkerLayers {
	public record MapLayer(String id, String name, int color) {
	}

	public static final String DEFAULT_ID = "personal";
	public static final String DEATHS_ID = "deaths";

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("roleplayers-atlas").resolve("layers.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final java.lang.reflect.Type TYPE = new TypeToken<Map<String, List<MapLayer>>>() {
	}.getType();

	private static final List<MapLayer> custom = new CopyOnWriteArrayList<>();
	private static String worldId = null;

	private MarkerLayers() {
	}

	public static MapLayer defaultLayer() {
		return new MapLayer(DEFAULT_ID, Component.translatable("gui.roleplayers_atlas.layer.personal").getString(), 0xF9FFFE);
	}

	/** The default layer followed by the player's custom layers. */
	public static List<MapLayer> all() {
		List<MapLayer> all = new ArrayList<>();
		all.add(defaultLayer());
		all.addAll(custom);
		return all;
	}

	public static MapLayer get(String id) {
		if (id == null || DEFAULT_ID.equals(id)) return defaultLayer();
		for (MapLayer layer : custom) {
			if (layer.id().equals(id)) return layer;
		}
		return null;
	}

	public static void put(MapLayer layer) {
		custom.removeIf(other -> other.id().equals(layer.id()));
		custom.add(layer);
		save();
	}

	public static void remove(String id) {
		custom.removeIf(other -> other.id().equals(id));
		RoleplayersAtlas.hiddenLayers.remove(id);
		save();
	}

	private static String currentWorldId(Minecraft client) {
		if (client.isLocalServer() && client.getSingleplayerServer() != null) {
			return "sp:" + client.getSingleplayerServer().getWorldData().getLevelName();
		}
		ServerData entry = client.getCurrentServer();
		return entry != null ? "mp:" + entry.ip : null;
	}

	public static void load(Minecraft client) {
		worldId = currentWorldId(client);
		custom.clear();
		if (worldId == null || !Files.exists(FILE)) return;
		try (Reader reader = Files.newBufferedReader(FILE)) {
			Map<String, List<MapLayer>> all = GSON.fromJson(reader, TYPE);
			List<MapLayer> layers = all == null ? null : all.get(worldId);
			if (layers != null) custom.addAll(layers.stream().filter(l -> l != null && l.id() != null && !DEFAULT_ID.equals(l.id())).toList());
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to load marker layers", e);
		}
	}

	public static void save() {
		if (worldId == null) return;
		try {
			Files.createDirectories(FILE.getParent());
			Map<String, List<MapLayer>> all = new HashMap<>();
			if (Files.exists(FILE)) {
				try (Reader reader = Files.newBufferedReader(FILE)) {
					Map<String, List<MapLayer>> read = GSON.fromJson(reader, TYPE);
					if (read != null) all.putAll(read);
				} catch (Exception ignored) {
				}
			}
			all.put(worldId, List.copyOf(custom));
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(all, TYPE, writer);
			}
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to save marker layers", e);
		}
	}
}
