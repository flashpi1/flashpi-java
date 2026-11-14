package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.Page;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Paths;
import ec.com.flashpi.internal.Readers;
import ec.com.flashpi.internal.RequestSpec;
import ec.com.flashpi.models.WebhookDelivery;
import ec.com.flashpi.models.WebhookDeliveryDetail;
import ec.com.flashpi.models.WebhookEndpointsDeliveriesListParams;

/** Deliveries of events to one endpoint. */
public final class WebhookDeliveries extends Resource {
  private final WithResponse withResponse = new WithResponse(this);

  /**
   * Created by the client: use {@code flashpi.webhookEndpoints().deliveries()}.
   *
   * @param engine the client's engine
   */
  public WebhookDeliveries(Engine engine) {
    super(engine);
  }

  /**
   * Lists deliveries, newest first, optionally by status, event type or event.
   *
   * @param endpointId the endpoint's id
   * @return the first page; iterating it walks every page
   */
  public Page<WebhookDelivery> list(String endpointId) {
    return list(endpointId, null, null);
  }

  /**
   * Lists deliveries, newest first, optionally by status, event type or event.
   *
   * @param endpointId the endpoint's id
   * @param params the filters, or null
   * @return the first page; iterating it walks every page
   */
  public Page<WebhookDelivery> list(
      String endpointId, WebhookEndpointsDeliveriesListParams params) {
    return list(endpointId, params, null);
  }

  /**
   * Lists deliveries, newest first, optionally by status, event type or event.
   *
   * @param endpointId the endpoint's id
   * @param params the filters, or null
   * @param options the call's options, or null
   * @return the first page; iterating it walks every page
   */
  public Page<WebhookDelivery> list(
      String endpointId, WebhookEndpointsDeliveriesListParams params, RequestOptions options) {
    return list(
        RequestSpec.of(
                "GET",
                Paths.of("/v1/webhook-endpoints/{}/deliveries", endpointId),
                Readers.list(WebhookDelivery.class))
            .query(params)
            .options(options)
            .build());
  }

  /**
   * Retrieves a delivery with its attempts.
   *
   * @param endpointId the endpoint's id
   * @param deliveryId the delivery's id
   * @return the delivery
   */
  public WebhookDeliveryDetail get(String endpointId, String deliveryId) {
    return get(endpointId, deliveryId, null);
  }

  /**
   * Retrieves a delivery with its attempts.
   *
   * @param endpointId the endpoint's id
   * @param deliveryId the delivery's id
   * @param options the call's options, or null
   * @return the delivery
   */
  public WebhookDeliveryDetail get(String endpointId, String deliveryId, RequestOptions options) {
    return request(
        RequestSpec.of(
                "GET",
                Paths.of("/v1/webhook-endpoints/{}/deliveries/{}", endpointId, deliveryId),
                Readers.json(WebhookDeliveryDetail.class))
            .options(options)
            .build());
  }

  /**
   * Sends the delivery's event again, with the same {@code webhook-id}.
   *
   * @param endpointId the endpoint's id
   * @param deliveryId the delivery's id
   * @return the delivery
   */
  public WebhookDeliveryDetail replay(String endpointId, String deliveryId) {
    return replay(endpointId, deliveryId, null);
  }

  /**
   * Sends the delivery's event again, with the same {@code webhook-id}.
   *
   * @param endpointId the endpoint's id
   * @param deliveryId the delivery's id
   * @param options the call's options, or null
   * @return the delivery
   */
  public WebhookDeliveryDetail replay(
      String endpointId, String deliveryId, RequestOptions options) {
    return request(
        RequestSpec.of(
                "POST",
                Paths.of("/v1/webhook-endpoints/{}/deliveries/{}/replay", endpointId, deliveryId),
                Readers.json(WebhookDeliveryDetail.class))
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

  /** The methods of {@link WebhookDeliveries}, returning an {@link ApiResponse}. */
  public static final class WithResponse {
    private final WebhookDeliveries webhookDeliveries;

    WithResponse(WebhookDeliveries webhookDeliveries) {
      this.webhookDeliveries = webhookDeliveries;
    }

    /**
     * See {@link WebhookDeliveries#list(String)}.
     *
     * @param endpointId the endpoint's id
     * @return the first page and the response
     */
    public ApiResponse<Page<WebhookDelivery>> list(String endpointId) {
      return Capture.run(() -> webhookDeliveries.list(endpointId));
    }

    /**
     * See {@link WebhookDeliveries#list(String, WebhookEndpointsDeliveriesListParams)}.
     *
     * @param endpointId the endpoint's id
     * @param params the filters, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<WebhookDelivery>> list(
        String endpointId, WebhookEndpointsDeliveriesListParams params) {
      return Capture.run(() -> webhookDeliveries.list(endpointId, params));
    }

    /**
     * See {@link WebhookDeliveries#list(String, WebhookEndpointsDeliveriesListParams,
     * RequestOptions)}.
     *
     * @param endpointId the endpoint's id
     * @param params the filters, or null
     * @param options the call's options, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<WebhookDelivery>> list(
        String endpointId, WebhookEndpointsDeliveriesListParams params, RequestOptions options) {
      return Capture.run(() -> webhookDeliveries.list(endpointId, params, options));
    }

    /**
     * See {@link WebhookDeliveries#get(String, String)}.
     *
     * @param endpointId the endpoint's id
     * @param deliveryId the delivery's id
     * @return the delivery and the response
     */
    public ApiResponse<WebhookDeliveryDetail> get(String endpointId, String deliveryId) {
      return Capture.run(() -> webhookDeliveries.get(endpointId, deliveryId));
    }

    /**
     * See {@link WebhookDeliveries#get(String, String, RequestOptions)}.
     *
     * @param endpointId the endpoint's id
     * @param deliveryId the delivery's id
     * @param options the call's options, or null
     * @return the delivery and the response
     */
    public ApiResponse<WebhookDeliveryDetail> get(
        String endpointId, String deliveryId, RequestOptions options) {
      return Capture.run(() -> webhookDeliveries.get(endpointId, deliveryId, options));
    }

    /**
     * See {@link WebhookDeliveries#replay(String, String)}.
     *
     * @param endpointId the endpoint's id
     * @param deliveryId the delivery's id
     * @return the delivery and the response
     */
    public ApiResponse<WebhookDeliveryDetail> replay(String endpointId, String deliveryId) {
      return Capture.run(() -> webhookDeliveries.replay(endpointId, deliveryId));
    }

    /**
     * See {@link WebhookDeliveries#replay(String, String, RequestOptions)}.
     *
     * @param endpointId the endpoint's id
     * @param deliveryId the delivery's id
     * @param options the call's options, or null
     * @return the delivery and the response
     */
    public ApiResponse<WebhookDeliveryDetail> replay(
        String endpointId, String deliveryId, RequestOptions options) {
      return Capture.run(() -> webhookDeliveries.replay(endpointId, deliveryId, options));
    }
  }
}
