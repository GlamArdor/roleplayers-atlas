package glam.ardor.roleplayers_atlas;

import folk.sisby.surveyor.landmark.Landmark;
import net.minecraft.network.chat.Component;

/**
 * Which map zooms a label shows at. A level is the power of two of the scale
 * bookmark's ruler: 0 is "1c", 1 is "2c", -1 is "8b" – the same reading the
 * player sees on the bookmark, whatever the GUI scale.
 */
public final class LabelZoom {
	/** Closest zoom a limit can be set to ("4b"). */
	public static final int MIN_LEVEL = -2;
	/** Farthest zoom a limit can be set to ("64c"). */
	public static final int MAX_LEVEL = 6;
	/** Stored as the far limit when the player asked for no limit at all. */
	public static final int UNLIMITED = 99;
	/** Inscriptions with no far limit of their own fade out past 4c, as they always have. */
	public static final int PEN_DEFAULT_MAX = 2;

	private LabelZoom() {
	}

	/** The zoom level a map drawn at this many pixels per block is at. */
	public static int level(double pixelsPerBlock) {
		if (pixelsPerBlock <= 0) return 0;
		return (int) Math.round(-Math.log(pixelsPerBlock) / Math.log(2));
	}

	/** The level as the scale bookmark writes it. */
	public static String name(int level) {
		return level >= 0 ? (1 << level) + "c" : (16 >> -level) + "b";
	}

	public static int minOf(Landmark landmark) {
		Integer min = landmark.get(AtlasComponents.LABEL_ZOOM_MIN);
		return min == null ? Integer.MIN_VALUE : min;
	}

	public static int maxOf(Landmark landmark) {
		Integer max = landmark.get(AtlasComponents.LABEL_ZOOM_MAX);
		if (max != null) return max;
		return Boolean.TRUE.equals(landmark.get(AtlasComponents.PEN_LABEL)) ? PEN_DEFAULT_MAX : UNLIMITED;
	}

	public static boolean visible(Landmark landmark, double pixelsPerBlock) {
		int level = level(pixelsPerBlock);
		return level >= minOf(landmark) && level <= maxOf(landmark);
	}

	/** "4c", or "any" past either end of the range. */
	public static Component describe(int level) {
		if (level < MIN_LEVEL || level > MAX_LEVEL) return Component.translatable("gui.roleplayers_atlas.marker.zoom.any");
		return Component.literal(name(level));
	}
}
