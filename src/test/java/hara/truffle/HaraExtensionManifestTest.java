package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class HaraExtensionManifestTest {
  @Test
  public void packagedNoirProofManifestMatchesTheProviderContract() throws Exception {
    Path descriptor = Path.of("examples/extensions/blockchain/proof/noir/hara.extension.edn");
    HaraExtensionManifest manifest =
        HaraExtensionManifest.parse(
            Files.readString(descriptor, StandardCharsets.UTF_8), descriptor.toString());
    assertEquals("blockchain.proof.noir", manifest.namespace());
    assertEquals("0.1.0", manifest.version());
    assertEquals("wasm", manifest.provider());
    assertEquals("noir.wasm", manifest.module());
    assertEquals("core-v1", manifest.abi());
    assertEquals(2, manifest.exports().size());
    assertEquals("i32", manifest.exports().get("version").returns());
    assertEquals(2, manifest.exports().get("add").arguments().size());
    assertTrue(manifest.capabilities().isEmpty());
  }

  @Test
  public void malformedManifestsFailBeforeProviderSelection() {
    String base =
        "{:namespace \"demo.extension\" :version \"1\" :provider :wasm "
            + ":module \"demo.wasm\" :abi :core-v1 "
            + ":exports {\"run\" {:args [] :returns :i32}} "
            + ":capabilities []}";
    assertThrows(
        IllegalArgumentException.class,
        () ->
            HaraExtensionManifest.parse(
                base.replace(":provider :wasm", ":provider \"wasm\""), "test"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            HaraExtensionManifest.parse(
                base.replace(":capabilities []", ":capabilities [] :extra true"), "test"));
  }
}
