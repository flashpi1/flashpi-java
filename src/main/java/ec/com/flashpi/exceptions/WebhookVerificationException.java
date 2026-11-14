package ec.com.flashpi.exceptions;

/**
 * A webhook did not come from Flashpi, was altered, or is too old: answer 400 and ignore it.
 *
 * <p>Its message never carries the secret or a signature.
 */
public class WebhookVerificationException extends FlashpiException {
  private static final long serialVersionUID = 1L;

  /** Why a webhook was rejected. */
  public enum Reason {
    /** The webhook-id, webhook-timestamp or webhook-signature header is missing. */
    MISSING_HEADERS("missing_headers"),
    /** The webhook-timestamp is not Unix seconds. */
    INVALID_TIMESTAMP("invalid_timestamp"),
    /** The webhook-timestamp is too far from now (more than the tolerance). */
    TIMESTAMP_OUT_OF_RANGE("timestamp_out_of_range"),
    /** No signature matches the body and the secret. */
    NO_MATCHING_SIGNATURE("no_matching_signature"),
    /** The secret is not a whsec_ secret. */
    INVALID_SECRET("invalid_secret"),
    /** The body is not text or not a JSON object. */
    INVALID_PAYLOAD("invalid_payload");

    private final String value;

    Reason(String value) {
      this.value = value;
    }

    /**
     * The reason as the other Flashpi SDKs name it.
     *
     * @return the reason in snake_case ({@code no_matching_signature})
     */
    public String value() {
      return value;
    }
  }

  private final Reason reason;

  /**
   * A rejected webhook.
   *
   * @param reason why it was rejected
   * @param message what went wrong
   */
  public WebhookVerificationException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /**
   * A rejected webhook, with the cause.
   *
   * @param reason why it was rejected
   * @param message what went wrong
   * @param cause what failed while reading it
   */
  public WebhookVerificationException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  /**
   * Why the webhook was rejected.
   *
   * @return the reason
   */
  public Reason getReason() {
    return reason;
  }
}
