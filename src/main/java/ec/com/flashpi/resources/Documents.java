package ec.com.flashpi.resources;

import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.DocumentXml;
import ec.com.flashpi.Page;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.internal.Capture;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Paths;
import ec.com.flashpi.internal.Readers;
import ec.com.flashpi.internal.RequestSpec;
import ec.com.flashpi.models.Document;
import ec.com.flashpi.models.DocumentsListParams;
import ec.com.flashpi.models.SendDocumentEmail;

/** Every issued document, of any type: search, XML, RIDE (PDF) and email. */
public final class Documents extends Resource {
  static final String XML_ACCEPT = "application/xml, application/problem+json";
  static final String PDF_ACCEPT = "application/pdf, application/problem+json";

  private final WithResponse withResponse = new WithResponse(this);

  /**
   * Created by the client: use {@code flashpi.documents()}.
   *
   * @param engine the client's engine
   */
  public Documents(Engine engine) {
    super(engine);
  }

  /**
   * Lists documents, newest first.
   *
   * @return the first page; iterating it walks every page
   */
  public Page<Document> list() {
    return list(null, null);
  }

  /**
   * Lists documents, newest first, with filters.
   *
   * @param params the filters, or null
   * @return the first page; iterating it walks every page
   */
  public Page<Document> list(DocumentsListParams params) {
    return list(params, null);
  }

  /**
   * Lists documents, newest first, with filters.
   *
   * @param params the filters, or null
   * @param options the call's options, or null
   * @return the first page; iterating it walks every page
   */
  public Page<Document> list(DocumentsListParams params, RequestOptions options) {
    return list(
        RequestSpec.of("GET", "/v1/documents", Readers.list(Document.class))
            .query(params)
            .options(options)
            .build());
  }

  /**
   * Retrieves a document with its tax status, SRI messages, RIDE and email state.
   *
   * @param documentId the document's id
   * @return the document
   */
  public Document get(String documentId) {
    return get(documentId, null);
  }

  /**
   * Retrieves a document with its tax status, SRI messages, RIDE and email state.
   *
   * @param documentId the document's id
   * @param options the call's options, or null
   * @return the document
   */
  public Document get(String documentId, RequestOptions options) {
    return request(
        RequestSpec.of(
                "GET", Paths.of("/v1/documents/{}", documentId), Readers.json(Document.class))
            .options(options)
            .build());
  }

  /**
   * The document's XML: the authorized one once the SRI authorizes it, otherwise the signed or
   * unsigned one (see {@link DocumentXml#getKind()}).
   *
   * @param documentId the document's id
   * @return the XML and its stage
   */
  public DocumentXml xml(String documentId) {
    return xml(documentId, null);
  }

  /**
   * The document's XML: the authorized one once the SRI authorizes it, otherwise the signed or
   * unsigned one (see {@link DocumentXml#getKind()}).
   *
   * @param documentId the document's id
   * @param options the call's options, or null
   * @return the XML and its stage
   */
  public DocumentXml xml(String documentId, RequestOptions options) {
    // The stage comes from a header, so the XML is read with its response.
    ApiResponse<String> result =
        Capture.run(
            () ->
                request(
                    RequestSpec.of(
                            "GET", Paths.of("/v1/documents/{}/xml", documentId), Readers.text())
                        .accept(XML_ACCEPT)
                        .options(options)
                        .build()));
    DocumentXml xml =
        new DocumentXml(
            result.getData(),
            DocumentXml.Kind.fromValue(
                result.getHeaders().firstValue("flashpi-xml-kind").orElse(null)));
    Capture.record(
        new ApiResponse<>(xml, result.getStatusCode(), result.getHeaders(), result.getRequestId()));
    return xml;
  }

  /**
   * The RIDE (the printable PDF) of an authorized document. Throws a {@code ConflictException} with
   * code {@code ride_not_available} while the document is not authorized yet or its RIDE is being
   * generated, and when its generation failed (see {@code getProblem().getRideStatus()}).
   *
   * @param documentId the document's id
   * @return the PDF's bytes
   */
  public byte[] pdf(String documentId) {
    return pdf(documentId, null);
  }

  /**
   * The RIDE (the printable PDF) of an authorized document. Throws a {@code ConflictException} with
   * code {@code ride_not_available} while the document is not authorized yet or its RIDE is being
   * generated, and when its generation failed (see {@code getProblem().getRideStatus()}).
   *
   * @param documentId the document's id
   * @param options the call's options, or null
   * @return the PDF's bytes
   */
  public byte[] pdf(String documentId, RequestOptions options) {
    return request(
        RequestSpec.of("GET", Paths.of("/v1/documents/{}/pdf", documentId), Readers.bytes())
            .accept(PDF_ACCEPT)
            .options(options)
            .build());
  }

  /**
   * Emails the authorized document (RIDE and XML) to the address of its request, also when no email
   * was asked for when it was issued; in test mode, only to the verified members of your team. The
   * new delivery shows in {@code email.deliveries}; daily limits apply ({@code
   * RateLimitException}).
   *
   * @param documentId the document's id
   * @return the document
   */
  public Document sendEmail(String documentId) {
    return sendEmail(documentId, null);
  }

  /**
   * Emails the authorized document (RIDE and XML) to the address of its request, also when no email
   * was asked for when it was issued; in test mode, only to the verified members of your team. The
   * new delivery shows in {@code email.deliveries}; daily limits apply ({@code
   * RateLimitException}).
   *
   * @param documentId the document's id
   * @param options the call's options, or null
   * @return the document
   */
  public Document sendEmail(String documentId, RequestOptions options) {
    return request(
        RequestSpec.of(
                "POST",
                Paths.of("/v1/documents/{}/email", documentId),
                Readers.json(Document.class))
            .body(SendDocumentEmail.builder().build())
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

  /** The methods of {@link Documents}, returning an {@link ApiResponse}. */
  public static final class WithResponse {
    private final Documents documents;

    WithResponse(Documents documents) {
      this.documents = documents;
    }

    /**
     * See {@link Documents#list()}.
     *
     * @return the first page and its response
     */
    public ApiResponse<Page<Document>> list() {
      return Capture.run(documents::list);
    }

    /**
     * See {@link Documents#list(DocumentsListParams)}.
     *
     * @param params the filters, or null
     * @return the first page and its response
     */
    public ApiResponse<Page<Document>> list(DocumentsListParams params) {
      return Capture.run(() -> documents.list(params));
    }

    /**
     * See {@link Documents#list(DocumentsListParams, RequestOptions)}.
     *
     * @param params the filters, or null
     * @param options the call's options, or null
     * @return the first page and its response
     */
    public ApiResponse<Page<Document>> list(DocumentsListParams params, RequestOptions options) {
      return Capture.run(() -> documents.list(params, options));
    }

    /**
     * See {@link Documents#get(String)}.
     *
     * @param documentId the document's id
     * @return the document and the response
     */
    public ApiResponse<Document> get(String documentId) {
      return Capture.run(() -> documents.get(documentId));
    }

    /**
     * See {@link Documents#get(String, RequestOptions)}.
     *
     * @param documentId the document's id
     * @param options the call's options, or null
     * @return the document and the response
     */
    public ApiResponse<Document> get(String documentId, RequestOptions options) {
      return Capture.run(() -> documents.get(documentId, options));
    }

    /**
     * See {@link Documents#xml(String)}.
     *
     * @param documentId the document's id
     * @return the XML and the response
     */
    public ApiResponse<DocumentXml> xml(String documentId) {
      return Capture.run(() -> documents.xml(documentId));
    }

    /**
     * See {@link Documents#xml(String, RequestOptions)}.
     *
     * @param documentId the document's id
     * @param options the call's options, or null
     * @return the XML and the response
     */
    public ApiResponse<DocumentXml> xml(String documentId, RequestOptions options) {
      return Capture.run(() -> documents.xml(documentId, options));
    }

    /**
     * See {@link Documents#pdf(String)}.
     *
     * @param documentId the document's id
     * @return the PDF and the response
     */
    public ApiResponse<byte[]> pdf(String documentId) {
      return Capture.run(() -> documents.pdf(documentId));
    }

    /**
     * See {@link Documents#pdf(String, RequestOptions)}.
     *
     * @param documentId the document's id
     * @param options the call's options, or null
     * @return the PDF and the response
     */
    public ApiResponse<byte[]> pdf(String documentId, RequestOptions options) {
      return Capture.run(() -> documents.pdf(documentId, options));
    }

    /**
     * See {@link Documents#sendEmail(String)}.
     *
     * @param documentId the document's id
     * @return the document and the response
     */
    public ApiResponse<Document> sendEmail(String documentId) {
      return Capture.run(() -> documents.sendEmail(documentId));
    }

    /**
     * See {@link Documents#sendEmail(String, RequestOptions)}.
     *
     * @param documentId the document's id
     * @param options the call's options, or null
     * @return the document and the response
     */
    public ApiResponse<Document> sendEmail(String documentId, RequestOptions options) {
      return Capture.run(() -> documents.sendEmail(documentId, options));
    }
  }
}
