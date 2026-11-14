# Contributing to flashpi-java

Read [docs/decisions.md](docs/decisions.md) first: it records why the SDK is built the way it
is. Decisions about the product (names, behavior, releases) belong to the Flashpi team: ask
before changing them.

## Setup

A JDK 21 or later (the build targets Java 11; Error Prone and the formatter need 21). Maven comes
with the wrapper, pinned by its SHA-256: use `./mvnw`, not your own `mvn`.

## Checks

Run the same checks as CI before every commit:

```bash
java scripts/OpenApi.java check && ./mvnw spotless:check && ./mvnw verify && ./mvnw spotbugs:check
```

- `./mvnw verify` compiles with `-Xlint:all` and Error Prone (every warning fails the build; add
  `-Dcompiler.failOnWarning=false` to see them all at once), runs the tests with the coverage gate
  (95 % of the lines, 70 % of the branches) and builds the Javadoc. `./mvnw spotless:apply`
  formats the code (google-java-format); the generated models are left alone.
- The tests run without a network, on a fake `HttpClient` that drives the SDK's body handlers,
  except `NetworkTest`, which starts a server on localhost.
- CI also runs the tests on Java 11, 17, 21 and 25, and on Java 11 with Jackson 2.13.5
  (`./mvnw verify -Djackson.version=2.13.5`), the oldest the SDK supports.
- `ReadmeTest` compiles every java block of both READMEs. A block is either a class or statements,
  which run inside a method where `flashpi`, `body`, `invoice`, `documentId` and `order` exist.
  After changing a Kotlin example, run `./mvnw test -Dtest=ReadmeTest -Dreadme.update=true` and
  `./mvnw -Pkotlin test-compile`, which compiles it.
- This builds the jars and checks them file by file, as the release does:

  ```bash
  ./mvnw package -DskipTests && java scripts/PackageCheck.java
  ```

- `./mvnw verify -Plive` calls the real API in test mode. It needs `FLASHPI_TEST_API_KEY` (an
  `fp_test_` key, read_write) and `FLASHPI_DEMO_ISSUER_ID` (in CI, an organization secret and
  variable shared with the other SDKs), and issues one test invoice on the demo issuer, which has
  a daily quota: run it only when needed.

## Code conventions

- English for code, identifiers, comments and commit messages. The READMEs are in Spanish
  (`README.md`) and English (`README.en.md`); keep both in step.
- Java 11 (`--release 11`): no records, no `var` in the public API, no APIs newer than Java 11.
- The SDK has **one runtime dependency**, Jackson 2 (`jackson-databind`), and must work with any
  2.x from 2.13 on: do not use newer Jackson APIs (`JsonNode.properties()` is 2.15, for example).
- Retries, idempotency, recoveries and errors live once, in `internal/Engine.java`. Each operation
  is declared once, as a `RequestSpec` in its resource, and each method of a resource has a twin in
  its `withResponse()` view. `OperationsTest` checks that every operation of the OpenAPI document
  has exactly one call, and that every view mirrors its resource.
- Resource methods mirror the API: `create`, `list`, `get`, `update`, `delete`, then actions named
  after the operation (`sendEmail`, `rotateSecret`, `replay`, ...). Path ids come first, then the
  body or the filters, then `RequestOptions`.

## The API contract

`openapi/openapi.json` is a copy of the public document at
https://flashpi.com.ec/docs/openapi.json and `src/generated/java` is generated from it. Never edit
either by hand:

```bash
java scripts/OpenApi.java sync      # download the document and regenerate the models
java scripts/OpenApi.java generate  # regenerate from the committed copy
```

The `OpenAPI sync` workflow does this every day and opens a pull request when the contract
changes. New operations or fields may need a method or a hand-written type in `src/main/java`.
When the generation fails because an enum that several places share changed, update
`SHARED_ENUMS` in the generator: keep each public enum's name where you can, since renaming one
breaks the code of the SDK's users. `GeneratorTest` runs the generator on contracts it must refuse
or escape.

## Commits and releases

Commit with [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`,
`docs:`, `test:`, `ci:`, `chore:`). They drive the version and the changelog:
[release-please](https://github.com/googleapis/release-please) keeps a release pull request open,
and merging it tags the version and publishes it to Maven Central from the `Release` workflow.
release-please writes the version to `pom.xml`, `src/main/java/ec/com/flashpi/Version.java` and
the install lines of both READMEs.

While the version is 0.x, `feat:` raises the minor version and `fix:` the patch version.

The release PR and the OpenAPI sync PR are opened with a token of the organization's GitHub App,
so CI runs on them like on any pull request. (One opened with `GITHUB_TOKEN` would get its CI only
after someone approves it.) The Release workflow also starts the live API test (`live.yml`) on the
release branch each time release-please updates it. A dispatched `ci.yml` run does not count for a
pull request's required checks: to run CI again, re-run the PR's own checks.

### The GitHub App

An app of the `flashpi1` organization (flashpi-bot), installed on `flashpi-java` as on the other
SDKs, with no webhook and these repository permissions: Contents, Pull requests and Issues, read
and write. Its Client ID is the organization variable `FLASHPI_BOT_CLIENT_ID` and its private key
the organization secret `FLASHPI_BOT_PRIVATE_KEY`. The workflows create a one-hour token per run
with only the permissions each job needs.

### Maven Central

Central (the Sonatype Central Portal) has no trusted publishing, so the publish job uses a
portal user token and a GPG key, kept as secrets of the `maven-central` environment, which waits
for a maintainer's approval before each publish and only runs for `main`:

- `CENTRAL_TOKEN_USERNAME` and `CENTRAL_TOKEN_PASSWORD`: a user token generated at
  https://central.sonatype.com/usertoken (not the account's password).
- `MAVEN_GPG_PRIVATE_KEY` (the ASCII-armored private key) and `MAVEN_GPG_PASSPHRASE`. Its public
  key must be on a public keyserver (`keys.openpgp.org`), where Central looks it up.

The namespace `ec.com.flashpi` is verified on the portal with a TXT record on flashpi.com.ec.

The release builds in two jobs. The build job checks out the tag, builds the jar, the sources and
the Javadoc with the wrapper's Maven and no cache, and checks them (`scripts/PackageCheck.java`),
with a read-only token and no secret. The publish job checks out nothing and runs no code of this
repository or of its dependencies: with the runner's own gpg, md5sum, sha1sum, zip, curl and jq it
signs the four files, bundles them in the Maven layout and uploads the bundle with
`publishingType=AUTOMATIC`, then waits until Central publishes it. A version on Central can never
be changed or deleted. The build job keeps the files for 30 days, as long as GitHub waits for the
approval; if the publish job fails because they expired, re-run the "Build the release" job alone
(not "Re-run all jobs", which skips the release once it exists).

To rotate the GPG key, publish the new public key, replace both GPG secrets, and keep the old public
key on the keyserver: the versions it signed still point to it.
