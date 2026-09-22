package glam.ardor.roleplayers_atlas;

import folk.sisby.kaleido.api.WrappedConfig;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.Comment;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.IntegerRange;
import folk.sisby.kaleido.lib.quiltconfig.api.values.ValueMap;
import folk.sisby.surveyor.client.SurveyorClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("CanBeFinal")
public class AtlasConfig extends WrappedConfig {
	public enum GraveStyle {
		CAUSE,
		GRAVE,
		ITEMS,
		DIED,
		EUPHEMISMS
	}

	public enum FallbackHandling {
		TEST,
		MISSING,
		PLAINS,
		CRASH
	}

	public enum EmptyHandling {
		CLOUDS,
		EMPTY
	}

	/**
	 * Where the quick-mark key lands. Under your feet is the obvious one; where
	 * you are looking is the useful one — a peak on the horizon can be marked
	 * from the valley you are standing in, without walking to it first.
	 */
	public enum QuickMark {
		AT_PLAYER,
		LOOKING
	}

	/**
	 * Which calendar dates a mark.
	 * <p>
	 * REIGN is the reckoning kept on Reign RP — cycles, unias and years, worked
	 * out from the real moment the mark was drawn. DAYS is the world's own day
	 * count, which is what the atlas used before the server opened.
	 */
	public enum Reckoning {
		REIGN,
		DAYS
	}

	/**
	 * Whether the map can send you somewhere.
	 * <p>
	 * AUTO offers it only where the game says you may teleport — singleplayer
	 * with cheats, or an operator's seat on a server. ON is for servers that
	 * hand the command out through a permissions plugin without the operator
	 * rank the client can see; OFF keeps the map a map.
	 */
	public enum Teleport {
		AUTO,
		ON,
		OFF
	}

	/** How the bookmark list is ordered. */
	public enum MarkerSort {
		KIND,
		DISTANCE,
		DATE,
		NAME
	}

	/** Sets a top-level config value by field name and writes it to disk (used by the settings screen). */
	@SuppressWarnings("unchecked")
	public void setAndSave(String key, Object value) {
		for (folk.sisby.kaleido.lib.quiltconfig.api.values.TrackedValue<?> tracked : this.values()) {
			if (tracked.key().toString().equals(key)) {
				((folk.sisby.kaleido.lib.quiltconfig.api.values.TrackedValue<Object>) tracked).setValue(value, true);
			}
		}
	}

	/**
	 * Puts every option back the way it shipped. The tracked value is what gets
	 * written to disk, but the mod reads the plain field, so both are set — a
	 * reset that only touched one of them would either not apply until a
	 * restart or not survive one.
	 */
	@SuppressWarnings("unchecked")
	public void resetToDefaults() {
		for (folk.sisby.kaleido.lib.quiltconfig.api.values.TrackedValue<?> tracked : this.values()) {
			folk.sisby.kaleido.lib.quiltconfig.api.values.TrackedValue<Object> value = (folk.sisby.kaleido.lib.quiltconfig.api.values.TrackedValue<Object>) tracked;
			Object fallback = value.getDefaultValue();
			value.setValue(fallback, true);
			try {
				getClass().getField(tracked.key().toString()).set(this, fallback);
			} catch (Exception ignored) {
				// Nested sections and maps have no field of that name to mirror.
			}
		}
	}

	/**
	 * Writes every field back out to disk. The settings screen edits the plain
	 * fields and calls this once when the player saves, so cancelling leaves
	 * both the fields and the file untouched.
	 */
	@SuppressWarnings("unchecked")
	public void saveFields() {
		for (folk.sisby.kaleido.lib.quiltconfig.api.values.TrackedValue<?> tracked : this.values()) {
			try {
				Object current = getClass().getField(tracked.key().toString()).get(this);
				((folk.sisby.kaleido.lib.quiltconfig.api.values.TrackedValue<Object>) tracked).setValue(current, true);
			} catch (Exception ignored) {
				// Nested sections and maps have no field of that name to mirror.
			}
		}
	}

	@Comment("Whether to display the map in full-screen")
	@Comment("The background is slightly less stylish, but more tiles are shown at once")
	public boolean fullscreen = true;


	@Comment("Whether to keep scale after closing the map")
	public boolean keepZoom = false;

	@Comment("Whether to keep offset after closing the map")
	public boolean keepOffset = false;

	@Comment("Whether the line of controls appears at the foot of the map while a drawing tool is in hand")
	public boolean showHints = true;

	@Comment("Whether the atlas held in the hands is steadied against the walking sway")
	@Comment("Off by default: the book bobs in step with you, like any held map")
	public boolean stabilizeHeldMap = false;

	@Comment("Size of the atlas held in the hands, percent. The zoom-in/out keys adjust this live")
	@IntegerRange(min = 50, max = 200)
	public int handheldScale = 100;

	@Comment("Where the quick-mark key puts its mark: AT_PLAYER, or LOOKING at whatever you are facing")
	public QuickMark quickMark = QuickMark.LOOKING;

	@Comment("How far the quick-mark key looks for what you are facing, in blocks")
	@IntegerRange(min = 16, max = 512)
	public int quickMarkRange = 256;

	@Comment("Icon used for quick marks, from the custom marker set")
	public String quickMarkIcon = "red_x_small";

	@Comment("How the bookmark list is ordered: KIND, DISTANCE, DATE or NAME")
	public MarkerSort markerSort = MarkerSort.KIND;

	@Comment("Whether ctrl-clicking the map sends you there: AUTO where the game allows teleporting, ON always, OFF never")
	public Teleport teleport = Teleport.AUTO;

	@Comment("The command the map sends to travel, without its leading slash")
	@Comment("{x} {y} {z} are filled in; {dim} is the dimension the map is showing")
	@Comment("Servers running Essentials usually need: minecraft:tp @s {x} {y} {z}")
	public String teleportCommand = "tp @s {x} {y} {z}";

	@Comment("Which calendar dates a mark: REIGN for the server's cycles, unias and years, DAYS for the world's own day count")
	public Reckoning reckoning = Reckoning.REIGN;

	@Comment("Whether a mark shows the in-world date it was drawn on")
	public boolean showMarkDate = true;

	@Comment("Whether the real date and time follows it, in ((out-of-character brackets))")
	@Comment("Under the server's reckoning it is read on the Moscow clock the cycles are counted on")
	public boolean showRealDate = true;

	@Comment("Whether entering the area of a named marker shows its title on screen")
	public boolean zoneTitles = true;

	@Comment("Whether a faint chime plays when a zone title appears")
	public boolean zoneTitleSound = false;

	@Comment("How many seconds a zone title stays fully shown before it fades")
	@IntegerRange(min = 1, max = 30)
	public int zoneTitleSeconds = 3;

	@Comment("Least seconds between one zone title and the next of the same place")
	@Comment("Off by default (0), so titles appear at once as before; raise it to stop a title flickering when you cross a border back and forth")
	@IntegerRange(min = 0, max = 600)
	public int zoneTitleCooldown = 0;

	@Comment("Whether player death markers appear on the map")
	public boolean deathMarkers = true;

	@Comment("Whether the deaths layer starts hidden when joining a world")
	public boolean hideDeathsByDefault = false;

	@Comment("Opacity of guide arrows, percent")
	@IntegerRange(min = 10, max = 100)
	public int guideArrowOpacity = 100;

	@Comment("Whether a guide arrow stops tracking once you arrive at what it points to")
	public boolean clearTrackingOnArrival = true;

	@Comment("Whether your respawn point is marked on the map")
	public boolean spawnMarker = true;

	@Comment("Whether lying down in a bed moves the hearth mark to it.")
	@Comment("Off by default: plenty of servers let you sleep without moving your spawn, and there the bed would lie.")
	@Comment("Turn on for vanilla behaviour, where the hearth then moves without waiting for a death.")
	public boolean hearthFollowsBeds = false;

	@Comment("Icon used for the respawn point, from the custom marker set")
	public String spawnMarkerIcon = "bed";

	@Comment("Dye colour of the respawn point marker")
	public String spawnMarkerColor = "red";

	@Comment("Opacity of the respawn point marker, percent")
	@IntegerRange(min = 0, max = 100)
	public int spawnMarkerOpacity = 100;

	@Comment("How to depict player death locations.")
	public GraveStyle graveStyle = GraveStyle.EUPHEMISMS;

	// How far above the sea land has to stand to be drawn as each kind of
	// ground. The defaults suit a world built high, where vanilla's thresholds
	// would make everything read as one endless peak.
	//
	// Edited here, they take effect when the game restarts; changed from the
	// settings screen, the map redraws itself straight away.

	@Comment("Height above sea level, in blocks, at which land stops being drawn as a valley")
	@Comment("Value suited to vanilla generation: 10")
	@Comment("Edits to this file apply on restart - the settings screen applies them at once")
	@IntegerRange(min = -64, max = 320)
	public int elevationLow = 10;

	@Comment("Height above sea level, in blocks, at which land starts being drawn as middling")
	@Comment("Value suited to vanilla generation: 20")
	@Comment("Edits to this file apply on restart - the settings screen applies them at once")
	@IntegerRange(min = -64, max = 320)
	public int elevationMid = 20;

	@Comment("Height above sea level, in blocks, at which land starts being drawn as high")
	@Comment("Value suited to vanilla generation: 35")
	@Comment("Edits to this file apply on restart - the settings screen applies them at once")
	@IntegerRange(min = -64, max = 320)
	public int elevationHigh = 50;

	@Comment("Height above sea level, in blocks, at which land starts being drawn as peaks")
	@Comment("Value suited to vanilla generation: 50")
	@Comment("Edits to this file apply on restart - the settings screen applies them at once")
	@IntegerRange(min = -64, max = 320)
	public int elevationPeak = 90;

	@Comment("The maximum number of chunks to represent as a tile, as a power of 2")
	@Comment("Effectively the 'minimum zoom'")
	@Comment("0: 1x1 chunk = 1 tile | 6: 64x64 chunks = 1 tile")
	@IntegerRange(min = 0, max = 6)
	public int maxTileChunks = 5;

	@Comment("The maximum size to render a tile at, as a power of 2 multiplier")
	@Comment("Effectively the 'maximum zoom'")
	@Comment("0: 1 tile = 16x16 | 3: 1 tile = 128x128")
	@IntegerRange(min = 0, max = 3)
	public int maxTilePixels = 1;

	@Comment("The effective GUI scale for tiles and markers - independent of the overall GUI scale.")
	@Comment("0 will match your GUI scale - pixels will be the same size as the background & buttons")
	@Comment("-1 will use half your GUI scale, rounding up.")
	@Comment("-2 will use half your GUI scale, rounding down.")
	@IntegerRange(min = -2, max = 10)
	public int mapScale = 0;

	@Comment("The maximum number of chunks to load onto the map per tick after entering a world")
	public int chunkTickLimit = 100;

	@Comment("How to handle biomes that aren't in any minecraft, conventional, or forge biome tags")
	public FallbackHandling fallbackFailHandling = FallbackHandling.MISSING;

	@Comment("How to display areas that aren't explored yet")
	public EmptyHandling emptyHandling = EmptyHandling.EMPTY;

	public Map<String, Boolean> structureMarkers = ValueMap.builder(true)
		.put("minecraft:type/end_city", false)
		.build();

	@Comment("Options to adjust map behaviour for custom or modified dimensions.")
	public Dimensions dimensions = new Dimensions();

	public static class Dimensions implements Section {
		@Comment("Cycle order and coordinate scales of each dimension.")
		@Comment("If not 0, the relative position of the player will be shown.")
		public Map<String, Integer> scales = ValueMap.builder(0)
			.put("minecraft:overworld", 8)
			.put("minecraft:the_nether", 1)
			.put("minecraft:the_end", 0)
			.build();

		public List<RegistryKey<World>> getOrder(ClientPlayNetworkHandler handler) {
			List<RegistryKey<World>> dims = new ArrayList<>(SurveyorClient.getSummaries(handler).keySet().stream().sorted(Comparator.comparing(RegistryKey::toString)).toList());
			dims.removeIf(WorldAtlasData::isEmpty);
			scales.keySet().removeIf(v -> handler.getWorldKeys().stream().noneMatch(d -> d.getValue().toString().equals(v)));
			dims.stream().filter(dim -> !scales.containsKey(dim.getValue().toString())).forEach(dim -> scales.put(dim.getValue().toString(), 0));
			return dims.stream().sorted(Comparator.comparing(dim -> scales.keySet().stream().toList().indexOf(dim.getValue().toString()))).toList();
		}

		public Map<RegistryKey<World>, Integer> getScales(ClientPlayNetworkHandler handler) {
			List<RegistryKey<World>> dims = new ArrayList<>(SurveyorClient.getSummaries(handler).keySet().stream().sorted(Comparator.comparing(RegistryKey::toString)).toList());
			dims.removeIf(WorldAtlasData::isEmpty);
			scales.keySet().removeIf(v -> handler.getWorldKeys().stream().noneMatch(d -> d.getValue().toString().equals(v)));
			dims.stream().filter(dim -> !scales.containsKey(dim.getValue().toString())).forEach(dim -> scales.put(dim.getValue().toString(), 0));
			return dims.stream().collect(Collectors.toMap(k -> k, k -> scales.get(k.getValue().toString())));
		}
	}
}
