# Flashpi para Java

SDK oficial de [Flashpi](https://flashpi.com.ec), la API de facturación electrónica del SRI de
Ecuador, para Java y Kotlin.

[English](https://github.com/flashpi1/flashpi-java/blob/main/README.en.md)

> Aún no está publicado en Maven Central: la primera versión será la 0.1.0.

- Las 45 operaciones de la API: los seis comprobantes, documentos (XML, RIDE en PDF, correo),
  emisores con establecimientos, puntos de emisión y certificados .p12, webhooks y eventos.
- Modelos generados del documento OpenAPI de la API, con getters tipados y builders
  (`CreateInvoice.builder()`), enums y fechas de `java.time`: tu IDE conoce cada campo.
- Reintentos seguros: cada emisión lleva un `Idempotency-Key`, así un reintento nunca emite dos
  veces.
- Excepciones tipadas (RFC 9457) con su `code`, los errores por campo y el `requestId`.
- Paginación automática con `for` y `Stream`.
- Verificación de firmas de webhooks (Standard Webhooks) para Spring Boot, servlets o cualquier
  framework.
- Java 11 o superior, y Kotlin. Una sola dependencia: Jackson 2 (`jackson-databind`, desde la
  2.13), que Spring Boot ya trae.

## Instalación

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

## Primeros pasos

Crea una clave de API en la [consola](https://flashpi.com.ec/app/) y guárdala en la variable de
entorno `FLASHPI_API_KEY`. Una clave `fp_test_` emite en el ambiente de pruebas del SRI; una
`fp_live_` emite comprobantes reales en producción.

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

// Lee FLASHPI_API_KEY; también puedes pasarla: new Flashpi("fp_test_...")
Flashpi flashpi = new Flashpi();

Document invoice =
    flashpi
        .invoices()
        .create(
            CreateInvoice.builder()
                .issuerId("<id del emisor demo o del tuyo>")
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
                            .description("Servicio de prueba")
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

Los modelos están en `ec.com.flashpi.models` y usan los nombres de la API (`getAccessKey()`,
`issuerId(...)`). Los montos y cantidades son **textos** (`"1.15"`), nunca números, para no
perder decimales; las fechas son `LocalDate` y `OffsetDateTime`. Un valor que esta versión del SDK
aún no conoce se lee como `UNKNOWN` en los enums, y un campo nuevo queda en
`getAdditionalProperties()`.

Con el **emisor demo** puedes emitir en pruebas sin subir un certificado: su id está en el
Resumen de la consola (hasta 50 comprobantes al día). Más en
[Sandbox y emisor demo](https://flashpi.com.ec/docs/sandbox/).

Un cliente es seguro entre hilos y mantiene un grupo de conexiones: crea uno y compártelo (por
ejemplo como un bean de Spring). Ciérralo con `flashpi.close()` o úsalo en un
`try (Flashpi flashpi = new Flashpi()) { ... }`.

## Kotlin

El mismo SDK sirve en Kotlin: los getters se leen como propiedades y los enums funcionan con
`when`.

```kotlin
import ec.com.flashpi.Flashpi
import ec.com.flashpi.models.CreateInvoice
import ec.com.flashpi.models.DocumentItem
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
                            .description("Servicio de prueba")
                            .quantity("1")
                            .unitPrice("1.00")
                            .taxes(listOf(DocumentTax.builder().type(TaxType.VAT).rate("15").build()))
                            .build(),
                    ),
                )
                .payments(listOf(DocumentPayment.builder().method(PaymentMethod.CASH).amount("1.15").build()))
                .build(),
        )

        when (invoice.status) {
            DocumentStatus.AUTHORIZED -> println("Autorizada: ${invoice.accessKey}")
            DocumentStatus.REJECTED -> println("Rechazada: ${invoice.sriMessages}")
            else -> println("Estado: ${invoice.status}")
        }

        for (document in flashpi.documents().list()) {
            println(document.accessKey)
        }
    }
}
```

## Emitir comprobantes

| Comprobante           | Método                                         |
| --------------------- | ---------------------------------------------- |
| Factura               | `flashpi.invoices().create(body)`              |
| Nota de crédito       | `flashpi.creditNotes().create(body)`           |
| Nota de débito        | `flashpi.debitNotes().create(body)`            |
| Liquidación de compra | `flashpi.purchaseSettlements().create(body)`   |
| Guía de remisión      | `flashpi.waybills().create(body)`              |
| Retención             | `flashpi.withholdings().create(body)`          |

Todos devuelven el documento (`Document`). Por defecto la respuesta llega enseguida con el
estado `PROCESSING` y el resultado del SRI llega por webhook. Con
`DocumentOptions.builder().waitForAuthorization(true)` la API espera la respuesta del SRI (15 s
por defecto, hasta 30 s con `waitTimeoutSeconds`) y devuelve `AUTHORIZED`, `REJECTED` o
`FAILED`; si el SRI tarda más, devuelve `PROCESSING` o `PENDING_SRI` y el documento sigue su
curso.

## Documentos

```java
import ec.com.flashpi.DocumentXml;
import ec.com.flashpi.models.Document;
import java.nio.file.Files;
import java.nio.file.Path;

Document document = flashpi.documents().get(invoice.getId());

DocumentXml xml = flashpi.documents().xml(invoice.getId()); // getKind(): AUTHORIZED cuando ya lo autorizó el SRI
byte[] pdf = flashpi.documents().pdf(invoice.getId()); // el RIDE
Files.write(Path.of(invoice.getAccessKey() + ".pdf"), pdf);

flashpi.documents().sendEmail(invoice.getId()); // reenvía RIDE y XML al correo del comprador
```

## Listas y paginación

Una lista devuelve una página. Con `for` (o `stream()`) recorres todas las páginas, que se piden
a medida que avanzas:

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
System.out.println(page.getData().size()); // los documentos de esta página
if (page.hasNextPage()) {
  page = page.nextPage();
}
```

## Errores

Cada error de la API es una subclase de `ApiException` según su estado HTTP; todas son
excepciones no verificadas. Decide según `getCode()`, que es estable y en inglés; el mensaje
sigue el idioma pedido.

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
  // Comparte getRequestId() con soporte.
  System.out.println(error.getStatus() + " " + error.getCode() + " " + error.getRequestId());
  throw error;
}
```

| Clase                          | Estado | Ejemplos de `code`                                          |
| ------------------------------ | ------ | ----------------------------------------------------------- |
| `BadRequestException`          | 400    | `malformed_json`, `invalid_idempotency_key`                 |
| `AuthenticationException`      | 401    | `authentication_required`, `invalid_api_key`                |
| `PermissionDeniedException`    | 403    | `key_read_only`, `key_scope_mismatch`, `ip_not_allowed`     |
| `NotFoundException`            | 404    | `not_found`                                                 |
| `ConflictException`            | 409    | `sequential_already_used`, `ride_not_available`             |
| `UnprocessableEntityException` | 422    | `validation_failed`, `total_mismatch`                       |
| `RateLimitException`           | 429    | `demo_issuer_quota_exceeded`, `account_email_limit_reached` |
| `InternalServerException`      | 5xx    | `internal_error`                                            |
| `ApiConnectionException`       | —      | sin respuesta (red); `ApiTimeoutException` si se agotó el plazo |

Todas están en `ec.com.flashpi.exceptions` y derivan de `FlashpiException`. Algunos errores traen
datos extra en `getProblem()`, por ejemplo `getDocumentId()`, `getLimit()`, `getResetsAt()` o
`getRideStatus()`. Para recibir los mensajes en español:
`Flashpi.builder().language(Language.ES).build()`.

## Reintentos e idempotencia

El SDK reintenta hasta 2 veces, con espera creciente, los errores de red, los tiempos agotados,
las respuestas 408, 429 (si piden esperar 60 s o menos) y 5xx. Cada POST lleva un
`Idempotency-Key` que se repite en sus reintentos, así que la API nunca emite dos veces:

- Si un reintento descubre que el primer intento sí creó el documento, el SDK devuelve ese
  documento.
- Si el primer intento sigue en curso en la API (por ejemplo, se cortó la conexión mientras
  esperaba al SRI), el SDK espera a que termine con la misma clave, hasta el tiempo límite de la
  llamada, sin gastar reintentos.

Para usar tu propia clave (por ejemplo el id de tu pedido), pásala en las opciones de la llamada:

```java
import ec.com.flashpi.RequestOptions;

flashpi
    .invoices()
    .create(body, RequestOptions.builder().idempotencyKey("pedido-" + order.getId()).build());
```

Si una emisión falla igual, el error (una `FlashpiException`) trae la clave que usó en
`getIdempotencyKey()`. Reintentar con esa clave nunca emite un segundo documento: dentro de 24
horas la API devuelve la primera respuesta; después (o si la API perdió el primer intento), la
llamada falla con un 422 `idempotency_key_reused` cuyo `getProblem().getDocumentId()` es el
documento que ya existe (léelo con `flashpi.documents().get(...)`).

```java
import ec.com.flashpi.exceptions.FlashpiException;

try {
  flashpi.invoices().create(body);
} catch (FlashpiException error) {
  if (error.getIdempotencyKey() != null) {
    saveForRetry(body, error.getIdempotencyKey()); // tu cola de reintentos
  }
  throw error;
}
```

Si interrumpes el hilo de una llamada, el SDK deja de esperar, lanza una `FlashpiException` con
la clave y conserva la interrupción del hilo.

Los límites diarios (como la cuota del emisor demo) no se reintentan: se lanzan como
`RateLimitException` con `getRetryAfter()`.

## Webhooks

Verifica cada webhook con el cuerpo **tal cual llegó** (sin parsear) y el secreto `whsec_` del
endpoint. `Webhooks` no necesita clave de API (también está en `flashpi.webhooks()`), no hace
E/S y es seguro entre hilos.

Con Spring Boot:

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
      // La activación del endpoint: responde el desafío.
      String challenge = ((WebhookEndpointVerificationEvent) event).getData().getChallenge();
      return ResponseEntity.ok(Map.of("challenge", challenge));
    }
    if (event instanceof DocumentEvent) {
      DocumentEvent document = (DocumentEvent) event; // getData() es el documento
      if (document.getType() == DocumentEventType.INVOICE_AUTHORIZED) {
        System.out.println("Autorizada " + document.getData().getAccessKey());
      } else if (document.getType() == DocumentEventType.INVOICE_REJECTED) {
        System.out.println("Rechazada " + document.getData().getSriMessages());
      }
    }
    return ResponseEntity.noContent().build();
  }
}
```

Con un servlet, pasa los encabezados como `request::getHeader`:
`webhooks.unwrap(body, request::getHeader, secret)`. Los encabezados también pueden ser cualquier
`Map` con valores de texto o listas de textos.

Guarda el `getId()` del evento y descarta los repetidos: un evento puede llegar más de una vez.
Durante una rotación del secreto, Flashpi firma con el nuevo y el anterior, así que cualquiera de
los dos sirve. Un evento de un tipo que esta versión del SDK aún no conoce llega como
`EventThin`. Si el endpoint usa el modo `thin` (sin `data`), pide el evento completo con
`flashpi.events().get(event.getId())`.

## Emisores y certificados

Estas operaciones necesitan una clave de toda la cuenta con acceso de lectura y escritura.

Un emisor nace de su firma electrónica (.p12): Flashpi la verifica, lee el RUC y consulta al SRI,
y te devuelve un `prefill` con lo que encontró para que armes el emisor.

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
  // Ya tienes ese RUC: el certificado se agrega a ese emisor (una renovación).
  completed = flashpi.issuerOnboardings().complete(onboarding.getId());
} else {
  // Un RUC nuevo: tú confirmas los datos del emisor, partiendo del prefill.
  IssuerOnboardingPrefill prefill = onboarding.getPrefill();
  IssuerOnboardingPrefillTaxProfile tax = prefill.getTaxProfile();
  IssuerOnboardingPrefillTaxProfileRimpeRegime regime = tax.getRimpeRegime();
  completed =
      flashpi
          .issuerOnboardings()
          .complete(
              onboarding.getId(),
              CompleteIssuerOnboarding.builder()
                  .legalName(prefill.getLegalName() != null ? prefill.getLegalName() : "MI EMPRESA S.A.")
                  .headOfficeAddress(prefill.getHeadOfficeAddress())
                  .taxProfile(
                      IssuerTaxProfileInput.builder()
                          .keepsAccounting(Boolean.TRUE.equals(tax.getKeepsAccounting()))
                          // Si es contribuyente especial o agente de retención, agrega sus resoluciones.
                          .rimpeRegime(
                              regime == null || regime == IssuerOnboardingPrefillTaxProfileRimpeRegime.GENERAL
                                  ? null
                                  : RimpeRegime.fromValue(regime.value()))
                          .build())
                  // Si onboarding.getRucNeedsConfirmation() es true, envía también ruc(...).
                  .build());
}

for (Establishment establishment :
    flashpi.issuers().establishments().list(completed.getIssuer().getId())) {
  System.out.println(establishment.getCode());
}
```

El .p12 puede venir de un `Path`, de un `InputStream` o de sus bytes, de hasta 1 MiB; el SDK lo
lee una vez y lo reenvía igual en cada reintento.

Para **renovar la firma** de un emisor, para pruebas y producción a la vez, repite el mismo
flujo con el .p12 nuevo: Flashpi reconoce el RUC (`getExistingIssuer()`) y `complete()` sin datos
agrega el certificado. En cambio, `flashpi.issuers().certificates().upload(issuerId, upload)`
sube un certificado solo para el modo de la clave que usas (pruebas o producción).

## Opciones

```java
import ec.com.flashpi.Flashpi;
import ec.com.flashpi.Language;
import java.time.Duration;

Flashpi flashpi =
    Flashpi.builder()
        .apiKey(System.getenv("FLASHPI_API_KEY")) // por defecto, la variable FLASHPI_API_KEY
        .language(Language.ES) // idioma de los mensajes: EN (por defecto) o ES
        .timeout(Duration.ofSeconds(60)) // por intento (con waitForAuthorization, al menos la espera + 15 s)
        .maxRetries(2) // 0 desactiva los reintentos
        .build();
```

Cada método acepta además, como último argumento, opciones de esa petición: `idempotencyKey`,
`timeout`, `maxRetries`, `language`, `correlationId` (tu propio id, que Flashpi registra) y
encabezados extra (solo ASCII).

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
                .correlationId("pedido-42")
                .build());
```

Para ver la respuesta HTTP y el id de la petición, llama al método a través de `withResponse()`:

```java
import ec.com.flashpi.ApiResponse;
import ec.com.flashpi.models.Document;

ApiResponse<Document> result = flashpi.documents().withResponse().get(documentId);
System.out.println(result.getData().getStatus() + " " + result.getStatusCode() + " " + result.getRequestId());
```

Para enviar las peticiones con tu propio `java.net.http.HttpClient` (un proxy, otro
`SSLContext`, tu executor), pásalo en `httpClient(...)`: no debe seguir redirecciones, y tú lo
cierras.

```java
import ec.com.flashpi.Flashpi;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;

Flashpi flashpi =
    Flashpi.builder()
        .httpClient(
            HttpClient.newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress("proxy.interno", 3128)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build())
        .build();
```

## Solo en el servidor

Tu clave de API es secreta: usa el SDK en tus servidores, nunca en una app de Android ni en
código que corre en los dispositivos de tus usuarios. `flashpi.toString()` muestra el ambiente y
la URL, nunca la clave, y ninguna excepción la incluye.

## Licencia

[MIT](https://github.com/flashpi1/flashpi-java/blob/main/LICENSE) © 2026 SIGNIADIGITAL S.A.
