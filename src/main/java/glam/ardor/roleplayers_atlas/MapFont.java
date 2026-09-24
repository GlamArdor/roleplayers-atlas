package glam.ardor.roleplayers_atlas;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * CPU renderer for the vanilla bitmap font, used to write names onto exported
 * map images. Parses the default font's bitmap providers (ascii, accented,
 * nonlatin_european – covers Latin and Cyrillic) and blits tinted glyphs.
 */
public final class MapFont {
	private record Glyph(NativeImage tex, int u, int v, int cellW, int cellH, int height, int ascent, int advance) {
	}

	private static Map<Integer, Glyph> glyphs = null;
	private static Map<Integer, Integer> spaces = null;

	private MapFont() {
	}

	private static void ensureLoaded() {
		if (glyphs != null) return;
		glyphs = new HashMap<>();
		spaces = new HashMap<>();
		try {
			loadFontJson(Identifier.of("minecraft", "font/default.json"));
		} catch (Exception e) {
			RoleplayersAtlas.LOGGER.warn("[Roleplayer's Atlas] Failed to load font for map export", e);
		}
	}

	private static void loadFontJson(Identifier fontId) {
		Resource resource = MinecraftClient.getInstance().getResourceManager().getResource(fontId).orElse(null);
		if (resource == null) return;
		JsonObject root;
		try (InputStream in = resource.getInputStream()) {
			root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return;
		}
		JsonArray providers = root.getAsJsonArray("providers");
		if (providers == null) return;
		for (JsonElement el : providers) {
			JsonObject provider = el.getAsJsonObject();
			String type = provider.get("type").getAsString();
			switch (type) {
				case "reference" -> {
					Identifier ref = Identifier.tryParse(provider.get("id").getAsString());
					if (ref != null) loadFontJson(Identifier.of(ref.getNamespace(), "font/" + ref.getPath() + ".json"));
				}
				case "space" -> {
					JsonObject advances = provider.getAsJsonObject("advances");
					for (String key : advances.keySet()) {
						if (!key.isEmpty()) spaces.putIfAbsent(key.codePointAt(0), advances.get(key).getAsInt());
					}
				}
				case "bitmap" -> loadBitmapProvider(provider);
				default -> {
				}
			}
		}
	}

	private static void loadBitmapProvider(JsonObject provider) {
		Identifier fileId = Identifier.tryParse(provider.get("file").getAsString());
		if (fileId == null) return;
		Identifier textureId = Identifier.of(fileId.getNamespace(), "textures/" + fileId.getPath());
		Resource resource = MinecraftClient.getInstance().getResourceManager().getResource(textureId).orElse(null);
		if (resource == null) return;
		NativeImage tex;
		try (InputStream in = resource.getInputStream()) {
			tex = NativeImage.read(in);
		} catch (Exception e) {
			return;
		}
		int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
		int ascent = provider.has("ascent") ? provider.get("ascent").getAsInt() : 7;
		JsonArray rows = provider.getAsJsonArray("chars");
		if (rows == null || rows.isEmpty()) return;
		int rowLen = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
		if (rowLen == 0) return;
		int cellW = tex.getWidth() / rowLen;
		int cellH = tex.getHeight() / rows.size();
		double glyphScale = height / (double) cellH;

		for (int row = 0; row < rows.size(); row++) {
			String chars = rows.get(row).getAsString();
			int col = 0;
			for (int idx = 0; idx < chars.length(); col++) {
				int codePoint = chars.codePointAt(idx);
				idx += Character.charCount(codePoint);
				if (codePoint == 0 || glyphs.containsKey(codePoint)) continue;
				int u = col * cellW;
				int v = row * cellH;
				int maxCol = -1;
				for (int y = 0; y < cellH; y++) {
					for (int x = 0; x < cellW; x++) {
						if ((tex.getColorArgb(u + x, v + y) >>> 24) > 8 && x > maxCol) maxCol = x;
					}
				}
				if (maxCol < 0) continue;
				int advance = (int) Math.round((maxCol + 1) * glyphScale) + 1;
				glyphs.put(codePoint, new Glyph(tex, u, v, cellW, cellH, height, ascent, advance));
			}
		}
	}

	public static int width(String text, int scale) {
		ensureLoaded();
		int width = 0;
		for (int idx = 0; idx < text.length(); ) {
			int codePoint = text.codePointAt(idx);
			idx += Character.charCount(codePoint);
			Glyph glyph = glyphs.get(codePoint);
			if (glyph != null) width += glyph.advance() * scale;
			else width += spaces.getOrDefault(codePoint, 6) * scale;
		}
		return width;
	}

	/** Draws tinted text with (x, y) at the top-left of the line (line height = 8 * scale). */
	public static void draw(NativeImage dst, String text, int x, int y, int argb, int scale) {
		ensureLoaded();
		int tintA = argb >>> 24;
		int tintRgb = argb & 0xFFFFFF;
		int cursor = x;
		for (int idx = 0; idx < text.length(); ) {
			int codePoint = text.codePointAt(idx);
			idx += Character.charCount(codePoint);
			Glyph glyph = glyphs.get(codePoint);
			if (glyph == null) {
				cursor += spaces.getOrDefault(codePoint, 6) * scale;
				continue;
			}
			double glyphScale = glyph.height() / (double) glyph.cellH();
			int destW = (int) Math.round(glyph.cellW() * glyphScale) * scale;
			int destH = glyph.height() * scale;
			int offsetY = (7 - glyph.ascent()) * scale;
			for (int dy = 0; dy < destH; dy++) {
				int srcY = glyph.v() + (int) (dy / (glyphScale * scale));
				for (int dx = 0; dx < destW; dx++) {
					int srcX = glyph.u() + (int) (dx / (glyphScale * scale));
					int src = glyph.tex().getColorArgb(srcX, srcY);
					int alpha = (src >>> 24) * tintA / 255;
					if (alpha == 0) continue;
					blend(dst, cursor + dx, y + offsetY + dy, (alpha << 24) | tintRgb);
				}
			}
			cursor += glyph.advance() * scale;
		}
	}

	private static void blend(NativeImage dst, int x, int y, int src) {
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
}
