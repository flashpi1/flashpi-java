package ec.com.flashpi.internal;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks of the values that become headers, done by the SDK before anything is sent, so a bad one
 * is never retried as a network failure. The messages never show a value, which may be a secret.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public final class Headers {
  /** A header name (RFC 9110 {@code token}). */
  private static final Pattern TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

  /** Control characters other than the tab: a line break would end the header. */
  private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0a-\\x1f\\x7f]");

  /**
   * Headers the SDK or the JDK's HttpClient set from the request: Content-Length and
   * Transfer-Encoding frame the body, and Java 11's HttpClient refuses the others.
   */
  private static final Set<String> REFUSED =
      Set.of(
          "connection",
          "content-length",
          "date",
          "expect",
          "from",
          "host",
          "transfer-encoding",
          "upgrade",
          "via",
          "warning");

  private Headers() {}

  /**
   * A header name: an RFC 9110 token, and not one the HTTP client sets itself.
   *
   * @param name the name
   * @return the name
   * @throws IllegalArgumentException when it is not valid
   */
  public static String name(String name) {
    Objects.requireNonNull(name, "A header name is null");
    if (!TOKEN.matcher(name).matches()) {
      throw new IllegalArgumentException("\"" + name + "\" is not a valid header name");
    }
    if (REFUSED.contains(name.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException(
          "\"" + name + "\" is set by the HTTP client from the request: leave it out");
    }
    return name;
  }

  /**
   * A header value without the spaces, tabs and line breaks around it, as the Node SDK's {@code
   * Headers} sends it.
   *
   * @param label what the value is, for the message
   * @param value the value
   * @return the trimmed value
   * @throws IllegalArgumentException when a line break, another control character other than the
   *     tab, or a lone surrogate (which has no UTF-8) is left
   */
  public static String value(String label, String value) {
    Objects.requireNonNull(value, () -> label + " is null");
    String trimmed = trim(value);
    if (CONTROL.matcher(trimmed).find()) {
      throw new IllegalArgumentException(label + " has a line break or another control character");
    }
    for (int index = 0; index < trimmed.length(); index++) {
      char c = trimmed.charAt(index);
      if (Character.isHighSurrogate(c)
          && index + 1 < trimmed.length()
          && Character.isLowSurrogate(trimmed.charAt(index + 1))) {
        index++;
      } else if (Character.isSurrogate(c)) {
        throw new IllegalArgumentException(label + " has a character that cannot be sent as UTF-8");
      }
    }
    return trimmed;
  }

  /**
   * Headers with each name and value checked; later names replace earlier ones without regard to
   * case.
   *
   * @param label what the headers are, for the messages
   * @param headers the headers, or null
   * @return the checked headers, in order; unmodifiable
   */
  public static Map<String, String> checked(String label, Map<String, String> headers) {
    Map<String, String> checked = new CaseInsensitiveMap();
    if (headers == null) {
      return Collections.unmodifiableMap(checked);
    }
    for (Map.Entry<String, String> header : headers.entrySet()) {
      String name = name(header.getKey());
      checked.put(
          name, value("The value of header \"" + name + "\" in " + label, header.getValue()));
    }
    return Collections.unmodifiableMap(checked);
  }

  /**
   * A value as the JDK's HttpClient sends it: its bytes are the characters' low bytes, so text
   * outside ASCII becomes the chars of its UTF-8 bytes, which then go on the wire as UTF-8.
   *
   * @param value a checked value
   * @return the value for {@code HttpRequest.Builder.setHeader}
   */
  public static String wire(String value) {
    for (int index = 0; index < value.length(); index++) {
      if (value.charAt(index) > 0x7f) {
        return new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
      }
    }
    return value;
  }

  /** Trims HTTP white space (space, tab, CR, LF) around a value, as the Fetch standard does. */
  private static String trim(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && isHttpSpace(value.charAt(start))) {
      start++;
    }
    while (end > start && isHttpSpace(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(start, end);
  }

  private static boolean isHttpSpace(char c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
  }

  /**
   * Headers by name, in order, where a name replaces an earlier one that differs only in case
   * (keeping the new spelling).
   */
  static final class CaseInsensitiveMap extends LinkedHashMap<String, String> {
    private static final long serialVersionUID = 1L;

    @Override
    public String put(String name, String value) {
      String previous = null;
      for (String key : keySet()) {
        if (key.equalsIgnoreCase(name)) {
          previous = super.remove(key);
          break;
        }
      }
      super.put(name, value);
      return previous;
    }

    String find(String name) {
      for (Map.Entry<String, String> entry : entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
          return entry.getValue();
        }
      }
      return null;
    }
  }
}
