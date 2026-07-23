package hara.truffle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Discovers extension manifests packaged on the runtime classpath. */
final class HaraExtensionRegistry {
  private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
  private final ClassLoader classLoader;
  private final Map<String, HaraExtensionManifest> manifests = new ConcurrentHashMap<>();
  private final java.util.Set<String> missing = ConcurrentHashMap.newKeySet();

  HaraExtensionRegistry(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  HaraExtensionManifest discover(String namespace) {
    HaraExtensionManifest cached = manifests.get(namespace);
    if (cached != null) return cached;
    if (missing.contains(namespace)) return null;
    String resource = resourceName(namespace);
    try {
      ArrayList<URL> candidates = new ArrayList<>();
      Enumeration<URL> urls = classLoader.getResources(resource);
      while (urls.hasMoreElements()) candidates.add(urls.nextElement());
      if (candidates.isEmpty()) {
        missing.add(namespace);
        return null;
      }
      if (candidates.size() > 1) {
        throw new HaraException(
            "extension/ambiguous: multiple packages export " + namespace + ": " + candidates);
      }
      URL location = candidates.get(0);
      byte[] bytes;
      try (InputStream input = location.openStream()) {
        bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
      }
      if (bytes.length > MAX_MANIFEST_BYTES) {
        throw new HaraException("extension/malformed " + location + ": manifest is too large");
      }
      HaraExtensionManifest manifest =
          HaraExtensionManifest.parse(
              new String(bytes, StandardCharsets.UTF_8), location.toString());
      if (!manifest.namespace().equals(namespace)) {
        throw new HaraException(
            "extension/malformed " + location + ": expected namespace " + namespace);
      }
      manifests.put(namespace, manifest);
      return manifest;
    } catch (IOException | IllegalArgumentException error) {
      if (error instanceof HaraException) throw (HaraException) error;
      throw new HaraException(error.getMessage());
    }
  }

  static String resourceName(String namespace) {
    return "META-INF/hara/extensions/" + namespace.replace('.', '/') + "/hara.extension.edn";
  }
}
