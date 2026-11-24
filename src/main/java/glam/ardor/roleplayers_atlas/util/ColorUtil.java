package glam.ardor.roleplayers_atlas.util;

import net.minecraft.util.ARGB;

public class ColorUtil {
	public static float[] componentsFromRgb(int color) {
		return new float[]{ARGB.red(color) / 255f, ARGB.green(color) / 255f, ARGB.blue(color) / 255f};
	}

	public static int rgbFromComponents(float[] components) {
		return ARGB.color(255, (int) (255 * components[0]), (int) (255 * components[1]), (int) (255 * components[2]));
	}
}
