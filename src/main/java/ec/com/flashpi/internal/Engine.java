package ec.com.flashpi.internal;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.Version;
import ec.com.flashpi.exceptions.ApiConnectionException;
import ec.com.flashpi.exceptions.ApiException;
import ec.com.flashpi.exceptions.ApiTimeoutException;
import ec.com.flashpi.exceptions.AuthenticationException;
import ec.com.flashpi.exceptions.BadRequestException;
import ec.com.flashpi.exceptions.ConflictException;
import ec.com.flashpi.exceptions.FlashpiException;
import ec.com.flashpi.exceptions.InternalServerException;
import ec.com.flashpi.exceptions.NotFoundException;
import ec.com.flashpi.exceptions.PermissionDeniedException;
import ec.com.flashpi.exceptions.RateLimitException;
import ec.com.flashpi.exceptions.UnprocessableEntityException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends the requests of a client: retries, timeouts, idempotency, recoveries and typed errors, the
 * rules of the Node and Python SDKs.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public final class Engine {
  /** The SDK's User-Agent. */
  public static final String USER_AGENT = "flashpi-java/" + Version.VERSION;

  /** The longest timeout: the Node SDK's limit (2147483647 ms), so both accept the same values. */
  public static final Duration MAX_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

  /**
   * The longest Retry-After the SDK waits for by itself; longer waits (daily quotas) are thrown.
   */
  static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);

  static final Duration INITIAL_RETRY_DELAY = Duration.ofMillis(500);
  static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(8);

  /** Waits between checks on an earlier attempt that the API is still running. */
  static final Duration IN_PROGRESS_FIRST_WAIT = Duration.ofSeconds(1);

  static final Duration IN_PROGRESS_MAX_WAIT = Duration.ofSeconds(5);
  static final String IN_PROGRESS = "idempotency_key_in_progress";

  /** Response headers a DELETE that found its resource gone does not keep from the 404. */
  private static final Set<String> BODY_HEADERS =
      Set.of("content-length", "content-type", "content-encoding", "transfer-encoding");

  private final ClientConfig config;
  private final HttpClient http;
  private final Timing timing;
  private volatile boolean closed;

  /**
   * An engine.
   *
   * @param config the client's settings
   * @param http the HTTP client, which must not follow redirects
   * @param timing the clocks and waits
   */
  public Engine(ClientConfig config, HttpClient http, Timing timing) {
    this.config = config;
    this.http = http;
    this.timing = timing;
  }

  /** Refuses every later request: the client closed the HttpClient it created. */
  public void close() {
    closed = true;
  }

  /**
   * Sends a request with its retries and recoveries.
   *
   * @param spec the request
   * @param <T> its data
   * @return its data and response
   * @throws FlashpiException when it fails
   */
  public <T> ApiResponse<T> request(RequestSpec<T> spec) {
    try {
      return send(spec);
    } finally {
      if (spec.multipart != null) {
        // The .p12 and its password: no copy of the SDK's outlives the call.
        spec.multipart.clear();
      }
    }
  }

  private <T> ApiResponse<T> send(RequestSpec<T> spec) {
    Call call = prepare(spec);
    if (closed) {
      FlashpiException error =
          new FlashpiException(
              "This Flashpi client is closed: create another one to send requests");
      error.setIdempotencyKey(call.idempotencyKey);
      throw error;
    }
    long started = timing.nanoTime();
    int attempt = 0;
    int retries = 0;
    int waits = 0;
    // Whether an earlier attempt may have reached the API without its answer reaching us (a
    // network error, a timeout, or a 5xx from a proxy). Only then can a 409
    // idempotency_key_in_progress be this call's own attempt still running.
    boolean unanswered = false;

    while (true) {
      FlashpiException failure;
      try {
        HttpResponse<byte[]> response = send(spec, call);
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
          T data = spec.reader.read(status, response.body());
          return new ApiResponse<>(
              data,
              status,
              response.headers(),
              response.headers().firstValue("x-request-id").orElse(null));
        }
        failure =
            apiError(
                status,
                response.headers().map(),
                new String(response.body(), StandardCharsets.UTF_8));
      } catch (ApiConnectionException error) {
        failure = error;
      } catch (FlashpiException error) {
        error.setIdempotencyKey(call.idempotencyKey);
        throw error;
      }
      failure.setIdempotencyKey(call.idempotencyKey);

      if (attempt > 0 && failure instanceof ApiException) {
        ApiException error = (ApiException) failure;
        String documentId = recoverableDocument(spec, error);
        if (documentId != null) {
          return recoverDocument(spec, call, documentId);
        }
        // A DELETE retried after a lost answer finds the resource already gone.
        if (spec.method.equals("DELETE") && error.getStatus() == 404) {
          return deleted(error);
        }
        // This call's earlier attempt is still running on the API (a sync issue whose answer was
        // lost): wait for it with the same key while the call has time left. These checks do not
        // count as retries; once the time is up, the normal retry rules apply.
        if (unanswered && error.getStatus() == 409 && IN_PROGRESS.equals(error.getCode())) {
          Duration wait =
              min(times(IN_PROGRESS_FIRST_WAIT, Math.min(waits, 16)), IN_PROGRESS_MAX_WAIT);
          if (timing.nanoTime() + wait.toNanos() - started <= call.timeout.toNanos()) {
            waits++;
            attempt++;
            sleep(wait, call);
            continue;
          }
        }
      }

      if (failure instanceof ApiConnectionException
          || (((ApiException) failure).getStatus() >= 500
              && ((ApiException) failure).getCode() == null)) {
        unanswered = true;
      }
      Duration delay = retryDelay(failure, retries);
      if (delay == null || retries >= call.maxRetries) {
        throw failure;
      }
      retries++;
      attempt++;
      sleep(delay, call);
    }
  }

  /** What every attempt of one call shares, resolved before the first one is sent. */
  private static final class Call {
    final RequestOptions options;
    final int maxRetries;
    final Duration timeout;
    final String url;
    final String idempotencyKey;
    final byte[] content;

    Call(
        RequestOptions options,
        int maxRetries,
        Duration timeout,
        String url,
        String idempotencyKey,
        byte[] content) {
      this.options = options;
      this.maxRetries = maxRetries;
      this.timeout = timeout;
      this.url = url;
      this.idempotencyKey = idempotencyKey;
      this.content = content;
    }
  }

  private Call prepare(RequestSpec<?> spec) {
    RequestOptions options = spec.options;
    int maxRetries =
        options.getMaxRetries() != null ? options.getMaxRetries() : config.maxRetries();
    Duration timeout = options.getTimeout() != null ? options.getTimeout() : config.timeout();
    if (timeout.compareTo(spec.minTimeout) < 0) {
      timeout = spec.minTimeout;
    }
    timeout = min(timeout, MAX_TIMEOUT);
    // Serialized once: every attempt sends the same bytes under the same Idempotency-Key, even
    // when the caller changes the model's lists meanwhile.
    byte[] content = spec.body == null || spec.multipart != null ? null : Json.encode(spec.body);
    return new Call(
        options, maxRetries, timeout, url(spec), idempotencyKey(spec.method, options), content);
  }

  private String url(RequestSpec<?> spec) {
    StringBuilder url = new StringBuilder(config.baseUrl()).append(spec.path);
    String separator = "?";
    for (Map.Entry<String, String> parameter : spec.query.entrySet()) {
      url.append(separator)
          .append(Paths.queryComponent(parameter.getKey()))
          .append('=')
          .append(Paths.queryComponent(parameter.getValue()));
      separator = "&";
    }
    return url.toString();
  }

  /** The caller's Idempotency-Key of a POST (option or header), or a new one. */
  private static String idempotencyKey(String method, RequestOptions options) {
    if (!method.equals("POST")) {
      return null;
    }
    if (options.getIdempotencyKey() != null) {
      return options.getIdempotencyKey();
    }
    String header = null;
    for (Map.Entry<String, String> entry : options.getExtraHeaders().entrySet()) {
      if (entry.getKey().equalsIgnoreCase("idempotency-key")) {
        header = entry.getValue();
      }
    }
    return header != null ? header : UUID.randomUUID().toString();
  }

  /** The headers of an attempt, from the lowest priority to the highest. */
  private Map<String, String> headers(RequestSpec<?> spec, Call call) {
    Headers.CaseInsensitiveMap headers = new Headers.CaseInsensitiveMap();
    headers.put("Accept", spec.accept);
    headers.put("User-Agent", USER_AGENT);
    // Client language, client headers, request language, request headers, then the SDK's own.
    if (config.language() != null) {
      headers.put("Accept-Language", config.language().value());
    }
    headers.putAll(config.defaultHeaders());
    RequestOptions options = call.options;
    if (options.getLanguage() != null) {
      headers.put("Accept-Language", options.getLanguage().value());
    }
    for (Map.Entry<String, String> header : options.getExtraHeaders().entrySet()) {
      headers.put(header.getKey(), header.getValue());
    }
    if (options.getCorrelationId() != null) {
      headers.put("X-Correlation-Id", options.getCorrelationId());
    }
    if (call.idempotencyKey != null) {
      headers.put("Idempotency-Key", call.idempotencyKey);
    }
    if (spec.multipart != null) {
      headers.put("Content-Type", spec.multipart.contentType());
    } else if (call.content != null) {
      headers.put("Content-Type", "application/json");
    }
    headers.put("Authorization", "Bearer " + config.apiKey());
    return headers;
  }

  private HttpRequest build(RequestSpec<?> spec, Call call) {
    HttpRequest.BodyPublisher body;
    if (spec.multipart != null) {
      body = HttpRequest.BodyPublishers.ofByteArray(spec.multipart.bytes());
    } else if (call.content != null) {
      body = HttpRequest.BodyPublishers.ofByteArray(call.content);
    } else {
      body = HttpRequest.BodyPublishers.noBody();
    }
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(call.url))
              .timeout(call.timeout)
              .method(spec.method, body);
      // Not kept in a local or a field: the headers carry the API key.
      for (Map.Entry<String, String> header : headers(spec, call).entrySet()) {
        builder.setHeader(header.getKey(), header.getValue());
      }
      return builder.build();
    } catch (IllegalArgumentException error) {
      // Without the client's reason, which may quote a header: that may be the Authorization one.
      throw new FlashpiException(
          spec.method
              + " "
              + call.url
              + " was not sent: the HTTP client refused it as invalid HTTP");
    }
  }

  /** One attempt within its deadline, from sending it to reading the whole answer. */
  private HttpResponse<byte[]> send(RequestSpec<?> spec, Call call) {
    HttpRequest request = build(spec, call);
    long deadline = timing.nanoTime() + call.timeout.toNanos();
    DeadlineBodyHandler handler = new DeadlineBodyHandler(() -> timing.nanoTime() - deadline > 0);
    CompletableFuture<HttpResponse<byte[]>> future;
    try {
      future = http.sendAsync(request, handler);
    } catch (RuntimeException error) {
      throw transferError(spec, call, error);
    }
    try {
      HttpResponse<byte[]> response =
          future.get(Math.max(0, deadline - timing.nanoTime()), TimeUnit.NANOSECONDS);
      // Also an answer whose end came late.
      if (timing.nanoTime() - deadline > 0) {
        throw timeoutError(spec, call, null);
      }
      return response;
    } catch (TimeoutException error) {
      future.cancel(true);
      handler.cancel();
      throw timeoutError(spec, call, null);
    } catch (InterruptedException error) {
      future.cancel(true);
      handler.cancel();
      Thread.currentThread().interrupt();
      throw new FlashpiException(spec.method + " " + call.url + " was interrupted", error);
    } catch (CancellationException error) {
      handler.cancel();
      throw new FlashpiException(spec.method + " " + call.url + " was cancelled", error);
    } catch (ExecutionException error) {
      handler.cancel();
      throw transferError(spec, call, error.getCause());
    }
  }

  private static RuntimeException transferError(RequestSpec<?> spec, Call call, Throwable cause) {
    for (Throwable error = cause; error != null; error = error.getCause()) {
      if (error instanceof HttpTimeoutException
          || error instanceof DeadlineBodyHandler.DeadlineExceeded) {
        return timeoutError(spec, call, cause);
      }
    }
    if (cause instanceof IllegalArgumentException) {
      // Without the client's reason, which may quote a header: that may be the Authorization one.
      return new FlashpiException(
          spec.method
              + " "
              + call.url
              + " was not sent: the HTTP client refused it as invalid HTTP");
    }
    if (cause instanceof Error) {
      throw (Error) cause;
    }
    String failed = spec.method + " " + call.url + " failed: " + reason(cause);
    if (cause instanceof IOException || cause instanceof UncheckedIOException) {
      return new ApiConnectionException(failed, cause);
    }
    // Anything else from a caller's HttpClient: an SDK error, with the call's key, not retried.
    return new FlashpiException(failed, cause);
  }

  /**
   * Why a transfer failed: the first message of the cause chain, or the chain's classes when none
   * has one (a DNS failure on Java 21 is a ConnectException without a message).
   */
  static String reason(Throwable cause) {
    List<String> classes = new ArrayList<>();
    for (Throwable error = cause; error != null && classes.size() < 8; error = error.getCause()) {
      if (error.getMessage() != null && !error.getMessage().isEmpty()) {
        return error.getMessage();
      }
      classes.add(error.getClass().getSimpleName());
    }
    return String.join(": ", classes);
  }

  private static ApiTimeoutException timeoutError(RequestSpec<?> spec, Call call, Throwable cause) {
    String seconds =
        BigDecimal.valueOf(call.timeout.toMillis())
            .movePointLeft(3)
            .setScale(3, RoundingMode.UNNECESSARY)
            .stripTrailingZeros()
            .toPlainString();
    String message = spec.method + " " + call.url + " timed out after " + seconds + " seconds";
    return cause == null
        ? new ApiTimeoutException(message)
        : new ApiTimeoutException(message, cause);
  }

  private void sleep(Duration duration, Call call) {
    try {
      timing.sleep(duration);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      FlashpiException interrupted =
          new FlashpiException("The call was interrupted while waiting to retry", error);
      interrupted.setIdempotencyKey(call.idempotencyKey);
      throw interrupted;
    }
  }

  /** The error of an answer with an error status, by status. */
  static ApiException apiError(int status, Map<String, List<String>> headers, String body) {
    switch (status) {
      case 400:
        return new BadRequestException(status, headers, body);
      case 401:
        return new AuthenticationException(status, headers, body);
      case 403:
        return new PermissionDeniedException(status, headers, body);
      case 404:
        return new NotFoundException(status, headers, body);
      case 409:
        return new ConflictException(status, headers, body);
      case 422:
        return new UnprocessableEntityException(status, headers, body);
      case 429:
        return new RateLimitException(status, headers, body);
      default:
        return status >= 500
            ? new InternalServerException(status, headers, body)
            : new ApiException(status, headers, body);
    }
  }

  /**
   * How long to wait before retrying, or null when the failure must not be retried.
   *
   * <p>Retried: network errors and timeouts, 408, 429 and 5xx except 501 (with a Retry-After of
   * {@link #MAX_RETRY_AFTER} or less), and 409 {@code idempotency_key_in_progress}. POSTs are safe
   * to retry because they keep their Idempotency-Key.
   */
  Duration retryDelay(FlashpiException failure, int retries) {
    if (failure instanceof ApiConnectionException) {
      return backoff(retries);
    }
    if (!(failure instanceof ApiException)) {
      return null;
    }
    ApiException error = (ApiException) failure;
    int status = error.getStatus();
    if (status == 409) {
      if (!IN_PROGRESS.equals(error.getCode())) {
        return null;
      }
      Duration backoff = backoff(retries);
      return backoff.compareTo(IN_PROGRESS_FIRST_WAIT) < 0 ? IN_PROGRESS_FIRST_WAIT : backoff;
    }
    boolean transientStatus = status == 408 || status == 429 || (status >= 500 && status != 501);
    // The shared demo certificate is missing: no retry within seconds will fix that.
    if (!transientStatus || "demo_issuer_unavailable".equals(error.getCode())) {
      return null;
    }
    Duration retryAfter =
        RetryAfter.parse(error.getHeaders().firstValue("retry-after").orElse(null), timing.now());
    if (retryAfter == null) {
      return backoff(retries);
    }
    return retryAfter.compareTo(MAX_RETRY_AFTER) <= 0 ? retryAfter : null;
  }

  /** Exponential backoff from 0.5 s up to 8 s, minus up to 25 % of jitter, in milliseconds. */
  Duration backoff(int retries) {
    Duration delay = min(times(INITIAL_RETRY_DELAY, Math.min(retries, 16)), MAX_RETRY_DELAY);
    return Duration.ofMillis(Math.round(delay.toMillis() * (1 - timing.random() * 0.25)));
  }

  /** The id of the document an earlier attempt of this call created, if the answer shows one. */
  private static String recoverableDocument(RequestSpec<?> spec, ApiException error) {
    if (!spec.recoverDocument
        || error.getStatus() != 422
        || !"idempotency_key_reused".equals(error.getCode())) {
      return null;
    }
    String documentId = error.getProblem() == null ? null : error.getProblem().getDocumentId();
    return documentId == null || documentId.isEmpty() ? null : documentId;
  }

  /**
   * Reads the document an earlier attempt of the call created. A failure to read it keeps the
   * call's key on the error, so trying again later with that key returns the document instead of
   * issuing another.
   */
  private <T> ApiResponse<T> recoverDocument(RequestSpec<T> spec, Call call, String documentId) {
    try {
      RequestSpec<T> read =
          RequestSpec.of("GET", Paths.of("/v1/documents/{}", documentId), spec.reader)
              .options(call.options.toBuilder().idempotencyKey(null).build())
              .build();
      return request(read);
    } catch (FlashpiException error) {
      error.setIdempotencyKey(call.idempotencyKey);
      throw error;
    }
  }

  /** The result of a DELETE whose resource is gone: a 204 with the 404's headers. */
  private static <T> ApiResponse<T> deleted(ApiException error) {
    Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (Map.Entry<String, List<String>> header : error.getHeaders().map().entrySet()) {
      if (!BODY_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))) {
        headers.put(header.getKey(), new ArrayList<>(header.getValue()));
      }
    }
    return new ApiResponse<>(
        null, 204, HttpHeaders.of(headers, (name, value) -> true), error.getRequestId());
  }

  private static Duration times(Duration duration, int power) {
    return duration.multipliedBy(1L << power);
  }

  private static Duration min(Duration first, Duration second) {
    return first.compareTo(second) <= 0 ? first : second;
  }
}
