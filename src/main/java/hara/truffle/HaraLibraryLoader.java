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
    HaraLibraryProvider provider = providers.get(namespace);
    if (provider != null && installed.putIfAbsent(namespace, Boolean.TRUE) == null) {
      provider.install(context);
    }
  }
}
