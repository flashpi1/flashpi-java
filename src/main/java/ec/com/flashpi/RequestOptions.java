package ec.com.flashpi;

import ec.com.flashpi.internal.Headers;
import ec.com.flashpi.internal.Settings;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Options of one call, the last argument of every method. Each one left unset keeps the client's
 * setting.
 *
 * <pre>{@code
 * RequestOptions options = RequestOptions.builder()
 *     .idempotencyKey("order-42")
 *     .timeout(Duration.ofSeconds(10))
 *     .language(Language.ES)
 *     .build();
 * }</pre>
 */
public final class RequestOptions {
  private static final RequestOptions NONE = builder().build();

  private final String idempotencyKey;
  private final Duration timeout;
  private final Integer maxRetries;
  private final Language language;
  private final String correlationId;
  private final Map<String, String> extraHeaders;

  private RequestOptions(Builder builder) {
    this.idempotencyKey =
        builder.idempotencyKey == null
            ? null
            : Headers.value("idempotencyKey", builder.idempotencyKey);
    this.timeout = builder.timeout == null ? null : Settings.timeout("timeout", builder.timeout);
    this.maxRetries =
        builder.maxRetries == null ? null : Settings.retries("maxRetries", builder.maxRetries);
    this.language = builder.language;
    this.correlationId =
        builder.correlationId == null
            ? null
            : Headers.value("correlationId", builder.correlationId);
    this.extraHeaders = Headers.checked("extraHeaders", builder.extraHeaders);
  }

  /**
   * Options that change nothing.
   *
   * @return the options
   */
  public static RequestOptions none() {
    return NONE;
  }

  /**
   * A builder with nothing set.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A builder with these options, to change some.
   *
   * @return the builder
   */
  public Builder toBuilder() {
    Builder builder = new Builder();
    builder.idempotencyKey = idempotencyKey;
    builder.timeout = timeout;
    builder.maxRetries = maxRetries;
    builder.language = language;
    builder.correlationId = correlationId;
    builder.extraHeaders.putAll(extraHeaders);
    return builder;
  }

  /**
   * The Idempotency-Key of a POST.
   *
   * @return the key, or null to let the SDK make one
   */
  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  /**
   * The timeout of each attempt.
   *
   * @return the timeout, or null for the client's
   */
  public Duration getTimeout() {
    return timeout;
  }

  /**
   * The retries after transient failures.
   *
   * @return the retries, or null for the client's
   */
  public Integer getMaxRetries() {
    return maxRetries;
  }

  /**
   * The language of the API's messages.
   *
   * @return the language, or null for the client's
   */
  public Language getLanguage() {
    return language;
  }

  /**
   * Your own id for the request ({@code X-Correlation-Id}).
   *
   * @return the id, or null
   */
  public String getCorrelationId() {
    return correlationId;
  }

  /**
   * Extra headers of the request.
   *
   * @return the headers, in order; unmodifiable
   */
  public Map<String, String> getExtraHeaders() {
    return extraHeaders;
  }

  /** The options; of the extra headers, only their names (their values may be secrets). */
  @Override
  public String toString() {
    return "RequestOptions{idempotencyKey="
        + idempotencyKey
        + ", timeout="
        + timeout
        + ", maxRetries="
        + maxRetries
        + ", language="
        + language
        + ", correlationId="
        + correlationId
        + ", extraHeaders="
        + extraHeaders.keySet()
        + "}";
  }

  /** Builds {@link RequestOptions}. */
  public static final class Builder {
    private String idempotencyKey;
    private Duration timeout;
    private Integer maxRetries;
    private Language language;
    private String correlationId;
    private final Map<String, String> extraHeaders = new LinkedHashMap<>();

    private Builder() {}

    /**
     * The Idempotency-Key of a POST: 1 to 255 visible ASCII characters (no spaces), unique per
     * operation, such as your order id or a UUID. The SDK makes one when it is missing and reuses
     * it on its retries, so a retry never issues a document twice. Other methods ignore it.
     *
     * @param idempotencyKey the key, or null to let the SDK make one
     * @return this builder
     */
    public Builder idempotencyKey(String idempotencyKey) {
      this.idempotencyKey = idempotencyKey;
      return this;
    }

    /**
     * The timeout of each attempt, from sending it to reading the whole answer, instead of the
     * client's.
     *
     * @param timeout more than zero and at most 2147483.647 seconds, or null for the client's
     * @return this builder
     */
    public Builder timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    /**
     * The retries after network errors, timeouts, 408, 429 and 5xx (not 501 or {@code
     * demo_issuer_unavailable}), instead of the client's.
     *
     * @param maxRetries 0 or more (0 turns them off), or null for the client's
     * @return this builder
     */
    public Builder maxRetries(Integer maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    /**
     * The language of the API's messages for this request, instead of the client's.
     *
     * @param language the language, or null for the client's
     * @return this builder
     */
    public Builder language(Language language) {
      this.language = language;
      return this;
    }

    /**
     * Your own id for the request ({@code X-Correlation-Id}, 1 to 128 of {@code A-Za-z0-9._:/+=-}),
     * echoed back and logged by Flashpi.
     *
     * @param correlationId the id, or null
     * @return this builder
     */
    public Builder correlationId(String correlationId) {
      this.correlationId = correlationId;
      return this;
    }

    /**
     * An extra header for this request. It never replaces the API key.
     *
     * @param name the header's name
     * @param value its value
     * @return this builder
     */
    public Builder header(String name, String value) {
      extraHeaders.put(
          Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
      return this;
    }

    /**
     * Extra headers for this request, added to the ones set before. They never replace the API key.
     *
     * @param headers the headers by name
     * @return this builder
     */
    public Builder extraHeaders(Map<String, String> headers) {
      extraHeaders.putAll(headers);
      return this;
    }

    /**
     * Builds the options, checking them.
     *
     * @return the options
     * @throws ec.com.flashpi.exceptions.FlashpiException for a bad timeout or number of retries
     * @throws IllegalArgumentException for a header name or value that cannot be sent
     */
    public RequestOptions build() {
      return new RequestOptions(this);
    }
  }
}
