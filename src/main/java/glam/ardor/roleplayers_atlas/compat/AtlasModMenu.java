package glam.ardor.roleplayers_atlas.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.gui.AtlasConfigScreen;
import glam.ardor.roleplayers_atlas.gui.SettingsLook;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;

/**
 * ModMenu integration: opens the atlas settings.
 * <p>
 * Cloth Config gives the better screen – searchable, categories down the side,
 * a reset on every row – but it is only suggested, not required, so the
 * built-in list stays behind as a fallback. The Cloth-facing class is only
 * named inside the try, so it is never loaded on a client that doesn't have
 * Cloth, and a Cloth major version bump degrades to our own screen instead of
 * crashing the game.
 */
public class AtlasModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			FabricLoader loader = FabricLoader.getInstance();
			if (loader.isModLoaded("cloth-config") || loader.isModLoaded("cloth-config2")) {
				try {
					return AtlasClothConfig.create(parent);
				} catch (Throwable t) {
					RoleplayersAtlas.LOGGER.warn("Cloth Config screen failed, using the built-in one", t);
				}
			}
			Screen screen = new AtlasConfigScreen(parent);
			SettingsLook.arm(screen);
			return screen;
		};
	}
}
