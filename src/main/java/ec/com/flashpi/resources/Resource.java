package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.Page;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.ListBody;
import ec.com.flashpi.internal.RequestSpec;
import java.util.Objects;

/** Base of every resource: resources only declare their operations as request specs. */
public abstract class Resource {
  final Engine engine;

  Resource(Engine engine) {
    this.engine = Objects.requireNonNull(engine, "engine");
  }

  /** Sends the request (with its retries) and returns its data. */
  final <T> T request(RequestSpec<T> spec) {
    ApiResponse<T> result = engine.request(spec);
    Capture.record(result);
    return result.getData();
  }

  /** The first page of a list; iterating it walks every page. */
  final <T> Page<T> list(RequestSpec<ListBody<T>> spec) {
    ApiResponse<Page<T>> first = fetch(spec, spec);
    Capture.record(first);
    return first.getData();
  }

  private <T> ApiResponse<Page<T>> fetch(
      RequestSpec<ListBody<T>> first, RequestSpec<ListBody<T>> spec) {
    ApiResponse<ListBody<T>> result = engine.request(spec);
    ListBody<T> body = result.getData();
    Page<T> page =
        new Page<>(
            body.data(),
            body.hasMore(),
            body.nextCursor(),
            cursor -> fetch(first, first.withCursor(cursor)).getData());
    return new ApiResponse<>(
        page, result.getStatusCode(), result.getHeaders(), result.getRequestId());
  }
}
