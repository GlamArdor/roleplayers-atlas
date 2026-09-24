package glam.ardor.roleplayers_atlas.mixin;

import glam.ardor.roleplayers_atlas.gui.SettingsLook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the world visible behind the atlas settings screen.
 * <p>
 * The map redraws to these options, and a screen normally blurs and darkens
 * whatever is behind it – sensible for a menu, useless when the point of the
 * menu is what is behind it. Only the screen armed in {@link SettingsLook} is
 * affected, recognised by identity: every other screen in the game keeps its
 * background exactly as it was.
 */
@Mixin(Screen.class)
public abstract class MixinScreen {
	@Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true)
	private void roleplayers_atlas$noBlur(DrawContext context, CallbackInfo ci) {
		if (SettingsLook.isOurs()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;)V",
			at = @At("HEAD"), cancellable = true)
	private void roleplayers_atlas$noDarkening(DrawContext context, CallbackInfo ci) {
		if (SettingsLook.isOurs()) {
			ci.cancel();
		}
	}
}
