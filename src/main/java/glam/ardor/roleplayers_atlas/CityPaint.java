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
 * Towns drawn by hand.
 * <p>
 * The atlas already draws a village the moment one is found, because the game
 * tells it where villages are. It has no way of knowing about the town three
 * players built by a river – nothing in the world says "this is a town", and no
 * amount of looking at blocks would say it convincingly. So the cartographer
 * says it instead: a cell at a time, choosing from the same roads, wells,
 * houses and market stalls the atlas draws villages with.
 * <p>
 * This is a layer over the drawn map, not a change to it. Nothing about the
 * land is altered – lift the drawing and whatever the ground actually looks
 * like comes straight back. Kept per world and per dimension, because a town is
 * a place and places belong to one map.
 * <p>
 * Towns off other people's scrolls are filed under their names, exactly as
 * their marks go to a layer of their own, so a whole hand can be wiped away
 * without touching a stroke of your own. See {@link BiomeOverrides}, which
 * keeps biome corrections the same way.
 */
public final class CityPaint {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("roleplayers-atlas").resolve("cities.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** worldId -> {"chunks": {dimension: {"x,z": tile}}, "shared": {author: same again}} */
	private static final java.lang.reflect.Type TYPE = new TypeToken<Map<String, Stored>>() {
	}.getType();

	private static class Stored {
		Map<String, Map<String, String>> chunks;
		Map<String, Stored> shared;
	}

	/** Dimension to the cells drawn in it, and what each is drawn as. */
	private static final Map<Identifier, Map<ChunkPos, Identifier>> cells = new HashMap<>();
	/** Author to what their scroll drew. Insertion order is reading order. */
	private static final Map<String, Map<Identifier, Map<ChunkPos, Identifier>>> shared = new LinkedHashMap<>();
	private static String worldId = null;

	private CityPaint() {
	}

	public static void bind(String id) {
		if (id == null || id.equals(worldId)) return;
		worldId = id;
		cells.clear();
		shared.clear();
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				Map<String, Stored> all = GSON.fromJson(reader, TYPE);
				Stored mine = all == null ? null : all.get(worldId);
				if (mine != null) {
					readInto(mine.chunks, cells);
					if (mine.shared != null) mine.shared.forEach((author, theirs) -> {
						if (author == null || theirs == null) return;
						Map<Identifier, Map<ChunkPos, Identifier>> sheet = new HashMap<>();
						readInto(theirs.chunks, sheet);
						if (!sheet.isEmpty()) shared.put(author, sheet);
					});
				}
			} catch (Exception e) {
				RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to load drawn towns", e);
			}
		}
		WorldAtlasData.refreshAllCities();
	}

	private static void readInto(@Nullable Map<String, Map<String, String>> from, Map<Identifier, Map<ChunkPos, Identifier>> into) {
		if (from == null) return;
		from.forEach((dim, drawn) -> {
			Identifier dimension = Identifier.tryParse(dim);
			if (dimension == null || drawn == null) return;
			Map<ChunkPos, Identifier> target = into.computeIfAbsent(dimension, k -> new HashMap<>());
			drawn.forEach((cell, tile) -> {
				ChunkPos pos = parseChunk(cell);
				Identifier look = Identifier.tryParse(tile);
				if (pos != null && look != null) target.put(pos, look);
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
		cells.clear();
		shared.clear();
		WorldAtlasData.refreshAllCities();
	}

	// --- reading ---

	/** What is drawn on this cell, yours first and someone else's after. */
	public static @Nullable Identifier at(RegistryKey<World> dimension, ChunkPos pos) {
		Map<ChunkPos, Identifier> mine = cells.get(dimension.getValue());
		Identifier own = mine == null ? null : mine.get(pos);
		if (own != null) return own;
		for (Map<Identifier, Map<ChunkPos, Identifier>> sheet : shared.values()) {
			Map<ChunkPos, Identifier> theirs = sheet.get(dimension.getValue());
			Identifier look = theirs == null ? null : theirs.get(pos);
			if (look != null) return look;
		}
		return null;
	}

	/** Everything drawn here by anyone, for the map to put down in one pass. */
	public static Map<ChunkPos, Identifier> allAt(RegistryKey<World> dimension) {
		Map<ChunkPos, Identifier> out = new HashMap<>();
		// Someone else's hand goes down first so your own draws over it.
		for (Map<Identifier, Map<ChunkPos, Identifier>> sheet : shared.values()) {
			Map<ChunkPos, Identifier> theirs = sheet.get(dimension.getValue());
			if (theirs != null) out.putAll(theirs);
		}
		Map<ChunkPos, Identifier> mine = cells.get(dimension.getValue());
		if (mine != null) out.putAll(mine);
		return out;
	}

	public static Map<ChunkPos, Identifier> ownCells(RegistryKey<World> dimension) {
		Map<ChunkPos, Identifier> mine = cells.get(dimension.getValue());
		return mine == null ? Map.of() : java.util.Collections.unmodifiableMap(mine);
	}

	/** Everywhere you drew here, so the map can outline it back at you. */
	public static Set<ChunkPos> ownChunks(RegistryKey<World> dimension) {
		Map<ChunkPos, Identifier> mine = cells.get(dimension.getValue());
		return mine == null ? Set.of() : java.util.Collections.unmodifiableSet(mine.keySet());
	}

	/** Everywhere a scroll drew here that you didn't draw yourself. */
	public static Set<ChunkPos> importedChunks(RegistryKey<World> dimension) {
		if (shared.isEmpty()) return Set.of();
		Set<ChunkPos> out = new LinkedHashSet<>();
		for (Map<Identifier, Map<ChunkPos, Identifier>> sheet : shared.values()) {
			Map<ChunkPos, Identifier> theirs = sheet.get(dimension.getValue());
			if (theirs != null) out.addAll(theirs.keySet());
		}
		Map<ChunkPos, Identifier> mine = cells.get(dimension.getValue());
		if (mine != null) out.removeAll(mine.keySet());
		return out;
	}

	/**
	 * What you drew here, gathered by the piece you drew it with and how many
	 * cells each covers. A pile of loose coordinates can't be read; "twelve
	 * cells of plains road" can.
	 */
	public static Map<Identifier, Integer> ownLooks(RegistryKey<World> dimension) {
		Map<ChunkPos, Identifier> mine = cells.get(dimension.getValue());
		if (mine == null || mine.isEmpty()) return Map.of();
		Map<Identifier, Integer> out = new LinkedHashMap<>();
		mine.values().forEach(look -> out.merge(look, 1, Integer::sum));
		return out;
	}

	public static int ownCount(RegistryKey<World> dimension) {
		Map<ChunkPos, Identifier> mine = cells.get(dimension.getValue());
		return mine == null ? 0 : mine.size();
	}

	// --- drawing ---

	/** Strokes that have landed on the map but not yet been written to disk. */
	private static boolean dirty = false;
	/** What the cells of the stroke in progress said before it touched them. */
	private static final Map<ChunkPos, Identifier> strokeBefore = new LinkedHashMap<>();
	private static @Nullable RegistryKey<World> strokeDim = null;

	/**
	 * One stroke of the brush, shown at once and written down later.
	 * <p>
	 * Drawing is done a cell at a time as the mouse moves, and writing the file
	 * for each of them would put a hundred saves into one drag. So the change
	 * lands on the map immediately and {@link #flush} settles it when the hand
	 * comes off the button.
	 *
	 * @return whether anything actually changed, so a brush passing back over
	 * ground it has already covered is not heard doing it.
	 */
	public static boolean paint(RegistryKey<World> dimension, ChunkPos pos, @Nullable Identifier tile) {
		Map<ChunkPos, Identifier> mine = cells.get(dimension.getValue());
		Identifier was = mine == null ? null : mine.get(pos);
		if (tile == null) {
			if (mine == null || mine.remove(pos) == null) return false;
			if (mine.isEmpty()) cells.remove(dimension.getValue());
		} else {
			if (mine == null) mine = cells.computeIfAbsent(dimension.getValue(), k -> new HashMap<>());
			if (tile.equals(mine.put(pos, tile))) return false;
		}
		// The first state a cell had in this stroke is the one to go back to: a
		// drag that crosses the same cell twice should still undo to before it.
		strokeDim = dimension;
		strokeBefore.putIfAbsent(pos, was);
		dirty = true;
		WorldAtlasData.refreshAllCities();
		return true;
	}

	/**
	 * Writes down whatever the brush has laid down since the last time, and files
	 * the whole stroke away as one thing to take back. A drag is one act as far
	 * as the hand is concerned, so it should be one act to undo.
	 */
	public static void flush() {
		if (!dirty) return;
		dirty = false;
		if (!strokeBefore.isEmpty() && strokeDim != null) {
			RegistryKey<World> dimension = strokeDim;
			Map<ChunkPos, Identifier> before = new LinkedHashMap<>(strokeBefore);
			Text description = Text.translatable("gui.roleplayers_atlas.undo.city", before.size());
			AtlasUndo.push(description, () -> applyCells(dimension, before, description));
		}
		strokeBefore.clear();
		strokeDim = null;
		save();
	}

	/**
	 * The one way drawn towns ever change outside a live stroke. Every caller
	 * hands it what the named cells should say – a null meaning "nothing" – and
	 * it files the exact opposite before doing it, so taking a step back and
	 * going forward again are the same machinery rather than two.
	 */
	private static void applyCells(RegistryKey<World> dimension, Map<ChunkPos, Identifier> target, Text description) {
		if (target.isEmpty()) return;
		Map<ChunkPos, Identifier> existing = cells.get(dimension.getValue());
		Map<ChunkPos, Identifier> before = new LinkedHashMap<>();
		target.keySet().forEach(pos -> before.put(pos, existing == null ? null : existing.get(pos)));
		Map<ChunkPos, Identifier> into = cells.computeIfAbsent(dimension.getValue(), k -> new HashMap<>());
		target.forEach((pos, tile) -> {
			if (tile == null) into.remove(pos);
			else into.put(pos, tile);
		});
		if (into.isEmpty()) cells.remove(dimension.getValue());
		AtlasUndo.push(description, () -> applyCells(dimension, before, description));
		dirty = false;
		saveAndRefresh();
	}

	/** Draws the given cells, or rubs them out if the tile is null. */
	public static void set(RegistryKey<World> dimension, Collection<ChunkPos> drawn, @Nullable Identifier tile) {
		if (drawn.isEmpty()) return;
		Map<ChunkPos, Identifier> target = new LinkedHashMap<>();
		drawn.forEach(pos -> target.put(pos, tile));
		applyCells(dimension, target, Text.translatable("gui.roleplayers_atlas.undo.city", drawn.size()));
	}

	/** Rubs out every cell you drew with one particular piece. */
	public static void clearOf(RegistryKey<World> dimension, Identifier tile) {
		Map<ChunkPos, Identifier> mine = cells.get(dimension.getValue());
		if (mine == null) return;
		Map<ChunkPos, Identifier> target = new LinkedHashMap<>();
		mine.forEach((pos, was) -> {
			if (tile.equals(was)) target.put(pos, null);
		});
		applyCells(dimension, target, Text.translatable("gui.roleplayers_atlas.undo.city", target.size()));
	}

	/** Rubs out everything you drew in one dimension, leaving other people's alone. */
	public static void clearAll(RegistryKey<World> dimension) {
		Map<ChunkPos, Identifier> mine = cells.get(dimension.getValue());
		if (mine == null) return;
		Map<ChunkPos, Identifier> target = new LinkedHashMap<>();
		mine.keySet().forEach(pos -> target.put(pos, null));
		applyCells(dimension, target, Text.translatable("gui.roleplayers_atlas.undo.city", target.size()));
	}

	// --- what came in on other people's scrolls ---

	public static List<String> importedAuthors() {
		return List.copyOf(shared.keySet());
	}

	public static boolean hasImported() {
		return !shared.isEmpty();
	}

	public static int importedCount(String author) {
		Map<Identifier, Map<ChunkPos, Identifier>> sheet = shared.get(author);
		return sheet == null ? 0 : sheet.values().stream().mapToInt(Map::size).sum();
	}

	/**
	 * Files a scroll's town under its author, replacing whatever they sent
	 * before: a scroll says how their map looks now, it isn't an addition to an
	 * older one.
	 */
	public static void putImported(String author, RegistryKey<World> dimension, Map<ChunkPos, Identifier> theirs) {
		if (author == null || author.isBlank() || theirs == null || theirs.isEmpty()) return;
		Map<String, Map<Identifier, Map<ChunkPos, Identifier>>> target = copyOf(shared);
		target.computeIfAbsent(author, k -> new HashMap<>()).computeIfAbsent(dimension.getValue(), k -> new HashMap<>()).putAll(theirs);
		applySheets(target, Text.translatable("gui.roleplayers_atlas.undo.sheet", author));
	}

	public static void dropImported(String author) {
		if (!shared.containsKey(author)) return;
		Map<String, Map<Identifier, Map<ChunkPos, Identifier>>> target = copyOf(shared);
		target.remove(author);
		applySheets(target, Text.translatable("gui.roleplayers_atlas.undo.sheet", author));
	}

	public static void dropAllImported() {
		if (shared.isEmpty()) return;
		applySheets(Map.of(), Text.translatable("gui.roleplayers_atlas.undo.sheetsAll", shared.size()));
	}

	/** The one way the guest sheets ever change, filing the state they replace. */
	private static void applySheets(Map<String, Map<Identifier, Map<ChunkPos, Identifier>>> target, Text description) {
		Map<String, Map<Identifier, Map<ChunkPos, Identifier>>> copied = copyOf(target);
		Map<String, Map<Identifier, Map<ChunkPos, Identifier>>> before = copyOf(shared);
		shared.clear();
		shared.putAll(copied);
		AtlasUndo.push(description, () -> applySheets(before, description));
		saveAndRefresh();
	}

	/** A deep enough copy that writing to one doesn't reach into the other. */
	private static Map<String, Map<Identifier, Map<ChunkPos, Identifier>>> copyOf(Map<String, Map<Identifier, Map<ChunkPos, Identifier>>> from) {
		Map<String, Map<Identifier, Map<ChunkPos, Identifier>>> out = new LinkedHashMap<>();
		from.forEach((author, sheet) -> {
			Map<Identifier, Map<ChunkPos, Identifier>> dims = new HashMap<>();
			sheet.forEach((dim, drawn) -> dims.put(dim, new HashMap<>(drawn)));
			out.put(author, dims);
		});
		return out;
	}

	// --- both ---

	public static boolean isEmpty() {
		return cells.isEmpty() && shared.isEmpty();
	}

	/** Everything this holds, copied, so a whole import can be put back in one go. */
	public record State(Map<Identifier, Map<ChunkPos, Identifier>> own, Map<String, Map<Identifier, Map<ChunkPos, Identifier>>> shared) {
	}

	public static State capture() {
		Map<Identifier, Map<ChunkPos, Identifier>> copiedOwn = new HashMap<>();
		cells.forEach((dim, drawn) -> copiedOwn.put(dim, new HashMap<>(drawn)));
		return new State(copiedOwn, copyOf(shared));
	}

	public static void restore(State state) {
		Map<Identifier, Map<ChunkPos, Identifier>> copiedOwn = new HashMap<>();
		state.own().forEach((dim, drawn) -> copiedOwn.put(dim, new HashMap<>(drawn)));
		cells.clear();
		cells.putAll(copiedOwn);
		shared.clear();
		shared.putAll(copyOf(state.shared()));
		dirty = false;
		saveAndRefresh();
	}

	private static void saveAndRefresh() {
		save();
		WorldAtlasData.refreshAllCities();
	}

	private static Stored write(Map<Identifier, Map<ChunkPos, Identifier>> from) {
		Stored out = new Stored();
		if (!from.isEmpty()) {
			out.chunks = new LinkedHashMap<>();
			from.forEach((dimension, drawn) -> {
				if (drawn.isEmpty()) return;
				Map<String, String> written = new LinkedHashMap<>();
				drawn.forEach((pos, tile) -> written.put(pos.x + "," + pos.z, tile.toString()));
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
				Stored mine = write(cells);
				if (!shared.isEmpty()) {
					mine.shared = new LinkedHashMap<>();
					shared.forEach((author, sheet) -> mine.shared.put(author, write(sheet)));
				}
				all.put(worldId, mine);
			}
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(all, TYPE, writer);
			}
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to save drawn towns", e);
		}
	}
}
