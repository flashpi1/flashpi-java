package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Readers;
import ec.com.flashpi.internal.RequestSpec;
import ec.com.flashpi.models.Status;

/** The API's own status. It needs no API key. */
public final class StatusResource extends Resource {
  private final WithResponse withResponse = new WithResponse(this);

  /**
   * Created by the client: use {@code flashpi.status()}.
   *
   * @param engine the client's engine
   */
  public StatusResource(Engine engine) {
    super(engine);
  }

  /**
   * Whether Flashpi is operational.
   *
   * @return the status
   */
  public Status get() {
    return get(null);
  }

  /**
   * Whether Flashpi is operational.
   *
   * @param options the call's options, or null
   * @return the status
   */
  public Status get(RequestOptions options) {
    return request(
        RequestSpec.of("GET", "/v1/status", Readers.json(Status.class)).options(options).build());
  }

  /**
   * The same methods, returning the HTTP response and request id with the data.
   *
   * @return the view
   */
  public WithResponse withResponse() {
    return withResponse;
  }

  /** The methods of {@link StatusResource}, returning an {@link ApiResponse}. */
  public static final class WithResponse {
    private final StatusResource statusResource;

    WithResponse(StatusResource statusResource) {
      this.statusResource = statusResource;
    }

    /**
     * See {@link StatusResource#get()}.
     *
     * @return the status and the response
     */
    public ApiResponse<Status> get() {
      return Capture.run(() -> statusResource.get());
    }

    /**
     * See {@link StatusResource#get(RequestOptions)}.
     *
     * @param options the call's options, or null
     * @return the status and the response
     */
    public ApiResponse<Status> get(RequestOptions options) {
      return Capture.run(() -> statusResource.get(options));
    }
  }
}
