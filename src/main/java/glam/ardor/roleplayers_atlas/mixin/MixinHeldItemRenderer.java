package glam.ardor.roleplayers_atlas.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.AtlasHoldMode;
import glam.ardor.roleplayers_atlas.gui.HandheldAtlasRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class MixinHeldItemRenderer {
	// Field mode: render a virtual atlas book without it existing in the
	// inventory. While the main hand is empty the book fills both hands; with
	// an item there it moves to the off hand (whose own item is hidden in
	// first person), and the main hand renders its real item.
	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
	private ItemStack roleplayers_atlas$virtualAtlas(ItemStack stack, AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack stackArg, float equipProgress, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light) {
		if (!AtlasHoldMode.isActive()) return stack;
		if (!AtlasHoldMode.bookInSmallHand()) {
			// Big book fills both hands: virtual atlas in the main hand, whatever
			// is really held stays hidden until the shift.
			return hand == InteractionHand.MAIN_HAND ? AtlasHoldMode.getVirtualStack() : ItemStack.EMPTY;
		}
		InteractionHand bookHand = AtlasHoldMode.smallBookHand();
		return hand == bookHand ? AtlasHoldMode.getVirtualStack() : stack;
	}

	// While the mode is active the map-path routing must not depend on vanilla's
	// real off-hand field: big book always takes the two-handed path, small book
	// always the one-handed one. The field also lags a frame behind an F-swap,
	// which used to flash an empty vanilla map.
	@ModifyExpressionValue(method = "renderArmWithItem", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;offHandItem:Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack roleplayers_atlas$forceMapRouting(ItemStack stack) {
		if (!AtlasHoldMode.isActive()) return stack;
		return AtlasHoldMode.bookInSmallHand() ? AtlasHoldMode.getVirtualStack() : ItemStack.EMPTY;
	}

	// The two-handed map path reads the mainHand field directly instead of the
	// stack argument, so swap the virtual atlas in there too.
	@ModifyExpressionValue(method = "renderTwoHandedMap", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;mainHandItem:Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack roleplayers_atlas$virtualAtlasBothHands(ItemStack stack) {
		return AtlasHoldMode.isActive() && !AtlasHoldMode.bookInSmallHand() ? AtlasHoldMode.getVirtualStack() : stack;
	}

	// Field mode choreography: the main hand draws the closed book alone, then
	// the off hand reaches in from the side to open the cover.
	@WrapMethod(method = "renderMapHand")
	private void roleplayers_atlas$offHandReachesIn(PoseStack matrices, SubmitNodeCollector vertexConsumers, int light, HumanoidArm arm, Operation<Void> original) {
		Minecraft client = Minecraft.getInstance();
		if (AtlasHoldMode.isActive() && client.player != null && arm != client.player.getMainArm()) {
			float reach = AtlasHoldMode.easeOutCubic(AtlasHoldMode.armProgress());
			if (reach <= 0.0F) return;
			matrices.pushPose();
			float side = arm == HumanoidArm.LEFT ? -1.0F : 1.0F;
			// Negative y: in this space the hand slides in from the bottom of the
			// screen, matching how the main hand rises.
			matrices.translate(side * 0.5F * (1.0F - reach), -0.6F * (1.0F - reach), 0.0F);
			original.call(matrices, vertexConsumers, light, arm);
			matrices.popPose();
			return;
		}
		original.call(matrices, vertexConsumers, light, arm);
	}

	// Field mode: reuse the vanilla equip offset to animate the book being
	// drawn up into the hands, and to choreograph the hand shift: first half –
	// the book is lowered from both hands, second half – it rises small in the
	// off hand while the main hand raises its real item.
	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private float roleplayers_atlas$drawAnimation(float equipProgress, AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgressArg, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light) {
		if (!AtlasHoldMode.isActive()) return equipProgress;
		float shift = AtlasHoldMode.easeInOutCubic(AtlasHoldMode.handShift());
		if (shift > 0.5F) {
			// Second half of the shift: the small book and the real items rise
			// together in their hands (and lower back on the return).
			return Math.max(equipProgress, (1.0F - shift) * 2.0F);
		}
		if (hand == InteractionHand.MAIN_HAND) {
			float draw = Math.max(equipProgress, 1.0F - AtlasHoldMode.easeOutCubic(AtlasHoldMode.drawProgress()));
			return Math.max(draw, shift * 2.0F);
		}
		return equipProgress;
	}
	@Inject(method = "renderMap", at = @At("HEAD"), cancellable = true)
	protected void renderFirstPersonAtlas(PoseStack matrices, SubmitNodeCollector vertexConsumers, int light, ItemStack stack, CallbackInfo ci) {
		if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) return;
		// Reference check first: the field-mode virtual stack is a singleton and
		// must not depend on locale-sensitive display-name matching.
		if (stack != AtlasHoldMode.getVirtualStack()) {
			return;
		}
		HandheldAtlasRenderer.fromContext(Minecraft.getInstance().player).renderHandheldAtlas(matrices, vertexConsumers, light);
		ci.cancel();
	}

	// 1.21.8: the first-person map branch checks stack.contains(MAP_ID) instead of isOf(FILLED_MAP)
	@ModifyExpressionValue(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;contains(Lnet/minecraft/core/component/DataComponentType;)Z", ordinal = 0))
	protected boolean enableFirstPersonAtlasRendering(boolean original, AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack matrices, SubmitNodeCollector vertexConsumers, int light) {
		return original || stack == AtlasHoldMode.getVirtualStack();
	}
}
