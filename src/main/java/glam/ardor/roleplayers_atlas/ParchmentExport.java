package glam.ardor.roleplayers_atlas;

import glam.ardor.roleplayers_atlas.gui.tiles.SubTile;
import glam.ardor.roleplayers_atlas.util.Rect;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.RegionPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.resource.Resource;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Map snapshots: saving map images to the screenshots folder — either a crop
 * of the current view (captured between frames, when the finished frame with
 * the GUI is in the framebuffer) or the whole explored map composed on the CPU
 * from the same subtile batches the GUI renders. Landmark categories are
 * included per checkbox.
 */
public final class ParchmentExport {
	private static final int PARCHMENT = 0xFFE4CFA0;
	/** Outline + wooden rail + the mat the map sits in, in scale units. */
	private static final int FRAME_BORDER = 1 + 2 + 2 + 8;

	// Pending current-view capture (consumed at end of client tick).
	private static int captureDelay = -1;
	private static int capX;
	private static int capY;
	private static int capW;
	private static int capH;
	private static boolean includeMarkers = true;
	private static boolean includeZones = true;
	private static boolean includeRoutes = true;
	private static boolean includeLabels = true;

	private ParchmentExport() {
	}

	public static void requestViewCapture(int x, int y, int w, int h, boolean markers, boolean zones, boolean routes, boolean labels) {
		capX = x;
		capY = y;
		capW = w;
		capH = h;
		includeMarkers = markers;
		includeZones = zones;
		includeRoutes = routes;
		includeLabels = labels;
		captureDelay = 3;
	}

	// While a stitched full-map export runs, the category filters stay active.
	private static boolean sequenceFilter = false;

	public static void beginSequenceFilter(boolean markers, boolean zones, boolean routes, boolean labels) {
		includeMarkers = markers;
		includeZones = zones;
		includeRoutes = routes;
		includeLabels = labels;
		sequenceFilter = true;
	}

	public static void endSequenceFilter() {
		sequenceFilter = false;
	}

	public static boolean captureActive() {
		return captureDelay >= 0 || sequenceFilter;
	}

	/** While a capture is pending, excluded categories are hidden from the map render. */
	public static boolean visibleForCapture(Landmark landmark) {
		if (!captureActive()) return true;
		if (landmark.contains(AtlasComponents.ROUTE)) return includeRoutes;
		if (Boolean.TRUE.equals(landmark.get(AtlasComponents.PEN_LABEL))) return includeLabels;
		if (!landmark.contains(LandmarkComponentTypes.POS) && landmark.contains(LandmarkComponentTypes.CHUNKS)) return includeZones;
		return includeMarkers;
	}

	/** Called at the end of every client tick: the framebuffer now holds the finished frame with the GUI. */
	public static void tickCapture(MinecraftClient client) {
		if (captureDelay < 0) return;
		if (captureDelay > 0) {
			captureDelay--;
			return;
		}
		captureDelay = -1;
		int x = capX;
		int y = capY;
		int wantW = capW;
		int wantH = capH;
		ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), full -> {
			var player = MinecraftClient.getInstance().player;
			try {
				int w = Math.min(wantW, full.getWidth() - x);
				int h = Math.min(wantH, full.getHeight() - y);
				if (w <= 0 || h <= 0) {
					full.close();
					return;
				}
				NativeImage crop = new NativeImage(w, h, false);
				for (int py = 0; py < h; py++) {
					for (int px = 0; px < w; px++) {
						crop.setColorArgb(px, py, full.getColorArgb(x + px, y + py));
					}
				}
				full.close();
				NativeImage framed = decorate(crop);
				Path file = save(framed, "atlas_view");
				if (framed != crop) framed.close();
				crop.close();
				if (player != null) {
					player.sendMessage(Text.translatable("gui.roleplayers_atlas.screenshot.saved", fileLink(file)), false);
					AtlasSounds.exportDone();
				}
			} catch (Exception e) {
				RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] View capture failed", e);
				if (player != null) player.sendMessage(Text.translatable("gui.roleplayers_atlas.screenshot.failed"), false);
			}
		});
	}

	/** Vanilla-screenshot-style clickable filename that opens the saved image. */
	public static Text fileLink(Path file) {
		return Text.literal(file.getFileName().toString()).styled(style -> style
			.withUnderline(true)
			.withClickEvent(new net.minecraft.text.ClickEvent.OpenFile(file.toAbsolutePath())));
	}

	/** Same, for files nothing can open on their own — the click reveals the folder instead. */
	public static Text folderLink(Path file) {
		Path folder = file.toAbsolutePath().getParent();
		return Text.literal(file.getFileName().toString()).styled(style -> style
			.withUnderline(true)
			.withClickEvent(new net.minecraft.text.ClickEvent.OpenFile(folder == null ? file.toAbsolutePath() : folder)));
	}

	// Decoration for saved map images: the cartographer's name, the date, and a
	// carved wooden border. Chosen in the snapshot dialog, applied to whichever
	// image comes out — a cropped view or the whole stitched map.
	private static boolean stampAuthor = false;
	private static boolean stampTime = false;
	private static boolean stampFrame = false;

	public static void setDecoration(boolean author, boolean time, boolean frame) {
		stampAuthor = author;
		stampTime = time;
		stampFrame = frame;
	}

	public static boolean decorated() {
		return stampAuthor || stampTime || stampFrame;
	}

	/**
	 * Wraps a saved map in its scroll furniture. The border is grown outwards
	 * into a new image rather than painted over the map, so nothing the player
	 * drew is covered up by it.
	 */
	public static NativeImage decorate(NativeImage map) {
		if (!decorated()) return map;
		String author = stampAuthor ? AtlasTime.selfName() : "";
		String stamp = stampTime ? AtlasTime.stampNow() : "";
		String caption = author.isEmpty() ? stamp : stamp.isEmpty() ? author : author + " · " + stamp;

		int scale = Math.max(1, Math.min(4, Math.min(map.getWidth(), map.getHeight()) / 320));
		int border = stampFrame ? FRAME_BORDER * scale : 0;
		int captionH = caption.isEmpty() ? 0 : (stampFrame ? 22 : 20) * scale;
		if (border == 0 && captionH == 0) return map;

		int width = map.getWidth() + border * 2;
		int height = map.getHeight() + border * 2 + captionH;
		NativeImage framed = new NativeImage(width, height, false);
		if (stampFrame) paintItemFrame(framed, border, scale, border, border, map.getWidth(), map.getHeight());
		else framed.fillRect(0, 0, width, height, PARCHMENT);
		for (int y = 0; y < map.getHeight(); y++) {
			for (int x = 0; x < map.getWidth(); x++) {
				framed.setColorArgb(border + x, border + y, map.getColorArgb(x, y));
			}
		}

		if (!caption.isEmpty()) {
			int textW = MapFont.width(caption, scale);
			int textX = (width - textW) / 2;
			// Low on the rail, clear of the mat that wraps under the map.
			int textY = border + map.getHeight() + captionH - 10 * scale;
			MapFont.draw(framed, caption, textX, textY, stampFrame ? 0xFF3B2411 : 0xFF4A3018, scale);
		}
		return framed;
	}

	/**
	 * The vanilla item frame, rebuilt at any size: birch rails lit from the top
	 * left, and the dark recess the item sits in — here, the map. Colours are
	 * taken straight from the item frame sprite so the border reads as the same
	 * object rather than as an invented ornament.
	 */
	private static void paintItemFrame(NativeImage image, int border, int scale, int mapX, int mapY, int mapW, int mapH) {
		int width = image.getWidth();
		int height = image.getHeight();
		int outline = scale;
		int rail = 2 * scale;
		int mat = 8 * scale;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (x >= mapX && x < mapX + mapW && y >= mapY && y < mapY + mapH) continue;
				int speck = speckle(x, y, scale);
				// How far outside the map this pixel lies — the mat wraps it, the
				// wood sits at the image's own edge, and the wide strip left
				// between the two along the bottom carries the caption.
				int outX = Math.max(mapX - x, x - (mapX + mapW - 1));
				int outY = Math.max(mapY - y, y - (mapY + mapH - 1));
				int fromMap = Math.max(Math.max(outX, 0), Math.max(outY, 0));
				int edge = Math.min(Math.min(x, y), Math.min(width - 1 - x, height - 1 - y));
				boolean lit = Math.min(x, y) <= Math.min(width - 1 - x, height - 1 - y);
				int color;
				if (edge < outline) {
					color = 0xFF4A3722;
				} else if (fromMap <= mat) {
					color = speck == 0 ? 0xFF944C29 : speck == 1 ? 0xFF834829 : 0xFF734029;
				} else if (edge < outline + rail) {
					color = lit ? (speck == 0 ? 0xFFD5C184 : 0xFFE7D49B) : (speck == 0 ? 0xFF937D42 : 0xFF776432);
				} else {
					color = lit ? (speck == 0 ? 0xFF9D8A51 : 0xFF937D42) : (speck == 0 ? 0xFFB4A062 : 0xFF9D8A51);
				}
				image.setColorArgb(x, y, color);
			}
		}
	}

	/** Deterministic wood speckle, sized to the frame so it stays pixel art at any scale. */
	private static int speckle(int x, int y, int scale) {
		int hash = ((x / scale) * 374761393 + (y / scale) * 668265263) & 0x7FFFFFFF;
		hash = ((hash ^ (hash >>> 13)) * 1274126177) & 0x7FFFFFFF;
		return (hash >>> 16) & 3;
	}

	public static Path save(NativeImage image, String prefix) throws IOException {
		Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("screenshots");
		Files.createDirectories(dir);
		String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new java.util.Date());
		Path file = dir.resolve(prefix + "_" + stamp + ".png");
		image.writeTo(file);
		return file;
	}

	private static void blendPixel(NativeImage dst, int x, int y, int src) {
		if (x < 0 || y < 0 || x >= dst.getWidth() || y >= dst.getHeight()) return;
		int srcA = src >>> 24;
		if (srcA == 0) return;
		if (srcA == 255) {
			dst.setColorArgb(x, y, src);
			return;
		}
		int d = dst.getColorArgb(x, y);
		int outR = ((src >> 16 & 0xFF) * srcA + (d >> 16 & 0xFF) * (255 - srcA)) / 255;
		int outG = ((src >> 8 & 0xFF) * srcA + (d >> 8 & 0xFF) * (255 - srcA)) / 255;
		int outB = ((src & 0xFF) * srcA + (d & 0xFF) * (255 - srcA)) / 255;
		dst.setColorArgb(x, y, 0xFF000000 | (outR << 16) | (outG << 8) | outB);
	}

	private static void blendRect(NativeImage dst, int x, int y, int w, int h, int argb) {
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				blendPixel(dst, x + px, y + py, argb);
			}
		}
	}

	private static NativeImage loadTexture(Identifier id, Map<Identifier, NativeImage> cache) {
		return cache.computeIfAbsent(id, key -> {
			try {
				Resource resource = MinecraftClient.getInstance().getResourceManager().getResource(key).orElse(null);
				if (resource == null) return null;
				try (InputStream in = resource.getInputStream()) {
					return NativeImage.read(in);
				}
			} catch (Exception e) {
				return null;
			}
		});
	}

	/** Composes the whole explored map at 16px per chunk; null if nothing is explored. */
	public static NativeImage composeFullMap(WorldAtlasData data, boolean markers, boolean zones, boolean routes, boolean labels) {
		Rect scope = data.getScope();
		int iterMinChunkX = scope.minX - 1;
		int iterMinChunkZ = scope.minY - 1;
		Map<TileTexture, List<SubTile>> batches = data.getTileBatches(iterMinChunkX, iterMinChunkZ, scope.maxX + 1, scope.maxY + 1, 1);
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (List<SubTile> subtiles : batches.values()) {
			for (SubTile subtile : subtiles) {
				minX = Math.min(minX, subtile.x);
				minY = Math.min(minY, subtile.y);
				maxX = Math.max(maxX, subtile.x);
				maxY = Math.max(maxY, subtile.y);
			}
		}
		if (minX > maxX) return null;

		int pad = 16;
		int width = (maxX - minX + 1) * 8 + pad * 2;
		int height = (maxY - minY + 1) * 8 + pad * 2;
		NativeImage image = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setColorArgb(x, y, PARCHMENT);
			}
		}

		Map<Identifier, NativeImage> cache = new HashMap<>();
		int fMinX = minX;
		int fMinY = minY;
		batches.forEach((texture, subtiles) -> {
			NativeImage tex = loadTexture(texture.id(), cache);
			if (tex == null) return;
			for (SubTile subtile : subtiles) {
				int srcX = subtile.getTextureU() * 8;
				int srcY = subtile.getTextureV() * 8;
				int dstX = (subtile.x - fMinX) * 8 + pad;
				int dstY = (subtile.y - fMinY) * 8 + pad;
				for (int y = 0; y < 8; y++) {
					for (int x = 0; x < 8; x++) {
						if (srcX + x >= tex.getWidth() || srcY + y >= tex.getHeight()) continue;
						blendPixel(image, dstX + x, dstY + y, tex.getColorArgb(srcX + x, srcY + y));
					}
				}
			}
		});

		// World-to-image mapping shared by all overlays (calibrated to the
		// subtile grid used above).
		java.util.function.DoubleUnaryOperator toPxX = blockX -> pad + ((blockX / 16.0 - iterMinChunkX) * 2 - fMinX) * 8;
		java.util.function.DoubleUnaryOperator toPxZ = blockZ -> pad + ((blockZ / 16.0 - iterMinChunkZ) * 2 - fMinY) * 8;

		Map<Landmark, MarkerTexture> allMarkers = data.getAllMarkers(1);

		if (zones) {
			allMarkers.forEach((landmark, texture) -> {
				if (!RoleplayersAtlas.layerVisible(landmark)) return;
				if (landmark.contains(LandmarkComponentTypes.POS) || !landmark.contains(LandmarkComponentTypes.CHUNKS)) return;
				Integer color = landmark.get(LandmarkComponentTypes.COLOR);
				int rgb = color == null ? 0xFFFFFF : color & 0xFFFFFF;
				Set<ChunkPos> chunks = RegionPos.regionsToChunks(landmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new HashMap<>()));
				for (ChunkPos chunk : chunks) {
					int x = (int) Math.round(toPxX.applyAsDouble(chunk.getStartX()));
					int y = (int) Math.round(toPxZ.applyAsDouble(chunk.getStartZ()));
					blendRect(image, x, y, 16, 16, 0x40000000 | rgb);
					if (!chunks.contains(new ChunkPos(chunk.x - 1, chunk.z))) blendRect(image, x, y, 1, 16, 0x80000000 | rgb);
					if (!chunks.contains(new ChunkPos(chunk.x + 1, chunk.z))) blendRect(image, x + 15, y, 1, 16, 0x80000000 | rgb);
					if (!chunks.contains(new ChunkPos(chunk.x, chunk.z - 1))) blendRect(image, x, y, 16, 1, 0x80000000 | rgb);
					if (!chunks.contains(new ChunkPos(chunk.x, chunk.z + 1))) blendRect(image, x, y + 15, 16, 1, 0x80000000 | rgb);
				}
			});
		}

		if (routes) {
			allMarkers.forEach((landmark, texture) -> {
				if (!RoleplayersAtlas.layerVisible(landmark)) return;
				List<BlockPos> points = landmark.get(AtlasComponents.ROUTE);
				if (points == null || points.size() < 2) return;
				Integer color = landmark.get(LandmarkComponentTypes.COLOR);
				int argb = 0xE6000000 | (color == null ? 0x4A3421 : color & 0xFFFFFF);
				double phase = 0;
				for (int i = 0; i < points.size() - 1; i++) {
					double ax = toPxX.applyAsDouble(points.get(i).getX() + 0.5);
					double ay = toPxZ.applyAsDouble(points.get(i).getZ() + 0.5);
					double bx = toPxX.applyAsDouble(points.get(i + 1).getX() + 0.5);
					double by = toPxZ.applyAsDouble(points.get(i + 1).getZ() + 0.5);
					double len = Math.hypot(bx - ax, by - ay);
					if (len < 0.01) continue;
					for (double t = 0; t < len; t += 1) {
						if ((phase + t) % 10 >= 6) continue;
						int x = (int) Math.round(ax + (bx - ax) * t / len);
						int y = (int) Math.round(ay + (by - ay) * t / len);
						blendRect(image, x, y, 2, 2, argb);
					}
					phase = (phase + len) % 10;
				}
			});
		}

		if (markers) {
			allMarkers.forEach((landmark, texture) -> {
				if (!RoleplayersAtlas.layerVisible(landmark)) return;
				if (landmark.contains(AtlasComponents.ROUTE)) return;
				if (Boolean.TRUE.equals(landmark.get(AtlasComponents.PEN_LABEL))) return;
				BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
				if (pos == null) return;
				NativeImage tex = loadTexture(texture.id(), cache);
				if (tex == null) return;
				int cx = (int) Math.round(toPxX.applyAsDouble(pos.getX()));
				int cy = (int) Math.round(toPxZ.applyAsDouble(pos.getZ()));
				int dx = cx + texture.offsetX();
				int dy = cy + texture.offsetY();
				for (int y = 0; y < texture.textureHeight(); y++) {
					for (int x = 0; x < texture.textureWidth(); x++) {
						if (x >= tex.getWidth() || y >= tex.getHeight()) continue;
						blendPixel(image, dx + x, dy + y, tex.getColorArgb(x, y));
					}
				}
			});
		}

		// Names: territories at their centroid, routes at the path middle,
		// inscriptions at their position — dark plate + tinted text.
		if (zones) {
			allMarkers.forEach((landmark, texture) -> {
				if (!RoleplayersAtlas.layerVisible(landmark)) return;
				if (landmark.contains(LandmarkComponentTypes.POS) || !landmark.contains(LandmarkComponentTypes.CHUNKS)) return;
				String name = landmark.getOrDefault(LandmarkComponentTypes.NAME, Text.empty()).getString();
				if (name.isBlank()) return;
				net.minecraft.util.math.ColumnPos center = glam.ardor.roleplayers_atlas.util.TerritoryUtil.centroid(landmark.getOrDefault(LandmarkComponentTypes.CHUNKS, new HashMap<>()));
				drawPlateText(image, name.toUpperCase(java.util.Locale.ROOT), (int) Math.round(toPxX.applyAsDouble(center.x())), (int) Math.round(toPxZ.applyAsDouble(center.z())), landmark.get(LandmarkComponentTypes.COLOR), 2);
			});
		}
		if (routes) {
			allMarkers.forEach((landmark, texture) -> {
				if (!RoleplayersAtlas.layerVisible(landmark)) return;
				List<BlockPos> points = landmark.get(AtlasComponents.ROUTE);
				if (points == null || points.size() < 2) return;
				String name = landmark.getOrDefault(LandmarkComponentTypes.NAME, Text.empty()).getString();
				if (name.isBlank()) return;
				BlockPos mid = points.get(points.size() / 2);
				drawPlateText(image, name, (int) Math.round(toPxX.applyAsDouble(mid.getX() + 0.5)), (int) Math.round(toPxZ.applyAsDouble(mid.getZ() + 0.5)) - 10, landmark.get(LandmarkComponentTypes.COLOR), 1);
			});
		}
		if (labels) {
			allMarkers.forEach((landmark, texture) -> {
				if (!RoleplayersAtlas.layerVisible(landmark)) return;
				if (!Boolean.TRUE.equals(landmark.get(AtlasComponents.PEN_LABEL))) return;
				String name = landmark.getOrDefault(LandmarkComponentTypes.NAME, Text.empty()).getString();
				if (name.isBlank()) return;
				BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
				if (pos == null) return;
				drawPlateText(image, name, (int) Math.round(toPxX.applyAsDouble(pos.getX())), (int) Math.round(toPxZ.applyAsDouble(pos.getZ())) - 4, landmark.get(LandmarkComponentTypes.COLOR), 1);
			});
		}

		for (NativeImage tex : cache.values()) {
			if (tex != null) tex.close();
		}
		return image;
	}

	/** Centered text on the translucent dark plate used by name labels in-game. */
	private static void drawPlateText(NativeImage image, String text, int centerX, int topY, Integer color, int scale) {
		int width = MapFont.width(text, scale);
		if (width <= 0) return;
		int x = centerX - width / 2;
		blendRect(image, x - 2 * scale, topY - scale, width + 3 * scale, 10 * scale, 0x73201409);
		int ink = color == null ? 0xF8ECD0 : color & 0xFFFFFF;
		// Lift very dark inks so they stay readable on the dark plate.
		double luma = 0.299 * ((ink >> 16) & 0xFF) + 0.587 * ((ink >> 8) & 0xFF) + 0.114 * (ink & 0xFF);
		if (luma < 90) {
			float max = Math.max(1, Math.max((ink >> 16) & 0xFF, Math.max((ink >> 8) & 0xFF, ink & 0xFF)));
			float mul = 200 / max;
			ink = ((int) Math.min(255, ((ink >> 16) & 0xFF) * mul) << 16) | ((int) Math.min(255, ((ink >> 8) & 0xFF) * mul) << 8) | (int) Math.min(255, (ink & 0xFF) * mul);
		}
		MapFont.draw(image, text, x, topY, 0xFF000000 | ink, scale);
	}
}
