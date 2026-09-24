package glam.ardor.roleplayers_atlas.gui;

import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.AtlasHoldMode;
import glam.ardor.roleplayers_atlas.WorldAtlasData;
import glam.ardor.roleplayers_atlas.util.AtlasPainter;
import glam.ardor.roleplayers_atlas.util.DrawBatcher;
import glam.ardor.roleplayers_atlas.util.MathUtil;
import glam.ardor.roleplayers_atlas.MarkerTexture;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.joml.Vector2d;

import java.util.Map;

public record HandheldAtlasRenderer(int bookX, int bookY, int bookWidth, int bookHeight, int mapWidth, int mapHeight, int tilePixels, int tileChunks, double guiScale, double mapOffsetX, double mapOffsetY, int mapScale, PlayerEntity player, WorldAtlasData worldAtlasData, RegistryKey<World> dim) implements AtlasRenderer {
	public static final Identifier BOOK_COVER = RoleplayersAtlas.id("textures/gui/book_cover.png");

	public static HandheldAtlasRenderer fromContext(PlayerEntity player) {
		return new HandheldAtlasRenderer(
			0,
			0,
			DEFAULT_BOOK_WIDTH,
			DEFAULT_BOOK_HEIGHT,
			DEFAULT_BOOK_WIDTH - MAP_BORDER_WIDTH * 2,
			DEFAULT_BOOK_HEIGHT - MAP_BORDER_HEIGHT * 2,
			16,
			1,
			1,
			-player.getBlockX(),
			-player.getBlockZ(),
			1,
			player,
			WorldAtlasData.getOrCreate(player.getEntityWorld().getRegistryKey()),
			player.getEntityWorld().getRegistryKey()
		);
	}

	/**
	 * 1.21.9 stopped handing the first-person renderer a {@link VertexConsumerProvider}
	 * and defers everything through a command queue instead. The book is still drawn
	 * exactly as before, into a provider of our own, and that provider is flushed
	 * inside a custom command so it lands at the point in the frame the hand is drawn.
	 * The vertices are already in world space by then, so the entry handed to the
	 * command is not needed.
	 * <p>
	 * The allocator is kept between frames rather than rebuilt, since it holds
	 * native memory.
	 */
	private static BufferAllocator bookAllocator;
	private static VertexConsumerProvider.Immediate bookBuffers;

	public void renderHandheldAtlas(MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
		if (bookBuffers == null) {
			bookAllocator = new BufferAllocator(786432);
			bookBuffers = VertexConsumerProvider.immediate(bookAllocator);
		}
		VertexConsumerProvider.Immediate vertexConsumers = bookBuffers;

		matrices.push();
		// Mirror the vanilla first-person map transform so the book sits in the
		// hands exactly like a held map, then fit the book spread to map height.
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
		// The held book's size scales with the setting (and the live zoom keys).
		float handScale = 0.38F * (RoleplayersAtlas.CONFIG.handheldScale / 100.0F);
		matrices.scale(handScale, handScale, handScale);
		float fit = 142.0F / bookHeight;
		matrices.scale(fit, fit, 1.0F);
		matrices.scale(1.0F / 128.0F, 1.0F / 128.0F, 1.0F / 128.0F);

		float open = AtlasHoldMode.easeInOutCubic(AtlasHoldMode.openProgress());
		int half = bookWidth / 2;

		// The spine stays fixed for the whole animation: the closed book sits
		// where its right half will be, and the cover fans out to the left.
		matrices.translate(-bookWidth / 2.0, -bookHeight / 2.0, 0.0);

		AtlasPainter painter = AtlasPainter.world(matrices, vertexConsumers);

		boolean inScreen = MinecraftClient.getInstance().currentScreen instanceof AtlasScreen;

		if (open >= 1.0F) {
			DrawBatcher.drawSingle(painter, AtlasScreen.BOOK, bookWidth, bookHeight, light, bookX, bookY, 0.3F, bookWidth, bookHeight, 0, 0, bookWidth, bookHeight, 0xFFFFFFFF, false);
			if (!inScreen) {
				renderTiles(painter, light, 0xFFFFFFFF);
				DrawBatcher.drawSingle(painter, BOOK_FRAME, bookWidth, bookHeight, light, bookX, bookY, -0.45F, bookWidth, bookHeight, 0, 0, bookWidth, bookHeight, 0xFFFFFFFF, true);
				overlays.keySet().forEach(id -> overlays.get(id).onRender(new AtlasOverlay.AtlasRenderContext(this, painter, null, null, light, 1.0F, RoleplayersAtlas.getOrderedFriends())));
				renderMarkersAndPlayers(painter, light, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true);
			}
		} else {
			float angle = (1.0F - open) * 178.0F;

			// Flat right half of the spread, with its map content, frame and
			// markers. Hidden while the book is still shut, so it can't peek out
			// around the cover.
			if (angle < 170.0F) {
				DrawBatcher.drawSingle(painter, AtlasScreen.BOOK, bookWidth, bookHeight, light, half, bookY, 0.3F, half, bookHeight, half, 0, half, bookHeight, 0xFFFFFFFF, false);
				if (!inScreen) {
					renderTiles(painter, light, 0xFFFFFFFF, half, Integer.MAX_VALUE);
					renderMarkersAndPlayers(painter, light, half, Double.POSITIVE_INFINITY, false);
				}
				DrawBatcher.drawSingle(painter, BOOK_FRAME, bookWidth, bookHeight, light, half, bookY, -0.45F, half, bookHeight, half, 0, half, bookHeight, 0xFFFFFFFF, true);
			}

			// The hinged flap: leather cover on the back, the left page with its
			// map content on the front, rotating around the fixed spine. It stays
			// opaque for the whole swing and lands exactly where the flat left
			// page takes over.
			matrices.push();
			// Z travel of the swing is flattened (fake page flip) so the flap
			// never pokes into the camera, and the whole flap is lifted towards
			// the viewer to cover the pages beneath it.
			matrices.translate(half, 0.0, 0.0);
			matrices.scale(1.0F, 1.0F, 0.12F);
			matrices.translate(0.0, 0.0, -6.0);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-angle));
			// Back into book coordinates so map content lands on the flap and
			// swings open together with the page.
			matrices.translate(-half, 0.0, 0.0);
			// Front of the flap: the left page of the spread with the map, frame
			// and markers on it, so the page looks finished from the first frame.
			DrawBatcher.drawSingle(painter, AtlasScreen.BOOK, bookWidth, bookHeight, light, bookX, bookY, 2.0F, half, bookHeight, 0, 0, half, bookHeight, 0xFFFFFFFF, false);
			if (!inScreen) {
				renderTiles(painter, light, 0xFFFFFFFF, Integer.MIN_VALUE, half);
				renderMarkersAndPlayers(painter, light, Double.NEGATIVE_INFINITY, half, false);
			}
			DrawBatcher.drawSingle(painter, BOOK_FRAME, bookWidth, bookHeight, light, bookX, bookY, -0.5F, half, bookHeight, 0, 0, half, bookHeight, 0xFFFFFFFF, true);
			// Back of the flap: the leather cover. Drawn with reversed winding
			// (negative width) and mirrored UVs so it faces the viewer when the
			// book is folded shut.
			DrawBatcher.drawSingle(painter, BOOK_COVER, half, bookHeight, light, half, bookY, 4.0F, -half, bookHeight, half, 0, -half, bookHeight, 0xFFFFFFFF, false);
			matrices.pop();
		}

		matrices.pop();

		queue.submitCustom(matrices, RenderLayers.entityTranslucent(AtlasScreen.BOOK), (entry, vertices) -> vertexConsumers.draw());
	}

	/**
	 * Draws markers and player arrows whose anchor lies inside [clipMinX,
	 * clipMaxX) in book pixels, so each page carries its own markers during the
	 * opening animation. Area markers (chunk claims) have no single anchor and
	 * are only drawn in the full pass.
	 */
	private void renderMarkersAndPlayers(AtlasPainter painter, int light, double clipMinX, double clipMaxX, boolean fullPass) {
		Rect2i mapArea = new Rect2i(bookX + MAP_BORDER_WIDTH, bookY + MAP_BORDER_HEIGHT, mapWidth, mapHeight);

		Map<Landmark, MarkerTexture> markers = worldAtlasData.getAllMarkers(tileChunks);
		// The hearth is held back and drawn after everything else. Markers come
		// out of a hash map in no fixed order, and the hearth is rebuilt fresh
		// every frame, so sharing a spot with another mark had the two trading
		// places from frame to frame – which reads as flickering.
		Landmark spawnMark = null;
		MarkerTexture spawnTexture = null;
		for (Map.Entry<Landmark, MarkerTexture> entry : markers.entrySet()) {
			if (glam.ardor.roleplayers_atlas.SpawnMarker.is(entry.getKey())) {
				spawnMark = entry.getKey();
				spawnTexture = entry.getValue();
			}
		}

		markers.forEach((landmark, texture) -> {
			if (glam.ardor.roleplayers_atlas.SpawnMarker.is(landmark)) return;
			if (!RoleplayersAtlas.layerVisible(landmark)) return;
			// Routes span the whole spread, so they only draw in the full pass.
			if (landmark.contains(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE) && !fullPass) return;
			BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
			if (pos == null) {
				if (!fullPass) return;
			} else {
				double markerX = worldXToScreenX(pos.getX()) - bookX;
				if (markerX < clipMinX || markerX >= clipMaxX) return;
			}
			renderMarker(painter, landmark, texture, -0.3F, light, (x, y) -> (float) MathHelper.clamp(MathUtil.innerDistanceToEdge(mapArea, new Vector2d(x, y)) / 32.0, 0, 1), false, false, 1);
			if (fullPass) {
				renderMarkerLabel(painter, landmark, AtlasHoldMode.labelAlpha(), light, 1);
				if (pos == null && landmark.contains(LandmarkComponentTypes.CHUNKS)) {
					renderTerritoryLabel(painter, landmark, AtlasHoldMode.labelAlpha(), light);
				}
				if (landmark.contains(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE)) {
					renderRouteLabel(painter, landmark, AtlasHoldMode.labelAlpha(), light);
				}
			}
		});

		if (spawnMark != null) {
			BlockPos pos = spawnMark.get(LandmarkComponentTypes.POS);
			double markerX = pos == null ? Double.NaN : worldXToScreenX(pos.getX()) - bookX;
			if (pos != null && markerX >= clipMinX && markerX < clipMaxX) {
				renderMarker(painter, spawnMark, spawnTexture, -0.3F, light, (x, y) -> (float) MathHelper.clamp(MathUtil.innerDistanceToEdge(mapArea, new Vector2d(x, y)) / 32.0, 0, 1), false, false, 1);
				if (fullPass) renderMarkerLabel(painter, spawnMark, AtlasHoldMode.labelAlpha(), light, 1);
			}
		}

		// Player arrows fade in only after the book fully opens – drawn during
		// the page swing they poke through the moving flap.
		float arrowAlpha = AtlasHoldMode.labelAlpha();
		if (fullPass && arrowAlpha > 0.01F) {
			RoleplayersAtlas.getOrderedFriends().forEach((uuid, friend) -> {
				// z -0.85: closer than every text layer (plates -0.5, glyphs down
				// to -0.7), so the arrow never flickers under labels.
				renderPlayer(painter, -0.85F, light, friend, 1, arrowAlpha, false, uuid.equals(SurveyorClient.getClientUuid()));
			});
		}

		if (fullPass) {
			renderGuideArrows(painter, -0.65F, light, AtlasHoldMode.labelAlpha());
		}
	}

	@Override
	public boolean clipsMarkersToPage() {
		return true;
	}

	@Override
	public double getPixelsPerBlock() {
		return 1;
	}
}
