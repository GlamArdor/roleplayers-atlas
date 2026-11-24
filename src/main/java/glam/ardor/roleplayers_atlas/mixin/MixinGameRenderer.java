package glam.ardor.roleplayers_atlas.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import glam.ardor.roleplayers_atlas.AtlasHoldMode;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
	// Stabilization: with the option on, the walk bob is kept out of the hand
	// pass while the atlas is up, so the book reads steady against the moving
	// world. Only this pass is skipped - the world keeps bobbing as configured,
	// and the hurt tilt still comes through.
	@WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
	private void roleplayers_atlas$steadyAtlas(GameRenderer renderer, PoseStack matrices, float tickDelta, Operation<Void> original) {
		if (RoleplayersAtlas.CONFIG.stabilizeHeldMap && AtlasHoldMode.isActive()) return;
		original.call(renderer, matrices, tickDelta);
	}
}
