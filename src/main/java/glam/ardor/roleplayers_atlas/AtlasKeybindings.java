package glam.ardor.roleplayers_atlas;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;


public class AtlasKeybindings {
	/**
	 * Categories stopped being bare translation keys in 1.21.9. The label is now
	 * derived from the id as {@code key.category.<namespace>.<path>}, which is the
	 * key the language files carry.
	 */
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(RoleplayersAtlas.ID, "atlas"));

	public static final KeyMapping ATLAS_KEYMAPPING = new KeyMapping("key.roleplayers_atlas.open", InputConstants.Type.KEYSYM, 77, CATEGORY);
	/** N by default, and rebindable in the vanilla controls screen like any other. */
	public static final KeyMapping QUICK_MARK_KEYMAPPING = new KeyMapping("key.roleplayers_atlas.quickMark", InputConstants.Type.KEYSYM, 78, CATEGORY);
	/** Resize the atlas held in the hands. Unbound by default. */
	public static final KeyMapping HAND_ZOOM_IN = new KeyMapping("key.roleplayers_atlas.handZoomIn", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
	public static final KeyMapping HAND_ZOOM_OUT = new KeyMapping("key.roleplayers_atlas.handZoomOut", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

	public static void init() {
		KeyBindingHelper.registerKeyBinding(ATLAS_KEYMAPPING);
		KeyBindingHelper.registerKeyBinding(QUICK_MARK_KEYMAPPING);
		KeyBindingHelper.registerKeyBinding(HAND_ZOOM_IN);
		KeyBindingHelper.registerKeyBinding(HAND_ZOOM_OUT);
		ClientTickEvents.END_CLIENT_TICK.register(AtlasKeybindings::onClientTick);
	}

	/** Nudge the held-atlas size, clamped to the config range, and remember it. */
	private static void adjustHandScale(int delta) {
		int v = net.minecraft.util.Mth.clamp(RoleplayersAtlas.CONFIG.handheldScale + delta, 50, 200);
		if (v != RoleplayersAtlas.CONFIG.handheldScale) {
			RoleplayersAtlas.CONFIG.handheldScale = v;
			RoleplayersAtlas.CONFIG.saveFields();
		}
	}

	public static void onClientTick(Minecraft client) {
		while (HAND_ZOOM_IN.consumeClick()) adjustHandScale(10);
		while (HAND_ZOOM_OUT.consumeClick()) adjustHandScale(-10);
		while (QUICK_MARK_KEYMAPPING.consumeClick()) {
			if (client.player != null) QuickMark.place(client);
		}
		while (ATLAS_KEYMAPPING.consumeClick()) {
			if (client.player == null) continue;
			if (AtlasHoldMode.isClosing()) {
				// Pressing the key while the book is being put away draws it again.
				AtlasHoldMode.activate();
			} else if (AtlasHoldMode.isActive()) {
				// Second press: put the book away and open the full atlas screen.
				AtlasHoldMode.deactivate();
				RoleplayersAtlas.openAtlasScreen();
			} else {
				// First press: draw the book into the player's hands.
				
				AtlasHoldMode.activate();
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
			}
		}
	}
}
