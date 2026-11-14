package ec.com.flashpi.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Reads a whole answer into bytes, and stops reading it once the attempt's deadline has passed or
 * the attempt is cancelled: cancelling the subscription closes the connection, which Java 11's
 * HttpClient does not do when the future of {@code sendAsync} is cancelled.
 */
final class DeadlineBodyHandler implements HttpResponse.BodyHandler<byte[]> {
  private final BooleanSupplier expired;
  private final AtomicReference<Subscriber> current = new AtomicReference<>();
  private volatile boolean cancelled;

  DeadlineBodyHandler(BooleanSupplier expired) {
    this.expired = expired;
  }

  @Override
  public HttpResponse.BodySubscriber<byte[]> apply(HttpResponse.ResponseInfo info) {
    Subscriber subscriber = new Subscriber();
    current.set(subscriber);
    if (cancelled) {
      subscriber.cancel();
    }
    return subscriber;
  }

  /** Stops reading the answer, now or as soon as it starts. */
  void cancel() {
    cancelled = true;
    Subscriber subscriber = current.get();
    if (subscriber != null) {
      subscriber.cancel();
    }
  }

  /** The attempt's deadline passed while its answer was being read. */
  static final class DeadlineExceeded extends IOException {
    private static final long serialVersionUID = 1L;

    DeadlineExceeded() {
      super("The attempt's deadline passed while reading the answer");
    }
  }

  private final class Subscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

    @Override
    public CompletionStage<byte[]> getBody() {
      return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      if (!this.subscription.compareAndSet(null, subscription)) {
        subscription.cancel();
        return;
      }
      if (cancelled || body.isDone()) {
        cancel();
        return;
      }
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      if (body.isDone()) {
        return;
      }
      if (cancelled || expired.getAsBoolean()) {
        cancel();
        return;
      }
      synchronized (bytes) {
        for (ByteBuffer buffer : buffers) {
          byte[] chunk = new byte[buffer.remaining()];
          buffer.get(chunk);
          bytes.write(chunk, 0, chunk.length);
        }
      }
    }

    @Override
    public void onError(Throwable error) {
      body.completeExceptionally(error);
    }

    @Override
    public void onComplete() {
      synchronized (bytes) {
        body.complete(bytes.toByteArray());
      }
    }

    void cancel() {
      body.completeExceptionally(new DeadlineExceeded());
      Flow.Subscription active = subscription.get();
      if (active != null) {
        active.cancel();
      }
    }
  }
}
