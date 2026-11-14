package ec.com.flashpi.exceptions;

import java.util.List;
import java.util.Map;

/** 404: the resource does not exist in this mode (test or live) for this account. */
public class NotFoundException extends ApiException {
  private static final long serialVersionUID = 1L;

  /**
   * An error the API answered with.
   *
   * @param status the HTTP status
   * @param headers the response headers
   * @param body the response body, as text
   */
  public NotFoundException(int status, Map<String, List<String>> headers, String body) {
    super(status, headers, body);
  }
}
