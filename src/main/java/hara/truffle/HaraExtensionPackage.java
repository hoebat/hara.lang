package hara.truffle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/** A validated extension descriptor and its colocated provider artifact. */
final class HaraExtensionPackage {
  private static final int MAX_MODULE_BYTES = 64 * 1024 * 1024;

  private final HaraExtensionManifest manifest;
  private final URL descriptor;

  HaraExtensionPackage(HaraExtensionManifest manifest, URL descriptor) {
    this.manifest = manifest;
    this.descriptor = descriptor;
  }

  HaraExtensionManifest manifest() {
    return manifest;
  }

  URL descriptor() {
    return descriptor;
  }

  byte[] moduleBytes() {
    try {
      URL module = new URL(descriptor, manifest.module());
      byte[] bytes;
      try (InputStream input = module.openStream()) {
        bytes = input.readNBytes(MAX_MODULE_BYTES + 1);
      }
      if (bytes.length > MAX_MODULE_BYTES) {
        throw new HaraException("extension/module-too-large: " + module);
      }
      return bytes;
    } catch (IOException error) {
      throw new HaraException(
          "extension/module-unavailable: "
              + manifest.namespace()
              + "/"
              + manifest.module()
              + " ("
              + error.getMessage()
              + ")");
    }
  }
}
