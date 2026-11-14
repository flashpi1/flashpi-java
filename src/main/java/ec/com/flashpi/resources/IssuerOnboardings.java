package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.CertificateUpload;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Paths;
import ec.com.flashpi.internal.Readers;
import ec.com.flashpi.internal.RequestSpec;
import ec.com.flashpi.internal.Uploads;
import ec.com.flashpi.models.CompleteIssuerOnboarding;
import ec.com.flashpi.models.CompletedIssuerOnboarding;
import ec.com.flashpi.models.IssuerOnboarding;

/**
 * Creates an issuer from its .p12: upload the certificate, wait while the onboarding is {@code
 * verifying} until it is {@code ready} (Flashpi reads the holder, RUC and SRI data) or {@code
 * rejected}, review the prefill, then complete it.
 *
 * <p>Needs an account-scoped API key with read_write access.
 */
public final class IssuerOnboardings extends Resource {
  private final WithResponse withResponse = new WithResponse(this);

  /**
   * Created by the client: use {@code flashpi.issuerOnboardings()}.
   *
   * @param engine the client's engine
   */
  public IssuerOnboardings(Engine engine) {
    super(engine);
  }

  /**
   * Starts an onboarding with a .p12 and its password. It starts {@code verifying}.
   *
   * @param upload the .p12 and its password
   * @return the onboarding
   */
  public IssuerOnboarding create(CertificateUpload upload) {
    return create(upload, null);
  }

  /**
   * Starts an onboarding with a .p12 and its password. It starts {@code verifying}.
   *
   * @param upload the .p12 and its password
   * @param options the call's options, or null
   * @return the onboarding
   */
  public IssuerOnboarding create(CertificateUpload upload, RequestOptions options) {
    return request(
        RequestSpec.of("POST", "/v1/issuer-onboardings", Readers.json(IssuerOnboarding.class))
            .multipart(Uploads.form(upload))
            .options(options)
            .build());
  }

  /**
   * Retrieves an onboarding, to follow it while it is {@code verifying}.
   *
   * @param onboardingId the onboarding's id
   * @return the onboarding
   */
  public IssuerOnboarding get(String onboardingId) {
    return get(onboardingId, null);
  }

  /**
   * Retrieves an onboarding, to follow it while it is {@code verifying}.
   *
   * @param onboardingId the onboarding's id
   * @param options the call's options, or null
   * @return the onboarding
   */
  public IssuerOnboarding get(String onboardingId, RequestOptions options) {
    return request(
        RequestSpec.of(
                "GET",
                Paths.of("/v1/issuer-onboardings/{}", onboardingId),
                Readers.json(IssuerOnboarding.class))
            .options(options)
            .build());
  }

  /**
   * Completes a {@code ready} onboarding.
   *
   * <ul>
   *   <li>For a new RUC, {@code body} is the issuer to create (as in {@code issuers().create()}):
   *       {@code legalName}, {@code headOfficeAddress} and {@code taxProfile} are required. Build
   *       it from the onboarding's {@code prefill}; the API does not apply the prefill by itself.
   *   <li>When the onboarding's {@code existingIssuer} is set, {@code body} can be left out ({@code
   *       {}} is sent): the certificate is added to that issuer for both modes (a renewal) and the
   *       issuer is left as it is.
   *   <li>Send {@code ruc} when {@code rucNeedsConfirmation} is true.
   * </ul>
   *
   * @param onboardingId the onboarding's id
   * @return the onboarding and the issuer
   */
  public CompletedIssuerOnboarding complete(String onboardingId) {
    return complete(onboardingId, null, null);
  }

  /**
   * Completes a {@code ready} onboarding.
   *
   * <ul>
   *   <li>For a new RUC, {@code body} is the issuer to create (as in {@code issuers().create()}):
   *       {@code legalName}, {@code headOfficeAddress} and {@code taxProfile} are required. Build
   *       it from the onboarding's {@code prefill}; the API does not apply the prefill by itself.
   *   <li>When the onboarding's {@code existingIssuer} is set, {@code body} can be left out ({@code
   *       {}} is sent): the certificate is added to that issuer for both modes (a renewal) and the
   *       issuer is left as it is.
   *   <li>Send {@code ruc} when {@code rucNeedsConfirmation} is true.
   * </ul>
   *
   * @param onboardingId the onboarding's id
   * @param body the body, or null
   * @return the onboarding and the issuer
   */
  public CompletedIssuerOnboarding complete(String onboardingId, CompleteIssuerOnboarding body) {
    return complete(onboardingId, body, null);
  }

  /**
   * Completes a {@code ready} onboarding.
   *
   * <ul>
   *   <li>For a new RUC, {@code body} is the issuer to create (as in {@code issuers().create()}):
   *       {@code legalName}, {@code headOfficeAddress} and {@code taxProfile} are required. Build
   *       it from the onboarding's {@code prefill}; the API does not apply the prefill by itself.
   *   <li>When the onboarding's {@code existingIssuer} is set, {@code body} can be left out ({@code
   *       {}} is sent): the certificate is added to that issuer for both modes (a renewal) and the
   *       issuer is left as it is.
   *   <li>Send {@code ruc} when {@code rucNeedsConfirmation} is true.
   * </ul>
   *
   * @param onboardingId the onboarding's id
   * @param body the body, or null
   * @param options the call's options, or null
   * @return the onboarding and the issuer
   */
  public CompletedIssuerOnboarding complete(
      String onboardingId, CompleteIssuerOnboarding body, RequestOptions options) {
    return request(
        RequestSpec.of(
                "POST",
                Paths.of("/v1/issuer-onboardings/{}/complete", onboardingId),
                Readers.json(CompletedIssuerOnboarding.class))
            .body(body == null ? CompleteIssuerOnboarding.builder().build() : body)
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

  /** The methods of {@link IssuerOnboardings}, returning an {@link ApiResponse}. */
  public static final class WithResponse {
    private final IssuerOnboardings issuerOnboardings;

    WithResponse(IssuerOnboardings issuerOnboardings) {
      this.issuerOnboardings = issuerOnboardings;
    }

    /**
     * See {@link IssuerOnboardings#create(CertificateUpload)}.
     *
     * @param upload the .p12 and its password
     * @return the onboarding and the response
     */
    public ApiResponse<IssuerOnboarding> create(CertificateUpload upload) {
      return Capture.run(() -> issuerOnboardings.create(upload));
    }

    /**
     * See {@link IssuerOnboardings#create(CertificateUpload, RequestOptions)}.
     *
     * @param upload the .p12 and its password
     * @param options the call's options, or null
     * @return the onboarding and the response
     */
    public ApiResponse<IssuerOnboarding> create(CertificateUpload upload, RequestOptions options) {
      return Capture.run(() -> issuerOnboardings.create(upload, options));
    }

    /**
     * See {@link IssuerOnboardings#get(String)}.
     *
     * @param onboardingId the onboarding's id
     * @return the onboarding and the response
     */
    public ApiResponse<IssuerOnboarding> get(String onboardingId) {
      return Capture.run(() -> issuerOnboardings.get(onboardingId));
    }

    /**
     * See {@link IssuerOnboardings#get(String, RequestOptions)}.
     *
     * @param onboardingId the onboarding's id
     * @param options the call's options, or null
     * @return the onboarding and the response
     */
    public ApiResponse<IssuerOnboarding> get(String onboardingId, RequestOptions options) {
      return Capture.run(() -> issuerOnboardings.get(onboardingId, options));
    }

    /**
     * See {@link IssuerOnboardings#complete(String)}.
     *
     * @param onboardingId the onboarding's id
     * @return the onboarding and the issuer and the response
     */
    public ApiResponse<CompletedIssuerOnboarding> complete(String onboardingId) {
      return Capture.run(() -> issuerOnboardings.complete(onboardingId));
    }

    /**
     * See {@link IssuerOnboardings#complete(String, CompleteIssuerOnboarding)}.
     *
     * @param onboardingId the onboarding's id
     * @param body the body, or null
     * @return the onboarding and the issuer and the response
     */
    public ApiResponse<CompletedIssuerOnboarding> complete(
        String onboardingId, CompleteIssuerOnboarding body) {
      return Capture.run(() -> issuerOnboardings.complete(onboardingId, body));
    }

    /**
     * See {@link IssuerOnboardings#complete(String, CompleteIssuerOnboarding, RequestOptions)}.
     *
     * @param onboardingId the onboarding's id
     * @param body the body, or null
     * @param options the call's options, or null
     * @return the onboarding and the issuer and the response
     */
    public ApiResponse<CompletedIssuerOnboarding> complete(
        String onboardingId, CompleteIssuerOnboarding body, RequestOptions options) {
      return Capture.run(() -> issuerOnboardings.complete(onboardingId, body, options));
    }
  }
}
