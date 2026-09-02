package glam.ardor.roleplayers_atlas.gui;

import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.MarkerTexture;
import glam.ardor.roleplayers_atlas.TileTexture;
import glam.ardor.roleplayers_atlas.WorldAtlasData;
import glam.ardor.roleplayers_atlas.gui.core.ScreenState;
import glam.ardor.roleplayers_atlas.gui.tiles.SubTile;
import glam.ardor.roleplayers_atlas.gui.tiles.SubTileQuartet;
import glam.ardor.roleplayers_atlas.gui.tiles.TileRenderIterator;
import glam.ardor.roleplayers_atlas.util.ColorUtil;
import glam.ardor.roleplayers_atlas.util.AtlasPainter;
import glam.ardor.roleplayers_atlas.util.DrawBatcher;
import glam.ardor.roleplayers_atlas.util.DrawUtil;
import glam.ardor.roleplayers_atlas.util.MathUtil;
import glam.ardor.roleplayers_atlas.util.Rect;
import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.RegionPos;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public interface AtlasRenderer {
	Map<Identifier, AtlasOverlay> overlays = new HashMap<>();

	static void registerOverlay(Identifier id, AtlasOverlay overlay) {
		overlays.put(id, overlay);
	}

	Identifier BOOK = RoleplayersAtlas.id("textures/gui/book.png");
	Identifier BOOK_FULLSCREEN = RoleplayersAtlas.id("book_fullscreen");
	Identifier BOOK_FULLSCREEN_M = RoleplayersAtlas.id("middle/book_fullscreen_m");
	Identifier BOOK_FULLSCREEN_R = RoleplayersAtlas.id("book_fullscreen_r");
	Identifier BOOK_FRAME = RoleplayersAtlas.id("textures/gui/book_frame.png");
	Identifier BOOK_FRAME_FULLSCREEN = RoleplayersAtlas.id("book_frame_fullscreen");
	Identifier BOOK_FRAME_FULLSCREEN_M = RoleplayersAtlas.id("middle/book_frame_fullscreen_m");
	Identifier BOOK_FRAME_FULLSCREEN_R = RoleplayersAtlas.id("book_frame_fullscreen_r");
	Identifier BOOK_FRAME_NARROW = RoleplayersAtlas.id("textures/gui/book_frame_narrow.png");
	Identifier BOOK_FRAME_NARROW_FULLSCREEN = RoleplayersAtlas.id("book_frame_narrow_fullscreen");
	Identifier BOOK_FRAME_NARROW_FULLSCREEN_M = RoleplayersAtlas.id("middle/book_frame_narrow_fullscreen_m");
	Identifier BOOK_FRAME_NARROW_FULLSCREEN_R = RoleplayersAtlas.id("book_frame_narrow_fullscreen_r");
	Identifier PLAYER = RoleplayersAtlas.id("textures/gui/player.png");
	Identifier ERASER = RoleplayersAtlas.id("textures/gui/eraser.png");
	Identifier ICON_ADD_MARKER = RoleplayersAtlas.id("textures/gui/icons/add_marker.png");
	Identifier ICON_ADD_TERRITORY = RoleplayersAtlas.id("textures/gui/icons/add_territory.png");
	Identifier ICON_ADD_LABEL = RoleplayersAtlas.id("textures/gui/icons/add_label.png");
	Identifier ICON_ADD_ROUTE = RoleplayersAtlas.id("textures/gui/icons/add_route.png");
	Identifier GUIDE_ARROW = RoleplayersAtlas.id("textures/gui/guide_arrow.png");
	Identifier ICON_CLEAR_TRACKING = RoleplayersAtlas.id("textures/gui/icons/clear_tracking.png");
	Identifier ICON_SHARE_MAP = RoleplayersAtlas.id("textures/gui/icons/share_map.png");
	Identifier ICON_SCREENSHOT = RoleplayersAtlas.id("textures/gui/icons/screenshot_map.png");
	Identifier ICON_SEARCH = RoleplayersAtlas.id("textures/gui/icons/search.png");
	Identifier ICON_SORT = RoleplayersAtlas.id("textures/gui/icons/sort.png");
	Identifier ICON_DELETE_MARKER = RoleplayersAtlas.id("textures/gui/icons/del_marker.png");
	Identifier ICON_SHOW_MARKERS = RoleplayersAtlas.id("textures/gui/icons/show_markers.png");
	Identifier ICON_HIDE_MARKERS = RoleplayersAtlas.id("textures/gui/icons/hide_markers.png");
	Identifier ICON_EDIT_BIOMES = RoleplayersAtlas.id("textures/gui/icons/edit_biomes.png");
	Identifier ICON_PAINT_CITY = RoleplayersAtlas.id("textures/gui/icons/paint_city.png");
	Identifier ICON_UNKNOWN = RoleplayersAtlas.id("textures/gui/icons/unknown.png");
	Text TEXT_ADD_MARKER = Text.translatable("gui.roleplayers_atlas.addMarker");
	Text TEXT_ADD_MARKER_HERE = Text.translatable("gui.roleplayers_atlas.addMarkerHere");
	Text TEXT_ADD_TERRITORY = Text.translatable("gui.roleplayers_atlas.addTerritory");
	Text TEXT_ADD_LABEL = Text.translatable("gui.roleplayers_atlas.addLabel");
	Text TEXT_ADD_ROUTE = Text.translatable("gui.roleplayers_atlas.addRoute");

	int DEFAULT_BOOK_WIDTH = 310;
	int DEFAULT_BOOK_HEIGHT = 218;
	int MAP_BORDER_WIDTH = 17;
	int MAP_BORDER_HEIGHT = 11;
	float PLAYER_ROTATION_STEPS = 16;
	int PLAYER_ICON_WIDTH = 7;
	int PLAYER_ICON_HEIGHT = 8;
	int BOOKMARK_SPACING = 2;
	int MARKER_SIZE = 32;
	int NAVIGATE_STEP = 24; // How much the map view is offset, in blocks, per click (or per tick).
	int MAX_LIGHT = 0xF000F0;

	ScreenState.State<AtlasScreen> NORMAL = new ScreenState.ToggleState<>();
	ScreenState.State<AtlasScreen> PLACING_MARKER = new ScreenState.ToggleState<>(s -> s.addMarkerBookmark);
	ScreenState.State<AtlasScreen> PLACING_TERRITORY = new ScreenState.ToggleState<>(s -> s.addTerritoryBookmark);
	ScreenState.State<AtlasScreen> PLACING_LABEL = new ScreenState.ToggleState<>(s -> s.addLabelBookmark);
	ScreenState.State<AtlasScreen> PLACING_ROUTE = new ScreenState.ToggleState<>(s -> s.addRouteBookmark);
	ScreenState.State<AtlasScreen> DELETING_MARKER = new ScreenState.ToggleState<>(s -> s.deleteMarkerBookmark, s -> s.addChild(s.eraser), s -> s.removeChild(s.eraser));
	/** Waiting for a click on the map to say which biome the player means. */
	ScreenState.State<AtlasScreen> PICKING_BIOME = new ScreenState.ToggleState<>(s -> s.editBiomesBookmark);
	/** Marking out the patch of map to repaint, before choosing what to paint it as. */
	ScreenState.State<AtlasScreen> PAINTING_BIOME = new ScreenState.ToggleState<>(s -> s.editBiomesBookmark);
	/** Drawing a town by hand, cell by cell. */
	ScreenState.State<AtlasScreen> PAINTING_CITY = new ScreenState.ToggleState<>(s -> s.paintCityBookmark);
	ScreenState.State<AtlasScreen> HIDING_MARKERS = new ScreenState.ToggleState<>(s -> s.markerVisibilityBookmark, s -> {
		s.markerVisibilityBookmark.setTitle(Text.translatable("gui.roleplayers_atlas.showMarkers"));
		s.markerVisibilityBookmark.setIconTexture(ICON_SHOW_MARKERS);
	}, s -> {
		s.clearTargetBookmarks(s.playerBookmark);
		s.markerVisibilityBookmark.setTitle(Text.translatable("gui.roleplayers_atlas.hideMarkers"));
		s.markerVisibilityBookmark.setIconTexture(ICON_HIDE_MARKERS);
	});

	int bookX();

	int bookY();

	int bookWidth();

	int bookHeight();

	int mapWidth();

	int mapHeight();

	double mapOffsetX();

	double mapOffsetY();

	int tilePixels();

	int tileChunks();

	int mapScale();

	PlayerEntity player();

	WorldAtlasData worldAtlasData();

	double getPixelsPerBlock();

	double guiScale();

	RegistryKey<World> dim();

	default int screenXToWorldX(double screenX) {
		return screenXToWorldX(screenX, bookX(), mapOffsetX(), mapWidth(), getPixelsPerBlock());
	}

	default int screenYToWorldZ(double screenY) {
		return screenYToWorldZ(screenY, bookY(), mapOffsetY(), mapHeight(), getPixelsPerBlock());
	}

	default double worldXToScreenX(double x) {
		return worldXToScreenX(x, bookX(), mapOffsetX(), mapWidth(), getPixelsPerBlock());
	}

	default double worldZToScreenY(double z) {
		return worldZToScreenY(z, bookY(), mapOffsetY(), mapHeight(), getPixelsPerBlock());
	}

	static int screenXToWorldX(double screenX, int bookX, double mapOffsetX, int mapWidth, double pixelsPerBlock) {
		double mapX = (int) Math.round(screenX - bookX - MAP_BORDER_WIDTH);
		return (int) Math.round((mapX - (mapWidth / 2f) - mapOffsetX) / pixelsPerBlock);
	}

	static int screenYToWorldZ(double screenY, int bookY, double mapOffsetY, int mapHeight, double pixelsPerBlock) {
		double mapY = (int) Math.round(screenY - bookY - MAP_BORDER_HEIGHT);
		return (int) Math.round((mapY - (mapHeight / 2f) - mapOffsetY) / pixelsPerBlock);
	}

	static double worldXToScreenX(double x, int bookX, double mapOffsetX, int mapWidth, double pixelsPerBlock) {
		double mapX = x * pixelsPerBlock + mapOffsetX + (mapWidth / 2f);
		return mapX + bookX + MAP_BORDER_WIDTH;
	}

	static double worldZToScreenY(double z, int bookY, double mapOffsetY, int mapHeight, double pixelsPerBlock) {
		double mapY = z * pixelsPerBlock + mapOffsetY + (mapHeight / 2f);
		return mapY + bookY + MAP_BORDER_HEIGHT;
	}

	default float markerOpacity(Landmark landmark) {
		Integer opacity = landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.OPACITY);
		float alpha = opacity == null ? 1.0F : MathHelper.clamp(opacity, 0, 100) / 100.0F;
		// Marks copied from someone else's scroll are drawn faint until their
		// owner has actually stood there: known by hearsay, not by having gone.
		if (glam.ardor.roleplayers_atlas.AtlasTime.isUnverified(landmark)) alpha *= 0.5F;
		return alpha;
	}

	/** Whether territory chunk quads should be geometrically clipped to the map area (the handheld book has no scissor). */
	default boolean clipsMarkersToPage() {
		return false;
	}

	private void fillClipped(AtlasPainter painter, float z, int light, int x1, int y1, int x2, int y2, int clipX1, int clipY1, int clipX2, int clipY2, float alpha, float[] color) {
		x1 = Math.max(x1, clipX1);
		y1 = Math.max(y1, clipY1);
		x2 = Math.min(x2, clipX2);
		y2 = Math.min(y2, clipY2);
		if (x2 > x1 && y2 > y1) DrawUtil.fill(painter, RenderLayer.getTextBackgroundSeeThrough(), z, light, x1, y1, x2, y2, alpha, color);
	}

	default void renderMarker(AtlasPainter painter, Landmark landmark, MarkerTexture texture, float z, int light, BiFunction<Double, Double, Float> alphaGetter, boolean pinned, boolean hovering, float markerScale) {
		float opacity = markerOpacity(landmark);
		if (opacity <= 0.0F) return;
		BiFunction<Double, Double, Float> baseAlphaGetter = alphaGetter;
		alphaGetter = (x, y) -> baseAlphaGetter.apply(x, y) * opacity;
		BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
		Integer color = landmark.get(LandmarkComponentTypes.COLOR);
		float[] accent = color == null ? null : ColorUtil.componentsFromRgb(color);
		float tint = hovering ? 0.8f : 1.0f;

		if (landmark.contains(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE)) {
			renderRoutePath(painter, landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE), z, light,
				alphaGetter.apply((double) MAP_BORDER_WIDTH + mapWidth() / 2.0, (double) MAP_BORDER_HEIGHT + mapHeight() / 2.0),
				color == null ? 0x4A3421 : color & 0xFFFFFF, false);
			return;
		}

		if (pos == null) {
			Set<ChunkPos> chunks = RegionPos.regionsToChunks(landmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new HashMap<>()));
			boolean clipToPage = clipsMarkersToPage();
			for (ChunkPos chunk : chunks) {
				double markerX = worldXToScreenX(chunk.getStartX()) - bookX();
				double markerY = worldZToScreenY(chunk.getStartZ()) - bookY();
				float effectiveScale = (float) (mapScale() / guiScale());
				painter.push();
				painter.translate(markerX, markerY);
				painter.scale(effectiveScale);
				int size = tilePixels() / tileChunks();
				int lineSize = tilePixels() / 16;
				// Handheld page: no scissor exists in world rendering, so the chunk
				// quads are clipped geometrically to the map area instead of fading
				// out early — territories run right up to the page border.
				int clipX1 = 0, clipY1 = 0, clipX2 = size, clipY2 = size;
				if (clipToPage) {
					clipX1 = Math.max(0, (int) Math.ceil((MAP_BORDER_WIDTH - markerX) / effectiveScale));
					clipY1 = Math.max(0, (int) Math.ceil((MAP_BORDER_HEIGHT - markerY) / effectiveScale));
					clipX2 = Math.min(size, (int) Math.floor((MAP_BORDER_WIDTH + mapWidth() - markerX) / effectiveScale));
					clipY2 = Math.min(size, (int) Math.floor((MAP_BORDER_HEIGHT + mapHeight() - markerY) / effectiveScale));
				}
				if (size > 0 && clipX2 > clipX1 && clipY2 > clipY1) {
					float[] fillColor = accent == null ? ColorUtil.componentsFromRgb(0xFFFFFF) : new float[] { tint * accent[0], tint * accent[1], tint * accent[2] };
					// With the hard clip the spatial edge fade is dropped (evaluated
					// at the page center) so zones stay opaque up to the border.
					float alpha = clipToPage
						? alphaGetter.apply((double) MAP_BORDER_WIDTH + mapWidth() / 2.0, (double) MAP_BORDER_HEIGHT + mapHeight() / 2.0)
						: alphaGetter.apply(markerX, markerY);
					fillClipped(painter, z, light, 0, 0, size, size, clipX1, clipY1, clipX2, clipY2, 0.25F * alpha, fillColor);
					if (lineSize > 0) {
						if (!chunks.contains(new ChunkPos(chunk.x - 1, chunk.z))) fillClipped(painter, z, light, 0, 0, lineSize, size, clipX1, clipY1, clipX2, clipY2, 0.5F * alpha, fillColor);
						if (!chunks.contains(new ChunkPos(chunk.x, chunk.z - 1))) fillClipped(painter, z, light, 0, 0, size, lineSize, clipX1, clipY1, clipX2, clipY2, 0.5F * alpha, fillColor);
						if (!chunks.contains(new ChunkPos(chunk.x + 1, chunk.z))) fillClipped(painter, z, light, size - lineSize, 0, size, size, clipX1, clipY1, clipX2, clipY2, 0.5F * alpha, fillColor);
						if (!chunks.contains(new ChunkPos(chunk.x, chunk.z + 1))) fillClipped(painter, z, light, 0, size - lineSize, size, size, clipX1, clipY1, clipX2, clipY2, 0.5F * alpha, fillColor);
					}
				}
				painter.pop();
			}
			return;
		}

		// Pen inscriptions are text-only: their name is drawn by renderMarkerLabel.
		if (Boolean.TRUE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.PEN_LABEL))) return;

		double markerX = worldXToScreenX(pos.getX()) - bookX();
		double markerY = worldZToScreenY(pos.getZ()) - bookY();

		if (pinned) {
			markerX = MathHelper.clamp(markerX, MAP_BORDER_WIDTH, mapWidth() + MAP_BORDER_WIDTH);
			markerY = MathHelper.clamp(markerY, MAP_BORDER_HEIGHT, mapHeight() + MAP_BORDER_HEIGHT);
		}

		// Tracked markers glow along their outline: four flat-color silhouette
		// copies in the marker's own color (hue at full brightness), offset
		// around the icon so the glow hugs its actual shape.
		if (RoleplayersAtlas.trackedMarkers.contains(RoleplayersAtlas.trackKey(landmark))) {
			int glowRgb;
			boolean nearWhite = false;
			if (accent == null) {
				glowRgb = 0xE8C878;
			} else {
				float max = Math.max(accent[0], Math.max(accent[1], accent[2]));
				float mul = max > 0.01F ? 1.0F / max : 1.0F;
				float r = Math.min(1, accent[0] * mul);
				float g = Math.min(1, accent[1] * mul);
				float b = Math.min(1, accent[2] * mul);
				// White markers: pure white at full opacity, or the glow melts
				// into the parchment.
				nearWhite = Math.min(r, Math.min(g, b)) > 0.75F;
				glowRgb = nearWhite ? 0xFFFFFF : ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
			}
			int glowArgb = ((int) (alphaGetter.apply(markerX, markerY) * (nearWhite ? 1.0F : 0.9F) * 255) << 24) | glowRgb;
			for (int i = 0; i < 4; i++) {
				double ox = (i == 0 ? 1.5 : i == 1 ? -1.5 : 0) * markerScale;
				double oy = (i == 2 ? 1.5 : i == 3 ? -1.5 : 0) * markerScale;
				texture.drawSilhouette(painter, markerX + ox, markerY + oy, z + 0.03F, markerScale, tileChunks(), glowArgb, light);
			}
		}

		texture.draw(painter, markerX, markerY, z, markerScale, tileChunks(), accent, tint, alphaGetter.apply(markerX, markerY), light);
	}

	/**
	 * Draws the name of a player-created marker beneath its icon on the map
	 * itself. Structure markers stay label-free to avoid clutter.
	 */
	default void renderMarkerLabel(AtlasPainter painter, Landmark landmark, float labelAlpha, int light, float markerScale) {
		if (landmark.contains(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE)) return; // route names are written along the path
		if (landmark.owner() == null || landmark.owner().equals(folk.sisby.surveyor.landmark.WorldLandmarks.GLOBAL)) return;
		if (Boolean.TRUE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.HIDE_LABEL))) return;
		labelAlpha *= markerOpacity(landmark);
		if (labelAlpha <= 0.05F) return;
		Text name = landmark.get(LandmarkComponentTypes.NAME);
		if (name == null || name.getString().isEmpty()) return;
		BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
		if (pos == null) return;
		double markerX = worldXToScreenX(pos.getX()) - bookX();
		double markerY = worldZToScreenY(pos.getZ()) - bookY();
		net.minecraft.client.font.TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		if (Boolean.TRUE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.PEN_LABEL))) {
			// A pen inscription: italic text centered on its position, no icon,
			// on the same translucent plate as marker name labels. The ink takes
			// the marker's color.
			Text styled = name.copy().formatted(net.minecraft.util.Formatting.ITALIC);
			double half = textRenderer.getWidth(styled) / 2.0;
			if (markerX - half < MAP_BORDER_WIDTH || markerX + half > MAP_BORDER_WIDTH + mapWidth()
				|| markerY - 4 < MAP_BORDER_HEIGHT || markerY + 5 > MAP_BORDER_HEIGHT + mapHeight()) return;
			Integer color = landmark.get(LandmarkComponentTypes.COLOR);
			int ink = color == null ? 0xF8ECD0 : color & 0xFFFFFF;
			int penArgb = ((int) (labelAlpha * 255) << 24) | ink;
			boolean penNoPlate = Boolean.TRUE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.LABEL_NO_SHADOW));
			int penRot = landmark.getOrDefault(glam.ardor.roleplayers_atlas.AtlasComponents.LABEL_ROTATION, 0);
			painter.push();
			painter.translate(markerX, markerY - 4);
			if (penRot != 0) painter.rotateDegrees(penRot);
			// No plate means bare ink (drawGlyph with a zero shadow); otherwise the
			// usual translucent name-tag plate.
			if (penNoPlate) painter.drawGlyph(styled, (float) -half, 0, penArgb, 0, light);
			else painter.drawText(styled, (float) -half, 0, penArgb, light);
			painter.pop();
			return;
		}
		double labelHalf = textRenderer.getWidth(name) / 2.0;
		double labelY = markerY + 14 * markerScale;
		// Only draw the label while it fits entirely on the page.
		if (markerX - labelHalf < MAP_BORDER_WIDTH || markerX + labelHalf > MAP_BORDER_WIDTH + mapWidth()
			|| labelY < MAP_BORDER_HEIGHT || labelY + 9 > MAP_BORDER_HEIGHT + mapHeight()) return;
		int argb = ((int) (labelAlpha * 255) << 24) | 0xF8ECD0;
		if (Boolean.TRUE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.LABEL_NO_SHADOW)))
			painter.drawGlyph(name, (float) (markerX - labelHalf), (float) labelY, argb, 0, light);
		else painter.drawText(name, (float) (markerX - labelHalf), (float) labelY, argb, light);
	}

	/**
	 * Draws a territory's name across its area like on antique maps: letters
	 * spread along the territory's principal axis on a gentle arc, scaled to
	 * the territory's extent.
	 */
	default void renderTerritoryLabel(AtlasPainter painter, Landmark landmark, float labelAlpha, int light) {
		if (landmark.owner() == null || landmark.owner().equals(folk.sisby.surveyor.landmark.WorldLandmarks.GLOBAL)) return;
		if (Boolean.TRUE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.HIDE_LABEL))) return;
		labelAlpha *= markerOpacity(landmark);
		if (labelAlpha <= 0.05F) return;
		Text name = landmark.get(LandmarkComponentTypes.NAME);
		if (name == null || name.getString().isEmpty()) return;
		Set<ChunkPos> chunks = RegionPos.regionsToChunks(landmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new HashMap<>()));
		if (chunks.isEmpty()) return;

		// Mean and covariance of chunk centers in book space.
		double meanX = 0, meanY = 0;
		for (ChunkPos chunk : chunks) {
			meanX += worldXToScreenX(chunk.getStartX() + 8) - bookX();
			meanY += worldZToScreenY(chunk.getStartZ() + 8) - bookY();
		}
		meanX /= chunks.size();
		meanY /= chunks.size();
		double sxx = 0, sxy = 0, syy = 0;
		for (ChunkPos chunk : chunks) {
			double dx = worldXToScreenX(chunk.getStartX() + 8) - bookX() - meanX;
			double dy = worldZToScreenY(chunk.getStartZ() + 8) - bookY() - meanY;
			sxx += dx * dx;
			sxy += dx * dy;
			syy += dy * dy;
		}
		double theta = chunks.size() == 1 ? 0 : 0.5 * Math.atan2(2 * sxy, sxx - syy);
		double deg = Math.toDegrees(theta);
		if (deg > 90) deg -= 180;
		if (deg < -90) deg += 180;
		theta = Math.toRadians(deg);
		double cos = Math.cos(theta), sin = Math.sin(theta);

		// Extent along the axis (plus one chunk of slack for single-row shapes).
		double tMin = Double.MAX_VALUE, tMax = -Double.MAX_VALUE;
		for (ChunkPos chunk : chunks) {
			double dx = worldXToScreenX(chunk.getStartX() + 8) - bookX() - meanX;
			double dy = worldZToScreenY(chunk.getStartZ() + 8) - bookY() - meanY;
			double t = dx * cos + dy * sin;
			tMin = Math.min(tMin, t);
			tMax = Math.max(tMax, t);
		}
		double chunkPixels = worldXToScreenX(16) - worldXToScreenX(0);
		double span = tMax - tMin + chunkPixels;
		if (span < 44) return;

		String text = name.getString().toUpperCase(java.util.Locale.ROOT);
		net.minecraft.client.font.TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		int count = text.length();
		if (count == 0) return;
		// Ink picked against the territory fill for contrast, with an
		// opposite-tone shadow (set via the text style, so it is still a single
		// text pass — no glyph z-fighting) instead of a backing plate.
		Integer territoryColor = landmark.get(LandmarkComponentTypes.COLOR);
		int fillRgb = territoryColor == null ? 0xFFFFFF : territoryColor;
		double luma = 0.299 * ((fillRgb >> 16) & 0xFF) + 0.587 * ((fillRgb >> 8) & 0xFF) + 0.114 * (fillRgb & 0xFF);
		boolean darkInk = luma > 110;
		int inkColor = darkInk ? 0x2E1A0C : 0xF8ECD0;
		boolean noShadow = Boolean.TRUE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.LABEL_NO_SHADOW));
		int shadowArgb = noShadow ? 0 : ((int) (labelAlpha * 200) << 24) | (darkInk ? 0xF3E7C9 : 0x2E1A0C);
		// A per-mark twist rides on top of the auto-computed axis: the extent and
		// scale are still measured along the principal axis, so only the finished
		// label spins about its centre.
		int userRot = landmark.getOrDefault(glam.ardor.roleplayers_atlas.AtlasComponents.LABEL_ROTATION, 0);
		double drawDeg = deg + userRot;
		double drawTheta = Math.toRadians(drawDeg);
		double dcos = Math.cos(drawTheta), dsin = Math.sin(drawTheta);
		Text[] glyphs = new Text[count];
		int[] glyphWidths = new int[count];
		int rawWidth = 0;
		for (int i = 0; i < count; i++) {
			glyphs[i] = Text.literal(String.valueOf(text.charAt(i))).formatted(net.minecraft.util.Formatting.BOLD);
			glyphWidths[i] = textRenderer.getWidth(glyphs[i]);
			rawWidth += glyphWidths[i];
		}
		if (rawWidth == 0) return;
		double target = span * 0.72;
		double scale = MathHelper.clamp(target / (rawWidth + 2.0 * (count - 1)), 0.85, 2.6);
		double spacing = count > 1 ? MathHelper.clamp((target - rawWidth * scale) / (count - 1), 0, 8 * scale) : 0;
		double total = rawWidth * scale + spacing * (count - 1);
		double sagitta = MathHelper.clamp(span * 0.07, 2, 14);

		int argb = ((int) (labelAlpha * 235) << 24) | inkColor;
		double cursor = -total / 2;
		for (int i = 0; i < count; i++) {
			double charWidth = glyphWidths[i] * scale;
			double t = cursor + charWidth / 2;
			double u = total > 0 ? t / (total / 2) : 0;
			double arcOffset = -sagitta * (1 - u * u);
			double tilt = Math.toDegrees(Math.atan2(2 * sagitta * u / (total / 2), 1));
			double px = meanX + dcos * t - dsin * arcOffset;
			double py = meanY + dsin * t + dcos * arcOffset;
			cursor += charWidth + spacing;
			if (px < MAP_BORDER_WIDTH || px > MAP_BORDER_WIDTH + mapWidth() || py < MAP_BORDER_HEIGHT || py > MAP_BORDER_HEIGHT + mapHeight()) continue;
			painter.push();
			painter.translate(px, py);
			painter.rotateDegrees((float) (drawDeg + tilt));
			painter.scale((float) scale);
			painter.drawGlyph(glyphs[i], -glyphWidths[i] / 2.0F, -4, argb, shadowArgb, light);
			painter.pop();
		}
	}

	/**
	 * Samples a route's control points into a smooth polyline (Catmull-Rom
	 * through the points), in book-space pixels.
	 */
	private java.util.List<double[]> sampleRoute(java.util.List<BlockPos> points, boolean straight) {
		int count = points.size();
		if (count == 0) return new java.util.ArrayList<>();
		double[][] screen = new double[count][2];
		for (int i = 0; i < count; i++) {
			screen[i][0] = worldXToScreenX(points.get(i).getX() + 0.5) - bookX();
			screen[i][1] = worldZToScreenY(points.get(i).getZ() + 0.5) - bookY();
		}
		return glam.ardor.roleplayers_atlas.util.RouteUtil.sample(screen, straight);
	}

	/** Dashed hand-drawn path through the route's points, clipped to the page. */
	default void renderRoutePath(AtlasPainter painter, java.util.List<BlockPos> points, float z, int light, float alpha, int rgb, boolean straight) {
		if (points == null || points.size() < 2 || alpha <= 0.03F) return;
		java.util.List<double[]> samples = sampleRoute(points, straight);
		double dash = MathHelper.clamp(6 * getPixelsPerBlock(), 3, 18);
		double gap = dash * 0.7;
		int th = (int) MathHelper.clamp(Math.round(2 * getPixelsPerBlock()), 1, 4);
		int y1 = -(th + 1) / 2;
		float[] colorF = ColorUtil.componentsFromRgb(rgb);
		double phase = 0;
		int dashGuard = 0;
		for (int i = 0; i < samples.size() - 1; i++) {
			double[] a = samples.get(i);
			double[] b = samples.get(i + 1);
			double dx = b[0] - a[0];
			double dy = b[1] - a[1];
			double segLen = Math.hypot(dx, dy);
			if (segLen < 0.01) continue;

			// Only the on-page part of the segment is walked; off-page spans are
			// skipped wholesale — a zoomed-in route otherwise costs tens of
			// thousands of iterations per frame and hangs the render thread.
			double tEnter = 0;
			double tExit = 1;
			boolean visible = true;
			double[] p = {-dx, dx, -dy, dy};
			double[] q = {a[0] - (MAP_BORDER_WIDTH - 2), (mapWidth() + MAP_BORDER_WIDTH + 2) - a[0], a[1] - (MAP_BORDER_HEIGHT - 2), (mapHeight() + MAP_BORDER_HEIGHT + 2) - a[1]};
			for (int k = 0; k < 4; k++) {
				if (Math.abs(p[k]) < 1.0E-9) {
					if (q[k] < 0) {
						visible = false;
						break;
					}
				} else {
					double r = q[k] / p[k];
					if (p[k] < 0) tEnter = Math.max(tEnter, r);
					else tExit = Math.min(tExit, r);
				}
			}
			if (!visible || tEnter > tExit) {
				phase = (phase + segLen) % (dash + gap);
				continue;
			}

			float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
			double pos = tEnter * segLen;
			double end = tExit * segLen;
			while (pos < end - 0.01 && dashGuard < 4000) {
				double cycle = (phase + pos) % (dash + gap);
				double advance;
				if (cycle < dash) {
					advance = Math.min(dash - cycle, end - pos);
					double sx = a[0] + dx / segLen * pos;
					double sy = a[1] + dy / segLen * pos;
					painter.push();
					painter.translate(sx, sy);
					painter.rotateDegrees(angle);
					DrawUtil.fill(painter, RenderLayer.getTextBackgroundSeeThrough(), z, light, 0, y1, (int) Math.max(1, Math.round(advance)), y1 + th, alpha * 0.9F, colorF);
					painter.pop();
					dashGuard++;
				} else {
					advance = Math.min((dash + gap) - cycle, end - pos);
				}
				pos += Math.max(advance, 0.05);
			}
			phase = (phase + segLen) % (dash + gap);
		}
	}

	/** The route's name written glyph-by-glyph along the path, centered on its length. */
	default void renderRouteLabel(AtlasPainter painter, Landmark landmark, float labelAlpha, int light) {
		if (landmark.owner() == null || landmark.owner().equals(folk.sisby.surveyor.landmark.WorldLandmarks.GLOBAL)) return;
		if (Boolean.TRUE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.HIDE_LABEL))) return;
		labelAlpha *= markerOpacity(landmark);
		if (labelAlpha <= 0.05F) return;
		Text name = landmark.get(LandmarkComponentTypes.NAME);
		if (name == null || name.getString().isEmpty()) return;
		java.util.List<BlockPos> points = landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE);
		if (points == null || points.size() < 2) return;

		java.util.List<double[]> samples = sampleRoute(points, false);
		if (samples.size() < 2) return;
		double[] lens = new double[samples.size()];
		double total = 0;
		for (int i = 1; i < samples.size(); i++) {
			total += Math.hypot(samples.get(i)[0] - samples.get(i - 1)[0], samples.get(i)[1] - samples.get(i - 1)[1]);
			lens[i] = total;
		}

		net.minecraft.client.font.TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		String text = name.getString();
		int count = text.length();
		Text[] glyphs = new Text[count];
		int[] widths = new int[count];
		int textWidth = 0;
		for (int i = 0; i < count; i++) {
			glyphs[i] = Text.literal(String.valueOf(text.charAt(i))).formatted(net.minecraft.util.Formatting.ITALIC);
			widths[i] = textRenderer.getWidth(glyphs[i]);
			textWidth += widths[i];
		}
		if (textWidth == 0 || total < textWidth + 24) return;

		// Keep the text readable left-to-right: if the path runs leftwards at
		// its middle, walk the glyphs backwards and flip them.
		double mid = total / 2;
		int midIndex = 1;
		while (midIndex < samples.size() - 1 && lens[midIndex] < mid) midIndex++;
		boolean reversed = samples.get(midIndex)[0] - samples.get(midIndex - 1)[0] < 0;

		Integer color = landmark.get(LandmarkComponentTypes.COLOR);
		int ink = color == null ? 0xF8ECD0 : color & 0xFFFFFF;
		// Dark inks get lifted so they stay readable on the dark plate.
		double inkLuma = 0.299 * ((ink >> 16) & 0xFF) + 0.587 * ((ink >> 8) & 0xFF) + 0.114 * (ink & 0xFF);
		if (inkLuma < 90) {
			float max = Math.max(1, Math.max((ink >> 16) & 0xFF, Math.max((ink >> 8) & 0xFF, ink & 0xFF)));
			float mul = 200 / max;
			ink = ((int) Math.min(255, ((ink >> 16) & 0xFF) * mul) << 16) | ((int) Math.min(255, ((ink >> 8) & 0xFF) * mul) << 8) | (int) Math.min(255, (ink & 0xFF) * mul);
		}
		// No shadow: plain ink on the dark plate, or the two blur together.
		int shadowArgb = 0;
		int argb = ((int) (labelAlpha * 235) << 24) | ink;
		// The route's "shadow" is the dark ribbon behind its name; hiding it
		// leaves the name as bare ink laid along the path.
		boolean routeNoPlate = Boolean.TRUE.equals(landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.LABEL_NO_SHADOW));

		// The dark plate is one continuous ribbon: a plate per glyph, or a strip
		// of rectangles, leaves a wedge open on every bend. Each piece is a quad
		// whose far corners are the near corners of the next one, so the ribbon
		// has no seams at all no matter how sharply the route turns.
		double plateNear = reversed ? 1 : -12;
		double plateFar = reversed ? 12 : -1;
		double start = MathHelper.clamp((total - textWidth) / 2 - 2, 0, total);
		double end = MathHelper.clamp(start + textWidth + 4, 0, total);
		java.util.List<double[]> ribbon = new java.util.ArrayList<>();
		int rseg = 1;
		for (int i = 0; i < samples.size() + 1; i++) {
			double s = i == 0 ? start : i == samples.size() ? end : lens[i - 1];
			if (s <= start && i != 0) continue;
			if (s >= end && i != samples.size()) continue;
			while (rseg < samples.size() - 1 && lens[rseg] < s) rseg++;
			double segStart = lens[rseg - 1];
			double segLen = lens[rseg] - segStart;
			double t = segLen < 0.001 ? 0 : (s - segStart) / segLen;
			double x = samples.get(rseg - 1)[0] + (samples.get(rseg)[0] - samples.get(rseg - 1)[0]) * t;
			double y = samples.get(rseg - 1)[1] + (samples.get(rseg)[1] - samples.get(rseg - 1)[1]) * t;
			if (!ribbon.isEmpty()) {
				double[] last = ribbon.getLast();
				if (Math.hypot(x - last[0], y - last[1]) < 0.05) continue;
			}
			ribbon.add(new double[]{x, y});
		}
		if (!routeNoPlate && ribbon.size() > 1) {
			// The offset at each joint follows the bisector of the two pieces
			// meeting there, which is what makes their corners line up exactly.
			int nodes = ribbon.size();
			double[][] normals = new double[nodes][2];
			for (int k = 0; k < nodes; k++) {
				double tx = 0;
				double ty = 0;
				if (k > 0) {
					double ax = ribbon.get(k)[0] - ribbon.get(k - 1)[0];
					double ay = ribbon.get(k)[1] - ribbon.get(k - 1)[1];
					double la = Math.max(1.0E-6, Math.hypot(ax, ay));
					tx += ax / la;
					ty += ay / la;
				}
				if (k < nodes - 1) {
					double bx = ribbon.get(k + 1)[0] - ribbon.get(k)[0];
					double by = ribbon.get(k + 1)[1] - ribbon.get(k)[1];
					double lb = Math.max(1.0E-6, Math.hypot(bx, by));
					tx += bx / lb;
					ty += by / lb;
				}
				double l = Math.hypot(tx, ty);
				if (l < 1.0E-6) {
					tx = 1;
					ty = 0;
					l = 1;
				}
				normals[k][0] = -ty / l;
				normals[k][1] = tx / l;
			}
			float[] plateColor = {0.125F, 0.078F, 0.035F};
			double[] qx = new double[4];
			double[] qy = new double[4];
			for (int k = 0; k < nodes - 1; k++) {
				// Wound the way the renderers expect (near, far, far, near) —
				// the other way round the quads face away and get culled.
				qx[0] = ribbon.get(k)[0] + normals[k][0] * plateNear;
				qy[0] = ribbon.get(k)[1] + normals[k][1] * plateNear;
				qx[1] = ribbon.get(k)[0] + normals[k][0] * plateFar;
				qy[1] = ribbon.get(k)[1] + normals[k][1] * plateFar;
				qx[2] = ribbon.get(k + 1)[0] + normals[k + 1][0] * plateFar;
				qy[2] = ribbon.get(k + 1)[1] + normals[k + 1][1] * plateFar;
				qx[3] = ribbon.get(k + 1)[0] + normals[k + 1][0] * plateNear;
				qy[3] = ribbon.get(k + 1)[1] + normals[k + 1][1] * plateNear;
				DrawUtil.quadClipped(painter, RenderLayer.getTextBackground(), -0.5F, light, qx, qy, MAP_BORDER_WIDTH, MAP_BORDER_HEIGHT, MAP_BORDER_WIDTH + mapWidth(), MAP_BORDER_HEIGHT + mapHeight(), labelAlpha * 0.45F, plateColor);
			}
		}

		double cursor = (total - textWidth) / 2;
		int seg = 1;
		for (int n = 0; n < count; n++) {
			int i = reversed ? count - 1 - n : n;
			double s = cursor + widths[i] / 2.0;
			cursor += widths[i];
			while (seg < samples.size() - 1 && lens[seg] < s) seg++;
			double segStart = lens[seg - 1];
			double segLen = lens[seg] - segStart;
			double t = segLen < 0.001 ? 0 : (s - segStart) / segLen;
			double px = samples.get(seg - 1)[0] + (samples.get(seg)[0] - samples.get(seg - 1)[0]) * t;
			double py = samples.get(seg - 1)[1] + (samples.get(seg)[1] - samples.get(seg - 1)[1]) * t;
			double angle = Math.toDegrees(Math.atan2(samples.get(seg)[1] - samples.get(seg - 1)[1], samples.get(seg)[0] - samples.get(seg - 1)[0]));
			if (reversed) angle += 180;
			if (px < MAP_BORDER_WIDTH || px > MAP_BORDER_WIDTH + mapWidth() || py < MAP_BORDER_HEIGHT || py > MAP_BORDER_HEIGHT + mapHeight()) continue;
			painter.push();
			painter.translate(px, py);
			painter.rotateDegrees((float) angle);
			painter.drawGlyph(glyphs[i], -widths[i] / 2.0F, -11, argb, shadowArgb, light);
			painter.pop();
		}
	}


	/**
	 * Guide arrows for tracked landmarks: drawn at the player's spot on the map,
	 * each pointing towards its landmark in the landmark's color, with the
	 * distance in blocks underneath.
	 */
	default void renderGuideArrows(AtlasPainter painter, float z, int light, float alpha) {
		renderGuideArrows(painter, z, light, alpha, 1.0F);
	}

	default void renderGuideArrows(AtlasPainter painter, float z, int light, float alpha, float scale) {
		alpha *= RoleplayersAtlas.CONFIG.guideArrowOpacity / 100.0F;
		if (RoleplayersAtlas.trackedMarkers.isEmpty() || alpha <= 0.05F) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !dim().equals(client.player.getWorld().getRegistryKey())) return;
		double px = client.player.getX();
		double pz = client.player.getZ();
		double originX = MathHelper.clamp(worldXToScreenX(px) - bookX(), MAP_BORDER_WIDTH + 10, mapWidth() + MAP_BORDER_WIDTH - 10);
		double originY = MathHelper.clamp(worldZToScreenY(pz) - bookY(), MAP_BORDER_HEIGHT + 10, mapHeight() + MAP_BORDER_HEIGHT - 10);
		net.minecraft.client.font.TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		// Placed arrow positions, so arrows sharing a bearing stack along the
		// line towards the player instead of overlapping.
		java.util.List<double[]> placed = new java.util.ArrayList<>();
		double separation = Math.max(22 * scale, 38);

		for (Map.Entry<Landmark, MarkerTexture> entry : worldAtlasData().getAllMarkers(tileChunks()).entrySet()) {
			Landmark landmark = entry.getKey();
			if (!RoleplayersAtlas.trackedMarkers.contains(RoleplayersAtlas.trackKey(landmark))) continue;
			if (!RoleplayersAtlas.layerVisible(landmark)) continue;
			BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
			double tx, tz;
			if (landmark.contains(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE)) {
				// Routes: aim at the nearest control point of the path.
				java.util.List<BlockPos> routePoints = landmark.get(glam.ardor.roleplayers_atlas.AtlasComponents.ROUTE);
				if (routePoints == null || routePoints.isEmpty()) continue;
				double bestSq = Double.MAX_VALUE;
				tx = tz = 0;
				for (BlockPos point : routePoints) {
					double ddx = point.getX() + 0.5 - px;
					double ddz = point.getZ() + 0.5 - pz;
					double sq = ddx * ddx + ddz * ddz;
					if (sq < bestSq) {
						bestSq = sq;
						tx = point.getX() + 0.5;
						tz = point.getZ() + 0.5;
					}
				}
			} else if (pos != null) {
				tx = pos.getX() + 0.5;
				tz = pos.getZ() + 0.5;
			} else {
				var regions = landmark.get(LandmarkComponentTypes.CHUNKS);
				if (regions == null) continue;
				net.minecraft.util.math.ColumnPos center = glam.ardor.roleplayers_atlas.util.TerritoryUtil.centroid(regions);
				tx = center.x();
				tz = center.z();
			}
			double dx = tx - px;
			double dz = tz - pz;
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist < 2) continue;

			// The arrow rides the player→landmark line: out at the page edge
			// while the landmark is far, right next to it once it is close.
			double targetX = worldXToScreenX(tx) - bookX();
			double targetY = worldZToScreenY(tz) - bookY();
			double dirX = targetX - originX;
			double dirY = targetY - originY;
			double len = Math.sqrt(dirX * dirX + dirY * dirY);
			if (len < 0.5) continue;
			double nx = dirX / len;
			double ny = dirY / len;
			double margin = 10;
			double tEdge = Double.MAX_VALUE;
			if (nx > 1.0E-6) tEdge = Math.min(tEdge, (mapWidth() + MAP_BORDER_WIDTH - margin - originX) / nx);
			if (nx < -1.0E-6) tEdge = Math.min(tEdge, (MAP_BORDER_WIDTH + margin - originX) / nx);
			if (ny > 1.0E-6) tEdge = Math.min(tEdge, (mapHeight() + MAP_BORDER_HEIGHT - margin - originY) / ny);
			if (ny < -1.0E-6) tEdge = Math.min(tEdge, (MAP_BORDER_HEIGHT + margin - originY) / ny);
			if (tEdge == Double.MAX_VALUE) tEdge = 0;
			double along = Math.max(0, Math.min(len - 11 * scale, tEdge));
			double baseX = originX + nx * along;
			double baseY = originY + ny * along;
			// Find a free spot: the base position first, then rings of
			// candidates stepping inward along the bearing and sideways
			// (perpendicular), taking the first that overlaps nothing.
			double perpX = -ny;
			double perpY = nx;
			double ax = baseX;
			double ay = baseY;
			boolean found = placed.isEmpty();
			for (int ring = 0; ring < 10 && !found; ring++) {
				double inward = Math.max(0, along - ring * separation);
				double[][] candidates = ring == 0
					? new double[][]{{baseX, baseY}}
					: new double[][]{
					{originX + nx * inward, originY + ny * inward},
					{baseX + perpX * ring * separation, baseY + perpY * ring * separation},
					{baseX - perpX * ring * separation, baseY - perpY * ring * separation},
				};
				for (double[] candidate : candidates) {
					double cx = MathHelper.clamp(candidate[0], MAP_BORDER_WIDTH + margin, mapWidth() + MAP_BORDER_WIDTH - margin);
					double cy = MathHelper.clamp(candidate[1], MAP_BORDER_HEIGHT + margin, mapHeight() + MAP_BORDER_HEIGHT - margin);
					boolean conflict = false;
					for (double[] other : placed) {
						if (Math.hypot(cx - other[0], cy - other[1]) < separation) {
							conflict = true;
							break;
						}
					}
					if (!conflict) {
						ax = cx;
						ay = cy;
						found = true;
						break;
					}
				}
			}
			placed.add(new double[]{ax, ay});
			float rot = (float) Math.toDegrees(Math.atan2(nx, -ny)) + 180.0F;
			Integer color = landmark.get(LandmarkComponentTypes.COLOR);
			int tintColor = color == null ? 0xE8C878 : color & 0xFFFFFF;
			DrawUtil.drawCenteredWithRotation(painter, GUIDE_ARROW, ax, ay, z, scale, 16, 16, rot, light, ((int) (alpha * 255) << 24) | tintColor);

			Text distText = Text.translatable("gui.roleplayers_atlas.distance", (int) dist);
			double half = textRenderer.getWidth(distText) / 2.0;
			double textX = MathHelper.clamp(ax, MAP_BORDER_WIDTH + half + 2, mapWidth() + MAP_BORDER_WIDTH - half - 2);
			double textY = ay + 8 * scale + 2;
			if (textY + 9 > MAP_BORDER_HEIGHT + mapHeight()) textY = ay - 8 * scale - 10;
			painter.drawText(distText, (float) (textX - half), (float) textY, ((int) (alpha * 255) << 24) | 0xF8ECD0, light);
		}
	}

	default void renderPlayer(AtlasPainter painter, float z, int light, PlayerSummary player, float iconScale, float alpha, boolean hovering, boolean self) {
		double dimX = player.pos().getX();
		double dimZ = player.pos().getZ();

		boolean inDim = dim().equals(player.dimension());
		if (!inDim) {
			Map<RegistryKey<World>, Integer> scales = RoleplayersAtlas.CONFIG.dimensions.getScales(MinecraftClient.getInstance().getNetworkHandler());
			int newScale = scales.getOrDefault(dim(), 0);
			int oldScale = scales.getOrDefault(player.dimension(), 0);
			if (newScale * oldScale == 0) return; // no ratio!
			double mult = newScale / (double) oldScale;
			dimX = mult * dimX;
			dimZ = mult * dimZ;
		}

		double playerOffsetX = worldXToScreenX(dimX) - bookX();
		double playerOffsetY = worldZToScreenY(dimZ) - bookY();

		playerOffsetX = MathHelper.clamp(playerOffsetX, MAP_BORDER_WIDTH, mapWidth() + MAP_BORDER_WIDTH);
		playerOffsetY = MathHelper.clamp(playerOffsetY, MAP_BORDER_HEIGHT, mapHeight() + MAP_BORDER_HEIGHT);

		// Draw the icon:
		float tint = (player.online() ? 1 : 0.5f) * (hovering ? 0.9f : 1);
		float greenTint = self ? 1 : 0.7f;
		float redTint = inDim ? 1 : 0.7f;
		int argb = ColorHelper.getArgb((int) (alpha * 255.0), (int) (tint * redTint * 255), (int) (tint * greenTint * 255), (int) (tint * 255));
		float playerRotation = ((float) Math.round(player.yaw() / 360f * PLAYER_ROTATION_STEPS) / PLAYER_ROTATION_STEPS) * 360f;

		DrawUtil.drawCenteredWithRotation(painter, PLAYER, playerOffsetX, playerOffsetY, z, iconScale, PLAYER_ICON_WIDTH, PLAYER_ICON_HEIGHT, playerRotation, light, argb);
	}

	default void renderTiles(AtlasPainter painter, int light, int argb) {
		renderTiles(painter, light, argb, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	default void renderTiles(AtlasPainter painter, int light, int argb, int clipMinX, int clipMaxX) {
		int mapStartChunkX = MathUtil.roundToBase(screenXToWorldX(bookX()) >> 4, tileChunks()) - 2 * tileChunks();
		int mapStartChunkZ = MathUtil.roundToBase(screenYToWorldZ(bookY()) >> 4, tileChunks()) - 2 * tileChunks();
		int mapEndChunkX = MathUtil.roundToBase(screenXToWorldX(bookX() + bookWidth()) >> 4, tileChunks()) + 2 * tileChunks();
		int mapEndChunkZ = MathUtil.roundToBase(screenYToWorldZ(bookY() + bookHeight()) >> 4, tileChunks()) + 2 * tileChunks();
		double mapStartScreenX = worldXToScreenX(mapStartChunkX << 4);
		double mapStartScreenY = worldZToScreenY(mapStartChunkZ << 4);
		int mapX = bookX() + MAP_BORDER_WIDTH;
		int mapY = bookY() + MAP_BORDER_HEIGHT;
		float effectiveScale = (float) (mapScale() / guiScale());
		painter.push();
		painter.translate(Math.round(mapStartScreenX), Math.round(mapStartScreenY));
		painter.scale(effectiveScale);

		// Batches are cached per scope in the world data; rebuilding them every
		// frame made close zoom levels stutter.
		Map<TileTexture, java.util.List<SubTile>> tileTextures = worldAtlasData().getTileBatches(mapStartChunkX, mapStartChunkZ, mapEndChunkX, mapEndChunkZ, tileChunks());
		int subTilePixels = tilePixels() / 2;
		// Clip bounds in map-local pixels; subtiles straddling a bound get their
		// quad and texture region cut at it instead of being dropped whole.
		double clipLocalMin = clipMinX == Integer.MIN_VALUE ? Double.NEGATIVE_INFINITY : (clipMinX - mapStartScreenX) / effectiveScale;
		double clipLocalMax = clipMaxX == Integer.MAX_VALUE ? Double.POSITIVE_INFINITY : (clipMaxX - mapStartScreenX) / effectiveScale;
		tileTextures.forEach((texture, subtiles) -> {
			try (DrawBatcher batcher = new DrawBatcher(painter, texture.id(), 32, 48, light, true)) {
				for (SubTile subtile : subtiles) {
					int drawX = subtile.x * subTilePixels;
					int drawY = subtile.y * subTilePixels;
					// a non-scope bounds check allows subtile-level accuracy, and keeps border tiling accurate.
					if (drawX * effectiveScale > mapX + mapWidth() - mapStartScreenX || drawY * effectiveScale > mapY + mapHeight() - mapStartScreenY || (drawX + subTilePixels) * effectiveScale < mapX - mapStartScreenX || (drawY + subTilePixels) * effectiveScale < mapY - mapStartScreenY) continue;
					int clippedX1 = (int) Math.max(drawX, Math.ceil(clipLocalMin));
					int clippedX2 = (int) Math.min(drawX + subTilePixels, Math.floor(clipLocalMax));
					if (clippedX2 <= clippedX1) continue;
					batcher.add(clippedX1, drawY, 0, clippedX2 - clippedX1, subTilePixels, subtile.getTextureU() * 8 + (clippedX1 - drawX) * 8 / subTilePixels, subtile.getTextureV() * 8, (clippedX2 - clippedX1) * 8 / subTilePixels, 8, argb);
				}
			}
		});

		painter.pop();
	}
}
