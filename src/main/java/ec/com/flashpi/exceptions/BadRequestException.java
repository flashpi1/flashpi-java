package ec.com.flashpi.exceptions;

import java.util.List;
import java.util.Map;

/** 400: the request could not be read (malformed JSON, invalid Idempotency-Key, ...). */
public class BadRequestException extends ApiException {
  private static final long serialVersionUID = 1L;

  /**
   * An error the API answered with.
   *
   * @param status the HTTP status
   * @param headers the response headers
   * @param body the response body, as text
   */
  public BadRequestException(int status, Map<String, List<String>> headers, String body) {
    super(status, headers, body);
  }
}
