package ec.com.flashpi.exceptions;

/** An attempt took longer than its timeout, from sending it to reading the whole answer. */
public class ApiTimeoutException extends ApiConnectionException {
  private static final long serialVersionUID = 1L;

  /**
   * A request that timed out.
   *
   * @param message what went wrong
   */
  public ApiTimeoutException(String message) {
    super(message);
  }

  /**
   * A request that timed out, with its cause.
   *
   * @param message what went wrong
   * @param cause the timeout the HTTP client reported
   */
  public ApiTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
