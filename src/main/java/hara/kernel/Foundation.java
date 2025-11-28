package hara.kernel;

import hara.kernel.base.RT;
import hara.kernel.base.RT.Instance;
import hara.kernel.protocol.IRuntime;
import hara.lang.base.Arr;
import hara.lang.base.Ex;
import hara.lang.base.It;
import hara.lang.protocol.IContext;
import hara.lang.protocol.IDisplay;
import hara.lang.protocol.INamespaced;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class Foundation implements IContext {

  static final int DEFAULT_PORT = 4164;

  public static class Peer {
    public final String name;
    public final String host;
    public final int port;

    public Peer(String name, String host, int port) {
      this.name = name;
      this.host = host;
      this.port = port;
    }

    @Override
    public String toString() {
      return name + "@" + host + ":" + port;
    }
  }

  public final ConcurrentHashMap<String, Server> SERVERS = new ConcurrentHashMap<String, Server>();
  public final ConcurrentHashMap<String, IRuntime> RTS = new ConcurrentHashMap<String, IRuntime>();
  public final ConcurrentHashMap<String, Peer> PEERS = new ConcurrentHashMap<String, Peer>();
  public final ConcurrentHashMap<String, Command.Type> REGISTRY = new ConcurrentHashMap<>();

  public Foundation() {
    loadCommands(
        Arrays.asList(
            hara.kernel.command.Core.class,
            hara.kernel.command.Jvm.class,
            hara.kernel.command.Os.class,
            hara.kernel.command.Server.class,
            hara.kernel.command.Session.class,
            hara.kernel.command.Peer.class,
            hara.kernel.command.Maven.class));
  }

  public void register(String name, Command.Type cmd) {
    REGISTRY.put(name, cmd);
  }

  public void loadCommands(java.util.List<Class<?>> classes) {
    for (Class<?> cls : classes) {
      // Check for Class-level Command.Fn
      Command.Fn classCmd = cls.getAnnotation(Command.Fn.class);
      if (classCmd != null) {
        // Build Dispatcher
        Map<String, Method> subCommands = new HashMap<>();
        for (Method method : cls.getDeclaredMethods()) {
          Command.Sub sub = method.getAnnotation(Command.Sub.class);
          if (sub != null) {
            subCommands.put(sub.name(), method);
          }
        }

        register(
            classCmd.name(),
            (F, args) -> {
              if (args.isEmpty()) {
                // Default to HELP if no subcommand
                java.util.Set<String> keys = new java.util.HashSet<>(subCommands.keySet());
                keys.add("HELP");
                return It.toArrayList(Arr.toIter(keys.toArray()));
              }

              String subName = args.get(0).toString().toUpperCase();

              // Auto-HELP
              if ("HELP".equals(subName)) {
                java.util.Set<String> keys = new java.util.HashSet<>(subCommands.keySet());
                keys.add("HELP");
                return It.toArrayList(Arr.toIter(keys.toArray()));
              }

              Method m = subCommands.get(subName);
              if (m != null) {
                args.remove(0); // Consume subcommand
                try {
                  return m.invoke(null, F, args);
                } catch (Exception e) {
                  throw Ex.Sneaky(e);
                }
              }
              throw new Ex.Unsupported("Unknown subcommand: " + subName);
            });
      } else {
        // Scan for Method-level Command.Fn
        for (Method method : cls.getDeclaredMethods()) {
          Command.Fn annotation = method.getAnnotation(Command.Fn.class);
          if (annotation != null) {
            register(
                annotation.name(),
                (F, args) -> {
                  try {
                    return method.invoke(null, F, args);
                  } catch (Exception e) {
                    throw Ex.Sneaky(e);
                  }
                });
          }
        }
      }
    }
  }

  public enum CLASSPATH {
    ADD,
    LIST,
    REMOVE,
    PURGE
  }

  @SuppressWarnings("unchecked")
  public static java.util.List<String> toStringList(java.util.List<Object> args) {
    return args.stream().map(Object::toString).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  public static Object mapToList(Object m) {
    if (m instanceof Map) {
      return ((Map) m)
          .entrySet().stream()
              .map(
                  e ->
                      Arrays.asList(
                          ((Map.Entry) e).getKey(), mapToList(((Map.Entry) e).getValue())))
              .collect(Collectors.toList());
    } else {
      return m;
    }
  }

  @SuppressWarnings("unchecked")
  public static Object run(Function<java.util.List<String>, Object> f, Object... args) {
    java.util.List input =
        (args.length == 1 && args[0] instanceof java.util.List)
            ? (java.util.List) args[0]
            : Arrays.asList(args);
    return f.apply(input);
  }

  @SuppressWarnings("unchecked")
  public static Object runIn(
      Function<java.util.List<String>, Object> f, IContext c, Object... args) {
    java.util.List input =
        (args.length == 1 && args[0] instanceof java.util.List)
            ? (java.util.List) args[0]
            : Arrays.asList(args);
    return f.apply(input);
  }

  public interface Fn {

    public static Object JVM_ENV(java.util.List<String> args) {
      return (args.size() == 0) ? mapToList(System.getenv()) : System.getenv(args.get(0));
    }

    public static Object JVM_PROPS(java.util.List<String> args) {
      return (args.size() == 0)
          ? mapToList(System.getProperties())
          : System.getProperty(args.get(0));
    }

    public static java.util.List runDIR(Foundation F) {
      return Arrays.asList(
          "SERVERS", It.toArrayList(F.SERVERS.keys()),
          "RTS", It.toArrayList(F.RTS.keys()),
          "PEERS", It.toArrayList(F.PEERS.keys()));
    }

    public static Object runInfo(Foundation F) {
      return mapToList(
          Map.of(
              "java.version", System.getProperty("java.version"),
              "os.name", System.getProperty("os.name"),
              "sessions", F.RTS.size(),
              "servers", F.SERVERS.size(),
              "peers", F.PEERS.size()));
    }

    public static String runProcess(java.util.List<String> args) {
      try {
        var p = new ProcessBuilder().command(args).start();
        return new String(p.getInputStream().readAllBytes());
      } catch (Throwable t) {
        throw Ex.Sneaky(t);
      }
    }

    @SuppressWarnings("unchecked")
    public static java.util.List runHELP(Foundation F, Object enums) {
      return It.toArrayList(It.map(Arr.toIter(enums), (x) -> x.toString()));
    }

    public static Object runSessionFor(Foundation F, String key, Function<RT.Instance, Object> f) {
      RT.Instance s = (Instance) F.RTS.get(key);

      if (s != null) return f.apply(s);
      throw new Ex.Runtime("No Session: " + key);
    }

    public static Object runSessionCreate(
        Foundation F, java.util.List<String> args, boolean raise) {
      var key = args.get(0);
      var s = F.RTS.get(key);
      if (s != null) {
        if (raise) {
          throw new Ex.Runtime("Session already exists: " + key);
        }
      } else {
        F.RTS.put(key, new RT.Instance(F, key));
      }
      return key;
    }

    public static Object runSessionClasspath(RT.Instance rt, java.util.List<String> args) {
      CLASSPATH cmd = (args.size() == 1) ? CLASSPATH.LIST : CLASSPATH.valueOf(args.get(1));
      if (cmd == CLASSPATH.LIST) {
        System.out.println("LIST");
        return rt.pathCache();
      }

      args.remove(0);
      args.remove(0);
      switch (cmd) {
        case ADD:
          return rt.pathAdd(args.toArray(new String[] {}));
        case REMOVE:
          return rt.pathRemove(args.toArray(new String[] {}));
        case PURGE:
          return rt.pathCache().empty();
        case LIST:
          return rt.pathCache();
      }
      throw new Ex.Unsupported();
    }
  }

  @SuppressWarnings("unchecked")
  public static Object runCommand(Foundation F, java.util.List<Object> args) {
    if (args.isEmpty()) {
      throw new Ex.Runtime("No command specified");
    }

    Object cmdObj = args.get(0);
    String name;

    if (cmdObj instanceof INamespaced) {
      name = ((INamespaced) cmdObj).getName();
    } else if (cmdObj instanceof IDisplay) {
      String display = ((IDisplay) cmdObj).display();
      name = display.startsWith(":") ? display.substring(1) : display;
    } else {
      name = cmdObj.toString();
    }

    name = name.toUpperCase();

    args.remove(0);

    Command.Type cmd = F.REGISTRY.get(name);
    if (cmd != null) {
      return cmd.apply(F, args);
    }

    throw new Ex.Unsupported("Unknown command: " + name);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object call(Object... args) {
    java.util.List<Object> inputs = It.toArrayList(It.iter(args));
    return runCommand(this, inputs);
  }
}
