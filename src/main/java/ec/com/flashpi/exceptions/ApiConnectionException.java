package ec.com.flashpi.exceptions;

/** The request never got an answer (DNS, TLS, connection reset, ...). It was retried first. */
public class ApiConnectionException extends FlashpiException {
  private static final long serialVersionUID = 1L;

  /**
   * A request without an answer.
   *
   * @param message what went wrong
   */
  public ApiConnectionException(String message) {
    super(message);
  }

  /**
   * A request without an answer, with its cause.
   *
   * @param message what went wrong
   * @param cause the network error
   */
  public ApiConnectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
