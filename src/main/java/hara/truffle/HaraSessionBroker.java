package hara.truffle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

/** Owns the runtime contexts shared by local and RESP clients. */
final class HaraSessionBroker implements AutoCloseable {
  private final boolean allowFile;
  private final boolean allowNetwork;
  private final boolean allowProcess;
  private final ConcurrentHashMap<String, HaraSession> sessions = new ConcurrentHashMap<>();

  HaraSessionBroker(boolean allowFile, boolean allowNetwork) {
    this(allowFile, allowNetwork, false);
  }

  HaraSessionBroker(boolean allowFile, boolean allowNetwork, boolean allowProcess) {
    this.allowFile = allowFile;
    this.allowNetwork = allowNetwork;
    this.allowProcess = allowProcess;
    sessions.put("ROOT", new HaraSession("ROOT", allowFile, allowNetwork, allowProcess));
  }

  HaraSession root() {
    return require("ROOT");
  }

  HaraSession require(String name) {
    HaraSession session = sessions.get(name);
    if (session == null) throw new IllegalArgumentException("NO_SESSION " + name);
    return session;
  }

  synchronized HaraSession create(String value) {
    String name = normalizeName(value);
    if (sessions.containsKey(name)) throw new IllegalArgumentException("SESSION_EXISTS " + name);
    HaraSession session = new HaraSession(name, allowFile, allowNetwork, allowProcess);
    sessions.put(name, session);
    return session;
  }

  synchronized void closeSession(String value) {
    String name = normalizeName(value);
    if ("ROOT".equals(name)) throw new IllegalArgumentException("ROOT_CANNOT_CLOSE");
    HaraSession removed = sessions.remove(name);
    if (removed == null) throw new IllegalArgumentException("NO_SESSION " + name);
    removed.close();
  }

  Set<String> sessionNames() {
    return Collections.unmodifiableSet(sessions.keySet());
  }

  int size() {
    return sessions.size();
  }

  @Override
  public synchronized void close() {
    for (HaraSession session : sessions.values()) session.close();
    sessions.clear();
  }

  static String normalizeName(String value) {
    if (value == null || value.isEmpty() || !value.matches("[A-Za-z0-9_.-]+"))
      throw new IllegalArgumentException("INVALID_SESSION_NAME");
    return value;
  }

  static final class HaraSession implements AutoCloseable {
    private final String name;
    private final Context context;

    private HaraSession(
        String name, boolean allowFile, boolean allowNetwork, boolean allowProcess) {
      this.name = name;
      context =
          Context.newBuilder(HaraLanguage.ID)
              .allowCreateProcess(allowProcess)
              .allowIO(
                  IOAccess.newBuilder()
                      .allowHostFileAccess(allowFile)
                      .allowHostSocketAccess(allowNetwork)
                      .build())
              .build();
    }

    String name() {
      return name;
    }

    synchronized Value eval(String source) {
      try {
        return context.eval(HaraLanguage.ID, source);
      } catch (PolyglotException error) {
        throw new IllegalArgumentException(error.getMessage(), error);
      }
    }

    synchronized String currentNamespace() {
      Value value = eval("(current-namespace)");
      return value.isString() ? value.asString() : value.toString();
    }

    synchronized List<String> currentSymbols() {
      Value values = eval("(current-symbols)");
      List<String> result = new ArrayList<>();
      for (long index = 0; index < values.getArraySize(); index++) {
        result.add(values.getArrayElement(index).asString());
      }
      return result;
    }

    List<Object> info() {
      return List.of("NAME", name, "STATE", "RUNNING");
    }

    @Override
    public synchronized void close() {
      context.close(true);
    }
  }
}
