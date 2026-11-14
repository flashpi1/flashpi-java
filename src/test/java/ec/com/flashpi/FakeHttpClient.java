package ec.com.flashpi;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * An HttpClient that answers from a script, without a network: it records every request (method,
 * URL, headers and body) and drives the SDK's body handler with the scripted answer, as the JDK's
 * client does.
 */
final class FakeHttpClient extends HttpClient {
  /** A request as the fake received it. */
  static final class Recorded {
    final String method;
    final URI uri;
    final HttpHeaders headers;
    final byte[] body;
    final Duration timeout;

    Recorded(String method, URI uri, HttpHeaders headers, byte[] body, Duration timeout) {
      this.method = method;
      this.uri = uri;
      this.headers = headers;
      this.body = body;
      this.timeout = timeout;
    }

    String header(String name) {
      return headers.firstValue(name).orElse(null);
    }

    String bodyText() {
      return new String(body, StandardCharsets.UTF_8);
    }

    String path() {
      return uri.getRawPath();
    }

    String query() {
      return uri.getRawQuery();
    }
  }

  /** What the fake does with one request. */
  static final class Answer {
    final int status;
    final Map<String, List<String>> headers = new LinkedHashMap<>();
    final byte[] body;
    final IOException failure;
    final boolean hang;

    private Answer(int status, byte[] body, IOException failure, boolean hang) {
      this.status = status;
      this.body = body;
      this.failure = failure;
      this.hang = hang;
    }

    static Answer json(int status, String body) {
      return new Answer(status, body.getBytes(StandardCharsets.UTF_8), null, false)
          .header("content-type", status >= 400 ? "application/problem+json" : "application/json");
    }

    static Answer status(int status) {
      return new Answer(status, new byte[0], null, false);
    }

    static Answer bytes(int status, byte[] body, String contentType) {
      return new Answer(status, body, null, false).header("content-type", contentType);
    }

    static Answer problem(int status, String code) {
      return json(
          status,
          "{\"type\":\"https://flashpi.com.ec/docs/errors/"
              + code
              + "\",\"title\":\"Title\",\"status\":"
              + status
              + ",\"detail\":\"Detail of "
              + code
              + "\",\"instance\":\"/v1/x\",\"code\":\""
              + code
              + "\",\"errors\":[],\"requestId\":\"req_problem\"}");
    }

    static Answer failure(IOException failure) {
      return new Answer(0, null, failure, false);
    }

    /** Never answers: the attempt can only end by its deadline. */
    static Answer hang() {
      return new Answer(0, null, null, true);
    }

    Answer header(String name, String value) {
      headers.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
      return this;
    }
  }

  final List<Recorded> requests = new ArrayList<>();
  private final Deque<Function<Recorded, Answer>> script = new ArrayDeque<>();
  private Function<Recorded, Answer> fallback;
  private Redirect redirect = Redirect.NEVER;

  /** Answers the next request. */
  FakeHttpClient then(Answer answer) {
    script.add(request -> answer);
    return this;
  }

  /** Answers the next request from the request itself. */
  FakeHttpClient then(Function<Recorded, Answer> answer) {
    script.add(answer);
    return this;
  }

  /** Answers every request once the script is used up. */
  FakeHttpClient always(Answer answer) {
    fallback = request -> answer;
    return this;
  }

  FakeHttpClient redirects(Redirect redirect) {
    this.redirect = redirect;
    return this;
  }

  Recorded last() {
    return requests.get(requests.size() - 1);
  }

  @Override
  public Optional<CookieHandler> cookieHandler() {
    return Optional.empty();
  }

  @Override
  public Optional<Duration> connectTimeout() {
    return Optional.empty();
  }

  @Override
  public Redirect followRedirects() {
    return redirect;
  }

  @Override
  public Optional<ProxySelector> proxy() {
    return Optional.empty();
  }

  @Override
  public SSLContext sslContext() {
    return null;
  }

  @Override
  public SSLParameters sslParameters() {
    return null;
  }

  @Override
  public Optional<Authenticator> authenticator() {
    return Optional.empty();
  }

  @Override
  public Version version() {
    return Version.HTTP_1_1;
  }

  @Override
  public Optional<Executor> executor() {
    return Optional.empty();
  }

  @Override
  public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    throw new UnsupportedOperationException("The SDK sends with sendAsync");
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(
      HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    Recorded recorded =
        new Recorded(
            request.method(),
            request.uri(),
            request.headers(),
            body(request),
            request.timeout().orElse(null));
    synchronized (requests) {
      requests.add(recorded);
    }
    Function<Recorded, Answer> next = script.isEmpty() ? fallback : script.poll();
    if (next == null) {
      throw new AssertionError("Unexpected request: " + recorded.method + " " + recorded.uri);
    }
    Answer answer = next.apply(recorded);
    if (answer.failure != null) {
      return CompletableFuture.failedFuture(answer.failure);
    }
    if (answer.hang) {
      return new CompletableFuture<>();
    }
    HttpHeaders headers = HttpHeaders.of(answer.headers, (name, value) -> true);
    HttpResponse.ResponseInfo info =
        new HttpResponse.ResponseInfo() {
          @Override
          public int statusCode() {
            return answer.status;
          }

          @Override
          public HttpHeaders headers() {
            return headers;
          }

          @Override
          public Version version() {
            return Version.HTTP_1_1;
          }
        };
    HttpResponse.BodySubscriber<T> subscriber = handler.apply(info);
    subscriber.onSubscribe(
        new Flow.Subscription() {
          @Override
          public void request(long n) {}

          @Override
          public void cancel() {}
        });
    if (answer.body.length > 0) {
      subscriber.onNext(List.of(ByteBuffer.wrap(answer.body)));
    }
    subscriber.onComplete();
    CompletionStage<T> body = subscriber.getBody();
    return body.toCompletableFuture()
        .thenApply(data -> new Response<>(request, answer.status, headers, data));
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(
      HttpRequest request,
      HttpResponse.BodyHandler<T> handler,
      HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
    return sendAsync(request, handler);
  }

  /** The bytes a request's body publisher sends. */
  static byte[] body(HttpRequest request) {
    if (request.bodyPublisher().isEmpty()) {
      return new byte[0];
    }
    List<ByteBuffer> buffers = new ArrayList<>();
    CountDownLatch done = new CountDownLatch(1);
    request
        .bodyPublisher()
        .get()
        .subscribe(
            new Flow.Subscriber<ByteBuffer>() {
              @Override
              public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
              }

              @Override
              public void onNext(ByteBuffer item) {
                buffers.add(item);
              }

              @Override
              public void onError(Throwable throwable) {
                done.countDown();
              }

              @Override
              public void onComplete() {
                done.countDown();
              }
            });
    try {
      if (!done.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("The request body never ended");
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new AssertionError(error);
    }
    int size = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
    ByteBuffer all = ByteBuffer.allocate(size);
    buffers.forEach(all::put);
    return all.array();
  }

  private static final class Response<T> implements HttpResponse<T> {
    private final HttpRequest request;
    private final int status;
    private final HttpHeaders headers;
    private final T body;

    Response(HttpRequest request, int status, HttpHeaders headers, T body) {
      this.request = request;
      this.status = status;
      this.headers = headers;
      this.body = body;
    }

    @Override
    public int statusCode() {
      return status;
    }

    @Override
    public HttpRequest request() {
      return request;
    }

    @Override
    public Optional<HttpResponse<T>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return headers;
    }

    @Override
    public T body() {
      return body;
    }

    @Override
    public Optional<javax.net.ssl.SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return request.uri();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }
  }
}
