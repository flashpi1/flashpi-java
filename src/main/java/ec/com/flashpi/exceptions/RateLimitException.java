package ec.com.flashpi.exceptions;

import java.util.List;
import java.util.Map;

/**
 * 429: a limit was reached; see {@link #getRetryAfter()} and the {@code limit} and {@code resetsAt}
 * members of {@link #getProblem()}.
 */
public class RateLimitException extends ApiException {
  private static final long serialVersionUID = 1L;

  /**
   * An error the API answered with.
   *
   * @param status the HTTP status
   * @param headers the response headers
   * @param body the response body, as text
   */
  public RateLimitException(int status, Map<String, List<String>> headers, String body) {
    super(status, headers, body);
  }
}
