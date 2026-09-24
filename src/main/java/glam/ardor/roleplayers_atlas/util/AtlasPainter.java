package glam.ardor.roleplayers_atlas.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

/**
 * Bridges the two rendering paths that survive in 1.21.6+:
 * GUI drawing must go through {@link DrawContext} (2D matrices, deferred render
 * state), while in-world drawing (the handheld atlas) still uses a
 * {@link MatrixStack} with a {@link VertexConsumerProvider}.
 */
public final class AtlasPainter {
	private final DrawContext context;
	private final MatrixStack matrices;
	private final VertexConsumerProvider vertexConsumers;

	private AtlasPainter(DrawContext context, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
		this.context = context;
		this.matrices = matrices;
		this.vertexConsumers = vertexConsumers;
	}

	public static AtlasPainter gui(DrawContext context) {
		return new AtlasPainter(context, null, null);
	}

	public static AtlasPainter world(MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
		return new AtlasPainter(null, matrices, vertexConsumers);
	}

	public boolean isGui() {
		return context != null;
	}

	public DrawContext context() {
		return context;
	}

	public MatrixStack matrices() {
		return matrices;
	}

	public VertexConsumerProvider vertexConsumers() {
		return vertexConsumers;
	}

	public void push() {
		if (isGui()) context.getMatrices().pushMatrix();
		else matrices.push();
	}

	public void pop() {
		if (isGui()) context.getMatrices().popMatrix();
		else matrices.pop();
	}

	public void translate(double x, double y) {
		if (isGui()) context.getMatrices().translate((float) x, (float) y);
		else matrices.translate(x, y, 0.0);
	}

	public void scale(float scale) {
		if (isGui()) context.getMatrices().scale(scale, scale);
		else matrices.scale(scale, scale, 1.0F);
	}

	public void rotateDegrees(float degrees) {
		if (isGui()) context.getMatrices().rotate((float) Math.toRadians(degrees));
		else matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
	}

	/** Plain shadowed text without the plate, used for territory lettering. */
	public void drawGlyph(Text text, float x, float y, int argb, int light) {
		drawGlyph(text, x, y, argb, null, light);
	}

	/**
	 * Glyph with an explicit shadow color: null – the vanilla dark shadow,
	 * 0 – no shadow at all, otherwise that color. GUI: vanilla shadow via the
	 * text style. World: the style-driven shadow can land on top of the glyph
	 * in the baked text pipeline, so the shadow is drawn as its own shadowless
	 * pass at a deeper z with the glyph shadowless above it.
	 */
	public void drawGlyph(Text text, float x, float y, int argb, Integer shadowArgb, int light) {
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		boolean noShadow = shadowArgb != null && shadowArgb == 0;
		if (isGui()) {
			Text styled = shadowArgb == null || noShadow ? text : text.copy().styled(s -> s.withShadowColor(shadowArgb));
			context.drawText(textRenderer, styled, (int) x, (int) y, argb, !noShadow);
		} else if (shadowArgb == null || noShadow) {
			matrices.push();
			matrices.translate(0.0, 0.0, -0.7);
			textRenderer.draw(text, x, y, argb, !noShadow, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
			matrices.pop();
		} else {
			matrices.push();
			matrices.translate(0.0, 0.0, -0.65);
			textRenderer.draw(text, x + 1, y + 1, shadowArgb, false, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
			matrices.translate(0.0, 0.0, -0.05);
			textRenderer.draw(text, x, y, argb, false, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
			matrices.pop();
		}
	}

	/**
	 * Light text on a translucent dark plate (name-tag style): one text pass –
	 * no glyph z-fighting – and readable over any map background. In world mode
	 * the plate sits at a deeper z than the glyphs so depth testing keeps their
	 * order stable regardless of buffer flush order.
	 */
	public void drawText(Text text, float x, float y, int argb, int light) {
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		int alpha = argb >>> 24;
		int background = ((int) (alpha * 0.45F) << 24) | 0x201409;
		if (isGui()) {
			int width = textRenderer.getWidth(text);
			context.getMatrices().pushMatrix();
			context.getMatrices().translate(x, y);
			context.fill(-2, -1, width + 1, 9, background);
			context.drawText(textRenderer, text, 0, 0, argb, false);
			context.getMatrices().popMatrix();
		} else {
			int width = textRenderer.getWidth(text);
			DrawUtil.fill(this, net.minecraft.client.render.RenderLayers.textBackground(), -0.5F, light, (int) x - 2, (int) y - 1, (int) x + width + 1, (int) y + 9, alpha / 255.0F * 0.45F, new float[]{0.125F, 0.078F, 0.035F});
			matrices.push();
			matrices.translate(0.0, 0.0, -0.6);
			textRenderer.draw(text, x, y, argb, false, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
			matrices.pop();
		}
	}
}
