package ec.com.flashpi.models;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/** The members of a generated class, and how to read and create it. */
final class ModelType implements ValueType {
  private final Class<? extends FlashpiModel> type;
  private final BiFunction<Map<String, Object>, Map<String, Object>, FlashpiModel> factory;
  private final Map<String, Types.Member> members = new LinkedHashMap<>();

  ModelType(
      Class<? extends FlashpiModel> type,
      BiFunction<Map<String, Object>, Map<String, Object>, FlashpiModel> factory,
      Types.Member... members) {
    this.type = type;
    this.factory = factory;
    for (Types.Member member : members) {
      this.members.put(member.name, member);
    }
  }

  String name() {
    return type.getSimpleName();
  }

  boolean has(String member) {
    return members.containsKey(member);
  }

  boolean isSecret(String member) {
    Types.Member found = members.get(member);
    return found != null && found.secret;
  }

  FlashpiModel create(Map<String, Object> members, Map<String, Object> additionalProperties) {
    return factory.apply(members, additionalProperties);
  }

  @Override
  public Object read(JsonNode node, String path) {
    if (!node.isObject()) {
      throw new ModelException(path, "an object (" + name() + ")");
    }
    Map<String, Object> values = new LinkedHashMap<>();
    Map<String, Object> additional = new LinkedHashMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = Types.members(node);
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      Types.Member member = members.get(field.getKey());
      String where = path + "." + field.getKey();
      if (member == null) {
        additional.put(field.getKey(), Types.plain(field.getValue()));
      } else {
        values.put(field.getKey(), Types.read(member.type, field.getValue(), where));
      }
    }
    return create(Collections.unmodifiableMap(values), additional);
  }
}
