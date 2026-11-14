package ec.com.flashpi.internal;

import ec.com.flashpi.exceptions.FlashpiException;
import java.nio.charset.StandardCharsets;

/**
 * Paths with ids, and the query of a request, encoded as the Node and Python SDKs encode them.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public final class Paths {
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private Paths() {}

  /**
   * A path from a template with {@code {}} placeholders, each id percent-encoded as {@code
   * encodeURIComponent()} does: {@code Paths.of("/v1/issuers/{}", issuerId)}.
   *
   * @param template the path, with one {@code {}} per id
   * @param ids the ids
   * @return the path
   * @throws FlashpiException for an empty or blank id, or {@code .} and {@code ..}, which URL
   *     normalization would turn into the parent path (another resource)
   */
  public static String of(String template, String... ids) {
    String[] parts = template.split("\\{}", -1);
    if (parts.length != ids.length + 1) {
      throw new IllegalStateException(template + " does not have " + ids.length + " placeholders");
    }
    String shown = template.replace("{}", "{id}");
    StringBuilder path = new StringBuilder(parts[0]);
    for (int index = 0; index < ids.length; index++) {
      String id = ids[index];
      if (id == null || id.trim().isEmpty()) {
        throw new FlashpiException(
            "Missing id in " + shown + ": got " + (id == null ? "null" : "\"" + id + "\""));
      }
      if (id.equals(".") || id.equals("..")) {
        throw new FlashpiException("Invalid id in " + shown + ": \"" + id + "\"");
      }
      path.append(encode(id, "-_.!~*'()")).append(parts[index + 1]);
    }
    return path.toString();
  }

  /**
   * A query parameter's name or value as Python's {@code urlencode()} writes it: letters, digits
   * and {@code -_.~} as they are, a space as {@code +}, every other UTF-8 byte as {@code %XX}.
   *
   * @param value the text
   * @return the encoded text
   */
  public static String queryComponent(String value) {
    StringBuilder out = new StringBuilder();
    for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
      int c = b & 0xff;
      if (c == ' ') {
        out.append('+');
      } else if (isUnreserved(c) || "-_.~".indexOf(c) >= 0) {
        out.append((char) c);
      } else {
        out.append('%').append(HEX[c >> 4]).append(HEX[c & 0xf]);
      }
    }
    return out.toString();
  }

  /** Percent-encodes UTF-8 text, leaving letters, digits and {@code safe} as they are. */
  private static String encode(String value, String safe) {
    StringBuilder out = new StringBuilder();
    for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
      int c = b & 0xff;
      if (isUnreserved(c) || (c < 0x80 && safe.indexOf(c) >= 0)) {
        out.append((char) c);
      } else {
        out.append('%').append(HEX[c >> 4]).append(HEX[c & 0xf]);
      }
    }
    return out.toString();
  }

  private static boolean isUnreserved(int c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
  }
}
