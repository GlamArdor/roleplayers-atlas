package glam.ardor.roleplayers_atlas;

import glam.ardor.roleplayers_atlas.util.AtlasPainter;
import glam.ardor.roleplayers_atlas.util.DrawBatcher;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector2d;

public record MarkerTexture(Identifier id, Identifier accentId, Identifier item, int offsetX, int offsetY, int textureWidth, int textureHeight, int mipLevels, int nearClip, int farClip) {
	public static Identifier idToTexture(Identifier id) {
		return id.withPrefixedPath("textures/atlas/marker/").withSuffixedPath(".png");
	}

	public static MarkerTexture ofId(Identifier id, Identifier item, int offsetX, int offsetY, int width, int height, int mipLevels, int nearClip, int farClip, boolean accent) {
		return new MarkerTexture(idToTexture(id), accent ? idToTexture(id.withSuffixedPath("_accent")) : null, item, offsetX, offsetY, width, height, mipLevels, nearClip, farClip);
	}

	public static MarkerTexture centered(Identifier id, Identifier item, int width, int height, int mipLevels, int nearClip, int farClip, boolean accent) {
		return ofId(id, item, -width / 2, -height / 2, width, height, mipLevels, nearClip, farClip, accent);
	}

	public static final MarkerTexture DEFAULT = centered(RoleplayersAtlas.id("custom/point"), Identifier.of("minecraft", "emerald"), 32, 32, 0, 1, Integer.MAX_VALUE, true);

	public Identifier keyId() {
		return Identifier.of(id.getNamespace(), id.getPath().substring("textures/atlas/marker/".length(), id.getPath().length() - 4));
	}

	public String displayId() {
		return id.getNamespace().equals(RoleplayersAtlas.ID) ? keyId().getPath() : keyId().toString();
	}

	public int fullTextureWidth() {
		int width = textureWidth;
		for (int i = 0; i < mipLevels; i++) {
			width += textureWidth >> (i + 1);
		}
		return width;
	}

	public int getU(int mipLevel) {
		int currentMipLevel = mipLevel - 1;
		int u = 0;
		while (currentMipLevel >= 0) {
			u += textureWidth / (1 << currentMipLevel);
			currentMipLevel--;
		}
		return u;
	}

	public Vector2d getCenter(int tileChunks) {
		int mipLevel = MathHelper.clamp(MathHelper.ceilLog2(tileChunks), 0, mipLevels);
		return new Vector2d(((double) offsetX + (double) textureWidth / 2.0) / (double) (1 << mipLevel), ((double) offsetY + (double) textureHeight / 2.0) / (double) (1 << mipLevel));
	}

	public double getSquaredSize(int tileChunks) {
		int mipLevel = MathHelper.clamp(MathHelper.ceilLog2(tileChunks), 0, mipLevels);
		return textureWidth * textureHeight / (double) (1 << mipLevel);
	}

	public void drawIcon(DrawContext context, int x, int y, float[] accent) {
		context.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight, fullTextureWidth(), textureHeight);
		if (accentId != null && accent != null) {
			int accentArgb = ColorHelper.getArgb(255, (int) (accent[0] * 255), (int) (accent[1] * 255), (int) (accent[2] * 255));
			context.drawTexture(RenderPipelines.GUI_TEXTURED, accentId, x, y, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight, fullTextureWidth(), textureHeight, accentArgb);
		}
	}

	/** The texture's shape in one flat color – the tracked-marker outline glow. */
	public void drawSilhouette(AtlasPainter painter, double markerX, double markerY, float z, float markerScale, int tileChunks, int argb, int light) {
		Identifier silhouette = glam.ardor.roleplayers_atlas.util.SilhouetteTextures.get(id);
		painter.push();
		painter.translate(markerX, markerY);
		painter.scale(markerScale);
		if (tileChunks > 1 && mipLevels > 0) {
			int mipLevel = MathHelper.clamp(MathHelper.ceilLog2(tileChunks), 0, mipLevels);
			DrawBatcher.drawSingle(painter, silhouette, fullTextureWidth(), textureHeight, light, offsetX / (1 << mipLevel), offsetY / (1 << mipLevel), z, textureWidth / (1 << mipLevel), textureHeight / (1 << mipLevel), getU(mipLevel), 0, textureWidth / (1 << mipLevel), textureHeight / (1 << mipLevel), argb, false);
		} else {
			DrawBatcher.drawSingle(painter, silhouette, fullTextureWidth(), textureHeight, light, offsetX, offsetY, z, textureWidth, textureHeight, 0, 0, textureWidth, textureHeight, argb, false);
		}
		painter.pop();
	}

	public void draw(AtlasPainter painter, double markerX, double markerY, float z, float markerScale, int tileChunks, float[] accent, float tint, float alpha, int light) {
		if (alpha == 0) return;
		painter.push();
		painter.translate(markerX, markerY);
		painter.scale(markerScale);
		int mainArgb = ColorHelper.getArgb((int) (alpha * 255), (int) (tint * 255), (int) (tint * 255), (int) (tint * 255));
		int accentArgb = accent != null ? ColorHelper.getArgb((int) (alpha * 255), (int) (tint * accent[0] * 255), (int) (tint * accent[1] * 255), (int) (tint * accent[2] * 255)) : 0;
		if (tileChunks > 1 && mipLevels > 0) {
			int mipLevel = MathHelper.clamp(MathHelper.ceilLog2(tileChunks), 0, mipLevels);
			DrawBatcher.drawSingle(painter, id, fullTextureWidth(), textureHeight, light, offsetX / (1 << mipLevel), offsetY / (1 << mipLevel), z, textureWidth / (1 << mipLevel), textureHeight / (1 << mipLevel), getU(mipLevel), 0, textureWidth / (1 << mipLevel), textureHeight / (1 << mipLevel), mainArgb, false);
			if (accentId != null && accent != null) {
				DrawBatcher.drawSingle(painter, accentId, fullTextureWidth(), textureHeight, light, offsetX / (1 << mipLevel), offsetY / (1 << mipLevel), z, textureWidth / (1 << mipLevel), textureHeight / (1 << mipLevel), getU(mipLevel), 0, textureWidth / (1 << mipLevel), textureHeight / (1 << mipLevel), accentArgb, false);
			}
		} else {
			DrawBatcher.drawSingle(painter, id, fullTextureWidth(), textureHeight, light, offsetX, offsetY, z, textureWidth, textureHeight, 0, 0, textureWidth, textureHeight, mainArgb, false);
			if (accentId != null && accent != null) {
				DrawBatcher.drawSingle(painter, accentId, fullTextureWidth(), textureHeight, light, offsetX, offsetY, z, textureWidth, textureHeight, 0, 0, textureWidth, textureHeight, accentArgb, false);
			}
		}
		painter.pop();
	}
}
