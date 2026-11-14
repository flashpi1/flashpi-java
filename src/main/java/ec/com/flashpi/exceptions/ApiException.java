package ec.com.flashpi.exceptions;

import com.fasterxml.jackson.databind.JsonNode;
import ec.com.flashpi.internal.Json;
import ec.com.flashpi.internal.RetryAfter;
import ec.com.flashpi.models.FieldError;
import ec.com.flashpi.models.ProblemDetails;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The API answered with an error status. Each status has its subclass ({@link NotFoundException},
 * {@link UnprocessableEntityException}, ...).
 *
 * <p>Branch on {@link #getCode()} (stable English slugs such as {@code validation_failed}), never
 * on the message, which follows the requested language.
 */
public class ApiException extends FlashpiException {
  private static final long serialVersionUID = 1L;

  private final int status;

  @SuppressWarnings("serial") // a TreeMap of ArrayLists of text
  private final Map<String, List<String>> headers;

  private final ProblemDetails problem;
  private final String code;
  private final String title;
  private final String detail;

  @SuppressWarnings("serial") // an unmodifiable ArrayList
  private final List<FieldError> errors;

  private final String requestId;

  /**
   * An error the API answered with.
   *
   * @param status the HTTP status
   * @param headers the response headers
   * @param body the response body, as text
   */
  public ApiException(int status, Map<String, List<String>> headers, String body) {
    this(status, copy(headers), Json.problem(body), body);
  }

  private ApiException(
      int status, Map<String, List<String>> headers, JsonNode problem, String body) {
    super(message(status, problem, body));
    this.status = status;
    this.headers = headers;
    this.code = text(problem, "code");
    this.title = text(problem, "title");
    this.detail = text(problem, "detail");
    this.problem = problem == null ? null : Json.readOrNull(problem, ProblemDetails.class);
    this.errors = fieldErrors(problem);
    String id = text(problem, "requestId");
    this.requestId = id != null ? id : first(headers, "x-request-id");
  }

  /**
   * The HTTP status of the answer.
   *
   * @return the status
   */
  public int getStatus() {
    return status;
  }

  /**
   * The error code, such as {@code validation_failed} or {@code idempotency_key_reused}: the list
   * is {@link ec.com.flashpi.models.ErrorCode}.
   *
   * @return the code, or null when the answer had no Problem Details (for example a 502 from a
   *     proxy)
   */
  public String getCode() {
    return code;
  }

  /**
   * A short summary of the problem type.
   *
   * @return the title, or null
   */
  public String getTitle() {
    return title;
  }

  /**
   * What went wrong in this request.
   *
   * @return the detail, or null
   */
  public String getDetail() {
    return detail;
  }

  /**
   * The problems of each field of a request body or query.
   *
   * @return the field errors (may be empty); unmodifiable
   */
  public List<FieldError> getErrors() {
    return errors;
  }

  /**
   * Flashpi's id of the request ({@code x-request-id}): share it with support.
   *
   * @return the id, or null
   */
  public String getRequestId() {
    return requestId;
  }

  /**
   * The whole Problem Details body, with its extension members ({@code documentId}, {@code limit},
   * {@code resetsAt}, {@code rideStatus}, ...).
   *
   * @return the body, or null when the answer had none
   */
  public ProblemDetails getProblem() {
    return problem;
  }

  /**
   * The response headers.
   *
   * @return the headers
   */
  public HttpHeaders getHeaders() {
    return HttpHeaders.of(headers, (name, value) -> true);
  }

  /**
   * How long the API asked to wait before trying again ({@code Retry-After}). Daily quota errors
   * point at the next midnight in Ecuador, so this can be hours.
   *
   * @return the wait, or null when the answer did not ask for one
   */
  public Duration getRetryAfter() {
    return RetryAfter.parse(first(headers, "retry-after"), Instant.now());
  }

  private static Map<String, List<String>> copy(Map<String, List<String>> headers) {
    TreeMap<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (Map.Entry<String, List<String>> header : headers.entrySet()) {
      copy.computeIfAbsent(header.getKey(), name -> new ArrayList<>()).addAll(header.getValue());
    }
    return copy;
  }

  private static String first(Map<String, List<String>> headers, String name) {
    List<String> values = headers.get(name);
    return values == null || values.isEmpty() ? null : values.get(0);
  }

  private static String text(JsonNode problem, String member) {
    JsonNode value = problem == null ? null : problem.get(member);
    return value != null && value.isTextual() ? value.textValue() : null;
  }

  private static List<FieldError> fieldErrors(JsonNode problem) {
    JsonNode errors = problem == null ? null : problem.get("errors");
    if (errors == null || !errors.isArray()) {
      return Collections.emptyList();
    }
    List<FieldError> list = new ArrayList<>();
    for (JsonNode error : errors) {
      FieldError fieldError = Json.readOrNull(error, FieldError.class);
      if (fieldError != null) {
        list.add(fieldError);
      }
    }
    return Collections.unmodifiableList(list);
  }

  private static String message(int status, JsonNode problem, String body) {
    if (problem != null) {
      String code = text(problem, "code");
      String detail = text(problem, "detail");
      if (detail == null) {
        detail = text(problem, "title");
      }
      return detail == null ? status + " " + code : status + " " + code + ": " + detail;
    }
    String excerpt = String.join(" ", body.trim().split("\\s+")).trim();
    if (excerpt.codePointCount(0, excerpt.length()) > 200) {
      excerpt = excerpt.substring(0, excerpt.offsetByCodePoints(0, 200));
    }
    return excerpt.isEmpty() ? status + " (no response body)" : status + " " + excerpt;
  }
}
