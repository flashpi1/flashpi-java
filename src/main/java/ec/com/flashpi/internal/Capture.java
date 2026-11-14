package ec.com.flashpi.internal;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.exceptions.FlashpiException;
import java.util.function.Supplier;

/**
 * The response behind a call's data, for the {@code withResponse()} views: the resources record the
 * last response of a call in a thread-local while a view runs the call, so the methods themselves
 * stay unchanged.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public final class Capture {
  private static final ThreadLocal<Capture> CURRENT = new ThreadLocal<>();

  private ApiResponse<?> result;

  private Capture() {}

  /**
   * Keeps a call's response for the view running it on this thread, if any.
   *
   * @param result the response
   */
  public static void record(ApiResponse<?> result) {
    Capture capture = CURRENT.get();
    if (capture != null) {
      capture.result = result;
    }
  }

  /**
   * Runs a call and returns its data with the response it came from.
   *
   * @param call the call
   * @param <T> its data
   * @return the data and the response
   * @throws FlashpiException when the call sent no request
   */
  public static <T> ApiResponse<T> run(Supplier<T> call) {
    Capture previous = CURRENT.get();
    Capture capture = new Capture();
    CURRENT.set(capture);
    T data;
    try {
      data = call.get();
    } finally {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
    ApiResponse<?> result = capture.result;
    if (result == null) {
      throw new FlashpiException("The call sent no request, so there is no response to return");
    }
    return new ApiResponse<>(
        data, result.getStatusCode(), result.getHeaders(), result.getRequestId());
  }
}
