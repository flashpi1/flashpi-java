package ec.com.flashpi.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The clocks, the waits and the jitter of the requests, so tests can replace them.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public interface Timing {
  /**
   * A monotonic clock, for deadlines.
   *
   * @return nanoseconds from an arbitrary origin
   */
  long nanoTime();

  /**
   * The wall clock, for Retry-After dates.
   *
   * @return the current time
   */
  Instant now();

  /**
   * Waits before the next attempt.
   *
   * @param duration how long
   * @throws InterruptedException when the thread is interrupted
   */
  void sleep(Duration duration) throws InterruptedException;

  /**
   * A random number for the jitter of the backoff.
   *
   * @return a number from 0 (included) to 1 (excluded)
   */
  double random();

  /**
   * The system's clocks.
   *
   * @return the timing of real requests
   */
  static Timing system() {
    return SystemTiming.INSTANCE;
  }

  /** The system's clocks. */
  final class SystemTiming implements Timing {
    static final SystemTiming INSTANCE = new SystemTiming();

    private SystemTiming() {}

    @Override
    public long nanoTime() {
      return System.nanoTime();
    }

    @Override
    public Instant now() {
      return Instant.now();
    }

    @Override
    public void sleep(Duration duration) throws InterruptedException {
      Thread.sleep(duration.toMillis());
    }

    @Override
    public double random() {
      return ThreadLocalRandom.current().nextDouble();
    }
  }
}
