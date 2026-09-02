package glam.ardor.roleplayers_atlas;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;


public class AtlasKeybindings {
	public static final KeyBinding ATLAS_KEYMAPPING = new KeyBinding("key.roleplayers_atlas.open", InputUtil.Type.KEYSYM, 77, "key.roleplayers_atlas.category");
	/** N by default, and rebindable in the vanilla controls screen like any other. */
	public static final KeyBinding QUICK_MARK_KEYMAPPING = new KeyBinding("key.roleplayers_atlas.quickMark", InputUtil.Type.KEYSYM, 78, "key.roleplayers_atlas.category");
	/** Resize the atlas held in the hands. Unbound by default. */
	public static final KeyBinding HAND_ZOOM_IN = new KeyBinding("key.roleplayers_atlas.handZoomIn", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "key.roleplayers_atlas.category");
	public static final KeyBinding HAND_ZOOM_OUT = new KeyBinding("key.roleplayers_atlas.handZoomOut", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "key.roleplayers_atlas.category");

	public static void init() {
		KeyBindingHelper.registerKeyBinding(ATLAS_KEYMAPPING);
		KeyBindingHelper.registerKeyBinding(QUICK_MARK_KEYMAPPING);
		KeyBindingHelper.registerKeyBinding(HAND_ZOOM_IN);
		KeyBindingHelper.registerKeyBinding(HAND_ZOOM_OUT);
		ClientTickEvents.END_CLIENT_TICK.register(AtlasKeybindings::onClientTick);
	}

	/** Nudge the held-atlas size, clamped to the config range, and remember it. */
	private static void adjustHandScale(int delta) {
		int v = net.minecraft.util.math.MathHelper.clamp(RoleplayersAtlas.CONFIG.handheldScale + delta, 50, 200);
		if (v != RoleplayersAtlas.CONFIG.handheldScale) {
			RoleplayersAtlas.CONFIG.handheldScale = v;
			RoleplayersAtlas.CONFIG.saveFields();
		}
	}

	public static void onClientTick(MinecraftClient client) {
		while (HAND_ZOOM_IN.wasPressed()) adjustHandScale(10);
		while (HAND_ZOOM_OUT.wasPressed()) adjustHandScale(-10);
		while (QUICK_MARK_KEYMAPPING.wasPressed()) {
			if (client.player != null) QuickMark.place(client);
		}
		while (ATLAS_KEYMAPPING.wasPressed()) {
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
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
			}
		}
	}
}
