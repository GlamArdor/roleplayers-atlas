package glam.ardor.roleplayers_atlas.gui;

import glam.ardor.roleplayers_atlas.MarkerTexture;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class MarkerPreviewButton extends TexturePreviewButton<MarkerTexture> {
	public MarkerPreviewButton(MarkerTexture markerTexture, float[] tint) {
		super(markerTexture, markerTexture.id(), markerTexture.textureWidth(), markerTexture.textureHeight(), 0, tint);
	}

	@Override
	protected void drawTexture(GuiGraphicsExtractor context, int x, int y) {
		getValue().drawIcon(context, x, y, tint);
	}
}
