package glam.ardor.roleplayers_atlas;

import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import glam.ardor.roleplayers_atlas.reloader.MarkerTextures;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bringing marks over from Xaero's minimap.
 *
 * <p>Nobody wants to place the same fifty waypoints twice, and an atlas that
 * starts empty is an atlas nobody switches to. Xaero keeps its waypoints in
 * plain text — one line each, the format written at the top of every file — so
 * they can simply be read and copied in.
 *
 * <p>Only the waypoints. The terrain Xaero has drawn lives in a binary format
 * of its own that carries neither structures nor the survey the atlas draws
 * from, so the land itself still has to be walked.
 */
public final class XaeroImport {
	/** The folder Xaero's minimap keeps its waypoints in, beside the game's own. */
	private static final String FOLDER = "XaeroWaypoints";
	/** Xaero's own set, which every waypoint belongs to unless it was filed elsewhere. */
	private static final String DEFAULT_SET = "gui.xaero_default";

	private XaeroImport() {
	}

	/** One line of a waypoints file. */
	public record Waypoint(String name, String initials, int x, int y, int z, int colour, boolean disabled, int type, String set) {
		/** Xaero marks a death with type 1, and the one before it with type 2. */
		public boolean isDeath() {
			return type == 1 || type == 2;
		}

		/** What to call it on the map: Xaero's own names for its death points are keys, not names. */
		public String displayName() {
			if (name.startsWith("gui.xaero_deathpoint")) return Text.translatable("gui.roleplayers_atlas.xaero.deathpoint").getString();
			return name;
		}
	}

	/** One world Xaero has waypoints for, in the dimension the atlas is open on. */
	public record Source(Path file, String worldName, String multiworld, int count, long modified, boolean current) {
		public String label() {
			String world = worldName.replace("Multiplayer_", "");
			return multiworld.isEmpty() ? world : world + " / " + multiworld;
		}
	}

	private static Path root() {
		return FabricLoader.getInstance().getGameDir().resolve(FOLDER);
	}

	/** Whether there is anything at all to offer — the button says nothing when Xaero was never installed. */
	public static boolean present() {
		return Files.isDirectory(root());
	}

	/**
	 * Every set of waypoints Xaero holds for this dimension, the world currently
	 * being played on first and the rest by when they were last written.
	 */
	public static List<Source> sources(RegistryKey<World> dim) {
		List<Source> found = new ArrayList<>();
		Path root = root();
		if (!Files.isDirectory(root)) return found;
		String currentWorld = currentWorldFolder();
		try (var worlds = Files.list(root)) {
			for (Path world : worlds.toList()) {
				if (!Files.isDirectory(world)) continue;
				String worldName = world.getFileName().toString();
				if (worldName.equalsIgnoreCase("backup")) continue;
				try (var dims = Files.list(world)) {
					for (Path dimDir : dims.toList()) {
						if (!Files.isDirectory(dimDir)) continue;
						RegistryKey<World> parsed = dimensionOf(dimDir.getFileName().toString());
						if (parsed == null || !parsed.equals(dim)) continue;
						try (var files = Files.list(dimDir)) {
							for (Path file : files.toList()) {
								String fileName = file.getFileName().toString();
								if (!fileName.endsWith(".txt") || fileName.equals("config.txt")) continue;
								int count = read(file).size();
								if (count == 0) continue;
								found.add(new Source(file, worldName, multiworldOf(fileName), count,
									Files.getLastModifiedTime(file).toMillis(),
									worldName.equalsIgnoreCase(currentWorld)));
							}
						}
					}
				}
			}
		} catch (IOException e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Couldn't read Xaero's waypoints", e);
		}
		found.sort(Comparator.comparing(Source::current).reversed().thenComparing(Comparator.comparingLong(Source::modified).reversed()));
		return found;
	}

	/**
	 * The folder Xaero would be using right now. On a server it is the address
	 * it was joined by; in a singleplayer world it is the world's own folder
	 * name, with the brackets Xaero cannot put in a path spelled out.
	 */
	private static String currentWorldFolder() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getCurrentServerEntry() != null) return "Multiplayer_" + client.getCurrentServerEntry().address;
		if (client.getServer() != null) {
			return client.getServer().getSaveProperties().getLevelName().replace("[", "%lb%").replace("]", "%rb%");
		}
		return "";
	}

	/** {@code dim%0} and its kin; anything stranger is read as an identifier. */
	private static RegistryKey<World> dimensionOf(String folder) {
		if (!folder.startsWith("dim%")) return null;
		String id = folder.substring("dim%".length());
		return switch (id) {
			case "0" -> World.OVERWORLD;
			case "-1" -> World.NETHER;
			case "1" -> World.END;
			default -> {
				Identifier parsed = Identifier.tryParse(id.replace('$', ':'));
				yield parsed == null ? null : RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, parsed);
			}
		};
	}

	/** {@code mw$default_1.txt} and {@code mw-2,1,1_1.txt} name Xaero's multiworlds. */
	private static String multiworldOf(String fileName) {
		String name = fileName.substring(0, fileName.length() - ".txt".length());
		if (name.startsWith("mw")) name = name.substring(2);
		int underscore = name.lastIndexOf('_');
		if (underscore > 0) name = name.substring(0, underscore);
		if (name.startsWith("$")) name = name.substring(1);
		return name.equals("default") ? "" : name;
	}

	/**
	 * Reads one waypoints file. The format is a colon-separated line per
	 * waypoint, written out at the head of the file by Xaero itself; a name
	 * holding a colon is put back together rather than dropped.
	 */
	public static List<Waypoint> read(Path file) {
		List<Waypoint> waypoints = new ArrayList<>();
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Couldn't read {}", file, e);
			return waypoints;
		}
		for (String line : lines) {
			if (!line.startsWith("waypoint:")) continue;
			String[] parts = line.split(":");
			if (parts.length < 10) continue;
			// Fields after the name are fixed in number, so any extra colons
			// belong to the name and are stitched back into it.
			int extra = parts.length - 14;
			String name = parts[1];
			for (int i = 0; i < extra; i++) name += ":" + parts[2 + i];
			int at = 2 + Math.max(0, extra);
			try {
				waypoints.add(new Waypoint(
					name,
					parts[at],
					Integer.parseInt(parts[at + 1].trim()),
					Integer.parseInt(parts[at + 2].trim()),
					Integer.parseInt(parts[at + 3].trim()),
					Integer.parseInt(parts[at + 4].trim()),
					Boolean.parseBoolean(parts[at + 5].trim()),
					Integer.parseInt(parts[at + 6].trim()),
					parts.length > at + 7 ? parts[at + 7] : DEFAULT_SET
				));
			} catch (NumberFormatException e) {
				// A line Xaero wrote in a shape we don't know is skipped rather
				// than taking the whole file down with it.
				RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Skipping an unreadable Xaero waypoint: {}", line);
			}
		}
		return waypoints;
	}

	/** The sets in a file, in the order they first appear, with how many are in each. */
	public static Map<String, Integer> sets(List<Waypoint> waypoints) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (Waypoint waypoint : waypoints) counts.merge(setName(waypoint.set()), 1, Integer::sum);
		return counts;
	}

	/** Xaero's own default set has a translation key for a name; the player's sets are named by hand. */
	public static String setName(String set) {
		return DEFAULT_SET.equals(set) ? Text.translatable("gui.roleplayers_atlas.xaero.defaultSet").getString() : set;
	}

	/**
	 * Writes the chosen waypoints in as marks of our own.
	 *
	 * <p>Each one keeps its name, its height and its colour, and is filed into a
	 * layer named after the Xaero set it came from when asked for — a set is the
	 * same idea as a layer, and losing that grouping would be losing half of
	 * what was being carried over.
	 */
	public static int importWaypoints(List<Waypoint> chosen, RegistryKey<World> dim, boolean setsAsLayers) {
		if (chosen.isEmpty()) return 0;
		MarkerTexture texture = icon();
		int written = 0;
		for (Waypoint waypoint : chosen) {
			DyeColor colour = dyeOf(waypoint.colour());
			BlockPos at = new BlockPos(waypoint.x(), waypoint.y(), waypoint.z());
			String layer = waypoint.isDeath() ? MarkerLayers.DEATHS_ID : setsAsLayers ? layerFor(waypoint.set(), colour) : MarkerLayers.DEFAULT_ID;
			Identifier id = texture.keyId().withSuffixedPath("/" + colour.getId() + "/" + at.getX() + "/" + at.getZ());
			Text name = Text.literal(waypoint.displayName());
			Landmark mark = Landmark.create(SurveyorClient.getClientUuid(), id, b -> b.add(LandmarkComponentTypes.POS, at));
			String note = waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z();
			Landmark done = WorldAtlasData.copyLandmarkWith(mark, id, copy -> {
				copy.set(LandmarkComponentTypes.COLOR, colour.getEntityColor());
				copy.set(LandmarkComponentTypes.NAME, name);
				copy.set(AtlasComponents.NOTE, note);
				// Brought in by the handful: a walk past a dozen of them should not
				// be a dozen announcements.
				copy.set(AtlasComponents.ZONE_TITLE, false);
				copy.set(AtlasComponents.LAYER, layer);
				copy.set(AtlasComponents.DAY, AtlasTime.gameDay());
				copy.set(AtlasComponents.REAL_TIME, AtlasTime.realMillis());
			});
			WorldAtlasData.swapLandmark(dim, null, done, Text.translatable("gui.roleplayers_atlas.undo.markerAdded", name));
			written++;
		}
		return written;
	}

	/** Finds or makes the layer a Xaero set lands in. */
	private static String layerFor(String set, DyeColor colour) {
		if (DEFAULT_SET.equals(set)) return MarkerLayers.DEFAULT_ID;
		String id = "xaero/" + set.toLowerCase(Locale.ROOT).replace(' ', '_');
		if (MarkerLayers.get(id) == null) {
			MarkerLayers.put(new MarkerLayers.MapLayer(id, set, colour.getFireworkColor()));
		}
		return id;
	}

	/** The icon brought-in marks are drawn with: the same one the quick-mark key uses. */
	private static MarkerTexture icon() {
		Identifier chosen = RoleplayersAtlas.id("custom/" + RoleplayersAtlas.CONFIG.quickMarkIcon);
		MarkerTexture texture = MarkerTextures.getInstance().asMap().get(chosen);
		if (texture != null) return texture;
		for (MarkerTexture other : MarkerTextures.getInstance().asMap().values()) {
			if (other.keyId().getPath().startsWith("custom/")) return other;
		}
		return MarkerTexture.DEFAULT;
	}

	/**
	 * Xaero colours a waypoint with one of the sixteen colours the game writes
	 * chat in; the atlas colours a mark with a dye. The nearest dye to what was
	 * chosen keeps a red waypoint red.
	 */
	public static DyeColor dyeOf(int index) {
		Formatting formatting = Formatting.byColorIndex(Math.max(0, Math.min(15, index)));
		Integer rgb = formatting == null ? null : formatting.getColorValue();
		if (rgb == null) return DyeColor.WHITE;
		DyeColor nearest = DyeColor.WHITE;
		int best = Integer.MAX_VALUE;
		for (DyeColor dye : DyeColor.values()) {
			int colour = dye.getFireworkColor();
			int dr = ((rgb >> 16) & 0xFF) - ((colour >> 16) & 0xFF);
			int dg = ((rgb >> 8) & 0xFF) - ((colour >> 8) & 0xFF);
			int db = (rgb & 0xFF) - (colour & 0xFF);
			int distance = dr * dr + dg * dg + db * db;
			if (distance < best) {
				best = distance;
				nearest = dye;
			}
		}
		return nearest;
	}
}
