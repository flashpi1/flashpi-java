package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Readers;
import ec.com.flashpi.internal.RequestSpec;
import ec.com.flashpi.models.Document;
import ec.com.flashpi.models.DocumentOptions;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

/**
 * Issues one document type. The answer is the document: {@code processing} right away (202), or,
 * with {@code options.waitForAuthorization}, its outcome once the SRI decides (200), unless the
 * wait ends first (202 with {@code processing} or {@code pending_sri}; follow it with webhooks or
 * {@code documents().get()}).
 *
 * @param <B> the body of this document type
 */
public abstract class DocumentIssuer<B> extends Resource {
  /** Seconds the API waits for the SRI in sync mode when {@code waitTimeoutSeconds} is not sent. */
  static final int DEFAULT_WAIT_SECONDS = 15;

  /** Time on top of the sync wait for Flashpi to build, sign and answer. */
  static final int SYNC_MARGIN_SECONDS = 15;

  private final String path;
  private final Function<B, DocumentOptions> options;
  private final WithResponse<B> withResponse = new WithResponse<>(this);

  DocumentIssuer(Engine engine, String path, Function<B, DocumentOptions> options) {
    super(engine);
    this.path = path;
    this.options = options;
  }

  /**
   * Issues the document. Retries reuse the same Idempotency-Key, and when a retry finds the
   * document already created, its current state is returned instead of an error.
   *
   * @param body the document
   * @return the document
   */
  public Document create(B body) {
    return create(body, null);
  }

  /**
   * Issues the document. Retries reuse the same Idempotency-Key, and when a retry finds the
   * document already created, its current state is returned instead of an error.
   *
   * @param body the document
   * @param options the call's options, or null
   * @return the document
   */
  public Document create(B body, RequestOptions options) {
    Objects.requireNonNull(body, "body");
    return request(
        RequestSpec.of("POST", path, Readers.json(Document.class))
            .body(body)
            .options(options)
            .minTimeout(syncTimeoutFloor(this.options.apply(body)))
            .recoverDocument()
            .build());
  }

  /**
   * The same methods, returning the HTTP response and request id with the data.
   *
   * @return the view
   */
  public WithResponse<B> withResponse() {
    return withResponse;
  }

  /**
   * The shortest timeout of each attempt of an issue. In sync mode ({@code
   * options.waitForAuthorization}) the API holds the request for up to {@code waitTimeoutSeconds}
   * (15 by default) while the SRI decides, so each attempt waits at least that plus 15 seconds.
   */
  static Duration syncTimeoutFloor(DocumentOptions options) {
    if (options == null || !Boolean.TRUE.equals(options.getWaitForAuthorization())) {
      return Duration.ZERO;
    }
    Integer wait = options.getWaitTimeoutSeconds();
    return Duration.ofSeconds(
        (wait == null ? DEFAULT_WAIT_SECONDS : wait) + (long) SYNC_MARGIN_SECONDS);
  }

  /**
   * The methods of a {@link DocumentIssuer}, returning an {@link ApiResponse}.
   *
   * @param <B> the body of the document type
   */
  public static final class WithResponse<B> {
    private final DocumentIssuer<B> issuer;

    WithResponse(DocumentIssuer<B> issuer) {
      this.issuer = issuer;
    }

    /**
     * See {@link DocumentIssuer#create(Object)}.
     *
     * @param body the document
     * @return the document and the response
     */
    public ApiResponse<Document> create(B body) {
      return Capture.run(() -> issuer.create(body));
    }

    /**
     * See {@link DocumentIssuer#create(Object, RequestOptions)}.
     *
     * @param body the document
     * @param options the call's options, or null
     * @return the document and the response
     */
    public ApiResponse<Document> create(B body, RequestOptions options) {
      return Capture.run(() -> issuer.create(body, options));
    }
  }
}
