package glam.ardor.roleplayers_atlas.reloader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.MarkerTexture;
import glam.ardor.roleplayers_atlas.util.CodecUtil;
import folk.sisby.surveyor.landmark.Landmark;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MarkerTextures extends SimplePreparableReloadListener<Map<Identifier, MarkerTextures.MarkerTextureMeta>> implements IdentifiableResourceReloadListener {
	public static final MarkerTextures INSTANCE = new MarkerTextures();
	public static final Identifier ID = RoleplayersAtlas.id("marker_textures");

	public static MarkerTextures getInstance() {
		return INSTANCE;
	}

	protected final Map<Identifier, MarkerTexture> textures = new HashMap<>();

	public MarkerTexture get(Identifier id) {
		return textures.get(id);
	}

	public MarkerTexture getOrDefault(Identifier id) {
		return getOrDefault(id, MarkerTexture.DEFAULT);
	}

	public MarkerTexture getOrDefault(Identifier id, MarkerTexture defaultTexture) {
		return textures.getOrDefault(id, defaultTexture);
	}

	public Identifier minimumId(Identifier id) {
		while (get(id) == null && id.getPath().contains("/")) {
			id = id.withPath(id.getPath().substring(0, id.getPath().lastIndexOf('/')));
		}
		return get(id) == null ? id.withPath("default") : id;
	}

	public MarkerTexture fromLandmark(Landmark landmark) {
		return getOrDefault(minimumId(landmark.id()));
	}

	public MarkerTexture fromLandmark(Landmark landmark, String variant) {
		Identifier id = minimumId(landmark.id());
		return getOrDefault(id.withPath(p -> p + "/" + variant), getOrDefault(id));
	}

	public Map<Identifier, MarkerTexture> asMap() {
		return new HashMap<>(textures);
	}

	@Override
	protected Map<Identifier, MarkerTextureMeta> prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<Identifier, MarkerTextures.MarkerTextureMeta> textureMeta = new HashMap<>();
		for (Map.Entry<Identifier, Resource> e : manager.listResources("textures/atlas/marker", id -> id.getPath().endsWith(".png")).entrySet()) {
			Identifier id = Identifier.fromNamespaceAndPath(e.getKey().getNamespace(), e.getKey().getPath().substring("textures/atlas/marker/".length(), e.getKey().getPath().length() - ".png".length()));
			try {
				ResourceMetadata metadata = e.getValue().metadata();
				textureMeta.put(id, metadata.getSection(MarkerTextures.MarkerTextureMeta.METADATA).orElse(MarkerTextureMeta.DEFAULT));
			} catch (IOException ex) {
				RoleplayersAtlas.LOGGER.error("[Roleplayer's Atlas] Failed to access marker texture metadata for {}", e.getKey(), ex);
				textureMeta.put(id, MarkerTextures.MarkerTextureMeta.DEFAULT);
			}
		}
		return textureMeta;
	}

	@Override
	protected void apply(Map<Identifier, MarkerTextureMeta> prepared, ResourceManager manager, ProfilerFiller profiler) {
		RoleplayersAtlas.LOGGER.info("[Roleplayer's Atlas] Reloading Marker Textures...");
		textures.clear();
		prepared.forEach((id, meta) -> {
			if (id.getPath().endsWith("_accent")) {
				Identifier mainId = id.withPath(s -> s.substring(0, s.length() - "_accent".length()));
				MarkerTextureMeta main = prepared.get(mainId);
				if (main != null) {
					textures.put(mainId, main.build(mainId, true));
				} else {
					RoleplayersAtlas.LOGGER.error("[Roleplayer's Atlas] Marker accent {} has no main texture! Discarding.", id);
				}
			}
		});
		prepared.forEach((id, meta) -> {
			if (!textures.containsKey(id) && !id.getPath().endsWith("_accent")) {
				textures.put(id, meta.build(id, false));
			}
		});
	}

	@Override
	public Identifier getFabricId() {
		return ID;
	}

	public record MarkerTextureMeta(Optional<Identifier> item, Optional<Integer> textureWidth, Optional<Integer> textureHeight, Optional<Integer> mipLevels, Optional<Integer> offsetX, Optional<Integer> offsetY, Optional<Integer> nearClip, Optional<Integer> farClip) {
		public static final MarkerTextureMeta DEFAULT = new MarkerTextureMeta(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

		public static final Codec<MarkerTextureMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.optionalFieldOf("item").forGetter(MarkerTextureMeta::item),
			Codec.INT.optionalFieldOf("textureWidth").forGetter(MarkerTextureMeta::textureWidth),
			Codec.INT.optionalFieldOf("textureHeight").forGetter(MarkerTextureMeta::textureHeight),
			Codec.INT.optionalFieldOf("mipLevels").forGetter(MarkerTextureMeta::mipLevels),
			Codec.INT.optionalFieldOf("offsetX").forGetter(MarkerTextureMeta::offsetX),
			Codec.INT.optionalFieldOf("offsetY").forGetter(MarkerTextureMeta::offsetY),
			Codec.INT.optionalFieldOf("nearClip").forGetter(MarkerTextureMeta::nearClip),
			Codec.INT.optionalFieldOf("farClip").forGetter(MarkerTextureMeta::farClip)
		).apply(instance, MarkerTextureMeta::new));

		public static final MetadataSectionType<MarkerTextureMeta> METADATA = CodecUtil.metadataSerializer(CODEC, RoleplayersAtlas.id("marker"));

		public MarkerTexture build(Identifier id, boolean accent) {
			int textureWidth = this.textureWidth.orElse(32);
			int textureHeight = this.textureHeight.orElse(32);
			int mipLevels = this.mipLevels.orElse(0);
			int offsetX = this.offsetX.orElse(-textureWidth / 2);
			int offsetY = this.offsetY.orElse(-textureHeight / 2);
			int nearClip = this.nearClip.orElse(1);
			int farClip = this.farClip.orElse(Integer.MAX_VALUE);
			Identifier item = this.item.orElse(null);
			return MarkerTexture.ofId(id, item, offsetX, offsetY, textureWidth, textureHeight, mipLevels, nearClip, farClip, accent);
		}
	}
}
