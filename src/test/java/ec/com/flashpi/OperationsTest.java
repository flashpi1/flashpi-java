package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import ec.com.flashpi.FakeHttpClient.Answer;
import ec.com.flashpi.models.CompleteIssuerOnboarding;
import ec.com.flashpi.models.CreateCreditNote;
import ec.com.flashpi.models.CreateDebitNote;
import ec.com.flashpi.models.CreateEmissionPoint;
import ec.com.flashpi.models.CreateEstablishment;
import ec.com.flashpi.models.CreateInvoice;
import ec.com.flashpi.models.CreateIssuer;
import ec.com.flashpi.models.CreatePurchaseSettlement;
import ec.com.flashpi.models.CreateWaybill;
import ec.com.flashpi.models.CreateWebhookEndpoint;
import ec.com.flashpi.models.CreateWithholding;
import ec.com.flashpi.models.RotateWebhookEndpointSecret;
import ec.com.flashpi.models.UpdateEstablishment;
import ec.com.flashpi.models.UpdateIssuer;
import ec.com.flashpi.models.UpdateWebhookEndpoint;
import ec.com.flashpi.resources.DocumentIssuer;
import ec.com.flashpi.resources.Resource;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OperationsTest {
  private static final String A = "id-a";
  private static final String B = "id-b";
  private static final String C = "id-c";
  private static final CertificateUpload UPLOAD = CertificateUpload.of(new byte[] {1, 2, 3}, "pw");

  /** One call of the SDK per operation of the contract. */
  private static Map<String, Consumer<Flashpi>> calls() {
    Map<String, Consumer<Flashpi>> calls = new LinkedHashMap<>();
    calls.put("statusGet", f -> f.status().get());
    calls.put("issuersCreate", f -> f.issuers().create(CreateIssuer.builder().build()));
    calls.put("issuersList", f -> f.issuers().list());
    calls.put("issuersGet", f -> f.issuers().get(A));
    calls.put("issuersUpdate", f -> f.issuers().update(A, UpdateIssuer.builder().build()));
    calls.put("issuersDelete", f -> f.issuers().delete(A));
    calls.put(
        "establishmentsCreate",
        f -> f.issuers().establishments().create(A, CreateEstablishment.builder().build()));
    calls.put("establishmentsList", f -> f.issuers().establishments().list(A));
    calls.put("establishmentsGet", f -> f.issuers().establishments().get(A, B));
    calls.put(
        "establishmentsUpdate",
        f -> f.issuers().establishments().update(A, B, UpdateEstablishment.builder().build()));
    calls.put("establishmentsDelete", f -> f.issuers().establishments().delete(A, B));
    calls.put(
        "emissionPointsCreate",
        f ->
            f.issuers()
                .establishments()
                .emissionPoints()
                .create(A, B, CreateEmissionPoint.builder().build()));
    calls.put("emissionPointsList", f -> f.issuers().establishments().emissionPoints().list(A, B));
    calls.put("emissionPointsGet", f -> f.issuers().establishments().emissionPoints().get(A, B, C));
    calls.put(
        "emissionPointsDelete", f -> f.issuers().establishments().emissionPoints().delete(A, B, C));
    calls.put("certificatesUpload", f -> f.issuers().certificates().upload(A, UPLOAD));
    calls.put("certificatesList", f -> f.issuers().certificates().list(A));
    calls.put("certificatesGet", f -> f.issuers().certificates().get(A, B));
    calls.put("issuerOnboardingsCreate", f -> f.issuerOnboardings().create(UPLOAD));
    calls.put("issuerOnboardingsGet", f -> f.issuerOnboardings().get(A));
    calls.put("issuerOnboardingsComplete", f -> f.issuerOnboardings().complete(A));
    calls.put("invoicesCreate", f -> f.invoices().create(CreateInvoice.builder().build()));
    calls.put("creditNotesCreate", f -> f.creditNotes().create(CreateCreditNote.builder().build()));
    calls.put("debitNotesCreate", f -> f.debitNotes().create(CreateDebitNote.builder().build()));
    calls.put(
        "purchaseSettlementsCreate",
        f -> f.purchaseSettlements().create(CreatePurchaseSettlement.builder().build()));
    calls.put("waybillsCreate", f -> f.waybills().create(CreateWaybill.builder().build()));
    calls.put(
        "withholdingsCreate", f -> f.withholdings().create(CreateWithholding.builder().build()));
    calls.put("documentsList", f -> f.documents().list());
    calls.put("documentsGet", f -> f.documents().get(A));
    calls.put("documentsGetXml", f -> f.documents().xml(A));
    calls.put("documentsGetPdf", f -> f.documents().pdf(A));
    calls.put("documentsSendEmail", f -> f.documents().sendEmail(A));
    calls.put(
        "webhookEndpointsCreate",
        f -> f.webhookEndpoints().create(CreateWebhookEndpoint.builder().build()));
    calls.put("webhookEndpointsList", f -> f.webhookEndpoints().list());
    calls.put("webhookEndpointsGet", f -> f.webhookEndpoints().get(A));
    calls.put(
        "webhookEndpointsUpdate",
        f -> f.webhookEndpoints().update(A, UpdateWebhookEndpoint.builder().build()));
    calls.put("webhookEndpointsDelete", f -> f.webhookEndpoints().delete(A));
    calls.put("webhookEndpointsSecret", f -> f.webhookEndpoints().getSecret(A));
    calls.put("webhookEndpointsRotateSecret", f -> f.webhookEndpoints().rotateSecret(A));
    calls.put("webhookEndpointsVerify", f -> f.webhookEndpoints().verify(A));
    calls.put("webhookEndpointsDeliveriesList", f -> f.webhookEndpoints().deliveries().list(A));
    calls.put("webhookEndpointsDeliveriesGet", f -> f.webhookEndpoints().deliveries().get(A, B));
    calls.put(
        "webhookEndpointsDeliveriesReplay", f -> f.webhookEndpoints().deliveries().replay(A, B));
    calls.put("eventsList", f -> f.events().list());
    calls.put("eventsGet", f -> f.events().get(A));
    return calls;
  }

  private static Answer answer(String operationId) {
    if (operationId.endsWith("List")) {
      return Answer.json(200, Support.list("{}", false, null));
    }
    if (operationId.endsWith("Delete")) {
      return Answer.status(204);
    }
    if (operationId.equals("documentsGetPdf")) {
      return Answer.bytes(200, "%PDF-1.7".getBytes(StandardCharsets.UTF_8), "application/pdf");
    }
    if (operationId.equals("documentsGetXml")) {
      return Answer.bytes(200, "<factura/>".getBytes(StandardCharsets.UTF_8), "application/xml")
          .header("flashpi-xml-kind", "signed");
    }
    return Answer.json(200, "{}");
  }

  @Test
  void everyOperationOfTheContractHasExactlyOneCall() throws Exception {
    Map<String, String[]> operations = operations();
    assertEquals(45, operations.size());
    assertEquals(new TreeSet<>(operations.keySet()), new TreeSet<>(calls().keySet()));
  }

  @Test
  void eachCallSendsItsOperationsMethodAndPath() throws Exception {
    Map<String, String[]> operations = operations();
    for (Map.Entry<String, Consumer<Flashpi>> call : calls().entrySet()) {
      String operationId = call.getKey();
      FakeHttpClient http = new FakeHttpClient().always(answer(operationId));
      call.getValue().accept(Support.client(http));
      String[] operation = operations.get(operationId);
      FakeHttpClient.Recorded request = http.last();
      assertEquals(operation[0], request.method, operationId);
      String expected =
          operation[1]
              .replaceFirst("\\{[^}]+}", A)
              .replaceFirst("\\{[^}]+}", B)
              .replaceFirst("\\{[^}]+}", C);
      assertEquals(expected, request.path(), operationId);
      boolean post = request.method.equals("POST");
      assertEquals(post, request.header("idempotency-key") != null, operationId);
    }
  }

  @Test
  void bodiesAreSentAsTheOtherSdksSendThem() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.json(200, "{}"));
    Flashpi flashpi = Support.client(http);
    flashpi.issuerOnboardings().complete(A);
    assertEquals("{}", http.last().bodyText());
    flashpi.documents().sendEmail(A);
    assertEquals("{}", http.last().bodyText());
    flashpi.webhookEndpoints().rotateSecret(A);
    assertEquals("", http.last().bodyText());
    assertEquals(null, http.last().header("content-type"));
    flashpi
        .webhookEndpoints()
        .rotateSecret(
            A, RotateWebhookEndpointSecret.builder().previousSecretExpiresInHours(0).build());
    assertEquals("{\"previousSecretExpiresInHours\":0}", http.last().bodyText());
    flashpi
        .issuerOnboardings()
        .complete(A, CompleteIssuerOnboarding.builder().ruc("1790000000001").build());
    assertEquals("{\"ruc\":\"1790000000001\"}", http.last().bodyText());
    flashpi.webhookEndpoints().verify(A);
    assertEquals("", http.last().bodyText());
    flashpi.documents().pdf(A);
    assertEquals("application/pdf, application/problem+json", http.last().header("accept"));
  }

  @Test
  void theXmlCarriesItsStage() {
    FakeHttpClient http = new FakeHttpClient().always(answer("documentsGetXml"));
    ApiResponse<DocumentXml> result = Support.client(http).documents().withResponse().xml(A);
    assertEquals("<factura/>", result.getData().getXml());
    assertEquals(DocumentXml.Kind.SIGNED, result.getData().getKind());
    assertEquals("application/xml, application/problem+json", http.last().header("accept"));
    http.always(Answer.bytes(200, new byte[] {'x'}, "application/xml"));
    assertEquals(null, Support.client(http).documents().xml(A).getKind());
    http.always(answer("documentsGetXml").header("flashpi-xml-kind", "x"));
    assertEquals(DocumentXml.Kind.SIGNED, Support.client(http).documents().xml(A).getKind());
  }

  @Test
  void idsAreEncodedAndCheckedBeforeSending() {
    FakeHttpClient http = new FakeHttpClient().always(Answer.json(200, "{}"));
    Flashpi flashpi = Support.client(http);
    flashpi.documents().get("a/b c?é!~*'()");
    assertEquals("/v1/documents/a%2Fb%20c%3F%C3%A9!~*'()", http.last().path());
    for (String bad : Arrays.asList(null, "", "  ", ".", "..")) {
      ec.com.flashpi.exceptions.FlashpiException error =
          org.junit.jupiter.api.Assertions.assertThrows(
              ec.com.flashpi.exceptions.FlashpiException.class, () -> flashpi.issuers().get(bad));
      assertTrue(error.getMessage().contains("/v1/issuers/{id}"), error.getMessage());
    }
    assertEquals(1, http.requests.size());
  }

  /** Every public method of a resource has a twin in its withResponse() view. */
  @Test
  void everyViewMirrorsItsResource() throws Exception {
    Flashpi flashpi = Support.client(new FakeHttpClient());
    List<Object> resources =
        new ArrayList<>(
            List.of(
                flashpi.invoices(),
                flashpi.creditNotes(),
                flashpi.debitNotes(),
                flashpi.purchaseSettlements(),
                flashpi.waybills(),
                flashpi.withholdings(),
                flashpi.documents(),
                flashpi.issuers(),
                flashpi.issuers().establishments(),
                flashpi.issuers().establishments().emissionPoints(),
                flashpi.issuers().certificates(),
                flashpi.issuerOnboardings(),
                flashpi.webhookEndpoints(),
                flashpi.webhookEndpoints().deliveries(),
                flashpi.events(),
                flashpi.status()));
    int checked = 0;
    for (Object resource : resources) {
      Object view = resource.getClass().getMethod("withResponse").invoke(resource);
      Set<String> viewMethods = signatures(view.getClass());
      for (Method method : resource.getClass().getMethods()) {
        if (method.getDeclaringClass() == Object.class
            || !Modifier.isPublic(method.getModifiers())
            || method.getName().equals("withResponse")
            || Resource.class.isAssignableFrom(method.getReturnType())) {
          continue;
        }
        assertTrue(
            viewMethods.contains(signature(method)),
            resource.getClass().getSimpleName() + "." + signature(method));
        checked++;
      }
      assertNotNull(view);
    }
    assertEquals(100, checked);
    assertTrue(DocumentIssuer.class.isAssignableFrom(flashpi.invoices().getClass()));
  }

  /** Every method of every view returns its data with the response it came from. */
  @Test
  void eachViewReturnsTheResponseOfItsCall() throws Exception {
    FakeHttpClient http = new FakeHttpClient();
    Flashpi flashpi = Support.client(http);
    Map<Class<?>, Object> bodies =
        Map.of(
            ec.com.flashpi.resources.Invoices.class, CreateInvoice.builder().build(),
            ec.com.flashpi.resources.CreditNotes.class, CreateCreditNote.builder().build(),
            ec.com.flashpi.resources.DebitNotes.class, CreateDebitNote.builder().build(),
            ec.com.flashpi.resources.PurchaseSettlements.class,
                CreatePurchaseSettlement.builder().build(),
            ec.com.flashpi.resources.Waybills.class, CreateWaybill.builder().build(),
            ec.com.flashpi.resources.Withholdings.class, CreateWithholding.builder().build());
    List<Object> resources =
        List.of(
            flashpi.invoices(),
            flashpi.creditNotes(),
            flashpi.debitNotes(),
            flashpi.purchaseSettlements(),
            flashpi.waybills(),
            flashpi.withholdings(),
            flashpi.documents(),
            flashpi.issuers(),
            flashpi.issuers().establishments(),
            flashpi.issuers().establishments().emissionPoints(),
            flashpi.issuers().certificates(),
            flashpi.issuerOnboardings(),
            flashpi.webhookEndpoints(),
            flashpi.webhookEndpoints().deliveries(),
            flashpi.events(),
            flashpi.status());
    int calls = 0;
    for (Object resource : resources) {
      Object view = resource.getClass().getMethod("withResponse").invoke(resource);
      for (Method method : view.getClass().getMethods()) {
        if (method.getDeclaringClass() != view.getClass()) {
          continue;
        }
        Object[] arguments = new Object[method.getParameterCount()];
        Class<?>[] types = method.getParameterTypes();
        for (int index = 0; index < types.length; index++) {
          Class<?> type = types[index];
          if (type == String.class) {
            arguments[index] = A;
          } else if (type == RequestOptions.class) {
            arguments[index] = RequestOptions.none();
          } else if (type == CertificateUpload.class) {
            arguments[index] = UPLOAD;
          } else if (type == Object.class) {
            arguments[index] = bodies.get(resource.getClass());
          } else {
            Object builder = type.getMethod("builder").invoke(null);
            arguments[index] = builder.getClass().getMethod("build").invoke(builder);
          }
        }
        String name = method.getName();
        Answer answer =
            name.equals("list")
                ? answer("xList")
                : name.equals("delete")
                    ? answer("xDelete")
                    : name.equals("pdf")
                        ? answer("documentsGetPdf")
                        : name.equals("xml") ? answer("documentsGetXml") : answer("x");
        http.always(answer.header("x-request-id", "req_" + calls));
        ApiResponse<?> result = (ApiResponse<?>) method.invoke(view, arguments);
        assertEquals(
            "req_" + calls,
            result.getRequestId(),
            resource.getClass().getSimpleName() + "." + name);
        calls++;
      }
    }
    assertEquals(http.requests.size(), calls);
    assertEquals(100, calls);
  }

  private static Set<String> signatures(Class<?> type) {
    return Arrays.stream(type.getMethods())
        .map(OperationsTest::signature)
        .collect(Collectors.toSet());
  }

  private static String signature(Method method) {
    return method.getName()
        + Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.joining(",", "(", ")"));
  }

  /** operationId to {method, path}. */
  static Map<String, String[]> operations() throws Exception {
    JsonNode paths = ModelsTest.contract().get("paths");
    Map<String, String[]> operations = new LinkedHashMap<>();
    for (Iterator<String> names = paths.fieldNames(); names.hasNext(); ) {
      String path = names.next();
      JsonNode item = paths.get(path);
      for (Iterator<String> methods = item.fieldNames(); methods.hasNext(); ) {
        String method = methods.next();
        operations.put(
            item.get(method).get("operationId").asText(),
            new String[] {method.toUpperCase(java.util.Locale.ROOT), path});
      }
    }
    return operations;
  }
}
