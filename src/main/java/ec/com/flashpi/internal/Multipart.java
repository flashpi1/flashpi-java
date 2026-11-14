package ec.com.flashpi.internal;

import ec.com.flashpi.exceptions.FlashpiException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * A multipart/form-data body, built once so every attempt sends the same bytes. Its {@link
 * #toString()} shows only the part names: the parts are a .p12 and its password.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public final class Multipart {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private final byte[] body;
  private final String contentType;

  private Multipart(byte[] body, String contentType) {
    this.body = body;
    this.contentType = contentType;
  }

  /**
   * The body of a .p12 upload: a {@code password} field, then the {@code file} part.
   *
   * @param password the certificate's password; its UTF-8 bytes are cleared once copied
   * @param file the .p12
   * @param filename the file's name
   * @param fileType the file's content type
   * @return the body
   */
  public static Multipart certificate(
      char[] password, byte[] file, String filename, String fileType) {
    String boundary = boundary();
    ByteArrayOutputStream out = new ByteArrayOutputStream(file.length + 512);
    write(out, "--" + boundary + "\r\n");
    write(out, "Content-Disposition: form-data; name=\"password\"\r\n\r\n");
    byte[] secret = utf8(password);
    try {
      out.write(secret, 0, secret.length);
    } finally {
      Arrays.fill(secret, (byte) 0);
    }
    write(out, "\r\n--" + boundary + "\r\n");
    write(
        out,
        "Content-Disposition: form-data; name=\"file\"; filename=\"" + escape(filename) + "\"\r\n");
    write(out, "Content-Type: " + fileType + "\r\n\r\n");
    out.write(file, 0, file.length);
    write(out, "\r\n--" + boundary + "--\r\n");
    return new Multipart(out.toByteArray(), "multipart/form-data; boundary=" + boundary);
  }

  /**
   * The body's bytes.
   *
   * @return a copy of the bytes
   */
  public byte[] body() {
    return body.clone();
  }

  /**
   * The Content-Type header, with the boundary.
   *
   * @return the content type
   */
  public String contentType() {
    return contentType;
  }

  @Override
  public String toString() {
    return "Multipart{fields=[password], files=[file]}";
  }

  /**
   * A name or file name as HTML forms put it between the quotes of a Content-Disposition: {@code "}
   * as {@code %22}, {@code \} doubled and the control characters as {@code %XX}, as the Python and
   * PHP SDKs do. Other text is sent as UTF-8.
   */
  static String escape(String value) {
    StringBuilder out = new StringBuilder();
    for (int index = 0; index < value.length(); index++) {
      char c = value.charAt(index);
      if (c == '"') {
        out.append("%22");
      } else if (c == '\\') {
        out.append("\\\\");
      } else if (c < 0x20 || c == 0x7f) {
        out.append('%')
            .append(Character.toUpperCase(HEX[c >> 4]))
            .append(Character.toUpperCase(HEX[c & 0xf]));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static String boundary() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    StringBuilder out = new StringBuilder(32);
    for (byte b : bytes) {
      out.append(HEX[(b >> 4) & 0xf]).append(HEX[b & 0xf]);
    }
    return out.toString();
  }

  /** The UTF-8 of a password; the encoder's buffer is cleared once copied. */
  private static byte[] utf8(char[] text) {
    CharsetEncoder encoder =
        StandardCharsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    ByteBuffer encoded = ByteBuffer.allocate(text.length * 3 + 4);
    try {
      CoderResult result = encoder.encode(CharBuffer.wrap(text), encoded, true);
      if (result.isError()) {
        throw new FlashpiException(
            "The certificate password has a character that cannot be sent as UTF-8");
      }
      encoder.flush(encoded);
      encoded.flip();
      byte[] bytes = new byte[encoded.remaining()];
      encoded.get(bytes);
      return bytes;
    } finally {
      Arrays.fill(encoded.array(), (byte) 0);
    }
  }

  private static void write(ByteArrayOutputStream out, String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    out.write(bytes, 0, bytes.length);
  }
}
