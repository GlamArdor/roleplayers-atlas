package glam.ardor.roleplayers_atlas;

import glam.ardor.roleplayers_atlas.reloader.MarkerTextures;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

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

	public static void place(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) return;
		WorldSummary summary = SurveyorClient.tryGetSummary(client.world.getRegistryKey());
		if (summary == null || summary.landmarks() == null) {
			player.sendMessage(Text.translatable("gui.roleplayers_atlas.quickMark.failed").formatted(net.minecraft.util.Formatting.GRAY), true);
			return;
		}

		BlockPos at = RoleplayersAtlas.CONFIG.quickMark == AtlasConfig.QuickMark.LOOKING ? lookingAt(client, player) : player.getBlockPos();

		// The same shape of id the marker dialog writes, so the icon, colour and
		// everything else resolve exactly as they would for a hand-placed mark.
		MarkerTexture texture = icon();
		DyeColor colour = DyeColor.RED;
		Identifier id = texture.keyId().withSuffixedPath("/" + colour.getId() + "/" + at.getX() + "/" + at.getZ());
		Text name = Text.translatable("gui.roleplayers_atlas.quickMark.name");

		Landmark mark = Landmark.create(SurveyorClient.getClientUuid(), id, b -> b.add(LandmarkComponentTypes.POS, at));
		Landmark written = WorldAtlasData.copyLandmarkWith(mark, id, copy -> {
			copy.set(LandmarkComponentTypes.COLOR, colour.getEntityColor());
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
		WorldAtlasData.swapLandmark(client.world.getRegistryKey(), null, written, Text.translatable("gui.roleplayers_atlas.undo.markerAdded", name));

		client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 1.2F));
		player.sendMessage(Text.translatable("gui.roleplayers_atlas.quickMark.placed", at.getX(), at.getZ()), true);
	}

	/**
	 * What the player is facing, out to the configured range. A ray that hits
	 * nothing still answers: its far end is a place on the map, and marking the
	 * horizon you were looking at is exactly what was asked for.
	 */
	private static BlockPos lookingAt(MinecraftClient client, ClientPlayerEntity player) {
		double range = RoleplayersAtlas.CONFIG.quickMarkRange;
		Vec3d from = player.getCameraPosVec(1.0F);
		Vec3d to = from.add(player.getRotationVec(1.0F).multiply(range));
		BlockHitResult hit = client.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
		if (hit != null && hit.getType() == HitResult.Type.BLOCK) return hit.getBlockPos();
		return BlockPos.ofFloored(to);
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
