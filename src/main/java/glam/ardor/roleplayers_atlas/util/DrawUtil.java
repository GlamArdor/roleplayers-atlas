package glam.ardor.roleplayers_atlas.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import glam.ardor.roleplayers_atlas.mixin.MixinDrawContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

public class DrawUtil {
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("roleplayers_atlas", "textures/gui/white.png");

	/**
	 * Flat translucent plates in the world. 26.x dropped the untextured
	 * text-background layers, so the plates go through the text layers with a
	 * white texture instead, which also keeps them in the same pass as glyphs.
	 */
	public static RenderType plate(boolean seeThrough) {
		return seeThrough ? RenderTypes.textSeeThrough(WHITE) : RenderTypes.text(WHITE);
	}

	public static void drawCenteredWithRotation(AtlasPainter painter, Identifier texture, double x, double y, float z, float scale, int textureWidth, int textureHeight, float rotation, int light, int argb) {
		painter.push();
		painter.translate(x, y);
		painter.scale(scale);
		painter.rotateDegrees(180 + rotation);
		painter.translate(-textureWidth / 2f, -textureHeight / 2f);
		DrawBatcher.drawSingle(painter, texture, textureWidth, textureHeight, light, 0, 0, z, textureWidth, textureHeight, 0, 0, textureWidth, textureHeight, argb, false);
		painter.pop();
	}

	public static void fill(AtlasPainter painter, RenderType layer, float z, int light, int x1, int y1, int x2, int y2, float alpha, float[] color) {
		if (painter.isGui()) {
			painter.context().fill(x1, y1, x2, y2, ARGB.color((int) (alpha * 255), (int) (color[0] * 255), (int) (color[1] * 255), (int) (color[2] * 255)));
			return;
		}
		VertexConsumer vertexConsumer = painter.vertexConsumers().getBuffer(layer);
		Matrix4f matrix4f = painter.matrices().last().pose();

		vertexConsumer.addVertex(matrix4f, x1, y1, z).setColor(color[0], color[1], color[2], alpha).setUv(0.5F, 0.5F).setLight(light);
		vertexConsumer.addVertex(matrix4f, x1, y2, z).setColor(color[0], color[1], color[2], alpha).setUv(0.5F, 0.5F).setLight(light);
		vertexConsumer.addVertex(matrix4f, x2, y2, z).setColor(color[0], color[1], color[2], alpha).setUv(0.5F, 0.5F).setLight(light);
		vertexConsumer.addVertex(matrix4f, x2, y1, z).setColor(color[0], color[1], color[2], alpha).setUv(0.5F, 0.5F).setLight(light);
	}

	/**
	 * A quad with free corners, clipped to the given rectangle. Rectangles can't
	 * tile a strip that bends – neighbouring ones always leave a wedge open – so
	 * ribbons (route label plates) are built from quads that share their corners
	 * with the next piece. The clip stands in for the GUI scissor, which the
	 * render state below doesn't carry, and covers the handheld book too, where
	 * there is no scissor at all.
	 */
	public static void quadClipped(AtlasPainter painter, RenderType layer, float z, int light, double[] qx, double[] qy, double clipX1, double clipY1, double clipX2, double clipY2, float alpha, float[] color) {
		double[] px = new double[12];
		double[] py = new double[12];
		double[] cx = new double[12];
		double[] cy = new double[12];
		System.arraycopy(qx, 0, px, 0, 4);
		System.arraycopy(qy, 0, py, 0, 4);
		int count = 4;
		for (int edge = 0; edge < 4 && count > 2; edge++) {
			int kept = 0;
			for (int i = 0; i < count; i++) {
				int j = (i + 1) % count;
				double di = edgeDistance(edge, px[i], py[i], clipX1, clipY1, clipX2, clipY2);
				double dj = edgeDistance(edge, px[j], py[j], clipX1, clipY1, clipX2, clipY2);
				if (di >= 0) {
					cx[kept] = px[i];
					cy[kept] = py[i];
					kept++;
				}
				if ((di >= 0) != (dj >= 0)) {
					double t = di / (di - dj);
					cx[kept] = px[i] + (px[j] - px[i]) * t;
					cy[kept] = py[i] + (py[j] - py[i]) * t;
					kept++;
				}
			}
			System.arraycopy(cx, 0, px, 0, kept);
			System.arraycopy(cy, 0, py, 0, kept);
			count = kept;
		}
		if (count < 3) return;
		// A convex polygon goes out as a fan; the last corner is doubled up so
		// each piece still fits the quad the renderers expect.
		for (int i = 1; i < count - 1; i++) {
			quad(painter, layer, z, light, px[0], py[0], px[i], py[i], px[i + 1], py[i + 1], px[i + 1], py[i + 1], alpha, color);
		}
	}

	private static double edgeDistance(int edge, double x, double y, double clipX1, double clipY1, double clipX2, double clipY2) {
		return switch (edge) {
			case 0 -> x - clipX1;
			case 1 -> clipX2 - x;
			case 2 -> y - clipY1;
			default -> clipY2 - y;
		};
	}

	private static void quad(AtlasPainter painter, RenderType layer, float z, int light, double x0, double y0, double x1, double y1, double x2, double y2, double x3, double y3, float alpha, float[] color) {
		if (painter.isGui()) {
			float[] xs = {(float) x0, (float) x1, (float) x2, (float) x3};
			float[] ys = {(float) y0, (float) y1, (float) y2, (float) y3};
			int argb = ARGB.color((int) (alpha * 255), (int) (color[0] * 255), (int) (color[1] * 255), (int) (color[2] * 255));
			((MixinDrawContext) (Object) painter.context()).roleplayers_atlas$getState()
				.addGuiElement(AtlasQuadRenderState.of(new Matrix3x2f(painter.context().pose()), xs, ys, argb));
			return;
		}
		VertexConsumer vertexConsumer = painter.vertexConsumers().getBuffer(layer);
		Matrix4f matrix4f = painter.matrices().last().pose();

		vertexConsumer.addVertex(matrix4f, (float) x0, (float) y0, z).setColor(color[0], color[1], color[2], alpha).setUv(0.5F, 0.5F).setLight(light);
		vertexConsumer.addVertex(matrix4f, (float) x1, (float) y1, z).setColor(color[0], color[1], color[2], alpha).setUv(0.5F, 0.5F).setLight(light);
		vertexConsumer.addVertex(matrix4f, (float) x2, (float) y2, z).setColor(color[0], color[1], color[2], alpha).setUv(0.5F, 0.5F).setLight(light);
		vertexConsumer.addVertex(matrix4f, (float) x3, (float) y3, z).setColor(color[0], color[1], color[2], alpha).setUv(0.5F, 0.5F).setLight(light);
	}
}
