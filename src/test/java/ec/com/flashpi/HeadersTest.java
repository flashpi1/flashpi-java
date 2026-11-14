package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.com.flashpi.FakeHttpClient.Answer;
import ec.com.flashpi.Support.FakeTiming;
import ec.com.flashpi.models.CreateIssuer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HeadersTest {
  private static final String STATUS = "{\"status\":\"operational\"}";

  @Test
  void sendsTheSdksHeadersOverTheCallersAndTheClients() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.json(200, STATUS));
    Flashpi flashpi =
        Support.builder(http, new FakeTiming())
            .language(Language.EN)
            .defaultHeader("X-Team", "billing")
            .defaultHeader("Accept-Language", "fr")
            .build();
    flashpi
        .status()
        .get(
            RequestOptions.builder()
                .language(Language.ES)
                .header("x-team", "erp")
                .header("Authorization", "Bearer someone-else")
                .header("X-Correlation-Id", "mine")
                .correlationId("order-42")
                .build());
    FakeHttpClient.Recorded request = http.last();
    assertEquals("es", request.header("accept-language"));
    assertEquals("erp", request.header("x-team"));
    assertEquals("order-42", request.header("x-correlation-id"));
    assertEquals("Bearer " + Support.KEY, request.header("authorization"));
    assertEquals(1, request.headers.allValues("authorization").size());
    assertEquals("application/json", request.header("accept"));
    assertNull(request.header("idempotency-key"));
    assertNull(request.header("content-type"));
  }

  @Test
  void theClientsLanguageAppliesToEveryCall() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.json(200, STATUS));
    Support.builder(http, new FakeTiming()).language(Language.ES).build().status().get();
    assertEquals("es", http.last().header("accept-language"));
  }

  @Test
  void aPostCarriesJsonAndAnIdempotencyKeyFromTheOptionOrTheHeader() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.json(201, "{\"id\":\"i\"}"));
    Flashpi flashpi = Support.client(http);
    CreateIssuer body = CreateIssuer.builder().legalName("ACME").build();
    flashpi.issuers().create(body, RequestOptions.builder().idempotencyKey("  order-1\t").build());
    assertEquals("order-1", http.last().header("idempotency-key"));
    assertEquals("application/json", http.last().header("content-type"));
    assertEquals("{\"legalName\":\"ACME\"}", http.last().bodyText());
    flashpi
        .issuers()
        .create(body, RequestOptions.builder().header("IDEMPOTENCY-KEY", "from-header").build());
    assertEquals("from-header", http.last().header("idempotency-key"));
    assertEquals(1, http.last().headers.allValues("idempotency-key").size());
  }

  @Test
  void textOutsideAsciiIsSentAsUtf8() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.json(200, STATUS));
    Support.client(http)
        .status()
        .get(RequestOptions.builder().header("X-Name", "Año ñandú").build());
    String wire = http.last().header("x-name");
    assertEquals(
        "Año ñandú",
        new String(wire.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a\nb", "a\rb", "a\u0000b", "a\u007fb", "a\ud800b"})
  void refusesValuesThatCannotBeSentWithoutShowingThem(String value) {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> RequestOptions.builder().header("X-Secret", value).build());
    assertFalse(error.getMessage().contains(value));
    assertThrows(
        IllegalArgumentException.class,
        () -> RequestOptions.builder().idempotencyKey(value).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> RequestOptions.builder().correlationId(value).build());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Content-Length",
        "transfer-encoding",
        "Host",
        "Connection",
        "Expect",
        "Upgrade",
        "Date"
      })
  void refusesHeadersTheHttpClientSets(String name) {
    assertThrows(
        IllegalArgumentException.class, () -> RequestOptions.builder().header(name, "1").build());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Bad Name", "naïve", "a:b"})
  void refusesNamesThatAreNotTokens(String name) {
    assertThrows(
        IllegalArgumentException.class, () -> RequestOptions.builder().header(name, "1").build());
  }

  @Test
  void trimsValuesAndAllowsTabsInside() {
    RequestOptions options = RequestOptions.builder().header("X-A", " \t a\tb \r\n").build();
    assertEquals("a\tb", options.getExtraHeaders().get("X-A"));
  }

  @Test
  void laterNamesReplaceEarlierOnesInAnyCase() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-A", "1");
    headers.put("x-a", "2");
    RequestOptions options = RequestOptions.builder().extraHeaders(headers).build();
    assertEquals(Map.of("x-a", "2"), options.getExtraHeaders());
  }

  @Test
  void optionsNeverShowHeaderValues() {
    RequestOptions options =
        RequestOptions.builder().header("X-Api-Secret", "s3cr3t").idempotencyKey("k").build();
    assertTrue(options.toString().contains("X-Api-Secret"));
    assertFalse(options.toString().contains("s3cr3t"));
    assertEquals("k", options.toBuilder().build().getIdempotencyKey());
  }
}
