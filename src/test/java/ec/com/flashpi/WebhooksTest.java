package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.com.flashpi.exceptions.WebhookVerificationException;
import ec.com.flashpi.exceptions.WebhookVerificationException.Reason;
import ec.com.flashpi.models.DocumentEvent;
import ec.com.flashpi.models.DocumentEventType;
import ec.com.flashpi.models.Environment;
import ec.com.flashpi.models.EventThin;
import ec.com.flashpi.models.EventType;
import ec.com.flashpi.models.WebhookEndpointVerificationEvent;
import ec.com.flashpi.models.WebhookEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhooksTest {
  private static final byte[] KEY =
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
  private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(KEY);
  private static final long NOW = 1_794_571_200L;
  private static final Webhooks WEBHOOKS =
      new Webhooks(
          Webhooks.DEFAULT_TOLERANCE, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
  private static final String EVENT =
      "{\"id\":\"ev_1\",\"type\":\"invoice.authorized\",\"environment\":\"test\","
          + "\"createdAt\":\"2026-11-13T12:00:00Z\",\"data\":{\"id\":\"doc_1\",\"accessKey\":\"123\"}}";

  static String sign(byte[] key, String id, long timestamp, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    byte[] signature =
        mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
    return "v1," + Base64.getEncoder().encodeToString(signature);
  }

  private static Map<String, Object> headers(String body) throws Exception {
    Map<String, Object> headers = new HashMap<>();
    headers.put("Webhook-Id", "msg_1");
    headers.put("Webhook-Timestamp", Long.toString(NOW));
    headers.put("Webhook-Signature", sign(KEY, "msg_1", NOW, body));
    return headers;
  }

  private static Reason rejected(Runnable call) {
    return assertThrows(WebhookVerificationException.class, call::run).getReason();
  }

  @Test
  void unwrapsASignedDocumentEvent() throws Exception {
    WebhookEvent event = WEBHOOKS.unwrap(EVENT, headers(EVENT), SECRET);
    DocumentEvent document = assertInstanceOf(DocumentEvent.class, event);
    assertEquals(DocumentEventType.INVOICE_AUTHORIZED, document.getType());
    assertEquals("doc_1", document.getData().getId());
    assertEquals(Environment.TEST, event.getEnvironment());
    assertEquals("ev_1", event.getId());
  }

  @Test
  void unwrapsBytesAndServletStyleHeaders() throws Exception {
    Map<String, Object> headers = headers(EVENT);
    byte[] body = EVENT.getBytes(StandardCharsets.UTF_8);
    WebhookEvent event =
        WEBHOOKS.unwrap(
            body,
            name -> {
              for (Map.Entry<String, Object> header : headers.entrySet()) {
                if (header.getKey().equalsIgnoreCase(name)) {
                  return (String) header.getValue();
                }
              }
              return null;
            },
            SECRET);
    assertEquals("ev_1", event.getId());
  }

  @Test
  void unwrapsTheVerificationHandshake() throws Exception {
    String body =
        "{\"id\":\"wh_1\",\"type\":\"webhook_endpoint.verification\",\"environment\":\"live\","
            + "\"createdAt\":\"2026-11-13T12:00:00Z\",\"data\":{\"webhookEndpointId\":\"we_1\",\"challenge\":\"abc\"}}";
    WebhookEndpointVerificationEvent event =
        assertInstanceOf(
            WebhookEndpointVerificationEvent.class, WEBHOOKS.unwrap(body, headers(body), SECRET));
    assertEquals("abc", event.getData().getChallenge());
  }

  @Test
  void anEventOfATypeThisSdkDoesNotKnowIsThin() throws Exception {
    String body =
        "{\"id\":\"ev_2\",\"type\":\"invoice.voided\",\"environment\":\"test\",\"createdAt\":\"2026-11-13T12:00:00Z\",\"data\":{\"x\":1}}";
    EventThin event =
        assertInstanceOf(EventThin.class, WEBHOOKS.unwrap(body, headers(body), SECRET));
    assertEquals(EventType.UNKNOWN, event.getType());
    assertEquals(Map.of("x", 1), event.getAdditionalProperties().get("data"));
  }

  @Test
  void acceptsAnyOfSeveralSignaturesDuringARotation() throws Exception {
    Map<String, Object> headers = headers(EVENT);
    byte[] other = "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8);
    headers.put(
        "webhook-signature",
        List.of(
            "v1,bm9wZQ==",
            sign(other, "msg_1", NOW, EVENT) + " " + headers.remove("Webhook-Signature")));
    WEBHOOKS.verifySignature(EVENT, headers, SECRET);
    WEBHOOKS.verifySignature(EVENT, headers, "whsec_" + Base64.getEncoder().encodeToString(other));
  }

  @Test
  void acceptsASecretWithoutPrefixOrPadding() throws Exception {
    String unpadded = Base64.getEncoder().withoutPadding().encodeToString(KEY);
    WEBHOOKS.verifySignature(EVENT, headers(EVENT), "  " + unpadded + "\n");
  }

  @Test
  void rejectsAnAlteredBody() throws Exception {
    Map<String, Object> headers = headers(EVENT);
    assertEquals(
        Reason.NO_MATCHING_SIGNATURE,
        rejected(() -> WEBHOOKS.verifySignature(EVENT + " ", headers, SECRET)));
  }

  @Test
  void rejectsMissingHeaders() throws Exception {
    Map<String, Object> headers = headers(EVENT);
    headers.remove("Webhook-Id");
    assertEquals(
        Reason.MISSING_HEADERS, rejected(() -> WEBHOOKS.verifySignature(EVENT, headers, SECRET)));
    headers.put("webhook-id", "");
    assertEquals(
        Reason.MISSING_HEADERS, rejected(() -> WEBHOOKS.verifySignature(EVENT, headers, SECRET)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "-1", "1.5", "1234567890123", " 1794571200", "1794571200\n"})
  void rejectsATimestampThatIsNotUnixSeconds(String timestamp) throws Exception {
    Map<String, Object> headers = headers(EVENT);
    headers.put("Webhook-Timestamp", timestamp);
    assertEquals(
        Reason.INVALID_TIMESTAMP, rejected(() -> WEBHOOKS.verifySignature(EVENT, headers, SECRET)));
  }

  @Test
  void rejectsATimestampTooFarFromNow() throws Exception {
    long old = NOW - 301;
    Map<String, Object> headers = headers(EVENT);
    headers.put("Webhook-Timestamp", Long.toString(old));
    headers.put("Webhook-Signature", sign(KEY, "msg_1", old, EVENT));
    WebhookVerificationException error =
        assertThrows(
            WebhookVerificationException.class,
            () -> WEBHOOKS.verifySignature(EVENT, headers, SECRET));
    assertEquals(Reason.TIMESTAMP_OUT_OF_RANGE, error.getReason());
    assertEquals(
        "The webhook-timestamp is more than 300 seconds away from now", error.getMessage());
    new Webhooks(Duration.ofSeconds(301), Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC))
        .verifySignature(EVENT, headers, SECRET);
  }

  @Test
  void aZeroToleranceAcceptsOnlyTheSameSecond() throws Exception {
    Webhooks strict =
        new Webhooks(
            Duration.ZERO, Clock.fixed(Instant.ofEpochSecond(NOW, 999_000_000), ZoneOffset.UTC));
    strict.verifySignature(EVENT, headers(EVENT), SECRET);
    assertThrows(IllegalArgumentException.class, () -> new Webhooks(Duration.ofSeconds(-1)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "whsec_", "whsec_!!!!", "whsec_c2hvcnQ=", "whsec_a"})
  void rejectsASecretThatIsNotAWhsecSecretWithoutShowingIt(String secret) throws Exception {
    Map<String, Object> headers = headers(EVENT);
    WebhookVerificationException error =
        assertThrows(
            WebhookVerificationException.class,
            () -> WEBHOOKS.verifySignature(EVENT, headers, secret));
    assertEquals(Reason.INVALID_SECRET, error.getReason());
    if (secret.trim().length() > "whsec_".length()) {
      assertFalse(error.getMessage().contains(secret.trim()));
    }
  }

  @Test
  void aNullSecretIsInvalid() throws Exception {
    Map<String, Object> headers = headers(EVENT);
    assertEquals(
        Reason.INVALID_SECRET,
        rejected(() -> WEBHOOKS.verifySignature(EVENT, headers, (String) null)));
  }

  @Test
  void ignoresSignaturesOfOtherVersionsOrSizes() throws Exception {
    Map<String, Object> headers = headers(EVENT);
    String valid = (String) headers.get("Webhook-Signature");
    headers.put("Webhook-Signature", "v2," + valid.substring(3) + " v1 v1,AAAA v1,not-base64");
    assertEquals(
        Reason.NO_MATCHING_SIGNATURE,
        rejected(() -> WEBHOOKS.verifySignature(EVENT, headers, SECRET)));
  }

  @Test
  void aBodyThatIsNotTextOrJsonIsInvalid() throws Exception {
    Map<String, Object> headers = headers(EVENT);
    assertEquals(
        Reason.INVALID_PAYLOAD,
        rejected(() -> WEBHOOKS.verifySignature((byte[]) null, headers, SECRET)));
    assertEquals(
        Reason.INVALID_PAYLOAD,
        rejected(() -> WEBHOOKS.verifySignature("a\ud800", headers, SECRET)));
    String array = "[1]";
    Map<String, Object> signed = headers(array);
    assertEquals(Reason.INVALID_PAYLOAD, rejected(() -> WEBHOOKS.unwrap(array, signed, SECRET)));
    String broken = "{";
    Map<String, Object> brokenHeaders = headers(broken);
    assertEquals(
        Reason.INVALID_PAYLOAD, rejected(() -> WEBHOOKS.unwrap(broken, brokenHeaders, SECRET)));
  }

  @Test
  void aWebhookIdWithoutUtf8MatchesNothing() throws Exception {
    Map<String, Object> headers = headers(EVENT);
    headers.put("Webhook-Id", "msg\ud800");
    assertEquals(
        Reason.NO_MATCHING_SIGNATURE,
        rejected(() -> WEBHOOKS.verifySignature(EVENT, headers, SECRET)));
  }

  @Test
  void checksTheSecretBeforeTheHeaders() {
    assertEquals(
        Reason.INVALID_SECRET, rejected(() -> WEBHOOKS.verifySignature(EVENT, Map.of(), "")));
  }

  @Test
  void reasonsHaveTheOtherSdksNames() {
    assertEquals("no_matching_signature", Reason.NO_MATCHING_SIGNATURE.value());
    assertNull(new Webhooks().toString().contains("whsec") ? "leak" : null);
    assertTrue(new Webhooks().toString().startsWith("Webhooks{"));
  }
}
