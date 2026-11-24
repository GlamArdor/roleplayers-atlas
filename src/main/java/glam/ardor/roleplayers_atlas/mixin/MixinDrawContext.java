package glam.ardor.roleplayers_atlas.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Every fill the GUI offers is axis-aligned, which can't tile a ribbon that
 * bends. Reaching the render state lets us submit quads with free corners.
 */
@Mixin(GuiGraphics.class)
public interface MixinDrawContext {
	@Accessor("guiRenderState")
	GuiRenderState roleplayers_atlas$getState();
}
