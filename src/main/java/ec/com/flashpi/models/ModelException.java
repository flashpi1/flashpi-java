package ec.com.flashpi.models;

/** JSON that does not fit a model: ModelDeserializer reports it as Jackson's input mismatch. */
final class ModelException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  ModelException(String path, String expected) {
    super(path + ": expected " + expected);
  }
}
