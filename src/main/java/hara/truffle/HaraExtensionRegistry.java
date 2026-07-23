package hara.truffle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Discovers extension manifests packaged on the runtime classpath. */
final class HaraExtensionRegistry {
  private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
  private final ClassLoader classLoader;
  private final List<Path> roots;
  private final Map<String, HaraExtensionPackage> packages = new ConcurrentHashMap<>();
  private final java.util.Set<String> missing = ConcurrentHashMap.newKeySet();

  HaraExtensionRegistry(ClassLoader classLoader) {
    this(classLoader, configuredRoots());
  }

  HaraExtensionRegistry(ClassLoader classLoader, List<Path> roots) {
    this.classLoader = classLoader;
    this.roots = Collections.unmodifiableList(new ArrayList<>(roots));
  }

  HaraExtensionPackage discover(String namespace) {
    HaraExtensionPackage cached = packages.get(namespace);
    if (cached != null) return cached;
    if (missing.contains(namespace)) return null;
    String resource = resourceName(namespace);
    try {
      ArrayList<URL> candidates = new ArrayList<>();
      Enumeration<URL> urls = classLoader.getResources(resource);
      while (urls.hasMoreElements()) candidates.add(urls.nextElement());
      Path relative = Path.of(namespace.replace('.', '/'), "hara.extension.edn");
      for (Path root : roots) {
        Path descriptor = root.resolve(relative).normalize();
        if (!descriptor.startsWith(root.normalize())) {
          throw new HaraException("extension/path-denied: " + namespace);
        }
        if (Files.isRegularFile(descriptor)) candidates.add(descriptor.toUri().toURL());
      }
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
      HaraExtensionPackage extensionPackage = new HaraExtensionPackage(manifest, location);
      packages.put(namespace, extensionPackage);
      return extensionPackage;
    } catch (IOException | IllegalArgumentException error) {
      if (error instanceof HaraException) throw (HaraException) error;
      throw new HaraException(error.getMessage());
    }
  }

  static String resourceName(String namespace) {
    return "META-INF/hara/extensions/" + namespace.replace('.', '/') + "/hara.extension.edn";
  }

  private static List<Path> configuredRoots() {
    String configured = System.getProperty("hara.extensions.path", "");
    if (configured.isBlank()) configured = System.getenv().getOrDefault("HARA_EXTENSION_PATH", "");
    if (configured.isBlank()) return List.of();
    ArrayList<Path> roots = new ArrayList<>();
    for (String value : configured.split(java.io.File.pathSeparator)) {
      if (!value.isBlank()) roots.add(Path.of(value).toAbsolutePath().normalize());
    }
    return roots;
  }
}
