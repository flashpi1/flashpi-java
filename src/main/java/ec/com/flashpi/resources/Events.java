package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.Page;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Paths;
import ec.com.flashpi.internal.Readers;
import ec.com.flashpi.internal.RequestSpec;
import ec.com.flashpi.models.Event;
import ec.com.flashpi.models.EventsListParams;

/** Events: what happened to documents, certificates and email sending, newest first. */
public final class Events extends Resource {
  private final WithResponse withResponse = new WithResponse(this);

  /**
   * Created by the client: use {@code flashpi.events()}.
   *
   * @param engine the client's engine
   */
  public Events(Engine engine) {
    super(engine);
  }

  /**
   * Lists events, optionally by type, issuer, document or certificate.
   *
   * @return the first page; iterating it walks every page
   */
  public Page<Event> list() {
    return list(null, null);
  }

  /**
   * Lists events, optionally by type, issuer, document or certificate.
   *
   * @param params the filters, or null
   * @return the first page; iterating it walks every page
   */
  public Page<Event> list(EventsListParams params) {
    return list(params, null);
  }

  /**
   * Lists events, optionally by type, issuer, document or certificate.
   *
   * @param params the filters, or null
   * @param options the call's options, or null
   * @return the first page; iterating it walks every page
   */
  public Page<Event> list(EventsListParams params, RequestOptions options) {
    return list(
        RequestSpec.of("GET", "/v1/events", Readers.list(Event.class))
            .query(params)
            .options(options)
            .build());
  }

  /**
   * Retrieves an event: the same object its webhook delivered.
   *
   * @param eventId the event's id
   * @return the event: check its class with instanceof
   */
  public Event get(String eventId) {
    return get(eventId, null);
  }

  /**
   * Retrieves an event: the same object its webhook delivered.
   *
   * @param eventId the event's id
   * @param options the call's options, or null
   * @return the event: check its class with instanceof
   */
  public Event get(String eventId, RequestOptions options) {
    return request(
        RequestSpec.of("GET", Paths.of("/v1/events/{}", eventId), Readers.json(Event.class))
            .options(options)
            .build());
  }

  /**
   * The same methods, returning the HTTP response and request id with the data.
   *
   * @return the view
   */
  public WithResponse withResponse() {
    return withResponse;
  }

  /** The methods of {@link Events}, returning an {@link ApiResponse}. */
  public static final class WithResponse {
    private final Events events;

    WithResponse(Events events) {
      this.events = events;
    }

    /**
     * See {@link Events#list()}.
     *
     * @return the first page and the response
     */
    public ApiResponse<Page<Event>> list() {
      return Capture.run(() -> events.list());
    }

    /**
     * See {@link Events#list(EventsListParams)}.
     *
     * @param params the filters, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<Event>> list(EventsListParams params) {
      return Capture.run(() -> events.list(params));
    }

    /**
     * See {@link Events#list(EventsListParams, RequestOptions)}.
     *
     * @param params the filters, or null
     * @param options the call's options, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<Event>> list(EventsListParams params, RequestOptions options) {
      return Capture.run(() -> events.list(params, options));
    }

    /**
     * See {@link Events#get(String)}.
     *
     * @param eventId the event's id
     * @return the event: check its class with instanceof and the response
     */
    public ApiResponse<Event> get(String eventId) {
      return Capture.run(() -> events.get(eventId));
    }

    /**
     * See {@link Events#get(String, RequestOptions)}.
     *
     * @param eventId the event's id
     * @param options the call's options, or null
     * @return the event: check its class with instanceof and the response
     */
    public ApiResponse<Event> get(String eventId, RequestOptions options) {
      return Capture.run(() -> events.get(eventId, options));
    }
  }
}
