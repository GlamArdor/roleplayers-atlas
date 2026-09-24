package glam.ardor.roleplayers_atlas;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;

/** Small helper for the atlas' UI sounds, with throttling for dragged actions. */
public final class AtlasSounds {
	private static long lastPaintMs = 0;

	private AtlasSounds() {
	}

	public static void play(SoundEvent sound, float volume, float pitch) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getSoundManager() == null) return;
		client.getSoundManager().play(PositionedSoundInstance.ui(sound, pitch, volume));
	}

	/** Brush-on-parchment rustle while painting territory chunks; throttled so dragging doesn't crackle. */
	public static void paintTerritory(boolean erasing) {
		long now = Util.getMeasuringTimeMs();
		if (now - lastPaintMs < 90) return;
		lastPaintMs = now;
		if (erasing) {
			// Scratching ink off the parchment.
			play(SoundEvents.BLOCK_SAND_STEP, 0.3F, 1.4F);
		} else {
			play(SoundEvents.BLOCK_WOOL_STEP, 0.25F, 1.6F);
		}
	}

	private static long lastRedrawMs = 0;

	/**
	 * The map being redrawn under the player's hands – a correction applied, a
	 * setting changed. Throttled hard: hundreds of tiles can settle at once and
	 * the point is to hear that something is happening, not to hear each one.
	 */
	public static void redrawing() {
		long now = Util.getMeasuringTimeMs();
		if (now - lastRedrawMs < 110) return;
		lastRedrawMs = now;
		play(SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, 0.5F, 1.3F + (now % 5) * 0.05F);
	}

	/** A short pen stroke when a route node is placed. */
	public static void routeNode() {
		play(SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, 0.35F, 1.3F);
	}

	/** Short click when a guide arrow is toggled on or off. */
	public static void trackToggle(boolean tracked) {
		play(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.4F, tracked ? 1.6F : 1.0F);
	}

	/** Brief soft tone when a zone title appears. */
	public static void zoneTitle() {
		play(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.25F, 0.8F);
	}

	/** Dry click for layer tabs. */
	public static void layerTab() {
		play(SoundEvents.BLOCK_LEVER_CLICK, 0.3F, 1.5F);
	}

	public static void searchToggle(boolean opening) {
		play(opening ? SoundEvents.ITEM_SPYGLASS_USE : SoundEvents.ITEM_SPYGLASS_STOP_USING, 0.4F, 1.2F);
	}

	/** The book shifting between both hands and the off hand. */
	public static void handShift() {
		play(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.5F, 0.8F);
	}

	/** Arrived at what a guide arrow was pointing to: one short bell, then the arrow is done. */
	public static void arrived() {
		play(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.4F, 1.5F);
	}

	/** Something you had only been told about turns out to be real – a quiet confirming chime. */
	public static void hearsayConfirmed() {
		play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.2F);
	}

	/** Export/screenshot finished: a page turn plus the cartographer's approval. */
	public static void exportDone() {
		play(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.7F, 1.0F);
		play(SoundEvents.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 0.6F, 1.0F);
	}
}
