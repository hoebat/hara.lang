package hara.kernel.command;

import hara.kernel.Command;
import hara.kernel.Foundation;
import hara.kernel.base.RT;
import hara.lang.base.Ex;
import hara.lang.base.Iter;

import java.util.List;

@Command.Fn(name = "SESSION")
public class Session {

  public enum CLASSPATH {
    ADD,
    LIST,
    REMOVE,
    PURGE
  }

  @Command.Sub(name = "EXISTS")
  public static Object exists(Foundation F, List<Object> args) {
    List<String> sArgs = Foundation.toStringList(args);
    RT.Instance s = (RT.Instance) F.RTS.get(sArgs.get(0));
    return s != null;
  }

  @Command.Sub(name = "GET")
  public static Object get(Foundation F, List<Object> args) {
    List<String> sArgs = Foundation.toStringList(args);
    return createSession(F, sArgs, false);
  }

  @Command.Sub(name = "NEW")
  public static Object newSession(Foundation F, List<Object> args) {
    List<String> sArgs = Foundation.toStringList(args);
    return createSession(F, sArgs, true);
  }

  @Command.Sub(name = "PATH")
  public static Object path(Foundation F, List<Object> args) {
    List<String> sArgs = Foundation.toStringList(args);
    String key = sArgs.get(0);
    RT.Instance rt = (RT.Instance) F.RTS.get(key);
    if (rt == null) {
      throw new Ex.Runtime("No Session: " + key);
    }

    // Original logic passed full sArgs to runSessionClasspath, which did:
    // CLASSPATH cmd = (args.size() == 1) ? CLASSPATH.LIST : CLASSPATH.valueOf(args.get(1));
    // args.remove(0); args.remove(0); and then switch on cmd.

    // Here sArgs is [session_name, subcmd, ...]
    // So if size == 1, it's just PATH session_name -> LIST

    CLASSPATH cmd = (sArgs.size() == 1) ? CLASSPATH.LIST : CLASSPATH.valueOf(sArgs.get(1));
    if (cmd == CLASSPATH.LIST) {
      return rt.pathCache();
    }

    sArgs.remove(0); // session name
    sArgs.remove(0); // subcommand name (ADD/REMOVE/etc)

    switch (cmd) {
      case ADD:
        return rt.pathAdd(sArgs.toArray(new String[] {}));
      case REMOVE:
        return rt.pathRemove(sArgs.toArray(new String[] {}));
      case PURGE:
        return rt.pathCache().empty();
      case LIST:
        return rt.pathCache();
    }
    throw new Ex.Unsupported();
  }

  @Command.Sub(name = "LIST")
  public static Object list(Foundation F, List<Object> args) {
    return Iter.toArrayList(F.RTS.keys());
  }

  @Command.Sub(name = "KILL")
  public static Object kill(Foundation F, List<Object> args) {
    List<String> sArgs = Foundation.toStringList(args);
    String key = sArgs.get(0);
    RT.Instance rt = (RT.Instance) F.RTS.get(key);
    if (rt == null) {
      throw new Ex.Runtime("No Session: " + key);
    }
    F.RTS.remove(key);
    return true; // or whatever runSessionFor returned for logic?
    // runSessionFor called f(rt). The logic was F.RTS.remove(sArgs.get(0)).
    // F.RTS.remove returns the value.
    // Wait, the lambda was (rt) -> F.RTS.remove(sArgs.get(0)).
    // F.RTS.remove returns the removed object or null.
  }

  @Command.Sub(name = "INFO")
  public static Object info(Foundation F, List<Object> args) {
    throw new Ex.TODO();
  }

  // Helper method replacing Foundation.Fn.runSessionCreate
  private static Object createSession(Foundation F, List<String> args, boolean raise) {
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
}
