package glam.ardor.roleplayers_atlas;

import glam.ardor.roleplayers_atlas.reloader.MarkerTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;

/**
 * Marking a place without opening the book.
 * <p>
 * The atlas is a book: it has to be taken out, opened, and aimed at the right
 * stretch of map. That is the right amount of ceremony for drawing a road and
 * far too much for "there – remember that". One key does the whole of it.
 * <p>
 * Two ways to aim, chosen in the settings. Under your feet is the plain one.
 * Where you are looking is the one that earns its keep: a peak on the horizon,
 * a tower across the water, a light in the trees can all be marked from where
 * you stand, without walking there to do it. The mark only ever needs its X and
 * Z, so even a line of sight that runs off into unloaded chunks gives an answer
 * worth keeping.
 */
public final class QuickMark {
	private QuickMark() {
	}

	public static void place(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null) return;
		WorldSummary summary = SurveyorClient.tryGetSummary(client.level.dimension());
		if (summary == null || summary.landmarks() == null) {
			player.displayClientMessage(Component.translatable("gui.roleplayers_atlas.quickMark.failed").withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return;
		}

		BlockPos at = RoleplayersAtlas.CONFIG.quickMark == AtlasConfig.QuickMark.LOOKING ? lookingAt(client, player) : player.blockPosition();

		// The same shape of id the marker dialog writes, so the icon, colour and
		// everything else resolve exactly as they would for a hand-placed mark.
		MarkerTexture texture = icon();
		DyeColor colour = DyeColor.RED;
		Identifier id = texture.keyId().withSuffix("/" + colour.getName() + "/" + at.getX() + "/" + at.getZ());
		Component name = Component.translatable("gui.roleplayers_atlas.quickMark.name");

		Landmark mark = Landmark.create(SurveyorClient.getClientUuid(), id, b -> b.add(LandmarkComponentTypes.POS, at));
		Landmark written = WorldAtlasData.copyLandmarkWith(mark, id, copy -> {
			copy.set(LandmarkComponentTypes.COLOR, colour.getTextureDiffuseColor());
			copy.set(LandmarkComponentTypes.NAME, name);
			// The coordinates go in the note rather than the name: a dozen marks
			// all called by their numbers is a list nobody can read, but the
			// numbers are still worth having when one of them is opened.
			copy.set(AtlasComponents.NOTE, at.getX() + ", " + at.getZ());
			// No place name shouted on screen for these – they are jotted down in
			// passing, not named places, and a walk past twenty of them would be
			// nothing but announcements.
			copy.set(AtlasComponents.ZONE_TITLE, false);
			copy.set(AtlasComponents.LAYER, MarkerLayers.DEFAULT_ID);
			copy.set(AtlasComponents.DAY, AtlasTime.gameDay());
			copy.set(AtlasComponents.REAL_TIME, AtlasTime.realMillis());
		});
		WorldAtlasData.swapLandmark(client.level.dimension(), null, written, Component.translatable("gui.roleplayers_atlas.undo.markerAdded", name));

		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.2F));
		player.displayClientMessage(Component.translatable("gui.roleplayers_atlas.quickMark.placed", at.getX(), at.getZ()), true);
	}

	/**
	 * What the player is facing, out to the configured range. A ray that hits
	 * nothing still answers: its far end is a place on the map, and marking the
	 * horizon you were looking at is exactly what was asked for.
	 */
	private static BlockPos lookingAt(Minecraft client, LocalPlayer player) {
		double range = RoleplayersAtlas.CONFIG.quickMarkRange;
		Vec3 from = player.getEyePosition(1.0F);
		Vec3 to = from.add(player.getViewVector(1.0F).scale(range));
		BlockHitResult hit = client.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit != null && hit.getType() == HitResult.Type.BLOCK) return hit.getBlockPos();
		return BlockPos.containing(to);
	}

	/** The icon quick marks are drawn with, from the settings. A small red cross by default. */
	private static MarkerTexture icon() {
		Identifier chosen = RoleplayersAtlas.id("custom/" + RoleplayersAtlas.CONFIG.quickMarkIcon);
		MarkerTexture texture = MarkerTextures.getInstance().asMap().get(chosen);
		if (texture != null) return texture;
		for (MarkerTexture other : MarkerTextures.getInstance().asMap().values()) {
			if (other.keyId().getPath().startsWith("custom/")) return other;
		}
		return MarkerTexture.DEFAULT;
	}

	/** Every icon the settings may choose from, in a steady order. */
	public static java.util.List<String> icons() {
		return MarkerTextures.getInstance().asMap().keySet().stream()
			.map(Identifier::getPath)
			.filter(p -> p.startsWith("custom/"))
			.map(p -> p.substring("custom/".length()))
			.sorted()
			.toList();
	}
}
