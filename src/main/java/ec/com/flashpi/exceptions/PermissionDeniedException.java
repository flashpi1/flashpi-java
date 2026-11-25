package ec.com.flashpi.exceptions;

import java.util.List;
import java.util.Map;

/**
 * 403: the key may not do this (read-only, another issuer, IP allowlist, suspended account, or a
 * test key on an issuer that has live documents).
 */
public class PermissionDeniedException extends ApiException {
  private static final long serialVersionUID = 1L;

  /**
   * An error the API answered with.
   *
   * @param status the HTTP status
   * @param headers the response headers
   * @param body the response body, as text
   */
  public PermissionDeniedException(int status, Map<String, List<String>> headers, String body) {
    super(status, headers, body);
  }
}
