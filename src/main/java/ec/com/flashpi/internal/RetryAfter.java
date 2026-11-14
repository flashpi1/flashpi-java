package ec.com.flashpi.internal;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code Retry-After} header: delta-seconds or an HTTP date.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public final class RetryAfter {
  private static final Pattern DELTA_SECONDS = Pattern.compile("[0-9]+");

  /** Longer digit strings are past any useful wait (and past a long). */
  private static final int MAX_DELTA_DIGITS = 18;

  /** RFC 850: {@code Sunday, 06-Nov-94 08:49:37 GMT}. */
  private static final Pattern RFC_850 =
      Pattern.compile("[A-Za-z]+, (\\d{2})-([A-Za-z]{3})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2}) GMT");

  /** asctime: {@code Sun Nov 6 08:49:37 1994}. */
  private static final Pattern ASCTIME =
      Pattern.compile("[A-Za-z]{3} ([A-Za-z]{3}) +(\\d{1,2}) (\\d{2}):(\\d{2}):(\\d{2}) (\\d{4})");

  private static final String MONTHS = "janfebmaraprmayjunjulaugsepoctnovdec";

  private RetryAfter() {}

  /**
   * The wait a {@code Retry-After} value asks for.
   *
   * @param value the header's value, or null
   * @param now the current time, for an HTTP date
   * @return the wait (zero for a date in the past), or null when there is none or it is not valid
   */
  public static Duration parse(String value, Instant now) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (DELTA_SECONDS.matcher(trimmed).matches()) {
      if (trimmed.length() > MAX_DELTA_DIGITS) {
        return Duration.ofSeconds(Long.parseLong("1" + "0".repeat(MAX_DELTA_DIGITS)));
      }
      return Duration.ofSeconds(Long.parseLong(trimmed));
    }
    Instant moment = date(trimmed);
    if (moment == null) {
      return null;
    }
    Duration wait = Duration.between(now, moment);
    if (wait.isNegative()) {
      return Duration.ZERO;
    }
    // Whole seconds, rounded up.
    return wait.getNano() == 0 ? wait : Duration.ofSeconds(wait.getSeconds() + 1);
  }

  /**
   * Unix time of an HTTP date in any of the three forms RFC 9110 asks recipients to accept
   * (IMF-fixdate, RFC 850 and asctime), or of an ISO 8601 date and time (UTC without an offset), as
   * Python's {@code parsedate_to_datetime} and {@code fromisoformat} read them.
   */
  private static Instant date(String value) {
    try {
      return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (DateTimeParseException error) {
      // Other forms below.
    }
    Matcher rfc850 = RFC_850.matcher(value);
    if (rfc850.matches()) {
      int year = Integer.parseInt(rfc850.group(3));
      // Two-digit years as Python's email.utils reads them.
      year += year > 68 ? 1900 : 2000;
      return instant(
          year,
          rfc850.group(2),
          rfc850.group(1),
          rfc850.group(4),
          rfc850.group(5),
          rfc850.group(6));
    }
    Matcher asctime = ASCTIME.matcher(value);
    if (asctime.matches()) {
      return instant(
          Integer.parseInt(asctime.group(6)),
          asctime.group(1),
          asctime.group(2),
          asctime.group(3),
          asctime.group(4),
          asctime.group(5));
    }
    String iso =
        value.length() > 10 && value.charAt(10) == ' '
            ? value.substring(0, 10) + "T" + value.substring(11)
            : value;
    try {
      return OffsetDateTime.parse(iso).toInstant();
    } catch (DateTimeParseException error) {
      try {
        return LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC);
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
  }

  private static Instant instant(
      int year, String month, String day, String hour, String minute, String second) {
    int number = MONTHS.indexOf(month.toLowerCase(Locale.ROOT));
    if (number < 0 || number % 3 != 0) {
      return null;
    }
    try {
      return LocalDateTime.of(
              year,
              number / 3 + 1,
              Integer.parseInt(day),
              Integer.parseInt(hour),
              Integer.parseInt(minute),
              Integer.parseInt(second))
          .toInstant(ZoneOffset.UTC);
    } catch (DateTimeException error) {
      return null;
    }
  }
}
