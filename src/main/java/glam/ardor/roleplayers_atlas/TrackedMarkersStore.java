package glam.ardor.roleplayers_atlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists {@link RoleplayersAtlas#trackedMarkers} per world/server in a small
 * config-side JSON file, so guide arrows survive relogging.
 */
public final class TrackedMarkersStore {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("roleplayers-atlas").resolve("tracked_markers.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final java.lang.reflect.Type TYPE = new TypeToken<Map<String, List<String>>>() {
	}.getType();

	private static String worldId = null;

	private TrackedMarkersStore() {
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
		RoleplayersAtlas.trackedMarkers.clear();
		if (worldId == null || !Files.exists(FILE)) return;
		try (Reader reader = Files.newBufferedReader(FILE)) {
			Map<String, List<String>> all = GSON.fromJson(reader, TYPE);
			List<String> keys = all == null ? null : all.get(worldId);
			if (keys != null) RoleplayersAtlas.trackedMarkers.addAll(keys);
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to load tracked markers", e);
		}
	}

	public static void save() {
		if (worldId == null) return;
		try {
			Files.createDirectories(FILE.getParent());
			Map<String, List<String>> all = new HashMap<>();
			if (Files.exists(FILE)) {
				try (Reader reader = Files.newBufferedReader(FILE)) {
					Map<String, List<String>> read = GSON.fromJson(reader, TYPE);
					if (read != null) all.putAll(read);
				} catch (Exception ignored) {
				}
			}
			all.put(worldId, List.copyOf(RoleplayersAtlas.trackedMarkers));
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(all, TYPE, writer);
			}
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to save tracked markers", e);
		}
	}
}
