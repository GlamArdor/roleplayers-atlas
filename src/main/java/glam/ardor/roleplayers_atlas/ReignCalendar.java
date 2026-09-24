package glam.ardor.roleplayers_atlas;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The reckoning kept on Reign RP, worked out from the real moment a mark was
 * drawn.
 * <p>
 * Three units, all of them fixed to the Moscow clock the server runs on:
 * <ul>
 *   <li><b>Цикл</b> – one setting and rising of the sun, two elven hours. Two
 *       real hours long, so twelve of them fill a real day: 00:00–01:59 is the
 *       first, 22:00–23:59 the twelfth.</li>
 *   <li><b>Уния</b> – an elven day, twelve cycles. One real day, and it carries
 *       that day's number: 15 September is the 15th unia.</li>
 *   <li><b>Год</b> – an elven month, nominally thirty unias. One real calendar
 *       month, counted from the expedition: August 2026 is year 225, September
 *       2026 is 226, and so on backwards and forwards without limit.</li>
 * </ul>
 * A real month is 28 to 31 days rather than a clean thirty, so a year runs a
 * few unias short or long. That is deliberate: tying the unia to the number on
 * the calendar keeps every mark's date readable against the real one, which is
 * what players actually compare them by. The "360 cycles" of the lore is the
 * round figure, not the arithmetic.
 * <p>
 * Everything is derived from an instant, never from a running counter, so it
 * cannot drift, needs nothing stored, and dates a mark written on someone
 * else's machine in another timezone exactly as its author saw it.
 */
public final class ReignCalendar {
	/** The clock the server's cycles turn on. */
	public static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

	/** Real hours in one cycle. */
	private static final int CYCLE_HOURS = 2;

	/** The expedition: 24 August 2026 is the 24th unia of year 225. */
	private static final int EPOCH_YEAR = 2026;
	private static final int EPOCH_MONTH = 8;
	private static final int EPOCH_RECKONING_YEAR = 225;

	private ReignCalendar() {
	}

	/** A date in the server's reckoning. */
	public record Date(int cycle, int unia, int year) {
	}

	public static Date of(long millis) {
		return of(Instant.ofEpochMilli(millis).atZone(ZONE));
	}

	static Date of(ZonedDateTime moment) {
		return new Date(
			moment.getHour() / CYCLE_HOURS + 1,
			moment.getDayOfMonth(),
			EPOCH_RECKONING_YEAR + (moment.getYear() - EPOCH_YEAR) * 12 + (moment.getMonthValue() - EPOCH_MONTH)
		);
	}

	public static Date now() {
		return of(System.currentTimeMillis());
	}
}
