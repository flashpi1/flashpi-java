/*
 * Checks what Maven Central would get, file by file:
 *
 *   ./mvnw -B package -DskipTests
 *   java scripts/PackageCheck.java [tag]
 *
 * The jar holds the SDK's classes, its license and Maven's metadata, and declares the automatic
 * module name; the sources jar holds the hand-written and the generated sources; the Javadoc jar
 * documents the public packages only. With a tag (v0.1.0), the version must be the tag's.
 * A single-file JDK program: no dependency, no build.
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class PackageCheck {
  private static final List<String> problems = new ArrayList<>();

  public static void main(String[] args) throws IOException {
    String pom = Files.readString(Paths.get("pom.xml"), StandardCharsets.UTF_8);
    Matcher match =
        Pattern.compile("<artifactId>flashpi-java</artifactId>\\s*<version>([^<]+)</version>")
            .matcher(pom);
    if (!match.find()) {
      throw new IllegalStateException("pom.xml has no version after its artifactId");
    }
    String version = match.group(1);
    if (args.length > 0 && !args[0].equals("v" + version)) {
      fail("The tag " + args[0] + " is not the version of pom.xml (" + version + ")");
    }
    Path target = Paths.get("target");
    String base = "flashpi-java-" + version;

    List<String> classes = entries(target.resolve(base + ".jar"));
    expect(
        classes,
        "META-INF/MANIFEST.MF",
        "META-INF/LICENSE",
        "META-INF/maven/ec.com.flashpi/flashpi-java/pom.xml",
        "ec/com/flashpi/Flashpi.class",
        "ec/com/flashpi/Version.class",
        "ec/com/flashpi/models/ModelRegistry.class",
        "ec/com/flashpi/models/Document.class",
        "ec/com/flashpi/internal/Engine.class",
        "ec/com/flashpi/exceptions/ApiException.class");
    for (String entry : classes) {
      boolean allowed =
          entry.startsWith("META-INF/")
              || (entry.startsWith("ec/com/flashpi/")
                  && (entry.endsWith(".class") || entry.endsWith("/")))
              || entry.equals("ec/")
              || entry.equals("ec/com/");
      if (!allowed
          || entry.contains("Test")
          || entry.contains("Fake")
          || entry.startsWith("readme")) {
        fail(base + ".jar should not hold " + entry);
      }
    }
    try (JarFile jar = new JarFile(target.resolve(base + ".jar").toFile())) {
      Manifest manifest = jar.getManifest();
      String module = manifest.getMainAttributes().getValue("Automatic-Module-Name");
      if (!"ec.com.flashpi".equals(module)) {
        fail("The jar's Automatic-Module-Name is " + module + ", not ec.com.flashpi");
      }
    }

    List<String> sources = entries(target.resolve(base + "-sources.jar"));
    expect(
        sources,
        "META-INF/LICENSE",
        "ec/com/flashpi/Flashpi.java",
        "ec/com/flashpi/models/FlashpiModel.java",
        "ec/com/flashpi/models/Document.java",
        "ec/com/flashpi/models/ModelRegistry.java");

    // The pages are under the automatic module's name (ec.com.flashpi/ec/com/flashpi/...).
    List<String> javadoc = entries(target.resolve(base + "-javadoc.jar"));
    expect(javadoc, "index.html");
    for (String page :
        List.of("ec/com/flashpi/Flashpi.html", "ec/com/flashpi/models/Document.html")) {
      if (javadoc.stream().noneMatch(entry -> entry.endsWith(page))) {
        fail("The Javadoc has no " + page);
      }
    }
    for (String entry : javadoc) {
      if (entry.contains("ec/com/flashpi/internal/")) {
        fail("The Javadoc documents the internal package: " + entry);
      }
    }

    String code =
        Files.readString(
            Paths.get("src/main/java/ec/com/flashpi/Version.java"), StandardCharsets.UTF_8);
    if (!code.contains("VERSION = \"" + version + "\"")) {
      fail("Version.VERSION is not " + version + ": release-please updates both");
    }

    if (!problems.isEmpty()) {
      System.err.println(String.join("\n", problems));
      System.exit(1);
    }
    System.out.println(base + ": the jar, the sources and the Javadoc are as expected");
  }

  private static List<String> entries(Path jar) throws IOException {
    if (!Files.isRegularFile(jar)) {
      fail("Missing " + jar);
      return Collections.emptyList();
    }
    try (JarFile file = new JarFile(jar.toFile())) {
      return file.stream().map(entry -> entry.getName()).collect(Collectors.toList());
    }
  }

  private static void expect(List<String> entries, String... names) {
    for (String name : names) {
      if (!entries.contains(name)) {
        fail("Missing " + name);
      }
    }
  }

  private static void fail(String problem) {
    problems.add(problem);
  }
}
