package ec.com.flashpi.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * The SDK's Jackson mapper. The models carry their own (de)serializers, so this is a plain {@code
 * ObjectMapper}: the SDK works with any Jackson 2 from 2.13 on, and never needs a Java time module.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public final class Json {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Json() {}

  /**
   * The SDK's mapper.
   *
   * @return the mapper
   */
  public static ObjectMapper mapper() {
    return MAPPER;
  }

  /**
   * A body as compact UTF-8 JSON.
   *
   * @param body a model, or any value Jackson can write
   * @return the JSON
   * @throws IllegalArgumentException when Jackson cannot write it (for example NaN)
   */
  public static byte[] encode(Object body) {
    try {
      return MAPPER.writeValueAsBytes(body);
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException(
          "The body cannot be sent as JSON: " + error.getOriginalMessage(), error);
    }
  }

  /**
   * The members of a JSON object, in order. {@code JsonNode.fields()} is deprecated from Jackson
   * 2.19 on, but its replacement does not exist in Jackson 2.13, which the SDK supports.
   *
   * @param node an object node
   * @return its members
   */
  @SuppressWarnings("deprecation")
  public static Iterator<Map.Entry<String, JsonNode>> members(JsonNode node) {
    return node.fields();
  }

  /**
   * The JSON object of a Problem Details answer: a JSON object with a string {@code code}.
   *
   * @param body the answer's text
   * @return the object, or null for anything else (an answer from a proxy, a firewall, ...)
   */
  public static JsonNode problem(String body) {
    if (body == null || body.isEmpty()) {
      return null;
    }
    try {
      JsonNode node = MAPPER.readTree(body);
      if (node instanceof ObjectNode && node.path("code").isTextual()) {
        return node;
      }
    } catch (IOException | RuntimeException error) {
      // Not JSON: an answer from the edge (CDN or firewall), not from the API.
    }
    return null;
  }

  /**
   * A model read from a JSON node, or null when it does not fit.
   *
   * @param node the node
   * @param type the model's class
   * @param <T> the model
   * @return the model, or null
   */
  public static <T> T readOrNull(JsonNode node, Class<T> type) {
    try {
      return MAPPER.treeToValue(node, type);
    } catch (JsonProcessingException | RuntimeException error) {
      return null;
    }
  }
}
