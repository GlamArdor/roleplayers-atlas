package glam.ardor.roleplayers_atlas.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.AtlasHoldMode;
import glam.ardor.roleplayers_atlas.gui.HandheldAtlasRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class MixinHeldItemRenderer {
	// Field mode: render a virtual atlas book without it existing in the
	// inventory. While the main hand is empty the book fills both hands; with
	// an item there it moves to the off hand (whose own item is hidden in
	// first person), and the main hand renders its real item.
	@ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true)
	private ItemStack roleplayers_atlas$virtualAtlas(ItemStack stack, AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stackArg, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue vertexConsumers, int light) {
		if (!AtlasHoldMode.isActive()) return stack;
		if (!AtlasHoldMode.bookInSmallHand()) {
			// Big book fills both hands: virtual atlas in the main hand, whatever
			// is really held stays hidden until the shift.
			return hand == Hand.MAIN_HAND ? AtlasHoldMode.getVirtualStack() : ItemStack.EMPTY;
		}
		Hand bookHand = AtlasHoldMode.smallBookHand();
		return hand == bookHand ? AtlasHoldMode.getVirtualStack() : stack;
	}

	// While the mode is active the map-path routing must not depend on vanilla's
	// real off-hand field: big book always takes the two-handed path, small book
	// always the one-handed one. The field also lags a frame behind an F-swap,
	// which used to flash an empty vanilla map.
	@ModifyExpressionValue(method = "renderFirstPersonItem", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;offHand:Lnet/minecraft/item/ItemStack;"))
	private ItemStack roleplayers_atlas$forceMapRouting(ItemStack stack) {
		if (!AtlasHoldMode.isActive()) return stack;
		return AtlasHoldMode.bookInSmallHand() ? AtlasHoldMode.getVirtualStack() : ItemStack.EMPTY;
	}

	// The two-handed map path reads the mainHand field directly instead of the
	// stack argument, so swap the virtual atlas in there too.
	@ModifyExpressionValue(method = "renderMapInBothHands", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;mainHand:Lnet/minecraft/item/ItemStack;"))
	private ItemStack roleplayers_atlas$virtualAtlasBothHands(ItemStack stack) {
		return AtlasHoldMode.isActive() && !AtlasHoldMode.bookInSmallHand() ? AtlasHoldMode.getVirtualStack() : stack;
	}

	// Field mode choreography: the main hand draws the closed book alone, then
	// the off hand reaches in from the side to open the cover.
	@WrapMethod(method = "renderArm")
	private void roleplayers_atlas$offHandReachesIn(MatrixStack matrices, OrderedRenderCommandQueue vertexConsumers, int light, Arm arm, Operation<Void> original) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (AtlasHoldMode.isActive() && client.player != null && arm != client.player.getMainArm()) {
			float reach = AtlasHoldMode.easeOutCubic(AtlasHoldMode.armProgress());
			if (reach <= 0.0F) return;
			matrices.push();
			float side = arm == Arm.LEFT ? -1.0F : 1.0F;
			// Negative y: in this space the hand slides in from the bottom of the
			// screen, matching how the main hand rises.
			matrices.translate(side * 0.5F * (1.0F - reach), -0.6F * (1.0F - reach), 0.0F);
			original.call(matrices, vertexConsumers, light, arm);
			matrices.pop();
			return;
		}
		original.call(matrices, vertexConsumers, light, arm);
	}

	// Field mode: reuse the vanilla equip offset to animate the book being
	// drawn up into the hands, and to choreograph the hand shift: first half –
	// the book is lowered from both hands, second half – it rises small in the
	// off hand while the main hand raises its real item.
	@ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private float roleplayers_atlas$drawAnimation(float equipProgress, AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgressArg, MatrixStack matrices, OrderedRenderCommandQueue vertexConsumers, int light) {
		if (!AtlasHoldMode.isActive()) return equipProgress;
		float shift = AtlasHoldMode.easeInOutCubic(AtlasHoldMode.handShift());
		if (shift > 0.5F) {
			// Second half of the shift: the small book and the real items rise
			// together in their hands (and lower back on the return).
			return Math.max(equipProgress, (1.0F - shift) * 2.0F);
		}
		if (hand == Hand.MAIN_HAND) {
			float draw = Math.max(equipProgress, 1.0F - AtlasHoldMode.easeOutCubic(AtlasHoldMode.drawProgress()));
			return Math.max(draw, shift * 2.0F);
		}
		return equipProgress;
	}
	@Inject(method = "renderFirstPersonMap", at = @At("HEAD"), cancellable = true)
	protected void renderFirstPersonAtlas(MatrixStack matrices, OrderedRenderCommandQueue vertexConsumers, int light, ItemStack stack, CallbackInfo ci) {
		if (MinecraftClient.getInstance().player == null || MinecraftClient.getInstance().world == null) return;
		// Reference check first: the field-mode virtual stack is a singleton and
		// must not depend on locale-sensitive display-name matching.
		if (stack != AtlasHoldMode.getVirtualStack()) {
			return;
		}
		HandheldAtlasRenderer.fromContext(MinecraftClient.getInstance().player).renderHandheldAtlas(matrices, vertexConsumers, light);
		ci.cancel();
	}

	// 1.21.8: the first-person map branch checks stack.contains(MAP_ID) instead of isOf(FILLED_MAP)
	@ModifyExpressionValue(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;contains(Lnet/minecraft/component/ComponentType;)Z", ordinal = 0))
	protected boolean enableFirstPersonAtlasRendering(boolean original, AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack stack, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue vertexConsumers, int light) {
		return original || stack == AtlasHoldMode.getVirtualStack();
	}
}
