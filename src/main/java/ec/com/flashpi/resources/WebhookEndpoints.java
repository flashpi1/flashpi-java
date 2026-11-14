package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.Page;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Paths;
import ec.com.flashpi.internal.Readers;
import ec.com.flashpi.internal.RequestSpec;
import ec.com.flashpi.models.CreateWebhookEndpoint;
import ec.com.flashpi.models.RotateWebhookEndpointSecret;
import ec.com.flashpi.models.UpdateWebhookEndpoint;
import ec.com.flashpi.models.WebhookEndpoint;
import ec.com.flashpi.models.WebhookEndpointSecret;
import ec.com.flashpi.models.WebhookEndpointsListParams;
import java.util.Objects;

/**
 * The URLs Flashpi sends events to, and the deliveries of those events. Needs an account-scoped API
 * key with read_write access. To check the signature of a webhook you receive, use {@code
 * flashpi.webhooks()} instead.
 */
public final class WebhookEndpoints extends Resource {
  private final WebhookDeliveries deliveries;
  private final WithResponse withResponse = new WithResponse(this);

  /**
   * Created by the client: use {@code flashpi.webhookEndpoints()}.
   *
   * @param engine the client's engine
   */
  public WebhookEndpoints(Engine engine) {
    super(engine);
    this.deliveries = new WebhookDeliveries(engine);
  }

  /**
   * The deliveries of an endpoint: attempts, answers and manual replays.
   *
   * @return the resource
   */
  public WebhookDeliveries deliveries() {
    return deliveries;
  }

  /**
   * Creates an endpoint. It stays {@code pending_verification} until it answers the activation
   * challenge; read its secret with {@code getSecret()} first, then call {@code verify()} if
   * needed.
   *
   * @param body the body
   * @return the endpoint
   */
  public WebhookEndpoint create(CreateWebhookEndpoint body) {
    return create(body, null);
  }

  /**
   * Creates an endpoint. It stays {@code pending_verification} until it answers the activation
   * challenge; read its secret with {@code getSecret()} first, then call {@code verify()} if
   * needed.
   *
   * @param body the body
   * @param options the call's options, or null
   * @return the endpoint
   */
  public WebhookEndpoint create(CreateWebhookEndpoint body, RequestOptions options) {
    return request(
        RequestSpec.of("POST", "/v1/webhook-endpoints", Readers.json(WebhookEndpoint.class))
            .body(Objects.requireNonNull(body, "body"))
            .options(options)
            .build());
  }

  /**
   * Lists the endpoints, newest first.
   *
   * @return the first page; iterating it walks every page
   */
  public Page<WebhookEndpoint> list() {
    return list(null, null);
  }

  /**
   * Lists the endpoints, newest first.
   *
   * @param params the filters, or null
   * @return the first page; iterating it walks every page
   */
  public Page<WebhookEndpoint> list(WebhookEndpointsListParams params) {
    return list(params, null);
  }

  /**
   * Lists the endpoints, newest first.
   *
   * @param params the filters, or null
   * @param options the call's options, or null
   * @return the first page; iterating it walks every page
   */
  public Page<WebhookEndpoint> list(WebhookEndpointsListParams params, RequestOptions options) {
    return list(
        RequestSpec.of("GET", "/v1/webhook-endpoints", Readers.list(WebhookEndpoint.class))
            .query(params)
            .options(options)
            .build());
  }

  /**
   * Retrieves an endpoint.
   *
   * @param endpointId the endpoint's id
   * @return the endpoint
   */
  public WebhookEndpoint get(String endpointId) {
    return get(endpointId, null);
  }

  /**
   * Retrieves an endpoint.
   *
   * @param endpointId the endpoint's id
   * @param options the call's options, or null
   * @return the endpoint
   */
  public WebhookEndpoint get(String endpointId, RequestOptions options) {
    return request(
        RequestSpec.of(
                "GET",
                Paths.of("/v1/webhook-endpoints/{}", endpointId),
                Readers.json(WebhookEndpoint.class))
            .options(options)
            .build());
  }

  /**
   * Updates the URL, events, payload mode, description or {@code disabled} (only the members set).
   *
   * @param endpointId the endpoint's id
   * @param body the body
   * @return the endpoint
   */
  public WebhookEndpoint update(String endpointId, UpdateWebhookEndpoint body) {
    return update(endpointId, body, null);
  }

  /**
   * Updates the URL, events, payload mode, description or {@code disabled} (only the members set).
   *
   * @param endpointId the endpoint's id
   * @param body the body
   * @param options the call's options, or null
   * @return the endpoint
   */
  public WebhookEndpoint update(
      String endpointId, UpdateWebhookEndpoint body, RequestOptions options) {
    return request(
        RequestSpec.of(
                "PATCH",
                Paths.of("/v1/webhook-endpoints/{}", endpointId),
                Readers.json(WebhookEndpoint.class))
            .body(Objects.requireNonNull(body, "body"))
            .options(options)
            .build());
  }

  /**
   * Deletes an endpoint.
   *
   * @param endpointId the endpoint's id
   */
  public void delete(String endpointId) {
    delete(endpointId, null);
  }

  /**
   * Deletes an endpoint.
   *
   * @param endpointId the endpoint's id
   * @param options the call's options, or null
   */
  public void delete(String endpointId, RequestOptions options) {
    request(
        RequestSpec.of("DELETE", Paths.of("/v1/webhook-endpoints/{}", endpointId), Readers.none())
            .options(options)
            .build());
  }

  /**
   * The signing secret ({@code whsec_...}) and, during a rotation, when the previous one expires.
   *
   * @param endpointId the endpoint's id
   * @return the secret
   */
  public WebhookEndpointSecret getSecret(String endpointId) {
    return getSecret(endpointId, null);
  }

  /**
   * The signing secret ({@code whsec_...}) and, during a rotation, when the previous one expires.
   *
   * @param endpointId the endpoint's id
   * @param options the call's options, or null
   * @return the secret
   */
  public WebhookEndpointSecret getSecret(String endpointId, RequestOptions options) {
    return request(
        RequestSpec.of(
                "GET",
                Paths.of("/v1/webhook-endpoints/{}/secret", endpointId),
                Readers.json(WebhookEndpointSecret.class))
            .options(options)
            .build());
  }

  /**
   * Replaces the signing secret. The previous one keeps signing for {@code
   * previousSecretExpiresInHours} (24 by default); read the new secret with {@code getSecret()}.
   *
   * @param endpointId the endpoint's id
   * @return the endpoint
   */
  public WebhookEndpoint rotateSecret(String endpointId) {
    return rotateSecret(endpointId, null, null);
  }

  /**
   * Replaces the signing secret. The previous one keeps signing for {@code
   * previousSecretExpiresInHours} (24 by default); read the new secret with {@code getSecret()}.
   *
   * @param endpointId the endpoint's id
   * @param body the body, or null
   * @return the endpoint
   */
  public WebhookEndpoint rotateSecret(String endpointId, RotateWebhookEndpointSecret body) {
    return rotateSecret(endpointId, body, null);
  }

  /**
   * Replaces the signing secret. The previous one keeps signing for {@code
   * previousSecretExpiresInHours} (24 by default); read the new secret with {@code getSecret()}.
   *
   * @param endpointId the endpoint's id
   * @param body the body, or null
   * @param options the call's options, or null
   * @return the endpoint
   */
  public WebhookEndpoint rotateSecret(
      String endpointId, RotateWebhookEndpointSecret body, RequestOptions options) {
    return request(
        RequestSpec.of(
                "POST",
                Paths.of("/v1/webhook-endpoints/{}/rotate-secret", endpointId),
                Readers.json(WebhookEndpoint.class))
            .body(body)
            .options(options)
            .build());
  }

  /**
   * Sends the activation challenge ({@code webhook_endpoint.verification}) to the endpoint again.
   *
   * @param endpointId the endpoint's id
   * @return the endpoint
   */
  public WebhookEndpoint verify(String endpointId) {
    return verify(endpointId, null);
  }

  /**
   * Sends the activation challenge ({@code webhook_endpoint.verification}) to the endpoint again.
   *
   * @param endpointId the endpoint's id
   * @param options the call's options, or null
   * @return the endpoint
   */
  public WebhookEndpoint verify(String endpointId, RequestOptions options) {
    return request(
        RequestSpec.of(
                "POST",
                Paths.of("/v1/webhook-endpoints/{}/verify", endpointId),
                Readers.json(WebhookEndpoint.class))
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

  /** The methods of {@link WebhookEndpoints}, returning an {@link ApiResponse}. */
  public static final class WithResponse {
    private final WebhookEndpoints webhookEndpoints;

    WithResponse(WebhookEndpoints webhookEndpoints) {
      this.webhookEndpoints = webhookEndpoints;
    }

    /**
     * See {@link WebhookEndpoints#create(CreateWebhookEndpoint)}.
     *
     * @param body the body
     * @return the endpoint and the response
     */
    public ApiResponse<WebhookEndpoint> create(CreateWebhookEndpoint body) {
      return Capture.run(() -> webhookEndpoints.create(body));
    }

    /**
     * See {@link WebhookEndpoints#create(CreateWebhookEndpoint, RequestOptions)}.
     *
     * @param body the body
     * @param options the call's options, or null
     * @return the endpoint and the response
     */
    public ApiResponse<WebhookEndpoint> create(CreateWebhookEndpoint body, RequestOptions options) {
      return Capture.run(() -> webhookEndpoints.create(body, options));
    }

    /**
     * See {@link WebhookEndpoints#list()}.
     *
     * @return the first page and the response
     */
    public ApiResponse<Page<WebhookEndpoint>> list() {
      return Capture.run(() -> webhookEndpoints.list());
    }

    /**
     * See {@link WebhookEndpoints#list(WebhookEndpointsListParams)}.
     *
     * @param params the filters, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<WebhookEndpoint>> list(WebhookEndpointsListParams params) {
      return Capture.run(() -> webhookEndpoints.list(params));
    }

    /**
     * See {@link WebhookEndpoints#list(WebhookEndpointsListParams, RequestOptions)}.
     *
     * @param params the filters, or null
     * @param options the call's options, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<WebhookEndpoint>> list(
        WebhookEndpointsListParams params, RequestOptions options) {
      return Capture.run(() -> webhookEndpoints.list(params, options));
    }

    /**
     * See {@link WebhookEndpoints#get(String)}.
     *
     * @param endpointId the endpoint's id
     * @return the endpoint and the response
     */
    public ApiResponse<WebhookEndpoint> get(String endpointId) {
      return Capture.run(() -> webhookEndpoints.get(endpointId));
    }

    /**
     * See {@link WebhookEndpoints#get(String, RequestOptions)}.
     *
     * @param endpointId the endpoint's id
     * @param options the call's options, or null
     * @return the endpoint and the response
     */
    public ApiResponse<WebhookEndpoint> get(String endpointId, RequestOptions options) {
      return Capture.run(() -> webhookEndpoints.get(endpointId, options));
    }

    /**
     * See {@link WebhookEndpoints#update(String, UpdateWebhookEndpoint)}.
     *
     * @param endpointId the endpoint's id
     * @param body the body
     * @return the endpoint and the response
     */
    public ApiResponse<WebhookEndpoint> update(String endpointId, UpdateWebhookEndpoint body) {
      return Capture.run(() -> webhookEndpoints.update(endpointId, body));
    }

    /**
     * See {@link WebhookEndpoints#update(String, UpdateWebhookEndpoint, RequestOptions)}.
     *
     * @param endpointId the endpoint's id
     * @param body the body
     * @param options the call's options, or null
     * @return the endpoint and the response
     */
    public ApiResponse<WebhookEndpoint> update(
        String endpointId, UpdateWebhookEndpoint body, RequestOptions options) {
      return Capture.run(() -> webhookEndpoints.update(endpointId, body, options));
    }

    /**
     * See {@link WebhookEndpoints#delete(String)}.
     *
     * @param endpointId the endpoint's id
     * @return the response
     */
    public ApiResponse<Void> delete(String endpointId) {
      return Capture.run(
          () -> {
            webhookEndpoints.delete(endpointId);
            return null;
          });
    }

    /**
     * See {@link WebhookEndpoints#delete(String, RequestOptions)}.
     *
     * @param endpointId the endpoint's id
     * @param options the call's options, or null
     * @return the response
     */
    public ApiResponse<Void> delete(String endpointId, RequestOptions options) {
      return Capture.run(
          () -> {
            webhookEndpoints.delete(endpointId, options);
            return null;
          });
    }

    /**
     * See {@link WebhookEndpoints#getSecret(String)}.
     *
     * @param endpointId the endpoint's id
     * @return the secret and the response
     */
    public ApiResponse<WebhookEndpointSecret> getSecret(String endpointId) {
      return Capture.run(() -> webhookEndpoints.getSecret(endpointId));
    }

    /**
     * See {@link WebhookEndpoints#getSecret(String, RequestOptions)}.
     *
     * @param endpointId the endpoint's id
     * @param options the call's options, or null
     * @return the secret and the response
     */
    public ApiResponse<WebhookEndpointSecret> getSecret(String endpointId, RequestOptions options) {
      return Capture.run(() -> webhookEndpoints.getSecret(endpointId, options));
    }

    /**
     * See {@link WebhookEndpoints#rotateSecret(String)}.
     *
     * @param endpointId the endpoint's id
     * @return the endpoint and the response
     */
    public ApiResponse<WebhookEndpoint> rotateSecret(String endpointId) {
      return Capture.run(() -> webhookEndpoints.rotateSecret(endpointId));
    }

    /**
     * See {@link WebhookEndpoints#rotateSecret(String, RotateWebhookEndpointSecret)}.
     *
     * @param endpointId the endpoint's id
     * @param body the body, or null
     * @return the endpoint and the response
     */
    public ApiResponse<WebhookEndpoint> rotateSecret(
        String endpointId, RotateWebhookEndpointSecret body) {
      return Capture.run(() -> webhookEndpoints.rotateSecret(endpointId, body));
    }

    /**
     * See {@link WebhookEndpoints#rotateSecret(String, RotateWebhookEndpointSecret,
     * RequestOptions)}.
     *
     * @param endpointId the endpoint's id
     * @param body the body, or null
     * @param options the call's options, or null
     * @return the endpoint and the response
     */
    public ApiResponse<WebhookEndpoint> rotateSecret(
        String endpointId, RotateWebhookEndpointSecret body, RequestOptions options) {
      return Capture.run(() -> webhookEndpoints.rotateSecret(endpointId, body, options));
    }

    /**
     * See {@link WebhookEndpoints#verify(String)}.
     *
     * @param endpointId the endpoint's id
     * @return the endpoint and the response
     */
    public ApiResponse<WebhookEndpoint> verify(String endpointId) {
      return Capture.run(() -> webhookEndpoints.verify(endpointId));
    }

    /**
     * See {@link WebhookEndpoints#verify(String, RequestOptions)}.
     *
     * @param endpointId the endpoint's id
     * @param options the call's options, or null
     * @return the endpoint and the response
     */
    public ApiResponse<WebhookEndpoint> verify(String endpointId, RequestOptions options) {
      return Capture.run(() -> webhookEndpoints.verify(endpointId, options));
    }
  }
}
