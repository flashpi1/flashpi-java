package org.springframework.http;

/** Spring's ResponseEntity, as much as the README examples use, with its real signatures. */
public class ResponseEntity<T> {
  public static BodyBuilder badRequest() {
    throw new UnsupportedOperationException();
  }

  public static <T> ResponseEntity<T> ok(T body) {
    throw new UnsupportedOperationException();
  }

  public static HeadersBuilder<?> noContent() {
    throw new UnsupportedOperationException();
  }

  public interface HeadersBuilder<B extends HeadersBuilder<B>> {
    <T> ResponseEntity<T> build();
  }

  public interface BodyBuilder extends HeadersBuilder<BodyBuilder> {}
}
