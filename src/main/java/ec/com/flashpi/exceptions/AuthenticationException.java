package ec.com.flashpi.exceptions;

import java.util.List;
import java.util.Map;

/** 401: the API key is missing, malformed, unknown or revoked. */
public class AuthenticationException extends ApiException {
  private static final long serialVersionUID = 1L;

  /**
   * An error the API answered with.
   *
   * @param status the HTTP status
   * @param headers the response headers
   * @param body the response body, as text
   */
  public AuthenticationException(int status, Map<String, List<String>> headers, String body) {
    super(status, headers, body);
  }
}
