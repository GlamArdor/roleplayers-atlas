package glam.ardor.roleplayers_atlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the player would wake up: the hearth mark.
 * <p>
 * The server never tells the client where a player's respawn point is.
 * {@code ServerPlayerEntity#setSpawnPoint} only stores it and prints "Respawn
 * point set", and the spawn packet the vanilla compass follows is built from
 * {@code ServerWorld#getSpawnPos} – the <em>world</em> spawn. So the point has
 * to be inferred, and the inference has to hold for a spawn set by a plugin the
 * client knows nothing about.
 * <p>
 * The one thing that always holds: after dying, a player reappears at their
 * respawn point. That covers every way it could have been set. Beds are caught
 * separately at the moment the player lies down, purely so the mark moves
 * straight away instead of waiting for a death.
 * <p>
 * It is remembered per world, because none of this survives a relog otherwise.
 * <p>
 * The dimension is stored alongside it. The spawn packet carries none, which is
 * why the vanilla compass spins uselessly in the Nether; here the hearth simply
 * isn't shown outside the world it belongs to.
 * <p>
 * Owned by {@link WorldLandmarks#GLOBAL}, which keeps it out of the layers, the
 * bookmark list, the export picker and the delete tool without any of them
 * needing to know it exists.
 */
public final class SpawnMarker {
	public static final Identifier ID = RoleplayersAtlas.id("spawn");

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("roleplayers-atlas").resolve("hearth.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final java.lang.reflect.Type TYPE = new TypeToken<Map<String, String>>() {
	}.getType();

	private static BlockPos pos = null;
	private static RegistryKey<World> dimension = null;
	/** Anything learned from the player themselves outranks the world spawn. */
	private static boolean known = false;
	private static String worldId = null;

	// Coming back from the death screen: the spot the player reappears on IS
	// their respawn point, whatever set it.
	private static boolean wasDead = false;
	private static int captureDelay = -1;

	private SpawnMarker() {
	}

	public static void load(MinecraftClient client) {
		load(glam.ardor.roleplayers_atlas.util.WorldKey.current(client));
	}

	private static void load(String id) {
		worldId = id;
		pos = null;
		dimension = null;
		known = false;
		if (worldId == null || !Files.exists(FILE)) return;
		try (Reader reader = Files.newBufferedReader(FILE)) {
			Map<String, String> all = GSON.fromJson(reader, TYPE);
			String stored = all == null ? null : all.get(worldId);
			if (stored == null) return;
			String[] parts = stored.split(" ");
			if (parts.length != 4) return;
			Identifier dim = Identifier.tryParse(parts[0]);
			if (dim == null) return;
			dimension = RegistryKey.of(RegistryKeys.WORLD, dim);
			pos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
			known = true;
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to load the hearth", e);
		}
	}

	private static void save() {
		if (worldId == null || pos == null || dimension == null) return;
		try {
			Files.createDirectories(FILE.getParent());
			Map<String, String> all = new HashMap<>();
			if (Files.exists(FILE)) {
				try (Reader reader = Files.newBufferedReader(FILE)) {
					Map<String, String> read = GSON.fromJson(reader, TYPE);
					if (read != null) all.putAll(read);
				} catch (Exception ignored) {
				}
			}
			all.put(worldId, "%s %d %d %d".formatted(dimension.getValue(), pos.getX(), pos.getY(), pos.getZ()));
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(all, TYPE, writer);
			}
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to save the hearth", e);
		}
	}

	public static void clear() {
		pos = null;
		dimension = null;
		known = false;
		worldId = null;
	}

	/**
	 * Called every client tick. Both sources run together, and they cover for
	 * each other:
	 * <ol>
	 *   <li>Reappearing after death – wherever the player comes back is their
	 *       respawn point by definition, no matter what set it: a bed, an
	 *       anchor, {@code /spawnpoint}, or a plugin the client knows nothing
	 *       about. Right everywhere, but silent until the first death.</li>
	 *   <li>Lying down in a bed – the same act that sets the respawn in vanilla,
	 *       and the only one that shows without dying first. Servers where
	 *       sleeping leaves the spawn alone make this a lie, which is what
	 *       {@code hearthFollowsBeds} is there to switch off.</li>
	 *   <li>The world spawn – stands in until either of the above happens.</li>
	 * </ol>
	 */
	public static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || client.player == null) return;

		// Checked here rather than on joining, because a proxy can hand you a
		// different world without the connection ever dropping.
		String id = glam.ardor.roleplayers_atlas.util.WorldKey.current(client);
		if (id != null && !id.equals(worldId)) load(id);

		if (client.player.isDead()) {
			wasDead = true;
		} else if (wasDead) {
			wasDead = false;
			// The position lands a tick or two after the respawn itself.
			captureDelay = 3;
		}
		if (captureDelay >= 0 && --captureDelay < 0) {
			record(client.player.getBlockPos(), world.getRegistryKey());
			return;
		}

		if (RoleplayersAtlas.CONFIG.hearthFollowsBeds && client.player.isSleeping()) {
			BlockPos bed = client.player.getSleepingPosition().orElse(null);
			if (bed != null) record(bed, world.getRegistryKey());
			return;
		}

		if (known) return;
		BlockPos worldSpawn = world.getSpawnPoint().getPos();
		if (worldSpawn == null || worldSpawn.equals(pos)) return;
		pos = worldSpawn;
		dimension = world.getRegistryKey();
	}

	private static void record(BlockPos at, RegistryKey<World> dim) {
		if (known && at.equals(pos) && dim.equals(dimension)) return;
		pos = at;
		dimension = dim;
		known = true;
		save();
	}

	public static BlockPos pos(RegistryKey<World> dim) {
		if (!RoleplayersAtlas.CONFIG.spawnMarker || pos == null || dimension == null || !dimension.equals(dim)) return null;
		return pos;
	}

	public static boolean is(Landmark landmark) {
		return landmark != null && ID.equals(landmark.id());
	}

	/** The mark itself, rebuilt on demand – it is never stored as a landmark. */
	public static Landmark get(RegistryKey<World> dim) {
		BlockPos at = pos(dim);
		if (at == null) return null;
		int color = colorOf().getEntityColor();
		return Landmark.create(WorldLandmarks.GLOBAL, ID, b -> b
			.add(LandmarkComponentTypes.POS, at)
			.add(LandmarkComponentTypes.COLOR, color)
			.add(LandmarkComponentTypes.NAME, Text.translatable("gui.roleplayers_atlas.spawn.name"))
			.add(LandmarkComponentTypes.LORE, List.of(Text.translatable("gui.roleplayers_atlas.spawn.lore")))
			// No zone title: the hearth announcing itself every time you come
			// home would wear thin fast.
			.add(AtlasComponents.ZONE_TITLE, false)
			.add(AtlasComponents.OPACITY, RoleplayersAtlas.CONFIG.spawnMarkerOpacity));
	}

	public static DyeColor colorOf() {
		for (DyeColor color : DyeColor.values()) {
			if (color.getId().equals(RoleplayersAtlas.CONFIG.spawnMarkerColor)) return color;
		}
		return DyeColor.RED;
	}

	public static Identifier iconId() {
		return RoleplayersAtlas.id("custom/" + RoleplayersAtlas.CONFIG.spawnMarkerIcon);
	}

	public static MarkerTexture texture() {
		MarkerTexture found = glam.ardor.roleplayers_atlas.reloader.MarkerTextures.getInstance().asMap().get(iconId());
		if (found != null) return found;
		for (MarkerTexture texture : glam.ardor.roleplayers_atlas.reloader.MarkerTextures.getInstance().asMap().values()) {
			if (texture.keyId().equals(iconId())) return texture;
		}
		return MarkerTexture.DEFAULT;
	}
}
