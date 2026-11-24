package glam.ardor.roleplayers_atlas.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Remembers which screen object is the atlas settings, so the blur-and-darkening
 * mixin can recognise it by identity rather than by guesswork. Only the screen
 * armed here loses its backdrop; every other screen – of this mod or any other –
 * keeps its background exactly as it was.
 */
public final class SettingsLook {
	private static Screen armed;

	private SettingsLook() {
	}

	public static void arm(Screen screen) {
		armed = screen;
	}

	public static boolean isOurs() {
		Screen current = Minecraft.getInstance().gui.screen();
		return current != null && current == armed;
	}
}
