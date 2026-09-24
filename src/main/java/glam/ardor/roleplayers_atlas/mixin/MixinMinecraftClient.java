package glam.ardor.roleplayers_atlas.mixin;

import glam.ardor.roleplayers_atlas.AtlasHoldMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
	// Escape puts the field-mode atlas away instead of opening the pause menu.
	// pauseOnly=true means an automatic pause (e.g. focus loss) – let those through.
	@Inject(method = "openGameMenu", at = @At("HEAD"), cancellable = true)
	private void roleplayers_atlas$closeFieldModeOnEscape(boolean pauseOnly, CallbackInfo ci) {
		if (!pauseOnly && AtlasHoldMode.isActive() && !AtlasHoldMode.isClosing()) {
			AtlasHoldMode.beginClose();
			((MinecraftClient) (Object) this).getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8F));
			ci.cancel();
		}
	}
}
