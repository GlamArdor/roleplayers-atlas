package glam.ardor.roleplayers_atlas;

import com.mojang.serialization.Codec;
import folk.sisby.surveyor.landmark.component.LandmarkComponentType;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import net.minecraft.text.Text;

/** Custom landmark components added by the atlas. */
public final class AtlasComponents {
	/**
	 * Whether entering the marker's area shows its name as a zone title.
	 * Absent means enabled; only an explicit false is stored.
	 */
	public static final LandmarkComponentType<Boolean> ZONE_TITLE = LandmarkComponentTypes.register(RoleplayersAtlas.id("zone_title"), Codec.BOOL, b -> Text.literal(String.valueOf(b)));

	/** Zone radius in blocks; absent means the config default. */
	public static final LandmarkComponentType<Integer> ZONE_RADIUS = LandmarkComponentTypes.register(RoleplayersAtlas.id("zone_radius"), Codec.INT, i -> Text.literal(i + "m"));

	/** Marker opacity on the map, 0..100 percent; absent means 100. */
	public static final LandmarkComponentType<Integer> OPACITY = LandmarkComponentTypes.register(RoleplayersAtlas.id("opacity"), Codec.INT, i -> Text.literal(i + "%"));

	/** Whether the marker's name label is hidden on the map. */
	public static final LandmarkComponentType<Boolean> HIDE_LABEL = LandmarkComponentTypes.register(RoleplayersAtlas.id("hide_label"), Codec.BOOL, b -> Text.literal(String.valueOf(b)));

	/** A free-standing pen inscription: no icon, just its name written on the map at its position. */
	public static final LandmarkComponentType<Boolean> PEN_LABEL = LandmarkComponentTypes.register(RoleplayersAtlas.id("pen_label"), Codec.BOOL, b -> Text.literal(String.valueOf(b)));

	/** Marker layer id; absent means the personal layer. */
	public static final LandmarkComponentType<String> LAYER = LandmarkComponentTypes.register(RoleplayersAtlas.id("layer"), Codec.STRING, Text::literal);

	/** Free-text note attached to a marker, shown in its hover tooltip. */
	public static final LandmarkComponentType<String> NOTE = LandmarkComponentTypes.register(RoleplayersAtlas.id("note"), Codec.STRING, Text::literal);

	/** A drawn route: the ordered control points of a path across the map. */
	public static final LandmarkComponentType<java.util.List<net.minecraft.util.math.BlockPos>> ROUTE = LandmarkComponentTypes.register(RoleplayersAtlas.id("route"), Codec.list(net.minecraft.util.math.BlockPos.CODEC), points -> Text.literal(points.size() + " pts"));

	/** Whether a route shows its length on hover; absent means shown. */
	public static final LandmarkComponentType<Boolean> SHOW_DISTANCE = LandmarkComponentTypes.register(RoleplayersAtlas.id("show_distance"), Codec.BOOL, b -> Text.literal(String.valueOf(b)));

	/** The game day the mark was drawn on — see {@link AtlasTime} for why game time and not time of day. */
	public static final LandmarkComponentType<Long> DAY = LandmarkComponentTypes.register(RoleplayersAtlas.id("day"), Codec.LONG, d -> Text.literal(String.valueOf(d)));

	/** The real-world moment the mark was drawn, epoch millis. A caption only — never an ordering key. */
	public static final LandmarkComponentType<Long> REAL_TIME = LandmarkComponentTypes.register(RoleplayersAtlas.id("real_time"), Codec.LONG, t -> Text.literal(String.valueOf(t)));

	/** Whether the date stamp is shown; absent means shown. */
	public static final LandmarkComponentType<Boolean> SHOW_DATE = LandmarkComponentTypes.register(RoleplayersAtlas.id("show_date"), Codec.BOOL, b -> Text.literal(String.valueOf(b)));

	/** The cartographer whose scroll this mark was copied from; absent means drawn by hand. */
	public static final LandmarkComponentType<String> SOURCE = LandmarkComponentTypes.register(RoleplayersAtlas.id("source"), Codec.STRING, Text::literal);

	/** The game day the owner stood at a mark they had only heard of; absent means still unverified. */
	public static final LandmarkComponentType<Long> CONFIRMED_DAY = LandmarkComponentTypes.register(RoleplayersAtlas.id("confirmed_day"), Codec.LONG, d -> Text.literal(String.valueOf(d)));

	/** The real-world moment that walk happened, epoch millis — what the server's reckoning dates it by. Absent on marks verified before the reckoning existed. */
	public static final LandmarkComponentType<Long> CONFIRMED_REAL_TIME = LandmarkComponentTypes.register(RoleplayersAtlas.id("confirmed_real_time"), Codec.LONG, t -> Text.literal(String.valueOf(t)));

	/** Extra rotation of the mark's map label, in degrees; absent means none. Used by territory names and pen inscriptions. */
	public static final LandmarkComponentType<Integer> LABEL_ROTATION = LandmarkComponentTypes.register(RoleplayersAtlas.id("label_rotation"), Codec.INT, d -> Text.literal(d + "°"));

	/** Whether the label's shadow (or the dark backing behind a route name) is hidden. */
	public static final LandmarkComponentType<Boolean> LABEL_NO_SHADOW = LandmarkComponentTypes.register(RoleplayersAtlas.id("label_no_shadow"), Codec.BOOL, b -> Text.literal(String.valueOf(b)));

	private AtlasComponents() {
	}

	public static void init() {
	}
}
