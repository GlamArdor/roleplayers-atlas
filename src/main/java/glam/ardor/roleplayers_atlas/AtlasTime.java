package glam.ardor.roleplayers_atlas;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Dating for everything drawn on the map.
 * <p>
 * Two reckonings, chosen in the settings. On {@link AtlasConfig.Reckoning#REIGN}
 * a mark is dated in the server's own cycles, unias and years — see
 * {@link ReignCalendar} — worked out from the real moment it was drawn, which
 * is what the server's calendar is itself pinned to. On
 * {@link AtlasConfig.Reckoning#DAYS} it is dated by the world's day count, as
 * the atlas did before the server opened.
 * <p>
 * The world day comes from the world's <em>game</em> time rather than its time
 * of day: a server can freeze the day cycle or hand each player their own time
 * of day, while game time keeps ticking, is the same for everyone connected,
 * and survives restarts.
 * <p>
 * The real-world moment is stored as an instant, so it reads correctly whoever
 * wrote the mark and in whatever zone — but it is only ever a caption, never
 * something the mod sorts or reasons by.
 */
public final class AtlasTime {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private AtlasTime() {
	}

	public static long gameDay() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) return 0;
		return Math.max(0, client.world.getTime() / 24000L);
	}

	public static long realMillis() {
		return System.currentTimeMillis();
	}

	private static AtlasConfig.Reckoning reckoning() {
		return RoleplayersAtlas.CONFIG.reckoning;
	}

	/**
	 * The zone the out-of-character stamp is read in. Under the server's
	 * reckoning that is Moscow, because the cycle printed beside it is counted
	 * on the Moscow clock and the two would otherwise contradict each other.
	 * Under the plain day count nothing is pinned to Moscow, so it stays the
	 * viewer's own zone, as it was before.
	 */
	private static ZoneId oocZone() {
		return reckoning() == AtlasConfig.Reckoning.REIGN ? ReignCalendar.ZONE : ZoneId.systemDefault();
	}

	/** "15.09.2026, 22:00 MSK" — the out-of-character half of a stamp, brackets not included. */
	private static String realDateTime(long millis) {
		if (millis <= 0) return "";
		try {
			var moment = Instant.ofEpochMilli(millis).atZone(oocZone());
			String date = moment.format(DATE);
			String time = moment.format(TIME);
			return reckoning() == AtlasConfig.Reckoning.REIGN
				? Text.translatable("gui.roleplayers_atlas.marker.date.moscow", date, time).getString()
				: Text.translatable("gui.roleplayers_atlas.marker.date.local", date, time).getString();
		} catch (Exception e) {
			return "";
		}
	}

	/**
	 * The in-character half of a stamp: "12 цикл 15 унии 226 года", or "Day 214"
	 * under the plain reckoning.
	 * <p>
	 * The server's reckoning is derived from the real moment, and a mark drawn
	 * before this update — or a grave dug before it, which never had one — has
	 * no real moment recorded. Its world day cannot be turned into a cycle: the
	 * day counter says how long the world has been running, not when anyone was
	 * looking at it. Rather than invent a date, such a mark says it was drawn
	 * before the expedition, which is the one thing about it that is certainly
	 * true.
	 */
	public static Text inWorldDate(Long day, Long realTime) {
		if (reckoning() == AtlasConfig.Reckoning.REIGN) {
			if (realTime == null || realTime <= 0) return Text.translatable("gui.roleplayers_atlas.marker.date.beforeExpedition");
			ReignCalendar.Date date = ReignCalendar.of(realTime);
			return Text.translatable("gui.roleplayers_atlas.marker.date.cycle", date.cycle(), date.unia(), date.year());
		}
		return Text.translatable("gui.roleplayers_atlas.marker.date.day", day == null ? 0L : day);
	}

	/** Whether either half of a stamp would say anything at all. */
	public static boolean datingShown() {
		return RoleplayersAtlas.CONFIG.showMarkDate || RoleplayersAtlas.CONFIG.showRealDate;
	}

	/**
	 * A full date line: the in-world date, the real one in out-of-character
	 * brackets, or both. Null when the settings have both halves turned off,
	 * or when neither half has anything to say.
	 */
	public static MutableText dateText(Long day, Long realTime) {
		MutableText inWorld = RoleplayersAtlas.CONFIG.showMarkDate ? inWorldDate(day, realTime).copy() : null;
		String ooc = RoleplayersAtlas.CONFIG.showRealDate ? realDateTime(realTime == null ? 0L : realTime) : "";
		if (inWorld == null && ooc.isEmpty()) return null;
		if (inWorld == null) return Text.translatable("gui.roleplayers_atlas.marker.date.ooc", ooc);
		if (ooc.isEmpty()) return inWorld;
		return inWorld.append(" ").append(Text.translatable("gui.roleplayers_atlas.marker.date.ooc", ooc));
	}

	/** Today's date line, for the seal on an exported scroll. */
	public static String stampNow() {
		MutableText text = dateText(gameDay(), realMillis());
		return text == null ? "" : text.getString();
	}

	/** "Со слов Name, 12 цикл 15 унии 226 года ((...))" for marks copied from someone else's scroll. */
	public static Text hearsay(String author, Long day, Long realTime) {
		MutableText text = dateText(day, realTime);
		return text == null
			? Text.translatable("gui.roleplayers_atlas.marker.hearsayPlain", author)
			: Text.translatable("gui.roleplayers_atlas.marker.hearsayDated", author, text);
	}

	/** The name this client signs its scrolls with. */
	public static String selfName() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player == null ? "" : client.player.getGameProfile().name();
	}

	/** Whether a mark came from someone else's hand. Stays true after it's verified — who told you doesn't change. */
	public static boolean isHearsay(folk.sisby.surveyor.landmark.Landmark landmark) {
		String source = landmark.get(AtlasComponents.SOURCE);
		return source != null && !source.isEmpty() && !source.equals(selfName());
	}

	/** Hearsay nobody has gone and checked yet — this is what the map draws faint. */
	public static boolean isUnverified(folk.sisby.surveyor.landmark.Landmark landmark) {
		return isHearsay(landmark) && landmark.get(AtlasComponents.CONFIRMED_DAY) == null;
	}
}
