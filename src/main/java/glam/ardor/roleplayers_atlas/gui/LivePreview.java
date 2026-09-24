package glam.ardor.roleplayers_atlas.gui;

import glam.ardor.roleplayers_atlas.AtlasConfig;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes the Cloth settings screen show its own effect while it is open.
 * <p>
 * Cloth hands a value over only when the player presses save, which is the
 * right behaviour for a form and the wrong one for a map: a slider you cannot
 * see the result of is a slider you set by trial and error, three menu round
 * trips at a time. So the widgets are read every tick and written straight
 * into the live config.
 * <p>
 * That leaves the promise Cloth makes – that escape discards your edits –
 * which is worth keeping. The config fields are copied when the screen opens
 * and copied back when it closes without a save, so a preview stays a preview.
 */
public final class LivePreview {
	/**
	 * How long another screen may sit on top before the preview gives up and
	 * reverts. Cloth puts a confirmation dialog in front of its own screen when
	 * you leave with unsaved changes, and reverting the moment that appears
	 * would undo everything the player is being asked about.
	 */
	private static final long AWAY_MILLIS = 3000L;

	@Nullable
	private static Screen owner;
	@Nullable
	private static Map<Field, Object> snapshot;
	private static List<Runnable> appliers = List.of();
	private static boolean saved;
	private static long awaySince;

	private LivePreview() {
	}

	/** Takes over: from now until this screen closes, its widgets drive the config. */
	public static void start(Screen screen, List<Runnable> widgets) {
		finish();
		owner = screen;
		appliers = List.copyOf(widgets);
		saved = false;
		awaySince = 0L;
		snapshot = copyOf(RoleplayersAtlas.CONFIG);
	}

	/** The player pressed save, so the preview is now the real thing and is not to be undone. */
	public static void markSaved() {
		saved = true;
		snapshot = copyOf(RoleplayersAtlas.CONFIG);
	}

	public static void tick(MinecraftClient client) {
		if (owner == null) {
			return;
		}
		if (client.currentScreen == owner) {
			awaySince = 0L;
			for (Runnable applier : appliers) {
				try {
					applier.run();
				} catch (Throwable error) {
					RoleplayersAtlas.LOGGER.warn("a settings widget could not be previewed", error);
				}
			}
			return;
		}
		long now = System.currentTimeMillis();
		if (awaySince == 0L) {
			awaySince = now;
			return;
		}
		if (client.currentScreen == null || now - awaySince > AWAY_MILLIS) {
			finish();
		}
	}

	private static void finish() {
		if (owner != null && !saved && snapshot != null) {
			restore(snapshot, RoleplayersAtlas.CONFIG);
		}
		owner = null;
		snapshot = null;
		appliers = List.of();
		saved = false;
		awaySince = 0L;
	}

	// ---- copying, over the public fields, the way the reset does -------------

	private static Map<Field, Object> copyOf(AtlasConfig config) {
		Map<Field, Object> copy = new LinkedHashMap<>();
		for (Field field : AtlasConfig.class.getFields()) {
			// Only this config's own fields: the wrapped-config machinery
			// underneath is not the player's edits and is not to be copied.
			if (Modifier.isStatic(field.getModifiers()) || field.getDeclaringClass() != AtlasConfig.class) {
				continue;
			}
			try {
				Object value = field.get(config);
				// Collections are the player's own entries; copy them rather than
				// sharing one instance, or the snapshot would follow every edit.
				if (value instanceof List<?> list) {
					value = new ArrayList<>(list);
				}
				copy.put(field, value);
			} catch (IllegalAccessException error) {
				RoleplayersAtlas.LOGGER.warn("could not copy {} for the preview", field.getName(), error);
			}
		}
		return copy;
	}

	private static void restore(Map<Field, Object> from, AtlasConfig to) {
		from.forEach((field, value) -> {
			try {
				field.set(to, value);
			} catch (IllegalAccessException error) {
				RoleplayersAtlas.LOGGER.warn("could not restore {} after the preview", field.getName(), error);
			}
		});
	}
}
