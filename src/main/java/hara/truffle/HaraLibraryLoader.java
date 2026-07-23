package hara.truffle;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Discovers optional Java-backed Hara libraries through the application class loader. */
final class HaraLibraryLoader {
  private final Map<String, HaraLibraryProvider> providers = new ConcurrentHashMap<>();
  private final Map<String, Boolean> installed = new ConcurrentHashMap<>();

  HaraLibraryLoader() {
    List<HaraLibraryProvider> providers = new ArrayList<>();
    ServiceLoader.load(HaraLibraryProvider.class, HaraContext.class.getClassLoader())
        .forEach(providers::add);
    providers.sort(java.util.Comparator.comparingInt(HaraLibraryProvider::order));
    for (HaraLibraryProvider provider : providers) this.providers.put(provider.namespace(), provider);
  }

  void ensure(HaraContext context, String namespace) {
    for (Map.Entry<String, HaraLibraryProvider> entry : providers.entrySet()) {
      String provided = entry.getKey();
      if (namespace.equals(provided) || namespace.startsWith(provided + ".")) {
        if (installed.putIfAbsent(provided, Boolean.TRUE) == null) entry.getValue().install(context);
      }
    }
  }
}
