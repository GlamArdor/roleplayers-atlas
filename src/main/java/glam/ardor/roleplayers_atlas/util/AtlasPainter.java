package glam.ardor.roleplayers_atlas.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Bridges the two rendering paths that survive in 1.21.6+:
 * GUI drawing must go through {@link GuiGraphicsExtractor} (2D matrices, deferred render
 * state), while in-world drawing (the handheld atlas) uses a {@link PoseStack}
 * with {@link WorldBuffers}, which feed the frame's submit queue.
 */
public final class AtlasPainter {
	private final GuiGraphicsExtractor context;
	private final PoseStack matrices;
	private final WorldBuffers vertexConsumers;

	private AtlasPainter(GuiGraphicsExtractor context, PoseStack matrices, WorldBuffers vertexConsumers) {
		this.context = context;
		this.matrices = matrices;
		this.vertexConsumers = vertexConsumers;
	}

	public static AtlasPainter gui(GuiGraphicsExtractor context) {
		return new AtlasPainter(context, null, null);
	}

	public static AtlasPainter world(PoseStack matrices, WorldBuffers vertexConsumers) {
		return new AtlasPainter(null, matrices, vertexConsumers);
	}

	public boolean isGui() {
		return context != null;
	}

	public GuiGraphicsExtractor context() {
		return context;
	}

	public PoseStack matrices() {
		return matrices;
	}

	public WorldBuffers vertexConsumers() {
		return vertexConsumers;
	}

	public void push() {
		if (isGui()) context.pose().pushMatrix();
		else matrices.pushPose();
	}

	public void pop() {
		if (isGui()) context.pose().popMatrix();
		else matrices.popPose();
	}

	public void translate(double x, double y) {
		if (isGui()) context.pose().translate((float) x, (float) y);
		else matrices.translate(x, y, 0.0);
	}

	public void scale(float scale) {
		if (isGui()) context.pose().scale(scale, scale);
		else matrices.scale(scale, scale, 1.0F);
	}

	public void rotateDegrees(float degrees) {
		if (isGui()) context.pose().rotate((float) Math.toRadians(degrees));
		else matrices.rotate(Axis.ZP.rotationDegrees(degrees));
	}

	/** Plain shadowed text without the plate, used for territory lettering. */
	public void drawGlyph(Component text, float x, float y, int argb, int light) {
		drawGlyph(text, x, y, argb, null, light);
	}

	/**
	 * Glyph with an explicit shadow color: null – the vanilla dark shadow,
	 * 0 – no shadow at all, otherwise that color. GUI: vanilla shadow via the
	 * text style. World: the style-driven shadow can land on top of the glyph
	 * in the baked text pipeline, so the shadow is drawn as its own shadowless
	 * pass at a deeper z with the glyph shadowless above it.
	 */
	public void drawGlyph(Component text, float x, float y, int argb, Integer shadowArgb, int light) {
		Font textRenderer = Minecraft.getInstance().font;
		boolean noShadow = shadowArgb != null && shadowArgb == 0;
		if (isGui()) {
			Component styled = shadowArgb == null || noShadow ? text : text.copy().withStyle(s -> s.withShadowColor(shadowArgb));
			context.text(textRenderer, styled, (int) x, (int) y, argb, !noShadow);
		} else if (shadowArgb == null || noShadow) {
			matrices.pushPose();
			matrices.translate(0.0, 0.0, -0.7);
			vertexConsumers.text(matrices, text, x, y, argb, !noShadow, light);
			matrices.popPose();
		} else {
			matrices.pushPose();
			matrices.translate(0.0, 0.0, -0.65);
			vertexConsumers.text(matrices, text, x + 1, y + 1, shadowArgb, false, light);
			matrices.translate(0.0, 0.0, -0.05);
			vertexConsumers.text(matrices, text, x, y, argb, false, light);
			matrices.popPose();
		}
	}

	/**
	 * Light text on a translucent dark plate (name-tag style): one text pass –
	 * no glyph z-fighting – and readable over any map background. In world mode
	 * the plate sits at a deeper z than the glyphs so depth testing keeps their
	 * order stable regardless of buffer flush order.
	 */
	public void drawText(Component text, float x, float y, int argb, int light) {
		Font textRenderer = Minecraft.getInstance().font;
		int alpha = argb >>> 24;
		int background = ((int) (alpha * 0.45F) << 24) | 0x201409;
		if (isGui()) {
			int width = textRenderer.width(text);
			context.pose().pushMatrix();
			context.pose().translate(x, y);
			context.fill(-2, -1, width + 1, 9, background);
			context.text(textRenderer, text, 0, 0, argb, false);
			context.pose().popMatrix();
		} else {
			int width = textRenderer.width(text);
			DrawUtil.fill(this, DrawUtil.plate(false), -0.5F, light, (int) x - 2, (int) y - 1, (int) x + width + 1, (int) y + 9, alpha / 255.0F * 0.45F, new float[]{0.125F, 0.078F, 0.035F});
			matrices.pushPose();
			matrices.translate(0.0, 0.0, -0.6);
			vertexConsumers.text(matrices, text, x, y, argb, false, light);
			matrices.popPose();
		}
	}
}
