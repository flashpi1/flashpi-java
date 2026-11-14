package ec.com.flashpi.internal;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import ec.com.flashpi.exceptions.FlashpiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How to read the body of a successful answer.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public final class Readers {
  private Readers() {}

  /**
   * Reads a successful answer.
   *
   * @param <T> what it reads
   */
  @FunctionalInterface
  public interface Reader<T> {
    /**
     * The data of an answer.
     *
     * @param status the HTTP status
     * @param body the body
     * @return the data
     * @throws FlashpiException when the body cannot be read
     */
    T read(int status, byte[] body);
  }

  /**
   * A JSON body as a model; an empty body or a 204 as null.
   *
   * @param type the model's class
   * @param <T> the model
   * @return the reader
   */
  public static <T> Reader<T> json(Class<T> type) {
    return (status, body) -> {
      if (status == 204 || body.length == 0) {
        return null;
      }
      try {
        return Json.answer(type).readValue(body);
      } catch (JsonParseException error) {
        throw notJson(status, error);
      } catch (IOException error) {
        throw unreadable(status, error);
      }
    };
  }

  /**
   * The body of a list operation.
   *
   * @param type the class of its items
   * @param <T> the items
   * @return the reader
   */
  public static <T> Reader<ListBody<T>> list(Class<T> type) {
    return (status, body) -> {
      JsonNode tree;
      try {
        tree = Json.mapper().readTree(body);
      } catch (IOException error) {
        throw notJson(status, error);
      }
      JsonNode data = tree == null ? null : tree.get("data");
      JsonNode pagination = tree == null ? null : tree.get("pagination");
      if (data == null || !data.isArray() || pagination == null || !pagination.isObject()) {
        throw new FlashpiException("The API answered a list without data and pagination");
      }
      List<T> items = new ArrayList<>(data.size());
      try {
        for (JsonNode item : data) {
          items.add(Json.answer(type).readValue(item));
        }
      } catch (IOException error) {
        throw unreadable(status, error);
      }
      JsonNode cursor = pagination.get("nextCursor");
      JsonNode hasMore = pagination.get("hasMore");
      return new ListBody<>(
          Collections.unmodifiableList(items),
          hasMore != null && hasMore.isBoolean() && hasMore.booleanValue(),
          cursor != null && cursor.isTextual() ? cursor.textValue() : null);
    };
  }

  /**
   * A text body (XML), invalid UTF-8 replaced.
   *
   * @return the reader
   */
  public static Reader<String> text() {
    return (status, body) -> new String(body, StandardCharsets.UTF_8);
  }

  /**
   * A binary body (PDF), as it came.
   *
   * @return the reader
   */
  public static Reader<byte[]> bytes() {
    return (status, body) -> body;
  }

  /**
   * No data (a 204).
   *
   * @return the reader
   */
  public static Reader<Void> none() {
    return (status, body) -> null;
  }

  private static FlashpiException notJson(int status, Exception cause) {
    return new FlashpiException(
        "The API answered " + status + " with a body that is not JSON", cause);
  }

  private static FlashpiException unreadable(int status, IOException cause) {
    String reason =
        cause instanceof JsonProcessingException
            ? ((JsonProcessingException) cause).getOriginalMessage()
            : cause.getMessage();
    return new FlashpiException(
        "The API answered " + status + " with a body this SDK cannot read: " + reason, cause);
  }
}
