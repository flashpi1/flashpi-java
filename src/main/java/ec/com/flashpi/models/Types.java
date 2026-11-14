package ec.com.flashpi.models;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** The value types of the generated models, and the helpers their getters and builders use. */
final class Types {
  private Types() {}

  /** A member of a generated class. */
  static final class Member {
    final String name;
    final ValueType type;
    final boolean secret;

    Member(String name, ValueType type, boolean secret) {
      this.name = name;
      this.type = type;
      this.secret = secret;
    }
  }

  static Member member(String name, ValueType type, boolean secret) {
    return new Member(name, type, secret);
  }

  static final ValueType STRING =
      (node, path) -> {
        if (!node.isTextual()) {
          throw new ModelException(path, "a string");
        }
        return node.textValue();
      };

  static final ValueType INTEGER =
      (node, path) -> {
        if (node.isIntegralNumber() && node.canConvertToInt()) {
          return node.intValue();
        }
        if (isWhole(node)
            && node.doubleValue() >= Integer.MIN_VALUE
            && node.doubleValue() <= Integer.MAX_VALUE) {
          return (int) node.doubleValue();
        }
        throw new ModelException(path, "a whole number");
      };

  static final ValueType LONG =
      (node, path) -> {
        if (node.isIntegralNumber() && node.canConvertToLong()) {
          return node.longValue();
        }
        if (isWhole(node) && Math.abs(node.doubleValue()) < 0x1p63) {
          return (long) node.doubleValue();
        }
        throw new ModelException(path, "a whole number");
      };

  static final ValueType NUMBER =
      (node, path) -> {
        if (!node.isNumber()) {
          throw new ModelException(path, "a number");
        }
        return node.doubleValue();
      };

  static final ValueType BOOLEAN =
      (node, path) -> {
        if (!node.isBoolean()) {
          throw new ModelException(path, "true or false");
        }
        return node.booleanValue();
      };

  static final ValueType DATE =
      (node, path) -> {
        if (node.isTextual()) {
          try {
            return LocalDate.parse(node.textValue());
          } catch (DateTimeParseException error) {
            // Reported below.
          }
        }
        throw new ModelException(path, "a date (2026-11-13)");
      };

  static final ValueType DATE_TIME =
      (node, path) -> {
        if (node.isTextual()) {
          String text = node.textValue();
          try {
            return OffsetDateTime.parse(text);
          } catch (DateTimeParseException error) {
            try {
              // Without an offset: UTC, as the API's dates are.
              return LocalDateTime.parse(text).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
              // Reported below.
            }
          }
        }
        throw new ModelException(path, "a date and time (2026-11-13T15:04:05Z)");
      };

  /** Any JSON: maps, lists, strings, numbers, booleans and nulls. */
  static final ValueType ANY = (node, path) -> plain(node);

  /** The value type of a generated class, looked up when first needed. */
  static ValueType model(Supplier<ModelType> type) {
    return (node, path) -> type.get().read(node, path);
  }

  /** A value type looked up when first needed (the unions of ModelRegistry). */
  static ValueType lazy(Supplier<ValueType> type) {
    return (node, path) -> type.get().read(node, path);
  }

  static ValueType list(ValueType item) {
    return (node, path) -> {
      if (!node.isArray()) {
        throw new ModelException(path, "an array");
      }
      List<Object> items = new ArrayList<>(node.size());
      for (int index = 0; index < node.size(); index++) {
        items.add(read(item, node.get(index), path + "[" + index + "]"));
      }
      return Collections.unmodifiableList(items);
    };
  }

  static ValueType map(ValueType value) {
    return (node, path) -> {
      if (!node.isObject()) {
        throw new ModelException(path, "an object");
      }
      Map<String, Object> values = new LinkedHashMap<>();
      Iterator<Map.Entry<String, JsonNode>> fields = members(node);
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        values.put(field.getKey(), read(value, field.getValue(), path + "." + field.getKey()));
      }
      return Collections.unmodifiableMap(values);
    };
  }

  /**
   * A discriminated union: the member its {@code property} names, or the fallback for a value this
   * version does not know.
   *
   * @param pairs the discriminator values and their value types, alternating
   */
  static ValueType union(Class<?> union, String property, ValueType fallback, Object... pairs) {
    Map<String, ValueType> members = new LinkedHashMap<>();
    for (int index = 0; index < pairs.length; index += 2) {
      members.put((String) pairs[index], (ValueType) pairs[index + 1]);
    }
    String name = union.getSimpleName();
    return (node, path) -> {
      if (!node.isObject()) {
        throw new ModelException(path, "an object (" + name + ")");
      }
      JsonNode discriminator = node.get(property);
      ValueType member =
          discriminator != null && discriminator.isTextual()
              ? members.get(discriminator.textValue())
              : null;
      if (member == null) {
        member = fallback;
      }
      if (member == null) {
        throw new ModelException(path, "a known " + property + " (" + name + ")");
      }
      return member.read(node, path);
    };
  }

  /** The members of a JSON object (see {@code Json.members}). */
  @SuppressWarnings("deprecation")
  static Iterator<Map.Entry<String, JsonNode>> members(JsonNode node) {
    return node.fields();
  }

  static Object read(ValueType type, JsonNode node, String path) {
    return node == null || node.isNull() ? null : type.read(node, path);
  }

  private static boolean isWhole(JsonNode node) {
    if (!node.isFloatingPointNumber()) {
      return false;
    }
    double value = node.doubleValue();
    return !Double.isInfinite(value) && value == Math.rint(value);
  }

  /** Plain Java values of any JSON: LinkedHashMap, ArrayList, String, numbers, Boolean, null. */
  static Object plain(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isObject()) {
      Map<String, Object> values = new LinkedHashMap<>();
      Iterator<Map.Entry<String, JsonNode>> fields = members(node);
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        values.put(field.getKey(), plain(field.getValue()));
      }
      return values;
    }
    if (node.isArray()) {
      List<Object> items = new ArrayList<>(node.size());
      for (JsonNode item : node) {
        items.add(plain(item));
      }
      return items;
    }
    if (node.isTextual()) {
      return node.textValue();
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    if (node.isIntegralNumber()) {
      if (node.canConvertToInt()) {
        return node.intValue();
      }
      return node.canConvertToLong() ? (Object) node.longValue() : node.bigIntegerValue();
    }
    if (node.isNumber()) {
      return node.isBigDecimal() ? node.decimalValue() : node.doubleValue();
    }
    return node.toString();
  }

  // Getters and builders.

  /** The constants of a stored list of enum values. */
  static <E> List<E> enumList(Object stored, Function<String, E> fromValue) {
    if (stored == null) {
      return null;
    }
    List<E> constants = new ArrayList<>();
    for (Object value : (List<?>) stored) {
      constants.add(fromValue.apply((String) value));
    }
    return Collections.unmodifiableList(constants);
  }

  /** The value to store for an enum constant; UNKNOWN cannot be sent. */
  static String wireValue(String value, Object constant) {
    if (constant != null && value == null) {
      throw new IllegalArgumentException(
          constant
              + " cannot be sent: it stands for a value this version of the SDK does not know");
    }
    return value;
  }

  /** The values to store for a list of enum constants. */
  static <E> List<String> wireValues(List<E> constants, Function<E, String> value) {
    if (constants == null) {
      return null;
    }
    List<String> values = new ArrayList<>(constants.size());
    for (E constant : constants) {
      values.add(constant == null ? null : wireValue(value.apply(constant), constant));
    }
    return Collections.unmodifiableList(values);
  }

  static <T> List<T> copyList(List<T> list) {
    return list == null ? null : Collections.unmodifiableList(new ArrayList<>(list));
  }

  static <V> Map<String, V> copyMap(Map<String, V> map) {
    return map == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(map));
  }

  // Writing.

  static void write(Object value, JsonGenerator generator, SerializerProvider provider)
      throws IOException {
    if (value == null) {
      generator.writeNull();
    } else if (value instanceof String) {
      generator.writeString((String) value);
    } else if (value instanceof Integer) {
      generator.writeNumber((Integer) value);
    } else if (value instanceof Long) {
      generator.writeNumber((Long) value);
    } else if (value instanceof Double) {
      double number = (Double) value;
      if (Double.isNaN(number) || Double.isInfinite(number)) {
        throw JsonMappingException.from(
            provider, "NaN and infinite numbers cannot be sent as JSON");
      }
      generator.writeNumber(number);
    } else if (value instanceof Boolean) {
      generator.writeBoolean((Boolean) value);
    } else if (value instanceof LocalDate) {
      generator.writeString(value.toString());
    } else if (value instanceof OffsetDateTime) {
      generator.writeString(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format((OffsetDateTime) value));
    } else if (value instanceof FlashpiModel) {
      writeModel((FlashpiModel) value, generator, provider);
    } else if (value instanceof List) {
      generator.writeStartArray();
      for (Object item : (List<?>) value) {
        write(item, generator, provider);
      }
      generator.writeEndArray();
    } else if (value instanceof Map) {
      generator.writeStartObject();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        generator.writeFieldName(String.valueOf(entry.getKey()));
        write(entry.getValue(), generator, provider);
      }
      generator.writeEndObject();
    } else {
      // A caller's own value in additionalProperties.
      provider.defaultSerializeValue(value, generator);
    }
  }

  static void writeModel(FlashpiModel model, JsonGenerator generator, SerializerProvider provider)
      throws IOException {
    generator.writeStartObject();
    for (Map.Entry<String, Object> member : model.members().entrySet()) {
      generator.writeFieldName(member.getKey());
      write(member.getValue(), generator, provider);
    }
    for (Map.Entry<String, Object> member : model.getAdditionalProperties().entrySet()) {
      generator.writeFieldName(member.getKey());
      write(member.getValue(), generator, provider);
    }
    generator.writeEndObject();
  }
}
