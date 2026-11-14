package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ec.com.flashpi.exceptions.ApiConnectionException;
import ec.com.flashpi.exceptions.ApiException;
import ec.com.flashpi.exceptions.ApiTimeoutException;
import ec.com.flashpi.models.CreateIssuer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The SDK against a real HTTP server on localhost, with the JDK's own HttpClient. */
class NetworkTest {
  private HttpServer server;
  private ExecutorService executor;
  private final List<Map<String, List<String>>> received = new CopyOnWriteArrayList<>();
  private final Map<String, byte[]> bodies = new ConcurrentHashMap<>();

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext(
        "/v1/status", exchange -> respond(exchange, 200, "{\"status\":\"operational\"}"));
    server.createContext(
        "/v1/issuers",
        exchange -> {
          received.add(exchange.getRequestHeaders());
          bodies.put("issuer", exchange.getRequestBody().readAllBytes());
          respond(exchange, 201, "{\"id\":\"iss_1\",\"legalName\":\"ACME\"}");
        });
    server.createContext(
        "/v1/documents/trickle",
        exchange -> {
          exchange.getResponseHeaders().add("content-type", "application/json");
          exchange.sendResponseHeaders(200, 0);
          try (OutputStream out = exchange.getResponseBody()) {
            for (int index = 0; index < 50; index++) {
              out.write(' ');
              out.flush();
              Thread.sleep(100);
            }
            out.write("{}".getBytes(StandardCharsets.UTF_8));
          } catch (InterruptedException | IOException error) {
            // The client went away.
          }
        });
    server.createContext(
        "/v1/documents/silent",
        exchange -> {
          try {
            Thread.sleep(5_000);
          } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
          }
          respond(exchange, 200, "{}");
        });
    server.createContext(
        "/v1/documents/moved",
        exchange -> {
          exchange.getResponseHeaders().add("location", "http://example.com/stolen");
          respond(exchange, 302, "");
        });
    server.createContext(
        "/v1/documents/big",
        exchange -> respond(exchange, 200, "{\"id\":\"" + "x".repeat(2_000_000) + "\"}"));
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
    executor.shutdownNow();
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("content-type", "application/json");
    exchange.getResponseHeaders().add("x-request-id", "req_net");
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private Flashpi client() {
    return Flashpi.builder()
        .apiKey(Support.KEY)
        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
        .maxRetries(0)
        .build();
  }

  @Test
  void sendsTheRequestOnTheWire() {
    try (Flashpi flashpi = client()) {
      assertEquals("operational", flashpi.status().get().getStatus().value());
      flashpi
          .issuers()
          .create(
              CreateIssuer.builder().legalName("Compañía").build(),
              RequestOptions.builder().header("X-Name", "Ano").idempotencyKey("k-1").build());
      Map<String, List<String>> headers = received.get(0);
      assertEquals(List.of("Bearer " + Support.KEY), headers.get("Authorization"));
      assertEquals(List.of("k-1"), headers.get("Idempotency-key"));
      assertEquals("flashpi-java/" + Version.VERSION, headers.get("User-agent").get(0));
      assertEquals(List.of("Ano"), headers.get("X-name"));
      assertEquals(
          "{\"legalName\":\"Compañía\"}", new String(bodies.get("issuer"), StandardCharsets.UTF_8));
    }
  }

  @Test
  void aBodyThatTricklesPastTheDeadlineTimesOutAtIt() {
    long started = System.nanoTime();
    ApiTimeoutException error =
        assertThrows(
            ApiTimeoutException.class,
            () ->
                client()
                    .documents()
                    .get(
                        "trickle",
                        RequestOptions.builder().timeout(Duration.ofMillis(800)).build()));
    long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
    assertTrue(elapsed < 3_000, elapsed + " ms");
    assertTrue(error.getMessage().endsWith("timed out after 0.8 seconds"), error.getMessage());
  }

  @Test
  void anAnswerThatNeverComesTimesOut() {
    long started = System.nanoTime();
    assertThrows(
        ApiTimeoutException.class,
        () ->
            client()
                .documents()
                .get("silent", RequestOptions.builder().timeout(Duration.ofMillis(300)).build()));
    assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 3_000);
  }

  @Test
  void neverFollowsARedirect() {
    ApiException error = assertThrows(ApiException.class, () -> client().documents().get("moved"));
    assertEquals(302, error.getStatus());
    assertEquals(
        "http://example.com/stolen", error.getHeaders().firstValue("location").orElse(null));
  }

  @Test
  void readsALargeAnswerWhole() {
    assertEquals(2_000_000, client().documents().get("big").getId().length());
  }

  @Test
  void aRefusedConnectionIsAConnectionError() throws IOException {
    int port;
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = socket.getLocalPort();
    }
    Flashpi flashpi =
        Flashpi.builder()
            .apiKey(Support.KEY)
            .baseUrl("http://127.0.0.1:" + port)
            .maxRetries(0)
            .build();
    ApiConnectionException error =
        assertThrows(ApiConnectionException.class, () -> flashpi.status().get());
    assertTrue(
        error.getMessage().startsWith("GET http://127.0.0.1:" + port + "/v1/status failed: "),
        error.getMessage());
  }
}
