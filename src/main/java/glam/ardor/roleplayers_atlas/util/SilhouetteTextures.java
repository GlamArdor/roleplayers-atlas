package glam.ardor.roleplayers_atlas.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flat white copies of marker textures (alpha preserved, RGB forced to white),
 * registered lazily. Tinting one of these gives a silhouette in exactly the
 * requested color – a plain multiply over the original texture could never get
 * brighter than the texture's own pixels.
 */
public final class SilhouetteTextures {
	private static final Map<Identifier, Identifier> CACHE = new ConcurrentHashMap<>();

	private SilhouetteTextures() {
	}

	public static Identifier get(Identifier textureId) {
		return CACHE.computeIfAbsent(textureId, SilhouetteTextures::create);
	}

	private static Identifier create(Identifier textureId) {
		Identifier silhouetteId = textureId.withSuffixedPath(".silhouette");
		try {
			Resource resource = MinecraftClient.getInstance().getResourceManager().getResource(textureId).orElse(null);
			if (resource == null) return textureId;
			NativeImage image;
			try (InputStream in = resource.getInputStream()) {
				image = NativeImage.read(in);
			}
			// The alpha channel is the top byte in either packing, so OR-ing the
			// low 24 bits to full white is channel-order agnostic.
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					image.setColorArgb(x, y, image.getColorArgb(x, y) | 0x00FFFFFF);
				}
			}
			MinecraftClient.getInstance().getTextureManager().registerTexture(silhouetteId, new NativeImageBackedTexture(silhouetteId::toString, image));
			return silhouetteId;
		} catch (Exception e) {
			return textureId;
		}
	}
}
