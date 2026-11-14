package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import ec.com.flashpi.FakeHttpClient.Answer;
import ec.com.flashpi.exceptions.FlashpiException;
import ec.com.flashpi.exceptions.UnprocessableEntityException;
import ec.com.flashpi.models.CreateInvoice;
import ec.com.flashpi.models.Document;
import ec.com.flashpi.models.DocumentEvent;
import ec.com.flashpi.models.Event;
import ec.com.flashpi.models.ProblemDetails;
import ec.com.flashpi.models.WebhookEndpoint;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** What the adversarial review of 2026-11-13 found, each with the case that showed it. */
class ReviewFixesTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String EVENT =
      "{\"id\":\"e1\",\"type\":\"invoice.authorized\",\"environment\":\"test\","
          + "\"createdAt\":\"2026-11-13T12:00:00Z\",\"data\":{\"id\":\"doc_1\",\"sequential\":123456789}}";

  /** Writing by the declared union type (Spring MVC for a List<Event>) keeps type and data. */
  @Test
  void aUnionWrittenByItsDeclaredTypeKeepsEveryMember() throws Exception {
    Event event = MAPPER.readValue(EVENT, Event.class);
    assertEquals(
        MAPPER.readTree(EVENT),
        MAPPER.readTree(MAPPER.writerFor(Event.class).writeValueAsString(event)));
    String list =
        MAPPER.writerFor(new TypeReference<List<Event>>() {}).writeValueAsString(List.of(event));
    assertEquals(MAPPER.readTree("[" + EVENT + "]"), MAPPER.readTree(list));
  }

  /** Jackson's polymorphic typing (caches, sessions) around models. */
  @Test
  void modelsWorkWithJacksonsDefaultTyping() throws Exception {
    BasicPolymorphicTypeValidator validator =
        BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build();
    for (ObjectMapper.DefaultTyping typing :
        List.of(
            ObjectMapper.DefaultTyping.NON_FINAL, ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT)) {
      ObjectMapper mapper = new ObjectMapper().activateDefaultTyping(validator, typing);
      Holder holder = new Holder();
      holder.event = MAPPER.readValue(EVENT, Event.class);
      holder.value = MAPPER.readValue("{\"id\":\"doc_2\"}", Document.class);
      Holder copy = mapper.readValue(mapper.writeValueAsString(holder), Holder.class);
      assertEquals(holder.event, copy.event, typing.toString());
      assertEquals(holder.value, copy.value, typing.toString());
    }
  }

  /** A holder of a user's own, with a union field and a field of any type. */
  public static final class Holder {
    public Event event;
    public Object value;
  }

  /** Whole numbers the contract declares as numbers are written back as they came. */
  @Test
  void wholeNumbersAreWrittenWithoutAFraction() throws Exception {
    Document document = MAPPER.readValue("{\"sequential\":123456789}", Document.class);
    assertEquals("{\"sequential\":123456789}", MAPPER.writeValueAsString(document));
    ProblemDetails problem = MAPPER.readValue("{\"status\":422,\"limit\":3}", ProblemDetails.class);
    assertEquals("{\"status\":422,\"limit\":3}", MAPPER.writeValueAsString(problem));
    assertEquals(
        "{\"status\":1.5}",
        MAPPER.writeValueAsString(MAPPER.readValue("{\"status\":1.5}", ProblemDetails.class)));
  }

  /** A mapper that was Java-serialized (Spark, Flink) still reads models. */
  @Test
  void aSerializedMapperStillReadsModels() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper.readValue("{\"id\":\"a\"}", Document.class);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(mapper);
    }
    ObjectMapper copy;
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = (ObjectMapper) in.readObject();
    }
    assertEquals("b", copy.readValue("{\"id\":\"b\"}", Document.class).getId());
  }

  /** Members a model keeps but does not know cannot change once it is built. */
  @Test
  void additionalPropertiesAreImmutableAllTheWayDown() throws Exception {
    Document document = MAPPER.readValue("{\"future\":{\"list\":[1,{\"a\":2}]}}", Document.class);
    Map<?, ?> future = (Map<?, ?>) document.getAdditionalProperties().get("future");
    assertThrows(UnsupportedOperationException.class, future::clear);
    List<?> list = (List<?>) future.get("list");
    assertThrows(UnsupportedOperationException.class, list::clear);
    assertThrows(UnsupportedOperationException.class, ((Map<?, ?>) list.get(1))::clear);

    List<Object> mine = new ArrayList<>(List.of(1));
    CreateInvoice body = CreateInvoice.builder().additionalProperty("extra", mine).build();
    mine.add(2);
    assertEquals("{\"extra\":[1]}", MAPPER.writeValueAsString(body));
  }

  /** toString() hides a secret the SDK does not know by name yet, nested ones too. */
  @Test
  void secretsInAdditionalPropertiesAreHidden() throws Exception {
    WebhookEndpoint endpoint =
        MAPPER.readValue(
            "{\"id\":\"we\",\"secret\":\"whsec_LEAK1\",\"auth\":{\"password\":\"LEAK2\",\"user\":\"u\"},"
                + "\"signingToken\":\"LEAK3\"}",
            WebhookEndpoint.class);
    String shown = endpoint.toString();
    assertFalse(shown.contains("LEAK"), shown);
    assertTrue(shown.contains("user=u"), shown);
    assertTrue(
        CreateInvoice.builder()
            .additionalProperty("clientSecret", "LEAK4")
            .build()
            .toString()
            .contains("clientSecret=***"));
  }

  /** The API's answers are read leniently: a member that changed type does not lose the answer. */
  @Test
  void anAnswerWithAMemberThatDoesNotFitIsStillRead() {
    FakeHttpClient http =
        new FakeHttpClient()
            .then(
                Answer.json(
                    200, "{\"id\":\"doc\",\"sequential\":\"one\",\"status\":\"authorized\"}"));
    Document document = Support.client(http).documents().get("doc");
    assertEquals("doc", document.getId());
    assertNull(document.getSequential());
    assertFalse(document.has("sequential"));
    assertEquals("one", document.getAdditionalProperties().get("sequential"));
    assertEquals("authorized", document.getStatus().value());
  }

  /** Recovery reads documentId even when another member of the problem does not fit. */
  @Test
  void documentRecoveryDoesNotNeedEveryProblemMemberToFit() {
    String reused =
        "{\"code\":\"idempotency_key_reused\",\"status\":\"422\",\"limit\":\"x\",\"errors\":[{\"field\":1},"
            + "{\"field\":\"items\",\"code\":\"required\",\"message\":\"m\"}],\"documentId\":\"doc_1\"}";
    FakeHttpClient http =
        new FakeHttpClient()
            .then(Answer.status(503))
            .then(Answer.json(422, reused))
            .then(Answer.json(200, Support.DOCUMENT));
    Document document = Support.client(http).invoices().create(CreateInvoice.builder().build());
    assertEquals(Support.DOCUMENT_ID, document.getId());
    assertEquals("/v1/documents/doc_1", http.last().path());

    UnprocessableEntityException error = new UnprocessableEntityException(422, Map.of(), reused);
    assertEquals("doc_1", error.getProblem().getDocumentId());
    assertEquals("422", error.getProblem().getAdditionalProperties().get("status"));
    // As in Python, a field error that does not fit is kept too, with its member set apart.
    assertEquals(2, error.getErrors().size());
    assertNull(error.getErrors().get(0).getField());
    assertEquals(1, error.getErrors().get(0).getAdditionalProperties().get("field"));
    assertEquals("items", error.getErrors().get(1).getField());
  }

  /** Jackson 2.13 to 2.15 quote the source in their locations: the SDK's mapper never does. */
  @Test
  void anUnreadableAnswerNeverShowsItsBodyInTheStackTrace() {
    FakeHttpClient http =
        new FakeHttpClient().then(Answer.json(200, "{\"secret\":\"whsec_SUPERSECRET\", oops"));
    FlashpiException error =
        assertThrows(
            FlashpiException.class, () -> Support.client(http).webhookEndpoints().getSecret("we"));
    StringWriter trace = new StringWriter();
    error.printStackTrace(new PrintWriter(trace));
    assertFalse(trace.toString().contains("SUPERSECRET"), trace.toString());
  }

  @Test
  void aBaseUrlWithCredentialsIsRefusedWithoutShowingThem() {
    FlashpiException error =
        assertThrows(
            FlashpiException.class,
            () ->
                Flashpi.builder()
                    .apiKey(Support.KEY)
                    .baseUrl("https://user:p4ss@api.test")
                    .build());
    assertFalse(error.getMessage().contains("p4ss"));
    FlashpiException forged =
        assertThrows(
            FlashpiException.class,
            () -> Flashpi.builder().apiKey(Support.KEY).baseUrl("ftp://x\r\nINFO forged").build());
    assertFalse(forged.getMessage().contains("\n"), forged.getMessage());
    IllegalArgumentException header =
        assertThrows(
            IllegalArgumentException.class,
            () -> RequestOptions.builder().header("X\r\nY", "1").build());
    assertFalse(header.getMessage().contains("\n"), header.getMessage());
  }

  @Test
  void unionsStillReadThinEventsAsTheirClass() throws Exception {
    Event thin = MAPPER.readValue("{\"id\":\"e\",\"type\":\"invoice.authorized\"}", Event.class);
    assertNull(assertInstanceOf(DocumentEvent.class, thin).getData());
  }
}
