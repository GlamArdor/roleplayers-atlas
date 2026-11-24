package glam.ardor.roleplayers_atlas.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

public class DrawBatcher implements AutoCloseable {

	protected final AtlasPainter painter;
	protected final Identifier texture;
	protected final Matrix4f matrix4f;
	protected final VertexConsumer vertexConsumer;
	protected final float textureWidth;
	protected final float textureHeight;
	protected final int light;

	// Gui mode: quads are accumulated and submitted as one render-state element.
	protected FloatArrayList guiQuads;
	protected IntArrayList guiColors;
	protected float guiMinX, guiMinY, guiMaxX, guiMaxY;

	public static boolean areWeShadersRightNow() {
		try {
			Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Method instanceMethod = apiClass.getDeclaredMethod("getInstance");
			Method inUseMethod = apiClass.getDeclaredMethod("isShaderPackInUse");
			Object apiInstance = instanceMethod.invoke(null);
			return (boolean) inUseMethod.invoke(apiInstance);
		} catch (Exception e) {
			return false;
		}
	}

	public static void drawSingle(AtlasPainter painter, Identifier texture, int textureWidth, int textureHeight, int light, int x, int y, float z, int width, int height, int u, int v, int regionWidth, int regionHeight, int argb, boolean drawingTransparent) {
		try (DrawBatcher batcher = new DrawBatcher(painter, texture, textureWidth, textureHeight, light, drawingTransparent)) {
			batcher.add(x, y, z, width, height, u, v, regionWidth, regionHeight, argb);
		}
	}

	public DrawBatcher(AtlasPainter painter, Identifier texture, int textureWidth, int textureHeight, int light, boolean drawingTransparent) {
		this.painter = painter;
		this.texture = texture;
		if (painter.isGui()) {
			this.matrix4f = null;
			this.vertexConsumer = null;
		} else {
			WorldBuffers vertexConsumers = painter.vertexConsumers();
			if (areWeShadersRightNow()) {
				if (drawingTransparent) {
					this.vertexConsumer = vertexConsumers.getBuffer(RenderTypes.entityTranslucent(texture));
				} else {
					this.vertexConsumer = vertexConsumers.getBuffer(RenderTypes.entitySolid(texture));
				}
			} else {
				this.vertexConsumer = vertexConsumers.getBuffer(RenderTypes.text(texture));
			}
			this.matrix4f = painter.matrices().last().pose();
		}
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.light = light;
	}

	public void add(int x, int y, float z, int width, int height, int u, int v, int regionWidth, int regionHeight, int argb) {
		if (painter.isGui()) {
			if (guiQuads == null) {
				guiQuads = new FloatArrayList();
				guiColors = new IntArrayList();
				guiMinX = Float.MAX_VALUE;
				guiMinY = Float.MAX_VALUE;
				guiMaxX = -Float.MAX_VALUE;
				guiMaxY = -Float.MAX_VALUE;
			}
			float x1 = x, y1 = y, x2 = x + width, y2 = y + height;
			guiQuads.add(x1);
			guiQuads.add(y1);
			guiQuads.add(x2);
			guiQuads.add(y2);
			guiQuads.add((u + 0.0F) / textureWidth);
			guiQuads.add((v + 0.0F) / textureHeight);
			guiQuads.add((u + (float) regionWidth) / textureWidth);
			guiQuads.add((v + (float) regionHeight) / textureHeight);
			guiColors.add(argb);
			guiMinX = Math.min(guiMinX, Math.min(x1, x2));
			guiMinY = Math.min(guiMinY, Math.min(y1, y2));
			guiMaxX = Math.max(guiMaxX, Math.max(x1, x2));
			guiMaxY = Math.max(guiMaxY, Math.max(y1, y2));
			return;
		}
		this.innerAdd(x, x + width, y, y + height, z,
			(u + 0.0F) / textureWidth,
			(u + (float) regionWidth) / textureWidth,
			(v + 0.0F) / textureHeight,
			(v + (float) regionHeight) / textureHeight,
			argb
		);
	}

	protected void innerAdd(float x1, float x2, float y1, float y2, float z, float u1, float u2, float v1, float v2, int argb) {
		vertexConsumer.addVertex(matrix4f, x1, y1, z).setColor(argb).setUv(u1, v1).setOverlay(0).setLight(light).setNormal(0, 0, 0);
		vertexConsumer.addVertex(matrix4f, x1, y2, z).setColor(argb).setUv(u1, v2).setOverlay(0).setLight(light).setNormal(0, 0, 0);
		vertexConsumer.addVertex(matrix4f, x2, y2, z).setColor(argb).setUv(u2, v2).setOverlay(0).setLight(light).setNormal(0, 0, 0);
		vertexConsumer.addVertex(matrix4f, x2, y1, z).setColor(argb).setUv(u2, v1).setOverlay(0).setLight(light).setNormal(0, 0, 0);
	}

	@Override
	public void close() {
		if (painter.isGui() && guiQuads != null && !guiQuads.isEmpty()) {
			GuiGraphicsExtractor context = painter.context();
			Matrix3x2f pose = new Matrix3x2f(context.pose());
			ScreenRectangle scissor = context.scissorStack.peek();
			// Textures carry their own sampler since 1.21.9, and TextureSetup now wants both.
			AbstractTexture boundTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
			TextureSetup textureSetup = TextureSetup.singleTexture(boundTexture.getTextureView(), boundTexture.getSampler());
			ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(guiMinX), (int) Math.floor(guiMinY), (int) Math.ceil(guiMaxX - guiMinX), (int) Math.ceil(guiMaxY - guiMinY)).transformMaxBounds(pose);
			if (scissor != null) bounds = bounds.intersection(scissor);
			if (bounds != null) {
				context.guiRenderState.addGuiElement(new BatchedGuiQuads(RenderPipelines.GUI_TEXTURED, textureSetup, pose, scissor, guiQuads.toFloatArray(), guiColors.toIntArray(), guiColors.size(), bounds));
			}
			guiQuads = null;
			guiColors = null;
		}
	}
}
