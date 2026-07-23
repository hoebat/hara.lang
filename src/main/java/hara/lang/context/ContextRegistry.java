package hara.lang.context;

import hara.lang.protocol.IContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local registry for context implementations and their runtimes. */
public final class ContextRegistry {
  private final Map<Object, IContext> contexts = new ConcurrentHashMap<>();

  public void install(Object key, IContext context) {
    if (key == null || context == null)
      throw new IllegalArgumentException("Context and key required");
    contexts.put(key, context);
  }

  public void uninstall(Object key) {
    contexts.remove(key);
  }

  public IContext get(Object key) {
    IContext context = contexts.get(key);
    return context == null ? NullContext.INSTANCE : context;
  }

  public boolean contains(Object key) {
    return contexts.containsKey(key);
  }
}
