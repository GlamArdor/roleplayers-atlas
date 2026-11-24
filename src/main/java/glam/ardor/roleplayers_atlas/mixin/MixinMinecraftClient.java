package glam.ardor.roleplayers_atlas.mixin;

import glam.ardor.roleplayers_atlas.AtlasHoldMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftClient {
	// Escape puts the field-mode atlas away instead of opening the pause menu.
	// pauseOnly=true means an automatic pause (e.g. focus loss) – let those through.
	@Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
	private void roleplayers_atlas$closeFieldModeOnEscape(boolean pauseOnly, CallbackInfo ci) {
		if (!pauseOnly && AtlasHoldMode.isActive() && !AtlasHoldMode.isClosing()) {
			AtlasHoldMode.beginClose();
			((Minecraft) (Object) this).getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8F));
			ci.cancel();
		}
	}
}
