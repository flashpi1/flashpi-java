package ec.com.flashpi.exceptions;

/**
 * Every exception this SDK throws extends this one, except for misused arguments ({@link
 * IllegalArgumentException} and {@link NullPointerException}, as for any Java method).
 *
 * <p>Its message never carries the API key, a webhook secret or a certificate password.
 */
public class FlashpiException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private String idempotencyKey;

  /**
   * An error of the SDK.
   *
   * @param message what went wrong
   */
  public FlashpiException(String message) {
    super(message);
  }

  /**
   * An error of the SDK with its cause.
   *
   * @param message what went wrong
   * @param cause what caused it
   */
  public FlashpiException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * The Idempotency-Key a failed POST used: yours, or the one the SDK made. To try the same
   * operation again later without issuing twice, pass it back with {@code
   * RequestOptions.builder().idempotencyKey(error.getIdempotencyKey())}.
   *
   * @return the key, or null for a request that is not a POST
   */
  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  /**
   * Set by the SDK on the errors of a POST, before they are thrown.
   *
   * @param idempotencyKey the key the call used
   */
  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }
}
