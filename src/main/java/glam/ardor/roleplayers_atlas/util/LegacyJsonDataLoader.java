package glam.ardor.roleplayers_atlas.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Replicates the behaviour of the pre-1.21.5 gson-based JsonDataLoader:
 * collects every json under the given directory into a raw JsonElement map,
 * leaving the parsing to the subclass.
 */
public abstract class LegacyJsonDataLoader extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {
	private final FileToIdConverter finder;

	protected LegacyJsonDataLoader(String dataType) {
		this.finder = FileToIdConverter.json(dataType);
	}

	@Override
	protected Map<Identifier, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<Identifier, JsonElement> map = new HashMap<>();
		for (Map.Entry<Identifier, Resource> entry : finder.listMatchingResources(manager).entrySet()) {
			Identifier id = finder.fileToId(entry.getKey());
			try (Reader reader = entry.getValue().openAsReader()) {
				map.put(id, JsonParser.parseReader(reader));
			} catch (Exception e) {
				RoleplayersAtlas.LOGGER.error("[Roleplayer's Atlas] Couldn't parse data file {} from {}", id, entry.getKey(), e);
			}
		}
		return map;
	}
}
