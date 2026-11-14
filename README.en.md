# Flashpi for Java

The official SDK of [Flashpi](https://flashpi.com.ec), the electronic invoicing API for Ecuador's
SRI, for Java and Kotlin.

[Español](https://github.com/flashpi1/flashpi-java/blob/main/README.md)

> Not on Maven Central yet: the first version will be 0.1.0.

- The API's 45 operations: the six document types, documents (XML, RIDE PDF, email), issuers with
  their establishments, emission points and .p12 certificates, webhooks and events.
- Models generated from the API's OpenAPI document, with typed getters and builders
  (`CreateInvoice.builder()`), enums and `java.time` dates: your IDE knows every field.
- Safe retries: every document you issue carries an `Idempotency-Key`, so a retry never issues it
  twice.
- Typed exceptions (RFC 9457) with their `code`, field errors and `requestId`.
- Automatic pagination with `for` and `Stream`.
- Webhook signature verification (Standard Webhooks) for Spring Boot, servlets or any framework.
- Java 11 or later, and Kotlin. One dependency: Jackson 2 (`jackson-databind`, 2.13 or later),
  which Spring Boot 2.7 and 3 already bring. With Spring Boot 4, which uses Jackson 3, the SDK
  brings its own Jackson 2, which lives alongside it (see [Spring Boot 4](#spring-boot-4)).

## Installation

Maven:

```xml
<dependency>
  <groupId>ec.com.flashpi</groupId>
  <artifactId>flashpi-java</artifactId>
  <version>0.1.0</version> <!-- x-release-please-version -->
</dependency>
```

Gradle:

```kotlin
implementation("ec.com.flashpi:flashpi-java:0.1.0") // x-release-please-version
```

## Getting started

Create an API key in the [console](https://flashpi.com.ec/app/) and keep it in the
`FLASHPI_API_KEY` environment variable. An `fp_test_` key issues in the SRI test environment
(pruebas); an `fp_live_` key issues real documents in production.

```java
import ec.com.flashpi.Flashpi;
import ec.com.flashpi.models.CreateInvoice;
import ec.com.flashpi.models.Document;
import ec.com.flashpi.models.DocumentItem;
import ec.com.flashpi.models.DocumentOptions;
import ec.com.flashpi.models.DocumentParty;
import ec.com.flashpi.models.DocumentPayment;
import ec.com.flashpi.models.DocumentTax;
import ec.com.flashpi.models.IdType;
import ec.com.flashpi.models.PaymentMethod;
import ec.com.flashpi.models.TaxType;
import java.util.List;

// Reads FLASHPI_API_KEY; you can also pass it: new Flashpi("fp_test_...")
Flashpi flashpi = new Flashpi();

Document invoice =
    flashpi
        .invoices()
        .create(
            CreateInvoice.builder()
                .issuerId("<the demo issuer's id, or yours>")
                .establishment("001")
                .emissionPoint("001")
                .customer(
                    DocumentParty.builder()
                        .idType(IdType.RUC)
                        .id("1760013210001")
                        .name("PRUEBAS SERVICIO DE RENTAS INTERNAS")
                        .address("Av. Amazonas y Villalengua, Quito")
                        .build())
                .items(
                    List.of(
                        DocumentItem.builder()
                            .code("P-001")
                            .description("Test service")
                            .quantity("1")
                            .unitPrice("1.00")
                            .taxes(List.of(DocumentTax.builder().type(TaxType.VAT).rate("15").build()))
                            .build()))
                .payments(
                    List.of(DocumentPayment.builder().method(PaymentMethod.CASH).amount("1.15").build()))
                .options(DocumentOptions.builder().waitForAuthorization(true).build())
                .build());

System.out.println(invoice.getStatus() + " " + invoice.getAccessKey());
```

The models are in `ec.com.flashpi.models` and use the API's names (`getAccessKey()`,
`issuerId(...)`). Amounts and quantities are **strings** (`"1.15"`), never numbers, so no precision
is lost; dates are `LocalDate` and `OffsetDateTime`. A value this version of the SDK does not know
yet reads as `UNKNOWN` in the enums, and a new field is kept in `getAdditionalProperties()`.

The models are read and written by any Jackson 2 `ObjectMapper`, with no extra module.

With the **demo issuer** you can issue in test mode without uploading a certificate: its id is on
the console's Overview (up to 50 documents a day). More in
[Sandbox and demo issuer](https://flashpi.com.ec/docs/sandbox/).

A client is thread-safe and keeps a pool of connections: create one and share it (for example as
a Spring bean). Close it with `flashpi.close()` or use it in a
`try (Flashpi flashpi = new Flashpi()) { ... }`.

## Kotlin

The same SDK works from Kotlin: getters read as properties and enums work with `when`.

```kotlin
import ec.com.flashpi.Flashpi
import ec.com.flashpi.models.CreateInvoice
import ec.com.flashpi.models.DocumentItem
import ec.com.flashpi.models.DocumentOptions
import ec.com.flashpi.models.DocumentParty
import ec.com.flashpi.models.DocumentPayment
import ec.com.flashpi.models.DocumentStatus
import ec.com.flashpi.models.DocumentTax
import ec.com.flashpi.models.IdType
import ec.com.flashpi.models.PaymentMethod
import ec.com.flashpi.models.TaxType

fun main() {
    Flashpi().use { flashpi ->
        val invoice = flashpi.invoices().create(
            CreateInvoice.builder()
                .issuerId(System.getenv("FLASHPI_ISSUER_ID"))
                .establishment("001")
                .emissionPoint("001")
                .customer(DocumentParty.builder().idType(IdType.FINAL_CONSUMER).build())
                .items(
                    listOf(
                        DocumentItem.builder()
                            .code("P-001")
                            .description("Test service")
                            .quantity("1")
                            .unitPrice("1.00")
                            .taxes(listOf(DocumentTax.builder().type(TaxType.VAT).rate("15").build()))
                            .build(),
                    ),
                )
                .payments(listOf(DocumentPayment.builder().method(PaymentMethod.CASH).amount("1.15").build()))
                .options(DocumentOptions.builder().waitForAuthorization(true).build())
                .build(),
        )

        when (invoice.status) {
            DocumentStatus.AUTHORIZED -> println("Authorized: ${invoice.accessKey}")
            DocumentStatus.REJECTED -> println("Rejected: ${invoice.sriMessages}")
            else -> println("Status: ${invoice.status}")
        }

        for (document in flashpi.documents().list()) {
            println(document.accessKey)
        }
    }
}
```

## Issuing documents

| Document                                     | Method                                       |
| -------------------------------------------- | -------------------------------------------- |
| Invoice (factura)                            | `flashpi.invoices().create(body)`            |
| Credit note (nota de crédito)                | `flashpi.creditNotes().create(body)`         |
| Debit note (nota de débito)                  | `flashpi.debitNotes().create(body)`          |
| Purchase settlement (liquidación de compra)  | `flashpi.purchaseSettlements().create(body)` |
| Waybill (guía de remisión)                   | `flashpi.waybills().create(body)`            |
| Withholding receipt (retención)              | `flashpi.withholdings().create(body)`        |

All of them return the document (`Document`). By default the API answers right away with the
status `PROCESSING`, and the SRI's outcome arrives by webhook. With
`DocumentOptions.builder().waitForAuthorization(true)` the API waits for the SRI (15 s by
default, up to 30 s with `waitTimeoutSeconds`) and returns `AUTHORIZED`, `REJECTED` or `FAILED`;
if the SRI takes longer, it returns `PROCESSING` or `PENDING_SRI` and processing continues in the
background.

## Documents

```java
import ec.com.flashpi.DocumentXml;
import ec.com.flashpi.models.Document;
import java.nio.file.Files;
import java.nio.file.Path;

Document document = flashpi.documents().get(invoice.getId());

DocumentXml xml = flashpi.documents().xml(invoice.getId()); // getKind(): AUTHORIZED once the SRI authorized it
byte[] pdf = flashpi.documents().pdf(invoice.getId()); // the RIDE
Files.write(Path.of(invoice.getAccessKey() + ".pdf"), pdf);

flashpi.documents().sendEmail(invoice.getId()); // emails the RIDE and the XML to the document's address
```

## Lists and pagination

A list returns a page. A `for` loop (or `stream()`) walks every page, fetching them as you go:

```java
import ec.com.flashpi.Page;
import ec.com.flashpi.models.Document;
import ec.com.flashpi.models.DocumentStatus;
import ec.com.flashpi.models.DocumentsListParams;
import java.time.LocalDate;

DocumentsListParams authorized =
    DocumentsListParams.builder()
        .status(DocumentStatus.AUTHORIZED)
        .issueDateFrom(LocalDate.of(2026, 11, 1))
        .build();
for (Document document : flashpi.documents().list(authorized)) {
  System.out.println(document.getAccessKey());
}

Page<Document> page = flashpi.documents().list(DocumentsListParams.builder().limit(50).build());
System.out.println(page.getData().size()); // the documents of this page
if (page.hasNextPage()) {
  page = page.nextPage();
}
```

## Errors

Every API error is an `ApiException`, with a subclass for each common HTTP status; all of them are
unchecked exceptions. Branch on `getCode()`, which is stable and in English; the message follows the
requested language.

```java
import ec.com.flashpi.exceptions.ApiException;
import ec.com.flashpi.exceptions.UnprocessableEntityException;
import ec.com.flashpi.models.FieldError;

try {
  flashpi.invoices().create(body);
} catch (UnprocessableEntityException error) {
  System.out.println(error.getCode()); // "validation_failed", "total_mismatch", ...
  for (FieldError field : error.getErrors()) {
    System.out.println(field.getField() + ": " + field.getMessage());
  }
  throw error;
} catch (ApiException error) {
  // Share getRequestId() with support.
  System.out.println(error.getStatus() + " " + error.getCode() + " " + error.getRequestId());
  throw error;
}
```

| Class                          | Status | Examples of `code`                                          |
| ------------------------------ | ------ | ----------------------------------------------------------- |
| `BadRequestException`          | 400    | `malformed_json`, `invalid_idempotency_key`                 |
| `AuthenticationException`      | 401    | `authentication_required`, `invalid_api_key`                |
| `PermissionDeniedException`    | 403    | `key_read_only`, `key_scope_mismatch`, `ip_not_allowed`     |
| `NotFoundException`            | 404    | `not_found`                                                 |
| `ConflictException`            | 409    | `sequential_already_used`, `ride_not_available`             |
| `UnprocessableEntityException` | 422    | `validation_failed`, `total_mismatch`                       |
| `RateLimitException`           | 429    | `demo_issuer_quota_exceeded`, `account_email_limit_reached` |
| `InternalServerException`      | 5xx    | `internal_error`, `demo_issuer_unavailable`                 |
| `ApiException`                 | others | `payload_too_large` (413), `unsupported_media_type` (415)   |
| `ApiConnectionException`       | —      | no answer (network); `ApiTimeoutException` when it timed out |

All of them are in `ec.com.flashpi.exceptions` and extend `FlashpiException`. Some errors carry
extra data in `getProblem()`, for example `getDocumentId()`, `getLimit()`, `getResetsAt()` or
`getRideStatus()`. For messages in Spanish: `Flashpi.builder().language(Language.ES).build()`.

## Retries and idempotency

The SDK retries network errors, timeouts and 408, 429 and 5xx answers (except 501 and
`demo_issuer_unavailable`) up to 2 times, with exponential backoff. When an answer asks to wait more
than 60 s (`Retry-After`), it is not retried but thrown. Every POST carries an `Idempotency-Key`
that its retries repeat, so the API never issues twice:

- When a retry finds that the first attempt did create the document, the SDK returns that
  document.
- When the first attempt is still running on the API (for example, the connection dropped while
  it waited for the SRI), the SDK waits for it with the same key, without spending retries, while
  less than the `timeout` has passed since the call started; after that the normal retries apply.

To use your own key (for example your order's id), pass it in the call's options:

```java
import ec.com.flashpi.RequestOptions;

flashpi
    .invoices()
    .create(body, RequestOptions.builder().idempotencyKey("order-" + order.getId()).build());
```

If issuing still fails, the error (a `FlashpiException`) carries the key it used in
`getIdempotencyKey()`. Trying again with that key never issues a second document: within 24 hours
the API returns the first answer; after that (or if the API lost the first attempt), the call
fails with a 422 `idempotency_key_reused` whose `getProblem().getDocumentId()` is the document
that already exists (read it with `flashpi.documents().get(...)`).

```java
import ec.com.flashpi.exceptions.FlashpiException;

try {
  flashpi.invoices().create(body);
} catch (FlashpiException error) {
  if (error.getIdempotencyKey() != null) {
    saveForRetry(body, error.getIdempotencyKey()); // your retry queue
  }
  throw error;
}
```

If you interrupt a call's thread, the SDK stops waiting, throws a `FlashpiException` with the key
and preserves the thread's interrupted status.

Daily limits (such as the demo issuer's quota) are not retried: they are thrown as
`RateLimitException` with `getRetryAfter()`.

## Webhooks

Verify every webhook with the body **exactly as it arrived** (not parsed) and the endpoint's
`whsec_` secret. `Webhooks` needs no API key (it is also `flashpi.webhooks()`), does no I/O and is
thread-safe.

With Spring Boot:

```java
import ec.com.flashpi.Webhooks;
import ec.com.flashpi.exceptions.WebhookVerificationException;
import ec.com.flashpi.models.DocumentEvent;
import ec.com.flashpi.models.DocumentEventType;
import ec.com.flashpi.models.WebhookEndpointVerificationEvent;
import ec.com.flashpi.models.WebhookEvent;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FlashpiWebhookController {
  private final Webhooks webhooks = new Webhooks();
  private final String secret = System.getenv("FLASHPI_WEBHOOK_SECRET");

  @PostMapping("/webhooks/flashpi")
  ResponseEntity<?> receive(@RequestBody byte[] body, @RequestHeader Map<String, String> headers) {
    WebhookEvent event;
    try {
      event = webhooks.unwrap(body, headers, secret);
    } catch (WebhookVerificationException error) {
      return ResponseEntity.badRequest().build();
    }

    if (event instanceof WebhookEndpointVerificationEvent) {
      // The endpoint's activation: answer the challenge.
      String challenge = ((WebhookEndpointVerificationEvent) event).getData().getChallenge();
      return ResponseEntity.ok(Map.of("challenge", challenge));
    }
    if (event instanceof DocumentEvent) {
      DocumentEvent document = (DocumentEvent) event; // getData() is the document (null in thin mode)
      if (document.getType() == DocumentEventType.INVOICE_AUTHORIZED) {
        System.out.println("Authorized " + document.getData().getAccessKey());
      } else if (document.getType() == DocumentEventType.INVOICE_REJECTED) {
        System.out.println("Rejected " + document.getData().getSriMessages());
      }
    }
    return ResponseEntity.noContent().build();
  }
}
```

With a servlet, pass the headers as `request::getHeader`:
`webhooks.unwrap(body, request::getHeader, secret)`; with Spring's `HttpHeaders`,
`headers::getFirst`. The headers can also be any `Map` whose values are text or lists of text.

Keep the event's `getId()` and drop repeats: an event can arrive more than once. During a secret
rotation Flashpi signs with the new secret and the previous one, so either works. An event of a
type this version of the SDK does not know yet arrives as an `EventThin`. If the endpoint uses
the `thin` mode (no `data`), fetch the whole event with `flashpi.events().get(event.getId())`.

## Issuers and certificates

These operations need an account-scoped key with read and write access.

An issuer starts from its electronic signature (.p12): Flashpi checks it, reads the RUC and asks
the SRI, and gives you a `prefill` with what it found so you can build the issuer.

```java
import ec.com.flashpi.CertificateUpload;
import ec.com.flashpi.models.CompleteIssuerOnboarding;
import ec.com.flashpi.models.CompletedIssuerOnboarding;
import ec.com.flashpi.models.Establishment;
import ec.com.flashpi.models.IssuerOnboarding;
import ec.com.flashpi.models.IssuerOnboardingPrefill;
import ec.com.flashpi.models.IssuerOnboardingPrefillTaxProfile;
import ec.com.flashpi.models.IssuerOnboardingPrefillTaxProfileRimpeRegime;
import ec.com.flashpi.models.IssuerOnboardingStatus;
import ec.com.flashpi.models.IssuerTaxProfileInput;
import ec.com.flashpi.models.RimpeRegime;
import java.nio.file.Path;

IssuerOnboarding onboarding =
    flashpi
        .issuerOnboardings()
        .create(
            CertificateUpload.builder()
                .file(Path.of("firma.p12"))
                .password(System.getenv("P12_PASSWORD"))
                .build());
while (onboarding.getStatus() == IssuerOnboardingStatus.VERIFYING) {
  Thread.sleep(2_000);
  onboarding = flashpi.issuerOnboardings().get(onboarding.getId());
}
if (onboarding.getStatus() != IssuerOnboardingStatus.READY) {
  throw new IllegalStateException(
      onboarding.getRejection() != null
          ? onboarding.getRejection().getMessage()
          : onboarding.getStatus().toString());
}

CompletedIssuerOnboarding completed;
if (onboarding.getExistingIssuer() != null) {
  // You already have that RUC: the certificate is added to that issuer (a renewal).
  completed = flashpi.issuerOnboardings().complete(onboarding.getId());
} else {
  // A new RUC: you confirm the issuer's data, starting from the prefill.
  IssuerOnboardingPrefill prefill = onboarding.getPrefill();
  IssuerOnboardingPrefillTaxProfile tax = prefill.getTaxProfile();
  IssuerOnboardingPrefillTaxProfileRimpeRegime regime = tax.getRimpeRegime();
  completed =
      flashpi
          .issuerOnboardings()
          .complete(
              onboarding.getId(),
              CompleteIssuerOnboarding.builder()
                  .legalName(prefill.getLegalName() != null ? prefill.getLegalName() : "MY COMPANY S.A.")
                  .headOfficeAddress(
                      prefill.getHeadOfficeAddress() != null
                          ? prefill.getHeadOfficeAddress()
                          : "Av. Amazonas N24-100, Quito")
                  .taxProfile(
                      IssuerTaxProfileInput.builder()
                          .keepsAccounting(Boolean.TRUE.equals(tax.getKeepsAccounting()))
                          // For a special taxpayer or a withholding agent, add its resolutions.
                          .rimpeRegime(
                              regime == null || regime == IssuerOnboardingPrefillTaxProfileRimpeRegime.GENERAL
                                  ? null
                                  : RimpeRegime.fromValue(regime.value()))
                          .build())
                  // If onboarding.getRucNeedsConfirmation() is true, send ruc(...) too.
                  .build());
}

for (Establishment establishment :
    flashpi.issuers().establishments().list(completed.getIssuer().getId())) {
  System.out.println(establishment.getCode());
}
```

The .p12 can come from a `Path`, an `InputStream` or its bytes, up to 1 MiB; the SDK reads it once
and sends the same bytes on every retry.

To **renew an issuer's signature**, for test and live at once, run the same flow with the new
.p12: Flashpi recognizes the RUC (`getExistingIssuer()`) and `complete()` without a body adds the
certificate. `flashpi.issuers().certificates().upload(issuerId, upload)` instead uploads a
certificate only for the mode of the key you use (test or live).

## Options

```java
import ec.com.flashpi.Flashpi;
import ec.com.flashpi.Language;
import java.time.Duration;

Flashpi flashpi =
    Flashpi.builder()
        .apiKey(System.getenv("FLASHPI_API_KEY")) // by default, the FLASHPI_API_KEY variable
        .language(Language.ES) // the messages' language: EN (default) or ES
        .timeout(Duration.ofSeconds(60)) // per attempt (with waitForAuthorization, at least the wait + 15 s)
        .maxRetries(2) // 0 turns the retries off
        .build();
```

Every method also takes, as its last argument, options of that request: `idempotencyKey`,
`timeout`, `maxRetries`, `language`, `correlationId` (your own id, which Flashpi logs) and extra
headers (ASCII only).

```java
import ec.com.flashpi.Language;
import ec.com.flashpi.RequestOptions;
import ec.com.flashpi.models.Document;
import java.time.Duration;

Document document =
    flashpi
        .documents()
        .get(
            documentId,
            RequestOptions.builder()
                .timeout(Duration.ofSeconds(10))
                .language(Language.ES)
                .correlationId("order-42")
                .build());
```

To see the HTTP response and the request id, call the method through `withResponse()`:

```java
import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.models.Document;

ApiResponse<Document> result = flashpi.documents().withResponse().get(documentId);
System.out.println(result.getData().getStatus() + " " + result.getStatusCode() + " " + result.getRequestId());
```

To send the requests with your own `java.net.http.HttpClient` (a proxy, another `SSLContext`,
your executor), pass it to `httpClient(...)`: it must not follow redirects, and you close it.

```java
import ec.com.flashpi.Flashpi;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;

Flashpi flashpi =
    Flashpi.builder()
        .httpClient(
            HttpClient.newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress("proxy.internal", 3128)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build())
        .build();
```

## Spring Boot 4

The SDK works with Spring Boot 2.7, 3 and 4. Spring Boot 4 uses Jackson 3, whose `ObjectMapper`
does not know the SDK's models (they are made for Jackson 2): do not take a model as a
`@RequestBody` or return one as it is from a controller. Convert it with a Jackson 2 `ObjectMapper`
(`new ObjectMapper().writeValueAsString(document)`), or turn on Spring Boot 4's Jackson 2 support
(`spring-boot-jackson2`).

## Servers only

Your API key is a secret: use the SDK on your servers, never in an Android app or in code that
runs on your users' devices. `flashpi.toString()` shows the environment and the URL, never the
key, and no exception carries it.

## License

[MIT](https://github.com/flashpi1/flashpi-java/blob/main/LICENSE) © 2026 SIGNIADIGITAL S.A.
