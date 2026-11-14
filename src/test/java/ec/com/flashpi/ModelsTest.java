package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ec.com.flashpi.models.AccountEvent;
import ec.com.flashpi.models.CreateInvoice;
import ec.com.flashpi.models.DocumentItem;
import ec.com.flashpi.models.DocumentStatus;
import ec.com.flashpi.models.EnabledEventType;
import ec.com.flashpi.models.ErrorCode;
import ec.com.flashpi.models.Event;
import ec.com.flashpi.models.EventThin;
import ec.com.flashpi.models.FlashpiModel;
import ec.com.flashpi.models.IssuerOnboardingSriProfileRimpeRegime;
import ec.com.flashpi.models.IssuerTaxProfilePatch;
import ec.com.flashpi.models.UpdateIssuer;
import ec.com.flashpi.models.UpdateWebhookEndpoint;
import ec.com.flashpi.models.WebhookEndpointSecret;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModelsTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  static JsonNode contract() throws Exception {
    return MAPPER.readTree(Files.readAllBytes(Paths.get("openapi/openapi.json")));
  }

  /** Every generated class with the schema it comes from: components, then list queries. */
  static Stream<Arguments> classes() throws Exception {
    JsonNode contract = contract();
    JsonNode schemas = contract.get("components").get("schemas");
    List<Arguments> classes = new ArrayList<>();
    for (Iterator<String> names = schemas.fieldNames(); names.hasNext(); ) {
      String name = names.next();
      JsonNode schema = schemas.get(name);
      if (schema.has("properties") || "object".equals(schema.path("type").asText())) {
        classes.add(Arguments.of(name, schema));
      }
    }
    for (JsonNode path : contract.get("paths")) {
      for (JsonNode operation : path) {
        ObjectNode query = NODES.objectNode();
        ObjectNode properties = query.putObject("properties");
        for (JsonNode parameter : operation.path("parameters")) {
          if ("query".equals(parameter.path("in").asText())) {
            properties.set(parameter.get("name").asText(), parameter.get("schema"));
          }
        }
        if (properties.size() > 0) {
          String id = operation.get("operationId").asText();
          classes.add(
              Arguments.of(
                  Character.toUpperCase(id.charAt(0)) + id.substring(1) + "Params", query));
        }
      }
    }
    return classes.stream();
  }

  /** A JSON value with every member of the schema set. */
  static JsonNode sample(JsonNode schema, JsonNode schemas) {
    if (schema.has("$ref")) {
      return sample(
          schemas.get(schema.get("$ref").asText().replace("#/components/schemas/", "")), schemas);
    }
    if (schema.has("allOf")) {
      return sample(schema.get("allOf").get(0), schemas);
    }
    for (String combinator : List.of("anyOf", "oneOf")) {
      if (schema.has(combinator)) {
        for (JsonNode member : schema.get(combinator)) {
          if (!"null".equals(member.path("type").asText())) {
            return sample(member, schemas);
          }
        }
      }
    }
    if (schema.has("const")) {
      return schema.get("const");
    }
    if (schema.has("enum")) {
      for (JsonNode value : schema.get("enum")) {
        if (!value.isNull()) {
          return value;
        }
      }
    }
    String type =
        schema.path("type").isArray()
            ? schema.get("type").get(0).asText()
            : schema.path("type").asText("object");
    switch (type) {
      case "string":
        String format = schema.path("format").asText();
        if (format.equals("date")) {
          return NODES.textNode("2026-11-13");
        }
        if (format.equals("date-time")) {
          return NODES.textNode("2026-11-13T15:04:05.123Z");
        }
        return NODES.textNode("text");
      case "integer":
        return NODES.numberNode(7);
      case "number":
        return NODES.numberNode(1.5);
      case "boolean":
        return NODES.booleanNode(true);
      case "array":
        ArrayNode array = NODES.arrayNode();
        array.add(sample(schema.get("items"), schemas));
        return array;
      default:
        ObjectNode object = NODES.objectNode();
        if (schema.has("properties")) {
          JsonNode properties = schema.get("properties");
          for (Iterator<String> members = properties.fieldNames(); members.hasNext(); ) {
            String member = members.next();
            object.set(member, sample(properties.get(member), schemas));
          }
        } else if (schema.path("additionalProperties").isObject()) {
          object.set("key", sample(schema.get("additionalProperties"), schemas));
        } else {
          object.put("key", "value");
        }
        return object;
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("classes")
  void readsWritesAndRebuildsEveryModel(String name, JsonNode schema) throws Exception {
    JsonNode schemas = contract().get("components").get("schemas");
    JsonNode sample = sample(schema, schemas);
    Class<?> type = Class.forName("ec.com.flashpi.models." + name);
    FlashpiModel model = (FlashpiModel) MAPPER.treeToValue(sample, type);

    // Written back as it was read (dates in ISO 8601, with their fraction).
    assertEquals(normalize(sample), MAPPER.valueToTree(model), name);
    assertTrue(model.getAdditionalProperties().isEmpty(), name);

    // Every getter returns its member; enums are known constants.
    Method builder = type.getMethod("builder");
    Object rebuilt = builder.invoke(null);
    for (Method getter : type.getMethods()) {
      if (!getter.getName().startsWith("get")
          || getter.getParameterCount() > 0
          || getter.getDeclaringClass() != type) {
        continue;
      }
      Object value = getter.invoke(model);
      assertNotNull(value, name + "." + getter.getName());
      if (value instanceof Enum) {
        assertNotEquals("UNKNOWN", ((Enum<?>) value).name(), name + "." + getter.getName());
      }
      String member =
          Character.toLowerCase(getter.getName().charAt(3)) + getter.getName().substring(4);
      rebuilt.getClass().getMethod(member, getter.getReturnType()).invoke(rebuilt, value);
    }

    // The builder makes the same model, and so does toBuilder().
    Object built = rebuilt.getClass().getMethod("build").invoke(rebuilt);
    assertEquals(model, built, name);
    assertEquals(model.hashCode(), built.hashCode(), name);
    Object copy = type.getMethod("toBuilder").invoke(model);
    assertEquals(model, copy.getClass().getMethod("build").invoke(copy), name);
    assertTrue(model.toString().startsWith(name + "{"), name);

    // Java serialization keeps it whole.
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(model);
    }
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      assertEquals(model, in.readObject(), name);
    }
  }

  /** The sample as the SDK writes it: dates and times in their canonical ISO 8601 form. */
  private static JsonNode normalize(JsonNode node) {
    if (node.isTextual() && node.textValue().equals("2026-11-13T15:04:05.123Z")) {
      return NODES.textNode("2026-11-13T15:04:05.123Z");
    }
    return node;
  }

  @Test
  void everyComponentIsGenerated() throws Exception {
    JsonNode schemas = contract().get("components").get("schemas");
    for (Iterator<String> names = schemas.fieldNames(); names.hasNext(); ) {
      String name = names.next();
      assertNotNull(Class.forName("ec.com.flashpi.models." + name), name);
    }
  }

  @Test
  void aMemberSetToNullIsSentAndAnUnsetOneIsNot() throws Exception {
    UpdateIssuer patch =
        UpdateIssuer.builder()
            .tradeName(null)
            .taxProfile(IssuerTaxProfilePatch.builder().rimpeRegime(null).build())
            .build();
    assertEquals(
        "{\"tradeName\":null,\"taxProfile\":{\"rimpeRegime\":null}}",
        MAPPER.writeValueAsString(patch));
    assertTrue(patch.has("tradeName"));
    assertFalse(patch.has("legalName"));
    assertNull(patch.getLegalName());
  }

  @Test
  void keepsMembersThisVersionDoesNotKnow() throws Exception {
    String json =
        "{\"id\":\"ev\",\"type\":\"account.email_paused\",\"future\":{\"a\":[1,2.5,true,null,\"x\"]},\"big\":12345678901234}";
    AccountEvent event = MAPPER.readValue(json, AccountEvent.class);
    assertEquals(
        java.util.Arrays.asList(1, 2.5, true, null, "x"),
        ((Map<?, ?>) event.getAdditionalProperties().get("future")).get("a"));
    assertEquals(12345678901234L, event.getAdditionalProperties().get("big"));
    assertEquals(MAPPER.readTree(json), MAPPER.valueToTree(event));
  }

  @Test
  void sendsMembersThisVersionDoesNotKnow() throws Exception {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("when", LocalDate.of(2026, 1, 2));
    CreateInvoice body =
        CreateInvoice.builder()
            .issuerId("i")
            .additionalProperty("newMember", nested)
            .additionalProperties(Map.of("other", 1))
            .build();
    assertEquals(
        "{\"issuerId\":\"i\",\"newMember\":{\"when\":\"2026-01-02\"},\"other\":1}",
        MAPPER.writeValueAsString(body));
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> CreateInvoice.builder().additionalProperty("issuerId", "x"));
    assertEquals(
        "issuerId is a member of CreateInvoice: set it with issuerId(...)", error.getMessage());
  }

  @Test
  void aValueThisVersionDoesNotKnowReadsAsUnknownAndIsWrittenBackAsItCame() throws Exception {
    String json =
        "{\"enabledEvents\":[\"invoice.authorized\",\"invoice.archived\"],\"payloadMode\":\"fat\"}";
    ec.com.flashpi.models.Document document =
        MAPPER.readValue("{\"status\":\"archived\"}", ec.com.flashpi.models.Document.class);
    assertEquals(DocumentStatus.UNKNOWN, document.getStatus());
    assertEquals("{\"status\":\"archived\"}", MAPPER.writeValueAsString(document));
    UpdateWebhookEndpoint endpoint = MAPPER.readValue(json, UpdateWebhookEndpoint.class);
    assertEquals(
        List.of(EnabledEventType.INVOICE_AUTHORIZED, EnabledEventType.UNKNOWN),
        endpoint.getEnabledEvents());
    assertEquals(ec.com.flashpi.models.PayloadMode.UNKNOWN, endpoint.getPayloadMode());
    assertEquals(json, MAPPER.writeValueAsString(endpoint));
  }

  @Test
  void unknownCannotBeSent() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ec.com.flashpi.models.DocumentsListParams.builder().status(DocumentStatus.UNKNOWN));
    assertTrue(error.getMessage().startsWith("UNKNOWN cannot be sent"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            UpdateWebhookEndpoint.builder()
                .enabledEvents(List.of(EnabledEventType.ALL, EnabledEventType.UNKNOWN)));
    assertEquals("*", EnabledEventType.ALL.value());
  }

  @Test
  void enumsMapTheirValues() {
    assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED, ErrorCode.fromValue("idempotency_key_reused"));
    assertEquals(ErrorCode.UNKNOWN, ErrorCode.fromValue("new_code"));
    assertNull(ErrorCode.fromValue(null));
    assertNull(ErrorCode.UNKNOWN.value());
    // An enum whose own values include "unknown" uses it for values it does not know.
    assertEquals("unknown", IssuerOnboardingSriProfileRimpeRegime.UNKNOWN.value());
    assertEquals(
        IssuerOnboardingSriProfileRimpeRegime.UNKNOWN,
        IssuerOnboardingSriProfileRimpeRegime.fromValue("other"));
  }

  @Test
  void neverShowsASecret() throws Exception {
    WebhookEndpointSecret secret =
        MAPPER.readValue(
            "{\"secret\":\"whsec_abc\",\"previousSecretExpiresAt\":null}",
            WebhookEndpointSecret.class);
    assertEquals(
        "WebhookEndpointSecret{secret=***, previousSecretExpiresAt=null}", secret.toString());
    assertEquals("whsec_abc", secret.getSecret());
  }

  @Test
  void reportsAMemberThatDoesNotFitWithItsPath() {
    JsonMappingException error =
        assertThrows(
            JsonMappingException.class,
            () ->
                MAPPER.readValue("{\"items\":[{\"taxes\":[{\"type\":5}]}]}", CreateInvoice.class));
    assertTrue(
        error.getOriginalMessage().contains("$.items[0].taxes[0].type: expected a string"),
        error.getOriginalMessage());
    assertThrows(
        JsonMappingException.class,
        () -> MAPPER.readValue("{\"issueDate\":\"13/11/2026\"}", CreateInvoice.class));
    assertThrows(
        JsonMappingException.class,
        () -> MAPPER.readValue("{\"sequential\":1.5}", CreateInvoice.class));
    assertThrows(JsonMappingException.class, () -> MAPPER.readValue("[]", CreateInvoice.class));
  }

  @Test
  void acceptsWholeNumbersWrittenWithAFraction() throws Exception {
    assertEquals(
        12, MAPPER.readValue("{\"sequential\":12.0}", CreateInvoice.class).getSequential());
  }

  @Test
  void readsDatesAndTimes() throws Exception {
    ec.com.flashpi.models.Document document =
        MAPPER.readValue(
            "{\"issueDate\":\"2026-11-13\",\"createdAt\":\"2026-11-13T15:04:05\"}",
            ec.com.flashpi.models.Document.class);
    assertEquals(LocalDate.of(2026, 11, 13), document.getIssueDate());
    assertEquals(OffsetDateTime.parse("2026-11-13T15:04:05Z"), document.getCreatedAt());
  }

  @Test
  void anEventOfAnUnknownTypeOrWithoutOneIsThin() throws Exception {
    assertInstanceOf(
        EventThin.class, MAPPER.readValue("{\"id\":\"e\",\"type\":\"x.y\"}", Event.class));
    assertInstanceOf(EventThin.class, MAPPER.readValue("{\"id\":\"e\"}", Event.class));
    assertInstanceOf(
        AccountEvent.class, MAPPER.readValue("{\"type\":\"account.email_paused\"}", Event.class));
  }

  @Test
  void listsInModelsCannotBeChanged() {
    DocumentItem item = DocumentItem.builder().taxes(new ArrayList<>()).build();
    assertThrows(UnsupportedOperationException.class, () -> item.getTaxes().add(null));
    assertThrows(
        UnsupportedOperationException.class, () -> item.getAdditionalProperties().put("a", 1));
  }

  @Test
  void theBaseClassIsTheModelsOnly() {
    assertTrue(Modifier.isAbstract(FlashpiModel.class.getModifiers()));
  }
}
