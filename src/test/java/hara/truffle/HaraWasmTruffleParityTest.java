package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hara.kernel.base.Parser;
import hara.lang.data.Keyword;
import hara.lang.data.types.ILinearType;
import hara.lang.data.types.IMapType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;

/** Executes the shared numeric parity corpus against Rust WASM and Truffle Hara. */
public class HaraWasmTruffleParityTest {
  private static final Path CORPUS = Path.of("spec/hara/wasm-truffle-parity.edn");
  private static final Path ARTIFACT =
      Path.of("wasm/raw/target/wasm32-unknown-unknown/release/hara_wasm_raw.wasm");

  private static Keyword key(String name) {
    return Keyword.create(null, name);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Test
  public void sharedCorpusMatchesRustWasmAndTruffle() throws Exception {
    assertTrue("build wasm/raw before parity tests: " + ARTIFACT, Files.isRegularFile(ARTIFACT));
    IMapType manifest = (IMapType) Parser.LispReader.readString(Files.readString(CORPUS), null);
    ILinearType<?> cases = (ILinearType<?>) manifest.lookup(key("cases"));
    assertTrue(cases.count() > 0);

    Path root = Files.createTempDirectory("hara-wasm-truffle-parity-");
    Path extension = root.resolve("test/rust/parity");
    Files.createDirectories(extension);
    Files.writeString(
        extension.resolve("hara.extension.edn"),
        "{:namespace \"test.rust.parity\" :version \"0.1.0\" :provider :wasm "
            + ":module \"hara.wasm\" :abi :core-v1 "
            + ":exports {\"eval_i64\" {:args [:utf8] :returns :i64}} :capabilities []}");
    Files.copy(ARTIFACT, extension.resolve("hara.wasm"));
    String previous = System.getProperty("hara.extensions.path");
    System.setProperty("hara.extensions.path", root.toString());
    try {
      for (Object item : cases) {
        IMapType testCase = (IMapType) item;
        String id = ((Keyword) testCase.lookup(key("id"))).getName();
        String source = (String) testCase.lookup(key("source"));
        long expected = ((Number) testCase.lookup(key("expect"))).longValue();
        try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
          context.eval(HaraLanguage.ID, "(ns app (:require [test.rust.parity :as rust]))");
          Value truffle = context.eval(HaraLanguage.ID, source);
          Value wasm = context.eval(HaraLanguage.ID, "(rust/eval_i64 " + quote(source) + ")");
          assertEquals(id + " expected Truffle result", expected, truffle.asLong());
          assertEquals(id + " expected Rust WASM result", expected, wasm.asLong());
          assertEquals(id + " Rust WASM/Truffle mismatch", truffle.asLong(), wasm.asLong());
        }
      }
    } finally {
      if (previous == null) System.clearProperty("hara.extensions.path");
      else System.setProperty("hara.extensions.path", previous);
      Files.deleteIfExists(extension.resolve("hara.wasm"));
      Files.deleteIfExists(extension.resolve("hara.extension.edn"));
      Files.deleteIfExists(extension);
      Files.deleteIfExists(extension.getParent());
      Files.deleteIfExists(extension.getParent().getParent());
      Files.deleteIfExists(root);
    }
  }

  private static String quote(String source) {
    return "\"" + source.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
