package ec.com.flashpi;

import ec.com.flashpi.internal.Timing;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** What the tests share: clients on a fake HttpClient, with a fake clock. */
final class Support {
  static final String KEY = "fp_test_0123456789abcdefghijklmnop";
  static final String DOCUMENT_ID = "01a00000-0000-7000-8000-000000000001";

  static final String DOCUMENT =
      "{\"id\":\""
          + DOCUMENT_ID
          + "\",\"type\":\"invoice\",\"environment\":\"test\",\"status\":\"authorized\","
          + "\"accessKey\":\"1311202601179000000000110010010000000011234567811\","
          + "\"issueDate\":\"2026-11-13\",\"createdAt\":\"2026-11-13T15:04:05.123Z\"}";

  private Support() {}

  /** A clock that moves only when the SDK sleeps (or a test advances it). */
  static final class FakeTiming implements Timing {
    long nanos = 1_000_000_000L;
    Instant now = Instant.parse("2026-11-13T12:00:00Z");
    double random;
    final List<Duration> sleeps = new ArrayList<>();

    @Override
    public long nanoTime() {
      return nanos;
    }

    @Override
    public Instant now() {
      return now;
    }

    @Override
    public void sleep(Duration duration) {
      sleeps.add(duration);
      advance(duration);
    }

    @Override
    public double random() {
      return random;
    }

    void advance(Duration duration) {
      nanos += duration.toNanos();
      now = now.plus(duration);
    }
  }

  static Flashpi.Builder builder(FakeHttpClient http, FakeTiming timing) {
    return Flashpi.builder()
        .apiKey(KEY)
        .baseUrl("https://api.test")
        .httpClient(http)
        .timing(timing)
        .environmentVariables(name -> null);
  }

  static Flashpi client(FakeHttpClient http) {
    return builder(http, new FakeTiming()).build();
  }

  static Flashpi client(FakeHttpClient http, FakeTiming timing) {
    return builder(http, timing).build();
  }

  static String list(String items, boolean hasMore, String nextCursor) {
    return "{\"data\":["
        + items
        + "],\"pagination\":{\"hasMore\":"
        + hasMore
        + ",\"nextCursor\":"
        + (nextCursor == null ? "null" : "\"" + nextCursor + "\"")
        + "}}";
  }
}
