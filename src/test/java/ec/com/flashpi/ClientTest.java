package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.com.flashpi.FakeHttpClient.Answer;
import ec.com.flashpi.Support.FakeTiming;
import ec.com.flashpi.exceptions.FlashpiException;
import ec.com.flashpi.models.Environment;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientTest {
  private static Flashpi.Builder base() {
    return Flashpi.builder().environmentVariables(name -> null);
  }

  @Test
  void readsTheKeyFromTheEnvironment() {
    FakeHttpClient http =
        new FakeHttpClient().then(Answer.json(200, "{\"status\":\"operational\"}"));
    Flashpi flashpi =
        Flashpi.builder()
            .httpClient(http)
            .environmentVariables(name -> name.equals("FLASHPI_API_KEY") ? "fp_live_abc" : null)
            .build();
    flashpi.status().get();
    assertEquals("Bearer fp_live_abc", http.last().header("authorization"));
    assertEquals(Environment.LIVE, flashpi.getEnvironment());
  }

  @Test
  void anArgumentWinsOverTheEnvironment() {
    Flashpi flashpi =
        Flashpi.builder().apiKey("fp_test_abc").environmentVariables(name -> "fp_live_xyz").build();
    assertEquals(Environment.TEST, flashpi.getEnvironment());
  }

  @Test
  void refusesAMissingKey() {
    FlashpiException error = assertThrows(FlashpiException.class, () -> base().build());
    assertTrue(error.getMessage().startsWith("Missing API key"));
  }

  @Test
  void refusesAWebhookSecretAsAKey() {
    FlashpiException error =
        assertThrows(FlashpiException.class, () -> base().apiKey("whsec_abc").build());
    assertTrue(error.getMessage().contains("webhook signing secret"));
  }

  @Test
  void refusesAKeyWithoutItsPrefix() {
    FlashpiException error =
        assertThrows(FlashpiException.class, () -> base().apiKey("sk_live_abc").build());
    assertEquals("The API key must start with fp_test_ or fp_live_", error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"fp_test_a b", "fp_test_a\nb", "fp_test_añb", "fp_test_a\u0000b"})
  void refusesAKeyWithCharactersAKeyCannotHaveWithoutShowingIt(String key) {
    FlashpiException error = assertThrows(FlashpiException.class, () -> base().apiKey(key).build());
    assertFalse(error.getMessage().contains("fp_test_a"));
    assertTrue(error.getMessage().contains("copy it again"));
  }

  @Test
  void trimsTheKeyAsJavaScriptDoesWithTheByteOrderMark() {
    FakeHttpClient http =
        new FakeHttpClient().then(Answer.json(200, "{\"status\":\"operational\"}"));
    Flashpi flashpi = base().apiKey("﻿  fp_test_abc \n").httpClient(http).build();
    flashpi.status().get();
    assertEquals("Bearer fp_test_abc", http.last().header("authorization"));
  }

  @Test
  void neverShowsTheKey() {
    Flashpi flashpi = base().apiKey("fp_live_secretsecret").build();
    assertEquals(
        "Flashpi{environment=live, baseUrl=https://api.flashpi.com.ec}", flashpi.toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"ftp://api.test", "api.test", "https://", "https://api.test?x=1", "http://a b"})
  void refusesABaseUrlThatIsNotHttp(String url) {
    assertThrows(FlashpiException.class, () -> base().apiKey(Support.KEY).baseUrl(url).build());
  }

  @Test
  void dropsTheTrailingSlashesOfTheBaseUrl() {
    FakeHttpClient http =
        new FakeHttpClient().then(Answer.json(200, "{\"status\":\"operational\"}"));
    Flashpi flashpi =
        base()
            .apiKey(Support.KEY)
            .baseUrl("http://localhost:8080/proxy//")
            .httpClient(http)
            .build();
    flashpi.status().get();
    assertEquals("http://localhost:8080/proxy/v1/status", http.last().uri.toString());
  }

  @Test
  void refusesBadTimeoutsAndRetries() {
    assertThrows(
        FlashpiException.class, () -> base().apiKey(Support.KEY).timeout(Duration.ZERO).build());
    assertThrows(
        FlashpiException.class,
        () -> base().apiKey(Support.KEY).timeout(Duration.ofSeconds(-1)).build());
    assertThrows(
        FlashpiException.class,
        () -> base().apiKey(Support.KEY).timeout(Duration.ofMillis(2_147_483_648L)).build());
    assertNotNull(base().apiKey(Support.KEY).timeout(Duration.ofMillis(2_147_483_647L)).build());
    assertThrows(FlashpiException.class, () -> base().apiKey(Support.KEY).maxRetries(-1).build());
  }

  @Test
  void refusesAnIdempotencyKeyInTheDefaultHeaders() {
    FlashpiException error =
        assertThrows(
            FlashpiException.class,
            () -> base().apiKey(Support.KEY).defaultHeader("IDEMPOTENCY-KEY", "x").build());
    assertTrue(error.getMessage().contains("unique per operation"));
  }

  @Test
  void refusesAnHttpClientThatFollowsRedirects() {
    FakeHttpClient http = new FakeHttpClient().redirects(HttpClient.Redirect.NORMAL);
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> base().apiKey(Support.KEY).httpClient(http).build());
    assertTrue(error.getMessage().contains("followRedirects"));
  }

  @Test
  void createsAClientThatNeverFollowsRedirects() throws Exception {
    try (Flashpi flashpi = base().apiKey(Support.KEY).build()) {
      java.lang.reflect.Field field = Flashpi.class.getDeclaredField("http");
      field.setAccessible(true);
      assertEquals(HttpClient.Redirect.NEVER, ((HttpClient) field.get(flashpi)).followRedirects());
    }
  }

  @Test
  void closesOnlyItsOwnHttpClient() {
    FakeHttpClient http = new FakeHttpClient();
    Flashpi flashpi = Support.client(http);
    flashpi.close();
    // The caller's client is still usable.
    http.then(Answer.json(200, "{\"status\":\"operational\"}"));
    assertEquals("operational", flashpi.status().get().getStatus().value());
  }

  @Test
  void sharesTheResourcesAndTheWebhooks() {
    Flashpi flashpi = Support.client(new FakeHttpClient());
    assertSame(flashpi.issuers().establishments(), flashpi.issuers().establishments());
    assertNotNull(flashpi.issuers().establishments().emissionPoints());
    assertNotNull(flashpi.issuers().certificates());
    assertNotNull(flashpi.webhookEndpoints().deliveries());
    assertEquals("Webhooks{tolerance=PT5M}", flashpi.webhooks().toString());
  }

  @Test
  void defaultHeadersAreCheckedWhenTheClientIsBuilt() {
    assertThrows(
        IllegalArgumentException.class,
        () -> base().apiKey(Support.KEY).defaultHeaders(Map.of("X-Bad", "a\nb")).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> base().apiKey(Support.KEY).defaultHeaders(Map.of("Bad Name", "a")).build());
  }

  @Test
  void theUserAgentNamesTheSdk() {
    FakeHttpClient http =
        new FakeHttpClient().then(Answer.json(200, "{\"status\":\"operational\"}"));
    Support.client(http, new FakeTiming()).status().get();
    assertEquals("flashpi-java/" + Version.VERSION, http.last().header("user-agent"));
  }
}
