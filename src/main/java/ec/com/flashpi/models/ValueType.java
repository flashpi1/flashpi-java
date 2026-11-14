package ec.com.flashpi.models;

import com.fasterxml.jackson.databind.JsonNode;

/** How a member's JSON becomes the value a model keeps. */
interface ValueType {
  /**
   * The value of a JSON node that is not null.
   *
   * @param node the node
   * @param path where the node is, for errors ({@code $.items[0].quantity})
   * @return the value
   * @throws ModelException when the node does not fit the type
   */
  Object read(JsonNode node, String path);
}
