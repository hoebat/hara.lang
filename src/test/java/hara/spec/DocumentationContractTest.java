package hara.spec;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

/** Verifies that published examples and mirrored normative documents describe the current slice. */
public class DocumentationContractTest {
  private static final List<String> MIRRORED_FILES =
      List.of(
          "l0-language.md",
          "runtime-libraries.md",
          "xtalk-equivalence.md",
          "extensions-contract.md",
          "extensions.md",
          "native-flavors.md",
          "jvm-flavor.md",
          "repl.md",
          "l0-conformance.edn",
          "rust-runtime.md");

  @Test
  public void normativeSpecificationsAreMirroredIntoDocumentationReference() throws Exception {
    for (String file : MIRRORED_FILES) {
      assertTrue("Missing canonical spec: " + file, Files.exists(Path.of("spec/hara", file)));
      assertTrue(
          "Missing documentation mirror: " + file, Files.exists(Path.of("docs/reference", file)));
      assertTrue(
          "Spec and documentation mirror differ: " + file,
          Files.readString(Path.of("spec/hara", file), StandardCharsets.UTF_8)
              .equals(Files.readString(Path.of("docs/reference", file), StandardCharsets.UTF_8)));
    }
  }

  @Test
  public void publishedExamplesUseSupportedMarkerSyntaxAndExistingFiles() throws Exception {
    String userGuide = Files.readString(Path.of("docs/user-guide.md"), StandardCharsets.UTF_8);
    assertTrue(userGuide.contains("(. a (push-last 4))"));
    assertTrue(userGuide.contains("(. a (get 3))"));
    assertFalse(userGuide.contains("(array:push-last"));
    assertFalse(userGuide.contains("(array:get"));
    assertTrue(Files.exists(Path.of("examples/hello.hal")));
    assertFalse(Files.exists(Path.of("examples/hello.hara")));
  }
}
