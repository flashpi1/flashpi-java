package ec.com.flashpi.models;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

/**
 * Writes a model as the API's JSON with any Jackson {@code ObjectMapper}: the members that are set,
 * in order, null included, then the additional properties. Dates are written as ISO 8601 text, so
 * no Java time module is needed. Jackson finds it through the models' annotations; you never use it
 * directly.
 */
public final class ModelSerializer extends StdSerializer<FlashpiModel> {
  private static final long serialVersionUID = 1L;

  /** Created by Jackson. */
  public ModelSerializer() {
    super(FlashpiModel.class);
  }

  @Override
  public void serialize(FlashpiModel value, JsonGenerator generator, SerializerProvider provider)
      throws IOException {
    Types.writeModel(value, generator, provider);
  }
}
