package glam.ardor.roleplayers_atlas.gui;

import glam.ardor.roleplayers_atlas.util.AtlasPainter;
import folk.sisby.surveyor.PlayerSummary;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface AtlasOverlay {
	default void onScreenInit(AtlasScreen screen) {
	}

	default void onScreenRender(AtlasScreenRenderContext context) {
		onRender(new AtlasRenderContext(context.screen(), AtlasPainter.gui(context.context()), context.mouseX(), context.mouseY(), AtlasScreen.MAX_LIGHT, context.markerScale(), context.friends()));
	}

	default void onRender(AtlasRenderContext context) {
	}

	record AtlasScreenRenderContext(AtlasScreen screen, GuiGraphicsExtractor context, int mouseX, int mouseY, float markerScale, Map<UUID, PlayerSummary> friends) {
	}

	record AtlasRenderContext(AtlasRenderer renderer, AtlasPainter painter, @Nullable Integer mouseX, @Nullable Integer mouseY, int light, float markerScale, Map<UUID, PlayerSummary> friends) {
	}
}
