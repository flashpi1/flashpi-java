package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.com.flashpi.FakeHttpClient.Answer;
import ec.com.flashpi.exceptions.ApiException;
import ec.com.flashpi.exceptions.AuthenticationException;
import ec.com.flashpi.exceptions.BadRequestException;
import ec.com.flashpi.exceptions.ConflictException;
import ec.com.flashpi.exceptions.InternalServerException;
import ec.com.flashpi.exceptions.NotFoundException;
import ec.com.flashpi.exceptions.PermissionDeniedException;
import ec.com.flashpi.exceptions.RateLimitException;
import ec.com.flashpi.exceptions.UnprocessableEntityException;
import ec.com.flashpi.internal.RetryAfter;
import ec.com.flashpi.models.CreateInvoice;
import ec.com.flashpi.models.CreateWebhookEndpoint;
import ec.com.flashpi.models.ErrorCode;
import ec.com.flashpi.models.FieldError;
import ec.com.flashpi.models.FieldErrorCode;
import ec.com.flashpi.models.UpdateIssuer;
import ec.com.flashpi.models.UpdateWebhookEndpoint;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ErrorsTest {
  private static final String VALIDATION =
      "{\"type\":\"https://flashpi.com.ec/docs/errors/validation_failed\",\"title\":\"Validation failed\","
          + "\"status\":422,\"detail\":\"The request has invalid fields\",\"instance\":\"/v1/invoices\","
          + "\"code\":\"validation_failed\",\"errors\":[{\"field\":\"items\",\"code\":\"too_short\","
          + "\"message\":\"At least 1 item\"}],\"requestId\":\"req_1\",\"documentStatus\":\"rejected\",\"extra\":1}";

  @Test
  void eachStatusHasItsClass() {
    Map<Integer, Class<?>> classes =
        Map.of(
            400, BadRequestException.class,
            401, AuthenticationException.class,
            403, PermissionDeniedException.class,
            404, NotFoundException.class,
            409, ConflictException.class,
            422, UnprocessableEntityException.class,
            429, RateLimitException.class,
            500, InternalServerException.class,
            503, InternalServerException.class,
            418, ApiException.class);
    for (Map.Entry<Integer, Class<?>> entry : classes.entrySet()) {
      FakeHttpClient http = new FakeHttpClient().always(Answer.problem(entry.getKey(), "x"));
      ApiException error =
          assertThrows(
              ApiException.class,
              () ->
                  Support.client(http)
                      .status()
                      .get(RequestOptions.builder().maxRetries(0).build()));
      assertSame(entry.getValue(), error.getClass(), "status " + entry.getKey());
      assertEquals(entry.getKey(), error.getStatus());
    }
  }

  @Test
  void readsTheProblemDetails() {
    FakeHttpClient http = new FakeHttpClient().then(Answer.json(422, VALIDATION));
    UnprocessableEntityException error =
        assertThrows(UnprocessableEntityException.class, () -> Support.client(http).status().get());
    assertEquals("422 validation_failed: The request has invalid fields", error.getMessage());
    assertEquals("validation_failed", error.getCode());
    assertEquals("Validation failed", error.getTitle());
    assertEquals("The request has invalid fields", error.getDetail());
    assertEquals("req_1", error.getRequestId());
    assertEquals(1, error.getErrors().size());
    assertEquals("items", error.getErrors().get(0).getField());
    assertEquals(FieldErrorCode.TOO_SHORT, error.getErrors().get(0).getCode());
    assertEquals(ErrorCode.VALIDATION_FAILED, error.getProblem().getCode());
    assertEquals(422.0, error.getProblem().getStatus());
    assertEquals(Map.of("extra", 1), error.getProblem().getAdditionalProperties());
    assertNull(error.getIdempotencyKey());
  }

  @Test
  void readsTheErrorCodesTheApiAdded() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.problem(403, "live_key_required"))
            .then(
                Answer.json(
                    409,
                    "{\"status\":409,\"code\":\"webhook_endpoint_limit_reached\",\"limit\":20}"))
            .then(
                Answer.json(
                    422,
                    "{\"status\":422,\"code\":\"validation_failed\",\"errors\":["
                        + "{\"field\":\"additionalInfo\",\"code\":\"demo_issuer_free_text_not_allowed\",\"message\":\"m\"},"
                        + "{\"field\":\"items[0].description\",\"code\":\"demo_issuer_description_too_long\",\"message\":\"m\"},"
                        + "{\"field\":\"file\",\"code\":\"certificate_ruc_missing\",\"message\":\"m\"}]}"));
    Flashpi flashpi = Support.client(http);

    PermissionDeniedException denied =
        assertThrows(
            PermissionDeniedException.class,
            () -> flashpi.issuers().update("issuer", UpdateIssuer.builder().build()));
    assertEquals("live_key_required", denied.getCode());
    assertEquals(ErrorCode.LIVE_KEY_REQUIRED, denied.getProblem().getCode());

    ConflictException full =
        assertThrows(
            ConflictException.class,
            () ->
                flashpi
                    .webhookEndpoints()
                    .create(
                        CreateWebhookEndpoint.builder().url("https://example.com/hook").build()));
    assertEquals(ErrorCode.WEBHOOK_ENDPOINT_LIMIT_REACHED, full.getProblem().getCode());
    assertEquals(20, full.getProblem().getLimit());

    UnprocessableEntityException invalid =
        assertThrows(
            UnprocessableEntityException.class,
            () -> flashpi.invoices().create(CreateInvoice.builder().issuerId("demo").build()));
    assertEquals(
        List.of(
            FieldErrorCode.DEMO_ISSUER_FREE_TEXT_NOT_ALLOWED,
            FieldErrorCode.DEMO_ISSUER_DESCRIPTION_TOO_LONG,
            FieldErrorCode.CERTIFICATE_RUC_MISSING),
        invalid.getErrors().stream().map(FieldError::getCode).collect(Collectors.toList()));
    assertEquals(3, http.requests.size());
  }

  @Test
  void theHourlyVerificationLimitOfAWebhookEndpointIsARateLimit() {
    FakeHttpClient http =
        new FakeHttpClient()
            .always(Answer.problem(429, "rate_limited").header("retry-after", "1800"));
    Flashpi flashpi = Support.client(http);
    RateLimitException verify =
        assertThrows(RateLimitException.class, () -> flashpi.webhookEndpoints().verify("we_1"));
    assertEquals(ErrorCode.RATE_LIMITED, verify.getProblem().getCode());
    assertEquals(Duration.ofMinutes(30), verify.getRetryAfter());
    RateLimitException update =
        assertThrows(
            RateLimitException.class,
            () ->
                flashpi
                    .webhookEndpoints()
                    .update(
                        "we_1",
                        UpdateWebhookEndpoint.builder().url("https://example.com/new").build()));
    assertEquals(ErrorCode.RATE_LIMITED, update.getProblem().getCode());
    // Longer than 60 seconds: thrown at once, not retried.
    assertEquals(2, http.requests.size());
  }

  @Test
  void anAnswerWithoutProblemDetailsShowsAnExcerpt() {
    String html = "<html>\n  <body>   Bad   gateway </body></html>" + "x".repeat(300);
    FakeHttpClient http =
        new FakeHttpClient()
            .then(
                Answer.bytes(
                        502, html.getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/html")
                    .header("x-request-id", "req_edge"));
    InternalServerException error =
        assertThrows(
            InternalServerException.class,
            () ->
                Support.client(http).status().get(RequestOptions.builder().maxRetries(0).build()));
    assertTrue(error.getMessage().startsWith("502 <html> <body> Bad gateway </body></html>xxx"));
    assertEquals(4 + 200, error.getMessage().length());
    assertNull(error.getCode());
    assertNull(error.getProblem());
    assertEquals(List.of(), error.getErrors());
    assertEquals("req_edge", error.getRequestId());
  }

  @Test
  void anEmptyAnswerSaysSo() {
    ApiException error = new ApiException(418, Map.of(), "");
    assertEquals("418 (no response body)", error.getMessage());
  }

  @Test
  void aProblemWithOnlyATitleUsesIt() {
    ApiException error =
        new ApiException(409, Map.of(), "{\"code\":\"conflict\",\"title\":\"Conflict\"}");
    assertEquals("409 conflict: Conflict", error.getMessage());
    assertEquals(
        "409 conflict", new ApiException(409, Map.of(), "{\"code\":\"conflict\"}").getMessage());
  }

  @Test
  void headersAreReadInAnyCase() {
    ApiException error =
        new ApiException(
            429, Map.of("Retry-After", List.of("12"), "X-Request-Id", List.of("r")), "");
    assertEquals(Duration.ofSeconds(12), error.getRetryAfter());
    assertEquals("r", error.getRequestId());
    assertEquals("12", error.getHeaders().firstValue("retry-after").orElse(null));
  }

  @Test
  void parsesRetryAfter() {
    Instant now = Instant.parse("2026-11-13T12:00:00Z");
    assertEquals(Duration.ofSeconds(0), RetryAfter.parse(" 0 ", now));
    assertEquals(Duration.ofMinutes(2), RetryAfter.parse("120", now));
    assertEquals(Duration.ofSeconds(90), RetryAfter.parse("Fri, 13 Nov 2026 12:01:30 GMT", now));
    assertEquals(Duration.ZERO, RetryAfter.parse("Fri, 13 Nov 2026 11:00:00 GMT", now));
    assertEquals(Duration.ofSeconds(31), RetryAfter.parse("2026-11-13T12:00:30.5Z", now));
    assertEquals(
        Duration.ofSeconds(1_000_000_000_000_000_000L), RetryAfter.parse("9".repeat(40), now));
    // The other two forms RFC 9110 asks recipients to accept, and ISO without an offset (UTC).
    assertEquals(Duration.ofSeconds(90), RetryAfter.parse("Friday, 13-Nov-26 12:01:30 GMT", now));
    assertEquals(Duration.ofSeconds(90), RetryAfter.parse("Fri Nov 13 12:01:30 2026", now));
    assertEquals(
        Duration.ofSeconds(90),
        RetryAfter.parse("Fri Nov  3 12:01:30 2026", Instant.parse("2026-11-03T12:00:00Z")));
    assertEquals(Duration.ofSeconds(30), RetryAfter.parse("2026-11-13T12:00:30", now));
    assertEquals(Duration.ofSeconds(30), RetryAfter.parse("2026-11-13 12:00:30Z", now));
    assertEquals(Duration.ZERO, RetryAfter.parse("Sunday, 06-Nov-94 08:49:37 GMT", now));
    assertNull(RetryAfter.parse("Friday, 13-Xyz-26 12:01:30 GMT", now));
    assertNull(RetryAfter.parse("Fri Nov 31 12:01:30 2026", now));
    assertNull(RetryAfter.parse("soon", now));
    assertNull(RetryAfter.parse("-1", now));
    assertNull(RetryAfter.parse(null, now));
  }

  @Test
  void survivesJavaSerialization() throws Exception {
    UnprocessableEntityException error =
        new UnprocessableEntityException(422, Map.of("x-request-id", List.of("r")), VALIDATION);
    error.setIdempotencyKey("k");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(error);
    }
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      UnprocessableEntityException copy = (UnprocessableEntityException) in.readObject();
      assertEquals(error.getMessage(), copy.getMessage());
      assertEquals("k", copy.getIdempotencyKey());
      assertEquals(error.getProblem(), copy.getProblem());
      assertEquals(error.getErrors(), copy.getErrors());
      assertEquals("r", copy.getHeaders().firstValue("x-request-id").orElse(null));
    }
  }
}
