package glam.ardor.roleplayers_atlas.gui;

import glam.ardor.roleplayers_atlas.MarkerTexture;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class MarkerBookmarkButton extends BookmarkButton {
	protected final MarkerTexture markerTexture;

	public MarkerBookmarkButton(Component title, MarkerTexture markerTexture, int accent, boolean backwards, boolean vertical) {
		super(title, markerTexture.id(), accent, accent, markerTexture.textureWidth(), markerTexture.textureHeight(), backwards, vertical);
		this.markerTexture = markerTexture;
	}

	@Override
	public void drawIcon(GuiGraphicsExtractor context, int x, int y) {
		markerTexture.drawIcon(context, x, y, iconTint);
	}
}
