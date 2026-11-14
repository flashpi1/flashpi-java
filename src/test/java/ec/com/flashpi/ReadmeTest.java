package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every java block of both READMEs compiles against the SDK, and every Kotlin block matches the
 * file in src/test/kotlin/readme that the kotlin profile compiles. Run with -Dreadme.update=true to
 * rewrite those files after changing a README.
 */
class ReadmeTest {
  private static final Pattern BLOCK =
      Pattern.compile("```(java|kotlin)\\n(.*?)```", Pattern.DOTALL);
  private static final List<String> READMES = List.of("README.md", "README.en.md");

  /**
   * What a statement block may use from the blocks before it, as fields its own variables can
   * shadow.
   */
  private static final String CONTEXT =
      "  ec.com.flashpi.Flashpi flashpi;\n"
          + "  ec.com.flashpi.models.CreateInvoice body;\n"
          + "  ec.com.flashpi.models.Document invoice;\n"
          + "  String documentId;\n"
          + "  Order order;\n\n"
          + "  static final class Order {\n"
          + "    String getId() {\n"
          + "      return \"42\";\n"
          + "    }\n"
          + "  }\n\n"
          + "  void saveForRetry(ec.com.flashpi.models.CreateInvoice body, String key) {}\n\n";

  static List<String[]> blocks(String readme, String language) throws IOException {
    String text = Files.readString(Paths.get(readme), StandardCharsets.UTF_8);
    List<String[]> blocks = new ArrayList<>();
    Matcher matcher = BLOCK.matcher(text);
    while (matcher.find()) {
      if (matcher.group(1).equals(language)) {
        blocks.add(new String[] {matcher.group(2)});
      }
    }
    return blocks;
  }

  /** A block as a compilation unit: a class as it is, statements inside a method. */
  static String unit(String block, String pkg) {
    List<String> imports = new ArrayList<>();
    List<String> body = new ArrayList<>();
    for (String line : block.split("\n", -1)) {
      if (line.startsWith("import ") && body.stream().allMatch(String::isBlank)) {
        imports.add(line);
      } else {
        body.add(line);
      }
    }
    String code = String.join("\n", body).trim();
    String head = "package " + pkg + ";\n\n" + String.join("\n", imports) + "\n\n";
    if (Pattern.compile("^(@\\w+\\s+)*(public\\s+)?(final\\s+)?class ", Pattern.MULTILINE)
        .matcher(code)
        .find()) {
      return head + code + "\n";
    }
    return head
        + "@SuppressWarnings(\"unused\")\nclass Example {\n"
        + CONTEXT
        + "  void run() throws Exception {\n"
        + code
        + "\n  }\n}\n";
  }

  @Test
  void bothReadmesHaveTheSameExamples() throws IOException {
    for (String language : List.of("java", "kotlin")) {
      assertEquals(
          blocks(READMES.get(0), language).size(),
          blocks(READMES.get(1), language).size(),
          language);
    }
    assertTrue(blocks("README.md", "java").size() >= 10);
  }

  @Test
  void theJavaExamplesCompile(@TempDir Path output) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "the tests need a JDK");
    List<JavaFileObject> sources = new ArrayList<>();
    for (String readme : READMES) {
      String lang = readme.equals("README.md") ? "es" : "en";
      List<String[]> blocks = blocks(readme, "java");
      for (int index = 0; index < blocks.size(); index++) {
        String pkg = "readme." + lang + ".b" + (index + 1);
        String unit = unit(blocks.get(index)[0], pkg);
        Matcher type =
            Pattern.compile(
                    "^(?:@\\w+\\s+)*(?:public\\s+)?(?:final\\s+)?class (\\w+)", Pattern.MULTILINE)
                .matcher(unit);
        String name = type.find() ? type.group(1) : "Example";
        sources.add(source(pkg.replace('.', '/') + "/" + name + ".java", unit));
      }
    }
    Path stubs = Paths.get("src/test/resources/readme-stubs");
    try (Stream<Path> files = Files.walk(stubs)) {
      for (Path file :
          files.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList())) {
        sources.add(
            source(
                stubs.relativize(file).toString(), Files.readString(file, StandardCharsets.UTF_8)));
      }
    }
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager files =
        compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
      List<String> options =
          List.of(
              "--release",
              "11",
              "-Xlint:all,-processing",
              "-classpath",
              classpath(),
              "-d",
              output.toString());
      boolean compiled = compiler.getTask(null, files, diagnostics, options, null, sources).call();
      String report =
          diagnostics.getDiagnostics().stream()
              .filter(diagnostic -> diagnostic.getKind() != Diagnostic.Kind.NOTE)
              .map(
                  d ->
                      d.getSource().getName()
                          + ":"
                          + d.getLineNumber()
                          + ": "
                          + d.getMessage(Locale.ROOT))
              .collect(Collectors.joining("\n"));
      assertTrue(compiled && report.isEmpty(), report);
    }
  }

  @Test
  void theKotlinExamplesMatchTheFilesTheKotlinProfileCompiles() throws IOException {
    boolean update = Boolean.getBoolean("readme.update");
    for (String readme : READMES) {
      String lang = readme.equals("README.md") ? "es" : "en";
      List<String> examples =
          blocks(readme, "kotlin").stream()
              .map(block -> block[0])
              .filter(block -> block.contains("import ec.com.flashpi"))
              .collect(Collectors.toList());
      assertEquals(1, examples.size(), readme);
      Path file = Paths.get("src/test/kotlin/readme/" + lang + "/Readme.kt");
      String expected = "package readme." + lang + "\n\n" + examples.get(0);
      if (update) {
        Files.createDirectories(file.getParent());
        Files.writeString(file, expected, StandardCharsets.UTF_8);
      }
      assertEquals(
          expected,
          Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "",
          file + " (run with -Dreadme.update=true)");
    }
  }

  /** The SDK's classes and Jackson's jars. */
  private static String classpath() throws URISyntaxException {
    List<String> entries = new ArrayList<>();
    for (Class<?> type :
        List.of(Flashpi.class, ObjectMapper.class, JsonParser.class, JsonCreator.class)) {
      URI location = type.getProtectionDomain().getCodeSource().getLocation().toURI();
      entries.add(Paths.get(location).toString());
    }
    return String.join(File.pathSeparator, entries);
  }

  private static JavaFileObject source(String name, String code) {
    return new SimpleJavaFileObject(URI.create("string:///" + name), JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
      }
    };
  }
}
