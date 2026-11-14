package ec.com.flashpi.exceptions;

import java.util.List;
import java.util.Map;

/**
 * 5xx: Flashpi failed to answer. Retried before it is thrown, except a 501, the code {@code
 * demo_issuer_unavailable} and an answer that asks to wait more than 60 seconds.
 */
public class InternalServerException extends ApiException {
  private static final long serialVersionUID = 1L;

  /**
   * An error the API answered with.
   *
   * @param status the HTTP status
   * @param headers the response headers
   * @param body the response body, as text
   */
  public InternalServerException(int status, Map<String, List<String>> headers, String body) {
    super(status, headers, body);
  }
}
