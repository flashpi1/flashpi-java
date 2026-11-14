package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.CertificateUpload;
import ec.com.flashpi.Page;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Paths;
import ec.com.flashpi.internal.Readers;
import ec.com.flashpi.internal.RequestSpec;
import ec.com.flashpi.internal.Uploads;
import ec.com.flashpi.models.Certificate;
import ec.com.flashpi.models.CertificatesListParams;

/** The .p12 certificates (firmas electrónicas) of an issuer. */
public final class Certificates extends Resource {
  private final WithResponse withResponse = new WithResponse(this);

  /**
   * Created by the client: use {@code flashpi.issuers().certificates()}.
   *
   * @param engine the client's engine
   */
  public Certificates(Engine engine) {
    super(engine);
  }

  /**
   * Uploads a .p12 for the issuer, for the mode of the API key only (test or live). To renew the
   * certificate of both modes, start an {@code issuerOnboardings()} with it instead.
   *
   * <p>Flashpi checks it in the background: the certificate starts {@code processing}, then becomes
   * {@code accepted} (event {@code certificate.verified}, or {@code certificate.pending} while its
   * revocation status cannot be checked) or {@code rejected} (no event: follow it with {@code
   * get()} and read {@code rejection}).
   *
   * @param issuerId the issuer's id
   * @param upload the .p12 and its password
   * @return the certificate
   */
  public Certificate upload(String issuerId, CertificateUpload upload) {
    return upload(issuerId, upload, null);
  }

  /**
   * Uploads a .p12 for the issuer, for the mode of the API key only (test or live). To renew the
   * certificate of both modes, start an {@code issuerOnboardings()} with it instead.
   *
   * <p>Flashpi checks it in the background: the certificate starts {@code processing}, then becomes
   * {@code accepted} (event {@code certificate.verified}, or {@code certificate.pending} while its
   * revocation status cannot be checked) or {@code rejected} (no event: follow it with {@code
   * get()} and read {@code rejection}).
   *
   * @param issuerId the issuer's id
   * @param upload the .p12 and its password
   * @param options the call's options, or null
   * @return the certificate
   */
  public Certificate upload(String issuerId, CertificateUpload upload, RequestOptions options) {
    return request(
        RequestSpec.of(
                "POST",
                Paths.of("/v1/issuers/{}/certificates", issuerId),
                Readers.json(Certificate.class))
            .multipart(Uploads.form(upload))
            .options(options)
            .build());
  }

  /**
   * Lists the certificates of an issuer, newest first.
   *
   * @param issuerId the issuer's id
   * @return the first page; iterating it walks every page
   */
  public Page<Certificate> list(String issuerId) {
    return list(issuerId, null, null);
  }

  /**
   * Lists the certificates of an issuer, newest first.
   *
   * @param issuerId the issuer's id
   * @param params the filters, or null
   * @return the first page; iterating it walks every page
   */
  public Page<Certificate> list(String issuerId, CertificatesListParams params) {
    return list(issuerId, params, null);
  }

  /**
   * Lists the certificates of an issuer, newest first.
   *
   * @param issuerId the issuer's id
   * @param params the filters, or null
   * @param options the call's options, or null
   * @return the first page; iterating it walks every page
   */
  public Page<Certificate> list(
      String issuerId, CertificatesListParams params, RequestOptions options) {
    return list(
        RequestSpec.of(
                "GET",
                Paths.of("/v1/issuers/{}/certificates", issuerId),
                Readers.list(Certificate.class))
            .query(params)
            .options(options)
            .build());
  }

  /**
   * Retrieves a certificate.
   *
   * @param issuerId the issuer's id
   * @param certificateId the certificate's id
   * @return the certificate
   */
  public Certificate get(String issuerId, String certificateId) {
    return get(issuerId, certificateId, null);
  }

  /**
   * Retrieves a certificate.
   *
   * @param issuerId the issuer's id
   * @param certificateId the certificate's id
   * @param options the call's options, or null
   * @return the certificate
   */
  public Certificate get(String issuerId, String certificateId, RequestOptions options) {
    return request(
        RequestSpec.of(
                "GET",
                Paths.of("/v1/issuers/{}/certificates/{}", issuerId, certificateId),
                Readers.json(Certificate.class))
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

  /** The methods of {@link Certificates}, returning an {@link ApiResponse}. */
  public static final class WithResponse {
    private final Certificates certificates;

    WithResponse(Certificates certificates) {
      this.certificates = certificates;
    }

    /**
     * See {@link Certificates#upload(String, CertificateUpload)}.
     *
     * @param issuerId the issuer's id
     * @param upload the .p12 and its password
     * @return the certificate and the response
     */
    public ApiResponse<Certificate> upload(String issuerId, CertificateUpload upload) {
      return Capture.run(() -> certificates.upload(issuerId, upload));
    }

    /**
     * See {@link Certificates#upload(String, CertificateUpload, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param upload the .p12 and its password
     * @param options the call's options, or null
     * @return the certificate and the response
     */
    public ApiResponse<Certificate> upload(
        String issuerId, CertificateUpload upload, RequestOptions options) {
      return Capture.run(() -> certificates.upload(issuerId, upload, options));
    }

    /**
     * See {@link Certificates#list(String)}.
     *
     * @param issuerId the issuer's id
     * @return the first page and the response
     */
    public ApiResponse<Page<Certificate>> list(String issuerId) {
      return Capture.run(() -> certificates.list(issuerId));
    }

    /**
     * See {@link Certificates#list(String, CertificatesListParams)}.
     *
     * @param issuerId the issuer's id
     * @param params the filters, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<Certificate>> list(String issuerId, CertificatesListParams params) {
      return Capture.run(() -> certificates.list(issuerId, params));
    }

    /**
     * See {@link Certificates#list(String, CertificatesListParams, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param params the filters, or null
     * @param options the call's options, or null
     * @return the first page and the response
     */
    public ApiResponse<Page<Certificate>> list(
        String issuerId, CertificatesListParams params, RequestOptions options) {
      return Capture.run(() -> certificates.list(issuerId, params, options));
    }

    /**
     * See {@link Certificates#get(String, String)}.
     *
     * @param issuerId the issuer's id
     * @param certificateId the certificate's id
     * @return the certificate and the response
     */
    public ApiResponse<Certificate> get(String issuerId, String certificateId) {
      return Capture.run(() -> certificates.get(issuerId, certificateId));
    }

    /**
     * See {@link Certificates#get(String, String, RequestOptions)}.
     *
     * @param issuerId the issuer's id
     * @param certificateId the certificate's id
     * @param options the call's options, or null
     * @return the certificate and the response
     */
    public ApiResponse<Certificate> get(
        String issuerId, String certificateId, RequestOptions options) {
      return Capture.run(() -> certificates.get(issuerId, certificateId, options));
    }
  }
}
