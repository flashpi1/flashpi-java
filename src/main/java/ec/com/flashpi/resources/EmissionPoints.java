package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.Page;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Paths;
import ec.com.flashpi.internal.Readers;
import ec.com.flashpi.internal.RequestSpec;
import ec.com.flashpi.models.CreateEmissionPoint;
import ec.com.flashpi.models.EmissionPoint;
import ec.com.flashpi.models.EmissionPointsListParams;
import java.util.Objects;

/** Emission points (the second 3-digit series code) of an establishment. */
public final class EmissionPoints extends Resource {
  private final WithResponse withResponse = new WithResponse(this);

  /**
   * Created by the client: use {@code flashpi.issuers().establishments().emissionPoints()}.
   *
   * @param engine the client's engine
   */
  public EmissionPoints(Engine engine) {
    super(engine);
  }

  /**
   * Creates an emission point.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param body the body
   * @return the emission point
   */
  public EmissionPoint create(String issuerId, String establishmentId, CreateEmissionPoint body) {
    return create(issuerId, establishmentId, body, null);
  }

  /**
   * Creates an emission point.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param body the body
   * @param options the call's options, or null
   * @return the emission point
   */
  public EmissionPoint create(
      String issuerId, String establishmentId, CreateEmissionPoint body, RequestOptions options) {
    return request(
        RequestSpec.of(
                "POST",
                Paths.of(
                    "/v1/issuers/{}/establishments/{}/emission-points", issuerId, establishmentId),
                Readers.json(EmissionPoint.class))
            .body(Objects.requireNonNull(body, "body"))
            .options(options)
            .build());
  }

  /**
   * Lists the emission points of an establishment, by code.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @return the first page; iterating it walks every page
   */
  public Page<EmissionPoint> list(String issuerId, String establishmentId) {
    return list(issuerId, establishmentId, null, null);
  }

  /**
   * Lists the emission points of an establishment, by code.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param params the filters, or null
   * @return the first page; iterating it walks every page
   */
  public Page<EmissionPoint> list(
      String issuerId, String establishmentId, EmissionPointsListParams params) {
    return list(issuerId, establishmentId, params, null);
  }

  /**
   * Lists the emission points of an establishment, by code.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param params the filters, or null
   * @param options the call's options, or null
   * @return the first page; iterating it walks every page
   */
  public Page<EmissionPoint> list(
      String issuerId,
      String establishmentId,
      EmissionPointsListParams params,
      RequestOptions options) {
    return list(
        RequestSpec.of(
                "GET",
                Paths.of(
                    "/v1/issuers/{}/establishments/{}/emission-points", issuerId, establishmentId),
                Readers.list(EmissionPoint.class))
            .query(params)
            .options(options)
            .build());
  }

  /**
   * Retrieves an emission point.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param emissionPointId the emission point's id
   * @return the emission point
   */
  public EmissionPoint get(String issuerId, String establishmentId, String emissionPointId) {
    return get(issuerId, establishmentId, emissionPointId, null);
  }

  /**
   * Retrieves an emission point.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param emissionPointId the emission point's id
   * @param options the call's options, or null
   * @return the emission point
   */
  public EmissionPoint get(
      String issuerId, String establishmentId, String emissionPointId, RequestOptions options) {
    return request(
        RequestSpec.of(
                "GET",
                Paths.of(
                    "/v1/issuers/{}/establishments/{}/emission-points/{}",
                    issuerId,
                    establishmentId,
                    emissionPointId),
                Readers.json(EmissionPoint.class))
            .options(options)
            .build());
  }

  /**
   * Deletes an emission point.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param emissionPointId the emission point's id
   */
  public void delete(String issuerId, String establishmentId, String emissionPointId) {
    delete(issuerId, establishmentId, emissionPointId, null);
  }

  /**
   * Deletes an emission point.
   *
   * @param issuerId the issuer's id
   * @param establishmentId the establishment's id
   * @param emissionPointId the emission point's id
   * @param options the call's options, or null
   */
  public void delete(
      String issuerId, String establishmentId, String emissionPointId, RequestOptions options) {
    request(
        RequestSpec.of(
                "DELETE",
                Paths.of(
                    "/v1/issuers/{}/establishments/{}/emission-points/{}",
                    issuerId,
                    establishmentId,
                    emissionPointId),
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

  /** The methods of {@link EmissionPoints}, returning an {@link ApiResponse}. */
  public static final class WithResponse {
    private final EmissionPoints emissionPoints;

    WithResponse(EmissionPoints emissionPoints) {
      this.emissionPoints = emissionPoints;
    }

    /**
     * See {@link EmissionPoints#create(String, String, CreateEmissionPoint)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param body the body
     * @return the emission point and the response
     */
    public ApiResponse<EmissionPoint> create(
        String issuerId, String establishmentId, CreateEmissionPoint body) {
      return Capture.run(() -> emissionPoints.create(issuerId, establishmentId, body));
    }

    /**
     * See {@link EmissionPoints#create(String, String, CreateEmissionPoint, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param body the body
     * @param options the call's options, or null
     * @return the emission point and the response
     */
    public ApiResponse<EmissionPoint> create(
        String issuerId, String establishmentId, CreateEmissionPoint body, RequestOptions options) {
      return Capture.run(() -> emissionPoints.create(issuerId, establishmentId, body, options));
    }

    /**
     * See {@link EmissionPoints#list(String, String)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @return the first page and the response
     */
    public ApiResponse<Page<EmissionPoint>> list(String issuerId, String establishmentId) {
      return Capture.run(() -> emissionPoints.list(issuerId, establishmentId));
    }

    /**
     * See {@link EmissionPoints#list(String, String, EmissionPointsListParams)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param params the filters, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<EmissionPoint>> list(
        String issuerId, String establishmentId, EmissionPointsListParams params) {
      return Capture.run(() -> emissionPoints.list(issuerId, establishmentId, params));
    }

    /**
     * See {@link EmissionPoints#list(String, String, EmissionPointsListParams, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param params the filters, or null
     * @param options the call's options, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<EmissionPoint>> list(
        String issuerId,
        String establishmentId,
        EmissionPointsListParams params,
        RequestOptions options) {
      return Capture.run(() -> emissionPoints.list(issuerId, establishmentId, params, options));
    }

    /**
     * See {@link EmissionPoints#get(String, String, String)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param emissionPointId the emission point's id
     * @return the emission point and the response
     */
    public ApiResponse<EmissionPoint> get(
        String issuerId, String establishmentId, String emissionPointId) {
      return Capture.run(() -> emissionPoints.get(issuerId, establishmentId, emissionPointId));
    }

    /**
     * See {@link EmissionPoints#get(String, String, String, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param emissionPointId the emission point's id
     * @param options the call's options, or null
     * @return the emission point and the response
     */
    public ApiResponse<EmissionPoint> get(
        String issuerId, String establishmentId, String emissionPointId, RequestOptions options) {
      return Capture.run(
          () -> emissionPoints.get(issuerId, establishmentId, emissionPointId, options));
    }

    /**
     * See {@link EmissionPoints#delete(String, String, String)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param emissionPointId the emission point's id
     * @return the response
     */
    public ApiResponse<Void> delete(
        String issuerId, String establishmentId, String emissionPointId) {
      return Capture.run(
          () -> {
            emissionPoints.delete(issuerId, establishmentId, emissionPointId);
            return null;
          });
    }

    /**
     * See {@link EmissionPoints#delete(String, String, String, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param establishmentId the establishment's id
     * @param emissionPointId the emission point's id
     * @param options the call's options, or null
     * @return the response
     */
    public ApiResponse<Void> delete(
        String issuerId, String establishmentId, String emissionPointId, RequestOptions options) {
      return Capture.run(
          () -> {
            emissionPoints.delete(issuerId, establishmentId, emissionPointId, options);
            return null;
          });
    }
  }
}
