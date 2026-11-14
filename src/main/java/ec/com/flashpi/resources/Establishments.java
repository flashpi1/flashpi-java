package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.Page;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Paths;
import ec.com.flashpi.internal.Readers;
import ec.com.flashpi.internal.RequestSpec;
import ec.com.flashpi.models.CreateEstablishment;
import ec.com.flashpi.models.Establishment;
import ec.com.flashpi.models.EstablishmentsListParams;
import ec.com.flashpi.models.UpdateEstablishment;
import java.util.Objects;

/** Establishments (the 3-digit series codes such as {@code 001}) of an issuer. */
public final class Establishments extends Resource {
  private final EmissionPoints emissionPoints;
  private final WithResponse withResponse = new WithResponse(this);

  /**
   * Created by the client: use {@code flashpi.issuers().establishments()}.
   *
   * @param engine the client's engine
   */
  public Establishments(Engine engine) {
    super(engine);
    this.emissionPoints = new EmissionPoints(engine);
  }

  /**
   * Emission points of an establishment.
   *
   * @return the resource
   */
  public EmissionPoints emissionPoints() {
    return emissionPoints;
  }

  /**
   * Creates an establishment.
   *
   * @param issuerId the issuer's id
   * @param body the body
   * @return the establishment
   */
  public Establishment create(String issuerId, CreateEstablishment body) {
    return create(issuerId, body, null);
  }

  /**
   * Creates an establishment.
   *
   * @param issuerId the issuer's id
   * @param body the body
   * @param options the call's options, or null
   * @return the establishment
   */
  public Establishment create(String issuerId, CreateEstablishment body, RequestOptions options) {
    return request(
        RequestSpec.of(
                "POST",
                Paths.of("/v1/issuers/{}/establishments", issuerId),
                Readers.json(Establishment.class))
            .body(Objects.requireNonNull(body, "body"))
            .options(options)
            .build());
  }

  /**
   * Lists the establishments of an issuer, by code.
   *
   * @param issuerId the issuer's id
   * @return the first page; iterating it walks every page
   */
  public Page<Establishment> list(String issuerId) {
    return list(issuerId, null, null);
  }

  /**
   * Lists the establishments of an issuer, by code.
   *
   * @param issuerId the issuer's id
   * @param params the filters, or null
   * @return the first page; iterating it walks every page
   */
  public Page<Establishment> list(String issuerId, EstablishmentsListParams params) {
    return list(issuerId, params, null);
  }

  /**
   * Lists the establishments of an issuer, by code.
   *
   * @param issuerId the issuer's id
   * @param params the filters, or null
   * @param options the call's options, or null
   * @return the first page; iterating it walks every page
   */
  public Page<Establishment> list(
      String issuerId, EstablishmentsListParams params, RequestOptions options) {
    return list(
        RequestSpec.of(
                "GET",
                Paths.of("/v1/issuers/{}/establishments", issuerId),
                Readers.list(Establishment.class))
            .query(params)
            .options(options)
            .build());
  }

  /**
   * Retrieves an establishment.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @return the establishment
   */
  public Establishment get(String issuerId, String establishmentId) {
    return get(issuerId, establishmentId, null);
  }

  /**
   * Retrieves an establishment.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param options the call's options, or null
   * @return the establishment
   */
  public Establishment get(String issuerId, String establishmentId, RequestOptions options) {
    return request(
        RequestSpec.of(
                "GET",
                Paths.of("/v1/issuers/{}/establishments/{}", issuerId, establishmentId),
                Readers.json(Establishment.class))
            .options(options)
            .build());
  }

  /**
   * Updates an establishment (only the members set).
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param body the body
   * @return the establishment
   */
  public Establishment update(String issuerId, String establishmentId, UpdateEstablishment body) {
    return update(issuerId, establishmentId, body, null);
  }

  /**
   * Updates an establishment (only the members set).
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param body the body
   * @param options the call's options, or null
   * @return the establishment
   */
  public Establishment update(
      String issuerId, String establishmentId, UpdateEstablishment body, RequestOptions options) {
    return request(
        RequestSpec.of(
                "PATCH",
                Paths.of("/v1/issuers/{}/establishments/{}", issuerId, establishmentId),
                Readers.json(Establishment.class))
            .body(Objects.requireNonNull(body, "body"))
            .options(options)
            .build());
  }

  /**
   * Deletes an establishment.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   */
  public void delete(String issuerId, String establishmentId) {
    delete(issuerId, establishmentId, null);
  }

  /**
   * Deletes an establishment.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param options the call's options, or null
   */
  public void delete(String issuerId, String establishmentId, RequestOptions options) {
    request(
        RequestSpec.of(
                "DELETE",
                Paths.of("/v1/issuers/{}/establishments/{}", issuerId, establishmentId),
                Readers.none())
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

  /** The methods of {@link Establishments}, returning an {@link ApiResponse}. */
  public static final class WithResponse {
    private final Establishments establishments;

    WithResponse(Establishments establishments) {
      this.establishments = establishments;
    }

    /**
     * See {@link Establishments#create(String, CreateEstablishment)}.
     *
     * @param issuerId the issuer's id
     * @param body the body
     * @return the establishment and the response
     */
    public ApiResponse<Establishment> create(String issuerId, CreateEstablishment body) {
      return Capture.run(() -> establishments.create(issuerId, body));
    }

    /**
     * See {@link Establishments#create(String, CreateEstablishment, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param body the body
     * @param options the call's options, or null
     * @return the establishment and the response
     */
    public ApiResponse<Establishment> create(
        String issuerId, CreateEstablishment body, RequestOptions options) {
      return Capture.run(() -> establishments.create(issuerId, body, options));
    }

    /**
     * See {@link Establishments#list(String)}.
     *
     * @param issuerId the issuer's id
     * @return the first page and the response
     */
    public ApiResponse<Page<Establishment>> list(String issuerId) {
      return Capture.run(() -> establishments.list(issuerId));
    }

    /**
     * See {@link Establishments#list(String, EstablishmentsListParams)}.
     *
     * @param issuerId the issuer's id
     * @param params the filters, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<Establishment>> list(String issuerId, EstablishmentsListParams params) {
      return Capture.run(() -> establishments.list(issuerId, params));
    }

    /**
     * See {@link Establishments#list(String, EstablishmentsListParams, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param params the filters, or null
     * @param options the call's options, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<Establishment>> list(
        String issuerId, EstablishmentsListParams params, RequestOptions options) {
      return Capture.run(() -> establishments.list(issuerId, params, options));
    }

    /**
     * See {@link Establishments#get(String, String)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @return the establishment and the response
     */
    public ApiResponse<Establishment> get(String issuerId, String establishmentId) {
      return Capture.run(() -> establishments.get(issuerId, establishmentId));
    }

    /**
     * See {@link Establishments#get(String, String, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param options the call's options, or null
     * @return the establishment and the response
     */
    public ApiResponse<Establishment> get(
        String issuerId, String establishmentId, RequestOptions options) {
      return Capture.run(() -> establishments.get(issuerId, establishmentId, options));
    }

    /**
     * See {@link Establishments#update(String, String, UpdateEstablishment)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param body the body
     * @return the establishment and the response
     */
    public ApiResponse<Establishment> update(
        String issuerId, String establishmentId, UpdateEstablishment body) {
      return Capture.run(() -> establishments.update(issuerId, establishmentId, body));
    }

    /**
     * See {@link Establishments#update(String, String, UpdateEstablishment, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param body the body
     * @param options the call's options, or null
     * @return the establishment and the response
     */
    public ApiResponse<Establishment> update(
        String issuerId, String establishmentId, UpdateEstablishment body, RequestOptions options) {
      return Capture.run(() -> establishments.update(issuerId, establishmentId, body, options));
    }

    /**
     * See {@link Establishments#delete(String, String)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @return the response
     */
    public ApiResponse<Void> delete(String issuerId, String establishmentId) {
      return Capture.run(
          () -> {
            establishments.delete(issuerId, establishmentId);
            return null;
          });
    }

    /**
     * See {@link Establishments#delete(String, String, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param options the call's options, or null
     * @return the response
     */
    public ApiResponse<Void> delete(
        String issuerId, String establishmentId, RequestOptions options) {
      return Capture.run(
          () -> {
            establishments.delete(issuerId, establishmentId, options);
            return null;
          });
    }
  }
}
