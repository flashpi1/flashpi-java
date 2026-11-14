package ec.com.flashpi.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

  private static Instant date(String value) {
    try {
      return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (DateTimeParseException error) {
      try {
        return OffsetDateTime.parse(value).toInstant();
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
  }
}
