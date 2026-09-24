package glam.ardor.roleplayers_atlas;

import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Map;

/**
 * Zone titles: entering the area around a named player marker shows its name
 * fading in and out at the top of the screen, cinema-style. Per-marker opt-out
 * via the {@link AtlasComponents#ZONE_TITLE} component.
 */
public final class ZoneTitles {
	private static final long FADE_IN_MS = 400;
	private static final long FADE_OUT_MS = 800;

	private static String insideZoneKey = null;
	private static String insideTerritoryKey = null;
	private static Text title = null;
	private static boolean grandTitle = false;
	private static long shownAt = 0;
	/** When each place last announced itself, so a cooldown can hold the next one back. */
	private static final java.util.Map<String, Long> lastShown = new java.util.HashMap<>();

	private ZoneTitles() {
	}

	/**
	 * Puts a title on screen, unless the same place announced itself less than
	 * the cooldown ago – which is what stops it flickering when you step across a
	 * border and back.
	 */
	private static void show(String key, Text name, boolean grand) {
		long now = Util.getMeasuringTimeMs();
		if (now - lastShown.getOrDefault(key, 0L) < RoleplayersAtlas.CONFIG.zoneTitleCooldown * 1000L) return;
		lastShown.put(key, now);
		title = name;
		grandTitle = grand;
		shownAt = now;
		if (RoleplayersAtlas.CONFIG.zoneTitleSound) AtlasSounds.zoneTitle();
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ZoneTitles::tick);
		HudElementRegistry.addLast(RoleplayersAtlas.id("zone_title"), ZoneTitles::render);
	}

	private static void tick(MinecraftClient client) {
		if (client.player == null || client.world == null || !RoleplayersAtlas.CONFIG.zoneTitles) {
			insideZoneKey = null;
			insideTerritoryKey = null;
			return;
		}
		WorldAtlasData data = WorldAtlasData.WORLDS.get(client.world.getRegistryKey());
		if (data == null) return;

		double px = client.player.getX();
		double pz = client.player.getZ();

		String bestKey = null;
		Text bestName = null;
		double bestRatio = 1.0;
		boolean stillInside = false;

		String territoryKey = null;
		Text territoryName = null;
		int territorySize = Integer.MAX_VALUE;

		for (Map.Entry<Landmark, MarkerTexture> entry : data.getAllMarkers(1).entrySet()) {
			Landmark landmark = entry.getKey();
			if (landmark.owner() == null || landmark.owner().equals(WorldLandmarks.GLOBAL)) continue;
			if (Boolean.FALSE.equals(landmark.get(AtlasComponents.ZONE_TITLE))) continue;
			// Pen inscriptions and routes never announce a zone.
			if (Boolean.TRUE.equals(landmark.get(AtlasComponents.PEN_LABEL))) continue;
			if (landmark.contains(AtlasComponents.ROUTE)) continue;
			// Markers on hidden layers stay silent too.
			if (!RoleplayersAtlas.layerVisible(landmark)) continue;
			Text name = landmark.get(LandmarkComponentTypes.NAME);
			if (name == null || name.getString().isEmpty()) continue;
			BlockPos pos = landmark.get(LandmarkComponentTypes.POS);
			if (pos == null) {
				// Territory: the title now fires within a radius of the claim, not
				// only when standing on it, so it can announce the place as you
				// approach. The per-mark ZONE_RADIUS (or the config default) is that
				// buffer; distance is measured to the nearest claimed chunk, and is
				// zero inside.
				var regions = landmark.get(LandmarkComponentTypes.CHUNKS);
				if (regions == null) continue;
				Integer markerRadius = landmark.get(AtlasComponents.ZONE_RADIUS);
				int radius = markerRadius != null ? markerRadius : RoleplayersAtlas.DEFAULT_ZONE_RADIUS;
				if (territoryDistance(regions, px, pz) > radius) continue;
				int size = regions.values().stream().mapToInt(java.util.BitSet::cardinality).sum();
				// The smallest containing territory wins (duchy inside a kingdom).
				if (size < territorySize) {
					territorySize = size;
					territoryKey = landmark.owner() + "/" + landmark.id();
					territoryName = name;
				}
				continue;
			}
			Integer markerRadius = landmark.get(AtlasComponents.ZONE_RADIUS);
			int radius = markerRadius != null ? markerRadius : RoleplayersAtlas.DEFAULT_ZONE_RADIUS;
			double dx = pos.getX() + 0.5 - px;
			double dz = pos.getZ() + 0.5 - pz;
			double sq = dx * dx + dz * dz;
			double ratio = sq / ((double) radius * radius);
			String key = landmark.owner() + "/" + landmark.id();
			if (key.equals(insideZoneKey) && ratio <= 1.25 * 1.25) stillInside = true;
			if (ratio <= bestRatio) {
				bestRatio = ratio;
				bestKey = key;
				bestName = name;
			}
		}

		if (territoryKey != null) {
			if (!territoryKey.equals(insideTerritoryKey)) {
				insideTerritoryKey = territoryKey;
				show(territoryKey, territoryName, true);
			}
		} else {
			insideTerritoryKey = null;
		}

		if (bestKey != null) {
			if (!bestKey.equals(insideZoneKey)) {
				insideZoneKey = bestKey;
				show(bestKey, bestName, false);
			}
		} else if (!stillInside) {
			// Left the zone (with a little hysteresis): the next entry shows the
			// title again.
			insideZoneKey = null;
		}
	}

	/** Block distance from a point to the nearest claimed chunk; zero inside the territory. */
	private static double territoryDistance(Map<folk.sisby.surveyor.util.RegionPos, java.util.BitSet> regions, double px, double pz) {
		double best = Double.MAX_VALUE;
		for (net.minecraft.util.math.ChunkPos chunk : folk.sisby.surveyor.util.RegionPos.regionsToChunks(regions)) {
			double x0 = chunk.getStartX(), z0 = chunk.getStartZ();
			double dx = Math.max(Math.max(x0 - px, px - (x0 + 16)), 0);
			double dz = Math.max(Math.max(z0 - pz, pz - (z0 + 16)), 0);
			double d = Math.hypot(dx, dz);
			if (d < best) best = d;
			if (best == 0) break;
		}
		return best;
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		if (title == null) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options.hudHidden || client.player == null) return;

		// How long the title holds fully shown before fading is a setting; the
		// fade-in and fade-out stay fixed.
		long holdMs = Math.max(0L, RoleplayersAtlas.CONFIG.zoneTitleSeconds * 1000L);
		long t = Util.getMeasuringTimeMs() - shownAt;
		if (t >= FADE_IN_MS + holdMs + FADE_OUT_MS) {
			title = null;
			return;
		}
		float alpha = t < FADE_IN_MS
			? t / (float) FADE_IN_MS
			: t > FADE_IN_MS + holdMs ? 1 - (t - FADE_IN_MS - holdMs) / (float) FADE_OUT_MS : 1;
		int a = (int) (MathHelper.clamp(alpha, 0, 1) * 255);
		if (a < 8) return;

		Text styled = title.copy().formatted(Formatting.ITALIC);
		var textRenderer = client.textRenderer;
		int width = textRenderer.getWidth(styled);
		int y = (int) (context.getScaledWindowHeight() * 0.22);
		int color = grandTitle ? 0xE8C878 : 0xF8ECD0;
		float scale = grandTitle ? 2.6F : 2.0F;

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(context.getScaledWindowWidth() / 2.0F, y);
		context.getMatrices().scale(scale, scale);
		context.drawText(textRenderer, styled, -width / 2, 0, (a << 24) | color, true);
		// Ornament lines: font-stroke thickness with the same 1px drop shadow as
		// the glyphs. Grand (territory) titles get one above and one below.
		int lineHalf = Math.max(24, width / 2 + 8);
		context.fill(-lineHalf + 1, 12, lineHalf + 1, 13, (a << 24) | 0x3E3B34);
		context.fill(-lineHalf, 11, lineHalf, 12, (a << 24) | color);
		if (grandTitle) {
			context.fill(-lineHalf + 1, -4, lineHalf + 1, -3, (a << 24) | 0x3E3B34);
			context.fill(-lineHalf, -5, lineHalf, -4, (a << 24) | color);
		}
		context.getMatrices().popMatrix();
	}
}
