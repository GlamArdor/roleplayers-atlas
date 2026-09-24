package glam.ardor.roleplayers_atlas;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * Client-side "field mode": the atlas book is shown in the player's hands
 * (first person, like a vanilla map) without occupying an inventory slot.
 * Toggled by the atlas key: first press draws the book, second press opens
 * the full atlas screen; Escape plays the closing animation.
 */
public final class AtlasHoldMode {
	// Opening timeline, ms: the book rises, the off hand reaches in, the cover opens.
	private static final long DRAW_MS = 400;
	private static final long ARM_START_MS = 350;
	private static final long ARM_MS = 300;
	private static final long OPEN_START_MS = 500;
	private static final long OPEN_MS = 600;
	// Closing timeline, ms – the opening mirrored: the cover swings shut, the
	// off hand lets go, the book is lowered away.
	private static final long CLOSE_COVER_MS = 500;
	private static final long CLOSE_ARM_START_MS = 350;
	private static final long CLOSE_ARM_MS = 300;
	private static final long CLOSE_DRAW_START_MS = 450;
	private static final long CLOSE_DRAW_MS = 400;
	private static final long CLOSE_TOTAL_MS = 900;

	// Hand shift, ms: with an item in the main hand the open book is lowered
	// from both hands and raised again, small, in the off hand (and back).
	private static final long SHIFT_MS = 500;

	private static boolean active = false;
	private static boolean closing = false;
	private static boolean pendingClose = false;
	private static long activatedAt = 0;
	private static long closingAt = 0;
	private static float shiftValue = 0;
	private static long shiftLastMs = 0;
	private static ItemStack virtualStack;

	private AtlasHoldMode() {
	}

	public static boolean isActive() {
		finishCloseIfDone();
		return active;
	}

	public static boolean isClosing() {
		finishCloseIfDone();
		return active && closing;
	}

	private static void finishCloseIfDone() {
		if (active && closing && Util.getMeasuringTimeMs() - closingAt >= CLOSE_TOTAL_MS) {
			active = false;
			closing = false;
			pendingClose = false;
			shiftValue = 0;
		}
	}

	public static void activate() {
		active = true;
		closing = false;
		pendingClose = false;
		shiftValue = 0;
		shiftLastMs = 0;
		activatedAt = Util.getMeasuringTimeMs();
	}

	/**
	 * Starts the closing animation; the mode deactivates itself once it ends.
	 * If the book is currently in the off hand, it first glides back into both
	 * hands and only then folds shut.
	 */
	public static void beginClose() {
		if (!active || closing || pendingClose) return;
		if (shiftValue > 0.001F) {
			pendingClose = true;
			return;
		}
		closing = true;
		closingAt = Util.getMeasuringTimeMs();
	}

	/** Instantly puts the book away, without the animation. */
	public static void deactivate() {
		active = false;
		closing = false;
		pendingClose = false;
		shiftValue = 0;
	}

	/**
	 * 0..1 hand shift: 0 – the open book fills both hands, 1 – it sits small in
	 * the off hand while the main hand holds its item. Advances lazily towards
	 * its target each query; when a deferred close is waiting, the moment the
	 * book returns to both hands the closing animation starts.
	 */
	public static float handShift() {
		if (!active) return shiftValue = 0;
		long now = Util.getMeasuringTimeMs();
		if (shiftLastMs == 0) shiftLastMs = now;
		float step = (now - shiftLastMs) / (float) SHIFT_MS;
		shiftLastMs = now;
		float target = shiftTargetNow();
		float before = shiftValue;
		shiftValue += MathHelper.clamp(target - shiftValue, -step, step);
		// A page rustle as the book starts moving between hands.
		if (before <= 0.001F && shiftValue > 0.001F) AtlasSounds.handShift();
		if (before >= 0.999F && shiftValue < 0.999F) AtlasSounds.handShift();
		if (pendingClose && shiftValue <= 0.001F) {
			shiftValue = 0;
			pendingClose = false;
			closing = true;
			closingAt = now;
		}
		return shiftValue;
	}

	/** Whether the book currently renders small in one hand (second half of the shift). */
	public static boolean bookInSmallHand() {
		return handShift() > 0.5F;
	}

	/**
	 * The hand the small book settles into: the off hand normally, or the main
	 * hand when only the off hand is holding an item.
	 */
	public static net.minecraft.util.Hand smallBookHand() {
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client.player != null && client.player.getMainHandStack().isEmpty() && !client.player.getOffHandStack().isEmpty()) return net.minecraft.util.Hand.MAIN_HAND;
		return net.minecraft.util.Hand.OFF_HAND;
	}

	private static float shiftTargetNow() {
		if (closing || pendingClose) return 0;
		if (openProgress() < 1) return 0;
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		return client.player != null && (!client.player.getMainHandStack().isEmpty() || !client.player.getOffHandStack().isEmpty()) ? 1 : 0;
	}

	public static ItemStack getVirtualStack() {
		if (virtualStack == null) virtualStack = RoleplayersAtlas.virtualAtlasStack();
		return virtualStack;
	}

	/** 0..1 raw progress of the draw (rise) phase; runs backwards while closing. */
	public static float drawProgress() {
		if (!active) return 1;
		if (closing) return 1 - MathHelper.clamp((Util.getMeasuringTimeMs() - closingAt - CLOSE_DRAW_START_MS) / (float) CLOSE_DRAW_MS, 0f, 1f);
		return MathHelper.clamp((Util.getMeasuringTimeMs() - activatedAt) / (float) DRAW_MS, 0f, 1f);
	}

	/** 0..1 raw progress of the off-hand reaching in; runs backwards while closing. */
	public static float armProgress() {
		if (!active) return 1;
		if (closing) return 1 - MathHelper.clamp((Util.getMeasuringTimeMs() - closingAt - CLOSE_ARM_START_MS) / (float) CLOSE_ARM_MS, 0f, 1f);
		return MathHelper.clamp((Util.getMeasuringTimeMs() - activatedAt - ARM_START_MS) / (float) ARM_MS, 0f, 1f);
	}

	/** 0..1 raw progress of the page-open phase; runs backwards while closing. */
	public static float openProgress() {
		if (!active) return 1;
		if (closing) return 1 - MathHelper.clamp((Util.getMeasuringTimeMs() - closingAt) / (float) CLOSE_COVER_MS, 0f, 1f);
		return MathHelper.clamp((Util.getMeasuringTimeMs() - activatedAt - OPEN_START_MS) / (float) OPEN_MS, 0f, 1f);
	}

	/**
	 * 0..1 alpha for marker labels: they appear quickly right after the book
	 * finishes opening (text can't be split across the swinging page), and
	 * vanish the moment closing starts.
	 */
	public static float labelAlpha() {
		if (!active) return 1;
		if (closing || pendingClose) return 0;
		return MathHelper.clamp((Util.getMeasuringTimeMs() - activatedAt - (OPEN_START_MS + OPEN_MS)) / 200f, 0f, 1f);
	}

	public static float easeOutCubic(float t) {
		float f = 1 - t;
		return 1 - f * f * f;
	}

	public static float easeInOutCubic(float t) {
		return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	}
}
