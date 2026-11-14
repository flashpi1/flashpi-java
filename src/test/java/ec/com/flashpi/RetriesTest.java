package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.com.flashpi.FakeHttpClient.Answer;
import ec.com.flashpi.Support.FakeTiming;
import ec.com.flashpi.exceptions.ApiConnectionException;
import ec.com.flashpi.exceptions.ApiException;
import ec.com.flashpi.exceptions.ApiTimeoutException;
import ec.com.flashpi.exceptions.ConflictException;
import ec.com.flashpi.exceptions.FlashpiException;
import ec.com.flashpi.exceptions.InternalServerException;
import ec.com.flashpi.exceptions.NotFoundException;
import ec.com.flashpi.exceptions.RateLimitException;
import ec.com.flashpi.exceptions.UnprocessableEntityException;
import ec.com.flashpi.models.CreateInvoice;
import ec.com.flashpi.models.Document;
import ec.com.flashpi.models.DocumentOptions;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RetriesTest {
  private static final CreateInvoice BODY =
      CreateInvoice.builder().issuerId("issuer").establishment("001").emissionPoint("001").build();

  private static String reused(String documentId) {
    return "{\"type\":\"x\",\"title\":\"t\",\"status\":422,\"detail\":\"reused\",\"instance\":\"/v1/invoices\","
        + "\"code\":\"idempotency_key_reused\",\"errors\":[],\"requestId\":\"r\""
        + (documentId == null ? "" : ",\"documentId\":\"" + documentId + "\"")
        + "}";
  }

  @ParameterizedTest
  @ValueSource(ints = {408, 429, 500, 502, 503, 504})
  void retriesTransientStatusesWithTheSameIdempotencyKey(int status) {
    FakeHttpClient http =
        new FakeHttpClient().then(Answer.status(status)).then(Answer.json(202, Support.DOCUMENT));
    FakeTiming timing = new FakeTiming();
    Document document = Support.client(http, timing).invoices().create(BODY);
    assertEquals(Support.DOCUMENT_ID, document.getId());
    assertEquals(2, http.requests.size());
    String key = http.requests.get(0).header("idempotency-key");
    assertEquals(36, key.length());
    assertEquals(key, http.requests.get(1).header("idempotency-key"));
    assertEquals(http.requests.get(0).bodyText(), http.requests.get(1).bodyText());
    assertEquals(List.of(Duration.ofMillis(500)), timing.sleeps);
  }

  @Test
  void backsOffExponentiallyWithJitterUpTo8Seconds() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.status(503));
    FakeTiming timing = new FakeTiming();
    timing.random = 0.5;
    Flashpi flashpi = Support.builder(http, timing).maxRetries(6).build();
    assertThrows(InternalServerException.class, () -> flashpi.status().get());
    assertEquals(
        List.of(438L, 875L, 1750L, 3500L, 7000L, 7000L),
        timing.sleeps.stream().map(Duration::toMillis).collect(Collectors.toList()));
  }

  @Test
  void givesUpAfterMaxRetriesAndKeepsTheKeyOnTheError() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.problem(500, "internal_error"));
    InternalServerException error =
        assertThrows(
            InternalServerException.class,
            () ->
                Support.client(http)
                    .invoices()
                    .create(BODY, RequestOptions.builder().idempotencyKey("order-1").build()));
    assertEquals(3, http.requests.size());
    assertEquals("order-1", error.getIdempotencyKey());
    assertEquals("internal_error", error.getCode());
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404, 409, 422, 501})
  void neverRetriesOtherStatuses(int status) {
    FakeHttpClient http = new FakeHttpClient().always(Answer.problem(status, "x"));
    assertThrows(ApiException.class, () -> Support.client(http).status().get());
    assertEquals(1, http.requests.size());
  }

  @Test
  void neverRetriesTheMissingDemoIssuer() {
    FakeHttpClient http =
        new FakeHttpClient().always(Answer.problem(503, "demo_issuer_unavailable"));
    assertThrows(InternalServerException.class, () -> Support.client(http).invoices().create(BODY));
    assertEquals(1, http.requests.size());
  }

  @Test
  void honorsARetryAfterOf60SecondsOrLess() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.status(429).header("retry-after", "7"))
            .then(Answer.json(200, "{\"status\":\"operational\"}"));
    FakeTiming timing = new FakeTiming();
    Support.client(http, timing).status().get();
    assertEquals(List.of(Duration.ofSeconds(7)), timing.sleeps);
  }

  @Test
  void honorsARetryAfterDate() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.status(503).header("retry-after", "Fri, 13 Nov 2026 12:00:30 GMT"))
            .then(Answer.json(200, "{\"status\":\"operational\"}"));
    FakeTiming timing = new FakeTiming();
    Support.client(http, timing).status().get();
    assertEquals(List.of(Duration.ofSeconds(30)), timing.sleeps);
  }

  @Test
  void throwsALongerRetryAfterSuchAsADailyQuota() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.problem(429, "demo_issuer_quota_exceeded").header("retry-after", "3600"));
    RateLimitException error =
        assertThrows(RateLimitException.class, () -> Support.client(http).invoices().create(BODY));
    assertEquals(Duration.ofHours(1), error.getRetryAfter());
    assertEquals(1, http.requests.size());
  }

  @Test
  void retriesNetworkErrorsAndTimeouts() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.failure(new IOException("Connection reset")))
            .then(Answer.failure(new HttpTimeoutException("request timed out")))
            .then(Answer.json(200, "{\"status\":\"operational\"}"));
    Support.client(http).status().get();
    assertEquals(3, http.requests.size());
  }

  @Test
  void aNetworkErrorKeepsItsCauseAndTheUrl() {
    FakeHttpClient http =
        new FakeHttpClient().always(Answer.failure(new IOException("Connection refused")));
    ApiConnectionException error =
        assertThrows(ApiConnectionException.class, () -> Support.client(http).status().get());
    assertEquals("GET https://api.test/v1/status failed: Connection refused", error.getMessage());
    assertTrue(error.getCause() instanceof IOException);
  }

  @Test
  void anAttemptThatNeverAnswersTimesOutAtItsDeadline() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.hang());
    Flashpi flashpi = Support.builder(http, new FakeTiming()).maxRetries(1).build();
    long started = System.nanoTime();
    ApiTimeoutException error =
        assertThrows(
            ApiTimeoutException.class,
            () ->
                flashpi
                    .status()
                    .get(RequestOptions.builder().timeout(Duration.ofMillis(50)).build()));
    assertTrue(System.nanoTime() - started < Duration.ofSeconds(5).toNanos());
    assertEquals("GET https://api.test/v1/status timed out after 0.05 seconds", error.getMessage());
    assertEquals(2, http.requests.size());
  }

  @Test
  void zeroRetriesPerCall() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.status(503));
    assertThrows(
        InternalServerException.class,
        () -> Support.client(http).status().get(RequestOptions.builder().maxRetries(0).build()));
    assertEquals(1, http.requests.size());
  }

  @Test
  void returnsTheDocumentAnEarlierAttemptCreated() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.status(502))
            .then(Answer.json(422, reused(Support.DOCUMENT_ID)))
            .then(Answer.json(200, Support.DOCUMENT));
    Document document = Support.client(http).invoices().create(BODY);
    assertEquals(Support.DOCUMENT_ID, document.getId());
    FakeHttpClient.Recorded read = http.last();
    assertEquals("GET", read.method);
    assertEquals("/v1/documents/" + Support.DOCUMENT_ID, read.path());
    assertNull(read.header("idempotency-key"));
  }

  @Test
  void aFirstAttemptAnsweredReusedIsTheCallersOwnMistake() {
    FakeHttpClient http = new FakeHttpClient().then(Answer.json(422, reused(Support.DOCUMENT_ID)));
    UnprocessableEntityException error =
        assertThrows(
            UnprocessableEntityException.class, () -> Support.client(http).invoices().create(BODY));
    assertEquals("idempotency_key_reused", error.getCode());
    assertEquals(Support.DOCUMENT_ID, error.getProblem().getDocumentId());
    assertEquals(1, http.requests.size());
  }

  @Test
  void aFailedRecoveryKeepsTheCallsKey() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.status(502))
            .then(Answer.json(422, reused(Support.DOCUMENT_ID)))
            .then(Answer.problem(404, "not_found"));
    NotFoundException error =
        assertThrows(
            NotFoundException.class,
            () ->
                Support.client(http)
                    .invoices()
                    .create(BODY, RequestOptions.builder().idempotencyKey("order-7").build()));
    assertEquals("order-7", error.getIdempotencyKey());
  }

  @Test
  void otherPostsAreNotRecoveredAsDocuments() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.status(502))
            .then(Answer.json(422, reused(Support.DOCUMENT_ID)));
    assertThrows(
        UnprocessableEntityException.class,
        () ->
            Support.client(http)
                .issuers()
                .create(ec.com.flashpi.models.CreateIssuer.builder().legalName("X").build()));
  }

  @Test
  void aRetriedDeleteThatFindsNothingIsDone() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.failure(new IOException("reset")))
            .then(Answer.problem(404, "not_found").header("x-request-id", "req_404"));
    ApiResponse<Void> result =
        Support.client(http).webhookEndpoints().withResponse().delete("we_1");
    assertEquals(204, result.getStatusCode());
    assertEquals("req_problem", result.getRequestId());
    assertTrue(result.getHeaders().firstValue("content-type").isEmpty());
  }

  @Test
  void aFirstDeleteThatFindsNothingIsNotFound() {
    FakeHttpClient http = new FakeHttpClient().then(Answer.problem(404, "not_found"));
    assertThrows(
        NotFoundException.class, () -> Support.client(http).webhookEndpoints().delete("we_1"));
  }

  @Test
  void waitsForItsOwnAttemptStillRunningWithoutSpendingRetries() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.failure(new IOException("reset")))
            .then(Answer.problem(409, "idempotency_key_in_progress"))
            .then(Answer.problem(409, "idempotency_key_in_progress"))
            .then(Answer.problem(409, "idempotency_key_in_progress"))
            .then(Answer.json(200, Support.DOCUMENT));
    FakeTiming timing = new FakeTiming();
    Flashpi flashpi = Support.builder(http, timing).maxRetries(1).build();
    Document document = flashpi.invoices().create(BODY);
    assertEquals(Support.DOCUMENT_ID, document.getId());
    assertEquals(
        List.of(
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4)),
        timing.sleeps);
  }

  @Test
  void inProgressIsAConflictWhenNoEarlierAttemptWasLost() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.problem(409, "idempotency_key_in_progress"))
            .then(Answer.json(200, Support.DOCUMENT));
    FakeTiming timing = new FakeTiming();
    // Retried as a 409 in progress (another caller's attempt), with at least 1 second of wait.
    Support.client(http, timing).invoices().create(BODY);
    assertEquals(List.of(Duration.ofSeconds(1)), timing.sleeps);
  }

  @Test
  void stopsWaitingOnceTheCallsTimeIsUp() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.failure(new IOException("reset")))
            .always(Answer.problem(409, "idempotency_key_in_progress"));
    FakeTiming timing = new FakeTiming();
    Flashpi flashpi =
        Support.builder(http, timing).maxRetries(1).timeout(Duration.ofSeconds(3)).build();
    ConflictException error =
        assertThrows(ConflictException.class, () -> flashpi.invoices().create(BODY));
    assertEquals("idempotency_key_in_progress", error.getCode());
  }

  @Test
  void aSyncIssueWaitsAtLeastTheSriWaitPlus15Seconds() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.json(200, Support.DOCUMENT));
    Flashpi flashpi = Support.client(http);
    flashpi
        .invoices()
        .create(
            BODY.toBuilder()
                .options(
                    DocumentOptions.builder()
                        .waitForAuthorization(true)
                        .waitTimeoutSeconds(30)
                        .build())
                .build(),
            RequestOptions.builder().timeout(Duration.ofSeconds(5)).build());
    assertEquals(Duration.ofSeconds(45), http.last().timeout);
    flashpi
        .invoices()
        .create(
            BODY.toBuilder()
                .options(DocumentOptions.builder().waitForAuthorization(true).build())
                .build());
    assertEquals(Duration.ofSeconds(60), http.last().timeout);
    flashpi
        .invoices()
        .create(BODY, RequestOptions.builder().timeout(Duration.ofSeconds(5)).build());
    assertEquals(Duration.ofSeconds(5), http.last().timeout);
  }

  @Test
  void aBodyThatIsNotJsonIsAnErrorWithTheKey() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(
                Answer.bytes(
                    200, "<html>".getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/html"));
    FlashpiException error =
        assertThrows(FlashpiException.class, () -> Support.client(http).invoices().create(BODY));
    assertEquals("The API answered 200 with a body that is not JSON", error.getMessage());
    assertEquals(http.last().header("idempotency-key"), error.getIdempotencyKey());
  }

  @Test
  void anInterruptedWaitStopsTheCallAndKeepsTheInterrupt() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.status(503));
    Support.FakeTiming timing = new Support.FakeTiming();
    ec.com.flashpi.internal.Timing interrupting =
        new ec.com.flashpi.internal.Timing() {
          @Override
          public long nanoTime() {
            return timing.nanoTime();
          }

          @Override
          public java.time.Instant now() {
            return timing.now();
          }

          @Override
          public void sleep(Duration duration) throws InterruptedException {
            throw new InterruptedException("stop");
          }

          @Override
          public double random() {
            return 0;
          }
        };
    Flashpi flashpi =
        Flashpi.builder().apiKey(Support.KEY).httpClient(http).timing(interrupting).build();
    FlashpiException error =
        assertThrows(FlashpiException.class, () -> flashpi.invoices().create(BODY));
    assertTrue(Thread.interrupted());
    assertEquals("The call was interrupted while waiting to retry", error.getMessage());
    assertEquals(http.last().header("idempotency-key"), error.getIdempotencyKey());
  }
}
