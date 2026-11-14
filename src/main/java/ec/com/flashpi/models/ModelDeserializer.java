package ec.com.flashpi.models;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/**
 * Reads a model, or a union of models, from the API's JSON with any Jackson {@code ObjectMapper}.
 * Members this version of the SDK does not know are kept as additional properties; a member whose
 * JSON does not fit its type is reported as Jackson's input mismatch, with its path (or, when the
 * {@link #LENIENT} attribute is set, as the SDK reads the API's answers, kept as an additional
 * property). Jackson finds it through the models' annotations; you never use it directly.
 */
public final class ModelDeserializer extends StdDeserializer<Object>
    implements ContextualDeserializer {
  private static final long serialVersionUID = 1L;

  /**
   * A {@code DeserializationContext} attribute: when it is {@code Boolean.TRUE}, a member whose
   * JSON does not fit its type is kept as an additional property (its getter returns null) instead
   * of failing the whole read. The SDK reads the API's answers so, so an older SDK never fails on a
   * member the API changed.
   */
  public static final String LENIENT = "ec.com.flashpi.models.lenient";

  // Looked up again after Java deserialization (a serialized ObjectMapper).
  private transient ValueType type;

  /** Created by Jackson, then made contextual for the model it reads. */
  public ModelDeserializer() {
    super(Object.class);
    this.type = null;
  }

  private ModelDeserializer(Class<?> model, ValueType type) {
    super(model);
    this.type = type;
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property)
      throws JsonMappingException {
    JavaType target = context.getContextualType();
    if (target == null && property != null) {
      target = property.getType();
    }
    ValueType found = target == null ? null : ModelRegistry.typeOf(target.getRawClass());
    if (found == null) {
      return context.reportBadDefinition(
          target, "ModelDeserializer only reads the models of ec.com.flashpi.models");
    }
    return new ModelDeserializer(target.getRawClass(), found);
  }

  @Override
  public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    ValueType resolved = type;
    if (resolved == null && handledType() != Object.class) {
      resolved = ModelRegistry.typeOf(handledType());
      type = resolved;
    }
    if (resolved == null) {
      return context.reportBadDefinition(
          context.constructType(handledType()), "ModelDeserializer was not made contextual");
    }
    // The tree is read without type handling of its own, so Jackson's polymorphic typing (default
    // typing, @JsonTypeInfo) around a model works.
    JsonNode node =
        (JsonNode)
            context
                .findNonContextualValueDeserializer(context.constructType(JsonNode.class))
                .deserialize(parser, context);
    try {
      return Types.read(resolved, node, "$", Boolean.TRUE.equals(context.getAttribute(LENIENT)));
    } catch (ModelException error) {
      return context.reportInputMismatch(handledType(), "%s", error.getMessage());
    }
  }
}
