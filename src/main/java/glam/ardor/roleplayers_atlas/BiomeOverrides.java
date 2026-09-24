package glam.ardor.roleplayers_atlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import glam.ardor.roleplayers_atlas.util.WorldKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The player's corrections to how the map is drawn.
 * <p>
 * Two kinds, and the finer one wins. A <em>biome</em> correction says "draw
 * this biome as that one" and mends the whole world at once – the atlas guesses
 * at biomes it has no picture for, and only the person looking at the map can
 * say when a guess is wrong. A <em>patch</em> corrects named chunks and nothing
 * else, for the places a biome can't describe: a built city, a burnt field, a
 * castle that ought to read as mountains.
 * <p>
 * Corrections that arrive on someone else's scroll are kept in a sheet of their
 * own, one per cartographer, exactly as their marks go to a layer under their
 * name. Your own word always outranks theirs, and a whole sheet can be torn out
 * again without disturbing a single correction you made yourself.
 * <p>
 * Neither touches the drawn map. Tiles are worked out afresh every time and
 * these are consulted on the way, so lifting a correction brings back whatever
 * was there before and can't damage anything explored.
 * <p>
 * Kept per world, because a correction that suits one server's datapack means
 * nothing on another's, and patches are places – they belong to one map only.
 */
public final class BiomeOverrides {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("roleplayers-atlas").resolve("biomes.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** worldId -> {"biomes": {biome: look}, "chunks": {dimension: {"x,z": look}}, "shared": {author: same again}} */
	private static final java.lang.reflect.Type TYPE = new TypeToken<Map<String, Stored>>() {
	}.getType();

	private static class Stored {
		Map<String, String> biomes;
		Map<String, Map<String, String>> chunks;
		Map<String, Stored> shared;
	}

	/** One cartographer's corrections, as they came off their scroll. */
	public static final class Sheet {
		final Map<Identifier, Identifier> biomes = new HashMap<>();
		final Map<Identifier, Map<ChunkPos, Identifier>> patches = new HashMap<>();

		public int biomeCount() {
			return biomes.size();
		}

		public int patchCount() {
			return patches.values().stream().mapToInt(Map::size).sum();
		}

		boolean isEmpty() {
			return biomes.isEmpty() && patches.values().stream().allMatch(Map::isEmpty);
		}
	}

	/** Biome id to the biome whose look it borrows. */
	private static final Map<Identifier, Identifier> biomes = new HashMap<>();
	/** Dimension to the chunks patched in it, and what each is drawn as. */
	private static final Map<Identifier, Map<ChunkPos, Identifier>> patches = new HashMap<>();
	/** Author to what their scroll had to say. Insertion order is reading order. */
	private static final Map<String, Sheet> shared = new LinkedHashMap<>();
	private static String worldId = null;

	private BiomeOverrides() {
	}

	/** Reloads for the given world if it isn't the one already loaded. */
	public static void bind(String id) {
		if (id == null || id.equals(worldId)) return;
		worldId = id;
		biomes.clear();
		patches.clear();
		shared.clear();
		if (!Files.exists(FILE)) return;
		try (Reader reader = Files.newBufferedReader(FILE)) {
			Map<String, Stored> all = GSON.fromJson(reader, TYPE);
			Stored mine = all == null ? null : all.get(worldId);
			if (mine == null) return;
			readInto(mine, biomes, patches);
			if (mine.shared != null) mine.shared.forEach((author, theirs) -> {
				if (author == null || theirs == null) return;
				Sheet sheet = new Sheet();
				readInto(theirs, sheet.biomes, sheet.patches);
				if (!sheet.isEmpty()) shared.put(author, sheet);
			});
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to load map corrections", e);
		}
	}

	private static void readInto(Stored from, Map<Identifier, Identifier> intoBiomes, Map<Identifier, Map<ChunkPos, Identifier>> intoPatches) {
		if (from.biomes != null) from.biomes.forEach((biome, to) -> {
			Identifier key = Identifier.tryParse(biome);
			Identifier look = Identifier.tryParse(to);
			if (key != null && look != null) intoBiomes.put(key, look);
		});
		if (from.chunks != null) from.chunks.forEach((dim, cells) -> {
			Identifier dimension = Identifier.tryParse(dim);
			if (dimension == null || cells == null) return;
			Map<ChunkPos, Identifier> into = intoPatches.computeIfAbsent(dimension, k -> new HashMap<>());
			cells.forEach((cell, to) -> {
				ChunkPos pos = parseChunk(cell);
				Identifier look = Identifier.tryParse(to);
				if (pos != null && look != null) into.put(pos, look);
			});
		});
	}

	private static @Nullable ChunkPos parseChunk(String cell) {
		int comma = cell.indexOf(',');
		if (comma < 0) return null;
		try {
			return new ChunkPos(Integer.parseInt(cell.substring(0, comma)), Integer.parseInt(cell.substring(comma + 1)));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static void clear() {
		worldId = null;
		biomes.clear();
		patches.clear();
		shared.clear();
	}

	// --- corrections by biome ---

	public static @Nullable Identifier get(Identifier biome) {
		Identifier own = biomes.get(biome);
		if (own != null) return own;
		for (Sheet sheet : shared.values()) {
			Identifier theirs = sheet.biomes.get(biome);
			if (theirs != null) return theirs;
		}
		return null;
	}

	/** True when this biome is only corrected by someone else's scroll. */
	public static boolean isImported(Identifier biome) {
		if (biomes.containsKey(biome)) return false;
		for (Sheet sheet : shared.values()) {
			if (sheet.biomes.containsKey(biome)) return true;
		}
		return false;
	}

	/** Corrections you made yourself. */
	public static Map<Identifier, Identifier> all() {
		return java.util.Collections.unmodifiableMap(biomes);
	}

	/** Every biome corrected by a scroll rather than by you. */
	public static Set<Identifier> importedBiomes() {
		Set<Identifier> out = new LinkedHashSet<>();
		for (Sheet sheet : shared.values()) out.addAll(sheet.biomes.keySet());
		out.removeAll(biomes.keySet());
		return out;
	}

	/** Draw {@code biome} as {@code look}, or drop the correction if null. */
	public static void set(Identifier biome, @Nullable Identifier look) {
		Identifier before = biomes.get(biome);
		if (look == null) {
			if (biomes.remove(biome) == null) return;
		} else {
			if (look.equals(before)) return;
			biomes.put(biome, look);
		}
		AtlasUndo.push(Text.translatable("gui.roleplayers_atlas.undo.biome"), () -> set(biome, before));
		saveAndRedraw();
	}

	// --- corrections by chunk ---

	public static @Nullable Identifier patch(RegistryKey<World> dimension, ChunkPos pos) {
		Map<ChunkPos, Identifier> cells = patches.get(dimension.getValue());
		Identifier own = cells == null ? null : cells.get(pos);
		if (own != null) return own;
		for (Sheet sheet : shared.values()) {
			Map<ChunkPos, Identifier> theirs = sheet.patches.get(dimension.getValue());
			Identifier look = theirs == null ? null : theirs.get(pos);
			if (look != null) return look;
		}
		return null;
	}

	/** Paints the given chunks, or lifts their patches if the look is null. */
	public static void setPatches(RegistryKey<World> dimension, Collection<ChunkPos> cells, @Nullable Identifier look) {
		if (cells.isEmpty()) return;
		Map<ChunkPos, Identifier> target = new HashMap<>();
		cells.forEach(pos -> target.put(pos, look));
		applyCells(dimension, target, Text.translatable("gui.roleplayers_atlas.undo.patch", cells.size()));
	}

	/**
	 * The one way patches ever change. Every caller hands it what the named cells
	 * should say – a null meaning "nothing" – and it files the exact opposite
	 * before doing it. That symmetry is what makes taking a step back and going
	 * forward again the same machinery rather than two.
	 */
	private static void applyCells(RegistryKey<World> dimension, Map<ChunkPos, Identifier> target, Text description) {
		if (target.isEmpty()) return;
		Map<ChunkPos, Identifier> existing = patches.get(dimension.getValue());
		Map<ChunkPos, Identifier> before = new HashMap<>();
		target.keySet().forEach(pos -> before.put(pos, existing == null ? null : existing.get(pos)));
		Map<ChunkPos, Identifier> into = patches.computeIfAbsent(dimension.getValue(), k -> new HashMap<>());
		target.forEach((pos, look) -> {
			if (look == null) into.remove(pos);
			else into.put(pos, look);
		});
		if (into.isEmpty()) patches.remove(dimension.getValue());
		AtlasUndo.push(description, () -> applyCells(dimension, before, description));
		saveAndRedraw();
	}

	/**
	 * Takes a whole scroll's worth of your own corrections back at once. One
	 * write and one redraw for the lot – applied one at a time, a scroll with
	 * fifty corrections on it would redraw the map fifty times.
	 */
	public static void setAll(Map<Identifier, Identifier> newBiomes, RegistryKey<World> dimension, Map<ChunkPos, Identifier> newCells) {
		Map<Identifier, Identifier> mergedBiomes = new HashMap<>(biomes);
		if (newBiomes != null) mergedBiomes.putAll(newBiomes);
		Map<Identifier, Map<ChunkPos, Identifier>> mergedPatches = new HashMap<>();
		patches.forEach((dim, cells) -> mergedPatches.put(dim, new HashMap<>(cells)));
		if (newCells != null && !newCells.isEmpty()) mergedPatches.computeIfAbsent(dimension.getValue(), k -> new HashMap<>()).putAll(newCells);
		if (mergedBiomes.equals(biomes) && mergedPatches.equals(patches)) return;
		applyAll(mergedBiomes, mergedPatches, shared, Text.translatable("gui.roleplayers_atlas.undo.import"));
	}

	public static int patchCount() {
		return patches.values().stream().mapToInt(Map::size).sum();
	}

	/** Everywhere you painted in this dimension, so the map can show it back. */
	public static Set<ChunkPos> patchedChunks(RegistryKey<World> dimension) {
		Map<ChunkPos, Identifier> cells = patches.get(dimension.getValue());
		return cells == null ? Set.of() : java.util.Collections.unmodifiableSet(cells.keySet());
	}

	/** Everywhere a scroll painted here that you didn't paint yourself. */
	public static Set<ChunkPos> importedChunks(RegistryKey<World> dimension) {
		if (shared.isEmpty()) return Set.of();
		Set<ChunkPos> out = new LinkedHashSet<>();
		for (Sheet sheet : shared.values()) {
			Map<ChunkPos, Identifier> cells = sheet.patches.get(dimension.getValue());
			if (cells != null) out.addAll(cells.keySet());
		}
		Map<ChunkPos, Identifier> mine = patches.get(dimension.getValue());
		if (mine != null) out.removeAll(mine.keySet());
		return out;
	}

	/** Your own patches in one dimension, for writing onto a scroll. */
	public static Map<ChunkPos, Identifier> ownPatches(RegistryKey<World> dimension) {
		Map<ChunkPos, Identifier> cells = patches.get(dimension.getValue());
		return cells == null ? Map.of() : java.util.Collections.unmodifiableMap(cells);
	}

	/**
	 * What you painted here, gathered by the look you painted it as, with how
	 * many cells each covers. This is the only readable shape a pile of loose
	 * chunk corrections has: nobody remembers coordinates, but everyone
	 * remembers painting a stretch of coast as swamp.
	 */
	public static Map<Identifier, Integer> ownPatchLooks(RegistryKey<World> dimension) {
		Map<ChunkPos, Identifier> cells = patches.get(dimension.getValue());
		if (cells == null || cells.isEmpty()) return Map.of();
		Map<Identifier, Integer> out = new LinkedHashMap<>();
		cells.values().forEach(look -> out.merge(look, 1, Integer::sum));
		return out;
	}

	/** Lifts every cell you painted as one particular look. */
	public static void clearPatchesOf(RegistryKey<World> dimension, Identifier look) {
		Map<ChunkPos, Identifier> cells = patches.get(dimension.getValue());
		if (cells == null) return;
		Map<ChunkPos, Identifier> target = new HashMap<>();
		cells.forEach((pos, was) -> {
			if (look.equals(was)) target.put(pos, null);
		});
		applyCells(dimension, target, Text.translatable("gui.roleplayers_atlas.undo.patch", target.size()));
	}

	/** Lifts every patch you made in one dimension, leaving scrolls and biome corrections alone. */
	public static void clearPatches(RegistryKey<World> dimension) {
		Map<ChunkPos, Identifier> cells = patches.get(dimension.getValue());
		if (cells == null) return;
		Map<ChunkPos, Identifier> target = new HashMap<>();
		cells.keySet().forEach(pos -> target.put(pos, null));
		applyCells(dimension, target, Text.translatable("gui.roleplayers_atlas.undo.patch", target.size()));
	}

	// --- what came in on other people's scrolls ---

	/** Cartographers whose corrections this atlas is carrying, in the order they arrived. */
	public static List<String> importedAuthors() {
		return List.copyOf(shared.keySet());
	}

	public static @Nullable Sheet importedSheet(String author) {
		return shared.get(author);
	}

	public static boolean hasImported() {
		return !shared.isEmpty();
	}

	/**
	 * Files a scroll's corrections under its author. Arriving twice from the same
	 * person overwrites what they said before rather than piling up: a scroll is
	 * a statement of how that map looks now, not an addition to an older one.
	 */
	public static void putImported(String author, Map<Identifier, Identifier> theirBiomes, Map<Identifier, Map<ChunkPos, Identifier>> theirPatches) {
		if (author == null || author.isBlank()) return;
		Sheet sheet = new Sheet();
		Sheet existing = shared.get(author);
		if (existing != null) {
			sheet.biomes.putAll(existing.biomes);
			existing.patches.forEach((dim, cells) -> sheet.patches.computeIfAbsent(dim, k -> new HashMap<>()).putAll(cells));
		}
		if (theirBiomes != null) sheet.biomes.putAll(theirBiomes);
		if (theirPatches != null) theirPatches.forEach((dim, cells) -> sheet.patches.computeIfAbsent(dim, k -> new HashMap<>()).putAll(cells));
		if (sheet.isEmpty()) return;
		Map<String, Sheet> target = new LinkedHashMap<>(shared);
		target.put(author, sheet);
		applySheets(target, Text.translatable("gui.roleplayers_atlas.undo.sheet", author));
	}

	/** Tears out one cartographer's sheet whole. Yours are not touched. */
	public static void dropImported(String author) {
		if (!shared.containsKey(author)) return;
		Map<String, Sheet> target = new LinkedHashMap<>(shared);
		target.remove(author);
		applySheets(target, Text.translatable("gui.roleplayers_atlas.undo.sheet", author));
	}

	/** Tears out everyone else's sheets at once. */
	public static void dropAllImported() {
		if (shared.isEmpty()) return;
		applySheets(Map.of(), Text.translatable("gui.roleplayers_atlas.undo.sheetsAll", shared.size()));
	}

	/** The one way the guest sheets ever change, filing the state they replace. */
	private static void applySheets(Map<String, Sheet> target, Text description) {
		Map<String, Sheet> copied = new LinkedHashMap<>(target);
		Map<String, Sheet> before = new LinkedHashMap<>(shared);
		shared.clear();
		shared.putAll(copied);
		AtlasUndo.push(description, () -> applySheets(before, description));
		saveAndRedraw();
	}

	// --- both ---

	public static boolean isEmpty() {
		return biomes.isEmpty() && patches.isEmpty() && shared.isEmpty();
	}

	/** Everything this holds, copied, so a whole import can be put back in one go. */
	public record State(Map<Identifier, Identifier> biomes, Map<Identifier, Map<ChunkPos, Identifier>> patches, Map<String, Sheet> shared) {
	}

	public static State capture() {
		Map<Identifier, Map<ChunkPos, Identifier>> copiedPatches = new HashMap<>();
		patches.forEach((dim, cells) -> copiedPatches.put(dim, new HashMap<>(cells)));
		return new State(new HashMap<>(biomes), copiedPatches, new LinkedHashMap<>(shared));
	}

	public static void restore(State state) {
		applyAll(state.biomes(), state.patches(), state.shared(), Text.translatable("gui.roleplayers_atlas.undo.import"));
	}

	/** Nothing of your own left to write down. */
	private static boolean ownIsEmpty() {
		return biomes.isEmpty() && patches.isEmpty();
	}

	public static void resetAll() {
		if (isEmpty()) return;
		applyAll(Map.of(), Map.of(), Map.of(), Text.translatable("gui.roleplayers_atlas.undo.resetAll"));
	}

	/** Replaces the lot at once, filing what it replaced. */
	private static void applyAll(Map<Identifier, Identifier> newBiomes, Map<Identifier, Map<ChunkPos, Identifier>> newPatches, Map<String, Sheet> newShared, Text description) {
		// Copied first: a caller may well have handed us one of the live maps, and
		// clearing it before reading it would empty what it was asked to keep.
		newBiomes = new HashMap<>(newBiomes);
		Map<Identifier, Map<ChunkPos, Identifier>> copiedPatches = new HashMap<>();
		newPatches.forEach((dim, cells) -> copiedPatches.put(dim, new HashMap<>(cells)));
		newPatches = copiedPatches;
		newShared = new LinkedHashMap<>(newShared);
		Map<Identifier, Identifier> wasBiomes = new HashMap<>(biomes);
		Map<Identifier, Map<ChunkPos, Identifier>> wasPatches = new HashMap<>();
		patches.forEach((dim, cells) -> wasPatches.put(dim, new HashMap<>(cells)));
		Map<String, Sheet> wasShared = new LinkedHashMap<>(shared);
		biomes.clear();
		biomes.putAll(newBiomes);
		patches.clear();
		patches.putAll(newPatches);
		shared.clear();
		shared.putAll(newShared);
		AtlasUndo.push(description, () -> applyAll(wasBiomes, wasPatches, wasShared, description));
		saveAndRedraw();
	}

	private static void saveAndRedraw() {
		save();
		WorldAtlasData.retileAll();
	}

	private static Stored write(Map<Identifier, Identifier> fromBiomes, Map<Identifier, Map<ChunkPos, Identifier>> fromPatches) {
		Stored out = new Stored();
		if (!fromBiomes.isEmpty()) {
			out.biomes = new LinkedHashMap<>();
			fromBiomes.forEach((from, to) -> out.biomes.put(from.toString(), to.toString()));
		}
		if (!fromPatches.isEmpty()) {
			out.chunks = new LinkedHashMap<>();
			fromPatches.forEach((dimension, cells) -> {
				if (cells.isEmpty()) return;
				Map<String, String> written = new LinkedHashMap<>();
				cells.forEach((pos, look) -> written.put(pos.x + "," + pos.z, look.toString()));
				out.chunks.put(dimension.toString(), written);
			});
		}
		return out;
	}

	private static void save() {
		if (worldId == null) worldId = WorldKey.current();
		if (worldId == null) return;
		try {
			Files.createDirectories(FILE.getParent());
			Map<String, Stored> all = new LinkedHashMap<>();
			if (Files.exists(FILE)) {
				try (Reader reader = Files.newBufferedReader(FILE)) {
					Map<String, Stored> read = GSON.fromJson(reader, TYPE);
					if (read != null) all.putAll(read);
				} catch (Exception ignored) {
				}
			}
			if (isEmpty()) {
				all.remove(worldId);
			} else {
				Stored mine = ownIsEmpty() ? new Stored() : write(biomes, patches);
				if (!shared.isEmpty()) {
					mine.shared = new LinkedHashMap<>();
					shared.forEach((author, sheet) -> mine.shared.put(author, write(sheet.biomes, sheet.patches)));
				}
				all.put(worldId, mine);
			}
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(all, TYPE, writer);
			}
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to save map corrections", e);
		}
	}
}
