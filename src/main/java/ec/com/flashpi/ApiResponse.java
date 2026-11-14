package ec.com.flashpi;

import java.net.http.HttpHeaders;
import java.util.Objects;

/**
 * The data of a call together with the HTTP response it came from, from a resource's {@code
 * withResponse()} view: {@code flashpi.documents().withResponse().get(id)}.
 *
 * @param <T> the data
 */
public final class ApiResponse<T> {
  private final T data;
  private final int statusCode;
  private final HttpHeaders headers;
  private final String requestId;

  /**
   * A response.
   *
   * @param data what the method returns
   * @param statusCode the HTTP status
   * @param headers the response headers
   * @param requestId Flashpi's id of the request, or null
   */
  public ApiResponse(T data, int statusCode, HttpHeaders headers, String requestId) {
    this.data = data;
    this.statusCode = statusCode;
    this.headers = Objects.requireNonNull(headers, "headers");
    this.requestId = requestId;
  }

  /**
   * What the method returns.
   *
   * @return the data (null for a method that returns nothing)
   */
  public T getData() {
    return data;
  }

  /**
   * The HTTP status of the answer.
   *
   * @return the status
   */
  public int getStatusCode() {
    return statusCode;
  }

  /**
   * The response headers ({@code idempotent-replayed}, {@code location}, ...).
   *
   * @return the headers
   */
  public HttpHeaders getHeaders() {
    return headers;
  }

  /**
   * Flashpi's id of the request ({@code x-request-id}): share it with support.
   *
   * @return the id, or null
   */
  public String getRequestId() {
    return requestId;
  }

  @Override
  public String toString() {
    return "ApiResponse{statusCode="
        + statusCode
        + ", requestId="
        + requestId
        + ", data="
        + data
        + "}";
  }
}
