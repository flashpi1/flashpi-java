package ec.com.flashpi;

import com.fasterxml.jackson.databind.JsonNode;
import ec.com.flashpi.exceptions.WebhookVerificationException;
import ec.com.flashpi.exceptions.WebhookVerificationException.Reason;
import ec.com.flashpi.internal.Json;
import ec.com.flashpi.models.WebhookEvent;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies the webhooks Flashpi sends (Standard Webhooks). It needs no API key: use {@code new
 * Webhooks()} on its own, or {@code flashpi.webhooks()}.
 *
 * <p>A webhook is signed with HMAC-SHA256 over {@code webhook-id.webhook-timestamp.body} with the
 * endpoint's {@code whsec_} secret. During a secret rotation Flashpi signs with both secrets, so
 * the {@code webhook-signature} header lists several signatures. Pass the raw body exactly as it
 * arrived: a body parsed and serialized again does not match.
 *
 * <pre>{@code
 * WebhookEvent event = webhooks.unwrap(body, request::getHeader, System.getenv("FLASHPI_WEBHOOK_SECRET"));
 * if (event instanceof WebhookEndpointVerificationEvent) { ... }
 * }</pre>
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class Webhooks {
  /** The default largest gap between a webhook's timestamp and the receiver's clock. */
  public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

  private static final String SECRET_PREFIX = "whsec_";
  // Key sizes of a Standard Webhooks secret (Flashpi makes 32-byte keys).
  private static final int MIN_SECRET_BYTES = 24;
  private static final int MAX_SECRET_BYTES = 64;
  private static final int SIGNATURE_BYTES = 32;
  private static final Pattern TIMESTAMP = Pattern.compile("[0-9]{1,12}");
  private static final Pattern BASE64 = Pattern.compile("[A-Za-z0-9+/]*={0,2}");

  private final Duration tolerance;
  private final Clock clock;

  /** Verifies webhooks with a tolerance of 300 seconds and the system clock. */
  public Webhooks() {
    this(DEFAULT_TOLERANCE, Clock.systemUTC());
  }

  /**
   * Verifies webhooks with another tolerance.
   *
   * @param tolerance the largest gap between a webhook's timestamp and now, zero or more
   */
  public Webhooks(Duration tolerance) {
    this(tolerance, Clock.systemUTC());
  }

  /**
   * Verifies webhooks with another tolerance and clock (for tests).
   *
   * @param tolerance the largest gap between a webhook's timestamp and now, zero or more
   * @param clock the clock to compare the timestamp with
   */
  public Webhooks(Duration tolerance, Clock clock) {
    Objects.requireNonNull(tolerance, "tolerance");
    if (tolerance.isNegative()) {
      throw new IllegalArgumentException("tolerance must be zero or more");
    }
    this.tolerance = tolerance;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Checks that a webhook comes from Flashpi and is recent.
   *
   * @param payload the raw body, as it arrived
   * @param headers the request headers: any map, names in any case, values as text or lists of text
   *     (JAX-RS's {@code MultivaluedMap}, Spring's {@code HttpHeaders} before Spring 7; from Spring
   *     7 on, which no longer makes it a map, pass {@code headers::getFirst})
   * @param secret the endpoint's signing secret ({@code whsec_...})
   * @throws WebhookVerificationException when it does not: answer 400 and ignore the request
   */
  public void verifySignature(byte[] payload, Map<String, ?> headers, String secret) {
    verify(payload, lookup(headers), secret);
  }

  /**
   * Checks that a webhook comes from Flashpi and is recent.
   *
   * @param payload the raw body, as it arrived
   * @param headers the request headers: any map, names in any case, values as text or lists of text
   * @param secret the endpoint's signing secret ({@code whsec_...})
   * @throws WebhookVerificationException when it does not: answer 400 and ignore the request
   */
  public void verifySignature(String payload, Map<String, ?> headers, String secret) {
    verify(payload, lookup(headers), secret);
  }

  /**
   * Checks that a webhook comes from Flashpi and is recent.
   *
   * @param payload the raw body, as it arrived
   * @param header reads a header by name, such as a servlet's {@code request::getHeader}
   * @param secret the endpoint's signing secret ({@code whsec_...})
   * @throws WebhookVerificationException when it does not: answer 400 and ignore the request
   */
  public void verifySignature(byte[] payload, Function<String, String> header, String secret) {
    verify(payload, Objects.requireNonNull(header, "header"), secret);
  }

  /**
   * Checks that a webhook comes from Flashpi and is recent.
   *
   * @param payload the raw body, as it arrived
   * @param header reads a header by name, such as a servlet's {@code request::getHeader}
   * @param secret the endpoint's signing secret ({@code whsec_...})
   * @throws WebhookVerificationException when it does not: answer 400 and ignore the request
   */
  public void verifySignature(String payload, Function<String, String> header, String secret) {
    verify(payload, Objects.requireNonNull(header, "header"), secret);
  }

  /**
   * Verifies a webhook (see {@link #verifySignature(byte[], Map, String)}) and reads its event.
   *
   * @param payload the raw body, as it arrived
   * @param headers the request headers
   * @param secret the endpoint's signing secret ({@code whsec_...})
   * @return the event: check its class with instanceof
   * @throws WebhookVerificationException when it is not verified or not an event
   */
  public WebhookEvent unwrap(byte[] payload, Map<String, ?> headers, String secret) {
    return event(verify(payload, lookup(headers), secret));
  }

  /**
   * Verifies a webhook (see {@link #verifySignature(String, Map, String)}) and reads its event.
   *
   * @param payload the raw body, as it arrived
   * @param headers the request headers
   * @param secret the endpoint's signing secret ({@code whsec_...})
   * @return the event: check its class with instanceof
   * @throws WebhookVerificationException when it is not verified or not an event
   */
  public WebhookEvent unwrap(String payload, Map<String, ?> headers, String secret) {
    return event(verify(payload, lookup(headers), secret));
  }

  /**
   * Verifies a webhook (see {@link #verifySignature(byte[], Function, String)}) and reads its
   * event.
   *
   * @param payload the raw body, as it arrived
   * @param header reads a header by name, such as a servlet's {@code request::getHeader}
   * @param secret the endpoint's signing secret ({@code whsec_...})
   * @return the event: check its class with instanceof
   * @throws WebhookVerificationException when it is not verified or not an event
   */
  public WebhookEvent unwrap(byte[] payload, Function<String, String> header, String secret) {
    return event(verify(payload, Objects.requireNonNull(header, "header"), secret));
  }

  /**
   * Verifies a webhook (see {@link #verifySignature(String, Function, String)}) and reads its
   * event.
   *
   * @param payload the raw body, as it arrived
   * @param header reads a header by name, such as a servlet's {@code request::getHeader}
   * @param secret the endpoint's signing secret ({@code whsec_...})
   * @return the event: check its class with instanceof
   * @throws WebhookVerificationException when it is not verified or not an event
   */
  public WebhookEvent unwrap(String payload, Function<String, String> header, String secret) {
    return event(verify(payload, Objects.requireNonNull(header, "header"), secret));
  }

  @Override
  public String toString() {
    return "Webhooks{tolerance=" + tolerance + "}";
  }

  /**
   * Checks the secret, the headers, the timestamp, the body and the signatures, in the order of the
   * other Flashpi SDKs, and returns the body's bytes.
   */
  private byte[] verify(Object payload, Function<String, String> header, String secret) {
    Mac signer = signer(secret);
    String webhookId = header.apply("webhook-id");
    String timestamp = header.apply("webhook-timestamp");
    String signatures = header.apply("webhook-signature");
    if (isEmpty(webhookId) || isEmpty(timestamp) || isEmpty(signatures)) {
      throw new WebhookVerificationException(
          Reason.MISSING_HEADERS,
          "The webhook-id, webhook-timestamp and webhook-signature headers are required");
    }
    if (!TIMESTAMP.matcher(timestamp).matches()) {
      throw new WebhookVerificationException(
          Reason.INVALID_TIMESTAMP, "The webhook-timestamp is not valid");
    }
    long gap = Math.abs(clock.instant().getEpochSecond() - Long.parseLong(timestamp));
    if (Duration.ofSeconds(gap).compareTo(tolerance) > 0) {
      throw new WebhookVerificationException(
          Reason.TIMESTAMP_OUT_OF_RANGE,
          "The webhook-timestamp is more than " + seconds(tolerance) + " seconds away from now");
    }
    byte[] body = payload(payload);
    byte[] prefix;
    try {
      prefix = strictUtf8(webhookId + "." + timestamp + ".");
    } catch (CharacterCodingException error) {
      // A webhook-id that is not text was not signed by Flashpi.
      throw noMatchingSignature();
    }
    signer.update(prefix);
    byte[] expected = signer.doFinal(body);
    try {
      for (String entry : signatures.split(" ", -1)) {
        int comma = entry.indexOf(',');
        if (comma < 0 || !entry.substring(0, comma).equals("v1")) {
          continue;
        }
        byte[] signature = decodeBase64(entry.substring(comma + 1));
        if (signature != null
            && signature.length == SIGNATURE_BYTES
            && MessageDigest.isEqual(signature, expected)) {
          return body;
        }
      }
    } finally {
      Arrays.fill(expected, (byte) 0);
    }
    throw noMatchingSignature();
  }

  private static WebhookVerificationException noMatchingSignature() {
    return new WebhookVerificationException(
        Reason.NO_MATCHING_SIGNATURE, "No webhook-signature matches the body and the secret");
  }

  /** An HMAC-SHA256 keyed with the decoded secret; the decoded key is cleared once used. */
  private static Mac signer(String secret) {
    if (secret == null || secret.trim().isEmpty()) {
      throw new WebhookVerificationException(
          Reason.INVALID_SECRET,
          "The webhook secret is empty: copy it from the console or webhookEndpoints().getSecret()");
    }
    String encoded = secret.trim();
    if (encoded.startsWith(SECRET_PREFIX)) {
      encoded = encoded.substring(SECRET_PREFIX.length());
    }
    byte[] key = decodeBase64(encoded);
    int size = key == null ? 0 : key.length;
    try {
      if (size == 0) {
        throw new WebhookVerificationException(
            Reason.INVALID_SECRET,
            "The webhook secret is not a whsec_ secret (base64 after the prefix)");
      }
      if (size < MIN_SECRET_BYTES || size > MAX_SECRET_BYTES) {
        throw new WebhookVerificationException(
            Reason.INVALID_SECRET,
            "The webhook secret is not a complete whsec_ secret: copy it again from the console or"
                + " webhookEndpoints().getSecret()");
      }
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac;
    } catch (NoSuchAlgorithmException | InvalidKeyException error) {
      throw new IllegalStateException("This Java has no HMAC-SHA256", error);
    } finally {
      if (key != null) {
        Arrays.fill(key, (byte) 0);
      }
    }
  }

  /** The event of a verified body. */
  private static WebhookEvent event(byte[] payload) {
    JsonNode node;
    try {
      node = Json.mapper().readTree(new String(payload, StandardCharsets.UTF_8));
    } catch (IOException error) {
      throw new WebhookVerificationException(
          Reason.INVALID_PAYLOAD, "The webhook body is not JSON", error);
    }
    if (node == null || !node.isObject()) {
      throw new WebhookVerificationException(
          Reason.INVALID_PAYLOAD, "The webhook body is not a JSON object");
    }
    try {
      return Json.answer(WebhookEvent.class).readValue(node);
    } catch (IOException error) {
      throw new WebhookVerificationException(
          Reason.INVALID_PAYLOAD, "The webhook body is not an event this SDK can read", error);
    }
  }

  /** Headers by name without case; repeated signature headers are joined, otherwise the first. */
  private static Function<String, String> lookup(Map<String, ?> headers) {
    Objects.requireNonNull(headers, "headers");
    return name -> {
      List<String> values = new ArrayList<>();
      for (Map.Entry<String, ?> entry : headers.entrySet()) {
        if (entry.getKey() == null || !entry.getKey().toLowerCase(Locale.ROOT).equals(name)) {
          continue;
        }
        Object value = entry.getValue();
        if (value instanceof String) {
          values.add((String) value);
        } else if (value instanceof Collection) {
          for (Object item : (Collection<?>) value) {
            if (item instanceof String) {
              values.add((String) item);
            }
          }
        } else if (value instanceof String[]) {
          values.addAll(Arrays.asList((String[]) value));
        }
      }
      if (values.isEmpty()) {
        return null;
      }
      return name.equals("webhook-signature") ? String.join(" ", values) : values.get(0);
    };
  }

  /**
   * The bytes of the body: bytes as they are, text as UTF-8 (text that has no UTF-8, such as a lone
   * surrogate, is not a valid body).
   */
  private static byte[] payload(Object payload) {
    if (payload instanceof byte[]) {
      return (byte[]) payload;
    }
    if (payload instanceof String) {
      try {
        return strictUtf8((String) payload);
      } catch (CharacterCodingException error) {
        throw new WebhookVerificationException(
            Reason.INVALID_PAYLOAD, "The webhook body is not valid text", error);
      }
    }
    throw new WebhookVerificationException(
        Reason.INVALID_PAYLOAD, "Pass the raw webhook body as bytes or text (not parsed JSON)");
  }

  private static byte[] strictUtf8(String text) throws CharacterCodingException {
    ByteBuffer encoded =
        StandardCharsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(text));
    byte[] bytes = new byte[encoded.remaining()];
    encoded.get(bytes);
    return bytes;
  }

  /** Standard base64, padding optional (as atob), or null when it is not base64. */
  private static byte[] decodeBase64(String value) {
    if (!BASE64.matcher(value).matches() || value.length() % 4 == 1) {
      return null;
    }
    String data = value.length() % 4 == 0 ? value.replaceAll("=+$", "") : value;
    if (data.contains("=") || data.length() % 4 == 1) {
      return null;
    }
    try {
      return Base64.getDecoder().decode(data + "==".substring(0, (4 - data.length() % 4) % 4));
    } catch (IllegalArgumentException error) {
      return null;
    }
  }

  private static boolean isEmpty(String value) {
    return value == null || value.isEmpty();
  }

  private static String seconds(Duration duration) {
    return duration.getNano() == 0
        ? Long.toString(duration.getSeconds())
        : java.math.BigDecimal.valueOf(duration.toNanos(), 9).stripTrailingZeros().toPlainString();
  }
}
