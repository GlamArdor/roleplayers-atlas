package glam.ardor.roleplayers_atlas.util;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a drawn route. Both the map and the length readout run through
 * the same sampling, so the number a player is told always matches the line
 * they are looking at.
 */
public final class RouteUtil {
	private RouteUtil() {
	}

	/** Catmull-Rom through the given points; a straight path is passed through untouched. */
	public static List<double[]> sample(double[][] points, boolean straight) {
		List<double[]> samples = new ArrayList<>();
		int count = points.length;
		if (count == 0) return samples;
		if (straight || count < 3) {
			for (double[] point : points) samples.add(point);
			return samples;
		}
		for (int i = 0; i < count - 1; i++) {
			double[] p0 = points[Math.max(0, i - 1)];
			double[] p1 = points[i];
			double[] p2 = points[i + 1];
			double[] p3 = points[Math.min(count - 1, i + 2)];
			int steps = 8;
			for (int s = 0; s < steps; s++) {
				double t = s / (double) steps;
				double t2 = t * t;
				double t3 = t2 * t;
				double x = 0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
				double y = 0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
				samples.add(new double[]{x, y});
			}
		}
		samples.add(points[count - 1]);
		return samples;
	}

	/**
	 * The route's length in blocks – the length of the line as drawn on the map,
	 * measured flat. There is no height in a route's points, and a cartographer
	 * measures the parchment rather than the ground under it.
	 */
	public static double length(List<BlockPos> points) {
		if (points == null || points.size() < 2) return 0;
		double[][] world = new double[points.size()][2];
		for (int i = 0; i < points.size(); i++) {
			world[i][0] = points.get(i).getX() + 0.5;
			world[i][1] = points.get(i).getZ() + 0.5;
		}
		List<double[]> samples = sample(world, false);
		double total = 0;
		for (int i = 1; i < samples.size(); i++) {
			total += Math.hypot(samples.get(i)[0] - samples.get(i - 1)[0], samples.get(i)[1] - samples.get(i - 1)[1]);
		}
		return total;
	}
}
