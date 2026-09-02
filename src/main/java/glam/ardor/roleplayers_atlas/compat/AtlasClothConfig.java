package glam.ardor.roleplayers_atlas.compat;

import glam.ardor.roleplayers_atlas.AtlasConfig;
import glam.ardor.roleplayers_atlas.RoleplayersAtlas;
import glam.ardor.roleplayers_atlas.WorldAtlasData;
import glam.ardor.roleplayers_atlas.gui.LivePreview;
import glam.ardor.roleplayers_atlas.gui.SettingsLook;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * The settings screen, built with Cloth Config: one long list with the
 * categories down the side and a search box over all of them, a reset button
 * on every row, and the world left visible behind the list.
 * <p>
 * Cloth is optional, so nothing outside this class may name it — the mod has to
 * keep working for players who don't have it. {@link AtlasModMenu} checks first
 * and only then calls in here, which is what keeps the class from being loaded
 * at all when Cloth is absent.
 * <p>
 * Entries are also read back into the config every tick while the screen is
 * open ({@link LivePreview}), so a change shows itself as it is made; leaving
 * without saving puts everything back.
 */
public final class AtlasClothConfig {
	private AtlasClothConfig() {
	}

	/** Widgets to read back into the config every tick, so the map follows the edits. */
	private static final List<Runnable> LIVE = new ArrayList<>();

	private static Text label(String key) {
		return Text.translatable("config.roleplayers_atlas." + key);
	}

	private static Text tip(String key) {
		return Text.translatable("config.roleplayers_atlas." + key + ".tooltip");
	}

	public static Screen create(Screen parent) {
		AtlasConfig config = RoleplayersAtlas.CONFIG;
		LIVE.clear();
		// Anything that changes what a tile should look like has to redraw the
		// land already on the map, or the setting appears to do nothing at all.
		AtlasConfig.FallbackHandling wasFallback = config.fallbackFailHandling;
		int[] wasElevations = {config.elevationLow, config.elevationMid, config.elevationHigh, config.elevationPeak};
		// A grave's epitaph carries its date in the marker's own name, so a change
		// of reckoning has to go back and rewrite the ones already on the page.
		AtlasConfig.Reckoning wasReckoning = config.reckoning;
		boolean wasMarkDate = config.showMarkDate;
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Text.translatable("gui.roleplayers_atlas.config"))
			.setSavingRunnable(() -> {
				LivePreview.markSaved();
				config.saveFields();
				boolean elevationsChanged = wasElevations[0] != config.elevationLow
					|| wasElevations[1] != config.elevationMid
					|| wasElevations[2] != config.elevationHigh
					|| wasElevations[3] != config.elevationPeak;
				if (config.fallbackFailHandling != wasFallback || elevationsChanged) WorldAtlasData.retileAll();
				if (config.reckoning != wasReckoning || config.showMarkDate != wasMarkDate) WorldAtlasData.redateGraves();
			});
		// See the land while setting it up: the map redraws to these options, and
		// a solid menu background would hide what the settings are for.
		builder.setTransparentBackground(true);
		// One long list with the categories down the side, rather than tabs.
		// Tabbed, the search box only ever looks inside the tab you are standing
		// in, so finding a setting means knowing which tab it lives in first —
		// which is exactly what a search is for.
		builder.setGlobalized(true);
		builder.setGlobalizedExpanded(true);

		ConfigEntryBuilder e = builder.entryBuilder();

		ConfigCategory map = builder.getOrCreateCategory(Text.translatable("roleplayers_atlas.category.map"));
		map.addEntry(toggle(e, "fullscreen", config.fullscreen, true, v -> config.fullscreen = v));
		map.addEntry(toggle(e, "keepZoom", config.keepZoom, false, v -> config.keepZoom = v));
		map.addEntry(toggle(e, "keepOffset", config.keepOffset, false, v -> config.keepOffset = v));
		var emptyEntry = e.startEnumSelector(label("emptyHandling"), AtlasConfig.EmptyHandling.class, config.emptyHandling)
			.setDefaultValue(AtlasConfig.EmptyHandling.EMPTY)
			.setEnumNameProvider(v -> Text.translatable("gui.roleplayers_atlas.config.emptyHandling." + v.name().toLowerCase()))
			.setTooltip(tip("emptyHandling"))
			.setSaveConsumer(v -> config.emptyHandling = v)
			.build();
		map.addEntry(emptyEntry);
		LIVE.add(() -> config.emptyHandling = emptyEntry.getValue());
		map.addEntry(toggle(e, "stabilizeHeldMap", config.stabilizeHeldMap, false, v -> config.stabilizeHeldMap = v));
		map.addEntry(intSlider(e, "handheldScale", config.handheldScale, 100, 50, 200,
			v -> Text.translatable("config.roleplayers_atlas.percent", v), v -> config.handheldScale = v));
		map.addEntry(toggle(e, "showHints", config.showHints, true, v -> config.showHints = v));
		map.addEntry(intSlider(e, "mapScale", config.mapScale, 0, -2, 10, null, v -> config.mapScale = v));
		map.addEntry(intSlider(e, "maxTileChunks", config.maxTileChunks, 5, 0, 6, null, v -> config.maxTileChunks = v));
		map.addEntry(intSlider(e, "maxTilePixels", config.maxTilePixels, 1, 0, 3, null, v -> config.maxTilePixels = v));
		var chunkEntry = e.startIntField(label("chunkTickLimit"), config.chunkTickLimit)
			.setDefaultValue(100)
			.setMin(1)
			.setMax(4096)
			.setTooltip(tip("chunkTickLimit"))
			.setSaveConsumer(v -> config.chunkTickLimit = v)
			.build();
		map.addEntry(chunkEntry);
		LIVE.add(() -> config.chunkTickLimit = chunkEntry.getValue());

		ConfigCategory marks = builder.getOrCreateCategory(Text.translatable("roleplayers_atlas.category.marks"));
		var quickMarkEntry = e.startEnumSelector(label("quickMark"), AtlasConfig.QuickMark.class, config.quickMark)
			.setDefaultValue(AtlasConfig.QuickMark.LOOKING)
			.setEnumNameProvider(v -> Text.translatable("gui.roleplayers_atlas.config.quickMark." + v.name().toLowerCase()))
			.setTooltip(tip("quickMark"))
			.setSaveConsumer(v -> config.quickMark = v)
			.build();
		marks.addEntry(quickMarkEntry);
		LIVE.add(() -> config.quickMark = quickMarkEntry.getValue());
		// Whatever icons the resource pack actually ships, read at the moment the
		// screen opens rather than listed here and left to rot.
		List<String> icons = glam.ardor.roleplayers_atlas.QuickMark.icons();
		if (!icons.isEmpty()) {
			var iconEntry = e.startSelector(label("quickMarkIcon"), icons.toArray(new String[0]),
					icons.contains(config.quickMarkIcon) ? config.quickMarkIcon : icons.getFirst())
				.setDefaultValue("red_x_small")
				.setNameProvider(v -> Text.literal(org.apache.commons.lang3.text.WordUtils.capitalizeFully(v.replace('_', ' '))))
				.setTooltip(tip("quickMarkIcon"))
				.setSaveConsumer(v -> config.quickMarkIcon = v)
				.build();
			marks.addEntry(iconEntry);
			LIVE.add(() -> config.quickMarkIcon = iconEntry.getValue());
		}
		marks.addEntry(intSlider(e, "quickMarkRange", config.quickMarkRange, 256, 16, 512,
			v -> Text.translatable("config.roleplayers_atlas.blocks", v), v -> config.quickMarkRange = v));
		var sortEntry = e.startEnumSelector(label("markerSort"), AtlasConfig.MarkerSort.class, config.markerSort)
			.setDefaultValue(AtlasConfig.MarkerSort.KIND)
			.setEnumNameProvider(v -> Text.translatable("gui.roleplayers_atlas.sort." + v.name().toLowerCase()))
			.setTooltip(tip("markerSort"))
			.setSaveConsumer(v -> config.markerSort = v)
			.build();
		marks.addEntry(sortEntry);
		LIVE.add(() -> config.markerSort = sortEntry.getValue());
		var reckoningEntry = e.startEnumSelector(label("reckoning"), AtlasConfig.Reckoning.class, config.reckoning)
			.setDefaultValue(AtlasConfig.Reckoning.REIGN)
			.setEnumNameProvider(v -> Text.translatable("gui.roleplayers_atlas.config.reckoning." + v.name().toLowerCase()))
			.setTooltip(tip("reckoning"))
			.setSaveConsumer(v -> config.reckoning = v)
			.build();
		marks.addEntry(reckoningEntry);
		LIVE.add(() -> config.reckoning = reckoningEntry.getValue());
		marks.addEntry(toggle(e, "showMarkDate", config.showMarkDate, true, v -> config.showMarkDate = v));
		marks.addEntry(toggle(e, "showRealDate", config.showRealDate, true, v -> config.showRealDate = v));
		marks.addEntry(toggle(e, "deathMarkers", config.deathMarkers, true, v -> config.deathMarkers = v));
		marks.addEntry(toggle(e, "hideDeathsByDefault", config.hideDeathsByDefault, false, v -> config.hideDeathsByDefault = v));
		var graveEntry = e.startEnumSelector(label("graveStyle"), AtlasConfig.GraveStyle.class, config.graveStyle)
			.setDefaultValue(AtlasConfig.GraveStyle.EUPHEMISMS)
			.setEnumNameProvider(v -> Text.translatable("gui.roleplayers_atlas.config.graveStyle." + v.name().toLowerCase()))
			.setTooltip(tip("graveStyle"))
			.setSaveConsumer(v -> config.graveStyle = v)
			.build();
		marks.addEntry(graveEntry);
		LIVE.add(() -> config.graveStyle = graveEntry.getValue());
		// Only whether the hearth exists at all. Its icon, colour and opacity are
		// restyled from the atlas itself, like any other mark.
		marks.addEntry(toggle(e, "spawnMarker", config.spawnMarker, true, v -> config.spawnMarker = v));
		marks.addEntry(toggle(e, "hearthFollowsBeds", config.hearthFollowsBeds, false, v -> config.hearthFollowsBeds = v));
		marks.addEntry(intSlider(e, "guideArrowOpacity", config.guideArrowOpacity, 100, 10, 100,
			v -> Text.translatable("config.roleplayers_atlas.percent", v), v -> config.guideArrowOpacity = v));
		marks.addEntry(toggle(e, "clearTrackingOnArrival", config.clearTrackingOnArrival, true, v -> config.clearTrackingOnArrival = v));

		ConfigCategory titles = builder.getOrCreateCategory(Text.translatable("roleplayers_atlas.category.titles"));
		titles.addEntry(toggle(e, "zoneTitles", config.zoneTitles, true, v -> config.zoneTitles = v));
		titles.addEntry(toggle(e, "zoneTitleSound", config.zoneTitleSound, false, v -> config.zoneTitleSound = v));
		titles.addEntry(intSlider(e, "zoneTitleRadius", config.zoneTitleRadius, 32, 4, 256,
			v -> Text.translatable("config.roleplayers_atlas.blocks", v), v -> config.zoneTitleRadius = v));
		titles.addEntry(intSlider(e, "zoneTitleSeconds", config.zoneTitleSeconds, 3, 1, 30,
			v -> Text.translatable("config.roleplayers_atlas.seconds", v), v -> config.zoneTitleSeconds = v));

		ConfigCategory terrain = builder.getOrCreateCategory(Text.translatable("roleplayers_atlas.category.terrain"));
		terrain.addEntry(intField(e, "elevationLow", config.elevationLow, 10, v -> config.elevationLow = v));
		terrain.addEntry(intField(e, "elevationMid", config.elevationMid, 20, v -> config.elevationMid = v));
		terrain.addEntry(intField(e, "elevationHigh", config.elevationHigh, 50, v -> config.elevationHigh = v));
		terrain.addEntry(intField(e, "elevationPeak", config.elevationPeak, 90, v -> config.elevationPeak = v));
		// CRASH is a resource pack author's tool — it stops the game dead on a
		// biome with no texture. Left out of the list so a player can't pick it
		// by accident; the config file still takes it.
		AtlasConfig.FallbackHandling[] fallbacks = Arrays.stream(AtlasConfig.FallbackHandling.values())
			.filter(v -> v != AtlasConfig.FallbackHandling.CRASH)
			.toArray(AtlasConfig.FallbackHandling[]::new);
		AtlasConfig.FallbackHandling fallback = config.fallbackFailHandling == AtlasConfig.FallbackHandling.CRASH
			? AtlasConfig.FallbackHandling.MISSING
			: config.fallbackFailHandling;
		var fallbackEntry = e.startSelector(label("fallbackFailHandling"), fallbacks, fallback)
			.setDefaultValue(AtlasConfig.FallbackHandling.MISSING)
			.setNameProvider(v -> Text.translatable("gui.roleplayers_atlas.config.fallbackFailHandling." + v.name().toLowerCase()))
			.setTooltip(tip("fallbackFailHandling"))
			.setSaveConsumer(v -> config.fallbackFailHandling = v)
			.build();
		terrain.addEntry(fallbackEntry);
		LIVE.add(() -> config.fallbackFailHandling = fallbackEntry.getValue());

		Screen screen = builder.build();
		LivePreview.start(screen, LIVE);
		SettingsLook.arm(screen);
		return screen;
	}

	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> toggle(ConfigEntryBuilder e,
			String key, boolean value, boolean fallback, Consumer<Boolean> save) {
		var entry = e.startBooleanToggle(label(key), value)
			.setDefaultValue(fallback)
			.setTooltip(tip(key))
			.setSaveConsumer(save)
			.build();
		LIVE.add(() -> save.accept(entry.getValue()));
		return entry;
	}

	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> intSlider(ConfigEntryBuilder e,
			String key, int value, int fallback, int min, int max,
			java.util.function.IntFunction<Text> display, Consumer<Integer> save) {
		var builder = e.startIntSlider(label(key), value, min, max)
			.setDefaultValue(fallback)
			.setTooltip(tip(key))
			.setSaveConsumer(save);
		if (display != null) builder.setTextGetter(v -> display.apply(v));
		var entry = builder.build();
		LIVE.add(() -> save.accept(entry.getValue()));
		return entry;
	}

	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> intField(ConfigEntryBuilder e,
			String key, int value, int fallback, Consumer<Integer> save) {
		var entry = e.startIntField(label(key), value)
			.setDefaultValue(fallback).setMin(-64).setMax(320)
			.setTooltip(tip(key))
			.setSaveConsumer(save)
			.build();
		LIVE.add(() -> save.accept(entry.getValue()));
		return entry;
	}
}
