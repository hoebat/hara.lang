package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class HaraExtensionManifestTest {
  @Test
  public void packagedNoirProofManifestMatchesTheProviderContract() throws Exception {
    String resource = HaraExtensionRegistry.resourceName("blockchain.proof.noir");
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
      HaraExtensionManifest manifest =
          HaraExtensionManifest.parse(
              new String(input.readAllBytes(), StandardCharsets.UTF_8), resource);
      assertEquals("blockchain.proof.noir", manifest.namespace());
      assertEquals("0.1.0", manifest.version());
      assertEquals("wasm", manifest.provider());
      assertEquals("hara.noir", manifest.module());
      assertEquals(14, manifest.exports().size());
      assertTrue(manifest.exports().get("prove").async());
      assertEquals("promise", manifest.exports().get("verify").returns());
      assertTrue(manifest.capabilities().isEmpty());
    }
  }

  @Test
  public void malformedManifestsFailBeforeProviderSelection() {
    String base =
        "{:namespace \"demo.extension\" :version \"1\" :provider :wasm "
            + ":module \"demo\" :exports {\"run\" {:args [] :returns :value}} "
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
