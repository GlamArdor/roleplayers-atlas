package glam.ardor.roleplayers_atlas.util;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;

/**
 * A single gui render-state element carrying many textured quads: thousands of
 * map subtiles become one draw instead of one gui element each, which is what
 * keeps close zoom levels smooth.
 */
public final class BatchedGuiQuads implements GuiElementRenderState {
	private final RenderPipeline pipeline;
	private final TextureSetup textureSetup;
	private final Matrix3x2f pose;
	private final ScreenRectangle scissorArea;
	/** Packed per quad: x1, y1, x2, y2, u1, v1, u2, v2. */
	private final float[] quads;
	private final int[] colors;
	private final int count;
	private final ScreenRectangle bounds;

	public BatchedGuiQuads(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, ScreenRectangle scissorArea, float[] quads, int[] colors, int count, ScreenRectangle bounds) {
		this.pipeline = pipeline;
		this.textureSetup = textureSetup;
		this.pose = pose;
		this.scissorArea = scissorArea;
		this.quads = quads;
		this.colors = colors;
		this.count = count;
		this.bounds = bounds;
	}

	@Override
	public void buildVertices(VertexConsumer vertices) {
		// Depth is no longer passed in: since 1.21.9 the GUI renderer assigns it
		// per element, and the vertex itself is plain 2D.
		for (int i = 0; i < count; i++) {
			int q = i * 8;
			float x1 = quads[q], y1 = quads[q + 1], x2 = quads[q + 2], y2 = quads[q + 3];
			float u1 = quads[q + 4], v1 = quads[q + 5], u2 = quads[q + 6], v2 = quads[q + 7];
			int color = colors[i];
			vertices.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
			vertices.addVertexWith2DPose(pose, x1, y2).setUv(u1, v2).setColor(color);
			vertices.addVertexWith2DPose(pose, x2, y2).setUv(u2, v2).setColor(color);
			vertices.addVertexWith2DPose(pose, x2, y1).setUv(u2, v1).setColor(color);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return pipeline;
	}

	@Override
	public TextureSetup textureSetup() {
		return textureSetup;
	}

	@Override
	public ScreenRectangle scissorArea() {
		return scissorArea;
	}

	@Override
	public ScreenRectangle bounds() {
		return bounds;
	}
}
