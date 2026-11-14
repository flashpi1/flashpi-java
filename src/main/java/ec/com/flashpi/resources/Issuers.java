package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.Page;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Paths;
import ec.com.flashpi.internal.Readers;
import ec.com.flashpi.internal.RequestSpec;
import ec.com.flashpi.models.CreateIssuer;
import ec.com.flashpi.models.Issuer;
import ec.com.flashpi.models.IssuersListParams;
import ec.com.flashpi.models.UpdateIssuer;
import java.util.Objects;

/**
 * The issuers (RUCs) of the account, with their establishments, emission points and .p12
 * certificates. Needs an account-scoped API key with read_write access, also to read.
 */
public final class Issuers extends Resource {
  private final Establishments establishments;
  private final Certificates certificates;
  private final WithResponse withResponse = new WithResponse(this);

  /**
   * Created by the client: use {@code flashpi.issuers()}.
   *
   * @param engine the client's engine
   */
  public Issuers(Engine engine) {
    super(engine);
    this.establishments = new Establishments(engine);
    this.certificates = new Certificates(engine);
  }

  /**
   * Establishments of an issuer, with their emission points.
   *
   * @return the resource
   */
  public Establishments establishments() {
    return establishments;
  }

  /**
   * The .p12 certificates of an issuer.
   *
   * @return the resource
   */
  public Certificates certificates() {
    return certificates;
  }

  /**
   * Creates an issuer. To start from its .p12 instead, see {@code issuerOnboardings()}.
   *
   * @param body the body
   * @return the issuer
   */
  public Issuer create(CreateIssuer body) {
    return create(body, null);
  }

  /**
   * Creates an issuer. To start from its .p12 instead, see {@code issuerOnboardings()}.
   *
   * @param body the body
   * @param options the call's options, or null
   * @return the issuer
   */
  public Issuer create(CreateIssuer body, RequestOptions options) {
    return request(
        RequestSpec.of("POST", "/v1/issuers", Readers.json(Issuer.class))
            .body(Objects.requireNonNull(body, "body"))
            .options(options)
            .build());
  }

  /**
   * Lists the issuers, newest first.
   *
   * @return the first page; iterating it walks every page
   */
  public Page<Issuer> list() {
    return list(null, null);
  }

  /**
   * Lists the issuers, newest first.
   *
   * @param params the filters, or null
   * @return the first page; iterating it walks every page
   */
  public Page<Issuer> list(IssuersListParams params) {
    return list(params, null);
  }

  /**
   * Lists the issuers, newest first.
   *
   * @param params the filters, or null
   * @param options the call's options, or null
   * @return the first page; iterating it walks every page
   */
  public Page<Issuer> list(IssuersListParams params, RequestOptions options) {
    return list(
        RequestSpec.of("GET", "/v1/issuers", Readers.list(Issuer.class))
            .query(params)
            .options(options)
            .build());
  }

  /**
   * Retrieves an issuer.
   *
   * @param issuerId the issuer's id
   * @return the issuer
   */
  public Issuer get(String issuerId) {
    return get(issuerId, null);
  }

  /**
   * Retrieves an issuer.
   *
   * @param issuerId the issuer's id
   * @param options the call's options, or null
   * @return the issuer
   */
  public Issuer get(String issuerId, RequestOptions options) {
    return request(
        RequestSpec.of("GET", Paths.of("/v1/issuers/{}", issuerId), Readers.json(Issuer.class))
            .options(options)
            .build());
  }

  /**
   * Updates an issuer's name, tax profile, branding or defaults (only the members set).
   *
   * @param issuerId the issuer's id
   * @param body the body
   * @return the issuer
   */
  public Issuer update(String issuerId, UpdateIssuer body) {
    return update(issuerId, body, null);
  }

  /**
   * Updates an issuer's name, tax profile, branding or defaults (only the members set).
   *
   * @param issuerId the issuer's id
   * @param body the body
   * @param options the call's options, or null
   * @return the issuer
   */
  public Issuer update(String issuerId, UpdateIssuer body, RequestOptions options) {
    return request(
        RequestSpec.of("PATCH", Paths.of("/v1/issuers/{}", issuerId), Readers.json(Issuer.class))
            .body(Objects.requireNonNull(body, "body"))
            .options(options)
            .build());
  }

  /**
   * Deletes an issuer that has no documents and no certificates, with its establishments, emission
   * points and issuer-scoped API keys. Otherwise 409 {@code resource_in_use}: certificates cannot
   * be deleted, so an issuer that ever had a .p12 (one from {@code issuerOnboardings()} too) stays.
   *
   * @param issuerId the issuer's id
   */
  public void delete(String issuerId) {
    delete(issuerId, null);
  }

  /**
   * Deletes an issuer that has no documents and no certificates, with its establishments, emission
   * points and issuer-scoped API keys. Otherwise 409 {@code resource_in_use}: certificates cannot
   * be deleted, so an issuer that ever had a .p12 (one from {@code issuerOnboardings()} too) stays.
   *
   * @param issuerId the issuer's id
   * @param options the call's options, or null
   */
  public void delete(String issuerId, RequestOptions options) {
    request(
        RequestSpec.of("DELETE", Paths.of("/v1/issuers/{}", issuerId), Readers.none())
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

  /** The methods of {@link Issuers}, returning an {@link ApiResponse}. */
  public static final class WithResponse {
    private final Issuers issuers;

    WithResponse(Issuers issuers) {
      this.issuers = issuers;
    }

    /**
     * See {@link Issuers#create(CreateIssuer)}.
     *
     * @param body the body
     * @return the issuer and the response
     */
    public ApiResponse<Issuer> create(CreateIssuer body) {
      return Capture.run(() -> issuers.create(body));
    }

    /**
     * See {@link Issuers#create(CreateIssuer, RequestOptions)}.
     *
     * @param body the body
     * @param options the call's options, or null
     * @return the issuer and the response
     */
    public ApiResponse<Issuer> create(CreateIssuer body, RequestOptions options) {
      return Capture.run(() -> issuers.create(body, options));
    }

    /**
     * See {@link Issuers#list()}.
     *
     * @return the first page and the response
     */
    public ApiResponse<Page<Issuer>> list() {
      return Capture.run(() -> issuers.list());
    }

    /**
     * See {@link Issuers#list(IssuersListParams)}.
     *
     * @param params the filters, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<Issuer>> list(IssuersListParams params) {
      return Capture.run(() -> issuers.list(params));
    }

    /**
     * See {@link Issuers#list(IssuersListParams, RequestOptions)}.
     *
     * @param params the filters, or null
     * @param options the call's options, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<Issuer>> list(IssuersListParams params, RequestOptions options) {
      return Capture.run(() -> issuers.list(params, options));
    }

    /**
     * See {@link Issuers#get(String)}.
     *
     * @param issuerId the issuer's id
     * @return the issuer and the response
     */
    public ApiResponse<Issuer> get(String issuerId) {
      return Capture.run(() -> issuers.get(issuerId));
    }

    /**
     * See {@link Issuers#get(String, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param options the call's options, or null
     * @return the issuer and the response
     */
    public ApiResponse<Issuer> get(String issuerId, RequestOptions options) {
      return Capture.run(() -> issuers.get(issuerId, options));
    }

    /**
     * See {@link Issuers#update(String, UpdateIssuer)}.
     *
     * @param issuerId the issuer's id
     * @param body the body
     * @return the issuer and the response
     */
    public ApiResponse<Issuer> update(String issuerId, UpdateIssuer body) {
      return Capture.run(() -> issuers.update(issuerId, body));
    }

    /**
     * See {@link Issuers#update(String, UpdateIssuer, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param body the body
     * @param options the call's options, or null
     * @return the issuer and the response
     */
    public ApiResponse<Issuer> update(String issuerId, UpdateIssuer body, RequestOptions options) {
      return Capture.run(() -> issuers.update(issuerId, body, options));
    }

    /**
     * See {@link Issuers#delete(String)}.
     *
     * @param issuerId the issuer's id
     * @return the response
     */
    public ApiResponse<Void> delete(String issuerId) {
      return Capture.run(
          () -> {
            issuers.delete(issuerId);
            return null;
          });
    }

    /**
     * See {@link Issuers#delete(String, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param options the call's options, or null
     * @return the response
     */
    public ApiResponse<Void> delete(String issuerId, RequestOptions options) {
      return Capture.run(
          () -> {
            issuers.delete(issuerId, options);
            return null;
          });
    }
  }
}
