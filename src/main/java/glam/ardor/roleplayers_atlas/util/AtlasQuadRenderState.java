package glam.ardor.roleplayers_atlas.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/**
 * A flat-coloured quad with free corners. The GUI's own fills are rectangles,
 * so a strip of them tears open wherever the shape it follows turns; this state
 * lets the corners be shared between neighbouring strips instead.
 */
public record AtlasQuadRenderState(Matrix3x2f pose, float[] xs, float[] ys, int color, ScreenRectangle bounds) implements GuiElementRenderState {
	public static AtlasQuadRenderState of(Matrix3x2f pose, float[] xs, float[] ys, int color) {
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		Vector2f corner = new Vector2f();
		for (int i = 0; i < xs.length; i++) {
			pose.transformPosition(corner.set(xs[i], ys[i]));
			minX = Math.min(minX, corner.x);
			minY = Math.min(minY, corner.y);
			maxX = Math.max(maxX, corner.x);
			maxY = Math.max(maxY, corner.y);
		}
		int x = (int) Math.floor(minX);
		int y = (int) Math.floor(minY);
		ScreenRectangle bounds = new ScreenRectangle(x, y, (int) Math.ceil(maxX) - x + 1, (int) Math.ceil(maxY) - y + 1);
		return new AtlasQuadRenderState(pose, xs, ys, color, bounds);
	}

	@Override
	public void buildVertices(VertexConsumer vertices) {
		// Depth is no longer passed in: since 1.21.9 the GUI renderer assigns it
		// per element, and the vertex itself is plain 2D.
		for (int i = 0; i < 4; i++) {
			vertices.addVertexWith2DPose(this.pose, this.xs[i], this.ys[i]).setColor(this.color);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return RenderPipelines.GUI;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.noTexture();
	}

	@Override
	public ScreenRectangle scissorArea() {
		return null;
	}
}
