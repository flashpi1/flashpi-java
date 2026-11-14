/*
 * Uses the built jar alone, with Jackson only, as an application would (no test classes, no
 * Kotlin): CI runs it on the oldest and the newest Java.
 *
 *   java -cp target/flashpi-java-<version>.jar:<jackson jars> scripts/InstalledCheck.java
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.com.flashpi.Flashpi;
import ec.com.flashpi.Webhooks;
import ec.com.flashpi.models.CreateInvoice;
import ec.com.flashpi.models.DocumentEvent;
import ec.com.flashpi.models.DocumentStatus;
import ec.com.flashpi.models.WebhookEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class InstalledCheck {
  public static void main(String[] args) throws Exception {
    Flashpi flashpi = Flashpi.builder().apiKey("fp_test_installed").build();
    check(
        "Flashpi{environment=test, baseUrl=https://api.flashpi.com.ec}".equals(flashpi.toString()),
        flashpi.toString());
    flashpi.close();

    ObjectMapper mapper = new ObjectMapper();
    CreateInvoice body =
        CreateInvoice.builder()
            .issuerId("issuer")
            .issueDate(LocalDate.of(2026, 11, 13))
            .tip(null)
            .build();
    String json = mapper.writeValueAsString(body);
    check("{\"issuerId\":\"issuer\",\"issueDate\":\"2026-11-13\",\"tip\":null}".equals(json), json);

    byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    String payload =
        "{\"id\":\"ev\",\"type\":\"invoice.authorized\",\"environment\":\"test\","
            + "\"createdAt\":\"2026-11-13T12:00:00Z\",\"data\":{\"status\":\"authorized\"}}";
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    String signature =
        Base64.getEncoder()
            .encodeToString(
                mac.doFinal(("msg.1794571200." + payload).getBytes(StandardCharsets.UTF_8)));
    Webhooks webhooks =
        new Webhooks(
            Duration.ofMinutes(5),
            Clock.fixed(Instant.ofEpochSecond(1_794_571_200L), ZoneOffset.UTC));
    WebhookEvent event =
        webhooks.unwrap(
            payload,
            Map.of(
                "webhook-id",
                "msg",
                "webhook-timestamp",
                "1794571200",
                "webhook-signature",
                "v1," + signature),
            "whsec_" + Base64.getEncoder().encodeToString(key));
    check(
        ((DocumentEvent) event).getData().getStatus() == DocumentStatus.AUTHORIZED,
        event.toString());

    System.out.println(
        "flashpi-java " + ec.com.flashpi.Version.VERSION + " works on Java " + Runtime.version());
  }

  private static void check(boolean condition, String what) {
    if (!condition) {
      throw new AssertionError(what);
    }
  }
}
