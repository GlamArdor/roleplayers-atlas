package glam.ardor.roleplayers_atlas;

import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.terrain.RegionSummary;
import folk.sisby.surveyor.util.RegionPos;
import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Map "scrolls": exporting a chosen slice of the atlas (terrain, markers,
 * inscriptions, territories) to a shareable file and importing such files into
 * another player's atlas. Terrain regions ride Surveyor's own region-update
 * packet codec, landmarks their own codec, so the data model stays canonical.
 */
public final class MapShare {
	public record ImportResult(int landmarks, int regions, int corrections, String error) {
	}

	private MapShare() {
	}

	public static Path scrollsDir() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("roleplayers-atlas").resolve("scrolls");
		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) {
		}
		return dir;
	}

	public static List<Path> listScrolls() {
		try (var stream = Files.list(scrollsDir())) {
			return stream.filter(p -> p.getFileName().toString().endsWith(".atlas")).sorted().toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	public static Path export(RegistryKey<World> dim, WorldSummary summary, DynamicRegistryManager manager, List<Landmark> landmarks, boolean includeTerrain, boolean includeCorrections, boolean signed, String name) throws IOException {
		NbtCompound root = new NbtCompound();
		root.putInt("Version", 1);
		root.putString("Dimension", dim.getValue().toString());

		NbtList landmarkList = new NbtList();
		for (Landmark landmark : landmarks) {
			// Signing happens on a copy: the scroll carries the cartographer's
			// name, the marks on the author's own map stay untouched.
			Landmark written = signed ? sign(landmark) : landmark;
			NbtCompound entry = new NbtCompound();
			entry.putString("Id", written.id().toString());
			entry.put("Data", written.toNbt());
			landmarkList.add(entry);
		}
		root.put("Landmarks", landmarkList);

		// Definitions of custom layers used by the exported landmarks, so the
		// importer sees the same names and colors.
		NbtList layerList = new NbtList();
		for (Landmark landmark : landmarks) {
			String layerId = RoleplayersAtlas.layerOf(landmark);
			if (MarkerLayers.DEFAULT_ID.equals(layerId)) continue;
			MarkerLayers.MapLayer layer = MarkerLayers.get(layerId);
			if (layer == null) continue;
			boolean already = false;
			for (NbtElement el : layerList) {
				if (el instanceof NbtCompound c && layerId.equals(c.getString("Id").orElse(null))) already = true;
			}
			if (already) continue;
			NbtCompound layerEntry = new NbtCompound();
			layerEntry.putString("Id", layer.id());
			layerEntry.putString("Name", layer.name());
			layerEntry.putInt("Color", layer.color());
			layerList.add(layerEntry);
		}
		root.put("Layers", layerList);

		if (includeTerrain && summary.terrain() != null) {
			NbtList terrainList = new NbtList();
			Map<RegionPos, BitSet> regions = summary.terrain().bitSet(SurveyorClient.getExploration());
			for (Map.Entry<RegionPos, BitSet> entry : regions.entrySet()) {
				if (entry.getValue().isEmpty()) continue;
				RegionSummary region = summary.terrain().getRegion(entry.getKey());
				if (region == null) continue;
				S2CUpdateRegionPacket packet = S2CUpdateRegionPacket.of(dim, false, entry.getKey(), region, entry.getValue());
				RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), manager);
				S2CUpdateRegionPacket.CODEC.encode(buf, packet);
				byte[] bytes = new byte[buf.readableBytes()];
				buf.readBytes(bytes);
				NbtCompound regionEntry = new NbtCompound();
				regionEntry.putInt("X", entry.getKey().x());
				regionEntry.putInt("Z", entry.getKey().z());
				regionEntry.putByteArray("Data", bytes);
				terrainList.add(regionEntry);
			}
			root.put("Terrain", terrainList);
		}

		// How the sender decided the map should read. Only their own word travels –
		// corrections they inherited from a third scroll stay where they came from,
		// so a sheet never gets passed along under the wrong name.
		if (includeCorrections) {
			NbtList biomeList = new NbtList();
			BiomeOverrides.all().forEach((from, to) -> {
				NbtCompound entry = new NbtCompound();
				entry.putString("From", from.toString());
				entry.putString("To", to.toString());
				biomeList.add(entry);
			});
			NbtList chunkList = new NbtList();
			BiomeOverrides.ownPatches(dim).forEach((pos, look) -> {
				NbtCompound entry = new NbtCompound();
				entry.putInt("X", pos.x);
				entry.putInt("Z", pos.z);
				entry.putString("To", look.toString());
				chunkList.add(entry);
			});
			// Towns drawn by hand travel in the same sheet: they are the same kind
			// of thing – the cartographer's own reading of the land, written over
			// what the game had to say about it.
			NbtList cityList = new NbtList();
			CityPaint.ownCells(dim).forEach((pos, tile) -> {
				NbtCompound entry = new NbtCompound();
				entry.putInt("X", pos.x);
				entry.putInt("Z", pos.z);
				entry.putString("To", tile.toString());
				cityList.add(entry);
			});
			if (!biomeList.isEmpty() || !chunkList.isEmpty() || !cityList.isEmpty()) {
				NbtCompound corrections = new NbtCompound();
				corrections.put("Biomes", biomeList);
				corrections.put("Chunks", chunkList);
				corrections.put("Cities", cityList);
				// Unsigned, the sheet is filed under the scroll's own name instead –
				// it still has to go somewhere it can be torn out of again.
				if (signed) corrections.putString("Author", AtlasTime.selfName());
				root.put("Corrections", corrections);
			}
		}

		String safe = name.isBlank() ? "map" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
		Path file = scrollsDir().resolve(safe + ".atlas");
		try (OutputStream out = Files.newOutputStream(file)) {
			NbtIo.writeCompressed(root, out);
		}
		return file;
	}

	/**
	 * Stamps a landmark with the cartographer's seal for export. A mark that
	 * already names a source keeps it – passing a scroll along shouldn't erase
	 * whose knowledge it was to begin with.
	 */
	private static Landmark sign(Landmark landmark) {
		return WorldAtlasData.copyLandmarkWith(landmark, landmark.id(), copy -> {
			if (landmark.get(AtlasComponents.SOURCE) == null) {
				copy.set(AtlasComponents.SOURCE, AtlasTime.selfName());
			}
			if (landmark.get(AtlasComponents.DAY) == null) copy.set(AtlasComponents.DAY, AtlasTime.gameDay());
			if (landmark.get(AtlasComponents.REAL_TIME) == null) copy.set(AtlasComponents.REAL_TIME, AtlasTime.realMillis());
		});
	}

	/**
	 * The layer a scroll's marks land in, one per cartographer. Its colour is
	 * derived from the name so the same person always reads the same on the
	 * tab strip, kept bright enough to tell apart on parchment.
	 */
	private static MarkerLayers.MapLayer scrollLayer(String author) {
		String id = "scroll_" + author.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
		MarkerLayers.MapLayer existing = MarkerLayers.get(id);
		if (existing != null) return existing;
		int hue = Math.floorMod(author.hashCode(), 360);
		int rgb = java.awt.Color.HSBtoRGB(hue / 360.0F, 0.55F, 0.85F) & 0xFFFFFF;
		MarkerLayers.MapLayer layer = new MarkerLayers.MapLayer(id, Text.translatable("gui.roleplayers_atlas.layer.scroll", author).getString(), rgb);
		MarkerLayers.put(layer);
		return layer;
	}

	/**
	 * What a scroll holds, read without applying any of it.
	 * <p>
	 * Taking a scroll in was blind: you learned what was on it by having it
	 * already written into your own map. This reads the same file and counts,
	 * which is cheap – the marks are parsed anyway, and terrain is only tallied,
	 * not decoded.
	 */
	public record Preview(String dimension, boolean sameDimension, int markers, int labels, int routes, int territories,
	                      int regions, int biomeCorrections, int patches, int towns, List<String> authors) {
	}

	public static @Nullable Preview peek(Path file, RegistryKey<World> dim) {
		NbtCompound root;
		try (InputStream in = Files.newInputStream(file)) {
			root = NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
		} catch (Exception e) {
			return null;
		}
		String dimension = root.getString("Dimension").orElse("");
		int markers = 0, labels = 0, routes = 0, territories = 0;
		java.util.LinkedHashSet<String> authors = new java.util.LinkedHashSet<>();
		if (root.get("Landmarks") instanceof NbtList landmarkList) {
			for (NbtElement el : landmarkList) {
				if (!(el instanceof NbtCompound entry)) continue;
				Identifier id = entry.getString("Id").map(Identifier::tryParse).orElse(null);
				if (id == null) continue;
				NbtElement data = entry.get("Data");
				if (data != null) {
					var parsed = Landmark.createCodec(SurveyorClient.getClientUuid(), id).parse(NbtOps.INSTANCE, data).result();
					if (parsed.isPresent()) {
						String source = parsed.get().get(AtlasComponents.SOURCE);
						if (source != null && !source.isBlank()) authors.add(source);
					}
				}
				// Counted from the id, which carries the kind – no need to build
				// the landmark to know whether it is a road or a zone.
				String path = id.getPath();
				if (path.startsWith("route/")) routes++;
				else if (path.startsWith("label/")) labels++;
				else if (path.startsWith("territory/")) territories++;
				else markers++;
			}
		}
		int regions = root.get("Terrain") instanceof NbtList terrain ? terrain.size() : 0;
		int biomes = 0, patches = 0, towns = 0;
		if (root.get("Corrections") instanceof NbtCompound corrections) {
			if (corrections.get("Biomes") instanceof NbtList list) biomes = list.size();
			if (corrections.get("Chunks") instanceof NbtList list) patches = list.size();
			if (corrections.get("Cities") instanceof NbtList list) towns = list.size();
			corrections.getString("Author").filter(a -> !a.isBlank()).ifPresent(authors::add);
		}
		return new Preview(dimension, dim.getValue().toString().equals(dimension), markers, labels, routes, territories, regions, biomes, patches, towns, List.copyOf(authors));
	}

	public static ImportResult importFile(Path file, RegistryKey<World> dim, WorldSummary summary, DynamicRegistryManager manager) {
		return importFile(file, dim, summary, manager, true);
	}

	public static ImportResult importFile(Path file, RegistryKey<World> dim, WorldSummary summary, DynamicRegistryManager manager, boolean takeCities) {
		NbtCompound root;
		try (InputStream in = Files.newInputStream(file)) {
			root = NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to read map scroll {}", file, e);
			return new ImportResult(0, 0, 0, "read_failed");
		}

		Optional<String> dimId = root.getString("Dimension");
		if (dimId.isEmpty() || !dim.getValue().toString().equals(dimId.get())) return new ImportResult(0, 0, 0, "wrong_dimension");

		// What the map said before the scroll was read, so the whole reading can
		// be taken back as one act. Terrain is the exception and stays: it is
		// merged into what you have explored, and land once known cannot be
		// unknown without throwing away your own walking with it.
		BiomeOverrides.State correctionsBefore = BiomeOverrides.capture();
		CityPaint.State townsBefore = CityPaint.capture();
		List<String> layersBefore = MarkerLayers.all().stream().map(MarkerLayers.MapLayer::id).toList();
		Map<Landmark, Landmark> landmarksTouched = new java.util.LinkedHashMap<>();

		ImportResult result = AtlasUndo.withRecordingOff(() -> readInto(root, file, dim, summary, manager, takeCities, landmarksTouched));

		if (result.error() == null) {
			AtlasUndo.pushReversible(Text.translatable("gui.roleplayers_atlas.undo.import"),
				() -> {
					landmarksTouched.forEach((written, previous) -> {
						if (summary.landmarks() == null) return;
						summary.landmarks().remove(written.owner(), written.id());
						if (previous != null) summary.landmarks().put(previous);
					});
					MarkerLayers.all().stream().map(MarkerLayers.MapLayer::id)
						.filter(id -> !layersBefore.contains(id)).toList()
						.forEach(MarkerLayers::remove);
					BiomeOverrides.restore(correctionsBefore);
					CityPaint.restore(townsBefore);
					WorldAtlasData data = WorldAtlasData.WORLDS.get(dim);
					if (data != null) data.invalidateTileBatches();
					if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof glam.ardor.roleplayers_atlas.gui.AtlasScreen as) as.updateBookmarkerList();
				},
				() -> importFile(file, dim, summary, manager, takeCities));
		}
		return result;
	}

	private static ImportResult readInto(NbtCompound root, Path file, RegistryKey<World> dim, WorldSummary summary, DynamicRegistryManager manager, boolean takeCities, Map<Landmark, Landmark> landmarksTouched) {
		int landmarkCount = 0;
		java.util.Set<String> keptLayers = new java.util.HashSet<>();
		if (root.get("Landmarks") instanceof NbtList landmarkList && summary.landmarks() != null) {
			for (NbtElement el : landmarkList) {
				if (!(el instanceof NbtCompound entry)) continue;
				Identifier id = entry.getString("Id").map(Identifier::tryParse).orElse(null);
				NbtElement data = entry.get("Data");
				if (id == null || data == null) continue;
				var parsed = Landmark.createCodec(SurveyorClient.getClientUuid(), id).parse(NbtOps.INSTANCE, data).result();
				if (parsed.isEmpty()) continue;
				Landmark landmark = parsed.get();
				// Someone else's marks go into a layer of their own rather than
				// mixing into yours: it can be hidden or dropped in one go, and
				// two people's maps stay tellable apart.
				String author = landmark.get(AtlasComponents.SOURCE);
				if (author != null && !author.isEmpty() && !author.equals(AtlasTime.selfName())) {
					landmark = WorldAtlasData.copyLandmarkWith(landmark, landmark.id(), copy -> copy.set(AtlasComponents.LAYER, scrollLayer(author).id()));
				} else {
					keptLayers.add(RoleplayersAtlas.layerOf(landmark));
				}
				// Remembered with whatever it displaced, so taking the scroll back
				// out restores a mark it happened to write over rather than losing it.
				landmarksTouched.put(landmark, summary.landmarks().get(landmark.owner(), landmark.id()));
				summary.landmarks().put(landmark);
				landmarkCount++;
			}
		}

		// Only the scroll's own layer definitions that something still sits in.
		if (root.get("Layers") instanceof NbtList layerList) {
			for (NbtElement el : layerList) {
				if (!(el instanceof NbtCompound entry)) continue;
				String layerId = entry.getString("Id").orElse(null);
				String layerName = entry.getString("Name").orElse(null);
				if (layerId == null || layerName == null || MarkerLayers.DEFAULT_ID.equals(layerId)) continue;
				if (!keptLayers.contains(layerId)) continue;
				if (MarkerLayers.get(layerId) == null) {
					MarkerLayers.put(new MarkerLayers.MapLayer(layerId, layerName, entry.getInt("Color").orElse(0xF9FFFE)));
				}
			}
		}

		int regionCount = 0;
		if (root.get("Terrain") instanceof NbtList terrainList && summary.terrain() != null) {
			for (NbtElement el : terrainList) {
				if (!(el instanceof NbtCompound entry) || !(entry.get("Data") instanceof NbtByteArray dataArray)) continue;
				try {
					RegistryByteBuf buf = new RegistryByteBuf(Unpooled.wrappedBuffer(dataArray.getByteArray()), manager);
					S2CUpdateRegionPacket packet = S2CUpdateRegionPacket.CODEC.decode(buf);
					RegionPos regionPos = packet.regionPos();
					RegionSummary region = summary.terrain().getRegion(regionPos);
					BitSet merged = region.readUpdatePacket(packet);
					SurveyorClient.getPersonalExploration().mergeRegion(dim, regionPos, merged, false);
					SurveyorClient.getPersonalExploration().updateClientForMergeRegion(summary, regionPos, merged);
					regionCount++;
				} catch (Exception e) {
					RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Skipping unreadable terrain region in {}", file, e);
				}
			}
		}

		int correctionCount = importCorrections(root, file, dim, takeCities);

		WorldAtlasData data = WorldAtlasData.WORLDS.get(dim);
		if (data != null) data.invalidateTileBatches();
		return new ImportResult(landmarkCount, regionCount, correctionCount, null);
	}

	/**
	 * Takes in how the sender read the land. Their sheet is filed under their
	 * name and consulted only where the reader has said nothing themselves, so a
	 * scroll can never quietly overrule a correction its reader made – and the
	 * whole sheet can be torn out again in one go.
	 */
	private static int importCorrections(NbtCompound root, Path file, RegistryKey<World> dim, boolean takeCities) {
		if (!(root.get("Corrections") instanceof NbtCompound tag)) return 0;
		Map<Identifier, Identifier> theirBiomes = new java.util.LinkedHashMap<>();
		if (tag.get("Biomes") instanceof NbtList biomeList) {
			for (NbtElement el : biomeList) {
				if (!(el instanceof NbtCompound entry)) continue;
				Identifier from = entry.getString("From").map(Identifier::tryParse).orElse(null);
				Identifier to = entry.getString("To").map(Identifier::tryParse).orElse(null);
				if (from != null && to != null) theirBiomes.put(from, to);
			}
		}
		Map<net.minecraft.util.math.ChunkPos, Identifier> theirCells = readCells(tag.get("Chunks"));
		// Someone else's towns are the one part of a scroll a reader may want to
		// leave on the page: they redraw ground the reader may have their own
		// plans for. Left off, the rest of the sheet still comes in.
		Map<net.minecraft.util.math.ChunkPos, Identifier> theirTowns = takeCities ? readCells(tag.get("Cities")) : Map.of();
		if (theirBiomes.isEmpty() && theirCells.isEmpty() && theirTowns.isEmpty()) return 0;

		String author = tag.getString("Author").filter(a -> !a.isBlank())
			.orElseGet(() -> file.getFileName().toString().replaceFirst("\\.atlas$", ""));
		if (author.equals(AtlasTime.selfName())) {
			// Your own scroll come home: these were your corrections to begin with,
			// so they go back where they were rather than into a sheet of guests.
			BiomeOverrides.setAll(theirBiomes, dim, theirCells);
			// Grouped by piece: each call writes the file and redraws, and a town
			// of two hundred cells is not worth two hundred of either.
			Map<Identifier, java.util.List<net.minecraft.util.math.ChunkPos>> byPiece = new java.util.LinkedHashMap<>();
			theirTowns.forEach((pos, tile) -> byPiece.computeIfAbsent(tile, k -> new java.util.ArrayList<>()).add(pos));
			byPiece.forEach((tile, drawn) -> CityPaint.set(dim, drawn, tile));
		} else {
			BiomeOverrides.putImported(author, theirBiomes, theirCells.isEmpty() ? Map.of() : Map.of(dim.getValue(), theirCells));
			CityPaint.putImported(author, dim, theirTowns);
		}
		return theirBiomes.size() + theirCells.size() + theirTowns.size();
	}

	private static Map<net.minecraft.util.math.ChunkPos, Identifier> readCells(@org.jetbrains.annotations.Nullable NbtElement from) {
		Map<net.minecraft.util.math.ChunkPos, Identifier> out = new java.util.LinkedHashMap<>();
		if (!(from instanceof NbtList list)) return out;
		for (NbtElement el : list) {
			if (!(el instanceof NbtCompound entry)) continue;
			Identifier to = entry.getString("To").map(Identifier::tryParse).orElse(null);
			Integer x = entry.getInt("X").orElse(null);
			Integer z = entry.getInt("Z").orElse(null);
			if (to != null && x != null && z != null) out.put(new net.minecraft.util.math.ChunkPos(x, z), to);
		}
		return out;
	}
}
