package hara.kernel.command;

import hara.kernel.Command;
import hara.kernel.Foundation;

import java.util.List;

@Command.Fn(name = "MAVEN")
public class Maven {

  @Command.Sub(name = "LOAD")
  public static Object load(Foundation F, List<Object> args) {
    List<String> sArgs = Foundation.toStringList(args);
    return Foundation.Fn.runSessionFor(
        F, sArgs.get(0), (rt) -> hara.kernel.maven.Maven.load.invoke(rt, sArgs.get(1)));
  }
}
