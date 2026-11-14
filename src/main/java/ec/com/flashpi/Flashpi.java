package ec.com.flashpi;

import ec.com.flashpi.exceptions.FlashpiException;
import ec.com.flashpi.internal.ClientConfig;
import ec.com.flashpi.internal.Engine;
import ec.com.flashpi.internal.Headers;
import ec.com.flashpi.internal.Settings;
import ec.com.flashpi.internal.Timing;
import ec.com.flashpi.models.Environment;
import ec.com.flashpi.resources.CreditNotes;
import ec.com.flashpi.resources.DebitNotes;
import ec.com.flashpi.resources.Documents;
import ec.com.flashpi.resources.Events;
import ec.com.flashpi.resources.Invoices;
import ec.com.flashpi.resources.IssuerOnboardings;
import ec.com.flashpi.resources.Issuers;
import ec.com.flashpi.resources.PurchaseSettlements;
import ec.com.flashpi.resources.StatusResource;
import ec.com.flashpi.resources.Waybills;
import ec.com.flashpi.resources.WebhookEndpoints;
import ec.com.flashpi.resources.Withholdings;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The Flashpi API client.
 *
 * <pre>{@code
 * Flashpi flashpi = new Flashpi(); // reads FLASHPI_API_KEY
 * Document invoice = flashpi.invoices().create(body);
 * }</pre>
 *
 * <p>An {@code fp_test_} key issues in the SRI test environment (pruebas); an {@code fp_live_} key
 * issues real documents in production. Use it on servers only: the key must stay secret. A client
 * is thread-safe and keeps a pool of connections: create one and share it.
 */
public final class Flashpi implements AutoCloseable {
  /** The API's address. */
  public static final String DEFAULT_BASE_URL = "https://api.flashpi.com.ec";

  /** How long each attempt of a request may take, from sending it to reading the whole answer. */
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

  /** Retries after network errors, timeouts, 429 and 5xx. */
  public static final int DEFAULT_MAX_RETRIES = 2;

  /** What an API key is made of: visible ASCII, without spaces. */
  private static final Pattern API_KEY_CHARACTERS = Pattern.compile("[\\x21-\\x7e]+");

  /**
   * What JavaScript's {@code trim()} removes around a string, as the Node SDK does with the key:
   * its white space and line breaks, and the byte order mark.
   */
  private static final Pattern AROUND =
      Pattern.compile(
          "\\A[\\t\\n\\x0B\\f\\r \\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+"
              + "|[\\t\\n\\x0B\\f\\r \\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+\\z");

  private final Environment environment;
  private final ClientConfig config;
  private final HttpClient http;
  private final boolean ownsHttpClient;

  private final Invoices invoices;
  private final CreditNotes creditNotes;
  private final DebitNotes debitNotes;
  private final PurchaseSettlements purchaseSettlements;
  private final Waybills waybills;
  private final Withholdings withholdings;
  private final Documents documents;
  private final Issuers issuers;
  private final IssuerOnboardings issuerOnboardings;
  private final WebhookEndpoints webhookEndpoints;
  private final Events events;
  private final StatusResource status;
  private final Webhooks webhooks = new Webhooks();

  /** A client with the key of the {@code FLASHPI_API_KEY} environment variable. */
  public Flashpi() {
    this(builder());
  }

  /**
   * A client with an API key.
   *
   * @param apiKey the key ({@code fp_test_...} or {@code fp_live_...})
   */
  public Flashpi(String apiKey) {
    this(builder().apiKey(apiKey));
  }

  private Flashpi(Builder builder) {
    String apiKey = apiKey(builder.apiKey, builder.environmentVariables);
    Map<String, String> headers = Headers.checked("defaultHeaders", builder.defaultHeaders);
    for (String name : headers.keySet()) {
      if (name.equalsIgnoreCase("idempotency-key")) {
        throw new FlashpiException(
            "defaultHeaders cannot carry an Idempotency-Key: it must be unique per operation. Set it"
                + " on each create() with RequestOptions instead");
      }
    }
    String baseUrl = baseUrl(builder.baseUrl == null ? DEFAULT_BASE_URL : builder.baseUrl);
    Duration timeout =
        Settings.timeout("timeout", builder.timeout == null ? DEFAULT_TIMEOUT : builder.timeout);
    int maxRetries =
        Settings.retries(
            "maxRetries", builder.maxRetries == null ? DEFAULT_MAX_RETRIES : builder.maxRetries);
    if (builder.httpClient != null
        && builder.httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
      throw new IllegalArgumentException(
          "The HttpClient must not follow redirects (they would carry the API key elsewhere): build it"
              + " with followRedirects(HttpClient.Redirect.NEVER)");
    }
    this.config = new ClientConfig(apiKey, baseUrl, timeout, maxRetries, builder.language, headers);
    this.environment = apiKey.startsWith("fp_test_") ? Environment.TEST : Environment.LIVE;
    this.ownsHttpClient = builder.httpClient == null;
    this.http =
        builder.httpClient != null
            ? builder.httpClient
            : HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    Engine engine = new Engine(config, http, builder.timing);
    this.invoices = new Invoices(engine);
    this.creditNotes = new CreditNotes(engine);
    this.debitNotes = new DebitNotes(engine);
    this.purchaseSettlements = new PurchaseSettlements(engine);
    this.waybills = new Waybills(engine);
    this.withholdings = new Withholdings(engine);
    this.documents = new Documents(engine);
    this.issuers = new Issuers(engine);
    this.issuerOnboardings = new IssuerOnboardings(engine);
    this.webhookEndpoints = new WebhookEndpoints(engine);
    this.events = new Events(engine);
    this.status = new StatusResource(engine);
  }

  /**
   * A builder for a client with other settings.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * The mode of the API key.
   *
   * @return {@code TEST} for fp_test_ keys (SRI pruebas), {@code LIVE} for fp_live_ keys (SRI
   *     production)
   */
  public Environment getEnvironment() {
    return environment;
  }

  /**
   * Invoices (facturas).
   *
   * @return the resource
   */
  public Invoices invoices() {
    return invoices;
  }

  /**
   * Credit notes (notas de crédito).
   *
   * @return the resource
   */
  public CreditNotes creditNotes() {
    return creditNotes;
  }

  /**
   * Debit notes (notas de débito).
   *
   * @return the resource
   */
  public DebitNotes debitNotes() {
    return debitNotes;
  }

  /**
   * Purchase settlements (liquidaciones de compra).
   *
   * @return the resource
   */
  public PurchaseSettlements purchaseSettlements() {
    return purchaseSettlements;
  }

  /**
   * Waybills (guías de remisión).
   *
   * @return the resource
   */
  public Waybills waybills() {
    return waybills;
  }

  /**
   * Withholding receipts (comprobantes de retención).
   *
   * @return the resource
   */
  public Withholdings withholdings() {
    return withholdings;
  }

  /**
   * Every issued document: search, XML, RIDE (PDF) and email.
   *
   * @return the resource
   */
  public Documents documents() {
    return documents;
  }

  /**
   * Issuers (RUCs) with their establishments, emission points and certificates.
   *
   * @return the resource
   */
  public Issuers issuers() {
    return issuers;
  }

  /**
   * Create an issuer from its .p12 certificate.
   *
   * @return the resource
   */
  public IssuerOnboardings issuerOnboardings() {
    return issuerOnboardings;
  }

  /**
   * The URLs Flashpi sends events to.
   *
   * @return the resource
   */
  public WebhookEndpoints webhookEndpoints() {
    return webhookEndpoints;
  }

  /**
   * Events of documents, certificates and email sending.
   *
   * @return the resource
   */
  public Events events() {
    return events;
  }

  /**
   * The API's status.
   *
   * @return the resource
   */
  public StatusResource status() {
    return status;
  }

  /**
   * Verify the signature of the webhooks you receive (no API key needed; {@code new Webhooks()}
   * does the same).
   *
   * @return the verifier, with the default tolerance
   */
  public Webhooks webhooks() {
    return webhooks;
  }

  /**
   * Closes the connections of the HttpClient the SDK created (on Java 21 or later, where an
   * HttpClient can be closed; before, they close once the client is unused). An HttpClient of yours
   * is left open.
   */
  @Override
  public void close() {
    if (ownsHttpClient && http instanceof AutoCloseable) {
      try {
        ((AutoCloseable) http).close();
      } catch (Exception error) {
        throw new FlashpiException("Could not close the HTTP client", error);
      }
    }
  }

  /** The mode and the address; never the API key. */
  @Override
  public String toString() {
    return "Flashpi{environment=" + environment.value() + ", baseUrl=" + config.baseUrl() + "}";
  }

  /** The key: the argument or FLASHPI_API_KEY, trimmed as JavaScript's trim(), and checked. */
  private static String apiKey(String apiKey, Function<String, String> environmentVariables) {
    String value = apiKey != null ? apiKey : environmentVariables.apply("FLASHPI_API_KEY");
    String key = value == null ? "" : AROUND.matcher(value).replaceAll("");
    if (key.isEmpty()) {
      throw new FlashpiException(
          "Missing API key: pass it to new Flashpi(apiKey) or set the FLASHPI_API_KEY environment variable");
    }
    if (key.startsWith("whsec_")) {
      throw new FlashpiException(
          "That is a webhook signing secret (whsec_...), not an API key: use it with flashpi.webhooks()");
    }
    if (!key.startsWith("fp_test_") && !key.startsWith("fp_live_")) {
      throw new FlashpiException("The API key must start with fp_test_ or fp_live_");
    }
    if (!API_KEY_CHARACTERS.matcher(key).matches()) {
      // Never the key: the HTTP client would refuse it in the Authorization header.
      throw new FlashpiException(
          "The API key has spaces, line breaks or other characters an API key cannot have: copy it"
              + " again from the Flashpi console");
    }
    return key;
  }

  /** An http(s) URL with a host, without the trailing slashes. */
  private static String baseUrl(String url) {
    try {
      URI uri = new URI(url);
      String scheme =
          uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
      if ((scheme.equals("http") || scheme.equals("https"))
          && uri.getHost() != null
          && uri.getRawQuery() == null
          && uri.getRawFragment() == null) {
        return url.replaceAll("/+$", "");
      }
    } catch (URISyntaxException error) {
      // Reported below.
    }
    throw new FlashpiException(
        "baseUrl must be an http:// or https:// URL with a host, got \"" + url + "\"");
  }

  /** Builds a {@link Flashpi} client. Every setting left unset keeps its default. */
  public static final class Builder {
    private String apiKey;
    private String baseUrl;
    private Duration timeout;
    private Integer maxRetries;
    private Language language;
    private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
    private HttpClient httpClient;
    private Timing timing = Timing.system();
    private Function<String, String> environmentVariables = System::getenv;

    private Builder() {}

    /**
     * The API key ({@code fp_test_...} or {@code fp_live_...}).
     *
     * @param apiKey the key, or null for the FLASHPI_API_KEY environment variable
     * @return this builder
     */
    public Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    /**
     * The API's address (default https://api.flashpi.com.ec).
     *
     * @param baseUrl an http:// or https:// URL
     * @return this builder
     */
    public Builder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    /**
     * How long each attempt of a request may take, from sending it to reading the whole answer
     * (default 60 seconds). Issuing with {@code options.waitForAuthorization} raises it to the wait
     * plus 15 seconds.
     *
     * @param timeout more than zero and at most 2147483.647 seconds
     * @return this builder
     */
    public Builder timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    /**
     * Retries after network errors, timeouts, 429 and 5xx (default 2).
     *
     * @param maxRetries 0 or more; 0 turns them off
     * @return this builder
     */
    public Builder maxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    /**
     * The language of the API's messages (default English). Error codes do not change.
     *
     * @param language the language
     * @return this builder
     */
    public Builder language(Language language) {
      this.language = language;
      return this;
    }

    /**
     * A header sent with every request. Not {@code Idempotency-Key}: that one is per request.
     *
     * @param name the header's name
     * @param value its value
     * @return this builder
     */
    public Builder defaultHeader(String name, String value) {
      defaultHeaders.put(
          Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
      return this;
    }

    /**
     * Headers sent with every request, added to the ones set before.
     *
     * @param headers the headers by name
     * @return this builder
     */
    public Builder defaultHeaders(Map<String, String> headers) {
      defaultHeaders.putAll(headers);
      return this;
    }

    /**
     * The HttpClient to send the requests with (proxies, TLS, executors). It must not follow
     * redirects, and you close it.
     *
     * @param httpClient the client
     * @return this builder
     */
    public Builder httpClient(HttpClient httpClient) {
      this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
      return this;
    }

    /** The clocks and waits of the requests, for tests. */
    Builder timing(Timing timing) {
      this.timing = Objects.requireNonNull(timing);
      return this;
    }

    /** Where the environment variables come from, for tests. */
    Builder environmentVariables(Function<String, String> environmentVariables) {
      this.environmentVariables = Objects.requireNonNull(environmentVariables);
      return this;
    }

    /**
     * Builds the client, checking the settings.
     *
     * @return the client
     * @throws FlashpiException for a missing or bad API key, base URL, timeout or retries
     * @throws IllegalArgumentException for a header that cannot be sent, or an HttpClient that
     *     follows redirects
     */
    public Flashpi build() {
      return new Flashpi(this);
    }
  }
}
