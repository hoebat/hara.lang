package hara.lang.context;

import hara.lang.protocol.ISpace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Namespace-local collection of context configurations and active runtimes. */
public final class Space implements ISpace {
  private final Map<Object, Object> configurations = new LinkedHashMap<>();
  private final Map<Object, Object> runtimes = new LinkedHashMap<>();

  @Override
  public synchronized void contextSet(Object context, Object key, Object options) {
    configurations.put(context, options == null ? key : options);
  }

  @Override
  public synchronized void contextUnset(Object context) {
    runtimes.remove(context);
    configurations.remove(context);
  }

  @Override
  public synchronized List<?> contextList() {
    return new ArrayList<>(configurations.keySet());
  }

  @Override
  public synchronized Object contextGet(Object context) {
    return configurations.get(context);
  }

  @Override
  public synchronized List<?> activeRuntimes() {
    return new ArrayList<>(runtimes.keySet());
  }

  @Override
  public synchronized Object runtimeGet(Object context) {
    return runtimes.get(context);
  }

  @Override
  public synchronized Object runtimeStart(Object context) {
    Object runtime = runtimes.get(context);
    if (runtime == null) {
      runtime = configurations.get(context);
      if (runtime == null) runtime = NullContext.INSTANCE;
      runtimes.put(context, runtime);
    }
    return runtime;
  }

  @Override
  public synchronized boolean runtimeStarted(Object context) {
    return runtimes.containsKey(context);
  }

  @Override
  public synchronized boolean runtimeStopped(Object context) {
    return !runtimes.containsKey(context);
  }

  @Override
  public synchronized void runtimeStop(Object context) {
    runtimes.remove(context);
  }
}
