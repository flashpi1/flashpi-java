package ec.com.flashpi.internal;

import com.fasterxml.jackson.databind.JsonNode;
import ec.com.flashpi.RequestOptions;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One API operation, as the resources declare it.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 *
 * @param <T> the type of its data
 */
public final class RequestSpec<T> {
  /** The Accept header of JSON operations. */
  public static final String JSON = "application/json";

  final String method;
  final String path;
  final Map<String, String> query;
  final Object body;
  final Multipart multipart;
  final String accept;
  final Readers.Reader<T> reader;
  final RequestOptions options;
  final Duration minTimeout;
  final boolean recoverDocument;

  private RequestSpec(Builder<T> builder) {
    this.method = builder.method;
    this.path = builder.path;
    this.query = Collections.unmodifiableMap(new LinkedHashMap<>(builder.query));
    this.body = builder.body;
    this.multipart = builder.multipart;
    this.accept = builder.accept;
    this.reader = builder.reader;
    this.options = builder.options == null ? RequestOptions.none() : builder.options;
    this.minTimeout = builder.minTimeout;
    this.recoverDocument = builder.recoverDocument;
  }

  /**
   * A request.
   *
   * @param method GET, POST, PATCH or DELETE
   * @param path the path, built with {@link Paths#of} when it carries ids
   * @param reader how to read its data
   * @param <T> the data
   * @return the builder
   */
  public static <T> Builder<T> of(String method, String path, Readers.Reader<T> reader) {
    return new Builder<>(method, path, reader);
  }

  /**
   * The same request for the page after {@code cursor}: same filters and options.
   *
   * @param cursor the cursor of the next page
   * @return the request
   */
  public RequestSpec<T> withCursor(String cursor) {
    Builder<T> builder = new Builder<>(method, path, reader);
    builder.query.putAll(query);
    builder.query.put("cursor", cursor);
    builder.options = options;
    builder.accept = accept;
    return builder.build();
  }

  /**
   * Builds a {@link RequestSpec}.
   *
   * @param <T> the data
   */
  public static final class Builder<T> {
    private final String method;
    private final String path;
    private final Readers.Reader<T> reader;
    private final Map<String, String> query = new LinkedHashMap<>();
    private Object body;
    private Multipart multipart;
    private String accept = JSON;
    private RequestOptions options;
    private Duration minTimeout = Duration.ZERO;
    private boolean recoverDocument;

    private Builder(String method, String path, Readers.Reader<T> reader) {
      this.method = method;
      this.path = path;
      this.reader = reader;
    }

    /**
     * The query, from a params model: members that are null or unset are left out, booleans are
     * sent as true and false.
     *
     * @param params the params, or null
     * @return this builder
     */
    public Builder<T> query(Object params) {
      if (params == null) {
        return this;
      }
      JsonNode node = Json.mapper().valueToTree(params);
      Iterator<Map.Entry<String, JsonNode>> fields = Json.members(node);
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        JsonNode value = field.getValue();
        if (value.isNull()) {
          continue;
        }
        if (!value.isValueNode()) {
          throw new IllegalArgumentException(
              "The query parameter " + field.getKey() + " is not a single value");
        }
        query.put(field.getKey(), value.asText());
      }
      return this;
    }

    /**
     * A JSON body, serialized once for every attempt.
     *
     * @param body a model, or null to send no body
     * @return this builder
     */
    public Builder<T> body(Object body) {
      this.body = body;
      return this;
    }

    /**
     * A multipart body, instead of a JSON one.
     *
     * @param multipart the body
     * @return this builder
     */
    public Builder<T> multipart(Multipart multipart) {
      this.multipart = Objects.requireNonNull(multipart);
      return this;
    }

    /**
     * The Accept header.
     *
     * @param accept the media types
     * @return this builder
     */
    public Builder<T> accept(String accept) {
      this.accept = accept;
      return this;
    }

    /**
     * The caller's options.
     *
     * @param options the options, or null for none
     * @return this builder
     */
    public Builder<T> options(RequestOptions options) {
      this.options = options;
      return this;
    }

    /**
     * A floor for the timeout of each attempt, for requests the API holds open (issuing with {@code
     * options.waitForAuthorization}).
     *
     * @param minTimeout the floor
     * @return this builder
     */
    public Builder<T> minTimeout(Duration minTimeout) {
      this.minTimeout = minTimeout;
      return this;
    }

    /**
     * For document POSTs: when a retry is answered 422 {@code idempotency_key_reused} with a {@code
     * documentId}, an earlier attempt created the document, so the call returns it instead of
     * failing.
     *
     * @return this builder
     */
    public Builder<T> recoverDocument() {
      this.recoverDocument = true;
      return this;
    }

    /**
     * Builds the request.
     *
     * @return the request
     */
    public RequestSpec<T> build() {
      return new RequestSpec<>(this);
    }
  }
}
