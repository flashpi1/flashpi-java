package ec.com.flashpi.exceptions;

import java.util.List;
import java.util.Map;

/** 5xx: Flashpi failed to answer. Requests are retried automatically before this is thrown. */
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
