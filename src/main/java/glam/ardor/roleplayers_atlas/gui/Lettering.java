package glam.ardor.roleplayers_atlas.gui;

import glam.ardor.roleplayers_atlas.util.AtlasPainter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Lays out a name in large spaced capitals along a gentle curve, the way old
 * maps letter a region. The curve is a parabola through three points – start,
 * middle and end – each lifted above the straight line by a percentage of half
 * the label's length, and the letters are spaced along its arc so bending a
 * label never stretches it.
 */
public final class Lettering {
	/** Gap between letters of an inscription, in unscaled font pixels. */
	public static final double PEN_GAP = 2.5;
	/** Unscaled letter scale of a lettered inscription at 100%. */
	public static final double PEN_BASE_SCALE = 2.0;
	/** How far a curve point can be lifted or lowered, in percent of half the length. */
	public static final int MAX_BEND = 50;
	/** The arch a new lettered inscription starts with. */
	public static final int[] DEFAULT_CURVE = {0, 12, 0};

	private static final int SAMPLES = 48;

	/** One letter: where its centre sits relative to the label's centre, and how it is turned. */
	public record Glyph(Component text, int width, double x, double y, double angle) {
	}

	private Lettering() {
	}

	public static Component[] glyphs(String name) {
		String upper = name.toUpperCase(java.util.Locale.ROOT);
		int[] codepoints = upper.codePoints().toArray();
		Component[] glyphs = new Component[codepoints.length];
		for (int i = 0; i < codepoints.length; i++) {
			glyphs[i] = Component.literal(new String(Character.toChars(codepoints[i]))).withStyle(ChatFormatting.BOLD);
		}
		return glyphs;
	}

	/** Width of the glyphs laid end to end with a gap between each, unscaled. */
	public static double rawLength(Component[] glyphs, double gap) {
		Font textRenderer = Minecraft.getInstance().font;
		double length = 0;
		for (Component glyph : glyphs) length += textRenderer.width(glyph);
		return length + gap * Math.max(0, glyphs.length - 1);
	}

	private static double bend(int[] curve, double u) {
		double a = curve[1] / 100.0;
		double b = (curve[2] - curve[0]) / 200.0;
		double c = (curve[0] + curve[2] - 2 * curve[1]) / 200.0;
		return a + b * u + c * u * u;
	}

	private static double slope(int[] curve, double u) {
		double b = (curve[2] - curve[0]) / 200.0;
		double c = (curve[0] + curve[2] - 2 * curve[1]) / 200.0;
		return b + 2 * c * u;
	}

	/** Arc length of the curve from start to end, in units of half its chord. */
	public static double arcLength(int[] curve) {
		double length = 0;
		double prevY = bend(curve, -1);
		for (int i = 1; i <= SAMPLES; i++) {
			double u = -1 + 2.0 * i / SAMPLES;
			double y = bend(curve, u);
			length += Math.hypot(2.0 / SAMPLES, y - prevY);
			prevY = y;
		}
		return length;
	}

	/**
	 * Places each glyph along the curve.
	 *
	 * @param scale    letter scale
	 * @param gap      space between letters, in screen pixels
	 * @param curve    lift of start, middle and end, percent of half the length
	 * @param rotation turn of the whole label, degrees
	 * @param center   whether to centre the label's height on its point rather than its chord
	 */
	public static Glyph[] layout(Component[] glyphs, double scale, double gap, int[] curve, double rotation, boolean center) {
		int count = glyphs.length;
		if (count == 0) return new Glyph[0];
		Font textRenderer = Minecraft.getInstance().font;
		int[] widths = new int[count];
		double total = 0;
		for (int i = 0; i < count; i++) {
			widths[i] = textRenderer.width(glyphs[i]);
			total += widths[i] * scale;
		}
		total += gap * (count - 1);
		if (total <= 0) return new Glyph[0];

		// Cumulative arc length at evenly spaced u, so a distance along the arc
		// can be turned back into a point on the curve.
		double[] arc = new double[SAMPLES + 1];
		double prevY = bend(curve, -1);
		double low = prevY, high = prevY;
		for (int i = 1; i <= SAMPLES; i++) {
			double u = -1 + 2.0 * i / SAMPLES;
			double y = bend(curve, u);
			arc[i] = arc[i - 1] + Math.hypot(2.0 / SAMPLES, y - prevY);
			prevY = y;
			low = Math.min(low, y);
			high = Math.max(high, y);
		}
		double half = total / arc[SAMPLES];
		double lift = center ? (low + high) / 2 : 0;
		double rad = Math.toRadians(rotation);
		double cos = Math.cos(rad), sin = Math.sin(rad);

		Glyph[] placed = new Glyph[count];
		double cursor = 0;
		int segment = 0;
		for (int i = 0; i < count; i++) {
			double target = (cursor + widths[i] * scale / 2) / half;
			cursor += widths[i] * scale + gap;
			while (segment < SAMPLES - 1 && arc[segment + 1] < target) segment++;
			double span = arc[segment + 1] - arc[segment];
			double f = span > 0 ? Math.min(1, Math.max(0, (target - arc[segment]) / span)) : 0;
			double u = -1 + 2.0 * (segment + f) / SAMPLES;
			double x = half * u;
			double y = -half * (bend(curve, u) - lift);
			double angle = Math.toDegrees(Math.atan2(-slope(curve, u), 1)) + rotation;
			placed[i] = new Glyph(glyphs[i], widths[i], x * cos - y * sin, x * sin + y * cos, angle);
		}
		return placed;
	}

	/** Draws one placed glyph around the given centre. */
	public static void draw(AtlasPainter painter, Glyph glyph, double centerX, double centerY, double scale, int argb, int shadowArgb, int light) {
		painter.push();
		painter.translate(centerX + glyph.x(), centerY + glyph.y());
		painter.rotateDegrees((float) glyph.angle());
		painter.scale((float) scale);
		painter.drawGlyph(glyph.text(), -glyph.width() / 2.0F, -4, argb, shadowArgb, light);
		painter.pop();
	}

	public static int[] curveOf(java.util.List<Integer> stored) {
		if (stored == null || stored.size() != 3) return null;
		return new int[]{stored.get(0), stored.get(1), stored.get(2)};
	}

	/** Shadow tone opposite to the ink, so dark ink gets a pale halo and pale ink a dark one. */
	public static int shadowFor(int inkRgb) {
		double luma = 0.299 * ((inkRgb >> 16) & 0xFF) + 0.587 * ((inkRgb >> 8) & 0xFF) + 0.114 * (inkRgb & 0xFF);
		return luma > 110 ? 0x2E1A0C : 0xF3E7C9;
	}
}
