package ec.com.flashpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * scripts/OpenApi.java on contracts it must refuse or escape: compiled here with the JDK's compiler
 * and called without writing anything to the repository.
 */
class GeneratorTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static Method generate;
  private static Method parse;

  @BeforeAll
  static void compileTheGenerator(@TempDir Path output) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    try (StandardJavaFileManager files =
        compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      boolean compiled =
          compiler
              .getTask(
                  null,
                  files,
                  null,
                  List.of("--release", "11", "-d", output.toString()),
                  null,
                  files.getJavaFileObjects(Paths.get("scripts/OpenApi.java").toFile()))
              .call();
      assertTrue(compiled);
    }
    URLClassLoader loader = new URLClassLoader(new URL[] {output.toUri().toURL()});
    Class<?> openApi = loader.loadClass("OpenApi");
    generate = openApi.getDeclaredMethod("generate", String.class);
    generate.setAccessible(true);
    Class<?> json = loader.loadClass("OpenApi$Json");
    parse = json.getDeclaredMethod("parse", String.class);
    parse.setAccessible(true);
  }

  private static ObjectNode contract() throws Exception {
    return (ObjectNode) MAPPER.readTree(Files.readAllBytes(Paths.get("openapi/openapi.json")));
  }

  private static ObjectNode schemas(ObjectNode contract) {
    return (ObjectNode) contract.get("components").get("schemas");
  }

  private static ObjectNode schema(ObjectNode contract, String name) {
    return (ObjectNode) schemas(contract).get(name);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> generate(ObjectNode contract) throws Exception {
    try {
      return (Map<String, String>) generate.invoke(null, MAPPER.writeValueAsString(contract));
    } catch (InvocationTargetException error) {
      if (error.getCause() instanceof Exception) {
        throw (Exception) error.getCause();
      }
      throw error;
    }
  }

  private static String refusal(ObjectNode contract) {
    try {
      generate(contract);
      return null;
    } catch (Exception error) {
      assertEquals("OpenApi$GenerationError", error.getClass().getName(), String.valueOf(error));
      return error.getMessage();
    }
  }

  @Test
  void theCommittedModelsAreTheGeneratorsOutput() throws Exception {
    Map<String, String> files = generate(contract());
    for (Map.Entry<String, String> file : files.entrySet()) {
      Path path = Paths.get("src/generated/java/ec/com/flashpi/models", file.getKey());
      assertEquals(file.getValue(), Files.readString(path, StandardCharsets.UTF_8), file.getKey());
    }
  }

  /** Text of the contract never becomes code: enum and discriminator values, descriptions. */
  @Test
  void aHostileContractCompilesToHarmlessModels(@TempDir Path output) throws Exception {
    ObjectNode contract = contract();
    String payload = "System.setProperty(\"flashpi.injected\", \"yes\")";
    ArrayNode codes = (ArrayNode) schema(contract, "ErrorCode").get("enum");
    codes.add("x\" + " + payload + " + \"");
    codes.add("back\\slash\\u002a/");
    schema(contract, "FieldError")
        .put(
            "description",
            "Ends */ static { "
                + payload
                + "; } /* and {@code */ static { } /*} @return x"
                + " \\u002a/ static { "
                + payload
                + "; } /* `code with */ and \\u002a/ in it`");
    ((ObjectNode)
            schema(contract, "WebhookEndpointVerificationEvent").get("properties").get("type"))
        .put("const", "webhook\"endpoint.verification");
    Map<String, String> files = generate(contract);

    List<JavaFileObject> sources = new ArrayList<>();
    for (Map.Entry<String, String> file : files.entrySet()) {
      sources.add(source("ec/com/flashpi/models/" + file.getKey(), file.getValue()));
    }
    try (Stream<Path> runtime = Files.list(Paths.get("src/main/java/ec/com/flashpi/models"))) {
      for (Path file : runtime.collect(Collectors.toList())) {
        sources.add(
            source(
                "ec/com/flashpi/models/" + file.getFileName(),
                Files.readString(file, StandardCharsets.UTF_8)));
      }
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
      boolean compiled =
          compiler
              .getTask(
                  null,
                  fileManager,
                  diagnostics,
                  List.of(
                      "--release", "11", "-classpath", jacksonClasspath(), "-d", output.toString()),
                  null,
                  sources)
              .call();
      assertTrue(compiled, diagnostics.getDiagnostics().toString());
    }
    // Isolated from the SDK's own classes: only the generated models and Jackson.
    List<URL> urls = new ArrayList<>();
    urls.add(output.toUri().toURL());
    for (String jar : jacksonClasspath().split(File.pathSeparator, -1)) {
      urls.add(Paths.get(jar).toUri().toURL());
    }
    try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), null)) {
      Class<?> errorCode = Class.forName("ec.com.flashpi.models.ErrorCode", true, loader);
      Class.forName("ec.com.flashpi.models.FieldError", true, loader);
      Class.forName("ec.com.flashpi.models.ModelRegistry", true, loader);
      Object[] constants = errorCode.getEnumConstants();
      List<Object> values = new ArrayList<>();
      for (Object constant : constants) {
        values.add(errorCode.getMethod("value").invoke(constant));
      }
      assertTrue(values.contains("x\" + " + payload + " + \""), values.toString());
      assertTrue(values.contains("back\\slash\\u002a/"), values.toString());
    }
    assertNull(System.getProperty("flashpi.injected"));
    String fieldError = files.get("FieldError.java");
    String javadoc =
        fieldError.substring(fieldError.indexOf("/**"), fieldError.indexOf("@JsonSerialize"));
    assertEquals(1, javadoc.split("\\*/", -1).length - 1, javadoc);
    assertTrue(!javadoc.contains("\\u"), javadoc);
  }

  @Test
  void aChangeThatWouldRenameOrSplitASharedEnumFails() throws Exception {
    ObjectNode grown = contract();
    ((ArrayNode)
            ((ObjectNode) schema(grown, "Document").get("properties").get("status")).get("enum"))
        .add("archived");
    String split = refusal(grown);
    assertNotNull(split);
    assertTrue(split.contains("SHARED_ENUMS"), split);

    ObjectNode shared = contract();
    ObjectNode export = schemas(shared).putObject("AaaExport");
    export.put("type", "object");
    export
        .putObject("properties")
        .set(
            "kind",
            ((ObjectNode) schema(shared, "Document").get("properties").get("type")).deepCopy());
    String renamed = refusal(shared);
    assertNotNull(renamed);
    assertTrue(renamed.contains("no SHARED_ENUMS entry lists exactly AaaExport.kind"), renamed);
  }

  @Test
  void refusesNamesThatHideTheTypesTheModelsUse() throws Exception {
    ObjectNode contract = contract();
    schemas(contract).putObject("Override").put("type", "object").putObject("properties");
    String refused = refusal(contract);
    assertNotNull(refused);
    assertTrue(refused.contains("Override would hide a type"), refused);
  }

  @Test
  void anInt64OrALargeBoundIsALong() throws Exception {
    ObjectNode contract = contract();
    ObjectNode properties = (ObjectNode) schema(contract, "Pagination").get("properties");
    properties.putObject("total").put("type", "integer").put("format", "int64");
    properties.putObject("offset").put("type", "integer").put("exclusiveMaximum", 1e12);
    properties.putObject("page").put("type", "integer").put("minimum", 1);
    String pagination = generate(contract).get("Pagination.java");
    assertTrue(pagination.contains("public Long getTotal()"), pagination);
    assertTrue(pagination.contains("public Long getOffset()"), pagination);
    assertTrue(pagination.contains("public Integer getPage()"), pagination);
    ((ObjectNode) properties.get("page")).put("format", "uint8");
    assertNotNull(refusal(contract));
  }

  @Test
  void theJsonReaderIsStrict() throws Exception {
    for (String bad :
        List.of(
            "+1",
            "01",
            ".5",
            "1.",
            "\"\\u+041\"",
            "\"\\u00\"",
            "\"abc\\",
            "\ufeff{}",
            "{\"a\":1,\"a\":2}")) {
      try {
        parse.invoke(null, bad);
        throw new AssertionError("accepted " + bad);
      } catch (InvocationTargetException error) {
        assertEquals(
            "OpenApi$GenerationError",
            error.getCause().getClass().getName(),
            bad + ": " + error.getCause());
      }
    }
    assertEquals("A", parse.invoke(null, "\"\\u0041\""));
  }

  private static String jacksonClasspath() throws Exception {
    List<String> entries = new ArrayList<>();
    for (Class<?> type : List.of(ObjectMapper.class, JsonParser.class, JsonCreator.class)) {
      entries.add(
          Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
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
