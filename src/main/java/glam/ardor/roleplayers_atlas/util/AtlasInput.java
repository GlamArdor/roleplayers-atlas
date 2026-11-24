package glam.ardor.roleplayers_atlas.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * Screen lost its static hasShiftDown/hasControlDown/hasAltDown helpers in 1.21.9,
 * which now live on the input object handed to each callback. Half the places that
 * ask here are not callbacks at all - they are draws and tooltips deciding what to
 * show while a modifier is merely being held - so the window is polled directly,
 * exactly as the old helpers did.
 * <p>
 * The names are kept so that a static import restores the calls that used to be
 * inherited from Screen.
 */
public final class AtlasInput {
	private AtlasInput() {
	}

	private static boolean pressed(int leftKey, int rightKey) {
		var window = Minecraft.getInstance().getWindow();
		if (window == null) return false;
		return InputConstants.isKeyDown(window, leftKey) || InputConstants.isKeyDown(window, rightKey);
	}

	public static boolean hasShiftDown() {
		return pressed(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT);
	}

	public static boolean hasControlDown() {
		return pressed(InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL);
	}

	public static boolean hasAltDown() {
		return pressed(InputConstants.KEY_LALT, InputConstants.KEY_RALT);
	}
}
