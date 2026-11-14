package ec.com.flashpi.internal;

import ec.com.flashpi.Language;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The settings of a client that the requests need, checked by the client's builder. Its {@link
 * #toString()} never shows the API key.
 *
 * <p>Internal: not part of the SDK's API, and may change in any version.
 */
public final class ClientConfig {
  private final String apiKey;
  private final String baseUrl;
  private final Duration timeout;
  private final int maxRetries;
  private final Language language;
  private final Map<String, String> defaultHeaders;

  /**
   * The settings.
   *
   * @param apiKey the checked API key
   * @param baseUrl the API's address, without a trailing slash
   * @param timeout the timeout of each attempt
   * @param maxRetries the retries after transient failures
   * @param language the language of the API's messages, or null
   * @param defaultHeaders checked headers for every request
   */
  public ClientConfig(
      String apiKey,
      String baseUrl,
      Duration timeout,
      int maxRetries,
      Language language,
      Map<String, String> defaultHeaders) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
    this.timeout = timeout;
    this.maxRetries = maxRetries;
    this.language = language;
    this.defaultHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(defaultHeaders));
  }

  String apiKey() {
    return apiKey;
  }

  /**
   * The API's address.
   *
   * @return the base URL, without a trailing slash
   */
  public String baseUrl() {
    return baseUrl;
  }

  Duration timeout() {
    return timeout;
  }

  int maxRetries() {
    return maxRetries;
  }

  Language language() {
    return language;
  }

  Map<String, String> defaultHeaders() {
    return defaultHeaders;
  }

  @Override
  public String toString() {
    return "ClientConfig{baseUrl="
        + baseUrl
        + ", timeout="
        + timeout
        + ", maxRetries="
        + maxRetries
        + "}";
  }
}
