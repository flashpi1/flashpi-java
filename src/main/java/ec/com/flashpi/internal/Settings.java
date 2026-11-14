package ec.com.flashpi.internal;

import ec.com.flashpi.exceptions.FlashpiException;
import java.time.Duration;

/**
 * Checks of the timeouts and retries of a client or a call. Bad ones are {@link FlashpiException}s,
 * as in the Node and Python SDKs.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public final class Settings {
  private Settings() {}

  /**
   * A timeout: more than zero and at most {@link Engine#MAX_TIMEOUT}.
   *
   * @param name the setting, for the message
   * @param timeout the timeout
   * @return the timeout
   */
  public static Duration timeout(String name, Duration timeout) {
    if (timeout == null
        || timeout.isNegative()
        || timeout.isZero()
        || timeout.compareTo(Engine.MAX_TIMEOUT) > 0) {
      throw new FlashpiException(name + " must be more than 0 and at most 2147483.647 seconds");
    }
    return timeout;
  }

  /**
   * A number of retries: 0 or more.
   *
   * @param name the setting, for the message
   * @param retries the retries
   * @return the retries
   */
  public static int retries(String name, int retries) {
    if (retries < 0) {
      throw new FlashpiException(name + " must be a whole number, 0 or more");
    }
    return retries;
  }
}
