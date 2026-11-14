# Decisions

Decisions about ec.com.flashpi:flashpi-java, the Java SDK, newest first. Its design follows the
Python SDK, [flashpi](https://github.com/flashpi1/flashpi-python), and the Node.js SDK,
[@flashpi/node](https://github.com/flashpi1/flashpi-node), whose decisions
([Python](https://github.com/flashpi1/flashpi-python/blob/main/docs/decisions.md),
[Node](https://github.com/flashpi1/flashpi-node/blob/main/docs/decisions.md)) apply here unless an
entry says otherwise. Entries marked as the owner's were taken with the Flashpi product owner; the
others were chosen while porting, after the Python SDK where the owner had not decided, and are
listed so they can be confirmed or changed before 1.0.

## 2026-11-13: the Java SDK

### The owner's decisions

- **Why Java.** Java is the most used enterprise backend in Ecuador and in the SRI's own
  ecosystem. Kotlin users use this SDK too (no separate Kotlin SDK): each README has a Kotlin
  example.
- **Package and repository.** `ec.com.flashpi:flashpi-java` on Maven Central, package
  `ec.com.flashpi`, public repository `flashpi1/flashpi-java`, MIT license held by SIGNIADIGITAL
  S.A. Java 11 or later (tested on 11, 17, 21 and 25), with the JDK's `java.net.http.HttpClient`.
  First version 0.1.0 through release-please.
- **One dependency.** Jackson 2 (`jackson-databind`).
- **Models.** Classes generated from the OpenAPI document, with typed getters and builders
  (`CreateInvoice.builder()...build()`), friendly to Kotlin; no records, because of Java 11. The
  generator is committed and has a check mode that CI runs.
- **A synchronous client only.**
- **Tooling.** Maven (with the wrapper); JUnit 5; Spotless for the formatting; Error Prone and
  SpotBugs.
- **Publishing.** To Maven Central (the Sonatype Central Portal) from GitHub Actions, behind a
  `maven-central` environment the owner approves. Central has no trusted publishing, so the owner
  keeps the portal's user token and a GPG key as that environment's secrets. The namespace
  `ec.com.flashpi` is verified with a DNS TXT record on flashpi.com.ec.
- **Everything else as the Python SDK.** READMEs in Spanish (`README.md`) and English
  (`README.en.md`); resource style (`flashpi.invoices().create(body)`); an automatic
  Idempotency-Key reused by the retries; up to 2 retries with backoff and jitter; Retry-After up to
  60 s; the wait for a 409 `idempotency_key_in_progress`; the recovery of a document a lost attempt
  created; a retried DELETE answered 404 counts as done; one deadline per attempt; checks of the
  API key and the headers; English messages with the language `es` per client or per call; one
  typed exception per status; `FLASHPI_API_KEY`; automatic pagination; Standard Webhooks
  verification; secrets never in `toString()`, messages or stack traces; release-please from 0.1.0;
  the live API test on the release PR only; the flashpi-bot GitHub App token for the release and
  OpenAPI sync PRs; grouped Dependabot.
- **Answers of 2026-11-13, as recommended.**
  - Dates are `java.time`: `LocalDate` for `date`, `OffsetDateTime` for `date-time`, through the
    SDK's own Jackson (de)serializers, so no Java time module is needed and any `ObjectMapper`
    (Spring's too) reads and writes the models.
  - Enums are Java enums with an `UNKNOWN` constant for a value this version of the SDK does not
    know, so an older SDK never fails to read an answer.
  - Exceptions are unchecked: `FlashpiException extends RuntimeException`.
  - Central publishes a version as soon as it validates it (`publishingType=AUTOMATIC`): the
    approval of the `maven-central` environment is the only gate.

### Chosen while porting (to confirm)

- **Layout.** The client, `RequestOptions`, `Page`, `ApiResponse`, `Webhooks`,
  `CertificateUpload`, `DocumentXml`, `Language` and `Version` are in `ec.com.flashpi`; the
  exceptions in `ec.com.flashpi.exceptions`; the models in `ec.com.flashpi.models`; the resources
  in `ec.com.flashpi.resources`; the engine in `ec.com.flashpi.internal`, whose classes are public
  (other packages use them) but documented as internal and left out of the Javadoc. The jar
  declares the automatic module name `ec.com.flashpi`; there is no `module-info`.
- **The client.** `new Flashpi()` (reads `FLASHPI_API_KEY`), `new Flashpi(apiKey)` and
  `Flashpi.builder()...build()`. It is thread-safe and `AutoCloseable`: `close()` closes the
  HttpClient the SDK created on Java 21 or later, where an HttpClient can be closed (before, its
  connections close once it is unused), and never a caller's. A caller's HttpClient that follows
  redirects is refused (`IllegalArgumentException`): Java cannot turn redirects off per request,
  and a redirect would carry the API key elsewhere. The User-Agent is `flashpi-java/<version>`.
- **Resources.** Methods, not fields: `flashpi.invoices()`, `flashpi.issuers().establishments()
  .emissionPoints()`, `flashpi.webhookEndpoints().deliveries()`; Java names
  (`creditNotes`, `sendEmail`, `getSecret`, `rotateSecret`); `StatusResource` as in Python.
- **Per-call options.** A last `RequestOptions` argument, built with a builder (`idempotencyKey`,
  `timeout`, `maxRetries`, `language`, `correlationId`, `header`/`extraHeaders`); each method also
  has an overload without it, and a list takes `(params)` or `(params, options)` (null params for
  options alone). Timeouts are `java.time.Duration`s. The options are checked when they are built.
- **Exceptions.** `ApiException` with `BadRequestException`, `AuthenticationException`,
  `PermissionDeniedException`, `NotFoundException`, `ConflictException`,
  `UnprocessableEntityException`, `RateLimitException` and `InternalServerException`;
  `ApiConnectionException` and `ApiTimeoutException` (Python's names, so they never shadow
  `java.net.ConnectException` or `java.util.concurrent.TimeoutException`);
  `WebhookVerificationException` with `getReason()`. `getCode()` is the API's code as text (the
  `ErrorCode` enum is on `getProblem().getCode()`), `getRetryAfter()` a `Duration`, `getHeaders()`
  an `HttpHeaders`. `setIdempotencyKey()` is public, as Python's attribute: the SDK sets it on the
  errors of a POST. They survive Java serialization. Misused arguments throw
  `IllegalArgumentException` or `NullPointerException`; bad timeouts, retries, ids, base URLs and
  API keys stay `FlashpiException`s, as in Node and Python. Interrupting a call's thread stops it
  with a `FlashpiException` (with the key) and keeps the interrupt.
- **Headers.** Python's rules, with one difference: values must be ASCII, because the JDK's
  HttpClient writes header values as US-ASCII and sends any other character as `?` (found by the
  network tests). A value with one fails before sending, without showing it. Besides
  `Content-Length` and `Transfer-Encoding`, the headers Java 11's HttpClient refuses (`Connection`,
  `Date`, `Expect`, `From`, `Host`, `Upgrade`, `Via`, `Warning`) are refused on every Java, so the
  SDK behaves the same on all of them.
- **The deadline of an attempt.** Each attempt is sent with `sendAsync`, the request's own
  timeout, and a body subscriber that stops reading (cancelling the subscription, which closes the
  connection) once the deadline passes; the call waits on the future for the time left and checks
  the deadline once the answer is read. The same applies to a caller's HttpClient. A request the
  HttpClient refuses to build is a `FlashpiException` that is not retried, without the client's
  reason, which may quote the header.
- **Models.** A model keeps which members are set (`has("tradeName")`), so a member set to null is
  sent as null and an unset one is not (what a PATCH needs), and an answer is written back as it
  came. Members this version does not know are kept (`getAdditionalProperties()`), and a builder
  can send new ones (`additionalProperty(name, value)`, refused for a member the SDK knows).
  Builders do not check the required members: the API answers `validation_failed`, in the
  requested language. Lists and maps are unmodifiable; models compare by value, are
  `Serializable`, and `toString()` shows a member named `secret` or `password` as `***`. JSON
  that does not fit a member fails with its path (`$.items[0].taxes[0].type`). `integer` is
  `Integer` (a `Long` only beyond its range) and `number` is `Double`: the contract declares some
  whole numbers as `number` (`Document.sequential`, the webhook attempts), which the API could
  declare as `integer`. Amounts stay text, as in the contract: `BigDecimal` would need a
  `format: decimal` there. A `date-time` without an offset is read as UTC.
- **Enums.** Inline enums with the same values share one Java enum, named after the first schema
  and member that has it, or from a short table for the shared ones (`Environment`, `EventType`,
  `EnabledEventType`, `PayloadMode`, `IdType`, `PaymentMethod`, `RideStatus`, `TaxType`,
  `RimpeRegime`, `WebhookDeliveryStatus`). Constants are the values in upper snake case; `*` is
  `ALL` and a number gets its member as prefix (`RATE_15`). `UNKNOWN` cannot be sent
  (`IllegalArgumentException`); an enum whose own values include `unknown`
  (`IssuerOnboardingSriProfileRimpeRegime`) uses that constant for unknown values too. A model
  keeps the value as it came, so an `UNKNOWN` is written back as the API sent it. `Language` is
  an enum (`EN`, `ES`).
- **Unions.** `Event` is an interface of `DocumentEvent`, `CertificateEvent` and `AccountEvent`;
  `WebhookEvent`, the SDK's own (Python's hand-written type), adds
  `WebhookEndpointVerificationEvent`. An event of a type this version does not know, or without
  a type, is read as an `EventThin`. Both interfaces declare the getters all their classes share
  (`getId()`, `getEnvironment()`, `getCreatedAt()`). A thin webhook is read as its class with
  `getData()` null.
- **Queries.** One class per list query, named after its operation (`DocumentsListParams`,
  `WebhookEndpointsDeliveriesListParams`), encoded as Python's `urlencode()`.
- **Pages.** `Page<T>` is `Iterable` over every item of every page, as Python's `for`; `stream()`
  does the same lazily; `getData()`, `hasMore()`, `getNextCursor()`, `hasNextPage()` and
  `nextPage()` (null after the last page) move one page at a time. Walking twice fetches the
  following pages again.
- **withResponse().** Each resource has a view with the same methods returning
  `ApiResponse<T>`: the data, the status, the response headers and the request id. The JDK's
  `HttpResponse` is not exposed: its request holds the Authorization header.
- **Webhooks.** `Webhooks` takes the tolerance and a `Clock` in its constructor (Python's per-call
  `tolerance` and `now`), and has `unwrap()` and `verifySignature()` for a `byte[]` or `String`
  body, with the headers as any `Map` (values as text or lists of text) or as a lookup such as a
  servlet's `request::getHeader`. The checks keep Python's order. A body that is not a JSON object,
  or an event the SDK cannot read, is `INVALID_PAYLOAD`. The decoded key and the expected
  signature are cleared once used.
- **Certificates.** `CertificateUpload` reads a `Path`, an `InputStream` (not closed) or bytes when
  it is built, up to one byte past 1 MiB; the password is a `String` or a `char[]`. The file name
  defaults to the `Path`'s name, else `certificate.p12`. The upload is checked when it is sent
  (`FlashpiException`), as in Python; a read error is an `UncheckedIOException`. The SDK clears its
  copies of the password and the file once the body is built, and the file name is escaped as in
  the Python and PHP SDKs.
- **Secrets in stack traces.** Java's stack traces carry no local variables, so Python's work on
  frame locals has no counterpart. The API key lives in the client's configuration only; no
  exception, `toString()` or log of the SDK shows it, a webhook secret, a signature, a .p12 or its
  password.
- **The generator.** `scripts/OpenApi.java` is a single-file JDK program (its own small JSON
  reader, no dependency), run with `java scripts/OpenApi.java sync|generate|check`. It writes the
  models to `src/generated/java` (same package as the hand-written base classes), with a marker
  line; it deletes the generated files it no longer produces and never touches a hand-written one.
  The generated code is left out of Spotless and Error Prone.
- **Nullability.** No nullability annotations: JSpecify would be a second dependency. Kotlin sees
  the getters as platform types.
- **Tooling.** google-java-format (Google style) 1.36.1 through Spotless 3.10.4 (1.37 needs a
  library this Spotless does not load); Error Prone on JDK 21 or later; javac with `-Xlint:all` and
  every warning as an error; SpotBugs at the maximum effort and the lowest threshold, with the
  intended findings in `spotbugs-exclude.xml`; JaCoCo gates the coverage at 95 % of the lines and
  70 % of the branches (the generated enums' null checks are most of the rest); JUnit 5 (JUnit 6
  needs Java 17). Plugins and tools are pinned to releases out for at least two weeks.
- **CI.** Formatting, the generator's check, Error Prone, the tests with coverage, the Javadoc and
  SpotBugs on JDK 21; the tests on Java 11, 17, 21 and 25; the tests on Java 11 with Jackson 2.13.5,
  the one Spring Boot 2.7 ships (the oldest the SDK supports); the Kotlin README examples, compiled
  by the `kotlin` profile (Kotlin 2.4.21, never a dependency of the SDK); and a build whose jars are
  checked file by file (`scripts/PackageCheck.java`) and whose jar is used alone, with Jackson only,
  on Java 11 and 25 (`scripts/InstalledCheck.java`).
- **README examples.** `ReadmeTest` compiles every java block of both READMEs with the JDK's
  compiler (statements inside a method, classes as they are), against small stubs of the Spring
  classes the webhook example uses, and keeps each Kotlin example in step with
  `src/test/kotlin/readme`. The READMEs link to each other and to the license with absolute URLs.
- **Releases.** release-please's `maven` type with `skip-snapshot` (no SNAPSHOT pull requests)
  writes the version to `pom.xml`; `Version.java` and the install lines of the READMEs are extra
  files with an `x-release-please-version` marker. A build job, with a read-only token and no
  secret, builds the tag with the wrapper's Maven and no cache, checks the jars and the version, and
  hands the jar, the sources, the Javadoc and `pom.xml` over. The publish job, the only one in the
  `maven-central` environment, checks out nothing and runs no code of the repository or of its
  dependencies: with the runner's gpg, md5sum, sha1sum, zip, curl and jq it signs the four files,
  bundles them in the Maven layout, uploads the bundle to the Central Portal API and waits up to
  30 minutes until Central publishes it. Its secrets are `CENTRAL_TOKEN_USERNAME`,
  `CENTRAL_TOKEN_PASSWORD`, `MAVEN_GPG_PRIVATE_KEY` and `MAVEN_GPG_PASSPHRASE`. The published POM is
  the project's `pom.xml`, with its build section. Maven Central shows no README.
- **Live suite.** `LiveTest`, tagged `live`, runs with `./mvnw verify -Plive`; without its
  variables it fails instead of skipping, so a misconfigured release check is seen.
- **Dependabot.** Jackson in a group of its own, the build and test tools in another, and the
  GitHub Actions in a third.
