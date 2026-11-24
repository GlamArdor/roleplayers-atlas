package glam.ardor.roleplayers_atlas.util;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

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
		Identifier silhouetteId = textureId.withSuffix(".silhouette");
		try {
			Resource resource = Minecraft.getInstance().getResourceManager().getResource(textureId).orElse(null);
			if (resource == null) return textureId;
			NativeImage image;
			try (InputStream in = resource.open()) {
				image = NativeImage.read(in);
			}
			// The alpha channel is the top byte in either packing, so OR-ing the
			// low 24 bits to full white is channel-order agnostic.
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					image.setPixel(x, y, image.getPixel(x, y) | 0x00FFFFFF);
				}
			}
			Minecraft.getInstance().getTextureManager().register(silhouetteId, new DynamicTexture(silhouetteId::toString, image));
			return silhouetteId;
		} catch (Exception e) {
			return textureId;
		}
	}
}
