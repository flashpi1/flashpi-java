package ec.com.flashpi.exceptions;

import java.util.List;
import java.util.Map;

/**
 * 422: the request is well formed but invalid ({@code validation_failed}, {@code total_mismatch},
 * ...).
 */
public class UnprocessableEntityException extends ApiException {
  private static final long serialVersionUID = 1L;

  /**
   * An error the API answered with.
   *
   * @param status the HTTP status
   * @param headers the response headers
   * @param body the response body, as text
   */
  public UnprocessableEntityException(int status, Map<String, List<String>> headers, String body) {
    super(status, headers, body);
  }
}
