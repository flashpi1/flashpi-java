package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.com.flashpi.exceptions.ConflictException;
import ec.com.flashpi.exceptions.NotFoundException;
import ec.com.flashpi.exceptions.UnprocessableEntityException;
import ec.com.flashpi.models.CreateInvoice;
import ec.com.flashpi.models.Document;
import ec.com.flashpi.models.DocumentEvent;
import ec.com.flashpi.models.DocumentItem;
import ec.com.flashpi.models.DocumentOptions;
import ec.com.flashpi.models.DocumentParty;
import ec.com.flashpi.models.DocumentPayment;
import ec.com.flashpi.models.DocumentStatus;
import ec.com.flashpi.models.DocumentTax;
import ec.com.flashpi.models.DocumentType;
import ec.com.flashpi.models.DocumentsListParams;
import ec.com.flashpi.models.Environment;
import ec.com.flashpi.models.Event;
import ec.com.flashpi.models.EventsListParams;
import ec.com.flashpi.models.IdType;
import ec.com.flashpi.models.PaymentMethod;
import ec.com.flashpi.models.StatusStatus;
import ec.com.flashpi.models.TaxType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * The SDK against the real Flashpi API in test mode (SRI pruebas), port of the Node SDK's
 * test/live/live.test.ts:
 *
 * <pre>./mvnw verify -Plive</pre>
 *
 * <p>Needs FLASHPI_TEST_API_KEY (an fp_test_ key with read_write access) and FLASHPI_DEMO_ISSUER_ID
 * (the demo issuer's id, on the console's Overview); FLASHPI_BASE_URL may point to another API.
 * Without them the run fails, so a misconfigured release check is seen. It issues ONE real test
 * invoice on the shared demo issuer, which counts toward its daily quota of 50, so CI only runs it
 * while a release is being prepared. It never accepts an fp_live_ key.
 *
 * <p>The tests run in order and share the invoice.
 */
@Tag("live")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LiveTest {
  private static final String API_KEY = System.getenv().getOrDefault("FLASHPI_TEST_API_KEY", "");
  private static final String ISSUER_ID =
      System.getenv().getOrDefault("FLASHPI_DEMO_ISSUER_ID", "");
  private static final String BASE_URL = System.getenv("FLASHPI_BASE_URL");
  private static final String IDEMPOTENCY_KEY =
      "sdk-live-java-"
          + Long.toHexString(System.currentTimeMillis() / 1000)
          + "-"
          + UUID.randomUUID();

  /** Checks of a slow SRI step, 5 seconds apart: up to 2 minutes. */
  private static final int POLLS = 24;

  private static Flashpi flashpi;
  private static Document invoice;

  @BeforeAll
  static void connect() {
    if (API_KEY.isEmpty() || ISSUER_ID.isEmpty()) {
      throw new IllegalStateException("Set FLASHPI_TEST_API_KEY and FLASHPI_DEMO_ISSUER_ID");
    }
    if (!API_KEY.startsWith("fp_test_")) {
      throw new IllegalStateException(
          "FLASHPI_TEST_API_KEY must be an fp_test_ key: this suite never runs live");
    }
    Flashpi.Builder builder = Flashpi.builder().apiKey(API_KEY);
    if (BASE_URL != null && !BASE_URL.isEmpty()) {
      builder.baseUrl(BASE_URL);
    }
    flashpi = builder.build();
  }

  @AfterAll
  static void close() {
    if (flashpi != null) {
      flashpi.close();
    }
  }

  /** The same test invoice as the docs' quickstart: USD 1.00 + 15 % VAT to the SRI's test RUC. */
  private static CreateInvoice sampleInvoice() {
    return CreateInvoice.builder()
        .issuerId(ISSUER_ID)
        .establishment("001")
        .emissionPoint("001")
        .customer(
            DocumentParty.builder()
                .idType(IdType.RUC)
                .id("1760013210001")
                .name("PRUEBAS SERVICIO DE RENTAS INTERNAS")
                .address("AMAZONAS AVENUE AND VILLALENGUA, QUITO")
                .build())
        .items(
            List.of(
                DocumentItem.builder()
                    .code("SDK-LIVE-001")
                    .description("flashpi-java live test")
                    .quantity("1")
                    .unitPrice("1.00")
                    .taxes(List.of(DocumentTax.builder().type(TaxType.VAT).rate("15").build()))
                    .build()))
        .payments(
            List.of(DocumentPayment.builder().method(PaymentMethod.CASH).amount("1.15").build()))
        .options(
            DocumentOptions.builder()
                .waitForAuthorization(true)
                .waitTimeoutSeconds(30)
                .sendEmail(false)
                .build())
        .build();
  }

  private static Document invoice() {
    assertNotNull(
        invoice, "The test invoice was not issued: see issuesATestInvoiceAndWaitsForTheSri");
    return invoice;
  }

  @Test
  @Order(1)
  void readsTheApiStatus() {
    assertEquals(StatusStatus.OPERATIONAL, flashpi.status().get().getStatus());
  }

  @Test
  @Order(2)
  void rejectsAnInvalidInvoiceWithFieldErrorsInSpanish() {
    CreateInvoice body = sampleInvoice().toBuilder().items(List.of()).build();
    UnprocessableEntityException error =
        assertThrows(
            UnprocessableEntityException.class,
            () ->
                flashpi
                    .invoices()
                    .create(
                        body,
                        RequestOptions.builder().language(Language.ES).maxRetries(0).build()));
    assertEquals("validation_failed", error.getCode());
    assertFalse(error.getErrors().isEmpty());
    assertTrue(error.getRequestId().matches("[0-9a-f-]{36}"), error.getRequestId());
    assertEquals("es", error.getHeaders().firstValue("content-language").orElse(null));
  }

  @Test
  @Order(3)
  void issuesATestInvoiceAndWaitsForTheSri() throws InterruptedException {
    ApiResponse<Document> result =
        flashpi
            .invoices()
            .withResponse()
            .create(
                sampleInvoice(),
                RequestOptions.builder()
                    .idempotencyKey(IDEMPOTENCY_KEY)
                    .correlationId("flashpi-java-live")
                    .build());
    assertTrue(result.getStatusCode() == 200 || result.getStatusCode() == 202);
    assertNotNull(result.getRequestId());
    assertEquals(DocumentType.INVOICE, result.getData().getType());
    assertEquals(Environment.TEST, result.getData().getEnvironment());
    Document document = result.getData();
    // The SRI's test environment can be slow: follow the document for up to 2 minutes.
    for (int poll = 0; poll < POLLS; poll++) {
      if (document.getStatus() != DocumentStatus.PROCESSING
          && document.getStatus() != DocumentStatus.PENDING_SRI) {
        break;
      }
      Thread.sleep(5_000);
      document = flashpi.documents().get(document.getId());
    }
    assertEquals(DocumentStatus.AUTHORIZED, document.getStatus());
    assertTrue(document.getAccessKey().matches("\\d{49}"));
    invoice = document;
  }

  @Test
  @Order(4)
  void replaysTheSameIdempotencyKeyInsteadOfIssuingAgain() {
    ApiResponse<Document> result =
        flashpi
            .invoices()
            .withResponse()
            .create(
                sampleInvoice(), RequestOptions.builder().idempotencyKey(IDEMPOTENCY_KEY).build());
    assertEquals(invoice().getId(), result.getData().getId());
    assertEquals("true", result.getHeaders().firstValue("idempotent-replayed").orElse(null));
  }

  @Test
  @Order(5)
  void findsTheDocumentByItsAccessKey() {
    Page<Document> page =
        flashpi
            .documents()
            .list(
                DocumentsListParams.builder().accessKey(invoice().getAccessKey()).limit(5).build());
    assertEquals(
        List.of(invoice().getId()),
        page.getData().stream().map(Document::getId).collect(Collectors.toList()));
  }

  @Test
  @Order(6)
  void downloadsTheAuthorizedXml() {
    DocumentXml xml = flashpi.documents().xml(invoice().getId());
    assertEquals(DocumentXml.Kind.AUTHORIZED, xml.getKind());
    assertTrue(xml.getXml().contains(invoice().getAccessKey()));
  }

  @Test
  @Order(7)
  void downloadsTheRideOnceItIsGenerated() throws InterruptedException {
    byte[] pdf = null;
    for (int poll = 0; poll < POLLS && pdf == null; poll++) {
      try {
        pdf = flashpi.documents().pdf(invoice().getId());
      } catch (ConflictException error) {
        if (!"ride_not_available".equals(error.getCode())) {
          throw error;
        }
        Thread.sleep(5_000);
      }
    }
    assertNotNull(pdf);
    assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"));
  }

  @Test
  @Order(8)
  void listsTheEventsOfTheDocument() throws InterruptedException {
    List<String> types = new ArrayList<>();
    for (int poll = 0; poll < 12 && !types.contains("invoice.authorized"); poll++) {
      if (poll > 0) {
        Thread.sleep(5_000);
      }
      types.clear();
      for (Event event :
          flashpi.events().list(EventsListParams.builder().documentId(invoice().getId()).build())) {
        DocumentEvent documentEvent = assertInstanceOf(DocumentEvent.class, event);
        types.add(documentEvent.getType().value());
        assertEquals(invoice().getId(), documentEvent.getData().getId());
      }
    }
    assertTrue(types.contains("invoice.authorized"), types.toString());
  }

  @Test
  @Order(9)
  void answers404ForADocumentThatDoesNotExist() {
    assertThrows(
        NotFoundException.class,
        () ->
            flashpi
                .documents()
                .get(
                    "01a00000-0000-7000-8000-000000000000",
                    RequestOptions.builder().maxRetries(0).build()));
  }
}
