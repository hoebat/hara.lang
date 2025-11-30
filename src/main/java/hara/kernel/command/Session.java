package hara.kernel.command;

import hara.kernel.Command;
import hara.kernel.Foundation;
import hara.lang.base.Ex;
import hara.lang.base.It;

import java.util.List;

@Command.Fn(name = "SESSION")
public class Session {

  @Command.Sub(name = "EXISTS")
  public static Object exists(Foundation F, List<Object> args) {
    List<String> sArgs = Foundation.toStringList(args);
    return Foundation.Fn.runSessionFor(F, sArgs.get(0), (rt) -> rt != null);
  }

  @Command.Sub(name = "GET")
  public static Object get(Foundation F, List<Object> args) {
    List<String> sArgs = Foundation.toStringList(args);
    return Foundation.Fn.runSessionCreate(F, sArgs, false);
  }

  @Command.Sub(name = "NEW")
  public static Object newSession(Foundation F, List<Object> args) {
    List<String> sArgs = Foundation.toStringList(args);
    return Foundation.Fn.runSessionCreate(F, sArgs, true);
  }

  @Command.Sub(name = "PATH")
  public static Object path(Foundation F, List<Object> args) {
    List<String> sArgs = Foundation.toStringList(args);
    return Foundation.Fn.runSessionFor(
        F, sArgs.get(0), (rt) -> Foundation.Fn.runSessionClasspath(rt, sArgs));
  }

  @Command.Sub(name = "LIST")
  public static Object list(Foundation F, List<Object> args) {
    return It.toArrayList(F.RTS.keys());
  }

  @Command.Sub(name = "KILL")
  public static Object kill(Foundation F, List<Object> args) {
    List<String> sArgs = Foundation.toStringList(args);
    return Foundation.Fn.runSessionFor(F, sArgs.get(0), (rt) -> F.RTS.remove(sArgs.get(0)));
  }

  @Command.Sub(name = "INFO")
  public static Object info(Foundation F, List<Object> args) {
    throw new Ex.TODO();
  }
}
