package ec.com.flashpi.exceptions;

import java.util.List;
import java.util.Map;

/** 409: the request conflicts with the current state (sequential used, RIDE not ready, ...). */
public class ConflictException extends ApiException {
  private static final long serialVersionUID = 1L;

  /**
   * An error the API answered with.
   *
   * @param status the HTTP status
   * @param headers the response headers
   * @param body the response body, as text
   */
  public ConflictException(int status, Map<String, List<String>> headers, String body) {
    super(status, headers, body);
  }
}
