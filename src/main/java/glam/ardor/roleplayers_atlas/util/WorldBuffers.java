package glam.ardor.roleplayers_atlas.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in for the MultiBufferSource that 26.x removed. The handheld book is
 * still drawn vertex by vertex with positions already transformed, so the
 * geometry is recorded here and {@link #flush()} replays it into the frame's
 * submit queue as custom geometry nodes. Text goes straight to the queue, which
 * captures the pose at the moment of the call.
 * <p>
 * Draw order matters: the page has to be down before the tiles whose soft
 * edges blend over it, or those edges blend with the world behind the book
 * instead. The queue groups nodes by render type within one order, so every
 * change of render type opens the next order and the book is drawn exactly in
 * the sequence it was painted.
 */
public final class WorldBuffers {
	private static final PoseStack IDENTITY = new PoseStack();
	/** Vanilla puts the hand and the map's own decorations in the first orders. */
	private static final int FIRST_ORDER = 2;

	private record Segment(RenderType type, Recorder recorder) {
	}

	private final SubmitNodeCollector queue;
	private final List<Segment> segments = new ArrayList<>();

	public WorldBuffers(SubmitNodeCollector queue) {
		this.queue = queue;
	}

	public VertexConsumer getBuffer(RenderType type) {
		Segment last = segments.isEmpty() ? null : segments.getLast();
		if (last != null && last.type.equals(type)) return last.recorder;
		Segment next = new Segment(type, new Recorder());
		segments.add(next);
		return next.recorder;
	}

	public void text(PoseStack matrices, Component text, float x, float y, int argb, boolean shadow, int light) {
		queue.order(FIRST_ORDER + Math.max(0, segments.size() - 1)).submitText(matrices, x, y, text.getVisualOrderText(), shadow, Font.DisplayMode.NORMAL, light, argb, 0, 0);
	}

	public void flush() {
		for (int i = 0; i < segments.size(); i++) {
			Segment segment = segments.get(i);
			if (segment.recorder.isEmpty()) continue;
			Recorder recorder = segment.recorder;
			queue.order(FIRST_ORDER + i).submitCustomGeometry(IDENTITY, segment.type, (pose, vertices) -> recorder.replay(vertices));
		}
		segments.clear();
	}

	/**
	 * Records the attributes of each vertex in the order they were set and
	 * replays them unchanged. Only what the atlas uses is kept: position,
	 * colour, uv, overlay, light and normal.
	 */
	private static final class Recorder implements VertexConsumer {
		private static final int POS = 1, COLOR = 2, UV = 4, OVERLAY = 8, LIGHT = 16, NORMAL = 32;

		private final FloatArrayList floats = new FloatArrayList();
		private final IntArrayList ints = new IntArrayList();
		// per vertex: mask, then the int and float attributes the mask names
		private int currentMask = -1;
		private int maskIndex = -1;

		boolean isEmpty() {
			return ints.isEmpty();
		}

		private void flag(int bit) {
			currentMask |= bit;
			ints.set(maskIndex, currentMask);
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			currentMask = POS;
			maskIndex = ints.size();
			ints.add(POS);
			floats.add(x);
			floats.add(y);
			floats.add(z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			return setColor((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF));
		}

		@Override
		public VertexConsumer setColor(int argb) {
			flag(COLOR);
			ints.add(argb);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			flag(UV);
			floats.add(u);
			floats.add(v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			flag(OVERLAY);
			ints.add(u);
			ints.add(v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			flag(LIGHT);
			ints.add(u);
			ints.add(v);
			return this;
		}

		@Override
		public VertexConsumer setUv3(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			flag(NORMAL);
			floats.add(x);
			floats.add(y);
			floats.add(z);
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}

		void replay(VertexConsumer out) {
			int i = 0, f = 0;
			int n = ints.size();
			while (i < n) {
				int mask = ints.getInt(i++);
				out.addVertex(floats.getFloat(f), floats.getFloat(f + 1), floats.getFloat(f + 2));
				f += 3;
				// Attributes were appended in call order; the atlas always calls
				// them in this order, so replaying by mask keeps the streams aligned.
				if ((mask & COLOR) != 0) out.setColor(ints.getInt(i++));
				if ((mask & UV) != 0) {
					out.setUv(floats.getFloat(f), floats.getFloat(f + 1));
					f += 2;
				}
				if ((mask & OVERLAY) != 0) {
					out.setUv1(ints.getInt(i), ints.getInt(i + 1));
					i += 2;
				}
				if ((mask & LIGHT) != 0) {
					out.setUv2(ints.getInt(i), ints.getInt(i + 1));
					i += 2;
				}
				if ((mask & NORMAL) != 0) {
					out.setNormal(floats.getFloat(f), floats.getFloat(f + 1), floats.getFloat(f + 2));
					f += 3;
				}
			}
		}
	}
}
